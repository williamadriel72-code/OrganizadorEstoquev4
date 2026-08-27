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
    private View captureOverlay;
    private View targetMarker;
    private WindowManager.LayoutParams panelParams;
    private WindowManager.LayoutParams markerParams;
    private TextView status;
    private Button markButton;
    private Button startButton;
    private Button stopButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private boolean hasTarget = false;
    private int targetX;
    private int targetY;
    private int current = 0;
    private int total = 100;
    private int intervalMs = 100;

    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartX;
    private int dragStartY;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showPanel();
    }

    private void showPanel() {
        if (panel != null) return;

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(238, 25, 30, 38));
        bg.setCornerRadius(dp(14));
        panel.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("AUTOCLICKER  •  ARRASTE AQUI");
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(4), dp(4), dp(4), dp(7));
        title.setOnTouchListener((v, event) -> handlePanelDrag(event));
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(38)
        ));

        status = new TextView(this);
        status.setText("Marque um ponto");
        status.setTextColor(Color.rgb(220, 225, 235));
        status.setGravity(Gravity.CENTER);
        status.setTextSize(12);
        status.setPadding(0, 0, 0, dp(6));
        panel.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(32)
        ));

        markButton = makeButton("MARCAR PONTO");
        markButton.setOnClickListener(v -> beginPointCapture());
        panel.addView(markButton, buttonParams());

        startButton = makeButton("INICIAR");
        startButton.setEnabled(false);
        startButton.setOnClickListener(v -> startClicks());
        panel.addView(startButton, buttonParams());

        stopButton = makeButton("PARAR");
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> stopClicks("Parado"));
        panel.addView(stopButton, buttonParams());

        panelParams = new WindowManager.LayoutParams(
                dp(210),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = dp(12);
        panelParams.y = dp(110);

        windowManager.addView(panel, panelParams);
    }

    private boolean handlePanelDrag(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartRawX = event.getRawX();
                dragStartRawY = event.getRawY();
                dragStartX = panelParams.x;
                dragStartY = panelParams.y;
                return true;
            case MotionEvent.ACTION_MOVE:
                panelParams.x = dragStartX + Math.round(event.getRawX() - dragStartRawX);
                panelParams.y = dragStartY + Math.round(event.getRawY() - dragStartRawY);
                try {
                    windowManager.updateViewLayout(panel, panelParams);
                } catch (Exception ignored) {
                }
                return true;
            default:
                return true;
        }
    }

    private void beginPointCapture() {
        if (running || captureOverlay != null) return;

        panel.setVisibility(View.GONE);
        captureOverlay = new View(this);
        captureOverlay.setBackgroundColor(Color.argb(1, 0, 0, 0));
        captureOverlay.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                targetX = Math.round(event.getRawX());
                targetY = Math.round(event.getRawY());
                hasTarget = true;
                endPointCapture();
                showTargetMarker();
                status.setText("Ponto: X=" + targetX + "  Y=" + targetY);
                startButton.setEnabled(true);
                Toast.makeText(this, "Ponto marcado", Toast.LENGTH_SHORT).show();
                return true;
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
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(captureOverlay, params);
        Toast.makeText(this, "Toque uma vez exatamente no ponto desejado", Toast.LENGTH_LONG).show();
    }

    private void showTargetMarker() {
        if (windowManager == null || !hasTarget) return;

        final int markerSize = dp(28);

        if (targetMarker == null) {
            targetMarker = new View(this);

            GradientDrawable marker = new GradientDrawable();
            marker.setShape(GradientDrawable.OVAL);
            marker.setColor(Color.argb(150, 255, 45, 45));
            marker.setStroke(dp(2), Color.WHITE);
            targetMarker.setBackground(marker);

            markerParams = new WindowManager.LayoutParams(
                    markerSize,
                    markerSize,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            markerParams.gravity = Gravity.TOP | Gravity.START;
            markerParams.x = targetX - markerSize / 2;
            markerParams.y = targetY - markerSize / 2;

            try {
                windowManager.addView(targetMarker, markerParams);
            } catch (Exception ignored) {
                targetMarker = null;
                markerParams = null;
            }
        } else {
            markerParams.x = targetX - markerSize / 2;
            markerParams.y = targetY - markerSize / 2;
            try {
                windowManager.updateViewLayout(targetMarker, markerParams);
            } catch (Exception ignored) {
            }
        }
    }

    private void removeTargetMarker() {
        if (targetMarker != null && windowManager != null) {
            try {
                windowManager.removeView(targetMarker);
            } catch (Exception ignored) {
            }
        }
        targetMarker = null;
        markerParams = null;
    }

    private void endPointCapture() {
        if (captureOverlay != null) {
            try {
                windowManager.removeView(captureOverlay);
            } catch (Exception ignored) {
            }
            captureOverlay = null;
        }
        if (panel != null) panel.setVisibility(View.VISIBLE);
    }

    private void startClicks() {
        if (!hasTarget || running) return;

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        total = clamp(prefs.getInt(MainActivity.KEY_QTY, 100), 1, 100);
        intervalMs = clamp(prefs.getInt(MainActivity.KEY_INTERVAL, 100), 10, 60000);

        current = 0;
        running = true;
        markButton.setEnabled(false);
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        status.setText("Executando 0/" + total + " • " + intervalMs + " ms");
        handler.post(clickRunnable);
    }

    private final Runnable clickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (current >= total) {
                finishRun();
                return;
            }

            Path path = new Path();
            path.moveTo(targetX, targetY);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, 1);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(stroke)
                    .build();

            boolean accepted = dispatchGesture(
                    gesture,
                    new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            super.onCompleted(gestureDescription);
                            if (!running) return;
                            current++;
                            status.setText("Cliques " + current + "/" + total + " • " + intervalMs + " ms");
                            if (current >= total) {
                                finishRun();
                            } else {
                                handler.postDelayed(clickRunnable, intervalMs);
                            }
                        }

                        @Override
                        public void onCancelled(GestureDescription gestureDescription) {
                            super.onCancelled(gestureDescription);
                            if (running) handler.postDelayed(clickRunnable, intervalMs);
                        }
                    },
                    null
            );

            if (!accepted && running) {
                handler.postDelayed(this, intervalMs);
            }
        }
    };

    private void finishRun() {
        running = false;
        handler.removeCallbacks(clickRunnable);
        markButton.setEnabled(true);
        startButton.setEnabled(hasTarget);
        stopButton.setEnabled(false);
        status.setText("Concluído: " + current + "/" + total);
        Toast.makeText(this, "Sequência concluída", Toast.LENGTH_SHORT).show();
    }

    private void stopClicks(String text) {
        running = false;
        handler.removeCallbacks(clickRunnable);
        if (markButton != null) markButton.setEnabled(true);
        if (startButton != null) startButton.setEnabled(hasTarget);
        if (stopButton != null) stopButton.setEnabled(false);
        if (status != null) status.setText(text + " em " + current + "/" + total);
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        );
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
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        stopClicks("Interrompido");
    }

    @Override
    public void onDestroy() {
        stopClicks("Encerrado");
        endPointCapture();
        removeTargetMarker();
        if (panel != null && windowManager != null) {
            try {
                windowManager.removeView(panel);
            } catch (Exception ignored) {
            }
            panel = null;
        }
        super.onDestroy();
    }
}
