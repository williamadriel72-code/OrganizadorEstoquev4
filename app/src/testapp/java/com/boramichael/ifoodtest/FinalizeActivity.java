package com.boramichael.ifoodtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FinalizeActivity extends Activity {
    private static final String RIDER_API = "https://rlgsbtolosxyymosidns.supabase.co/functions/v1/bora-ifood-test-rider";
    private static final String FINALIZER_URL = "https://share.google/EyC8YMZw1fsOSWcP6";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private TextView status;
    private Button fallback;
    private String orderId;
    private String displayId;
    private String locator;
    private String token;
    private boolean locatorInjected = false;
    private boolean completing = false;

    private final Runnable monitor = new Runnable() {
        @Override public void run() {
            if (web == null || completing) return;
            String js = "(function(){var t=(document.body&&document.body.innerText||'').toLowerCase();return /entrega.{0,20}(conclu[ií]da|finalizada)|pedido.{0,20}(conclu[ií]do|finalizado)|finalizado com sucesso|conclu[ií]do com sucesso/.test(t);})()";
            web.evaluateJavascript(js, value -> {
                if ("true".equalsIgnoreCase(String.valueOf(value))) {
                    status.setText("Finalização confirmada no iFood. Atualizando painel...");
                    markComplete();
                }
            });
            handler.postDelayed(this, 900);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        orderId = getIntent().getStringExtra("order_id");
        displayId = getIntent().getStringExtra("display_id");
        locator = getIntent().getStringExtra("locator");
        token = getIntent().getStringExtra("access_token");
        if (orderId == null) orderId = "";
        if (displayId == null) displayId = "";
        if (locator == null) locator = "";
        if (token == null) token = "";
        buildUi();
        openFinalizer();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0F14"));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(14), dp(10), dp(14), dp(9));
        top.setBackgroundColor(Color.parseColor("#111820"));

        TextView title = text("Finalizar pedido #" + (displayId.isEmpty() ? "—" : displayId), 18, Color.WHITE, true);
        TextView badge = text("AMBIENTE DE TESTE • localizador automático", 11, Color.parseColor("#86EFAC"), true);
        top.addView(title);
        top.addView(badge);
        root.addView(top, full());

        status = text(locator.matches("\\d{8}") ? "Abrindo iFood e preenchendo localizador " + locator + "..." : "Abrindo iFood. Localizador não disponível automaticamente.", 12, Color.parseColor("#CBD5E1"), false);
        status.setPadding(dp(14), dp(8), dp(14), dp(8));
        root.addView(status, full());

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!locatorInjected && locator.matches("\\d{8}")) {
                    locatorInjected = true;
                    handler.postDelayed(() -> injectLocator(locator), 550);
                }
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        fallback = new Button(this);
        fallback.setText("CONFIRMAR CONCLUÍDO (APÓS FINALIZAR NO IFOOD)");
        fallback.setAllCaps(false);
        fallback.setTextColor(Color.WHITE);
        fallback.setTextSize(11);
        fallback.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        fallback.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#166534")));
        fallback.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Confirmar conclusão")
                .setMessage("Use esta opção somente se o iFood já confirmou a entrega, mas o retorno automático não foi detectado.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Já foi concluído", (d, w) -> markComplete())
                .show());
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        fp.setMargins(dp(10), dp(7), dp(10), dp(10));
        root.addView(fallback, fp);

        setContentView(root);
    }

    private void openFinalizer() {
        web.loadUrl(FINALIZER_URL);
        handler.postDelayed(monitor, 1200);
    }

    private void injectLocator(String val) {
        String q = JSONObject.quote(val);
        String js = "(function(){var val=" + q + ";" +
                "function vis(e){var r=e.getBoundingClientRect();var s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!='hidden'&&s.display!='none'&&!e.disabled;}" +
                "function setv(el,v){try{var p=el.tagName=='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;var d=Object.getOwnPropertyDescriptor(p,'value');if(d&&d.set)d.set.call(el,v);else el.value=v;}catch(e){el.value=v;}try{el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:v}));}catch(e){el.dispatchEvent(new Event('input',{bubbles:true}));}el.dispatchEvent(new Event('change',{bubbles:true}));}" +
                "function go(){var all=[].slice.call(document.querySelectorAll('input,textarea')).filter(vis);var seg=all.filter(function(e){var r=e.getBoundingClientRect();return e.maxLength==1||(e.getAttribute('inputmode')=='numeric'&&r.width<100);});" +
                "if(seg.length>=8){for(let i=0;i<8;i++){setTimeout(function(){var cur=[].slice.call(document.querySelectorAll('input,textarea')).filter(vis).filter(function(e){var r=e.getBoundingClientRect();return e.maxLength==1||(e.getAttribute('inputmode')=='numeric'&&r.width<100);});if(cur[i]){cur[i].focus();setv(cur[i],val.charAt(i));}},i*85);}}" +
                "else{var one=all.find(function(e){var tag=((e.name||'')+' '+(e.id||'')+' '+(e.placeholder||'')+' '+(e.getAttribute('aria-label')||'')).toLowerCase();return e.maxLength==8||/localizador/.test(tag);})||all[0];if(one){one.focus();setv(one,val);}}" +
                "setTimeout(function(){var bs=[].slice.call(document.querySelectorAll('button,input[type=submit],[role=button]')).filter(vis);var b=bs.find(function(x){var t=(x.innerText||x.value||x.getAttribute('aria-label')||'').trim();return /continuar|avancar|avan[cç]ar|prosseguir|proseguir/i.test(t);});if(b&&!b.disabled&&b.getAttribute('aria-disabled')!='true')b.click();},1050);return true;}" +
                "if(document.readyState==='complete'||document.readyState==='interactive')return go();setTimeout(go,300);return true;})()";
        web.evaluateJavascript(js, value -> status.setText("Localizador preenchido. Digite apenas o código de 4 números do cliente."));
    }

    private void markComplete() {
        if (completing) return;
        completing = true;
        fallback.setEnabled(false);
        status.setText("Marcando pedido como concluído no painel de teste...");
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("action", "complete_order").put("orderId", orderId);
                postJson(RIDER_API, token, body);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Pedido concluído e sincronizado.", Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception e) {
                completing = false;
                runOnUiThread(() -> {
                    fallback.setEnabled(true);
                    status.setText(e.getMessage() == null ? "Não foi possível atualizar o painel." : e.getMessage());
                });
            }
        });
    }

    private JSONObject postJson(String urlText, String bearer, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        if (bearer != null && !bearer.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + bearer);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String txt = readAll(in);
        JSONObject j = txt.isEmpty() ? new JSONObject() : new JSONObject(txt);
        if (code < 200 || code >= 300) throw new Exception(j.optString("error", "HTTP " + code));
        return j;
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) b.write(buf, 0, n);
        in.close();
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(monitor);
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        io.shutdownNow();
        super.onDestroy();
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
