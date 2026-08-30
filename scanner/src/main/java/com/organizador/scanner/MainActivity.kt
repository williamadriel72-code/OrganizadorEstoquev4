package com.organizador.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity

private const val APP_URL = "https://bora-michael-hi-hi.vercel.app/?app=motoboy"
private const val CHANNEL_ID = "bora_michael_updates_v25"

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var sticker: ImageView
    private var heeHeePlayer: MediaPlayer? = null
    private var foreground = true
    private var lastNotifyAt = 0L
    private var fallbackLoaded = false
    private var pageReady = false

    inner class BoraBridge {
        @JavascriptInterface
        fun appReady() {
            pageReady = true
            runOnUiThread {
                if (::sticker.isInitialized) sticker.visibility = View.VISIBLE
            }
        }

        @JavascriptInterface
        fun notifyUpdate(payload: String?) {
            val now = System.currentTimeMillis()
            if (now - lastNotifyAt < 900) return
            lastNotifyAt = now
            runOnUiThread {
                if (foreground) playHeeHee() else showUpdateNotification()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()

        val density = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(12, 14, 16))
            setOnApplyWindowInsetsListener { view, insets ->
                val top: Int
                val bottom: Int
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val safe = insets.getInsets(
                        WindowInsets.Type.statusBars() or
                            WindowInsets.Type.navigationBars() or
                            WindowInsets.Type.displayCutout()
                    )
                    top = safe.top
                    bottom = safe.bottom
                } else {
                    @Suppress("DEPRECATION")
                    top = insets.systemWindowInsetTop
                    @Suppress("DEPRECATION")
                    bottom = insets.systemWindowInsetBottom
                }
                if (view.paddingTop != top || view.paddingBottom != bottom) {
                    view.setPadding(0, top, 0, bottom)
                }
                insets
            }
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(12, 14, 16))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = settings.userAgentString + " BoraMichaelHiHi/2.6.0"
            isVerticalScrollBarEnabled = false
            webChromeClient = WebChromeClient()
            addJavascriptInterface(BoraBridge(), "AndroidBora")
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    pageReady = false
                    if (::sticker.isInitialized) sticker.visibility = View.GONE
                    super.onPageStarted(view, url, favicon)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true && !pageReady) loadFallback()
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400 && !pageReady) {
                        loadFallback()
                    }
                }
            }
        }

        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        heeHeePlayer = createHeeHeePlayer()

        val stickerWidth = (36 * density).toInt()
        val stickerHeight = (54 * density).toInt()
        val sideMargin = (8 * density).toInt()
        val bottomMarginPx = (76 * density).toInt()

        sticker = ImageView(this).apply {
            setImageResource(R.drawable.michael_sticker)
            scaleType = ImageView.ScaleType.FIT_CENTER
            elevation = 14 * density
            contentDescription = "Bordão Hee Hee"
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener {
                playHeeHee()
                animate()
                    .scaleX(1.12f)
                    .scaleY(1.12f)
                    .rotation(-4f)
                    .setDuration(90)
                    .withEndAction {
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(0f)
                            .setDuration(120)
                            .start()
                    }
                    .start()
            }
        }

        root.addView(
            sticker,
            FrameLayout.LayoutParams(stickerWidth, stickerHeight, Gravity.END or Gravity.BOTTOM).apply {
                marginEnd = sideMargin
                bottomMargin = bottomMarginPx
            }
        )

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        setContentView(root)
        root.requestApplyInsets()
        loadLatestOnline()
    }

    private fun loadLatestOnline() {
        fallbackLoaded = false
        pageReady = false
        if (::sticker.isInitialized) sticker.visibility = View.GONE
        val url = "$APP_URL&shell=stock&v=${System.currentTimeMillis()}"
        webView.loadUrl(url)
    }

    private fun loadFallback() {
        if (fallbackLoaded || !::webView.isInitialized) return
        fallbackLoaded = true
        runOnUiThread {
            try {
                val html = assets.open("bora_fallback.html").bufferedReader().use { it.readText() }
                webView.loadDataWithBaseURL(
                    "https://bora-michael-hi-hi.vercel.app/?app=motoboy&fallback=1",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            } catch (_: Exception) {
                webView.loadData(
                    "<html><body style='background:#0c0e10;color:white;font-family:sans-serif;padding:30px'><h3>Não foi possível abrir o sistema.</h3><p>Verifique sua internet e tente novamente.</p><button onclick='location.reload()'>Tentar novamente</button></body></html>",
                    "text/html",
                    "UTF-8"
                )
            }
        }
    }

    private fun createHeeHeePlayer(): MediaPlayer? = try {
        MediaPlayer.create(this, R.raw.heehee)?.apply {
            setVolume(1f, 1f)
        }
    } catch (_: Exception) {
        null
    }

    private fun playHeeHee() {
        try {
            var player = heeHeePlayer
            if (player == null) {
                player = createHeeHeePlayer()
                heeHeePlayer = player
            }
            player ?: return
            if (player.isPlaying) player.pause()
            player.seekTo(0)
            player.start()
        } catch (_: Exception) {
            try {
                heeHeePlayer?.release()
            } catch (_: Exception) {
            }
            heeHeePlayer = createHeeHeePlayer()
            try {
                heeHeePlayer?.start()
            } catch (_: Exception) {
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val soundUri = Uri.parse("android.resource://$packageName/${R.raw.heehee}")
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Atualizações do motoboy",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos de novas entregas e alterações"
            setSound(soundUri, attrs)
            enableVibration(true)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2501)
        }
    }

    private fun showUpdateNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            2501,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
                .setSound(Uri.parse("android.resource://$packageName/${R.raw.heehee}"))
                .setPriority(android.app.Notification.PRIORITY_HIGH)
        }
        val notification = builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Bora Michael Hi Hi")
            .setContentText("Sua entrega foi atualizada.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(2501, notification)
    }

    override fun onStart() {
        super.onStart()
        foreground = true
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
            webView.evaluateJavascript(
                "try{window.dispatchEvent(new Event('focus'));window.dispatchEvent(new Event('online'));}catch(e){}",
                null
            )
        }
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onStop() {
        foreground = false
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        try {
            heeHeePlayer?.release()
        } catch (_: Exception) {
        }
        heeHeePlayer = null
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.removeJavascriptInterface("AndroidBora")
            webView.destroy()
        }
        super.onDestroy()
    }
}
