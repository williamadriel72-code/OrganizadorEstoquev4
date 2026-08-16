package com.organizador.estoque

import android.annotation.SuppressLint
import android.content.Intent
import android.database.Cursor
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.json.JSONObject
import kotlin.concurrent.thread

class LauncherActivity : ComponentActivity() {
    private lateinit var webView: WebView

    private var bipSoundPool: SoundPool? = null
    private var bipSoundId: Int = 0
    @Volatile private var bipSoundReady = false

    @Volatile
    private var pendingImport: ParsedPdfImport? = null

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) parseSelectedPdf(uri) else notifyPdfError("Seleção de PDF cancelada.")
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
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
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
        fun openUpdateUrl(url: String) {
            runOnUiThread {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Throwable) {
                    webView.evaluateJavascript(
                        "window.onNativeUpdateError && window.onNativeUpdateError('Não foi possível abrir o download da atualização.');",
                        null
                    )
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
        super.onDestroy()
    }
}
