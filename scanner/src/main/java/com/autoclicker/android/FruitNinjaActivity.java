package com.autoclicker.android;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class FruitNinjaActivity extends Activity {
    private static final int REQ_CAPTURE = 2401;
    private static final int REQ_NOTIFICATIONS = 2402;
    private static final String FRUIT_NINJA_PACKAGE = "com.halfbrick.fruitninjafree";

    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(7, 13, 9));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(26));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        art.setImageResource(R.drawable.ic_fruit_guard);
        art.setPadding(dp(22), dp(22), dp(22), dp(22));
        art.setBackgroundColor(Color.rgb(12, 28, 16));
        root.addView(art, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));

        TextView title = text("MASTER TOOLS • FRUIT GUARD", 23, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, full(dp(60)));

        TextView subtitle = text(
                "Detector visual para Fruit Ninja. Prioriza cortar frutas em movimento e cria uma zona de segurança ao redor das bombas detectadas.",
                14,
                Color.rgb(205, 235, 210));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(dp(4), dp(6), dp(4), dp(12));
        root.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        status = text("", 14, Color.rgb(120, 240, 140));
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(4), dp(10), dp(4), dp(10));
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Button accessibility = button("1. ATIVAR ACESSIBILIDADE FRUIT GUARD");
        accessibility.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Throwable t) {
                Toast.makeText(this, "Não foi possível abrir Acessibilidade", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(accessibility, full(dp(58)));

        Button capture = button("2. INICIAR CAPTURA DA TELA");
        capture.setOnClickListener(v -> requestCapture());
        root.addView(capture, full(dp(58)));

        Button openGame = button("3. ABRIR FRUIT NINJA");
        openGame.setOnClickListener(v -> openFruitNinja());
        root.addView(openGame, full(dp(58)));

        Button stop = button("PARAR CAPTURA");
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, FruitNinjaCaptureService.class);
            i.setAction(FruitNinjaCaptureService.ACTION_STOP);
            try { startService(i); } catch (Throwable ignored) {}
            FruitNinjaBus.captureRunning = false;
            FruitNinjaBus.captureStatus = "Captura parada";
            updateStatus();
        });
        root.addView(stop, full(dp(52)));

        TextView instructions = text(
                "ORDEM: ative a Acessibilidade → autorize a captura → abra o Fruit Ninja. "
                        + "No painel flutuante, deixe MODO SEGURO e então ligue CORTE AUTOMÁTICO. "
                        + "Se uma fruta estiver dentro da margem prevista de uma bomba, ela será ignorada.",
                13,
                Color.LTGRAY);
        instructions.setPadding(dp(4), dp(16), dp(4), dp(8));
        root.addView(instructions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView note = text(
                "Versão separada: pode ficar instalada junto com o Master Tools normal.",
                12,
                Color.rgb(130, 200, 145));
        note.setGravity(Gravity.CENTER);
        root.addView(note, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scroll);
        maybeRequestNotificationPermission();
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void requestCapture() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            Toast.makeText(this, "Captura de tela não disponível", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivityForResult(manager.createScreenCaptureIntent(), REQ_CAPTURE);
        } catch (Throwable t) {
            Toast.makeText(this, "Falha ao solicitar captura", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Captura não autorizada", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent service = new Intent(this, FruitNinjaCaptureService.class);
        service.putExtra("resultCode", resultCode);
        service.putExtra("data", data);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            FruitNinjaBus.captureStatus = "Iniciando captura...";
            updateStatus();
        } catch (Throwable t) {
            Toast.makeText(this, "Erro ao iniciar captura: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void openFruitNinja() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(FRUIT_NINJA_PACKAGE);
        if (launch == null) {
            Toast.makeText(this, "Fruit Ninja não encontrado no aparelho", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } catch (Throwable t) {
            Toast.makeText(this, "Não foi possível abrir o Fruit Ninja", Toast.LENGTH_LONG).show();
        }
    }

    private void updateStatus() {
        if (status == null) return;
        boolean accessibility = isFruitGuardAccessibilityEnabled();
        String a = accessibility ? "Acessibilidade: ATIVA" : "Acessibilidade: DESATIVADA";
        String c = FruitNinjaBus.captureRunning ? "Captura: ATIVA" : "Captura: PARADA";
        status.setText(a + "\n" + c + "\n" + FruitNinjaBus.captureStatus);
    }

    private boolean isFruitGuardAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> list =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        if (list == null) return false;
        ComponentName wanted = new ComponentName(this, FruitNinjaAssistService.class);
        String wantedFlat = wanted.flattenToString();
        for (AccessibilityServiceInfo info : list) {
            if (info == null || info.getResolveInfo() == null
                    || info.getResolveInfo().serviceInfo == null) continue;
            ComponentName got = new ComponentName(
                    info.getResolveInfo().serviceInfo.packageName,
                    info.getResolveInfo().serviceInfo.name);
            if (wantedFlat.equals(got.flattenToString())) return true;
        }
        return false;
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATIONS);
        }
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(12);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private LinearLayout.LayoutParams full(int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h);
        p.setMargins(0, dp(5), 0, dp(5));
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
