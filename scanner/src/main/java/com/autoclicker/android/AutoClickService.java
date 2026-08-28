package com.autoclicker.android;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AutoClickService extends AccessibilityService {
    private WindowManager windowManager;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelParams;
    private TextView miniBubble;
    private WindowManager.LayoutParams miniParams;
    private TextView status;
    private Button listenButton;
    private Button continuousButton;

    private SpeechRecognizer speechRecognizer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean listening;
    private boolean continuous;
    private boolean torchOn;

    private float panelDownX, panelDownY;
    private int panelStartX, panelStartY;
    private float miniDownX, miniDownY;
    private int miniStartX, miniStartY;
    private boolean miniMoved;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        setupSpeechRecognizer();
        showPanel();
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Reconhecimento de voz não disponível neste celular.", Toast.LENGTH_LONG).show();
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                listening = true;
                updateListeningUi("Ouvindo...");
            }
            @Override public void onBeginningOfSpeech() {
                updateListeningUi("Pode falar...");
            }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() {
                updateListeningUi("Processando...");
            }
            @Override public void onError(int error) {
                listening = false;
                updateListeningUi("Toque no microfone e fale");
                if (continuous && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    scheduleContinuousRestart(700);
                }
            }
            @Override public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String command = matches.get(0);
                    updateListeningUi("Comando: " + command);
                    executeCommand(command);
                } else {
                    updateListeningUi("Não entendi. Tente novamente.");
                }
                if (continuous) scheduleContinuousRestart(800);
            }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    private void showPanel() {
        if (panel != null || windowManager == null) return;

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(242, 25, 30, 38));
        bg.setCornerRadius(dp(16));
        panel.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("CONTROLE POR VOZ • ARRASTE AQUI");
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setOnTouchListener((v, e) -> dragPanel(e));
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        status = new TextView(this);
        status.setText("Toque em OUVIR e fale um comando");
        status.setTextColor(Color.rgb(225, 230, 238));
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(5), dp(4), dp(5), dp(4));
        panel.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        listenButton = button("🎤 OUVIR AGORA");
        listenButton.setOnClickListener(v -> startListening());
        panel.addView(listenButton, buttonParams());

        continuousButton = button("CONTÍNUO: DESLIGADO");
        continuousButton.setOnClickListener(v -> {
            continuous = !continuous;
            continuousButton.setText(continuous ? "CONTÍNUO: LIGADO" : "CONTÍNUO: DESLIGADO");
            if (continuous && !listening) startListening();
            if (!continuous && listening) stopListening();
        });
        panel.addView(continuousButton, buttonParams());

        Button minimize = button("MINIMIZAR");
        minimize.setOnClickListener(v -> minimizePanel());
        panel.addView(minimize, buttonParams());

        Button close = button("FECHAR CONTROLE");
        close.setOnClickListener(v -> disableSelf());
        panel.addView(close, buttonParams());

        panelParams = new WindowManager.LayoutParams(
                dp(250), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = dp(12);
        panelParams.y = dp(90);
        windowManager.addView(panel, panelParams);
    }

    private boolean dragPanel(MotionEvent event) {
        if (panelParams == null) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                panelDownX = event.getRawX();
                panelDownY = event.getRawY();
                panelStartX = panelParams.x;
                panelStartY = panelParams.y;
                return true;
            case MotionEvent.ACTION_MOVE:
                panelParams.x = panelStartX + Math.round(event.getRawX() - panelDownX);
                panelParams.y = panelStartY + Math.round(event.getRawY() - panelDownY);
                try { windowManager.updateViewLayout(panel, panelParams); } catch (Exception ignored) { }
                return true;
            default:
                return true;
        }
    }

    private void minimizePanel() {
        if (panel == null || miniBubble != null) return;
        panel.setVisibility(View.GONE);
        showMiniBubble();
    }

    private void showMiniBubble() {
        if (miniBubble != null || windowManager == null) return;
        miniBubble = new TextView(this);
        miniBubble.setText("🎤");
        miniBubble.setTextSize(24);
        miniBubble.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(242, 35, 105, 220));
        bg.setStroke(dp(2), Color.WHITE);
        miniBubble.setBackground(bg);
        miniBubble.setOnTouchListener((v, e) -> handleMiniTouch(e));

        miniParams = new WindowManager.LayoutParams(
                dp(58), dp(58),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        miniParams.gravity = Gravity.TOP | Gravity.START;
        miniParams.x = panelParams != null ? panelParams.x : dp(12);
        miniParams.y = panelParams != null ? panelParams.y : dp(100);
        try { windowManager.addView(miniBubble, miniParams); }
        catch (Exception ignored) { miniBubble = null; miniParams = null; }
    }

    private boolean handleMiniTouch(MotionEvent event) {
        if (miniParams == null) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                miniDownX = event.getRawX();
                miniDownY = event.getRawY();
                miniStartX = miniParams.x;
                miniStartY = miniParams.y;
                miniMoved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - miniDownX;
                float dy = event.getRawY() - miniDownY;
                if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) miniMoved = true;
                miniParams.x = miniStartX + Math.round(dx);
                miniParams.y = miniStartY + Math.round(dy);
                try { windowManager.updateViewLayout(miniBubble, miniParams); } catch (Exception ignored) { }
                return true;
            case MotionEvent.ACTION_UP:
                if (!miniMoved) {
                    if (listening) stopListening(); else startListening();
                }
                return true;
            default:
                return true;
        }
    }

    private void removeMiniBubble() {
        if (miniBubble != null && windowManager != null) {
            try { windowManager.removeView(miniBubble); } catch (Exception ignored) { }
        }
        miniBubble = null;
        miniParams = null;
    }

    private void startListening() {
        if (speechRecognizer == null || listening) return;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Abra o app e libere a permissão do microfone.", Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        try {
            speechRecognizer.startListening(intent);
            listening = true;
            updateListeningUi("Ouvindo...");
        } catch (Exception e) {
            listening = false;
            Toast.makeText(this, "Não foi possível iniciar o microfone.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopListening() {
        listening = false;
        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) { }
        }
        updateListeningUi("Microfone parado");
    }

    private void scheduleContinuousRestart(long delayMs) {
        handler.removeCallbacks(restartListeningRunnable);
        handler.postDelayed(restartListeningRunnable, delayMs);
    }

    private final Runnable restartListeningRunnable = () -> {
        if (continuous && !listening) startListening();
    };

    private void updateListeningUi(String text) {
        if (status != null) status.setText(text);
        if (listenButton != null) listenButton.setText(listening ? "■ PARAR MICROFONE" : "🎤 OUVIR AGORA");
        if (miniBubble != null) miniBubble.setText(listening ? "●" : "🎤");
    }

    private void executeCommand(String raw) {
        String cmd = normalize(raw);

        if (equalsAny(cmd, "voltar", "volte")) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            feedback("Voltar");
            return;
        }
        if (equalsAny(cmd, "inicio", "ir para inicio", "tela inicial", "home")) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            feedback("Início");
            return;
        }
        if (containsAny(cmd, "recentes", "aplicativos recentes")) {
            performGlobalAction(GLOBAL_ACTION_RECENTS);
            feedback("Recentes");
            return;
        }
        if (containsAny(cmd, "notificacoes", "abrir notificacoes")) {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
            feedback("Notificações");
            return;
        }
        if (containsAny(cmd, "configuracoes rapidas", "painel rapido")) {
            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
            feedback("Configurações rápidas");
            return;
        }
        if (containsAny(cmd, "captura de tela", "tirar print", "print da tela")) {
            if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
            else feedback("Captura de tela exige Android 9 ou superior");
            return;
        }
        if (containsAny(cmd, "bloquear tela", "travar tela")) {
            if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
            else feedback("Bloquear tela exige Android 9 ou superior");
            return;
        }
        if (containsAny(cmd, "menu desligar", "menu de energia", "botao desligar")) {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
            feedback("Menu de energia");
            return;
        }

        if (containsAny(cmd, "aumentar volume", "volume mais alto", "aumenta o volume")) {
            adjustVolume(AudioManager.ADJUST_RAISE);
            feedback("Volume aumentado");
            return;
        }
        if (containsAny(cmd, "diminuir volume", "volume mais baixo", "abaixar volume")) {
            adjustVolume(AudioManager.ADJUST_LOWER);
            feedback("Volume diminuído");
            return;
        }
        if (containsAny(cmd, "silenciar", "mudo", "tirar o som")) {
            adjustVolume(AudioManager.ADJUST_MUTE);
            feedback("Som silenciado");
            return;
        }
        if (containsAny(cmd, "ativar som", "desmutar", "ligar som")) {
            adjustVolume(AudioManager.ADJUST_UNMUTE);
            feedback("Som ativado");
            return;
        }

        if (containsAny(cmd, "ligar lanterna", "acender lanterna")) {
            setTorch(true);
            return;
        }
        if (containsAny(cmd, "desligar lanterna", "apagar lanterna")) {
            setTorch(false);
            return;
        }

        if (containsAny(cmd, "rolar para baixo", "descer tela", "descer a tela")) {
            swipeVertical(false);
            feedback("Rolando para baixo");
            return;
        }
        if (containsAny(cmd, "rolar para cima", "subir tela", "subir a tela")) {
            swipeVertical(true);
            feedback("Rolando para cima");
            return;
        }
        if (containsAny(cmd, "deslizar para esquerda", "arrastar para esquerda")) {
            swipeHorizontal(true);
            feedback("Deslizando para esquerda");
            return;
        }
        if (containsAny(cmd, "deslizar para direita", "arrastar para direita")) {
            swipeHorizontal(false);
            feedback("Deslizando para direita");
            return;
        }

        if (cmd.startsWith("tocar em ") || cmd.startsWith("clicar em ")) {
            String text = raw.replaceFirst("(?i)^(tocar|clicar)\\s+em\\s+", "").trim();
            if (!text.isEmpty()) clickText(text);
            return;
        }
        if (cmd.startsWith("escrever ") || cmd.startsWith("digitar ")) {
            String text = raw.replaceFirst("(?i)^(escrever|digitar)\\s+", "").trim();
            if (!text.isEmpty()) setFocusedText(text);
            return;
        }
        if (cmd.startsWith("pesquisar ") || cmd.startsWith("buscar ")) {
            String query = raw.replaceFirst("(?i)^(pesquisar|buscar)\\s+", "").trim();
            if (!query.isEmpty()) webSearch(query);
            return;
        }
        if (cmd.startsWith("ligar para ") || cmd.startsWith("telefonar para ")) {
            String target = raw.replaceFirst("(?i)^(ligar|telefonar)\\s+para\\s+", "").trim();
            dial(target);
            return;
        }

        if (containsAny(cmd, "abrir wifi", "configuracao wifi", "configuracoes wifi")) {
            startSystemIntent(new Intent(Settings.ACTION_WIFI_SETTINGS));
            feedback("Wi-Fi");
            return;
        }
        if (containsAny(cmd, "abrir bluetooth", "configuracao bluetooth", "configuracoes bluetooth")) {
            startSystemIntent(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            feedback("Bluetooth");
            return;
        }
        if (containsAny(cmd, "abrir configuracoes", "configuracoes do celular")) {
            startSystemIntent(new Intent(Settings.ACTION_SETTINGS));
            feedback("Configurações");
            return;
        }
        if (containsAny(cmd, "abrir camera", "camera")) {
            Intent camera = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            camera.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startSystemIntent(camera);
            feedback("Câmera");
            return;
        }
        if (cmd.startsWith("abrir ")) {
            String app = raw.replaceFirst("(?i)^abrir\\s+", "").trim();
            if (!app.isEmpty()) openApp(app);
            return;
        }

        if (equalsAny(cmd, "parar escuta", "parar de ouvir", "desligar escuta continua")) {
            continuous = false;
            if (continuousButton != null) continuousButton.setText("CONTÍNUO: DESLIGADO");
            stopListening();
            feedback("Escuta contínua desligada");
            return;
        }

        feedback("Comando não reconhecido: " + raw);
    }

    private void adjustVolume(int direction) {
        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audio != null) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
    }

    private void setTorch(boolean enabled) {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            feedback("Libere a permissão da câmera para usar a lanterna");
            return;
        }
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) throw new Exception("camera");
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(flash) && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    manager.setTorchMode(id, enabled);
                    torchOn = enabled;
                    feedback(enabled ? "Lanterna ligada" : "Lanterna desligada");
                    return;
                }
            }
            feedback("Lanterna não encontrada");
        } catch (Exception e) {
            feedback("Não foi possível controlar a lanterna");
        }
    }

    private void swipeVertical(boolean up) {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        float x = w * 0.5f;
        float fromY = up ? h * 0.72f : h * 0.30f;
        float toY = up ? h * 0.30f : h * 0.72f;
        dispatchSwipe(x, fromY, x, toY, 350);
    }

    private void swipeHorizontal(boolean left) {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        float y = h * 0.52f;
        float fromX = left ? w * 0.82f : w * 0.18f;
        float toX = left ? w * 0.18f : w * 0.82f;
        dispatchSwipe(fromX, y, toX, y, 320);
    }

    private void dispatchSwipe(float x1, float y1, float x2, float y2, long duration) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();
        dispatchGesture(gesture, null, null);
    }

    private void clickText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            feedback("Não encontrei elementos na tela");
            return;
        }
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes == null || nodes.isEmpty()) {
            feedback("Não encontrei: " + text);
            return;
        }
        for (AccessibilityNodeInfo node : nodes) {
            AccessibilityNodeInfo clickable = node;
            while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                feedback("Toquei em " + text);
                return;
            }
        }
        feedback("Encontrei " + text + ", mas não consegui tocar");
    }

    private void setFocusedText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            feedback("Nenhum campo de texto encontrado");
            return;
        }
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null || !focused.isEditable()) {
            feedback("Toque primeiro em um campo de texto");
            return;
        }
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        feedback(ok ? "Texto escrito" : "Não consegui escrever nesse campo");
    }

    private void webSearch(String query) {
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.putExtra(SearchManager.QUERY, query);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startSystemIntent(intent);
        feedback("Pesquisando " + query);
    }

    private void dial(String target) {
        String digits = target.replaceAll("[^0-9+]", "");
        if (digits.isEmpty()) {
            feedback("Fale um número para ligar");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + digits));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startSystemIntent(intent);
        feedback("Abrindo telefone");
    }

    private void openApp(String target) {
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN, null);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcher, 0);
        String wanted = normalize(target);
        ResolveInfo best = null;

        for (ResolveInfo info : apps) {
            CharSequence labelSequence = info.loadLabel(pm);
            String label = labelSequence == null ? "" : labelSequence.toString();
            String normalizedLabel = normalize(label);
            if (normalizedLabel.equals(wanted)) {
                best = info;
                break;
            }
            if (best == null && (normalizedLabel.contains(wanted) || wanted.contains(normalizedLabel))) {
                best = info;
            }
        }

        if (best == null) {
            feedback("Não encontrei o app " + target);
            return;
        }
        Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch == null) {
            feedback("Não consegui abrir " + target);
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
        feedback("Abrindo " + target);
    }

    private void startSystemIntent(Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            feedback("Essa função não está disponível neste celular");
        }
    }

    private void feedback(String text) {
        if (status != null) status.setText(text);
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private String normalize(String text) {
        if (text == null) return "";
        String n = Normalizer.normalize(text.toLowerCase(new Locale("pt", "BR")), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "").trim().replaceAll("\\s+", " ");
    }

    private boolean equalsAny(String value, String... options) {
        for (String option : options) if (value.equals(option)) return true;
        return false;
    }

    private boolean containsAny(String value, String... options) {
        for (String option : options) if (value.contains(option)) return true;
        return false;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(0, dp(2), 0, dp(2));
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() {
        stopListening();
    }

    @Override
    public void onDestroy() {
        continuous = false;
        listening = false;
        handler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (Exception ignored) { }
            try { speechRecognizer.destroy(); } catch (Exception ignored) { }
            speechRecognizer = null;
        }
        removeMiniBubble();
        if (panel != null && windowManager != null) {
            try { windowManager.removeView(panel); } catch (Exception ignored) { }
            panel = null;
        }
        if (torchOn) setTorch(false);
        super.onDestroy();
    }
}
