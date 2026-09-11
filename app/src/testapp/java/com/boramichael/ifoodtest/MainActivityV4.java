package com.boramichael.ifoodtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String BASE = "https://rlgsbtolosxyymosidns.supabase.co";
    private static final String RIDER_API = BASE + "/functions/v1/bora-ifood-test-rider";
    private static final String PUB = "sb_publishable_cdYfnl879c7gh4WQE27S5g_CxEtVxde";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> riderIds = new ArrayList<>();
    private final List<String> riderNames = new ArrayList<>();
    private SharedPreferences prefs;
    private String token = "", riderId = "", riderName = "";
    private LinearLayout orders;
    private TextView sync;
    private ProgressBar progress;
    private boolean onOrders = false, loading = false;

    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() {
            if (onOrders) refresh(true);
            handler.postDelayed(this, 15000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("bora_ifood_test_v4_fee", MODE_PRIVATE);
        token = prefs.getString("session_token", "");
        riderId = prefs.getString("rider_id", "");
        riderName = prefs.getString("rider_name", "");
        if (token.isEmpty() || riderId.isEmpty() || riderName.isEmpty()) showLogin(); else showOrders();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(autoRefresh);
        if (onOrders) { refresh(true); handler.postDelayed(autoRefresh, 15000); }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(autoRefresh);
        super.onPause();
    }

    private void showLogin() {
        onOrders = false; token = ""; riderId = ""; riderName = "";
        riderIds.clear(); riderNames.clear();
        LinearLayout root = baseRoot(); root.setGravity(Gravity.CENTER);

        TextView badge = text("TESTE v0.5 • POR NOME + TAXA", 12, "#86EFAC", true);
        badge.setGravity(Gravity.CENTER); badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        badge.setBackground(bg("#11241A", "#1D5B35", 50)); root.addView(badge, wrap());

        TextView title = text("Bora Michael • Motoboy", 25, "#FFFFFF", true); title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = wrap(); tp.topMargin = dp(18); root.addView(title, tp);
        TextView sub = text("Escolha seu nome. O pedido vai mostrar endereço, telefone e a taxa informada no painel teste.", 13, "#94A3B8", false);
        sub.setGravity(Gravity.CENTER); LinearLayout.LayoutParams sp = full(); sp.topMargin=dp(8); sp.bottomMargin=dp(22); root.addView(sub, sp);

        Button choose = button("CARREGANDO MOTOBOYS...", "#27313D"); choose.setEnabled(false); root.addView(choose, full());
        TextView selected = text("Nenhum nome selecionado", 13, "#CBD5E1", false); selected.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams selp=full(); selp.topMargin=dp(10); root.addView(selected, selp);
        Button enter = button("ENTRAR", "#16A34A"); enter.setEnabled(false); LinearLayout.LayoutParams ep=full(); ep.topMargin=dp(16); root.addView(enter, ep);
        TextView msg=text("Buscando lista de motoboys...",12,"#94A3B8",false); msg.setGravity(Gravity.CENTER); LinearLayout.LayoutParams mp=full(); mp.topMargin=dp(12); root.addView(msg,mp);

        choose.setOnClickListener(v -> {
            if (riderNames.isEmpty()) return;
            new AlertDialog.Builder(this).setTitle("Escolha seu nome").setItems(riderNames.toArray(new String[0]), (d, which) -> {
                riderId=riderIds.get(which); riderName=riderNames.get(which); selected.setText(riderName); selected.setTextColor(Color.parseColor("#86EFAC"));
                choose.setText("TROCAR NOME"); enter.setEnabled(true); msg.setText("Nome selecionado. Toque em ENTRAR.");
            }).setNegativeButton("Cancelar", null).show();
        });

        enter.setOnClickListener(v -> {
            if (riderId.isEmpty()) { msg.setText("Escolha seu nome primeiro."); return; }
            enter.setEnabled(false); choose.setEnabled(false); msg.setText("Entrando como "+riderName+"...");
            io.execute(() -> {
                try {
                    JSONObject j=post(RIDER_API,"",new JSONObject().put("action","login_name").put("riderId",riderId),true);
                    String session=j.optString("session_token",""); JSONObject rider=j.optJSONObject("rider");
                    if(session.isEmpty()||rider==null) throw new Exception("Sessão de teste não recebida.");
                    token=session; riderId=rider.optString("id",riderId); riderName=rider.optString("nome",riderName);
                    prefs.edit().putString("session_token",token).putString("rider_id",riderId).putString("rider_name",riderName).apply();
                    runOnUiThread(this::showOrders);
                } catch(Exception e){ runOnUiThread(() -> { enter.setEnabled(true); choose.setEnabled(true); msg.setText(e.getMessage()==null?"Não foi possível entrar.":e.getMessage()); }); }
            });
        });
        setContentView(root); loadRiders(choose,msg);
    }

    private void loadRiders(Button choose, TextView msg) {
        io.execute(() -> {
            try {
                JSONObject j=post(RIDER_API,"",new JSONObject().put("action","list_riders"),true); JSONArray a=j.optJSONArray("riders");
                riderIds.clear(); riderNames.clear();
                if(a!=null) for(int i=0;i<a.length();i++){ JSONObject r=a.optJSONObject(i); if(r==null)continue; String id=r.optString("id","").trim(), n=r.optString("nome","").trim(); if(!id.isEmpty()&&!n.isEmpty()){riderIds.add(id);riderNames.add(n);} }
                runOnUiThread(() -> { if(riderNames.isEmpty()){choose.setText("NENHUM MOTOBOY DISPONÍVEL");msg.setText("Nenhum motoboy ativo foi encontrado.");} else {choose.setText("ESCOLHER MEU NOME");choose.setEnabled(true);msg.setText(riderNames.size()+" motoboy(s) disponível(is).");} });
            } catch(Exception e){ runOnUiThread(() -> {choose.setText("TENTAR NOVAMENTE");choose.setEnabled(true);msg.setText("Falha ao carregar nomes.");choose.setOnClickListener(v->{choose.setEnabled(false);choose.setText("CARREGANDO MOTOBOYS...");loadRiders(choose,msg);});}); }
        });
    }

    private void showOrders() {
        onOrders=true; LinearLayout root=baseRoot(); root.setPadding(0,0,0,0);
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(16),dp(12),dp(12),dp(12)); top.setBackgroundColor(Color.parseColor("#111820"));
        LinearLayout names=new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.addView(text("Minhas entregas",20,"#FFFFFF",true)); names.addView(text(riderName+" • iFood TESTE",11,"#86EFAC",true));
        top.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        Button reload=button("↻","#27313D"); top.addView(reload,new LinearLayout.LayoutParams(dp(50),dp(44)));
        Button logout=button("TROCAR","#27313D"); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(82),dp(44)); lp.leftMargin=dp(8); top.addView(logout,lp); root.addView(top,full());

        LinearLayout state=new LinearLayout(this); state.setGravity(Gravity.CENTER_VERTICAL); state.setPadding(dp(16),dp(9),dp(16),dp(9)); progress=new ProgressBar(this); state.addView(progress,new LinearLayout.LayoutParams(dp(21),dp(21)));
        sync=text("Sincronizando pedidos de "+riderName+"...",12,"#94A3B8",false); LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f); slp.leftMargin=dp(8); state.addView(sync,slp); root.addView(state,full());

        ScrollView scroll=new ScrollView(this); orders=new LinearLayout(this); orders.setOrientation(LinearLayout.VERTICAL); orders.setPadding(dp(12),dp(4),dp(12),dp(28)); scroll.addView(orders,full()); root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        reload.setOnClickListener(v->refresh(false)); logout.setOnClickListener(v->{prefs.edit().clear().apply();token="";riderId="";riderName="";showLogin();});
        setContentView(root); refresh(false); handler.removeCallbacks(autoRefresh); handler.postDelayed(autoRefresh,15000);
    }

    private void refresh(boolean silent) {
        if(!onOrders||loading)return; loading=true; if(!silent){progress.setVisibility(View.VISIBLE);sync.setText("Atualizando pedidos de "+riderName+"...");}
        io.execute(() -> {
            try {
                JSONObject j=post(RIDER_API,token,new JSONObject().put("action","list_orders"),true); JSONObject rider=j.optJSONObject("rider");
                if(rider!=null){String n=rider.optString("nome","").trim();if(!n.isEmpty())riderName=n;}
                JSONArray a=j.optJSONArray("orders"); if(a==null)a=new JSONArray(); List<JSONObject> pending=new ArrayList<>(),done=new ArrayList<>();
                for(int i=0;i<a.length();i++){
                    JSONObject o=a.optJSONObject(i); if(o==null)continue;
                    try{Double fee=fetchFee(o.optString("order_id",""));if(fee!=null)o.put("dispatch_fee",fee);}catch(Exception ignored){}
                    if("completed".equals(o.optString("status")))done.add(o);else pending.add(o);
                }
                runOnUiThread(()->render(pending,done));
            } catch(Exception e){ String m=e.getMessage()==null?"Falha ao sincronizar.":e.getMessage(); runOnUiThread(()->{if(m.toLowerCase().contains("sessão expirada")||m.toLowerCase().contains("desativado")){Toast.makeText(this,m,Toast.LENGTH_LONG).show();prefs.edit().clear().apply();showLogin();}else{sync.setText(m);progress.setVisibility(View.GONE);}}); }
            finally{loading=false;}
        });
    }

    private Double fetchFee(String orderId) throws Exception {
        if(orderId==null||orderId.isEmpty())return null;
        String u=BASE+"/rest/v1/ifood_test_orders?select=dispatch_fee&order_id=eq."+orderId;
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setRequestMethod("GET"); c.setConnectTimeout(10000); c.setReadTimeout(15000); c.setRequestProperty("apikey",PUB); c.setRequestProperty("Accept","application/json");
        int code=c.getResponseCode(); InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream(); String txt=read(in); if(code<200||code>=300||txt.isEmpty())return null;
        JSONArray a=new JSONArray(txt); if(a.length()==0)return null; JSONObject j=a.optJSONObject(0); if(j==null||j.isNull("dispatch_fee"))return null; return j.optDouble("dispatch_fee");
    }

    private void render(List<JSONObject> pending,List<JSONObject> done){
        if(!onOrders||orders==null)return; orders.removeAllViews(); section("NÃO CONCLUÍDOS",pending.size(),"#F8FAFC");
        if(pending.isEmpty()){TextView e=text("Nenhuma entrega pendente para "+riderName+".",13,"#94A3B8",false);e.setGravity(Gravity.CENTER);e.setPadding(dp(14),dp(28),dp(14),dp(28));orders.addView(e,full());} else for(JSONObject o:pending)orders.addView(card(o,false),marginTop(7));
        section("CONCLUÍDOS",done.size(),"#86EFAC"); for(JSONObject o:done)orders.addView(card(o,true),marginTop(7)); progress.setVisibility(View.GONE); sync.setText(riderName+" • sincronizado • atualização automática a cada 15s");
    }

    private void section(String s,int n,String c){TextView t=text(s+"  •  "+n,12,c,true);t.setPadding(dp(4),dp(15),dp(4),dp(7));orders.addView(t,full());}

    private View card(JSONObject o,boolean done){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(14),dp(12),dp(14),dp(12)); c.setBackground(bg(done?"#102019":"#151D26",done?"#1E6A3A":"#2A3746",14));
        LinearLayout first=new LinearLayout(this); first.setGravity(Gravity.CENTER_VERTICAL); first.addView(text("#"+o.optString("display_id","—"),18,"#FFFFFF",true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); TextView st=text(done?"CONCLUÍDO":"EM ENTREGA",10,done?"#86EFAC":"#93C5FD",true);st.setPadding(dp(8),dp(5),dp(8),dp(5));first.addView(st);c.addView(first,full());
        String bairro=o.optString("neighborhood",""); c.addView(text(o.optString("customer_name","Cliente")+(bairro.isEmpty()?"":" • "+bairro),14,"#E5E7EB",true),marginTop(7));
        String addr=o.optString("formatted_address",""); if(addr.isEmpty())addr=join(o.optString("street_name",""),o.optString("street_number","")); c.addView(text(addr.isEmpty()?"Endereço não informado":addr,13,"#CBD5E1",false),marginTop(4));
        String extra=joinDot(o.optString("complement","") ,o.optString("reference","")); if(!extra.isEmpty())c.addView(text(extra,11,"#94A3B8",false),marginTop(3));
        String phone=o.optString("customer_phone",""); c.addView(text(phone.isEmpty()?"☎ Telefone não informado":"☎ "+phone,12,"#94A3B8",false),marginTop(4));
        if(o.has("dispatch_fee")&&!o.isNull("dispatch_fee"))c.addView(text("Taxa do bairro: "+currency(o.optDouble("dispatch_fee",0)),13,"#86EFAC",true),marginTop(5));
        else c.addView(text("Taxa do bairro: não informada",12,"#64748B",false),marginTop(5));
        if(!done){Button f=button("FINALIZAR NO IFOOD","#16A34A");c.addView(f,marginTop(10));f.setOnClickListener(v->{Intent i=new Intent(this,FinalizeActivity.class);i.putExtra("order_id",o.optString("order_id",""));i.putExtra("display_id",o.optString("display_id",""));i.putExtra("locator",o.optString("locator","").replaceAll("\\D",""));i.putExtra("access_token",token);startActivity(i);});}
        return c;
    }

    private String currency(double v){return NumberFormat.getCurrencyInstance(new Locale("pt","BR")).format(v);}

    private JSONObject post(String urlText,String bearer,JSONObject body,boolean apiKey)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(urlText).openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(25000); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("Accept","application/json"); if(apiKey)c.setRequestProperty("apikey",PUB); if(bearer!=null&&!bearer.isEmpty())c.setRequestProperty("Authorization","Bearer "+bearer); byte[] b=body.toString().getBytes(StandardCharsets.UTF_8); try(OutputStream out=c.getOutputStream()){out.write(b);} int code=c.getResponseCode(); InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream(); String txt=read(in); JSONObject j=txt.isEmpty()?new JSONObject():new JSONObject(txt); if(code<200||code>=300)throw new Exception(j.optString("error","HTTP "+code)); return j;
    }
    private String read(InputStream in)throws Exception{if(in==null)return"";ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);in.close();return new String(b.toByteArray(),StandardCharsets.UTF_8);}
    private LinearLayout baseRoot(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(dp(24),dp(30),dp(24),dp(30));r.setBackgroundColor(Color.parseColor("#0B0F14"));return r;}
    private Button button(String s,String color){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(color)));b.setMinHeight(dp(44));return b;}
    private TextView text(String s,float z,String color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(Color.parseColor(color));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable bg(String fill,String stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(fill));g.setCornerRadius(dp(radius));g.setStroke(dp(1),Color.parseColor(stroke));return g;}
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);} private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);} private LinearLayout.LayoutParams marginTop(int t){LinearLayout.LayoutParams p=full();p.topMargin=dp(t);return p;} private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);} private String join(String a,String b){if(a.isEmpty())return b;if(b.isEmpty())return a;return a+", "+b;} private String joinDot(String a,String b){if(a.isEmpty())return b;if(b.isEmpty())return a;return a+" • "+b;}
    @Override protected void onDestroy(){handler.removeCallbacks(autoRefresh);io.shutdownNow();super.onDestroy();}
}
