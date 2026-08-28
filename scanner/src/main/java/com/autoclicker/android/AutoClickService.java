package com.autoclicker.android;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoClickService extends AccessibilityService {
    private static final String PREFS = "master_tools_prefs";
    private static final int MAX_POINTS = 8;

    private static final int MODE_CLICK = 0;
    private static final int MODE_SWIPE = 1;
    private static final int MODE_DIAGONAL = 2;
    private static final int MODE_DUAL = 3;
    private static final int MODE_NINJA = 4;

    private WindowManager windowManager;
    private ScrollView panelRoot;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelParams;
    private TextView miniBubble;
    private WindowManager.LayoutParams miniParams;
    private View captureOverlay;

    private TextView voiceStatus;
    private TextView autoStatus;
    private TextView selectedPointText;
    private TextView speedText;
    private TextView quantityText;
    private Button listenButton;
    private Button continuousButton;
    private Button modeButton;
    private Button directionButton;
    private Button startButton;
    private Button stopButton;
    private Button addPointButton;
    private Button removePointButton;
    private Button unlimitedButton;
    private LinearLayout clickControls;
    private LinearLayout swipeControls;
    private LinearLayout speedControls;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<ClickPoint> points = new ArrayList<>();

    private SpeechRecognizer speechRecognizer;
    private boolean listening;
    private boolean continuous = true;
    private boolean torchOn;
    private String lastHeard = "";

    private int mode = MODE_CLICK;
    private int swipeDirection = 0;
    private int gestureMs = 350;
    private int clickLimit = 100;
    private boolean unlimitedClicks;
    private boolean automationRunning;
    private boolean gestureBusy;
    private boolean diagonalForward = true;
    private long autoCycle;
    private double ninjaPhase;

    private int nextPointId = 1;
    private int selectedPointId = -1;
    private boolean panelMinimized;

    private float panelDownX, panelDownY;
    private int panelStartX, panelStartY;
    private float miniDownX, miniDownY;
    private int miniStartX, miniStartY;
    private boolean miniMoved;

    private boolean workflowActive;
    private String pendingWhatsAppContact = "";
    private String pendingWhatsAppMessage = "";
    private int whatsAppStep;
    private int whatsAppRetries;

    private static class ClickPoint {
        int id;
        int x;
        int y;
        int intervalMs;
        long count;
        long nextAt;
        TextView marker;
        WindowManager.LayoutParams params;
        float dragStartRawX;
        float dragStartRawY;
        int dragStartX;
        int dragStartY;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        loadSettings();
        loadPoints();
        setupSpeechRecognizer();
        showPanel();
        showAllMarkers();
        handler.postDelayed(() -> {
            if (continuous && !workflowActive) startListening();
        }, 800);
    }

    // -------------------- PAINEL --------------------

    private void showPanel() {
        if (panelRoot != null || windowManager == null) return;

        panelRoot = new ScrollView(this);
        panelRoot.setFillViewport(true);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(246, 12, 20, 34));
        bg.setCornerRadius(dp(18));
        panel.setBackground(bg);
        panelRoot.addView(panel);

        TextView title = new TextView(this);
        title.setText("MASTER TOOLS  •  ARRASTE AQUI");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setOnTouchListener((v, e) -> dragPanel(e));
        panel.addView(title, fullWidth(dp(42)));

        voiceStatus = new TextView(this);
        voiceStatus.setText("Voz pronta");
        voiceStatus.setTextColor(Color.rgb(225, 235, 248));
        voiceStatus.setTextSize(12);
        voiceStatus.setGravity(Gravity.CENTER);
        voiceStatus.setPadding(dp(4), dp(3), dp(4), dp(3));
        panel.addView(voiceStatus, fullWidth(dp(62)));

        listenButton = button("🎤 OUVIR AGORA");
        listenButton.setOnClickListener(v -> {
            if (listening) stopListening(); else startListening();
        });
        panel.addView(listenButton, buttonParams());

        continuousButton = button("CONTÍNUO: LIGADO");
        continuousButton.setOnClickListener(v -> {
            continuous = !continuous;
            continuousButton.setText(continuous ? "CONTÍNUO: LIGADO" : "CONTÍNUO: DESLIGADO");
            if (continuous && !listening && !workflowActive) startListening();
            if (!continuous && listening) stopListening();
        });
        panel.addView(continuousButton, buttonParams());

        TextView divider = new TextView(this);
        divider.setText("AUTOMAÇÃO DE TOQUES / DESLIZES");
        divider.setTextColor(Color.rgb(70, 185, 255));
        divider.setTextSize(12);
        divider.setTypeface(null, android.graphics.Typeface.BOLD);
        divider.setGravity(Gravity.CENTER);
        panel.addView(divider, fullWidth(dp(38)));

        autoStatus = new TextView(this);
        autoStatus.setTextColor(Color.WHITE);
        autoStatus.setTextSize(12);
        autoStatus.setGravity(Gravity.CENTER);
        panel.addView(autoStatus, fullWidth(dp(42)));

        modeButton = button("MODO");
        modeButton.setOnClickListener(v -> cycleMode());
        panel.addView(modeButton, buttonParams());

        clickControls = new LinearLayout(this);
        clickControls.setOrientation(LinearLayout.VERTICAL);

        selectedPointText = new TextView(this);
        selectedPointText.setTextColor(Color.WHITE);
        selectedPointText.setTextSize(12);
        selectedPointText.setGravity(Gravity.CENTER);
        clickControls.addView(selectedPointText, fullWidth(dp(36)));

        LinearLayout pointRow = row();
        addPointButton = button("+ PONTO");
        addPointButton.setOnClickListener(v -> beginPointCapture());
        pointRow.addView(addPointButton, rowButtonParams());
        removePointButton = button("REMOVER");
        removePointButton.setOnClickListener(v -> removeSelectedPoint());
        pointRow.addView(removePointButton, rowButtonParams());
        clickControls.addView(pointRow);

        LinearLayout intervalRow = row();
        Button minus100 = smallButton("−100");
        minus100.setOnClickListener(v -> adjustPointInterval(-100));
        intervalRow.addView(minus100, smallParams());
        Button minus10 = smallButton("−10");
        minus10.setOnClickListener(v -> adjustPointInterval(-10));
        intervalRow.addView(minus10, smallParams());
        Button plus10 = smallButton("+10");
        plus10.setOnClickListener(v -> adjustPointInterval(10));
        intervalRow.addView(plus10, smallParams());
        Button plus100 = smallButton("+100");
        plus100.setOnClickListener(v -> adjustPointInterval(100));
        intervalRow.addView(plus100, smallParams());
        clickControls.addView(intervalRow, fullWidth(dp(42)));

        quantityText = new TextView(this);
        quantityText.setTextColor(Color.WHITE);
        quantityText.setGravity(Gravity.CENTER);
        quantityText.setTextSize(12);
        clickControls.addView(quantityText, fullWidth(dp(32)));

        LinearLayout quantityRow = row();
        Button qMinus = smallButton("−10");
        qMinus.setOnClickListener(v -> adjustClickLimit(-10));
        quantityRow.addView(qMinus, smallParams());
        Button qPlus = smallButton("+10");
        qPlus.setOnClickListener(v -> adjustClickLimit(10));
        quantityRow.addView(qPlus, smallParams());
        unlimitedButton = smallButton("ILIMITADO");
        unlimitedButton.setOnClickListener(v -> {
            if (automationRunning) return;
            unlimitedClicks = !unlimitedClicks;
            saveSettings();
            refreshAutomationUi();
        });
        LinearLayout.LayoutParams unlimitedLp = new LinearLayout.LayoutParams(0, dp(40), 2f);
        unlimitedLp.setMargins(dp(2), 0, dp(2), 0);
        quantityRow.addView(unlimitedButton, unlimitedLp);
        clickControls.addView(quantityRow, fullWidth(dp(44)));

        panel.addView(clickControls);

        swipeControls = new LinearLayout(this);
        swipeControls.setOrientation(LinearLayout.VERTICAL);
        directionButton = button("DIREÇÃO");
        directionButton.setOnClickListener(v -> cycleSwipeDirection());
        swipeControls.addView(directionButton, buttonParams());
        panel.addView(swipeControls);

        speedControls = new LinearLayout(this);
        speedControls.setOrientation(LinearLayout.VERTICAL);
        speedText = new TextView(this);
        speedText.setTextColor(Color.WHITE);
        speedText.setTextSize(12);
        speedText.setGravity(Gravity.CENTER);
        speedControls.addView(speedText, fullWidth(dp(32)));
        LinearLayout speedRow = row();
        Button speedMinus = button("−50 ms");
        speedMinus.setOnClickListener(v -> adjustGestureMs(-50));
        speedRow.addView(speedMinus, rowButtonParams());
        Button speedPlus = button("+50 ms");
        speedPlus.setOnClickListener(v -> adjustGestureMs(50));
        speedRow.addView(speedPlus, rowButtonParams());
        speedControls.addView(speedRow);
        panel.addView(speedControls);

        startButton = button("INICIAR AUTOMAÇÃO");
        startButton.setOnClickListener(v -> startAutomation());
        panel.addView(startButton, buttonParams());

        stopButton = button("PARAR");
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> stopAutomation("Parado"));
        panel.addView(stopButton, buttonParams());

        Button minimize = button("MINIMIZAR");
        minimize.setOnClickListener(v -> minimizePanel());
        panel.addView(minimize, buttonParams());

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int height = Math.min(dp(690), Math.max(dp(420), dm.heightPixels - dp(80)));
        panelParams = new WindowManager.LayoutParams(
                dp(292), height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = dp(8);
        panelParams.y = dp(45);

        windowManager.addView(panelRoot, panelParams);
        refreshAutomationUi();
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
                try { windowManager.updateViewLayout(panelRoot, panelParams); } catch (Exception ignored) { }
                return true;
            default:
                return true;
        }
    }

    private void minimizePanel() {
        if (panelRoot == null || miniBubble != null) return;
        panelMinimized = true;
        panelRoot.setVisibility(View.GONE);
        showMiniBubble();
    }

    private void showMiniBubble() {
        if (miniBubble != null || windowManager == null) return;
        miniBubble = new TextView(this);
        miniBubble.setText(listening ? "●" : "🎤");
        miniBubble.setTextColor(Color.WHITE);
        miniBubble.setTextSize(23);
        miniBubble.setGravity(Gravity.CENTER);
        miniBubble.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(245, 25, 105, 225));
        bg.setStroke(dp(3), Color.WHITE);
        miniBubble.setBackground(bg);
        miniBubble.setOnTouchListener((v, e) -> handleMiniTouch(e));
        miniBubble.setOnLongClickListener(v -> {
            restorePanel();
            return true;
        });

        miniParams = new WindowManager.LayoutParams(
                dp(58), dp(58),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        miniParams.gravity = Gravity.TOP | Gravity.START;
        miniParams.x = panelParams != null ? panelParams.x : dp(10);
        miniParams.y = panelParams != null ? panelParams.y : dp(90);
        try {
            windowManager.addView(miniBubble, miniParams);
            Toast.makeText(this, "Toque no 🎤 para ouvir • segure para abrir o painel", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            miniBubble = null;
            miniParams = null;
        }
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
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - miniDownX;
                float dy = event.getRawY() - miniDownY;
                if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) miniMoved = true;
                if (miniMoved) {
                    miniParams.x = miniStartX + Math.round(dx);
                    miniParams.y = miniStartY + Math.round(dy);
                    try { windowManager.updateViewLayout(miniBubble, miniParams); } catch (Exception ignored) { }
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
                if (!miniMoved) {
                    if (listening) stopListening(); else startListening();
                    return true;
                }
                return true;
            default:
                return false;
        }
    }

    private void restorePanel() {
        panelMinimized = false;
        removeMiniBubble();
        if (panelRoot != null) panelRoot.setVisibility(View.VISIBLE);
    }

    private void removeMiniBubble() {
        if (miniBubble != null && windowManager != null) {
            try { windowManager.removeView(miniBubble); } catch (Exception ignored) { }
        }
        miniBubble = null;
        miniParams = null;
    }

    // -------------------- AUTOMAÇÃO --------------------

    private void cycleMode() {
        if (automationRunning) return;
        mode = (mode + 1) % 5;
        saveSettings();
        refreshAutomationUi();
    }

    private void setMode(int newMode) {
        if (automationRunning) stopAutomation("Modo alterado");
        mode = clamp(newMode, MODE_CLICK, MODE_NINJA);
        saveSettings();
        refreshAutomationUi();
    }

    private void cycleSwipeDirection() {
        if (automationRunning) return;
        swipeDirection = (swipeDirection + 1) % 4;
        saveSettings();
        refreshAutomationUi();
    }

    private void adjustGestureMs(int delta) {
        if (automationRunning) return;
        gestureMs = clamp(gestureMs + delta, 80, 3000);
        saveSettings();
        refreshAutomationUi();
    }

    private void adjustClickLimit(int delta) {
        if (automationRunning) return;
        clickLimit = clamp(clickLimit + delta, 1, 10000);
        saveSettings();
        refreshAutomationUi();
    }

    private void refreshAutomationUi() {
        if (modeButton != null) {
            modeButton.setText("MODO: " + modeName());
            modeButton.setEnabled(!automationRunning);
        }
        if (clickControls != null) clickControls.setVisibility(mode == MODE_CLICK ? View.VISIBLE : View.GONE);
        if (swipeControls != null) swipeControls.setVisibility(mode == MODE_SWIPE ? View.VISIBLE : View.GONE);
        if (speedControls != null) speedControls.setVisibility(mode == MODE_CLICK ? View.GONE : View.VISIBLE);
        if (directionButton != null) directionButton.setText("DIREÇÃO: " + directionName());
        if (speedText != null) speedText.setText("Duração de cada gesto: " + gestureMs + " ms");
        if (quantityText != null) quantityText.setText(unlimitedClicks ? "Quantidade: ILIMITADO" : "Quantidade por ponto: " + clickLimit);
        if (unlimitedButton != null) unlimitedButton.setText(unlimitedClicks ? "LIMITADO" : "ILIMITADO");

        ClickPoint selected = selectedPoint();
        if (selected == null && !points.isEmpty()) {
            selectedPointId = points.get(0).id;
            selected = points.get(0);
        }
        if (selectedPointText != null) {
            selectedPointText.setText(selected == null
                    ? "Nenhum ponto marcado"
                    : "P" + selected.id + " • intervalo " + selected.intervalMs + " ms");
        }
        if (addPointButton != null) addPointButton.setEnabled(!automationRunning && points.size() < MAX_POINTS);
        if (removePointButton != null) removePointButton.setEnabled(!automationRunning && selected != null);

        for (ClickPoint p : points) {
            if (p.marker != null) {
                p.marker.setVisibility(mode == MODE_CLICK ? View.VISIBLE : View.GONE);
                if (mode == MODE_CLICK) setPointTouchable(p, !automationRunning);
            }
        }

        if (autoStatus != null && !automationRunning) {
            if (mode == MODE_CLICK) {
                autoStatus.setText(points.isEmpty() ? "Adicione um ponto" : points.size() + " ponto(s) pronto(s)");
            } else {
                autoStatus.setText(modeName() + " • pronto");
            }
        }
        if (startButton != null) startButton.setEnabled(!automationRunning && (mode != MODE_CLICK || !points.isEmpty()));
        if (stopButton != null) stopButton.setEnabled(automationRunning);
    }

    private String modeName() {
        switch (mode) {
            case MODE_SWIPE: return "DESLIZAR";
            case MODE_DIAGONAL: return "DIAGONAL";
            case MODE_DUAL: return "2 HORIZONTAIS";
            case MODE_NINJA: return "NINJA";
            default: return "AUTOCLICKER";
        }
    }

    private String directionName() {
        switch (swipeDirection) {
            case 1: return "BAIXO";
            case 2: return "ESQUERDA";
            case 3: return "DIREITA";
            default: return "CIMA";
        }
    }

    private void startAutomation() {
        if (automationRunning) return;
        if (mode == MODE_CLICK && points.isEmpty()) {
            feedback("Adicione pelo menos um ponto");
            return;
        }
        automationRunning = true;
        gestureBusy = false;
        autoCycle = 0;
        diagonalForward = true;
        ninjaPhase = 0;
        long now = SystemClock.uptimeMillis();
        for (ClickPoint p : points) {
            p.count = 0;
            p.nextAt = now;
            setPointTouchable(p, false);
        }
        refreshAutomationUi();
        if (autoStatus != null) autoStatus.setText("Executando " + modeName());
        handler.post(automationRunnable);
    }

    private void stopAutomation(String message) {
        automationRunning = false;
        gestureBusy = false;
        handler.removeCallbacks(automationRunnable);
        for (ClickPoint p : points) setPointTouchable(p, true);
        if (autoStatus != null) autoStatus.setText(message);
        refreshAutomationUi();
    }

    private final Runnable automationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!automationRunning) return;
            if (gestureBusy) {
                handler.postDelayed(this, 8);
                return;
            }

            if (mode == MODE_CLICK) {
                runClickerCycle();
                return;
            }

            GestureDescription gesture = buildAutomationGesture();
            if (gesture == null) {
                stopAutomation("Falha ao criar gesto");
                return;
            }
            gestureBusy = true;
            boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    gestureBusy = false;
                    if (!automationRunning) return;
                    autoCycle++;
                    if (mode == MODE_DIAGONAL) diagonalForward = !diagonalForward;
                    if (mode == MODE_NINJA) ninjaPhase += 0.37;
                    if (autoStatus != null) autoStatus.setText(modeName() + " • " + autoCycle + " gesto(s)");
                    handler.postDelayed(automationRunnable, 18);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    gestureBusy = false;
                    if (automationRunning) handler.postDelayed(automationRunnable, 40);
                }
            }, null);
            if (!accepted) {
                gestureBusy = false;
                handler.postDelayed(this, 40);
            }
        }
    };

    private void runClickerCycle() {
        if (!automationRunning) return;

        boolean allDone = !unlimitedClicks;
        long now = SystemClock.uptimeMillis();
        ClickPoint due = null;
        long nearest = Long.MAX_VALUE;

        for (ClickPoint p : points) {
            boolean done = !unlimitedClicks && p.count >= clickLimit;
            if (!done) allDone = false;
            if (done) continue;
            if (p.nextAt <= now && due == null) due = p;
            nearest = Math.min(nearest, p.nextAt);
        }

        if (allDone) {
            stopAutomation("Concluído");
            return;
        }

        if (due == null) {
            long delay = nearest == Long.MAX_VALUE ? 10 : Math.max(5, Math.min(100, nearest - now));
            handler.postDelayed(automationRunnable, delay);
            return;
        }

        final ClickPoint point = due;
        gestureBusy = true;
        GestureDescription tap = buildTap(point.x, point.y, 45);
        boolean accepted = dispatchGesture(tap, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                gestureBusy = false;
                point.count++;
                point.nextAt = SystemClock.uptimeMillis() + point.intervalMs;
                if (autoStatus != null) {
                    autoStatus.setText("P" + point.id + ": " + point.count + (unlimitedClicks ? "" : "/" + clickLimit));
                }
                handler.postDelayed(automationRunnable, 5);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                gestureBusy = false;
                point.nextAt = SystemClock.uptimeMillis() + 30;
                handler.postDelayed(automationRunnable, 10);
            }
        }, null);
        if (!accepted) {
            gestureBusy = false;
            point.nextAt = SystemClock.uptimeMillis() + 30;
            handler.postDelayed(automationRunnable, 10);
        }
    }

    private GestureDescription buildAutomationGesture() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float w = dm.widthPixels;
        float h = dm.heightPixels;
        float left = w * 0.05f;
        float right = w * 0.95f;
        float top = h * 0.08f;
        float bottom = h * 0.93f;

        if (mode == MODE_DUAL) {
            Path a = new Path();
            a.moveTo(left, h * 0.42f);
            a.lineTo(right, h * 0.42f);
            Path b = new Path();
            b.moveTo(right, h * 0.58f);
            b.lineTo(left, h * 0.58f);
            return new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(a, 0, gestureMs))
                    .addStroke(new GestureDescription.StrokeDescription(b, 0, gestureMs))
                    .build();
        }

        Path path = new Path();
        if (mode == MODE_DIAGONAL) {
            if (diagonalForward) {
                path.moveTo(left, top);
                path.lineTo(right, bottom);
            } else {
                path.moveTo(right, bottom);
                path.lineTo(left, top);
            }
        } else if (mode == MODE_SWIPE) {
            if (swipeDirection == 0) {
                path.moveTo(w * 0.5f, h * 0.78f);
                path.lineTo(w * 0.5f, h * 0.25f);
            } else if (swipeDirection == 1) {
                path.moveTo(w * 0.5f, h * 0.25f);
                path.lineTo(w * 0.5f, h * 0.78f);
            } else if (swipeDirection == 2) {
                path.moveTo(w * 0.85f, h * 0.52f);
                path.lineTo(w * 0.15f, h * 0.52f);
            } else {
                path.moveTo(w * 0.15f, h * 0.52f);
                path.lineTo(w * 0.85f, h * 0.52f);
            }
        } else if (mode == MODE_NINJA) {
            float cx = w * 0.5f;
            float cy = h * 0.5f;
            float rx = w * 0.45f;
            float ry = h * 0.42f;
            int steps = 50;
            for (int i = 0; i <= steps; i++) {
                double t = Math.PI * 2.0 * i / steps;
                float x = cx + rx * (float) Math.sin(2.0 * t + ninjaPhase);
                float y = cy + ry * (float) Math.sin(3.0 * t + 1.1 + ninjaPhase * 1.25);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
        } else {
            return null;
        }

        return new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, gestureMs))
                .build();
    }

    // -------------------- PONTOS DO AUTOCLICKER --------------------

    private void beginPointCapture() {
        if (automationRunning || mode != MODE_CLICK || captureOverlay != null || points.size() >= MAX_POINTS) return;
        if (panelRoot != null) panelRoot.setVisibility(View.GONE);
        setAllMarkersVisible(false);

        captureOverlay = new View(this);
        captureOverlay.setBackgroundColor(Color.argb(1, 0, 0, 0));
        captureOverlay.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                ClickPoint p = new ClickPoint();
                p.id = nextPointId++;
                p.x = Math.round(event.getRawX());
                p.y = Math.round(event.getRawY());
                p.intervalMs = 100;
                points.add(p);
                selectedPointId = p.id;
                savePoints();
                endPointCapture();
                showMarker(p);
                setAllMarkersVisible(true);
                refreshAutomationUi();
                Toast.makeText(this, "P" + p.id + " marcado. Você pode arrastar a bolinha para ajustar.", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(captureOverlay, params);
        Toast.makeText(this, "Toque exatamente no ponto que deseja marcar", Toast.LENGTH_LONG).show();
    }

    private void endPointCapture() {
        if (captureOverlay != null) {
            try { windowManager.removeView(captureOverlay); } catch (Exception ignored) { }
            captureOverlay = null;
        }
        if (panelRoot != null && !panelMinimized) panelRoot.setVisibility(View.VISIBLE);
    }

    private void showAllMarkers() {
        for (ClickPoint p : points) showMarker(p);
        setAllMarkersVisible(mode == MODE_CLICK);
    }

    private void showMarker(ClickPoint p) {
        if (p.marker != null || windowManager == null) return;
        int size = dp(40);
        TextView marker = new TextView(this);
        marker.setText(String.valueOf(p.id));
        marker.setTextColor(Color.WHITE);
        marker.setTextSize(13);
        marker.setGravity(Gravity.CENTER);
        marker.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(220, 220, 40, 50));
        bg.setStroke(dp(3), Color.WHITE);
        marker.setBackground(bg);
        marker.setOnTouchListener((v, e) -> handlePointTouch(p, e));
        p.marker = marker;

        p.params = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.params.gravity = Gravity.TOP | Gravity.START;
        p.params.x = p.x - size / 2;
        p.params.y = p.y - size / 2;
        try { windowManager.addView(marker, p.params); }
        catch (Exception ignored) { p.marker = null; p.params = null; }
    }

    private boolean handlePointTouch(ClickPoint p, MotionEvent event) {
        if (automationRunning || p.params == null) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                selectedPointId = p.id;
                p.dragStartRawX = event.getRawX();
                p.dragStartRawY = event.getRawY();
                p.dragStartX = p.x;
                p.dragStartY = p.y;
                refreshAutomationUi();
                return true;
            case MotionEvent.ACTION_MOVE:
                DisplayMetrics dm = getResources().getDisplayMetrics();
                int nx = p.dragStartX + Math.round(event.getRawX() - p.dragStartRawX);
                int ny = p.dragStartY + Math.round(event.getRawY() - p.dragStartRawY);
                p.x = clamp(nx, 0, dm.widthPixels - 1);
                p.y = clamp(ny, 0, dm.heightPixels - 1);
                moveMarker(p);
                return true;
            case MotionEvent.ACTION_UP:
                savePoints();
                refreshAutomationUi();
                return true;
            default:
                return true;
        }
    }

    private void moveMarker(ClickPoint p) {
        if (p.params == null || p.marker == null) return;
        p.params.x = p.x - p.params.width / 2;
        p.params.y = p.y - p.params.height / 2;
        try { windowManager.updateViewLayout(p.marker, p.params); } catch (Exception ignored) { }
    }

    private void setPointTouchable(ClickPoint p, boolean touchable) {
        if (p.params == null || p.marker == null) return;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        p.params.flags = flags;
        try { windowManager.updateViewLayout(p.marker, p.params); } catch (Exception ignored) { }
    }

    private void setAllMarkersVisible(boolean visible) {
        for (ClickPoint p : points) if (p.marker != null) p.marker.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void adjustPointInterval(int delta) {
        if (automationRunning) return;
        ClickPoint p = selectedPoint();
        if (p == null) return;
        p.intervalMs = clamp(p.intervalMs + delta, 10, 600000);
        savePoints();
        refreshAutomationUi();
    }

    private void removeSelectedPoint() {
        if (automationRunning) return;
        ClickPoint p = selectedPoint();
        if (p == null) return;
        if (p.marker != null) {
            try { windowManager.removeView(p.marker); } catch (Exception ignored) { }
        }
        points.remove(p);
        selectedPointId = points.isEmpty() ? -1 : points.get(0).id;
        savePoints();
        refreshAutomationUi();
    }

    private ClickPoint selectedPoint() {
        for (ClickPoint p : points) if (p.id == selectedPointId) return p;
        return null;
    }

    private void savePoints() {
        SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        ed.putInt("point_count", points.size());
        ed.putInt("next_point_id", nextPointId);
        for (int i = 0; i < MAX_POINTS; i++) {
            if (i < points.size()) {
                ClickPoint p = points.get(i);
                ed.putInt("p" + i + "_id", p.id);
                ed.putInt("p" + i + "_x", p.x);
                ed.putInt("p" + i + "_y", p.y);
                ed.putInt("p" + i + "_interval", p.intervalMs);
            } else {
                ed.remove("p" + i + "_id");
                ed.remove("p" + i + "_x");
                ed.remove("p" + i + "_y");
                ed.remove("p" + i + "_interval");
            }
        }
        ed.apply();
    }

    private void loadPoints() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int count = clamp(prefs.getInt("point_count", 0), 0, MAX_POINTS);
        nextPointId = Math.max(1, prefs.getInt("next_point_id", 1));
        points.clear();
        for (int i = 0; i < count; i++) {
            ClickPoint p = new ClickPoint();
            p.id = prefs.getInt("p" + i + "_id", i + 1);
            p.x = prefs.getInt("p" + i + "_x", 100);
            p.y = prefs.getInt("p" + i + "_y", 300);
            p.intervalMs = clamp(prefs.getInt("p" + i + "_interval", 100), 10, 600000);
            points.add(p);
        }
        if (!points.isEmpty()) selectedPointId = points.get(0).id;
    }

    private void saveSettings() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt("mode", mode)
                .putInt("direction", swipeDirection)
                .putInt("gesture_ms", gestureMs)
                .putInt("click_limit", clickLimit)
                .putBoolean("unlimited", unlimitedClicks)
                .apply();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        mode = clamp(prefs.getInt("mode", MODE_CLICK), MODE_CLICK, MODE_NINJA);
        swipeDirection = clamp(prefs.getInt("direction", 0), 0, 3);
        gestureMs = clamp(prefs.getInt("gesture_ms", 350), 80, 3000);
        clickLimit = clamp(prefs.getInt("click_limit", 100), 1, 10000);
        unlimitedClicks = prefs.getBoolean("unlimited", false);
    }

    // -------------------- VOZ --------------------

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Reconhecimento de voz não disponível neste celular.", Toast.LENGTH_LONG).show();
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                listening = true;
                updateVoiceUi("Ouvindo...");
            }
            @Override public void onBeginningOfSpeech() { updateVoiceUi("Pode falar..."); }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { updateVoiceUi("Processando..."); }
            @Override public void onError(int error) {
                listening = false;
                updateVoiceUi("Falha de voz (" + error + ")");
                if (speechRecognizer != null && error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    try { speechRecognizer.cancel(); } catch (Exception ignored) { }
                }
                if (continuous && !workflowActive && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    scheduleVoiceRestart(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1200 : 650);
                }
            }
            @Override public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    lastHeard = matches.get(0);
                    updateVoiceUi("OUVI: " + lastHeard);
                    executeCommand(lastHeard);
                } else {
                    updateVoiceUi("Não entendi");
                }
                if (continuous && !workflowActive) scheduleVoiceRestart(750);
            }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    private void startListening() {
        if (speechRecognizer == null || listening || workflowActive) return;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            feedback("Abra o app e libere o microfone");
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 650L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 450L);
        try {
            speechRecognizer.startListening(intent);
            listening = true;
            updateVoiceUi("Ouvindo...");
        } catch (Exception e) {
            listening = false;
            updateVoiceUi("Não consegui iniciar o microfone");
        }
    }

    private void stopListening() {
        listening = false;
        handler.removeCallbacks(voiceRestartRunnable);
        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) { }
        }
        updateVoiceUi("Microfone parado");
    }

    private void pauseVoiceForWorkflow() {
        listening = false;
        handler.removeCallbacks(voiceRestartRunnable);
        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (Exception ignored) { }
        }
    }

    private void scheduleVoiceRestart(long delay) {
        handler.removeCallbacks(voiceRestartRunnable);
        handler.postDelayed(voiceRestartRunnable, delay);
    }

    private final Runnable voiceRestartRunnable = () -> {
        if (continuous && !listening && !workflowActive) startListening();
    };

    private void updateVoiceUi(String text) {
        if (voiceStatus != null) {
            voiceStatus.setText(lastHeard.isEmpty() || text.startsWith("OUVI:")
                    ? text
                    : "OUVI: " + lastHeard + "\nAÇÃO: " + text);
        }
        if (listenButton != null) listenButton.setText(listening ? "■ PARAR MICROFONE" : "🎤 OUVIR AGORA");
        if (continuousButton != null) continuousButton.setText(continuous ? "CONTÍNUO: LIGADO" : "CONTÍNUO: DESLIGADO");
        if (miniBubble != null) miniBubble.setText(listening ? "●" : "🎤");
    }

    private void executeCommand(String raw) {
        String cmd = normalize(raw);
        if (cmd.startsWith("por favor ")) cmd = cmd.substring(10).trim();
        if (cmd.startsWith("pode ")) cmd = cmd.substring(5).trim();
        if (cmd.startsWith("voce pode ")) cmd = cmd.substring(9).trim();

        Matcher whats = Pattern.compile(
                "(?i)^(?:mandar|manda|mande|enviar|envie)\\s+(?:uma\\s+)?(?:mensagem\\s+)?(?:no\\s+whatsapp\\s+)?(?:para|pra)\\s+(.+?)\\s+(?:dizendo|falando|escrevendo|com\\s+a\\s+mensagem)\\s+(.+)$"
        ).matcher(raw.trim());
        if (whats.find()) {
            sendWhatsAppMessage(whats.group(1).trim(), whats.group(2).trim());
            return;
        }

        if (containsAny(cmd, "modo autoclicker", "modo clique", "modo cliques")) {
            setMode(MODE_CLICK); feedback("Modo AutoClicker"); return;
        }
        if (containsAny(cmd, "modo deslizar", "modo deslize")) {
            setMode(MODE_SWIPE); feedback("Modo deslizar"); return;
        }
        if (containsAny(cmd, "modo diagonal", "linha diagonal")) {
            setMode(MODE_DIAGONAL); feedback("Modo diagonal"); return;
        }
        if (containsAny(cmd, "modo horizontal", "dois horizontais", "2 horizontais")) {
            setMode(MODE_DUAL); feedback("Modo 2 horizontais"); return;
        }
        if (containsAny(cmd, "modo ninja", "fruit ninja", "tela toda")) {
            setMode(MODE_NINJA); feedback("Modo Ninja"); return;
        }
        if (containsAny(cmd, "iniciar automacao", "iniciar autoclicker", "iniciar cliques", "comecar automacao")) {
            startAutomation(); feedback("Automação iniciada"); return;
        }
        if (containsAny(cmd, "parar automacao", "parar autoclicker", "parar cliques")) {
            stopAutomation("Parado por voz"); feedback("Automação parada"); return;
        }
        if (containsAny(cmd, "adicionar ponto", "marcar ponto")) {
            setMode(MODE_CLICK); beginPointCapture(); feedback("Toque onde quer marcar o ponto"); return;
        }

        if (equalsAny(cmd, "voltar", "volte", "volta", "ir para tras", "vai para tras")) {
            performGlobalAction(GLOBAL_ACTION_BACK); feedback("Voltar"); return;
        }
        if (equalsAny(cmd, "inicio", "ir para inicio", "tela inicial", "home", "va para inicio", "vai para inicio")) {
            performGlobalAction(GLOBAL_ACTION_HOME); feedback("Início"); return;
        }
        if (containsAny(cmd, "recentes", "aplicativos recentes")) {
            performGlobalAction(GLOBAL_ACTION_RECENTS); feedback("Recentes"); return;
        }
        if (containsAny(cmd, "notificacoes", "abrir notificacoes")) {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); feedback("Notificações"); return;
        }
        if (containsAny(cmd, "configuracoes rapidas", "painel rapido")) {
            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS); feedback("Configurações rápidas"); return;
        }
        if (containsAny(cmd, "captura de tela", "tirar print", "print da tela")) {
            if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
            feedback("Captura de tela"); return;
        }
        if (containsAny(cmd, "bloquear tela", "travar tela")) {
            if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
            feedback("Bloquear tela"); return;
        }
        if (containsAny(cmd, "menu desligar", "menu de energia", "botao desligar")) {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); feedback("Menu de energia"); return;
        }

        if (containsAny(cmd, "aumentar volume", "aumente o volume", "sobe o volume")) {
            adjustVolume(AudioManager.ADJUST_RAISE); feedback("Volume aumentado"); return;
        }
        if (containsAny(cmd, "diminuir volume", "abaixar volume", "abaixe o volume", "baixa o volume")) {
            adjustVolume(AudioManager.ADJUST_LOWER); feedback("Volume diminuído"); return;
        }
        if (containsAny(cmd, "silenciar", "mudo", "tirar o som")) {
            adjustVolume(AudioManager.ADJUST_MUTE); feedback("Som silenciado"); return;
        }
        if (containsAny(cmd, "ativar som", "desmutar", "ligar som")) {
            adjustVolume(AudioManager.ADJUST_UNMUTE); feedback("Som ativado"); return;
        }
        if (containsAny(cmd, "ligar lanterna", "acender lanterna", "liga lanterna", "acende a lanterna")) {
            setTorch(true); return;
        }
        if (containsAny(cmd, "desligar lanterna", "apagar lanterna", "desliga lanterna", "apaga a lanterna")) {
            setTorch(false); return;
        }

        if (containsAny(cmd, "rolar para baixo", "descer tela", "descer a tela")) {
            dispatchSimpleSwipe(false, false); feedback("Rolando para baixo"); return;
        }
        if (containsAny(cmd, "rolar para cima", "subir tela", "subir a tela")) {
            dispatchSimpleSwipe(false, true); feedback("Rolando para cima"); return;
        }
        if (containsAny(cmd, "deslizar para esquerda", "arrastar para esquerda")) {
            dispatchSimpleSwipe(true, true); feedback("Deslizando para esquerda"); return;
        }
        if (containsAny(cmd, "deslizar para direita", "arrastar para direita")) {
            dispatchSimpleSwipe(true, false); feedback("Deslizando para direita"); return;
        }

        if (equalsAny(cmd, "apagar texto", "limpar texto", "limpar campo")) { clearFocusedText(); return; }
        if (equalsAny(cmd, "apagar ultima palavra", "excluir ultima palavra")) { deleteLastWord(); return; }
        if (equalsAny(cmd, "selecionar tudo", "seleciona tudo")) { selectAllFocused(); return; }
        if (equalsAny(cmd, "copiar", "copiar texto")) { copyFocused(); return; }
        if (equalsAny(cmd, "colar", "colar texto")) { pasteFocused(); return; }
        if (equalsAny(cmd, "enviar", "mandar", "enviar mensagem")) { clickAnyLabel("Enviar", "Send", "Mandar"); return; }

        if (cmd.startsWith("tocar em ") || cmd.startsWith("clicar em ") || cmd.startsWith("toque em ") || cmd.startsWith("clique em ")) {
            String text = raw.replaceFirst("(?i)^(tocar|clicar|toque|clique)\\s+em\\s+", "").trim();
            if (!text.isEmpty()) clickText(text);
            return;
        }
        if (cmd.startsWith("escrever ") || cmd.startsWith("digitar ") || cmd.startsWith("escreva ") || cmd.startsWith("digite ")) {
            String text = raw.replaceFirst("(?i)^(escrever|digitar|escreva|digite)\\s+", "").trim();
            if (!text.isEmpty()) setFocusedText(text);
            return;
        }
        if (cmd.startsWith("pesquisar ") || cmd.startsWith("buscar ") || cmd.startsWith("pesquise ") || cmd.startsWith("busque ")) {
            String query = raw.replaceFirst("(?i)^(pesquisar|buscar|pesquise|busque)\\s+", "").trim();
            if (!query.isEmpty()) webSearch(query);
            return;
        }
        if (cmd.startsWith("ligar para ") || cmd.startsWith("telefonar para ") || cmd.startsWith("ligue para ")) {
            String target = raw.replaceFirst("(?i)^(ligar|telefonar|ligue)\\s+para\\s+", "").trim();
            dial(target);
            return;
        }

        if (containsAny(cmd, "abrir wifi", "configuracoes wifi", "ligar wifi", "desligar wifi")) {
            startSystemIntent(new Intent(Settings.ACTION_WIFI_SETTINGS)); feedback("Abrindo Wi-Fi"); return;
        }
        if (containsAny(cmd, "abrir bluetooth", "configuracoes bluetooth", "ligar bluetooth", "desligar bluetooth")) {
            startSystemIntent(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); feedback("Abrindo Bluetooth"); return;
        }
        if (containsAny(cmd, "modo aviao", "abrir modo aviao")) {
            startSystemIntent(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)); feedback("Abrindo modo avião"); return;
        }
        if (containsAny(cmd, "aumentar brilho", "diminuir brilho", "configurar brilho")) {
            startSystemIntent(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); feedback("Abrindo brilho"); return;
        }
        if (containsAny(cmd, "abrir configuracoes", "configuracoes do celular")) {
            startSystemIntent(new Intent(Settings.ACTION_SETTINGS)); feedback("Configurações"); return;
        }
        if (containsAny(cmd, "abrir camera", "abra camera", "abre camera", "camera")) {
            Intent camera = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            camera.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startSystemIntent(camera); feedback("Câmera"); return;
        }
        if (cmd.startsWith("abrir ") || cmd.startsWith("abra ") || cmd.startsWith("abre ")) {
            String app = raw.replaceFirst("(?i)^(abrir|abra|abre)\\s+", "").trim();
            if (!app.isEmpty()) openApp(app);
            return;
        }

        if (equalsAny(cmd, "parar escuta", "parar de ouvir", "desligar escuta continua")) {
            continuous = false;
            stopListening();
            feedback("Escuta contínua desligada");
            return;
        }

        feedback("Comando não reconhecido: " + raw);
    }

    // -------------------- WHATSAPP MÃOS NA TELA --------------------

    private void sendWhatsAppMessage(String contact, String message) {
        workflowActive = true;
        pauseVoiceForWorkflow();
        pendingWhatsAppContact = contact;
        pendingWhatsAppMessage = message;
        whatsAppStep = 0;
        whatsAppRetries = 0;

        if (panelRoot != null) panelRoot.setVisibility(View.GONE);
        removeMiniBubble();

        PackageManager pm = getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage("com.whatsapp");
        if (launch == null) launch = pm.getLaunchIntentForPackage("com.whatsapp.w4b");
        if (launch == null) {
            finishWhatsApp(false, "WhatsApp não encontrado");
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
        updateVoiceUi("Abrindo WhatsApp para " + contact);
        handler.postDelayed(this::runWhatsAppHands, 1400);
    }

    private void runWhatsAppHands() {
        if (!workflowActive) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            retryWhatsApp("Esperando WhatsApp abrir");
            return;
        }

        if (whatsAppStep == 0) {
            if (clickSearchControl(root)) {
                whatsAppStep = 1;
                whatsAppRetries = 0;
                handler.postDelayed(this::runWhatsAppHands, 500);
            } else retryWhatsApp("Procurando Pesquisar");
            return;
        }

        if (whatsAppStep == 1) {
            AccessibilityNodeInfo edit = findAnyEditable(root);
            if (edit != null && setNodeText(edit, pendingWhatsAppContact)) {
                updateVoiceUi("Digitando contato: " + pendingWhatsAppContact);
                whatsAppStep = 2;
                whatsAppRetries = 0;
                handler.postDelayed(this::runWhatsAppHands, 900);
            } else retryWhatsApp("Esperando campo de pesquisa");
            return;
        }

        if (whatsAppStep == 2) {
            AccessibilityNodeInfo contactNode = findMatchingNode(root, pendingWhatsAppContact, false);
            if (contactNode != null && clickNodeOrParent(contactNode)) {
                updateVoiceUi("Abrindo conversa com " + pendingWhatsAppContact);
                whatsAppStep = 3;
                whatsAppRetries = 0;
                handler.postDelayed(this::runWhatsAppHands, 1000);
            } else retryWhatsApp("Procurando contato " + pendingWhatsAppContact);
            return;
        }

        if (whatsAppStep == 3) {
            AccessibilityNodeInfo edit = findMessageEditable(root);
            if (edit == null) edit = findBottomEditable(root);
            if (edit != null && setNodeText(edit, pendingWhatsAppMessage)) {
                updateVoiceUi("Mensagem digitada");
                whatsAppStep = 4;
                whatsAppRetries = 0;
                handler.postDelayed(this::runWhatsAppHands, 550);
            } else retryWhatsApp("Esperando campo da mensagem");
            return;
        }

        if (whatsAppStep == 4) {
            if (clickSendControl(root)) {
                finishWhatsApp(true, "Mensagem enviada para " + pendingWhatsAppContact);
            } else retryWhatsApp("Procurando botão Enviar");
        }
    }

    private void retryWhatsApp(String text) {
        whatsAppRetries++;
        if (whatsAppRetries > 14) {
            finishWhatsApp(false, "Não consegui concluir: " + text);
            return;
        }
        updateVoiceUi(text + " (" + whatsAppRetries + ")");
        handler.postDelayed(this::runWhatsAppHands, 500);
    }

    private void finishWhatsApp(boolean success, String message) {
        workflowActive = false;
        pendingWhatsAppContact = "";
        pendingWhatsAppMessage = "";
        whatsAppStep = -1;
        whatsAppRetries = 0;
        feedback(message);
        if (panelMinimized || (panelRoot != null && panelRoot.getVisibility() != View.VISIBLE)) {
            showMiniBubble();
        }
        if (continuous) scheduleVoiceRestart(900);
    }

    private boolean clickSearchControl(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo node = findByDescription(root, "pesquisar");
        if (node == null) node = findByDescription(root, "search");
        if (node != null && clickNodeOrParent(node)) return true;
        if (clickMatchingText(root, "Pesquisar") || clickMatchingText(root, "Search")) return true;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        dispatchTap(dm.widthPixels * 0.86f, dm.heightPixels * 0.075f);
        return true;
    }

    private boolean clickSendControl(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo node = findByDescription(root, "enviar");
        if (node == null) node = findByDescription(root, "send");
        if (node != null && clickNodeOrParent(node)) return true;
        if (clickMatchingText(root, "Enviar") || clickMatchingText(root, "Send")) return true;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        dispatchTap(dm.widthPixels * 0.92f, dm.heightPixels * 0.88f);
        return true;
    }

    // -------------------- AÇÕES DE TELA / TEXTO --------------------

    private void dispatchSimpleSwipe(boolean horizontal, boolean firstDirection) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float w = dm.widthPixels;
        float h = dm.heightPixels;
        Path path = new Path();
        if (horizontal) {
            float y = h * 0.52f;
            if (firstDirection) {
                path.moveTo(w * 0.82f, y);
                path.lineTo(w * 0.18f, y);
            } else {
                path.moveTo(w * 0.18f, y);
                path.lineTo(w * 0.82f, y);
            }
        } else {
            float x = w * 0.5f;
            if (firstDirection) {
                path.moveTo(x, h * 0.72f);
                path.lineTo(x, h * 0.30f);
            } else {
                path.moveTo(x, h * 0.30f);
                path.lineTo(x, h * 0.72f);
            }
        }
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 350))
                .build();
        dispatchGesture(gesture, null, null);
    }

    private GestureDescription buildTap(float x, float y, long duration) {
        Path path = new Path();
        path.moveTo(x, y);
        return new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();
    }

    private void dispatchTap(float x, float y) {
        dispatchGesture(buildTap(x, y, 70), null, null);
    }

    private void clickText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { feedback("Não encontrei elementos na tela"); return; }
        AccessibilityNodeInfo node = findMatchingNode(root, text, true);
        if (node != null && clickNodeOrParent(node)) {
            feedback("Toquei em " + text);
            return;
        }
        feedback("Não encontrei: " + text);
    }

    private AccessibilityNodeInfo findMatchingNode(AccessibilityNodeInfo root, String wanted, boolean allowEditable) {
        if (root == null) return null;
        String normalizedWanted = normalize(wanted);
        ArrayList<AccessibilityNodeInfo> exact = new ArrayList<>();
        ArrayList<AccessibilityNodeInfo> partial = new ArrayList<>();
        collectMatchingNodes(root, normalizedWanted, allowEditable, exact, partial);
        if (!exact.isEmpty()) return exact.get(0);
        if (!partial.isEmpty()) return partial.get(0);
        return null;
    }

    private void collectMatchingNodes(AccessibilityNodeInfo node, String wanted, boolean allowEditable,
                                      List<AccessibilityNodeInfo> exact, List<AccessibilityNodeInfo> partial) {
        if (node == null) return;
        if (node.isVisibleToUser() && (allowEditable || !node.isEditable())) {
            String value = "";
            if (node.getText() != null) value = normalize(node.getText().toString());
            if (value.isEmpty() && node.getContentDescription() != null) value = normalize(node.getContentDescription().toString());
            if (!value.isEmpty()) {
                if (value.equals(wanted)) exact.add(node);
                else if (value.contains(wanted) || wanted.contains(value)) partial.add(node);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectMatchingNodes(node.getChild(i), wanted, allowEditable, exact, partial);
        }
    }

    private boolean clickMatchingText(AccessibilityNodeInfo root, String wanted) {
        AccessibilityNodeInfo node = findMatchingNode(root, wanted, false);
        return node != null && clickNodeOrParent(node);
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo original = node;
        AccessibilityNodeInfo current = node;
        int depth = 0;
        while (current != null && depth < 8) {
            if (current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            current = current.getParent();
            depth++;
        }
        if (original != null && original.isVisibleToUser()) {
            Rect bounds = new Rect();
            original.getBoundsInScreen(bounds);
            if (!bounds.isEmpty()) {
                dispatchTap(bounds.exactCenterX(), bounds.exactCenterY());
                return true;
            }
        }
        return false;
    }

    private AccessibilityNodeInfo findByDescription(AccessibilityNodeInfo node, String wanted) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        if (desc != null && normalize(desc.toString()).contains(normalize(wanted))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findByDescription(node.getChild(i), wanted);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo focusedEditable() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null && focused.isEditable()) return focused;
        return findAnyEditable(root);
    }

    private AccessibilityNodeInfo findAnyEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isVisibleToUser()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findAnyEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findBottomEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        AccessibilityNodeInfo best = null;
        Rect bestRect = new Rect();
        if (node.isEditable() && node.isVisibleToUser()) {
            node.getBoundsInScreen(bestRect);
            best = node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = findBottomEditable(node.getChild(i));
            if (child != null) {
                Rect r = new Rect();
                child.getBoundsInScreen(r);
                if (best == null || r.centerY() > bestRect.centerY()) {
                    best = child;
                    bestRect = r;
                }
            }
        }
        return best;
    }

    private AccessibilityNodeInfo findMessageEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isVisibleToUser()) {
            String hint = "";
            if (Build.VERSION.SDK_INT >= 26 && node.getHintText() != null) hint = normalize(node.getHintText().toString());
            String desc = node.getContentDescription() == null ? "" : normalize(node.getContentDescription().toString());
            if (hint.contains("mensagem") || hint.contains("message") || desc.contains("mensagem") || desc.contains("message")) return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findMessageEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private boolean setNodeText(AccessibilityNodeInfo node, String value) {
        if (node == null) return false;
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private void setFocusedText(String text) {
        AccessibilityNodeInfo focused = focusedEditable();
        if (focused == null) { feedback("Toque primeiro em um campo de texto"); return; }
        feedback(setNodeText(focused, text) ? "Texto escrito" : "Não consegui escrever");
    }

    private void clearFocusedText() { setFocusedText(""); }

    private void deleteLastWord() {
        AccessibilityNodeInfo focused = focusedEditable();
        if (focused == null) { feedback("Toque primeiro em um campo de texto"); return; }
        String current = focused.getText() == null ? "" : focused.getText().toString();
        String trimmed = current.replaceFirst("\\s+$", "");
        int idx = trimmed.lastIndexOf(' ');
        String next = idx >= 0 ? trimmed.substring(0, idx + 1) : "";
        feedback(setNodeText(focused, next) ? "Última palavra apagada" : "Não consegui apagar");
    }

    private void selectAllFocused() {
        AccessibilityNodeInfo focused = focusedEditable();
        if (focused == null) { feedback("Toque primeiro em um campo de texto"); return; }
        int length = focused.getText() == null ? 0 : focused.getText().length();
        Bundle args = new Bundle();
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, length);
        feedback(focused.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args) ? "Texto selecionado" : "Não consegui selecionar");
    }

    private void copyFocused() {
        AccessibilityNodeInfo focused = focusedEditable();
        if (focused == null) { feedback("Toque primeiro em um campo de texto"); return; }
        selectAllFocused();
        feedback(focused.performAction(AccessibilityNodeInfo.ACTION_COPY) ? "Texto copiado" : "Não consegui copiar");
    }

    private void pasteFocused() {
        AccessibilityNodeInfo focused = focusedEditable();
        if (focused == null) { feedback("Toque primeiro em um campo de texto"); return; }
        feedback(focused.performAction(AccessibilityNodeInfo.ACTION_PASTE) ? "Texto colado" : "Não consegui colar");
    }

    private void clickAnyLabel(String... labels) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { feedback("Não encontrei botão na tela"); return; }
        for (String label : labels) {
            AccessibilityNodeInfo node = findMatchingNode(root, label, false);
            if (node != null && clickNodeOrParent(node)) {
                feedback("Executado: " + label);
                return;
            }
        }
        feedback("Não encontrei o botão");
    }

    // -------------------- SISTEMA / APPS --------------------

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
            feedback("Não consegui controlar a lanterna");
        }
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
        if (digits.isEmpty()) { feedback("Fale um número para ligar"); return; }
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + digits));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startSystemIntent(intent);
        feedback("Abrindo telefone");
    }

    private void openApp(String target) {
        String normalized = normalize(target);
        if (normalized.contains("whatsapp")) {
            PackageManager pm = getPackageManager();
            Intent wa = pm.getLaunchIntentForPackage("com.whatsapp");
            if (wa == null) wa = pm.getLaunchIntentForPackage("com.whatsapp.w4b");
            if (wa != null) {
                wa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(wa);
                feedback("Abrindo WhatsApp");
                return;
            }
        }

        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN, null);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcher, 0);
        ResolveInfo best = null;
        for (ResolveInfo info : apps) {
            String label = String.valueOf(info.loadLabel(pm));
            String nl = normalize(label);
            if (nl.equals(normalized)) { best = info; break; }
            if (best == null && !nl.isEmpty() && (nl.contains(normalized) || normalized.contains(nl))) best = info;
        }
        if (best == null) { feedback("Não encontrei o app " + target); return; }
        Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch == null) { feedback("Não consegui abrir " + target); return; }
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

    // -------------------- UTIL --------------------

    private void feedback(String text) {
        updateVoiceUi(text);
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private String normalize(String text) {
        if (text == null) return "";
        String n = Normalizer.normalize(text.toLowerCase(new Locale("pt", "BR")), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9+ ]", " ")
                .trim()
                .replaceAll("\\s+", " ");
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

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(10);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private LinearLayout.LayoutParams fullWidth(int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        lp.setMargins(0, dp(2), 0, dp(2));
        return lp;
    }

    private LinearLayout.LayoutParams buttonParams() {
        return fullWidth(dp(48));
    }

    private LinearLayout.LayoutParams rowButtonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        return lp;
    }

    private LinearLayout.LayoutParams smallParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lp.setMargins(dp(1), 0, dp(1), 0);
        return lp;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() {
        stopAutomation("Interrompido");
        stopListening();
    }

    @Override
    public void onDestroy() {
        automationRunning = false;
        workflowActive = false;
        continuous = false;
        handler.removeCallbacksAndMessages(null);

        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (Exception ignored) { }
            try { speechRecognizer.destroy(); } catch (Exception ignored) { }
            speechRecognizer = null;
        }

        if (captureOverlay != null && windowManager != null) {
            try { windowManager.removeView(captureOverlay); } catch (Exception ignored) { }
            captureOverlay = null;
        }

        for (ClickPoint p : points) {
            if (p.marker != null && windowManager != null) {
                try { windowManager.removeView(p.marker); } catch (Exception ignored) { }
            }
            p.marker = null;
        }

        removeMiniBubble();
        if (panelRoot != null && windowManager != null) {
            try { windowManager.removeView(panelRoot); } catch (Exception ignored) { }
            panelRoot = null;
        }
        if (torchOn) setTorch(false);
        super.onDestroy();
    }
}
