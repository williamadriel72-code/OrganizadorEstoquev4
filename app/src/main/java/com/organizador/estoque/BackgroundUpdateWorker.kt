package com.organizador.estoque

import android.app.DevicePolicyManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object UpdateScheduler {
    private const val PERIODIC_NAME = "aws_server_update_watch"
    private const val IMMEDIATE_NAME = "aws_server_update_check_now"

    fun ensureScheduled(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodic = PeriodicWorkRequestBuilder<BackgroundUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )

        val immediate = OneTimeWorkRequestBuilder<BackgroundUpdateWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_NAME,
            ExistingWorkPolicy.REPLACE,
            immediate
        )
    }
}

class BackgroundUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    data class RemoteVersion(
        val code: Int,
        val name: String,
        val url: String,
        val mandatory: Boolean,
        val notes: String?
    )

    override fun doWork(): Result {
        return try {
            val remote = fetchLatestVersion() ?: return Result.success()
            if (remote.code <= BuildConfig.VERSION_CODE) return Result.success()

            val apk = downloadAndValidate(remote)
            if (canInstallSilentlyOrPrompt()) {
                commitInstall(apk)
            } else {
                showPermissionNotification(remote)
            }
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    private fun fetchLatestVersion(): RemoteVersion? {
        val connection = (URL(LATEST_VERSION_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 18_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("apikey", SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("User-Agent", "AWS-Gestao-Estoque/${BuildConfig.VERSION_NAME}")
            useCaches = false
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Servidor de versão respondeu HTTP $status")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            if (!json.has("version_code")) return null
            val url = json.optString("download_url").trim()
            if (!url.startsWith("https://")) throw IOException("URL de atualização inválida")
            RemoteVersion(
                code = json.getInt("version_code"),
                name = json.optString("version_name", json.getInt("version_code").toString()),
                url = url,
                mandatory = json.optBoolean("obrigatoria", false),
                notes = json.optString("notas").takeIf { it.isNotBlank() }
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadAndValidate(remote: RemoteVersion): File {
        val dir = File(applicationContext.filesDir, "updates").apply { mkdirs() }
        val safe = remote.name.replace(Regex("[^0-9A-Za-z._-]+"), "-")
        val apk = File(dir, "AWS-Gestao-Estoque-$safe.apk")
        if (apk.exists()) {
            runCatching { validateDownloadedApk(apk, remote.code) }
                .onSuccess { return apk }
            apk.delete()
        }

        dir.listFiles()?.filter { it != apk }?.forEach { it.delete() }
        val partial = File(dir, "AWS-Gestao-Estoque-$safe.part")
        if (partial.exists()) partial.delete()

        var connection: HttpURLConnection? = null
        try {
            connection = openDownloadConnection(remote.url)
            val expected = connection.contentLengthLong.coerceAtLeast(0L)
            var downloaded = 0L
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                    }
                    output.flush()
                }
            }
            if (downloaded <= 0L) throw IOException("Download vazio")
            if (expected > 0L && downloaded != expected) {
                throw IOException("Download incompleto: $downloaded de $expected bytes")
            }
            if (apk.exists()) apk.delete()
            if (!partial.renameTo(apk)) {
                partial.copyTo(apk, overwrite = true)
                partial.delete()
            }
            validateDownloadedApk(apk, remote.code)
            return apk
        } finally {
            connection?.disconnect()
        }
    }

    private fun openDownloadConnection(initialUrl: String): HttpURLConnection {
        var current = initialUrl
        repeat(8) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 45_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "AWS-Gestao-Estoque/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
                useCaches = false
            }
            val status = connection.responseCode
            if (status in 200..299) return connection
            if (status in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                    ?: throw IOException("Redirecionamento sem destino")
                val next = URL(URL(current), location).toString()
                connection.disconnect()
                current = next
            } else {
                connection.disconnect()
                throw IOException("Download respondeu HTTP $status")
            }
        }
        throw IOException("Redirecionamentos demais")
    }

    private fun validateDownloadedApk(file: File, expectedVersion: Int) {
        val pm = applicationContext.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IOException("Arquivo não é um APK válido")
        if (archive.packageName != applicationContext.packageName) {
            throw IOException("APK pertence a outro aplicativo")
        }
        val archiveCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION") archive.versionCode.toLong()
        }
        if (archiveCode < expectedVersion || archiveCode <= BuildConfig.VERSION_CODE.toLong()) {
            throw IOException("Versão do APK baixado não é mais nova")
        }

        val installed = pm.getPackageInfo(applicationContext.packageName, flags)
        val installedSigners = signatureFingerprints(installed)
        val archiveSigners = signatureFingerprints(archive)
        if (installedSigners.isEmpty() || archiveSigners.isEmpty() || installedSigners != archiveSigners) {
            throw IOException("Assinatura da atualização diferente da instalada")
        }
    }

    private fun signatureFingerprints(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION") info.signatures ?: return emptySet()
        }
        return signatures.map { sig ->
            MessageDigest.getInstance("SHA-256")
                .digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun isDeviceOwner(): Boolean {
        val dpm = applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(applicationContext.packageName)
    }

    private fun canInstallSilentlyOrPrompt(): Boolean {
        if (isDeviceOwner()) return true
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || applicationContext.packageManager.canRequestPackageInstalls()
    }

    private fun commitInstall(file: File) {
        val installer = applicationContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(applicationContext.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(
                    if (isDeviceOwner()) PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                    else PackageInstaller.SessionParams.USER_ACTION_REQUIRED
                )
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("base.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val statusIntent = Intent(applicationContext, UpdateInstallReceiver::class.java).apply {
                action = "${applicationContext.packageName}.UPDATE_INSTALL_STATUS"
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(applicationContext, sessionId, statusIntent, flags)
            session.commit(pending.intentSender)
        }
    }

    private fun showPermissionNotification(remote: RemoteVersion) {
        ensureChannel()
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${applicationContext.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            applicationContext,
            9103,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_inventory)
            .setContentTitle("Atualização ${remote.name} pronta")
            .setContentText("Toque para autorizar a instalação. O APK já foi baixado.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .build()
        try {
            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Atualizações do aplicativo", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    companion object {
        private const val LATEST_VERSION_URL = "https://rlgsbtolosxyymosidns.supabase.co/functions/v1/latest-app-version"
        private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_cdYfnl879c7gh4WQE27S5g_CxEtVxde"
        const val CHANNEL_ID = "aws_app_updates"
        const val NOTIFICATION_ID = 51120
    }
}
