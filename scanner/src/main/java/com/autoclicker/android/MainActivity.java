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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    public static final String PREFS = "autoclicker_prefs";
    public static final String KEY_QTY = "quantity";
    public static final String KEY_INTERVAL = "interval_ms";
    public static final String KEY_UNLIMITED = "unlimited";

    private EditText quantityInput;
    private EditText intervalInput;
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
        root.setPadding(dp(24), dp(20), dp(24), dp(20));
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
        description.setText("Ative a Acessibilidade. Depois use o controle flutuante: MARCAR PONTO → toque no local → INICIAR.");
        description.setTextSize(15);
        description.setTextColor(Color.DKGRAY);
        description.setGravity(Gravity.CENTER);
        root.addView(description, fullWidth(dp(72)));

        accessibilityStatus = new TextView(this);
        accessibilityStatus.setTextSize(16);
        accessibilityStatus.setGravity(Gravity.CENTER);
        accessibilityStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(accessibilityStatus, fullWidth(dp(40)));

        TextView qLabel = label("Quantidade de cliques (1 a 100)");
        root.addView(qLabel, fullWidth(dp(30)));

        quantityInput = numberInput(String.valueOf(prefs.getInt(KEY_QTY, 100)));
        root.addView(quantityInput, fullWidth(dp(48)));

        unlimitedCheck = new CheckBox(this);
        unlimitedCheck.setText("CLIQUE ILIMITADO — só para quando tocar em PARAR");
        unlimitedCheck.setTextSize(14);
        unlimitedCheck.setTextColor(Color.rgb(25, 30, 40));
        unlimitedCheck.setChecked(prefs.getBoolean(KEY_UNLIMITED, false));
        unlimitedCheck.setOnCheckedChangeListener((buttonView, isChecked) -> quantityInput.setEnabled(!isChecked));
        quantityInput.setEnabled(!unlimitedCheck.isChecked());
        root.addView(unlimitedCheck, fullWidth(dp(54)));

        TextView iLabel = label("Intervalo em milissegundos (mínimo 10 ms)");
        root.addView(iLabel, fullWidth(dp(30)));

        intervalInput = numberInput(String.valueOf(prefs.getInt(KEY_INTERVAL, 100)));
        root.addView(intervalInput, fullWidth(dp(48)));

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
        help.setText("No modo ILIMITADO, o AutoClicker continua até você tocar em PARAR. O painel e o marcador do ponto continuam flutuando sobre os outros aplicativos.");
        help.setTextSize(13);
        help.setTextColor(Color.GRAY);
        help.setGravity(Gravity.CENTER);
        root.addView(help, fullWidth(dp(84)));

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
    }

    private void saveSettings() {
        int qty = parse(quantityInput.getText().toString(), 100);
        int interval = parse(intervalInput.getText().toString(), 100);
        boolean unlimited = unlimitedCheck.isChecked();

        qty = Math.max(1, Math.min(100, qty));
        interval = Math.max(10, Math.min(60000, interval));

        quantityInput.setText(String.valueOf(qty));
        intervalInput.setText(String.valueOf(interval));

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_QTY, qty)
                .putInt(KEY_INTERVAL, interval)
                .putBoolean(KEY_UNLIMITED, unlimited)
                .apply();

        Toast.makeText(this, unlimited ? "Modo ilimitado salvo." : "Configurações salvas.", Toast.LENGTH_SHORT).show();
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
        String[] entries = enabledServices.split(":");
        for (String entry : entries) {
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
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
        );
        lp.setMargins(0, dp(4), 0, dp(4));
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int parse(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
