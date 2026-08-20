package com.organizador.estoque.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

class SupabaseCatalogSync(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("supabase_session_v1", Context.MODE_PRIVATE)

    data class SyncResult(
        val products: Int,
        val expiries: Int
    )

    private data class Session(
        val accessToken: String,
        val refreshToken: String
    )

    private class HttpFailure(val status: Int, message: String) : Exception(message)

    fun hasSavedSession(): Boolean = !prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    suspend fun signInAndSync(email: String, password: String): SyncResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        require(cleanEmail.isNotEmpty()) { "Informe o e-mail" }
        require(password.isNotEmpty()) { "Informe a senha" }

        val body = JSONObject()
            .put("email", cleanEmail)
            .put("password", password)
            .toString()

        val json = JSONObject(
            request(
                method = "POST",
                path = "/auth/v1/token?grant_type=password",
                body = body,
                accessToken = null
            )
        )
        val session = Session(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token")
        )
        saveSession(session)
        sync(session.accessToken)
    }

    suspend fun syncSavedSession(): SyncResult? = withContext(Dispatchers.IO) {
        val access = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return@withContext null
        try {
            sync(access)
        } catch (failure: HttpFailure) {
            if (failure.status != 401) throw failure
            val refreshed = refreshSession() ?: throw failure
            sync(refreshed.accessToken)
        }
    }

    private fun refreshSession(): Session? {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val body = JSONObject().put("refresh_token", refreshToken).toString()
        val json = JSONObject(
            request(
                method = "POST",
                path = "/auth/v1/token?grant_type=refresh_token",
                body = body,
                accessToken = null
            )
        )
        val session = Session(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token", refreshToken)
        )
        saveSession(session)
        return session
    }

    private fun saveSession(session: Session) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .apply()
    }

    private fun sync(accessToken: String): SyncResult {
        val productsByCode = LinkedHashMap<String, Product>(14000)
        val prices = LinkedHashMap<String, Double>(14000)
        val expiries = ArrayList<ExpiryImportRow>(9000)
        var offset = 0

        while (true) {
            val path = "/rest/v1/app_catalog_sync?select=code,ean,description,stock,price,expiry&order=code.asc&limit=$PAGE_SIZE&offset=$offset"
            val page = JSONArray(request("GET", path, null, accessToken))
            if (page.length() == 0) break

            for (index in 0 until page.length()) {
                val row = page.getJSONObject(index)
                val code = row.getString("code")
                val ean = if (row.isNull("ean")) null else row.optString("ean").takeIf { it.isNotBlank() }
                val stock = row.optDouble("stock", 0.0)
                val price = row.optDouble("price", 0.0)
                val expiry = if (row.isNull("expiry")) null else row.optString("expiry").takeIf { it.isNotBlank() }

                productsByCode[code] = Product(
                    code = code,
                    ean = ean,
                    description = row.getString("description"),
                    groupCode = null,
                    category = null,
                    stock = stock,
                    controlsExpiry = expiry != null,
                    active = true
                )
                prices[code] = price
                if (expiry != null) {
                    expiries += ExpiryImportRow(
                        productRef = code,
                        expiryDate = expiry,
                        quantity = max(stock, 0.0)
                    )
                }
            }

            val read = page.length()
            offset += read
            if (read < PAGE_SIZE) break
        }

        require(productsByCode.isNotEmpty()) { "O Supabase não retornou produtos" }

        InventorySnapshotInstaller(appContext).replace(productsByCode.values.toList())
        CatalogPriceStore(appContext).also { store ->
            try {
                store.replace(prices)
            } finally {
                store.close()
            }
        }
        val (expiryCount, skipped) = ExpiryImportStore(appContext).replace(expiries)
        require(skipped == 0) { "Validades ignoradas durante sincronização: $skipped" }

        prefs.edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .putInt(KEY_LAST_PRODUCT_COUNT, productsByCode.size)
            .apply()

        return SyncResult(products = productsByCode.size, expiries = expiryCount)
    }

    private fun request(method: String, path: String, body: String?, accessToken: String?): String {
        val connection = (URL(SUPABASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("apikey", SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            if (!accessToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching {
                    val json = JSONObject(text)
                    json.optString("msg").ifBlank { json.optString("message") }.ifBlank { json.optString("error_description") }
                }.getOrDefault("").ifBlank { "Erro HTTP $status" }
                throw HttpFailure(status, message)
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val SUPABASE_URL = "https://rlgsbtolosxyymosidns.supabase.co"
        private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_cdYfnl879c7gh4WQE27S5g_CxEtVxde"
        private const val PAGE_SIZE = 1000
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_LAST_PRODUCT_COUNT = "last_product_count"
    }
}
