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
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity

private const val APP_URL = "https://bora-michael-hi-hi.vercel.app/?app=motoboy"
private const val CHANNEL_ID = "bora_michael_updates"

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var sticker: ImageView
    private var heeHeePlayer: MediaPlayer? = null
    private var foreground = true
    private var lastNotifyAt = 0L

    inner class BoraBridge {
        @JavascriptInterface
        fun appReady() {
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
                playHeeHee()
                if (!foreground) showUpdateNotification()
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
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(12, 14, 16))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = settings.userAgentString + " BoraMichaelHiHi/2.3.0"
            isVerticalScrollBarEnabled = false
            webChromeClient = WebChromeClient()
            addJavascriptInterface(BoraBridge(), "AndroidBora")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    // A figurinha continua escondida. A página chama AndroidBora.appReady()
                    // somente depois que a interface real estiver pronta.
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

        heeHeePlayer = MediaPlayer.create(this, R.raw.heehee)

        val stickerWidth = (46 * density).toInt()
        val stickerHeight = (69 * density).toInt()
        val sideMargin = (8 * density).toInt()
        val bottomMarginPx = (78 * density).toInt()

        sticker = ImageView(this).apply {
            setImageResource(R.drawable.michael_sticker)
            scaleType = ImageView.ScaleType.FIT_CENTER
            elevation = 16 * density
            contentDescription = "Bordão Hee Hee"
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener {
                playHeeHee()
                animate()
                    .scaleX(1.14f)
                    .scaleY(1.14f)
                    .rotation(-4f)
                    .setDuration(100)
                    .withEndAction {
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(0f)
                            .setDuration(130)
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
        webView.loadUrl(APP_URL)
    }

    private fun playHeeHee() {
        heeHeePlayer?.let { player ->
            try {
                if (player.isPlaying) player.pause()
                player.seekTo(0)
                player.start()
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
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2301)
        }
    }

    private fun showUpdateNotification() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            2301,
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
            .notify(2301, notification)
    }

    override fun onStart() {
        super.onStart()
        foreground = true
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
        heeHeePlayer?.release()
        heeHeePlayer = null
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.removeJavascriptInterface("AndroidBora")
            webView.destroy()
        }
        super.onDestroy()
    }
}
