package com.autoclicker.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class AutoClickService extends AccessibilityService {
    private WindowManager windowManager;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelParams;
    private TextView stopBubble;
    private WindowManager.LayoutParams stopParams;
    private TextView status;
    private Button startButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private boolean dispatching;
    private boolean unlimited;
    private int pattern;
    private int gestureMs;
    private int pauseMs;
    private int runSeconds;
    private long startedAt;
    private long cycle;

    private float panelDownX, panelDownY;
    private int panelStartX, panelStartY;
    private float stopDownX, stopDownY;
    private int stopStartX, stopStartY;
    private boolean stopMoved;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showPanel();
    }

    private void showPanel() {
        if (panel != null || windowManager == null) return;

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(10));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(240, 25, 30, 38));
        background.setCornerRadius(dp(16));
        panel.setBackground(background);

        TextView title = new TextView(this);
        title.setText("FRUIT NINJA  •  ARRASTE AQUI");
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setOnTouchListener((v, event) -> dragPanel(event));
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        status = new TextView(this);
        status.setText("Pronto para iniciar");
        status.setTextColor(Color.rgb(225, 230, 238));
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(4), dp(4), dp(4), dp(4));
        panel.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        Button reload = button("RECARREGAR CONFIG");
        reload.setOnClickListener(v -> {
            readSettings();
            updateIdleStatus();
            Toast.makeText(this, "Configurações recarregadas", Toast.LENGTH_SHORT).show();
        });
        panel.addView(reload, buttonParams());

        startButton = button("INICIAR NINJA");
        startButton.setOnClickListener(v -> startNinja());
        panel.addView(startButton, buttonParams());

        Button close = button("FECHAR PAINEL");
        close.setOnClickListener(v -> disableSelf());
        panel.addView(close, buttonParams());

        panelParams = new WindowManager.LayoutParams(
                dp(230),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = dp(12);
        panelParams.y = dp(90);

        windowManager.addView(panel, panelParams);
        readSettings();
        updateIdleStatus();
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
                try { windowManager.updateViewLayout(panel, panelParams); }
                catch (Exception ignored) { }
                return true;
            default:
                return true;
        }
    }

    private void readSettings() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        pattern = clamp(prefs.getInt(MainActivity.KEY_PATTERN, 0), 0, 2);
        gestureMs = clamp(prefs.getInt(MainActivity.KEY_GESTURE_MS, 380), 120, 5000);
        pauseMs = clamp(prefs.getInt(MainActivity.KEY_PAUSE_MS, 20), 0, 10000);
        runSeconds = clamp(prefs.getInt(MainActivity.KEY_RUN_SECONDS, 30), 1, 3600);
        unlimited = prefs.getBoolean(MainActivity.KEY_UNLIMITED, false);
    }

    private void updateIdleStatus() {
        if (status == null || running) return;
        status.setText(patternName() + " • " + gestureMs + " ms" +
                (unlimited ? " • ilimitado" : " • " + runSeconds + " s"));
    }

    private void startNinja() {
        if (running) return;
        readSettings();
        running = true;
        dispatching = false;
        cycle = 0;
        startedAt = SystemClock.uptimeMillis();
        if (startButton != null) startButton.setEnabled(false);
        if (panel != null) panel.setVisibility(View.GONE);
        showStopBubble();
        handler.post(ninjaRunnable);
    }

    private final Runnable ninjaRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || dispatching) return;

            long elapsed = SystemClock.uptimeMillis() - startedAt;
            if (!unlimited && elapsed >= runSeconds * 1000L) {
                stopNinja("Concluído");
                return;
            }

            dispatching = true;
            Path path = buildNinjaPath();
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, gestureMs))
                    .build();

            boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    dispatching = false;
                    if (!running) return;
                    cycle++;
                    handler.postDelayed(ninjaRunnable, pauseMs);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    dispatching = false;
                    if (running) handler.postDelayed(ninjaRunnable, Math.max(30, pauseMs));
                }
            }, null);

            if (!accepted) {
                dispatching = false;
                handler.postDelayed(this, Math.max(30, pauseMs));
            }
        }
    };

    private Path buildNinjaPath() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float width = dm.widthPixels;
        float height = dm.heightPixels;

        // Reserva o canto superior direito para o botão PARAR.
        float left = width * 0.06f;
        float right = width * 0.82f;
        float top = height * 0.08f;
        float bottom = height * 0.94f;
        float cx = (left + right) / 2f;
        float cy = (top + bottom) / 2f;
        float rx = (right - left) / 2f;
        float ry = (bottom - top) / 2f;
        float phase = (float) ((cycle % 24) * 0.27);

        Path path = new Path();

        if (pattern == 1) {
            // Elipse/círculo grande, com fase variável para não repetir exatamente igual.
            int steps = 42;
            for (int i = 0; i <= steps; i++) {
                double t = (Math.PI * 2.0 * i / steps) + phase;
                float x = cx + rx * 0.95f * (float) Math.cos(t);
                float y = cy + ry * 0.88f * (float) Math.sin(t);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            return path;
        }

        if (pattern == 2) {
            // Zigue-zague cobrindo linhas sucessivas da tela.
            int rows = 9;
            boolean reverse = (cycle % 2) == 1;
            for (int row = 0; row <= rows; row++) {
                float y = top + (bottom - top) * row / rows;
                boolean toRight = ((row % 2) == 0) ^ reverse;
                float x = toRight ? right : left;
                if (row == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            return path;
        }

        // TELA TODA: curva de Lissajous que cruza a área várias vezes como cortes.
        int steps = 48;
        for (int i = 0; i <= steps; i++) {
            double t = Math.PI * 2.0 * i / steps;
            float x = cx + rx * 0.98f * (float) Math.sin(2.0 * t + phase);
            float y = cy + ry * 0.96f * (float) Math.sin(3.0 * t + 1.1 + phase * 1.35);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        return path;
    }

    private void showStopBubble() {
        if (stopBubble != null || windowManager == null) return;

        stopBubble = new TextView(this);
        stopBubble.setText("PARAR");
        stopBubble.setTextColor(Color.WHITE);
        stopBubble.setTextSize(12);
        stopBubble.setGravity(Gravity.CENTER);
        stopBubble.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(245, 210, 35, 45));
        bg.setStroke(dp(3), Color.WHITE);
        stopBubble.setBackground(bg);
        stopBubble.setOnTouchListener((v, event) -> handleStopBubble(event));

        stopParams = new WindowManager.LayoutParams(
                dp(68), dp(68),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        stopParams.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        stopParams.x = Math.max(0, dm.widthPixels - dp(78));
        stopParams.y = dp(65);

        try { windowManager.addView(stopBubble, stopParams); }
        catch (Exception ignored) { stopBubble = null; stopParams = null; }
    }

    private boolean handleStopBubble(MotionEvent event) {
        if (stopParams == null) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopDownX = event.getRawX();
                stopDownY = event.getRawY();
                stopStartX = stopParams.x;
                stopStartY = stopParams.y;
                stopMoved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - stopDownX;
                float dy = event.getRawY() - stopDownY;
                if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) stopMoved = true;
                stopParams.x = stopStartX + Math.round(dx);
                stopParams.y = stopStartY + Math.round(dy);
                try { windowManager.updateViewLayout(stopBubble, stopParams); }
                catch (Exception ignored) { }
                return true;
            case MotionEvent.ACTION_UP:
                if (!stopMoved) stopNinja("Parado");
                return true;
            default:
                return true;
        }
    }

    private void stopNinja(String text) {
        running = false;
        dispatching = false;
        handler.removeCallbacks(ninjaRunnable);
        removeStopBubble();
        if (panel != null) panel.setVisibility(View.VISIBLE);
        if (startButton != null) startButton.setEnabled(true);
        if (status != null) {
            status.setText(text + " • " + cycle + " movimento(s)");
        }
    }

    private void removeStopBubble() {
        if (stopBubble != null && windowManager != null) {
            try { windowManager.removeView(stopBubble); }
            catch (Exception ignored) { }
        }
        stopBubble = null;
        stopParams = null;
    }

    private String patternName() {
        if (pattern == 1) return "CÍRCULO";
        if (pattern == 2) return "ZIGUE-ZAGUE";
        return "TELA TODA";
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
        stopNinja("Interrompido");
    }

    @Override
    public void onDestroy() {
        running = false;
        dispatching = false;
        handler.removeCallbacks(ninjaRunnable);
        removeStopBubble();
        if (panel != null && windowManager != null) {
            try { windowManager.removeView(panel); }
            catch (Exception ignored) { }
            panel = null;
        }
        super.onDestroy();
    }
}
