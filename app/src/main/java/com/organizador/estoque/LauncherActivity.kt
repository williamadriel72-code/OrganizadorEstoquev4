package com.organizador.estoque

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONObject

class LauncherActivity : ComponentActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    private inner class AndroidBridge {
        @JavascriptInterface
        fun scanBarcode() {
            runOnUiThread {
                val scanner = GmsBarcodeScanning.getClient(this@LauncherActivity)
                scanner.startScan()
                    .addOnSuccessListener { barcode ->
                        val raw = barcode.rawValue.orEmpty()
                        if (raw.isNotBlank()) {
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
        fun exitApp() {
            runOnUiThread { finish() }
        }
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("AndroidApp")
        webView.destroy()
        super.onDestroy()
    }
}
