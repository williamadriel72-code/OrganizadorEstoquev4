package com.organizador.scanner

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val GPS_ENDPOINT =
    "https://rlgsbtolosxyymosidns.supabase.co/functions/v1/bora-rider-location"
private const val GPS_PREFS = "bora_michael_gps_v1"
private const val GPS_CHANNEL_ID = "bora_michael_gps_tracking_v1"
private const val GPS_NOTIFICATION_ID = 2602
private const val MIN_SEND_INTERVAL_MS = 20_000L

object RiderGpsManager {
    private val io = Executors.newSingleThreadExecutor()
    private val binding = AtomicBoolean(false)

    fun hasLocationPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun isUserEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(GPS_PREFS, Context.MODE_PRIVATE)
            .getBoolean("user_enabled", true)
    }

    fun setUserEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        app.getSharedPreferences(GPS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("user_enabled", enabled)
            .apply()
        if (enabled) {
            startIfReady(app)
        } else {
            stop(app, clearSession = false)
        }
    }

    fun bindWebSession(
        context: Context,
        accessToken: String,
        expectedRiderId: String,
        riderName: String
    ) {
        if (accessToken.isBlank() || expectedRiderId.isBlank()) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(GPS_PREFS, Context.MODE_PRIVATE)
        val savedRider = prefs.getString("rider_id", "") ?: ""
        val savedGpsToken = prefs.getString("gps_token", "") ?: ""
        val expiresAt = prefs.getLong("expires_at", 0L)

        if (savedRider == expectedRiderId &&
            savedGpsToken.isNotBlank() &&
            expiresAt > System.currentTimeMillis() + 2 * 60 * 60 * 1000L
        ) {
            if (riderName.isNotBlank()) prefs.edit().putString("rider_name", riderName).apply()
            startIfReady(app)
            return
        }

        if (!binding.compareAndSet(false, true)) return
        io.execute {
            try {
                val body = JSONObject()
                    .put("action", "bootstrap")
                    .put("riderId", expectedRiderId)
                val response = postJson(GPS_ENDPOINT, accessToken, body)
                val gpsToken = response.optString("gps_token", "")
                val rider = response.optJSONObject("rider")
                val riderId = rider?.optString("id", expectedRiderId) ?: expectedRiderId
                val resolvedName = rider?.optString("nome", riderName) ?: riderName
                val expiry = response.optLong("expires_at", 0L)
                if (gpsToken.isBlank() || riderId.isBlank() || expiry <= System.currentTimeMillis()) return@execute
                prefs.edit()
                    .putString("gps_token", gpsToken)
                    .putString("rider_id", riderId)
                    .putString("rider_name", resolvedName)
                    .putLong("expires_at", expiry)
                    .putBoolean("auth_invalid", false)
                    .apply()
                startIfReady(app)
            } catch (_: Exception) {
                // Mantém o app funcionando mesmo se o servidor de GPS estiver temporariamente indisponível.
            } finally {
                binding.set(false)
            }
        }
    }

    fun startIfReady(context: Context) {
        val app = context.applicationContext
        if (!isUserEnabled(app)) return
        if (!hasLocationPermission(app)) return
        val prefs = app.getSharedPreferences(GPS_PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString("gps_token", "") ?: ""
        val expiry = prefs.getLong("expires_at", 0L)
        if (token.isBlank() || expiry <= System.currentTimeMillis()) return

        val intent = Intent(app, RiderLocationService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (_: Exception) {
            // Se o Android bloquear o início em segundo plano, a próxima abertura do app tenta novamente.
        }
    }

    fun stop(context: Context, clearSession: Boolean = false) {
        val app = context.applicationContext
        runCatching { app.stopService(Intent(app, RiderLocationService::class.java)) }
        if (clearSession) {
            app.getSharedPreferences(GPS_PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    fun clearForLogout(context: Context) = stop(context, clearSession = true)

    internal fun markAuthInvalid(context: Context) {
        context.getSharedPreferences(GPS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auth_invalid", true)
            .remove("gps_token")
            .remove("expires_at")
            .apply()
    }

    private fun postJson(urlText: String, bearer: String, body: JSONObject): JSONObject {
        val c = URL(urlText).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("Authorization", "Bearer $bearer")
        val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
        c.outputStream.use { it.write(bytes) }
        val code = c.responseCode
        val input = if (code in 200..299) c.inputStream else c.errorStream
        val text = readAll(input)
        val json = if (text.isBlank()) JSONObject() else JSONObject(text)
        if (code !in 200..299) throw IllegalStateException(json.optString("error", "HTTP $code"))
        return json
    }

    private fun readAll(input: InputStream?): String {
        if (input == null) return ""
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        input.use { stream ->
            while (true) {
                val n = stream.read(buffer)
                if (n <= 0) break
                out.write(buffer, 0, n)
            }
        }
        return out.toString(StandardCharsets.UTF_8.name())
    }
}

class RiderLocationService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private val io = Executors.newSingleThreadExecutor()
    private val sending = AtomicBoolean(false)
    @Volatile private var lastSentAt = 0L

    override fun onCreate() {
        super.onCreate()
        createGpsChannel()
        startForeground(GPS_NOTIFICATION_ID, buildNotification("GPS ativo • aguardando localização"))
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!RiderGpsManager.isUserEnabled(this) || !RiderGpsManager.hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startLocationUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        if (!RiderGpsManager.hasLocationPermission(this)) return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            runCatching {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 10_000L, 5f, this)
                    locationManager.getLastKnownLocation(provider)?.let { location ->
                        if (System.currentTimeMillis() - location.time < 5 * 60_000L) {
                            handleLocation(location, force = lastSentAt == 0L)
                        }
                    }
                }
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        handleLocation(location, force = false)
    }

    override fun onProviderEnabled(provider: String) {
        startLocationUpdates()
    }

    override fun onProviderDisabled(provider: String) {
        updateNotification("GPS ativo • procurando sinal")
    }

    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun handleLocation(location: Location, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSentAt < MIN_SEND_INTERVAL_MS) return
        if (!sending.compareAndSet(false, true)) return
        lastSentAt = now

        val prefs = getSharedPreferences(GPS_PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString("gps_token", "") ?: ""
        if (token.isBlank()) {
            sending.set(false)
            stopSelf()
            return
        }

        io.execute {
            try {
                val body = JSONObject()
                    .put("action", "update")
                    .put("latitude", location.latitude)
                    .put("longitude", location.longitude)
                    .put("accuracy", if (location.hasAccuracy()) location.accuracy.toDouble() else JSONObject.NULL)
                    .put("speed", if (location.hasSpeed()) location.speed.toDouble() else JSONObject.NULL)
                    .put("heading", if (location.hasBearing()) location.bearing.toDouble() else JSONObject.NULL)

                val code = sendLocation(token, body)
                if (code == 401 || code == 403) {
                    RiderGpsManager.markAuthInvalid(applicationContext)
                    stopSelf()
                    return@execute
                }
                if (code in 200..299) {
                    val name = prefs.getString("rider_name", "") ?: ""
                    val accuracy = if (location.hasAccuracy()) " • ±${location.accuracy.toInt()} m" else ""
                    updateNotification(
                        if (name.isBlank()) "GPS ativo$accuracy" else "$name • GPS ativo$accuracy"
                    )
                }
            } catch (_: Exception) {
                // Mantém o serviço ativo e tenta novamente na próxima posição.
            } finally {
                sending.set(false)
            }
        }
    }

    private fun sendLocation(token: String, body: JSONObject): Int {
        val c = URL(GPS_ENDPOINT).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 12_000
        c.readTimeout = 15_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("Authorization", "Bearer $token")
        val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
        c.outputStream.use { it.write(bytes) }
        val code = c.responseCode
        runCatching {
            val input = if (code in 200..299) c.inputStream else c.errorStream
            input?.close()
        }
        c.disconnect()
        return code
    }

    private fun createGpsChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            GPS_CHANNEL_ID,
            "Localização do motoboy",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantém o GPS do Bora Michael ativo durante o trabalho"
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            GPS_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, GPS_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Bora Michael Hi Hi")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()
    }

    private fun updateNotification(text: String) {
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(GPS_NOTIFICATION_ID, buildNotification(text))
        }
    }

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(this) }
        io.shutdownNow()
        super.onDestroy()
    }
}
