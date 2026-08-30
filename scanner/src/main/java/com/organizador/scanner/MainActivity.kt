package com.organizador.scanner

import android.annotation.SuppressLint
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity

private const val APP_URL = "https://bora-michael-hi-hi.vercel.app/?app=motoboy"
private const val BRAND_JS = """
(function(){
  const apply=function(){
    document.title='Bora Michael Hi Hi';
    const root=document.body||document.documentElement;
    if(!root)return;

    const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);
    let n;
    while((n=walker.nextNode())){
      if(n.nodeValue&&n.nodeValue.includes('Bora Maycon Hi Hi')){
        n.nodeValue=n.nodeValue.replaceAll('Bora Maycon Hi Hi','Bora Michael Hi Hi');
      }
    }

    if(!document.getElementById('bora-michael-polish')){
      const s=document.createElement('style');
      s.id='bora-michael-polish';
      s.textContent=`
        html,body{background:#0d0f12!important;}
        body{color:#f8fafc!important;}
        button{border-radius:18px!important;}
        [class*="card"],[class*="Card"]{border-radius:18px!important;}
      `;
      document.head.appendChild(s);
    }

    const labels=['ESTA SEMANA','ESTE MÊS'];
    document.querySelectorAll('body *').forEach(el=>{
      const txt=(el.textContent||'').trim().toUpperCase();
      if(labels.includes(txt)){
        let p=el;
        for(let i=0;i<4 && p.parentElement;i++){
          p=p.parentElement;
          const t=(p.textContent||'').trim().toUpperCase();
          if(t.includes(txt) && t.length<120){
            p.style.display='none';
          }
        }
      }
    });
  };
  apply();
  if(!window.__boraMichaelObserver){
    window.__boraMichaelObserver=new MutationObserver(apply);
    window.__boraMichaelObserver.observe(document.documentElement,{subtree:true,childList:true,characterData:true});
  }
})();
"""

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var heeHeePlayer: MediaPlayer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val density = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(13, 15, 18))
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(13, 15, 18))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = settings.userAgentString + " BoraMichaelHiHi/2.2.0"
            isVerticalScrollBarEnabled = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    view.evaluateJavascript(BRAND_JS, null)
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

        val stickerWidth = (58 * density).toInt()
        val stickerHeight = (87 * density).toInt()
        val sideMargin = (10 * density).toInt()
        val bottomMargin = (84 * density).toInt()

        val sticker = ImageView(this).apply {
            setImageResource(R.drawable.michael_sticker)
            scaleType = ImageView.ScaleType.FIT_CENTER
            elevation = 18 * density
            contentDescription = "Bordão Hee Hee"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                heeHeePlayer?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                        player.seekTo(0)
                    } else {
                        player.seekTo(0)
                    }
                    player.start()
                }
                animate()
                    .scaleX(1.16f)
                    .scaleY(1.16f)
                    .rotation(-5f)
                    .setDuration(110)
                    .withEndAction {
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(0f)
                            .setDuration(140)
                            .start()
                    }
                    .start()
            }
        }

        root.addView(
            sticker,
            FrameLayout.LayoutParams(stickerWidth, stickerHeight, Gravity.END or Gravity.BOTTOM).apply {
                marginEnd = sideMargin
                bottomMargin = bottomMargin
            }
        )

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        setContentView(root)
        webView.loadUrl(APP_URL)
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
            webView.destroy()
        }
        super.onDestroy()
    }
}
