package com.autoclicker.android;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_PERMISSIONS = 100;
    private TextView permissionStatus;
    private TextView accessibilityStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        TextView icon = new TextView(this);
        icon.setText("🎤");
        icon.setTextSize(48);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon, fullWidth(dp(74)));

        TextView title = new TextView(this);
        title.setText("VOICE CONTROL MASTER");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(20, 25, 35));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, fullWidth(dp(60)));

        TextView description = new TextView(this);
        description.setText("Controle o celular e digite textos usando comandos de voz pelo microfone flutuante.");
        description.setTextSize(15);
        description.setTextColor(Color.DKGRAY);
        description.setGravity(Gravity.CENTER);
        root.addView(description, fullWidth(dp(76)));

        permissionStatus = statusView();
        root.addView(permissionStatus, fullWidth(dp(42)));

        accessibilityStatus = statusView();
        root.addView(accessibilityStatus, fullWidth(dp(42)));

        Button permissions = new Button(this);
        permissions.setText("LIBERAR MICROFONE E LANTERNA");
        permissions.setOnClickListener(v -> requestRequiredPermissions());
        root.addView(permissions, fullWidth(dp(58)));

        Button accessibility = new Button(this);
        accessibility.setText("ATIVAR ACESSIBILIDADE");
        accessibility.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this,
                    "Ative Voice Control Master - Acessibilidade.",
                    Toast.LENGTH_LONG).show();
        });
        root.addView(accessibility, fullWidth(dp(58)));

        Button floating = new Button(this);
        floating.setText("USAR MICROFONE FLUTUANTE");
        floating.setOnClickListener(v -> {
            if (!isServiceEnabled(this, AutoClickService.class)) {
                Toast.makeText(this, "Ative a Acessibilidade primeiro.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            moveTaskToBack(true);
            Toast.makeText(this, "Use o painel flutuante e toque em OUVIR.", Toast.LENGTH_SHORT).show();
        });
        root.addView(floating, fullWidth(dp(58)));

        TextView examplesTitle = new TextView(this);
        examplesTitle.setText("EXEMPLOS DE COMANDOS");
        examplesTitle.setTextSize(16);
        examplesTitle.setTextColor(Color.rgb(30, 35, 45));
        examplesTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        examplesTitle.setGravity(Gravity.CENTER);
        root.addView(examplesTitle, fullWidth(dp(46)));

        TextView examples = new TextView(this);
        examples.setText(
                "• abrir WhatsApp / câmera / configurações\n" +
                "• voltar / início / recentes / notificações\n" +
                "• aumentar volume / diminuir volume / silenciar\n" +
                "• ligar lanterna / desligar lanterna\n" +
                "• rolar para baixo / cima\n" +
                "• deslizar para esquerda / direita\n" +
                "• tocar em Continuar\n" +
                "• escrever bom dia, tudo bem?\n" +
                "• apagar texto / apagar última palavra\n" +
                "• selecionar tudo / copiar / colar / enviar\n" +
                "• pesquisar previsão do tempo\n" +
                "• ligar para 21999999999\n" +
                "• captura de tela / bloquear tela / menu desligar");
        examples.setTextSize(14);
        examples.setTextColor(Color.DKGRAY);
        examples.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(examples, fullWidth(dp(350)));

        TextView note = new TextView(this);
        note.setText("Algumas funções protegidas pelo Android não podem ser alteradas diretamente por um app comum. Nesses casos o comando abre a configuração correspondente.");
        note.setTextSize(13);
        note.setTextColor(Color.GRAY);
        note.setGravity(Gravity.CENTER);
        root.addView(note, fullWidth(dp(100)));

        setContentView(scroll);
        requestRequiredPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateStatus();
    }

    private void requestRequiredPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, REQ_PERMISSIONS);
        }
    }

    private void updateStatus() {
        boolean mic = checkSelfPermissionCompat(Manifest.permission.RECORD_AUDIO);
        boolean camera = checkSelfPermissionCompat(Manifest.permission.CAMERA);
        permissionStatus.setText("PERMISSÕES: " + (mic ? "MIC ✓" : "MIC ✕") + "   " + (camera ? "LANTERNA ✓" : "LANTERNA ✕"));
        permissionStatus.setTextColor(mic ? Color.rgb(0, 140, 70) : Color.rgb(190, 45, 45));

        boolean enabled = isServiceEnabled(this, AutoClickService.class);
        accessibilityStatus.setText(enabled ? "ACESSIBILIDADE ATIVA ✓" : "ACESSIBILIDADE DESATIVADA");
        accessibilityStatus.setTextColor(enabled ? Color.rgb(0, 140, 70) : Color.rgb(190, 45, 45));
    }

    private boolean checkSelfPermissionCompat(String permission) {
        if (android.os.Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private TextView statusView() {
        TextView view = new TextView(this);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
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

    private LinearLayout.LayoutParams fullWidth(int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        lp.setMargins(0, dp(4), 0, dp(4));
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
