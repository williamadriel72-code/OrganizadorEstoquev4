package com.organizador.estoque

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

class LauncherActivity : ComponentActivity() {
    private lateinit var webView: WebView

    private var bipSoundPool: SoundPool? = null
    private var bipSoundId: Int = 0
    @Volatile private var bipSoundReady = false

    @Volatile private var pendingImport: ParsedPdfImport? = null
    @Volatile private var updateDownloadRunning = false
    private var pendingInstallFile: File? = null
    private var awaitingInstallPermission = false

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) parseSelectedPdf(uri) else notifyPdfError("Seleção de PDF cancelada.")
    }

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null
    private val cameraBatchUris = mutableListOf<Uri>()
    private var cameraBatchMode = false

    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val allowed =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val origin = pendingGeoOrigin
            val callback = pendingGeoCallback
            pendingGeoOrigin = null
            pendingGeoCallback = null
            if (origin != null && callback != null) {
                callback.invoke(origin, allowed, false)
            }
        }

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileChooserCallback ?: return@registerForActivityResult

        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            when {
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    completeFileChooser(Array(clip.itemCount) { i -> clip.getItemAt(i).uri })
                }
                data?.data != null -> {
                    completeFileChooser(arrayOf(data.data!!))
                }
                cameraBatchMode && cameraPhotoUri != null -> {
                    cameraBatchUris.add(cameraPhotoUri!!)
                    cameraPhotoUri = null
                    showCameraBatchDialog()
                }
                cameraPhotoUri != null -> {
                    completeFileChooser(arrayOf(cameraPhotoUri!!))
                }
                else -> completeFileChooser(null)
            }
        } else {
            if (cameraBatchMode && cameraBatchUris.isNotEmpty()) {
                completeFileChooser(cameraBatchUris.toTypedArray())
            } else {
                completeFileChooser(null)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        prepareBipSound()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.setGeolocationEnabled(true)
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (filePathCallback == null) return false
                    return launchImageChooser(filePathCallback, fileChooserParams)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    if (origin.isNullOrBlank() || callback == null) return

                    val fineGranted =
                        ContextCompat.checkSelfPermission(
                            this@LauncherActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    val coarseGranted =
                        ContextCompat.checkSelfPermission(
                            this@LauncherActivity,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                    if (fineGranted || coarseGranted) {
                        callback.invoke(origin, true, false)
                        return
                    }

                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
            addJavascriptInterface(AndroidBridge(), "AndroidApp")
            loadUrl("file:///android_asset/index.html")
        }

        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("window.androidBack && window.androidBack();", null)
            }
        })
    }

    private fun launchImageChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams?
    ): Boolean {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = callback
        cameraBatchUris.clear()
        cameraBatchMode = false
        cameraPhotoUri = null

        if (params?.isCaptureEnabled == true) {
            cameraBatchMode = true
            return launchNextCameraCapture()
        }

        val contentIntent = try {
            params?.createIntent()?.apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            } ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        } catch (_: Throwable) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }

        val chooser = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, contentIntent)
            putExtra(Intent.EXTRA_TITLE, "Selecione várias fotos das comandas")
        }

        return try {
            fileChooserLauncher.launch(chooser)
            true
        } catch (_: Throwable) {
            completeFileChooser(null)
            false
        }
    }

    private fun launchNextCameraCapture(): Boolean {
        val callback = fileChooserCallback ?: return false
        val cameraDir = File(cacheDir, "camera").apply { mkdirs() }
        val photoFile = File(
            cameraDir,
            "bora-michael-${System.currentTimeMillis()}-${cameraBatchUris.size + 1}.jpg"
        )
        cameraPhotoUri = FileProvider.getUriForFile(this, "$packageName.updates", photoFile)

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        return try {
            if (cameraIntent.resolveActivity(packageManager) == null) {
                callback.onReceiveValue(null)
                fileChooserCallback = null
                cameraPhotoUri = null
                cameraBatchMode = false
                false
            } else {
                fileChooserLauncher.launch(cameraIntent)
                true
            }
        } catch (_: Throwable) {
            completeFileChooser(if (cameraBatchUris.isEmpty()) null else cameraBatchUris.toTypedArray())
            false
        }
    }

    private fun showCameraBatchDialog() {
        if (fileChooserCallback == null) return
        AlertDialog.Builder(this)
            .setTitle("${cameraBatchUris.size} foto(s) tirada(s)")
            .setMessage("Quer tirar outra comanda ou concluir as fotos?")
            .setPositiveButton("Tirar outra") { _, _ ->
                launchNextCameraCapture()
            }
            .setNegativeButton("Concluir") { _, _ ->
                completeFileChooser(cameraBatchUris.toTypedArray())
            }
            .setNeutralButton("Cancelar tudo") { _, _ ->
                cameraBatchUris.clear()
                completeFileChooser(null)
            }
            .setCancelable(false)
            .show()
    }

    private fun completeFileChooser(uris: Array<Uri>?) {
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
        cameraPhotoUri = null
        cameraBatchMode = false
        cameraBatchUris.clear()
    }

    override fun onResume() {
        super.onResume()
        if (awaitingInstallPermission && canInstallPackages()) {
            awaitingInstallPermission = false
            pendingInstallFile?.takeIf { it.exists() }?.let { file ->
                runOnUiThread { launchInstaller(file) }
            }
        }
    }

    private fun prepareBipSound() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        bipSoundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attributes)
            .build()
            .also { pool ->
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (sampleId == bipSoundId && status == 0) bipSoundReady = true
                }
                bipSoundId = pool.load(this, R.raw.scan_funny, 1)
            }
    }

    private fun playBipSound() {
        val pool = bipSoundPool ?: return
        if (!bipSoundReady || bipSoundId == 0) return
        pool.play(bipSoundId, 1f, 1f, 1, 0, 1f)
    }

    private fun parseSelectedPdf(uri: Uri) {
        pendingImport = null
        val name = getDisplayName(uri) ?: "arquivo.pdf"

        thread(name = "pdf-import-parser") {
            try {
                val parsed = PdfImportParser.parse(this, uri, name) { page, total ->
                    runOnUiThread {
                        webView.evaluateJavascript(
                            "window.onPdfImportProgress && window.onPdfImportProgress($page,$total);",
                            null
                        )
                    }
                }
                pendingImport = parsed
                val summary = parsed.summaryJson().toString()
                runOnUiThread {
                    webView.evaluateJavascript(
                        "window.onPdfImportReady && window.onPdfImportReady($summary);",
                        null
                    )
                }
            } catch (t: Throwable) {
                notifyPdfError(t.message ?: "Não foi possível ler o PDF.")
            }
        }
    }

    private fun notifyPdfError(message: String) {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.onPdfImportError && window.onPdfImportError(${JSONObject.quote(message)});",
                null
            )
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        } finally {
            cursor?.close()
        }
    }

    private fun startInternalUpdate(url: String, versionName: String) {
        if (updateDownloadRunning) {
            notifyUpdateError("Uma atualização já está sendo baixada.")
            return
        }
        if (!url.startsWith("https://")) {
            notifyUpdateError("Endereço de atualização inválido.")
            return
        }

        updateDownloadRunning = true
        pendingInstallFile = null
        awaitingInstallPermission = false
        notifyUpdateProgress(0, 0, 0, "Preparando atualização...")

        thread(name = "apk-update-download") {
            var connection: HttpURLConnection? = null
            try {
                val updateDir = File(cacheDir, "updates").apply { mkdirs() }
                updateDir.listFiles()?.forEach { it.delete() }

                val safeVersion = versionName.replace(Regex("[^0-9A-Za-z._-]+"), "-").ifBlank { "nova" }
                val tempFile = File(updateDir, "update-$safeVersion.part")
                val apkFile = File(updateDir, "AWS-Gestao-Estoque-$safeVersion.apk")

                connection = openUpdateConnection(url)
                val total = connection.contentLengthLong.coerceAtLeast(0L)
                var downloaded = 0L
                var lastReport = 0L

                connection.inputStream.use { input ->
                    tempFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastReport >= 180L) {
                                val percent = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else -1
                                notifyUpdateProgress(percent, downloaded, total, "Baixando atualização...")
                                lastReport = now
                            }
                        }
                        output.flush()
                    }
                }

                if (downloaded <= 0L) throw IOException("O download terminou sem receber o APK.")
                if (total > 0L && downloaded != total) {
                    throw IOException("Download incompleto: ${downloaded} de ${total} bytes.")
                }

                if (apkFile.exists()) apkFile.delete()
                if (!tempFile.renameTo(apkFile)) {
                    tempFile.copyTo(apkFile, overwrite = true)
                    tempFile.delete()
                }

                notifyUpdateProgress(100, downloaded, total, "Verificando APK...")
                validateDownloadedApk(apkFile)

                pendingInstallFile = apkFile
                updateDownloadRunning = false
                notifyUpdateReady()
                runOnUiThread { requestInstallOrLaunch(apkFile) }
            } catch (t: Throwable) {
                updateDownloadRunning = false
                notifyUpdateError(t.message ?: "Falha ao baixar a atualização.")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun openUpdateConnection(initialUrl: String): HttpURLConnection {
        var current = initialUrl
        repeat(7) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 35_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "AWS-Gestao-Estoque/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
                useCaches = false
            }
            val code = connection.responseCode
            if (code in 200..299) return connection
            if (code in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                    ?: throw IOException("Redirecionamento de atualização sem destino.")
                val next = URL(URL(current), location).toString()
                connection.disconnect()
                current = next
            } else {
                connection.disconnect()
                throw IOException("Servidor da atualização respondeu HTTP $code.")
            }
        }
        throw IOException("A atualização teve redirecionamentos demais.")
    }

    private fun validateDownloadedApk(file: File) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IOException("O arquivo baixado não é um APK Android válido.")

        if (archive.packageName != packageName) {
            throw IOException("O APK baixado não pertence a este aplicativo.")
        }

        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archive.versionCode.toLong()
        }
        if (archiveVersion <= BuildConfig.VERSION_CODE.toLong()) {
            throw IOException("A versão baixada não é mais nova que a instalada.")
        }

        val installed = packageManager.getPackageInfo(packageName, flags)
        val installedSigners = signatureFingerprints(installed)
        val archiveSigners = signatureFingerprints(archive)
        if (installedSigners.isEmpty() || archiveSigners.isEmpty() || installedSigners != archiveSigners) {
            throw IOException("Assinatura do APK diferente da versão instalada. Atualização bloqueada por segurança.")
        }
    }

    private fun signatureFingerprints(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: return emptySet()
        }
        return signatures.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    private fun requestInstallOrLaunch(file: File) {
        if (!file.exists()) {
            notifyUpdateError("O APK baixado não foi encontrado para instalar.")
            return
        }

        if (!canInstallPackages()) {
            awaitingInstallPermission = true
            pendingInstallFile = file
            notifyUpdatePermissionRequired()
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (t: Throwable) {
                awaitingInstallPermission = false
                notifyUpdateError("Não foi possível abrir a permissão para instalar a atualização.")
            }
            return
        }

        launchInstaller(file)
    }

    private fun launchInstaller(file: File) {
        awaitingInstallPermission = false
        pendingInstallFile = null
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.updates", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (t: Throwable) {
            notifyUpdateError("APK baixado, mas não foi possível abrir o instalador: ${t.message ?: "erro desconhecido"}")
        }
    }

    private fun notifyUpdateProgress(percent: Int, downloaded: Long, total: Long, state: String) {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.onNativeUpdateProgress && window.onNativeUpdateProgress($percent,$downloaded,$total,${JSONObject.quote(state)});",
                null
            )
        }
    }

    private fun notifyUpdateReady() {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.onNativeUpdateReady && window.onNativeUpdateReady();",
                null
            )
        }
    }

    private fun notifyUpdatePermissionRequired() {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.onNativeUpdatePermissionRequired && window.onNativeUpdatePermissionRequired();",
                null
            )
        }
    }

    private fun notifyUpdateError(message: String) {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.onNativeUpdateError && window.onNativeUpdateError(${JSONObject.quote(message)});",
                null
            )
        }
    }

    private inner class AndroidBridge {
        @JavascriptInterface
        fun scanBarcode() {
            runOnUiThread {
                val scanner = GmsBarcodeScanning.getClient(this@LauncherActivity)
                scanner.startScan()
                    .addOnSuccessListener { barcode ->
                        val raw = barcode.rawValue.orEmpty()
                        if (raw.isNotBlank()) {
                            playBipSound()
                            webView.evaluateJavascript(
                                "window.onNativeBarcode && window.onNativeBarcode(${JSONObject.quote(raw)});",
                                null
                            )
                        }
                    }
                    .addOnCanceledListener {
                        webView.evaluateJavascript(
                            "window.onNativeScannerError && window.onNativeScannerError('Leitura cancelada.');",
                            null
                        )
                    }
                    .addOnFailureListener {
                        webView.evaluateJavascript(
                            "window.onNativeScannerError && window.onNativeScannerError('Não foi possível abrir o leitor de código.');",
                            null
                        )
                    }
            }
        }

        @JavascriptInterface
        fun pickPdfImport() {
            runOnUiThread { pdfPicker.launch(arrayOf("application/pdf")) }
        }

        @JavascriptInterface
        fun getPendingImportBatch(offset: Int, limit: Int): String {
            val parsed = pendingImport ?: return JSONObject()
                .put("items", org.json.JSONArray())
                .put("done", true)
                .put("totalItems", 0)
                .toString()
            return parsed.batchJson(offset, limit).toString()
        }

        @JavascriptInterface
        fun clearPendingImport() {
            pendingImport = null
        }

        @JavascriptInterface
        fun getAppVersionCode(): Int = BuildConfig.VERSION_CODE

        @JavascriptInterface
        fun getAppVersionName(): String = BuildConfig.VERSION_NAME

        @JavascriptInterface
        fun downloadAndInstallUpdate(url: String, versionName: String) {
            startInternalUpdate(url.trim(), versionName.trim())
        }

        @JavascriptInterface
        fun startBackgroundGps(token: String, riderName: String): Boolean {
            if (token.isBlank()) return false
            val fineGranted =
                ContextCompat.checkSelfPermission(
                    this@LauncherActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            val coarseGranted =
                ContextCompat.checkSelfPermission(
                    this@LauncherActivity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            if (!fineGranted && !coarseGranted) return false

            val intent = Intent(this@LauncherActivity, RiderLocationService::class.java).apply {
                action = RiderLocationService.ACTION_START
                putExtra(RiderLocationService.EXTRA_TOKEN, token)
                putExtra(RiderLocationService.EXTRA_RIDER_NAME, riderName)
            }
            ContextCompat.startForegroundService(this@LauncherActivity, intent)
            return true
        }

        @JavascriptInterface
        fun stopBackgroundGps() {
            val intent = Intent(this@LauncherActivity, RiderLocationService::class.java).apply {
                action = RiderLocationService.ACTION_STOP
            }
            startService(intent)
        }

        @JavascriptInterface
        fun openUpdateUrl(url: String) {
            runOnUiThread {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Throwable) {
                    notifyUpdateError("Não foi possível abrir o download da atualização.")
                }
            }
        }

        @JavascriptInterface
        fun exitApp() {
            runOnUiThread { finish() }
        }
    }

    override fun onDestroy() {
        bipSoundPool?.release()
        bipSoundPool = null
        bipSoundReady = false
        webView.removeJavascriptInterface("AndroidApp")
        webView.destroy()
        pendingImport = null
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        cameraPhotoUri = null
        cameraBatchMode = false
        cameraBatchUris.clear()
        pendingGeoCallback?.invoke(pendingGeoOrigin, false, false)
        pendingGeoOrigin = null
        pendingGeoCallback = null
        super.onDestroy()
    }
}
