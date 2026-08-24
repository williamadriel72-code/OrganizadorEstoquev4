package com.multifigurinhas.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String START_URL = "https://zapsticker.com/catalog";
    private static final int COPY_COUNT = 100;
    private static final int MAX_SOURCE_BYTES = 1024 * 1024;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private WebView webView;
    private ProgressBar progressBar;
    private Button actionButton;
    private TextView statusText;
    private String userAgent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildBrowser();
        webView.loadUrl(START_URL);
    }

    private void buildBrowser() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });

        LinearLayout actionArea = new LinearLayout(this);
        actionArea.setOrientation(LinearLayout.VERTICAL);
        actionArea.setPadding(dp(10), dp(8), dp(10), dp(8));

        statusText = new TextView(this);
        statusText.setText("Abra uma figurinha e toque no botão abaixo");
        statusText.setTextSize(14);
        statusText.setTextColor(Color.DKGRAY);
        statusText.setGravity(Gravity.CENTER);
        actionArea.addView(statusText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        actionButton = new Button(this);
        actionButton.setText("BAIXAR ×100 → WHATSAPP");
        actionButton.setTextColor(Color.WHITE);
        actionButton.setTextSize(15);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            actionButton.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(37, 211, 102)));
        }
        actionButton.setEnabled(true);
        actionButton.setOnClickListener(v -> captureCurrentSticker());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(0, dp(6), 0, 0);
        actionArea.addView(actionButton, buttonParams);

        root.addView(actionArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(4)));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(settings.getUserAgentString() + " MultiFigurinhas/1.4.1");
        userAgent = settings.getUserAgentString();

        webView.addJavascriptInterface(new StickerBridge(), "MultiFigurinhas");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                actionButton.setEnabled(true);
                statusText.setText("Abra uma figurinha e toque em BAIXAR ×100 → WHATSAPP");
            }
        });

        setContentView(root);
        root.requestApplyInsets();
    }

    private boolean handleUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String lower = url.toLowerCase(Locale.ROOT);

        if (lower.startsWith("https://wa.me/")
                || lower.startsWith("https://api.whatsapp.com/")
                || lower.contains("whatsapp.com/send")
                || lower.startsWith("whatsapp://")) {
            openExternal(url);
            return true;
        }

        if (lower.startsWith("intent://")) {
            try {
                Intent parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String fallback = parsed.getStringExtra("browser_fallback_url");
                parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(parsed);
                } catch (ActivityNotFoundException e) {
                    if (fallback != null && !fallback.isEmpty()) {
                        webView.loadUrl(fallback);
                    } else {
                        Toast.makeText(this, "Não foi possível abrir esse link.", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível abrir esse link.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null && (host.equals("zapsticker.com") || host.endsWith(".zapsticker.com"))) {
                return false;
            }
            openExternal(url);
            return true;
        }

        openExternal(url);
        return true;
    }

    private void captureCurrentSticker() {
        actionButton.setEnabled(false);
        statusText.setText("Identificando a figurinha aberta...");

        String js = "(function(){"
                + "var imgs=Array.from(document.images).filter(function(i){if(!i.src)return false;var r=i.getBoundingClientRect();var s=getComputedStyle(i);return r.width>80&&r.height>80&&r.bottom>0&&r.right>0&&r.top<innerHeight&&r.left<innerWidth&&s.display!='none'&&s.visibility!='hidden'&&parseFloat(s.opacity||'1')>0;});"
                + "if(!imgs.length){MultiFigurinhas.onStickerUrl('');return;}"
                + "imgs.sort(function(a,b){var ra=a.getBoundingClientRect(),rb=b.getBoundingClientRect();var aa=ra.width*ra.height,ab=rb.width*rb.height;return ab-aa;});"
                + "var img=imgs[0];"
                + "MultiFigurinhas.onStickerUrl(img.currentSrc||img.src||'');"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    private class StickerBridge {
        @JavascriptInterface
        public void onStickerUrl(String sourceUrl) {
            if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
                runOnUiThread(() -> {
                    statusText.setText("Nenhuma figurinha grande foi encontrada");
                    actionButton.setEnabled(true);
                    Toast.makeText(MainActivity.this,
                            "Abra a figurinha grande como no seu print e toque no botão novamente.",
                            Toast.LENGTH_LONG).show();
                });
                return;
            }
            executor.execute(() -> downloadAndPrepare(sourceUrl));
        }
    }

    private void downloadAndPrepare(String sourceUrl) {
        try {
            runOnUiThread(() -> statusText.setText("Baixando figurinha selecionada..."));

            byte[] data;
            String mime;

            if (sourceUrl.startsWith("data:")) {
                int comma = sourceUrl.indexOf(',');
                if (comma <= 0) throw new IllegalStateException("Imagem inválida");
                String meta = sourceUrl.substring(5, comma);
                mime = meta.contains(";") ? meta.substring(0, meta.indexOf(';')) : meta;
                data = Base64.decode(sourceUrl.substring(comma + 1), Base64.DEFAULT);
            } else {
                HttpURLConnection connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", userAgent);
                String current = webView.getUrl();
                if (current != null) connection.setRequestProperty("Referer", current);
                String cookie = CookieManager.getInstance().getCookie(sourceUrl);
                if (cookie != null && !cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
                connection.connect();

                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new IllegalStateException("Falha ao baixar: HTTP " + code);
                }

                mime = connection.getContentType();
                if (mime != null && mime.contains(";")) mime = mime.substring(0, mime.indexOf(';')).trim();

                try (InputStream in = connection.getInputStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    int total = 0;
                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_SOURCE_BYTES) {
                            throw new IllegalStateException("Figurinha maior que 1 MB");
                        }
                        out.write(buffer, 0, read);
                    }
                    data = out.toByteArray();
                } finally {
                    connection.disconnect();
                }
            }

            if (data.length == 0) throw new IllegalStateException("Arquivo vazio");
            if (data.length > MAX_SOURCE_BYTES) throw new IllegalStateException("Figurinha maior que 1 MB");

            if (mime == null || !mime.startsWith("image/")) mime = inferMime(sourceUrl);
            String ext = extensionForMime(mime);

            File dir = ShareContentProvider.getShareDirectory(this);
            clearDirectory(dir);

            runOnUiThread(() -> statusText.setText("Multiplicando por 100..."));
            for (int i = 1; i <= COPY_COUNT; i++) {
                String name = String.format(Locale.US, "sticker_%03d.%s", i, ext);
                try (FileOutputStream fos = new FileOutputStream(new File(dir, name))) {
                    fos.write(data);
                }
            }

            getSharedPreferences(ShareContentProvider.PREFS, MODE_PRIVATE)
                    .edit().putString(ShareContentProvider.KEY_MIME, mime).apply();

            String finalMime = mime;
            String finalExt = ext;
            runOnUiThread(() -> {
                statusText.setText("100 cópias prontas • escolha o WhatsApp");
                showWhatsAppChoice(finalMime, finalExt);
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                statusText.setText("Falha ao preparar ×100");
                actionButton.setEnabled(true);
                Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    private void showWhatsAppChoice(String mime, String ext) {
        boolean normal = getPackageManager().getLaunchIntentForPackage("com.whatsapp") != null;
        boolean business = getPackageManager().getLaunchIntentForPackage("com.whatsapp.w4b") != null;

        if (!normal && !business) {
            actionButton.setEnabled(true);
            Toast.makeText(this, "WhatsApp não encontrado.", Toast.LENGTH_LONG).show();
            return;
        }

        if (normal && business) {
            new AlertDialog.Builder(this)
                    .setTitle("Enviar 100 cópias para")
                    .setItems(new String[]{"WhatsApp", "WhatsApp Business"}, (dialog, which) ->
                            shareToWhatsApp(which == 0 ? "com.whatsapp" : "com.whatsapp.w4b", mime, ext))
                    .setNegativeButton("Cancelar", (dialog, which) -> actionButton.setEnabled(true))
                    .show();
        } else {
            shareToWhatsApp(normal ? "com.whatsapp" : "com.whatsapp.w4b", mime, ext);
        }
    }

    private void shareToWhatsApp(String packageName, String mime, String ext) {
        ArrayList<Uri> streams = new ArrayList<>(COPY_COUNT);
        ClipData clipData = null;

        for (int i = 1; i <= COPY_COUNT; i++) {
            String name = String.format(Locale.US, "sticker_%03d.%s", i, ext);
            Uri uri = Uri.parse("content://" + ShareContentProvider.AUTHORITY + "/sticker/" + name);
            streams.add(uri);
            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (clipData == null) clipData = ClipData.newUri(getContentResolver(), "figurinha", uri);
            else clipData.addItem(new ClipData.Item(uri));
        }

        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
        send.setPackage(packageName);
        send.setType(mime != null ? mime : "image/webp");
        send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams);
        send.setClipData(clipData);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(send);
            statusText.setText("Escolha a conversa/grupo no WhatsApp");
        } catch (Exception e) {
            actionButton.setEnabled(true);
            Toast.makeText(this,
                    "O WhatsApp não aceitou 100 anexos de uma vez.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private String inferMime(String url) {
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (lower.contains(".png")) return "image/png";
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "image/jpeg";
        if (lower.contains(".gif")) return "image/gif";
        return "image/webp";
    }

    private String extensionForMime(String mime) {
        if (mime == null) return "webp";
        String lower = mime.toLowerCase(Locale.ROOT);
        if (lower.contains("png")) return "png";
        if (lower.contains("jpeg") || lower.contains("jpg")) return "jpg";
        if (lower.contains("gif")) return "gif";
        return "webp";
    }

    private void clearDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) if (file.isFile()) file.delete();
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Nenhum aplicativo encontrado para abrir o link.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (webView != null) {
            webView.removeJavascriptInterface("MultiFigurinhas");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
