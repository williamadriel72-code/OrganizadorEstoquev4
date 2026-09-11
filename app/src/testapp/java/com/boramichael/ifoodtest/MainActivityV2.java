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
    private static final String BASE = "https://rlgsbtolosxyymosidns.supabase.co";
    private static final String LOGIN_URL = BASE + "/functions/v1/kh-pin-login";
    private static final String RIDER_API = BASE + "/functions/v1/bora-ifood-test-rider";
    private static final String PUB = "sb_publishable_cdYfnl879c7gh4WQE27S5g_CxEtVxde";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private String token = "";
    private LinearLayout orders;
    private TextView sync;
    private ProgressBar progress;
    private boolean onOrders = false;
    private boolean loading = false;

    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() {
            if (onOrders) refresh(true);
            handler.postDelayed(this, 15000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("bora_ifood_test_v2", MODE_PRIVATE);
        token = prefs.getString("access_token", "");
        if (token.isEmpty()) showLogin(); else showOrders();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(autoRefresh);
        if (onOrders) {
            refresh(true);
            handler.postDelayed(autoRefresh, 15000);
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(autoRefresh);
        super.onPause();
    }

    private void showLogin() {
        onOrders = false;
        LinearLayout root = baseRoot();
        root.setGravity(Gravity.CENTER);

        TextView badge = text("AMBIENTE DE TESTE", 12, "#86EFAC", true);
        badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        badge.setBackground(bg("#11241A", "#1D5B35", 50));
        root.addView(badge, wrap());

        TextView title = text("Bora Michael • Motoboy", 25, "#FFFFFF", true);
        LinearLayout.LayoutParams tp = wrap(); tp.topMargin = dp(18);
        root.addView(title, tp);

        TextView sub = text("Use o mesmo login e senha de 4 números do painel de teste.", 13, "#94A3B8", false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = full(); sp.topMargin = dp(8); sp.bottomMargin = dp(22);
        root.addView(sub, sp);

        EditText login = input("Login de 4 números", false);
        EditText pin = input("Senha de 4 números", true);
        root.addView(login, full());
        LinearLayout.LayoutParams pp = full(); pp.topMargin = dp(10);
        root.addView(pin, pp);

        Button enter = button("ENTRAR", "#16A34A");
        LinearLayout.LayoutParams ep = full(); ep.topMargin = dp(16);
        root.addView(enter, ep);

        TextView msg = text("", 13, "#FCA5A5", false);
        msg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = full(); mp.topMargin = dp(12);
        root.addView(msg, mp);

        enter.setOnClickListener(v -> {
            String l = login.getText().toString().replaceAll("\\D", "");
            String p = pin.getText().toString().replaceAll("\\D", "");
            if (l.length() != 4 || p.length() != 4) {
                msg.setText("Login e senha precisam ter 4 números.");
                return;
            }
            enter.setEnabled(false);
            msg.setText("Entrando no ambiente de teste...");
            io.execute(() -> {
                try {
                    JSONObject body = new JSONObject().put("login", l).put("pin", p);
                    JSONObject j = post(LOGIN_URL, "", body, true);
                    String access = j.optString("access_token", "");
                    if (access.isEmpty()) throw new Exception("Sessão não recebida.");
                    token = access;
                    prefs.edit().putString("access_token", token).apply();
                    runOnUiThread(this::showOrders);
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

    private void showOrders() {
        onOrders = true;
        LinearLayout root = baseRoot();
        root.setPadding(0,0,0,0);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(12), dp(12), dp(12));
        top.setBackgroundColor(Color.parseColor("#111820"));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text("Minhas entregas", 20, "#FFFFFF", true));
        names.addView(text("MOTOBOY TESTE • iFood", 11, "#86EFAC", true));
        top.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button reload = button("↻", "#27313D");
        top.addView(reload, new LinearLayout.LayoutParams(dp(50), dp(44)));
        Button logout = button("SAIR", "#27313D");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(44)); lp.leftMargin = dp(8);
        top.addView(logout, lp);
        root.addView(top, full());

        LinearLayout state = new LinearLayout(this);
        state.setGravity(Gravity.CENTER_VERTICAL);
        state.setPadding(dp(16), dp(9), dp(16), dp(9));
        progress = new ProgressBar(this);
        state.addView(progress, new LinearLayout.LayoutParams(dp(21), dp(21)));
        sync = text("Sincronizando com o painel teste...", 12, "#94A3B8", false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); slp.leftMargin = dp(8);
        state.addView(sync, slp);
        root.addView(state, full());

        ScrollView scroll = new ScrollView(this);
        orders = new LinearLayout(this);
        orders.setOrientation(LinearLayout.VERTICAL);
        orders.setPadding(dp(12), dp(4), dp(12), dp(28));
        scroll.addView(orders, full());
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        reload.setOnClickListener(v -> refresh(false));
        logout.setOnClickListener(v -> {
            prefs.edit().clear().apply(); token = ""; showLogin();
        });

        setContentView(root);
        refresh(false);
        handler.removeCallbacks(autoRefresh);
        handler.postDelayed(autoRefresh, 15000);
    }

    private void refresh(boolean silent) {
        if (!onOrders || loading) return;
        loading = true;
        if (!silent) { progress.setVisibility(View.VISIBLE); sync.setText("Atualizando pedidos..."); }
        io.execute(() -> {
            try {
                JSONObject j = post(RIDER_API, token, new JSONObject().put("action", "list_orders"), true);
                JSONArray a = j.optJSONArray("orders"); if (a == null) a = new JSONArray();
                List<JSONObject> pending = new ArrayList<>(), done = new ArrayList<>();
                for (int i=0;i<a.length();i++) {
                    JSONObject o = a.optJSONObject(i); if (o == null) continue;
                    if ("completed".equals(o.optString("status"))) done.add(o); else pending.add(o);
                }
                runOnUiThread(() -> render(pending, done));
            } catch (Exception e) {
                String m = e.getMessage() == null ? "Falha ao sincronizar." : e.getMessage();
                runOnUiThread(() -> { sync.setText(m); progress.setVisibility(View.GONE); });
            } finally { loading = false; }
        });
    }

    private void render(List<JSONObject> pending, List<JSONObject> done) {
        orders.removeAllViews();
        section("NÃO CONCLUÍDOS", pending.size(), "#F8FAFC");
        if (pending.isEmpty()) {
            TextView e = text("Nenhuma entrega pendente. Quando um pedido for despachado para o Motoboy TESTE, ele aparecerá aqui.", 13, "#94A3B8", false);
            e.setGravity(Gravity.CENTER); e.setPadding(dp(14), dp(28), dp(14), dp(28)); orders.addView(e, full());
        } else for (JSONObject o : pending) orders.addView(card(o, false), marginTop(7));
        section("CONCLUÍDOS", done.size(), "#86EFAC");
        for (JSONObject o : done) orders.addView(card(o, true), marginTop(7));
        progress.setVisibility(View.GONE);
        sync.setText("Sincronizado • atualização automática a cada 15s");
    }

    private void section(String s, int n, String c) {
        TextView t = text(s + "  •  " + n, 12, c, true);
        t.setPadding(dp(4), dp(15), dp(4), dp(7)); orders.addView(t, full());
    }

    private View card(JSONObject o, boolean done) {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14),dp(12),dp(14),dp(12));
        c.setBackground(bg(done ? "#102019":"#151D26", done ? "#1E6A3A":"#2A3746",14));
        LinearLayout first = new LinearLayout(this); first.setGravity(Gravity.CENTER_VERTICAL);
        first.addView(text("#" + o.optString("display_id","—"),18,"#FFFFFF",true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView st = text(done ? "CONCLUÍDO":"EM ENTREGA",10,done ? "#86EFAC":"#93C5FD",true);
        st.setPadding(dp(8),dp(5),dp(8),dp(5)); first.addView(st); c.addView(first,full());
        String bairro = o.optString("neighborhood","");
        TextView n = text(o.optString("customer_name","Cliente") + (bairro.isEmpty()?"":" • "+bairro),14,"#E5E7EB",true);
        c.addView(n, marginTop(7));
        String addr = o.optString("formatted_address","");
        if (addr.isEmpty()) addr = join(o.optString("street_name",""),o.optString("street_number",""));
        c.addView(text(addr.isEmpty()?"Endereço não informado":addr,13,"#CBD5E1",false),marginTop(4));
        String extra = joinDot(o.optString("complement",""),o.optString("reference",""));
        if (!extra.isEmpty()) c.addView(text(extra,11,"#94A3B8",false),marginTop(3));
        if (!done) {
            Button f = button("FINALIZAR NO IFOOD","#16A34A"); c.addView(f,marginTop(10));
            f.setOnClickListener(v -> {
                Intent i = new Intent(this, FinalizeActivity.class);
                i.putExtra("order_id",o.optString("order_id",""));
                i.putExtra("display_id",o.optString("display_id",""));
                i.putExtra("locator",o.optString("locator","").replaceAll("\\D",""));
                i.putExtra("access_token",token);
                startActivity(i);
            });
        }
        return c;
    }

    private JSONObject post(String urlText, String bearer, JSONObject body, boolean apiKey) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(urlText).openConnection();
        c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(25000); c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("Accept","application/json");
        if (apiKey) c.setRequestProperty("apikey",PUB);
        if (bearer != null && !bearer.isEmpty()) c.setRequestProperty("Authorization","Bearer "+bearer);
        byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
        try(OutputStream out=c.getOutputStream()){out.write(b);} int code=c.getResponseCode();
        InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream(); String txt=read(in);
        JSONObject j=txt.isEmpty()?new JSONObject():new JSONObject(txt);
        if(code<200||code>=300)throw new Exception(j.optString("error","HTTP "+code)); return j;
    }

    private String read(InputStream in) throws Exception {
        if(in==null)return ""; ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n;
        while((n=in.read(buf))!=-1)b.write(buf,0,n); in.close(); return new String(b.toByteArray(),StandardCharsets.UTF_8);
    }

    private LinearLayout baseRoot(){ LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(dp(24),dp(30),dp(24),dp(30));r.setBackgroundColor(Color.parseColor("#0B0F14"));return r; }
    private EditText input(String h, boolean pass){ EditText e=new EditText(this);e.setHint(h);e.setHintTextColor(Color.parseColor("#64748B"));e.setTextColor(Color.WHITE);e.setTextSize(16);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackground(bg("#10161D","#2A3746",12));e.setInputType(InputType.TYPE_CLASS_NUMBER|(pass?InputType.TYPE_NUMBER_VARIATION_PASSWORD:InputType.TYPE_NUMBER_VARIATION_NORMAL));return e; }
    private Button button(String s,String color){ Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(color)));b.setMinHeight(dp(44));return b; }
    private TextView text(String s,float z,String color,boolean bold){ TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(Color.parseColor(color));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t; }
    private GradientDrawable bg(String fill,String stroke,int radius){ GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(fill));g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.parseColor(stroke));return g; }
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);} private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);} private LinearLayout.LayoutParams marginTop(int t){LinearLayout.LayoutParams p=full();p.topMargin=dp(t);return p;} private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);} private String join(String a,String b){if(a.isEmpty())return b;if(b.isEmpty())return a;return a+", "+b;} private String joinDot(String a,String b){if(a.isEmpty())return b;if(b.isEmpty())return a;return a+" • "+b;}

    @Override protected void onDestroy(){handler.removeCallbacks(autoRefresh);io.shutdownNow();super.onDestroy();}
}
