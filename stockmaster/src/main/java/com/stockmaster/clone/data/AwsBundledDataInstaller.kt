package com.aws.gestaoestoque.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Instala no banco local, uma única vez, o conjunto de mercadorias e validades
 * extraído dos relatórios de 14/08/2026 que acompanha o APK.
 *
 * Somente mercadorias/validades ficam nos assets. Usuários, senhas e demais
 * configurações continuam exclusivamente no banco local importado pelo usuário.
 */
internal class AwsBundledDataInstaller(private val context: Context) {

    data class Result(
        val productsProcessed: Int,
        val expiryProcessed: Int,
        val alreadyInstalled: Boolean
    )

    fun installIfNeeded(database: SQLiteDatabase): Result {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS aws_meta (chave TEXT PRIMARY KEY, valor TEXT)"
        )

        val installed = database.rawQuery(
            "SELECT valor FROM aws_meta WHERE chave=? LIMIT 1",
            arrayOf(DATASET_KEY)
        ).use { c -> c.moveToFirst() && c.getString(0) == DATASET_VERSION }

        if (installed) return Result(0, 0, true)

        val assetNames = context.assets.list("")?.toList().orEmpty()
        val productAssets = assetNames
            .filter { it.startsWith(PRODUCT_PREFIX) && it.endsWith(".b64") }
            .sorted()
        val expiryAssets = assetNames
            .filter { it.startsWith(EXPIRY_PREFIX) && it.endsWith(".b64") }
            .sorted()

        require(productAssets.isNotEmpty()) {
            "Dados integrados de mercadorias não encontrados no APK."
        }
        require(expiryAssets.isNotEmpty()) {
            "Dados integrados de validade não encontrados no APK."
        }

        var productCount = 0
        var expiryCount = 0

        database.beginTransaction()
        try {
            decodedLines(productAssets).forEach { line ->
                val parts = line.split('\t', limit = 4)
                if (parts.size < 4) return@forEach

                val id = parts[0].trim().toLongOrNull() ?: return@forEach
                val ean = parts[1].trim()
                val stock = parts[2].trim().toDoubleOrNull() ?: 0.0
                val description = parts[3].trim()
                if (description.isBlank()) return@forEach

                val insert = ContentValues().apply {
                    put("id", id)
                    put("idempresa", 1)
                    put("idun", "UN")
                    put("ean", ean)
                    put("balanca", "N")
                    put("promocao", "N")
                    put("descricaoproduto", description)
                    put("qtestoque", stock)
                    put("ativo", "S")
                }
                database.insertWithOnConflict(
                    "produto",
                    null,
                    insert,
                    SQLiteDatabase.CONFLICT_IGNORE
                )

                val update = ContentValues().apply {
                    if (ean.isNotBlank()) put("ean", ean)
                    put("descricaoproduto", description)
                    put("qtestoque", stock)
                    put("ativo", "S")
                }
                database.update(
                    "produto",
                    update,
                    "id=?",
                    arrayOf(id.toString())
                )
                productCount++
            }

            decodedLines(expiryAssets).forEach { line ->
                val parts = line.split('\t')
                if (parts.size < 3) return@forEach

                val productId = parts[0].trim()
                val expiry = parts[2].trim()
                if (productId.isBlank() || expiry.isBlank()) return@forEach

                val exists = database.rawQuery(
                    "SELECT 1 FROM controlevalidade WHERE TRIM(CAST(idproduto AS TEXT))=? AND validade=? LIMIT 1",
                    arrayOf(productId, expiry)
                ).use { it.moveToFirst() }

                if (!exists) {
                    val values = ContentValues().apply {
                        put("idproduto", productId)
                        put("idempresa", 1)
                        put("dataemissao", "2026-08-15")
                        put("lote", "")
                        put("validade", expiry)
                        put("status", "P")
                        put("qtdecoletada", 0.0)
                        put("qtdecaixa", 0.0)
                        put("datahoraemissao", "2026-08-15T00:00:00")
                        put("datahoracoletado", "2026-08-15T00:00:00")
                    }
                    database.insertOrThrow("controlevalidade", null, values)
                }
                expiryCount++
            }

            database.execSQL(
                "INSERT OR REPLACE INTO aws_meta(chave,valor) VALUES(?,?)",
                arrayOf(DATASET_KEY, DATASET_VERSION)
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        return Result(productCount, expiryCount, false)
    }

    private fun decodedLines(assetNames: List<String>): Sequence<String> {
        val encoded = buildString {
            assetNames.forEach { name ->
                context.assets.open(name).bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.forEachLine { append(it.trim()) }
                }
            }
        }
        val compressed = Base64.decode(encoded, Base64.DEFAULT)
        val input = GZIPInputStream(ByteArrayInputStream(compressed))
        return input.bufferedReader(Charsets.UTF_8).lineSequence().constrainOnce()
    }

    companion object {
        private const val DATASET_KEY = "aws_integrated_dataset"
        private const val DATASET_VERSION = "2026-08-14-v1"
        private const val PRODUCT_PREFIX = "aws_bundle_products_"
        private const val EXPIRY_PREFIX = "aws_bundle_expiry_"
    }
}
