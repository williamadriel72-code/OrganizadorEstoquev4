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
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
    public static final String PREFS = "autoclicker_prefs";
    public static final String KEY_QTY = "quantity";
    public static final String KEY_INTERVAL = "interval_ms";
    public static final String KEY_UNLIMITED = "unlimited";
    public static final String KEY_MODE = "action_mode";
    public static final String KEY_DIRECTION = "swipe_direction";
    public static final String KEY_SWIPE_DURATION = "swipe_duration_ms";

    private EditText quantityInput;
    private EditText intervalInput;
    private EditText swipeDurationInput;
    private CheckBox unlimitedCheck;
    private Spinner modeSpinner;
    private Spinner directionSpinner;
    private TextView directionLabel;
    private TextView durationLabel;
    private TextView accessibilityStatus;

    private static final String[] MODES = {"CLIQUE", "DESLIZAR"};
    private static final String[] DIRECTIONS = {"CIMA", "BAIXO", "ESQUERDA", "DIREITA"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(18), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(245, 247, 250));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("AUTOCLICKER ANDROID");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(18, 24, 33));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, fullWidth(dp(52)));

        TextView description = new TextView(this);
        description.setText("Escolha CLIQUE ou DESLIZAR. No modo DESLIZAR você pode passar vídeos/telas automaticamente para cima, baixo, esquerda ou direita.");
        description.setTextSize(14);
        description.setTextColor(Color.DKGRAY);
        description.setGravity(Gravity.CENTER);
        root.addView(description, fullWidth(dp(88)));

        accessibilityStatus = new TextView(this);
        accessibilityStatus.setTextSize(16);
        accessibilityStatus.setGravity(Gravity.CENTER);
        accessibilityStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(accessibilityStatus, fullWidth(dp(40)));

        root.addView(label("Modo"), fullWidth(dp(28)));
        modeSpinner = spinner(MODES);
        String savedMode = prefs.getString(KEY_MODE, "click");
        modeSpinner.setSelection("swipe".equals(savedMode) ? 1 : 0);
        root.addView(modeSpinner, fullWidth(dp(52)));

        directionLabel = label("Direção do deslize");
        root.addView(directionLabel, fullWidth(dp(28)));
        directionSpinner = spinner(DIRECTIONS);
        directionSpinner.setSelection(directionIndex(prefs.getString(KEY_DIRECTION, "up")));
        root.addView(directionSpinner, fullWidth(dp(52)));

        root.addView(label("Quantidade de ações (1 a 100)"), fullWidth(dp(28)));
        quantityInput = numberInput(String.valueOf(prefs.getInt(KEY_QTY, 100)));
        root.addView(quantityInput, fullWidth(dp(48)));

        unlimitedCheck = new CheckBox(this);
        unlimitedCheck.setText("ILIMITADO — só para quando tocar em PARAR");
        unlimitedCheck.setTextSize(14);
        unlimitedCheck.setTextColor(Color.rgb(25, 30, 40));
        unlimitedCheck.setChecked(prefs.getBoolean(KEY_UNLIMITED, false));
        unlimitedCheck.setOnCheckedChangeListener((buttonView, isChecked) ->
                quantityInput.setEnabled(!isChecked));
        quantityInput.setEnabled(!unlimitedCheck.isChecked());
        root.addView(unlimitedCheck, fullWidth(dp(54)));

        root.addView(label("Tempo entre ações (ms) — 1000 ms = 1 segundo"), fullWidth(dp(34)));
        intervalInput = numberInput(String.valueOf(prefs.getInt(KEY_INTERVAL, 1000)));
        root.addView(intervalInput, fullWidth(dp(48)));

        durationLabel = label("Duração de cada deslize (ms) — ex.: 250 ms");
        root.addView(durationLabel, fullWidth(dp(34)));
        swipeDurationInput = numberInput(String.valueOf(prefs.getInt(KEY_SWIPE_DURATION, 250)));
        root.addView(swipeDurationInput, fullWidth(dp(48)));

        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateModeControls();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        updateModeControls();

        Button save = new Button(this);
        save.setText("SALVAR CONFIGURAÇÕES");
        save.setOnClickListener(v -> saveSettings());
        root.addView(save, fullWidth(dp(54)));

        Button accessibility = new Button(this);
        accessibility.setText("ATIVAR ACESSIBILIDADE");
        accessibility.setOnClickListener(v -> {
            saveSettings();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Procure por AutoClicker Android e ative o serviço.", Toast.LENGTH_LONG).show();
        });
        root.addView(accessibility, fullWidth(dp(58)));

        Button floating = new Button(this);
        floating.setText("MINIMIZAR E DEIXAR FLUTUANDO");
        floating.setOnClickListener(v -> {
            saveSettings();
            if (!isServiceEnabled(this, AutoClickService.class)) {
                Toast.makeText(this, "Ative a Acessibilidade primeiro.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            Toast.makeText(this, "Painel flutuante ativo.", Toast.LENGTH_SHORT).show();
            moveTaskToBack(true);
        });
        root.addView(floating, fullWidth(dp(58)));

        TextView help = new TextView(this);
        help.setText("No modo DESLIZAR, a direção indica para onde o dedo virtual vai. CIMA costuma avançar para o próximo vídeo em feeds verticais. O intervalo define quanto tempo esperar antes da próxima passada.");
        help.setTextSize(13);
        help.setTextColor(Color.GRAY);
        help.setGravity(Gravity.CENTER);
        root.addView(help, fullWidth(dp(104)));

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
    }

    private void updateModeControls() {
        boolean swipe = modeSpinner != null && modeSpinner.getSelectedItemPosition() == 1;
        if (directionSpinner != null) directionSpinner.setEnabled(swipe);
        if (swipeDurationInput != null) swipeDurationInput.setEnabled(swipe);
        if (directionLabel != null) directionLabel.setTextColor(swipe ? Color.rgb(40,45,55) : Color.GRAY);
        if (durationLabel != null) durationLabel.setTextColor(swipe ? Color.rgb(40,45,55) : Color.GRAY);
    }

    private void saveSettings() {
        int qty = clamp(parse(quantityInput.getText().toString(), 100), 1, 100);
        int interval = clamp(parse(intervalInput.getText().toString(), 1000), 10, 600000);
        int swipeDuration = clamp(parse(swipeDurationInput.getText().toString(), 250), 50, 5000);
        boolean unlimited = unlimitedCheck.isChecked();
        String mode = modeSpinner.getSelectedItemPosition() == 1 ? "swipe" : "click";
        String direction = directionValue(directionSpinner.getSelectedItemPosition());

        quantityInput.setText(String.valueOf(qty));
        intervalInput.setText(String.valueOf(interval));
        swipeDurationInput.setText(String.valueOf(swipeDuration));

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_QTY, qty)
                .putInt(KEY_INTERVAL, interval)
                .putInt(KEY_SWIPE_DURATION, swipeDuration)
                .putBoolean(KEY_UNLIMITED, unlimited)
                .putString(KEY_MODE, mode)
                .putString(KEY_DIRECTION, direction)
                .apply();

        Toast.makeText(this,
                "swipe".equals(mode) ? "Modo DESLIZAR salvo." : "Modo CLIQUE salvo.",
                Toast.LENGTH_SHORT).show();
    }

    private void updateAccessibilityStatus() {
        boolean enabled = isServiceEnabled(this, AutoClickService.class);
        accessibilityStatus.setText(enabled ? "ACESSIBILIDADE ATIVA ✓" : "ACESSIBILIDADE DESATIVADA");
        accessibilityStatus.setTextColor(enabled ? Color.rgb(0, 140, 70) : Color.rgb(190, 45, 45));
    }

    private static boolean isServiceEnabled(Context context, Class<?> serviceClass) {
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null) return false;
        ComponentName expected = new ComponentName(context, serviceClass);
        for (String entry : enabledServices.split(":")) {
            ComponentName current = ComponentName.unflattenFromString(entry);
            if (expected.equals(current)) return true;
        }
        return false;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
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

    private int directionIndex(String direction) {
        if ("down".equals(direction)) return 1;
        if ("left".equals(direction)) return 2;
        if ("right".equals(direction)) return 3;
        return 0;
    }

    private String directionValue(int index) {
        if (index == 1) return "down";
        if (index == 2) return "left";
        if (index == 3) return "right";
        return "up";
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
