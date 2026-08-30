package com.organizador.scanner

import android.annotation.SuppressLint
import android.graphics.Color
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(16, 18, 20))
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(16, 18, 20))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = settings.userAgentString + " BoraMichaelHiHi/2.1.1"
            isVerticalScrollBarEnabled = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    view.evaluateJavascript(BRAND_JS, null)
                }
            }
        }
        root.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val logoSize = (88 * resources.displayMetrics.density).toInt()
        val margin = (16 * resources.displayMetrics.density).toInt()
        val logo = ImageView(this).apply {
            setImageResource(com.organizador.scanner.R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
            elevation = 14 * resources.displayMetrics.density
            contentDescription = "Bora Michael Hi Hi"
        }
        root.addView(logo, FrameLayout.LayoutParams(logoSize, logoSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = margin
        })

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
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
