package com.organizador.scanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public class BoraMayconActivity extends Activity {
    private static final int REQ_BACKUP = 701;
    private static final int REQ_RESTORE = 702;
    private static final int BG = Color.rgb(17, 20, 24);
    private static final int CARD = Color.rgb(30, 34, 40);
    private static final int CARD_2 = Color.rgb(39, 44, 51);
    private static final int ACCENT = Color.rgb(220, 62, 58);
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(180, 185, 193);
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);

    private RouteDb db;
    private Bitmap profileBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        db = new RouteDb(this);
        profileBitmap = loadProfileBitmap();
        showHome();
    }

    private Bitmap loadProfileBitmap() {
        try {
            int id = getResources().getIdentifier("profile_b64", "raw", getPackageName());
            if (id == 0) return null;
            InputStream in = getResources().openRawResource(id);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            String b64 = new String(out.toByteArray(), StandardCharsets.UTF_8).replaceAll("\\s", "");
            byte[] bytes = Base64.getDecoder().decode(b64);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void showHome() {
        LocalDate today = LocalDate.now();
        LinearLayout root = page();
        root.addView(profileHeader());

        TextView date = text(today.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR) + " • " + DISPLAY.format(today), 15, MUTED, true);
        date.setGravity(Gravity.CENTER);
        root.addView(date, mp(-1, dp(42)));

        RouteDb.DaySummary s = db.daySummary(ISO.format(today));
        LinearLayout stats = row();
        stats.addView(statCard("ENTREGAS DE HOJE", String.valueOf(s.count)), weight());
        stats.addView(space(10), mp(dp(10), 1));
        stats.addView(statCard("TOTAL DE HOJE", money(s.total)), weight());
        root.addView(stats, margin(-1, -2, 16, 8, 16, 8));

        Button add = button("+  ADICIONAR ENTREGA", ACCENT);
        add.setOnClickListener(v -> showAddDeliveryDialog(today));
        root.addView(add, margin(-1, dp(58), 16, 8, 16, 14));

        TextView sec = sectionTitle("Entregas de hoje");
        root.addView(sec, margin(-1, -2, 16, 4, 16, 4));
        List<RouteDb.Delivery> deliveries = db.deliveriesForDate(ISO.format(today));
        if (deliveries.isEmpty()) {
            TextView empty = cardText("Nenhuma entrega lançada hoje. Toque em “Adicionar entrega” para começar.");
            root.addView(empty, margin(-1, -2, 16, 4, 16, 12));
        } else {
            for (RouteDb.Delivery d : deliveries) root.addView(deliveryRow(d, true), margin(-1, -2, 16, 3, 16, 3));
        }

        TextView navTitle = sectionTitle("Ferramentas");
        root.addView(navTitle, margin(-1, -2, 16, 16, 16, 5));
        LinearLayout r1 = row();
        Button cal = button("CALENDÁRIO", CARD_2);
        Button hist = button("HISTÓRICO", CARD_2);
        cal.setOnClickListener(v -> showCalendar());
        hist.setOnClickListener(v -> showHistory());
        r1.addView(cal, weight()); r1.addView(space(8), mp(dp(8), 1)); r1.addView(hist, weight());
        root.addView(r1, margin(-1, dp(52), 16, 0, 16, 8));

        LinearLayout r2 = row();
        Button rep = button("RELATÓRIOS", CARD_2);
        Button neigh = button("BAIRROS E TAXAS", CARD_2);
        rep.setOnClickListener(v -> showReports());
        neigh.setOnClickListener(v -> showNeighborhoods());
        r2.addView(rep, weight()); r2.addView(space(8), mp(dp(8), 1)); r2.addView(neigh, weight());
        root.addView(r2, margin(-1, dp(52), 16, 0, 16, 8));

        Button backup = button("BACKUP E RESTAURAÇÃO", CARD_2);
        backup.setOnClickListener(v -> showBackupMenu());
        root.addView(backup, margin(-1, dp(52), 16, 0, 16, 24));

        setPage(root);
    }

    private View profileHeader() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(16), dp(22), dp(16), dp(10));

        ImageView img = new ImageView(this);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(CARD);
        oval.setStroke(dp(3), ACCENT);
        img.setBackground(oval);
        img.setClipToOutline(true);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setPadding(dp(3), dp(3), dp(3), dp(3));
        if (profileBitmap != null) img.setImageBitmap(profileBitmap);
        box.addView(img, mp(dp(112), dp(112)));

        TextView title = text("Bora Maycon Hi Hi", 26, TEXT, true);
        title.setGravity(Gravity.CENTER);
        box.addView(title, margin(-1, -2, 0, 10, 0, 0));
        TextView sub = text("Rotas, Entregas e Taxas", 14, MUTED, false);
        sub.setGravity(Gravity.CENTER);
        box.addView(sub);
        return box;
    }

    private void showAddDeliveryDialog(LocalDate date) {
        final List<RouteDb.Neighborhood> all = db.neighborhoods("");
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText search = edit("Digite o bairro...");
        wrap.addView(search, mp(-1, dp(52)));
        ListView list = new ListView(this);
        list.setDividerHeight(1);
        wrap.addView(list, mp(-1, dp(420)));

        ArrayList<RouteDb.Neighborhood> shown = new ArrayList<>(all);
        ArrayAdapter<RouteDb.Neighborhood> adapter = neighborhoodAdapter(shown);
        list.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                shown.clear(); shown.addAll(db.neighborhoods(s.toString())); adapter.notifyDataSetChanged();
            }
            public void afterTextChanged(Editable e) {}
        });

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Adicionar entrega • " + DISPLAY.format(date))
                .setView(wrap)
                .setNegativeButton("Cancelar", null)
                .create();
        list.setOnItemClickListener((p, v, pos, id) -> {
            RouteDb.Neighborhood n = shown.get(pos);
            double fee = n.integral ? db.integralFee() : n.fee;
            if (n.integral && fee <= 0.0) {
                Toast.makeText(this, "Defina primeiro o valor da Taxa Integral.", Toast.LENGTH_LONG).show();
                dlg.dismiss();
                showIntegralFeeDialog(() -> showAddDeliveryDialog(date));
                return;
            }
            db.addDelivery(ISO.format(date), n.name, fee);
            dlg.dismiss();
            if (date.equals(LocalDate.now())) showHome(); else showDayDialog(date);
        });
        dlg.show();
    }

    private ArrayAdapter<RouteDb.Neighborhood> neighborhoodAdapter(List<RouteDb.Neighborhood> items) {
        return new ArrayAdapter<RouteDb.Neighborhood>(this, android.R.layout.simple_list_item_1, items) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                RouteDb.Neighborhood n = getItem(position);
                tv.setTextColor(Color.rgb(30,30,30));
                tv.setTextSize(16);
                tv.setPadding(dp(12), dp(10), dp(12), dp(10));
                double val = n.integral ? db.integralFee() : n.fee;
                String fee = n.integral ? "Taxa Integral" + (val > 0 ? " • " + money(val) : " • não definida") : money(val);
                tv.setText(n.name + "\n" + fee);
                return tv;
            }
        };
    }

    private View deliveryRow(RouteDb.Delivery d, boolean deletable) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(10), dp(10));
        row.setBackground(round(CARD, 14));
        LinearLayout txts = new LinearLayout(this);
        txts.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(d.neighborhood, 16, TEXT, true);
        TextView val = text(money(d.fee), 14, MUTED, false);
        txts.addView(name); txts.addView(val);
        row.addView(txts, weight());
        if (deletable) {
            Button del = button("×", Color.rgb(86, 41, 42));
            del.setTextSize(22);
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Excluir entrega?")
                    .setMessage(d.neighborhood + " • " + money(d.fee))
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Excluir", (dd, w) -> { db.deleteDelivery(d.id); showHome(); })
                    .show());
            row.addView(del, mp(dp(52), dp(46)));
        }
        return row;
    }

    private void showCalendar() {
        LinearLayout root = page();
        root.addView(topBar("Calendário anual", this::showHome));
        TextView hint = cardText("Toque em qualquer dia para ver as entregas. Abaixo você acompanha o resumo do ano inteiro.");
        root.addView(hint, margin(-1, -2, 16, 8, 16, 8));

        CalendarView cal = new CalendarView(this);
        cal.setFirstDayOfWeek(2);
        root.addView(cal, margin(-1, dp(310), 12, 4, 12, 8));
        LinearLayout selected = new LinearLayout(this);
        selected.setOrientation(LinearLayout.VERTICAL);
        root.addView(selected, margin(-1, -2, 16, 4, 16, 10));

        LocalDate now = LocalDate.now();
        renderSelectedDay(selected, now);
        cal.setOnDateChangeListener((view, year, month, day) -> renderSelectedDay(selected, LocalDate.of(year, month + 1, day)));

        root.addView(sectionTitle("Resumo de " + now.getYear()), margin(-1, -2, 16, 10, 16, 5));
        renderYearSummary(root, now.getYear());
        setPage(root);
    }

    private void renderSelectedDay(LinearLayout box, LocalDate date) {
        box.removeAllViews();
        RouteDb.DaySummary s = db.daySummary(ISO.format(date));
        TextView title = text(DISPLAY.format(date) + "  •  " + s.count + " entrega(s)  •  " + money(s.total), 16, TEXT, true);
        title.setPadding(dp(14), dp(12), dp(14), dp(12));
        title.setBackground(round(CARD_2, 14));
        title.setOnClickListener(v -> showDayDialog(date));
        box.addView(title, mp(-1, -2));
    }

    private void renderYearSummary(LinearLayout root, int year) {
        for (int m = 1; m <= 12; m++) {
            LocalDate start = LocalDate.of(year, m, 1);
            LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());
            RouteDb.DaySummary s = db.rangeSummary(ISO.format(start), ISO.format(end));
            String month = start.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
            TextView tv = cardText(cap(month) + "  •  " + s.count + " entrega(s)  •  " + money(s.total));
            root.addView(tv, margin(-1, -2, 16, 2, 16, 2));
        }
        root.addView(space(18));
    }

    private void showDayDialog(LocalDate date) {
        List<RouteDb.Delivery> list = db.deliveriesForDate(ISO.format(date));
        RouteDb.DaySummary s = db.daySummary(ISO.format(date));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(6), dp(12), dp(6));
        if (list.isEmpty()) body.addView(text("Nenhuma entrega nessa data.", 16, Color.DKGRAY, false));
        for (RouteDb.Delivery d : list) {
            TextView tv = text("• " + d.neighborhood + " — " + money(d.fee), 16, Color.DKGRAY, false);
            tv.setPadding(0, dp(6), 0, dp(6)); body.addView(tv);
        }
        new AlertDialog.Builder(this)
                .setTitle(DISPLAY.format(date) + " • " + s.count + " entrega(s) • " + money(s.total))
                .setView(body)
                .setPositiveButton("Adicionar entrega", (d,w) -> showAddDeliveryDialog(date))
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showHistory() {
        LinearLayout root = page();
        root.addView(topBar("Histórico", this::showHome));
        EditText search = edit("Pesquisar bairro ou data (ex.: Visconde ou 29/08/2026)");
        root.addView(search, margin(-1, dp(52), 16, 5, 16, 8));
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results, margin(-1, -2, 16, 0, 16, 20));
        Runnable render = () -> renderHistory(results, search.getText().toString());
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { render.run(); }
            public void afterTextChanged(Editable e) {}
        });
        render.run();
        setPage(root);
    }

    private void renderHistory(LinearLayout box, String filter) {
        box.removeAllViews();
        List<RouteDb.HistoryRow> rows = db.history(filter);
        int count = 0; double total = 0;
        for (RouteDb.HistoryRow r : rows) { count += r.count; total += r.total; }
        TextView summary = text(count + " entrega(s) • " + money(total), 17, TEXT, true);
        summary.setPadding(dp(14), dp(12), dp(14), dp(12)); summary.setBackground(round(CARD_2, 14));
        box.addView(summary, margin(-1, -2, 0, 0, 0, 8));
        if (rows.isEmpty()) box.addView(cardText("Nenhum registro encontrado."));
        for (RouteDb.HistoryRow r : rows) {
            String date = r.date;
            try { date = DISPLAY.format(LocalDate.parse(r.date, ISO)); } catch (Exception ignored) {}
            TextView tv = cardText(date + "\n" + r.neighborhood + " • " + r.count + " entrega(s) • " + money(r.total));
            box.addView(tv, margin(-1, -2, 0, 2, 0, 2));
        }
    }

    private void showReports() {
        LinearLayout root = page();
        root.addView(topBar("Relatórios", this::showHome));
        TextView desc = cardText("Escolha um período para ver quantidade, total recebido, média por entrega e bairros mais atendidos.");
        root.addView(desc, margin(-1, -2, 16, 8, 16, 12));

        LocalDate t = LocalDate.now();
        addReportButton(root, "HOJE", t, t);
        addReportButton(root, "ONTEM", t.minusDays(1), t.minusDays(1));
        addReportButton(root, "SEMANA", t.with(DayOfWeek.MONDAY), t);
        addReportButton(root, "MÊS", t.withDayOfMonth(1), t);
        addReportButton(root, "ANO", t.withDayOfYear(1), t);
        Button custom = button("PERÍODO PERSONALIZADO", CARD_2);
        custom.setOnClickListener(v -> pickCustomRange());
        root.addView(custom, margin(-1, dp(54), 16, 4, 16, 24));
        setPage(root);
    }

    private void addReportButton(LinearLayout root, String name, LocalDate start, LocalDate end) {
        Button b = button(name, CARD_2);
        b.setOnClickListener(v -> showReport(name, start, end));
        root.addView(b, margin(-1, dp(54), 16, 4, 16, 4));
    }

    private void pickCustomRange() {
        LocalDate now = LocalDate.now();
        final LocalDate[] start = new LocalDate[1];
        DatePickerDialog p1 = new DatePickerDialog(this, (v,y,m,d) -> {
            start[0] = LocalDate.of(y,m+1,d);
            DatePickerDialog p2 = new DatePickerDialog(this, (v2,y2,m2,d2) -> {
                LocalDate end = LocalDate.of(y2,m2+1,d2);
                if (end.isBefore(start[0])) { Toast.makeText(this, "A data final não pode ser anterior à inicial.", Toast.LENGTH_LONG).show(); return; }
                showReport("PERÍODO", start[0], end);
            }, now.getYear(), now.getMonthValue()-1, now.getDayOfMonth());
            p2.setTitle("Data final"); p2.show();
        }, now.getYear(), now.getMonthValue()-1, now.getDayOfMonth());
        p1.setTitle("Data inicial"); p1.show();
    }

    private void showReport(String title, LocalDate start, LocalDate end) {
        RouteDb.Report r = db.report(ISO.format(start), ISO.format(end));
        StringBuilder sb = new StringBuilder();
        sb.append(DISPLAY.format(start)).append(" a ").append(DISPLAY.format(end)).append("\n\n")
          .append("Quantidade de entregas: ").append(r.count).append("\n")
          .append("Valor total: ").append(money(r.total)).append("\n")
          .append("Média por entrega: ").append(money(r.count == 0 ? 0 : r.total / r.count)).append("\n")
          .append("Bairro com mais entregas: ").append(r.topNeighborhood == null ? "—" : r.topNeighborhood).append("\n\n")
          .append("Entregas por bairro:\n");
        if (r.byNeighborhood.isEmpty()) sb.append("Nenhuma entrega no período.");
        for (RouteDb.NeighborhoodStat s : r.byNeighborhood) sb.append("• ").append(s.name).append(": ").append(s.count).append(" • ").append(money(s.total)).append("\n");
        new AlertDialog.Builder(this).setTitle(title).setMessage(sb.toString()).setPositiveButton("OK", null).show();
    }

    private void showNeighborhoods() {
        LinearLayout root = page();
        root.addView(topBar("Bairros e taxas", this::showHome));
        double integral = db.integralFee();
        TextView intFee = cardText("Taxa Integral atual: " + (integral > 0 ? money(integral) : "NÃO DEFINIDA") + "\nToque para alterar.");
        intFee.setOnClickListener(v -> showIntegralFeeDialog(this::showNeighborhoods));
        root.addView(intFee, margin(-1, -2, 16, 6, 16, 8));

        Button add = button("+ NOVO BAIRRO", ACCENT);
        add.setOnClickListener(v -> editNeighborhood(null));
        root.addView(add, margin(-1, dp(54), 16, 4, 16, 10));

        EditText search = edit("Pesquisar bairro...");
        root.addView(search, margin(-1, dp(50), 16, 0, 16, 8));
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, margin(-1, -2, 16, 0, 16, 20));
        Runnable render = () -> renderNeighborhoodList(list, search.getText().toString());
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){render.run();}
            public void afterTextChanged(Editable e){}
        });
        render.run();
        setPage(root);
    }

    private void renderNeighborhoodList(LinearLayout box, String q) {
        box.removeAllViews();
        for (RouteDb.Neighborhood n : db.neighborhoods(q)) {
            double fee = n.integral ? db.integralFee() : n.fee;
            TextView tv = cardText(n.name + "\n" + (n.integral ? "Taxa Integral" + (fee > 0 ? " • " + money(fee) : "") : money(fee)));
            tv.setOnClickListener(v -> editNeighborhood(n));
            box.addView(tv, margin(-1, -2, 0, 2, 0, 2));
        }
    }

    private void editNeighborhood(RouteDb.Neighborhood n) {
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(16), dp(6), dp(16), 0);
        EditText name = edit("Nome do bairro");
        EditText fee = edit("Valor da taxa"); fee.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox integral = new CheckBox(this); integral.setText("Usar Taxa Integral");
        if (n != null) { name.setText(n.name); if (!n.integral) fee.setText(String.valueOf(n.fee).replace('.', ',')); integral.setChecked(n.integral); }
        body.addView(name, mp(-1, dp(52))); body.addView(fee, margin(-1, dp(52),0,6,0,0)); body.addView(integral, mp(-1, dp(48)));
        integral.setOnCheckedChangeListener((b, checked) -> fee.setEnabled(!checked)); fee.setEnabled(!integral.isChecked());

        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(n == null ? "Novo bairro" : "Editar bairro").setView(body).setNegativeButton("Cancelar", null);
        if (n != null) b.setNeutralButton("Excluir", (d,w) -> new AlertDialog.Builder(this).setTitle("Excluir " + n.name + "?").setMessage("O histórico de entregas já realizadas será mantido.").setNegativeButton("Cancelar", null).setPositiveButton("Excluir", (x,y)->{db.deleteNeighborhood(n.id); showNeighborhoods();}).show());
        b.setPositiveButton("Salvar", null);
        AlertDialog dlg = b.create(); dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nm = name.getText().toString().trim(); if (nm.isEmpty()) { name.setError("Informe o nome"); return; }
            double fv = 0; if (!integral.isChecked()) { try { fv = Double.parseDouble(fee.getText().toString().replace(',', '.')); } catch(Exception e){ fee.setError("Valor inválido"); return; } }
            boolean ok = n == null ? db.insertNeighborhood(nm, fv, integral.isChecked()) : db.updateNeighborhood(n.id, nm, fv, integral.isChecked());
            if (!ok) { name.setError("Já existe um bairro com esse nome"); return; }
            dlg.dismiss(); showNeighborhoods();
        })); dlg.show();
    }

    private void showIntegralFeeDialog(Runnable after) {
        EditText input = edit("Ex.: 15,00");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        double current = db.integralFee(); if (current > 0) input.setText(String.valueOf(current).replace('.', ','));
        LinearLayout wrap = new LinearLayout(this); wrap.setPadding(dp(20),0,dp(20),0); wrap.addView(input, mp(-1,dp(54)));
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Valor da Taxa Integral").setView(wrap).setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { double val = Double.parseDouble(input.getText().toString().replace(',', '.')); if (val <= 0) throw new Exception(); db.setIntegralFee(val); dlg.dismiss(); if (after != null) after.run(); }
            catch(Exception e) { input.setError("Informe um valor maior que zero"); }
        })); dlg.show();
    }

    private void showBackupMenu() {
        String[] options = {"Criar backup", "Restaurar backup"};
        new AlertDialog.Builder(this).setTitle("Backup e restauração").setItems(options, (d,which)->{
            if (which == 0) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE, "bora_maycon_backup_" + ISO.format(LocalDate.now()) + ".json"); startActivityForResult(i, REQ_BACKUP);
            } else {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/json"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, REQ_RESTORE);
            }
        }).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_BACKUP) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                out.write(db.exportJson().toString(2).getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Backup criado com sucesso.", Toast.LENGTH_LONG).show();
            } catch(Exception e) { Toast.makeText(this, "Falha ao criar backup: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        } else if (requestCode == REQ_RESTORE) {
            new AlertDialog.Builder(this).setTitle("Restaurar backup?").setMessage("Os dados atuais serão substituídos pelos dados do arquivo selecionado.").setNegativeButton("Cancelar", null).setPositiveButton("Restaurar", (d,w)->{
                try (InputStream in = getContentResolver().openInputStream(uri); BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line);
                    db.importJson(new JSONObject(sb.toString())); Toast.makeText(this, "Backup restaurado.", Toast.LENGTH_LONG).show(); showHome();
                } catch(Exception e) { Toast.makeText(this, "Falha ao restaurar: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
            }).show();
        }
    }

    private LinearLayout page() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setBackgroundColor(BG); l.setPadding(0,0,0,dp(12)); return l;
    }
    private void setPage(LinearLayout content) { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.setBackgroundColor(BG); s.addView(content, mp(-1,-2)); setContentView(s); }
    private View topBar(String title, Runnable back) {
        LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(10),dp(12),dp(16),dp(8));
        Button b = button("‹", CARD_2); b.setTextSize(28); b.setOnClickListener(v->back.run()); r.addView(b,mp(dp(52),dp(48)));
        TextView t = text(title,23,TEXT,true); t.setPadding(dp(12),0,0,0); r.addView(t,weight()); return r;
    }
    private LinearLayout row() { LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r; }
    private TextView text(String s,int sp,int color,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
    private TextView sectionTitle(String s){ return text(s,18,TEXT,true); }
    private TextView cardText(String s){ TextView t=text(s,15,TEXT,false); t.setLineSpacing(0,1.12f); t.setPadding(dp(14),dp(12),dp(14),dp(12)); t.setBackground(round(CARD,14)); return t; }
    private View statCard(String label,String value){ LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER); c.setPadding(dp(8),dp(14),dp(8),dp(14)); c.setBackground(round(CARD,16)); TextView v=text(value,22,TEXT,true); v.setGravity(Gravity.CENTER); TextView l=text(label,11,MUTED,true); l.setGravity(Gravity.CENTER); c.addView(v); c.addView(l); return c; }
    private Button button(String s,int color){ Button b=new Button(this); b.setText(s); b.setTextColor(TEXT); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setAllCaps(false); b.setBackgroundTintList(ColorStateList.valueOf(color)); b.setPadding(dp(8),0,dp(8),0); return b; }
    private EditText edit(String hint){ EditText e=new EditText(this); e.setHint(hint); e.setTextSize(16); e.setSingleLine(true); e.setPadding(dp(12),0,dp(12),0); return e; }
    private GradientDrawable round(int color,int radius){ GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }
    private LinearLayout.LayoutParams weight(){ return new LinearLayout.LayoutParams(0,-2,1f); }
    private LinearLayout.LayoutParams mp(int w,int h){ return new LinearLayout.LayoutParams(w,h); }
    private LinearLayout.LayoutParams margin(int w,int h,int l,int t,int r,int b){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p; }
    private View space(int h){ View v=new View(this); v.setLayoutParams(mp(1,dp(h))); return v; }
    private int dp(int n){ return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private String money(double v){ return NumberFormat.getCurrencyInstance(PT_BR).format(v); }
    private String cap(String s){ return s == null || s.isEmpty() ? s : s.substring(0,1).toUpperCase(PT_BR)+s.substring(1); }

    public static final class RouteDb extends SQLiteOpenHelper {
        private static final String DB = "bora_maycon_hi_hi.db";
        RouteDb(Context c){ super(c, DB, null, 1); }
        @Override public void onCreate(SQLiteDatabase d){
            d.execSQL("CREATE TABLE neighborhoods(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE COLLATE NOCASE,fee REAL NOT NULL DEFAULT 0,integral INTEGER NOT NULL DEFAULT 0)");
            d.execSQL("CREATE TABLE deliveries(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT NOT NULL,neighborhood TEXT NOT NULL,fee REAL NOT NULL,created_at INTEGER NOT NULL)");
            d.execSQL("CREATE INDEX idx_deliveries_date ON deliveries(date)");
            d.execSQL("CREATE INDEX idx_deliveries_neighborhood ON deliveries(neighborhood)");
            d.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT)");
            seed(d);
        }
        @Override public void onUpgrade(SQLiteDatabase d,int oldV,int newV){}

        private void seed(SQLiteDatabase d){
            Object[][] data = new Object[][]{
                {"Águas Maravilhosas",8d,false},{"Ajuda de Cima",7d,false},{"Ajuda de Baixo",6d,false},{"Aroeira",3d,false},{"Atlântico Norte",7d,false},
                {"Barra",4d,false},{"Barreto",8d,false},{"Barramares",7d,false},{"Bela Vista",4d,false},{"Bosque Azul",7d,false},{"Botafogo",5d,false},{"Brasília",4d,false},{"Brisa do Vale",8d,false},
                {"Cabiúnas",15d,false},{"Cajueiros",3d,false},{"Campo D'Oeste",4d,false},{"Cancela Preta",7d,false},{"Cavaleiros",7d,false},{"Centro",3d,false},{"Costa do Sol",4d,false},
                {"Engenho da Praia",13d,false},{"Franco Plaza",7d,false},{"Fronteira",4d,false},{"Glória",8d,false},{"Granja dos Cavaleiros",10d,false},{"Horto",15d,false},{"Imbetiba",3d,false},{"Ilha Leocádia",10d,false},{"Imburo",0d,true},
                {"Jardim Carioca 1",6d,false},{"Jardim Esperança",5d,false},{"Jardim Carioca 2",7d,false},{"Jardim Guanabara",12d,false},{"Jardim Maringá",5d,false},{"Jardim Santo Antônio",5d,false},{"Jardim Vitória",5d,false},{"Jardim Franco",7d,false},
                {"Lagoa",10d,false},{"Lagomar",13d,false},{"Maracaibo/Monza",7d,false},{"Marville",7d,false},{"Malvinas",5d,false},{"Miramar",3d,false},{"Mirante da Lagoa",12d,false},{"Morro de Santa Mônica",5d,false},
                {"Nova Esperança",7d,false},{"Nova Holanda",7d,false},{"Nova Macaé",5d,false},{"Novo Cavaleiros",10d,false},{"Novo Horizonte",5d,false},
                {"Parque Aeroporto",7d,false},{"Parque Atlântico",7d,false},{"Parque União",7d,false},{"Praia do Pecado",9d,false},{"Piracema",8d,false},{"Planalto da Ajuda",7d,false},{"Praia Campista",5d,false},
                {"Riviera",5d,false},{"São Marcos",11d,false},{"Sol e Mar",5d,false},{"Santa Mônica",5d,false},{"Vale das Palmeiras",15d,false},{"Vale dos Cristais",15d,false},{"Vale Encantado",0d,true},{"Verdes Mares",7d,false},
                {"Vila Badejo",5d,false},{"Vila Moreira",12d,false},{"Vill. do Horto",15d,false},{"Virgem Santa",7d,false},{"Visconde",3d,false},{"Itaparica",3d,false},
                {"Quinta da Boa Vista",0d,true},{"Fazenda depois da Virgem Santa",0d,true},{"Virgem Santa depois do posto de saúde",0d,true}
            };
            for(Object[] a:data){ ContentValues v=new ContentValues(); v.put("name",(String)a[0]); v.put("fee",(Double)a[1]); v.put("integral",(Boolean)a[2]?1:0); d.insert("neighborhoods",null,v); }
            ContentValues s=new ContentValues(); s.put("key","integral_fee"); s.put("value","0"); d.insert("settings",null,s);
        }

        List<Neighborhood> neighborhoods(String q){ ArrayList<Neighborhood> out=new ArrayList<>(); String like="%"+(q==null?"":q.trim())+"%"; Cursor c=getReadableDatabase().rawQuery("SELECT id,name,fee,integral FROM neighborhoods WHERE name LIKE ? ORDER BY name COLLATE NOCASE",new String[]{like}); try{while(c.moveToNext())out.add(new Neighborhood(c.getLong(0),c.getString(1),c.getDouble(2),c.getInt(3)==1));}finally{c.close();} return out; }
        boolean insertNeighborhood(String name,double fee,boolean integral){ try{ContentValues v=nv(name,fee,integral); return getWritableDatabase().insertOrThrow("neighborhoods",null,v)>0;}catch(Exception e){return false;} }
        boolean updateNeighborhood(long id,String name,double fee,boolean integral){ try{return getWritableDatabase().update("neighborhoods",nv(name,fee,integral),"id=?",new String[]{String.valueOf(id)})>0;}catch(Exception e){return false;} }
        void deleteNeighborhood(long id){getWritableDatabase().delete("neighborhoods","id=?",new String[]{String.valueOf(id)});}
        private ContentValues nv(String name,double fee,boolean integral){ContentValues v=new ContentValues();v.put("name",name);v.put("fee",fee);v.put("integral",integral?1:0);return v;}
        double integralFee(){ Cursor c=getReadableDatabase().rawQuery("SELECT value FROM settings WHERE key='integral_fee'",null); try{return c.moveToFirst()?Double.parseDouble(c.getString(0)):0;}catch(Exception e){return 0;}finally{c.close();} }
        void setIntegralFee(double val){ContentValues v=new ContentValues();v.put("value",String.valueOf(val));getWritableDatabase().update("settings",v,"key='integral_fee'",null);}
        void addDelivery(String date,String neighborhood,double fee){ContentValues v=new ContentValues();v.put("date",date);v.put("neighborhood",neighborhood);v.put("fee",fee);v.put("created_at",System.currentTimeMillis());getWritableDatabase().insert("deliveries",null,v);}
        void deleteDelivery(long id){getWritableDatabase().delete("deliveries","id=?",new String[]{String.valueOf(id)});}
        List<Delivery> deliveriesForDate(String date){ArrayList<Delivery> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT id,date,neighborhood,fee FROM deliveries WHERE date=? ORDER BY id DESC",new String[]{date});try{while(c.moveToNext())out.add(new Delivery(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3)));}finally{c.close();}return out;}
        DaySummary daySummary(String date){return rangeSummary(date,date);}
        DaySummary rangeSummary(String start,String end){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(SUM(fee),0) FROM deliveries WHERE date BETWEEN ? AND ?",new String[]{start,end});try{if(c.moveToFirst())return new DaySummary(c.getInt(0),c.getDouble(1));return new DaySummary(0,0);}finally{c.close();}}
        List<HistoryRow> history(String filter){ArrayList<HistoryRow> out=new ArrayList<>();String f=filter==null?"":filter.trim();String iso=f;try{if(f.matches("\\d{2}/\\d{2}/\\d{4}"))iso=ISO.format(LocalDate.parse(f,DISPLAY));}catch(Exception ignored){}String like="%"+f+"%",likeIso="%"+iso+"%";Cursor c=getReadableDatabase().rawQuery("SELECT date,neighborhood,COUNT(*),SUM(fee) FROM deliveries WHERE neighborhood LIKE ? OR date LIKE ? GROUP BY date,neighborhood ORDER BY date DESC,neighborhood COLLATE NOCASE LIMIT 500",new String[]{like,likeIso});try{while(c.moveToNext())out.add(new HistoryRow(c.getString(0),c.getString(1),c.getInt(2),c.getDouble(3)));}finally{c.close();}return out;}
        Report report(String start,String end){DaySummary ds=rangeSummary(start,end);ArrayList<NeighborhoodStat> list=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT neighborhood,COUNT(*),SUM(fee) FROM deliveries WHERE date BETWEEN ? AND ? GROUP BY neighborhood ORDER BY COUNT(*) DESC,neighborhood COLLATE NOCASE",new String[]{start,end});String top=null;try{while(c.moveToNext()){NeighborhoodStat s=new NeighborhoodStat(c.getString(0),c.getInt(1),c.getDouble(2));if(top==null)top=s.name;list.add(s);}}finally{c.close();}return new Report(ds.count,ds.total,top,list);}

        JSONObject exportJson() throws Exception {JSONObject root=new JSONObject();root.put("app","Bora Maycon Hi Hi");root.put("version",1);JSONArray ns=new JSONArray();Cursor c=getReadableDatabase().rawQuery("SELECT name,fee,integral FROM neighborhoods ORDER BY id",null);try{while(c.moveToNext()){JSONObject o=new JSONObject();o.put("name",c.getString(0));o.put("fee",c.getDouble(1));o.put("integral",c.getInt(2));ns.put(o);}}finally{c.close();}root.put("neighborhoods",ns);JSONArray ds=new JSONArray();c=getReadableDatabase().rawQuery("SELECT date,neighborhood,fee,created_at FROM deliveries ORDER BY id",null);try{while(c.moveToNext()){JSONObject o=new JSONObject();o.put("date",c.getString(0));o.put("neighborhood",c.getString(1));o.put("fee",c.getDouble(2));o.put("created_at",c.getLong(3));ds.put(o);}}finally{c.close();}root.put("deliveries",ds);root.put("integral_fee",integralFee());return root;}
        void importJson(JSONObject root) throws Exception {JSONArray ns=root.getJSONArray("neighborhoods");JSONArray ds=root.getJSONArray("deliveries");SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.delete("deliveries",null,null);d.delete("neighborhoods",null,null);for(int i=0;i<ns.length();i++){JSONObject o=ns.getJSONObject(i);ContentValues v=new ContentValues();v.put("name",o.getString("name"));v.put("fee",o.optDouble("fee",0));v.put("integral",o.optInt("integral",0));d.insertOrThrow("neighborhoods",null,v);}for(int i=0;i<ds.length();i++){JSONObject o=ds.getJSONObject(i);ContentValues v=new ContentValues();v.put("date",o.getString("date"));v.put("neighborhood",o.getString("neighborhood"));v.put("fee",o.getDouble("fee"));v.put("created_at",o.optLong("created_at",System.currentTimeMillis()));d.insertOrThrow("deliveries",null,v);}ContentValues v=new ContentValues();v.put("value",String.valueOf(root.optDouble("integral_fee",0)));d.update("settings",v,"key='integral_fee'",null);d.setTransactionSuccessful();}finally{d.endTransaction();}}

        static final class Neighborhood {final long id;final String name;final double fee;final boolean integral;Neighborhood(long i,String n,double f,boolean t){id=i;name=n;fee=f;integral=t;}@Override public String toString(){return name;}}
        static final class Delivery {final long id;final String date,neighborhood;final double fee;Delivery(long i,String d,String n,double f){id=i;date=d;neighborhood=n;fee=f;}}
        static final class DaySummary {final int count;final double total;DaySummary(int c,double t){count=c;total=t;}}
        static final class HistoryRow {final String date,neighborhood;final int count;final double total;HistoryRow(String d,String n,int c,double t){date=d;neighborhood=n;count=c;total=t;}}
        static final class NeighborhoodStat {final String name;final int count;final double total;NeighborhoodStat(String n,int c,double t){name=n;count=c;total=t;}}
        static final class Report {final int count;final double total;final String topNeighborhood;final List<NeighborhoodStat> byNeighborhood;Report(int c,double t,String top,List<NeighborhoodStat> list){count=c;total=t;topNeighborhood=top;byNeighborhood=list;}}
    }
}
