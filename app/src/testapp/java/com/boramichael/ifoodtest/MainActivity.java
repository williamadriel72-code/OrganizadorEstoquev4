package com.boramichael.ifoodtest;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String SUPABASE = "https://rlgsbtolosxyymosidns.supabase.co";
    private static final String LOGIN_URL = SUPABASE + "/functions/v1/kh-motoboy-login";
    private static final String RIDER_API = SUPABASE + "/functions/v1/bora-ifood-test-rider";
    private static final String PUB = "sb_publishable_cdYfnl879c7gh4WQE27S5g_CxEtVxde";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private String token = "";
    private String riderName = "";
    private LinearLayout ordersContainer;
    private TextView syncLabel;
    private ProgressBar progress;
    private boolean ordersScreen = false;
    private boolean loading = false;

    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() {
            if (ordersScreen) refreshOrders(true);
            handler.postDelayed(this, 15000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("bora_ifood_test", MODE_PRIVATE);
        token = prefs.getString("access_token", "");
        riderName = prefs.getString("rider_name", "");
        if (token.isEmpty()) showLogin();
        else showOrdersScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(autoRefresh);
        if (ordersScreen) {
            refreshOrders(true);
            handler.postDelayed(autoRefresh, 15000);
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(autoRefresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(autoRefresh);
        io.shutdownNow();
        super.onDestroy();
    }

    private void showLogin() {
        ordersScreen = false;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));
        root.setBackgroundColor(Color.parseColor("#0B0F14"));

        TextView badge = label("AMBIENTE DE TESTE", 12, Color.parseColor("#86EFAC"), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(7), dp(10), dp(7));
        badge.setBackground(roundBg("#11241A", "#1D5B35", 999));
        root.addView(badge, wrap());

        TextView title = label("Bora Michael • Motoboy", 26, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = wrap(); tp.topMargin = dp(18);
        root.addView(title, tp);

        TextView sub = label("APK de teste conectado somente ao painel iFood de teste.", 13, Color.parseColor("#94A3B8"), false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = wrap(); sp.topMargin = dp(8); sp.bottomMargin = dp(24);
        root.addView(sub, sp);

        EditText login = input("Login de 4 números", false);
        EditText pin = input("Senha de 4 números", true);
        root.addView(login, fullWithMargin(0, 8));
        root.addView(pin, fullWithMargin(10, 8));

        Button enter = button("ENTRAR", "#16A34A");
        LinearLayout.LayoutParams bp = fullWithMargin(16, 0);
        root.addView(enter, bp);

        TextView msg = label("", 13, Color.parseColor("#FCA5A5"), false);
        msg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = wrap(); mp.topMargin = dp(12);
        root.addView(msg, mp);

        enter.setOnClickListener(v -> {
            String l = login.getText().toString().replaceAll("\\D", "");
            String p = pin.getText().toString().replaceAll("\\D", "");
            if (l.length() != 4 || p.length() != 4) {
                msg.setText("Login e senha precisam ter 4 números.");
                return;
            }
            enter.setEnabled(false);
            msg.setText("Entrando...");
            io.execute(() -> {
                try {
                    JSONObject body = new JSONObject().put("login", l).put("pin", p);
                    JSONObject j = postJson(LOGIN_URL, "", body, true);
                    String access = j.optString("access_token", "");
                    JSONObject rider = j.optJSONObject("motoboy");
                    if (access.isEmpty() || rider == null) throw new Exception("Sessão não recebida.");
                    token = access;
                    riderName = rider.optString("nome", "Motoboy");
                    prefs.edit().putString("access_token", token).putString("rider_name", riderName).apply();
                    runOnUiThread(this::showOrdersScreen);
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        enter.setEnabled(true);
                        msg.setText(e.getMessage() == null ? "Não foi possível entrar." : e.getMessage());
                    });
                }
            });
        });

        setContentView(root);
    }

    private void showOrdersScreen() {
        ordersScreen = true;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0F14"));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(14), dp(12), dp(12));
        top.setBackgroundColor(Color.parseColor("#111820"));

        LinearLayout headText = new LinearLayout(this);
        headText.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("Minhas entregas", 20, Color.WHITE, true);
        TextView rider = label(riderName + " • TESTE", 12, Color.parseColor("#86EFAC"), true);
        headText.addView(title);
        headText.addView(rider);
        top.addView(headText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button refresh = button("↻", "#27313D");
        refresh.setTextSize(20);
        top.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(44)));
        Button logout = button("SAIR", "#27313D");
        LinearLayout.LayoutParams lop = new LinearLayout.LayoutParams(dp(74), dp(44)); lop.leftMargin = dp(8);
        top.addView(logout, lop);
        root.addView(top, full());

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(dp(16), dp(9), dp(16), dp(9));
        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        statusRow.addView(progress, new LinearLayout.LayoutParams(dp(22), dp(22)));
        syncLabel = label("Sincronizando com o painel de teste...", 12, Color.parseColor("#94A3B8"), false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); slp.leftMargin = dp(9);
        statusRow.addView(syncLabel, slp);
        root.addView(statusRow, full());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        ordersContainer = new LinearLayout(this);
        ordersContainer.setOrientation(LinearLayout.VERTICAL);
        ordersContainer.setPadding(dp(12), dp(4), dp(12), dp(28));
        scroll.addView(ordersContainer, full());
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        refresh.setOnClickListener(v -> refreshOrders(false));
        logout.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            token = "";
            riderName = "";
            showLogin();
        });

        setContentView(root);
        refreshOrders(false);
        handler.removeCallbacks(autoRefresh);
        handler.postDelayed(autoRefresh, 15000);
    }

    private void refreshOrders(boolean silent) {
        if (!ordersScreen || loading) return;
        loading = true;
        if (!silent) {
            progress.setVisibility(View.VISIBLE);
            syncLabel.setText("Atualizando pedidos...");
        }
        io.execute(() -> {
            try {
                JSONObject req = new JSONObject().put("action", "list_orders");
                JSONObject j = postJson(RIDER_API, token, req, false);
                JSONArray orders = j.optJSONArray("orders");
                if (orders == null) orders = new JSONArray();
                List<JSONObject> pending = new ArrayList<>();
                List<JSONObject> completed = new ArrayList<>();
                for (int i = 0; i < orders.length(); i++) {
                    JSONObject o = orders.optJSONObject(i);
                    if (o == null) continue;
                    if ("completed".equals(o.optString("status"))) completed.add(o); else pending.add(o);
                }
                List<JSONObject> finalPending = pending;
                List<JSONObject> finalCompleted = completed;
                runOnUiThread(() -> renderOrders(finalPending, finalCompleted));
            } catch (Exception e) {
                String m = e.getMessage() == null ? "Falha ao sincronizar." : e.getMessage();
                runOnUiThread(() -> {
                    if (m.toLowerCase().contains("sessão")) {
                        prefs.edit().clear().apply(); token = ""; Toast.makeText(this, m, Toast.LENGTH_LONG).show(); showLogin();
                    } else {
                        syncLabel.setText(m);
                        progress.setVisibility(View.GONE);
                    }
                });
            } finally {
                loading = false;
            }
        });
    }

    private void renderOrders(List<JSONObject> pending, List<JSONObject> completed) {
        if (!ordersScreen || ordersContainer == null) return;
        ordersContainer.removeAllViews();
        addSectionTitle("NÃO CONCLUÍDOS", pending.size(), "#F8FAFC");
        if (pending.isEmpty()) {
            TextView empty = label("Nenhuma entrega pendente. Quando a loja despachar pedidos para você, eles aparecerão aqui automaticamente.", 13, Color.parseColor("#94A3B8"), false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(28), dp(18), dp(28));
            ordersContainer.addView(empty, full());
        } else {
            for (JSONObject o : pending) ordersContainer.addView(orderCard(o, false), fullWithMargin(7, 0));
        }

        addSectionTitle("CONCLUÍDOS", completed.size(), "#86EFAC");
        for (JSONObject o : completed) ordersContainer.addView(orderCard(o, true), fullWithMargin(7, 0));

        progress.setVisibility(View.GONE);
        syncLabel.setText("Sincronizado • atualização automática a cada 15s");
    }

    private void addSectionTitle(String title, int count, String color) {
        TextView t = label(title + "  •  " + count, 12, Color.parseColor(color), true);
        t.setPadding(dp(4), dp(15), dp(4), dp(7));
        ordersContainer.addView(t, full());
    }

    private View orderCard(JSONObject o, boolean completed) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundBg(completed ? "#102019" : "#151D26", completed ? "#1E6A3A" : "#2A3746", 14));

        LinearLayout first = new LinearLayout(this);
        first.setGravity(Gravity.CENTER_VERTICAL);
        String display = o.optString("display_id", "—");
        TextView id = label("#" + display, 18, Color.WHITE, true);
        first.addView(id, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView st = label(completed ? "CONCLUÍDO" : "EM ENTREGA", 10, Color.parseColor(completed ? "#86EFAC" : "#93C5FD"), true);
        st.setPadding(dp(8), dp(5), dp(8), dp(5));
        st.setBackground(roundBg(completed ? "#12351F" : "#14253D", completed ? "#256B3D" : "#244A78", 999));
        first.addView(st);
        card.addView(first, full());

        String neighborhood = o.optString("neighborhood", "");
        TextView name = label(o.optString("customer_name", "Cliente") + (neighborhood.isEmpty() ? "" : "  •  " + neighborhood), 14, Color.parseColor("#E5E7EB"), true);
        LinearLayout.LayoutParams np = fullWithMargin(7, 0);
        card.addView(name, np);

        String address = o.optString("formatted_address", "");
        if (address.isEmpty()) {
            address = joinNonEmpty(o.optString("street_name", ""), o.optString("street_number", ""));
        }
        TextView addr = label(address.isEmpty() ? "Endereço não informado" : address, 13, Color.parseColor("#CBD5E1"), false);
        card.addView(addr, fullWithMargin(4, 0));

        String extra = joinDot(o.optString("complement", ""), o.optString("reference", ""));
        if (!extra.isEmpty()) {
            TextView ex = label(extra, 11, Color.parseColor("#94A3B8"), false);
            card.addView(ex, fullWithMargin(3, 0));
        }

        String phone = o.optString("customer_phone", "");
        if (!phone.isEmpty()) {
            TextView ph = label("☎ " + phone, 12, Color.parseColor("#94A3B8"), false);
            card.addView(ph, fullWithMargin(4, 0));
        }

        if (!completed) {
            Button finish = button("FINALIZAR NO IFOOD", "#16A34A");
            LinearLayout.LayoutParams fp = fullWithMargin(10, 0);
            card.addView(finish, fp);
            finish.setOnClickListener(v -> openFinalizer(o));
        }
        return card;
    }

    private void openFinalizer(JSONObject o) {
        String orderId = o.optString("order_id", "");
        String locator = o.optString("locator", "").replaceAll("\\D", "");
        if (orderId.isEmpty()) {
            Toast.makeText(this, "Pedido sem ID do iFood.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, FinalizeActivity.class);
        i.putExtra("order_id", orderId);
        i.putExtra("display_id", o.optString("display_id", ""));
        i.putExtra("locator", locator);
        i.putExtra("access_token", token);
        startActivity(i);
    }

    private JSONObject postJson(String urlText, String bearer, JSONObject body, boolean withApiKey) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        if (withApiKey) c.setRequestProperty("apikey", PUB);
        if (bearer != null && !bearer.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + bearer);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String text = readAll(in);
        JSONObject j = text.isEmpty() ? new JSONObject() : new JSONObject(text);
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

    private EditText input(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.parseColor("#64748B"));
        e.setTextColor(Color.WHITE);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(roundBg("#10161D", "#2A3746", 12));
        e.setInputType(InputType.TYPE_CLASS_NUMBER | (password ? InputType.TYPE_NUMBER_VARIATION_PASSWORD : InputType.TYPE_NUMBER_VARIATION_NORMAL));
        return e;
    }

    private Button button(String text, String bg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(bg)));
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinHeight(dp(44));
        return b;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable roundBg(String fill, String stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(fill));
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), Color.parseColor(stroke));
        return g;
    }

    private LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private LinearLayout.LayoutParams fullWithMargin(int top, int bottom) {
        LinearLayout.LayoutParams p = full(); p.topMargin = dp(top); p.bottomMargin = dp(bottom); return p;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String joinNonEmpty(String a, String b) {
        if (a == null) a = ""; if (b == null) b = "";
        if (a.isEmpty()) return b; if (b.isEmpty()) return a; return a + ", " + b;
    }
    private String joinDot(String a, String b) {
        if (a == null) a = ""; if (b == null) b = "";
        if (a.isEmpty()) return b; if (b.isEmpty()) return a; return a + " • " + b;
    }
}
