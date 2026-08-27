package com.autoclicker.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    public static final String PREFS = "fruit_ninja_prefs";
    public static final String KEY_PATTERN = "pattern";
    public static final String KEY_GESTURE_MS = "gesture_ms";
    public static final String KEY_PAUSE_MS = "pause_ms";
    public static final String KEY_RUN_SECONDS = "run_seconds";
    public static final String KEY_UNLIMITED = "unlimited";

    private static final String[] PATTERNS = {
            "TELA TODA", "CÍRCULO", "ZIGUE-ZAGUE"
    };

    private Spinner patternSpinner;
    private EditText gestureInput;
    private EditText pauseInput;
    private EditText secondsInput;
    private CheckBox unlimitedCheck;
    private TextView accessibilityStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(28));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(246, 247, 250));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("FRUIT NINJA AUTO SWIPE");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(20, 25, 35));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, fullWidth(dp(56)));

        TextView desc = new TextView(this);
        desc.setText("Faz arrastos contínuos pela tela, como cortes de Fruit Ninja. O botão PARAR fica flutuando enquanto estiver rodando.");
        desc.setTextSize(14);
        desc.setTextColor(Color.DKGRAY);
        desc.setGravity(Gravity.CENTER);
        root.addView(desc, fullWidth(dp(82)));

        accessibilityStatus = new TextView(this);
        accessibilityStatus.setTextSize(16);
        accessibilityStatus.setGravity(Gravity.CENTER);
        accessibilityStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(accessibilityStatus, fullWidth(dp(42)));

        root.addView(label("Movimento"), fullWidth(dp(28)));
        patternSpinner = new Spinner(this);
        ArrayAdapter<String> patternAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, PATTERNS);
        patternAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        patternSpinner.setAdapter(patternAdapter);
        patternSpinner.setSelection(clamp(prefs.getInt(KEY_PATTERN, 0), 0, 2));
        root.addView(patternSpinner, fullWidth(dp(52)));

        root.addView(label("Velocidade do movimento (ms) — menor = mais rápido"), fullWidth(dp(34)));
        gestureInput = numberInput(String.valueOf(prefs.getInt(KEY_GESTURE_MS, 380)));
        root.addView(gestureInput, fullWidth(dp(48)));

        root.addView(label("Pausa entre movimentos (ms)"), fullWidth(dp(30)));
        pauseInput = numberInput(String.valueOf(prefs.getInt(KEY_PAUSE_MS, 20)));
        root.addView(pauseInput, fullWidth(dp(48)));

        root.addView(label("Tempo total rodando (segundos)"), fullWidth(dp(30)));
        secondsInput = numberInput(String.valueOf(prefs.getInt(KEY_RUN_SECONDS, 30)));
        root.addView(secondsInput, fullWidth(dp(48)));

        unlimitedCheck = new CheckBox(this);
        unlimitedCheck.setText("ILIMITADO — só para quando tocar em PARAR");
        unlimitedCheck.setTextSize(14);
        unlimitedCheck.setChecked(prefs.getBoolean(KEY_UNLIMITED, false));
        unlimitedCheck.setOnCheckedChangeListener((buttonView, isChecked) ->
                secondsInput.setEnabled(!isChecked));
        secondsInput.setEnabled(!unlimitedCheck.isChecked());
        root.addView(unlimitedCheck, fullWidth(dp(56)));

        Button save = new Button(this);
        save.setText("SALVAR CONFIGURAÇÕES");
        save.setOnClickListener(v -> saveSettings());
        root.addView(save, fullWidth(dp(56)));

        Button accessibility = new Button(this);
        accessibility.setText("ATIVAR ACESSIBILIDADE");
        accessibility.setOnClickListener(v -> {
            saveSettings();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this,
                    "Procure por Fruit Ninja Auto Swipe e ative o serviço.",
                    Toast.LENGTH_LONG).show();
        });
        root.addView(accessibility, fullWidth(dp(60)));

        Button floating = new Button(this);
        floating.setText("ABRIR PAINEL FLUTUANTE");
        floating.setOnClickListener(v -> {
            saveSettings();
            if (!isServiceEnabled(this, AutoClickService.class)) {
                Toast.makeText(this, "Ative a Acessibilidade primeiro.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            Toast.makeText(this, "Painel flutuante pronto.", Toast.LENGTH_SHORT).show();
            moveTaskToBack(true);
        });
        root.addView(floating, fullWidth(dp(60)));

        TextView help = new TextView(this);
        help.setText("Sugestão: comece com 380 ms e pausa de 20 ms. Se ficar rápido demais, aumente o valor de velocidade. Durante a execução, toque no botão vermelho PARAR para interromper.");
        help.setTextSize(13);
        help.setTextColor(Color.GRAY);
        help.setGravity(Gravity.CENTER);
        root.addView(help, fullWidth(dp(110)));

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
    }

    private void saveSettings() {
        int pattern = patternSpinner.getSelectedItemPosition();
        int gestureMs = clamp(parse(gestureInput.getText().toString(), 380), 120, 5000);
        int pauseMs = clamp(parse(pauseInput.getText().toString(), 20), 0, 10000);
        int runSeconds = clamp(parse(secondsInput.getText().toString(), 30), 1, 3600);
        boolean unlimited = unlimitedCheck.isChecked();

        gestureInput.setText(String.valueOf(gestureMs));
        pauseInput.setText(String.valueOf(pauseMs));
        secondsInput.setText(String.valueOf(runSeconds));

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt(KEY_PATTERN, pattern)
                .putInt(KEY_GESTURE_MS, gestureMs)
                .putInt(KEY_PAUSE_MS, pauseMs)
                .putInt(KEY_RUN_SECONDS, runSeconds)
                .putBoolean(KEY_UNLIMITED, unlimited)
                .apply();

        Toast.makeText(this, "Configurações salvas.", Toast.LENGTH_SHORT).show();
    }

    private void updateAccessibilityStatus() {
        boolean enabled = isServiceEnabled(this, AutoClickService.class);
        accessibilityStatus.setText(enabled ? "ACESSIBILIDADE ATIVA ✓" : "ACESSIBILIDADE DESATIVADA");
        accessibilityStatus.setTextColor(enabled
                ? Color.rgb(0, 145, 70)
                : Color.rgb(190, 45, 45));
    }

    private static boolean isServiceEnabled(Context context, Class<?> serviceClass) {
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;
        ComponentName expected = new ComponentName(context, serviceClass);
        for (String entry : enabledServices.split(":")) {
            ComponentName current = ComponentName.unflattenFromString(entry);
            if (expected.equals(current)) return true;
        }
        return false;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.rgb(40, 45, 55));
        view.setGravity(Gravity.BOTTOM | Gravity.START);
        return view;
    }

    private EditText numberInput(String value) {
        EditText edit = new EditText(this);
        edit.setText(value);
        edit.setTextSize(18);
        edit.setGravity(Gravity.CENTER);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        return edit;
    }

    private LinearLayout.LayoutParams fullWidth(int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        lp.setMargins(0, dp(3), 0, dp(3));
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int parse(String text, int fallback) {
        try { return Integer.parseInt(text.trim()); }
        catch (Exception e) { return fallback; }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
