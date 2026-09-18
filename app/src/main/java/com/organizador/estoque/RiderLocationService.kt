package com.organizador.estoque

import android.Manifest
import android.annotation.SuppressLint
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
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class RiderLocationService : Service(), LocationListener {
    companion object {
        const val ACTION_START = "com.organizador.estoque.action.START_RIDER_GPS"
        const val ACTION_STOP = "com.organizador.estoque.action.STOP_RIDER_GPS"
        const val EXTRA_TOKEN = "rider_token"
        const val EXTRA_RIDER_NAME = "rider_name"

        private const val PREFS = "bora_background_gps"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TOKEN = "token"
        private const val KEY_RIDER_NAME = "rider_name"
        private const val CHANNEL_ID = "bora_michael_gps"
        private const val NOTIFICATION_ID = 52026
        private const val UPDATE_INTERVAL_MS = 20_000L
        private const val MIN_DISTANCE_M = 8f
        private const val API_URL =
            "https://rlgsbtolosxyymosidns.supabase.co/functions/v1/bora-ifood-test-rider"
    }

    private lateinit var locationManager: LocationManager
    @Volatile private var token: String = ""
    @Volatile private var riderName: String = ""
    @Volatile private var lastUploadAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTrackingAndSelf()
            return START_NOT_STICKY
        }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val incomingToken = intent?.getStringExtra(EXTRA_TOKEN).orEmpty()
        val incomingName = intent?.getStringExtra(EXTRA_RIDER_NAME).orEmpty()

        if (incomingToken.isNotBlank()) {
            token = incomingToken
            riderName = incomingName
            prefs.edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_TOKEN, token)
                .putString(KEY_RIDER_NAME, riderName)
                .apply()
        } else {
            token = prefs.getString(KEY_TOKEN, "").orEmpty()
            riderName = prefs.getString(KEY_RIDER_NAME, "").orEmpty()
        }

        if (token.isBlank() || !prefs.getBoolean(KEY_ENABLED, false)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val fineGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarseGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            stopTrackingAndSelf(clearCredentials = false)
            return
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    UPDATE_INTERVAL_MS,
                    MIN_DISTANCE_M,
                    this,
                    Looper.getMainLooper()
                )
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                    uploadLocation(it, force = true)
                }
            }
        } catch (_: Throwable) {
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    UPDATE_INTERVAL_MS,
                    MIN_DISTANCE_M,
                    this,
                    Looper.getMainLooper()
                )
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let {
                    uploadLocation(it, force = true)
                }
            }
        } catch (_: Throwable) {
        }
    }

    override fun onLocationChanged(location: Location) {
        uploadLocation(location)
    }

    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    private fun uploadLocation(location: Location, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUploadAt < 15_000L) return
        lastUploadAt = now
        val session = token
        if (session.isBlank()) return

        thread(name = "bora-background-gps-upload") {
            var connection: HttpURLConnection? = null
            try {
                val payload = JSONObject()
                    .put("action", "update_location")
                    .put("latitude", location.latitude)
                    .put("longitude", location.longitude)
                    .put("accuracy", location.accuracy.toDouble())
                    .put(
                        "speed",
                        if (location.hasSpeed()) location.speed.toDouble() else JSONObject.NULL
                    )
                    .put(
                        "heading",
                        if (location.hasBearing()) location.bearing.toDouble() else JSONObject.NULL
                    )
                    .toString()

                connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    doOutput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $session")
                }
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                }
                val status = connection.responseCode
                if (status == 401 || status == 403) {
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_ENABLED, false)
                        .apply()
                    stopSelf()
                } else {
                    try {
                        connection.inputStream.close()
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
                // Sem internet: a próxima atualização tenta novamente.
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, LauncherActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RiderLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val name = riderName.ifBlank { "Motoboy" }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_inventory)
            .setContentTitle("GPS Bora Michael ativo")
            .setContentText("$name • localização sendo compartilhada com o Painel TESTE")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Parar GPS", stopPending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GPS do motoboy",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantém a localização do motoboy ativa em segundo plano."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun stopTrackingAndSelf(clearCredentials: Boolean = true) {
        try {
            locationManager.removeUpdates(this)
        } catch (_: Throwable) {
        }
        if (clearCredentials) {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        try {
            locationManager.removeUpdates(this)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
