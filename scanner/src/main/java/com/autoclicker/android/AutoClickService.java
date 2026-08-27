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
    private View captureOverlay;
    private View targetMarker;
    private TextView miniButton;
    private WindowManager.LayoutParams panelParams;
    private WindowManager.LayoutParams markerParams;
    private WindowManager.LayoutParams miniParams;
    private TextView status;
    private Button markButton;
    private Button startButton;
    private Button stopButton;
    private Button minimizeButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private boolean hasTarget = false;
    private boolean unlimited = false;
    private boolean panelMinimized = false;
    private int targetX;
    private int targetY;
    private long current = 0;
    private int total = 100;
    private int intervalMs = 100;

    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartX;
    private int dragStartY;

    private float markerDragStartRawX;
    private float markerDragStartRawY;
    private int markerDragStartTargetX;
    private int markerDragStartTargetY;

    private float miniDragStartRawX;
    private float miniDragStartRawY;
    private int miniDragStartX;
    private int miniDragStartY;
    private boolean miniMoved;

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

        minimizeButton = makeButton("MINIMIZAR");
        minimizeButton.setOnClickListener(v -> minimizePanel());
        panel.addView(minimizeButton, buttonParams());

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

    private void minimizePanel() {
        if (panel == null || panelMinimized) return;
        panelMinimized = true;
        panel.setVisibility(View.GONE);
        showMiniButton();
    }

    private void restorePanel() {
        panelMinimized = false;
        removeMiniButton();
        if (panel != null) panel.setVisibility(View.VISIBLE);
    }

    private void showMiniButton() {
        if (windowManager == null || miniButton != null) return;

        miniButton = new TextView(this);
        miniButton.setText("≡");
        miniButton.setTextColor(Color.WHITE);
        miniButton.setTextSize(26);
        miniButton.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(235, 35, 105, 220));
        bg.setStroke(dp(2), Color.WHITE);
        miniButton.setBackground(bg);
        miniButton.setOnTouchListener((v, event) -> handleMiniButtonTouch(event));

        miniParams = new WindowManager.LayoutParams(
                dp(54),
                dp(54),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        miniParams.gravity = Gravity.TOP | Gravity.START;
        miniParams.x = panelParams != null ? panelParams.x : dp(12);
        miniParams.y = panelParams != null ? panelParams.y : dp(110);

        try {
            windowManager.addView(miniButton, miniParams);
        } catch (Exception ignored) {
            miniButton = null;
            miniParams = null;
        }
    }

    private boolean handleMiniButtonTouch(MotionEvent event) {
        if (miniParams == null) return true;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                miniDragStartRawX = event.getRawX();
                miniDragStartRawY = event.getRawY();
                miniDragStartX = miniParams.x;
                miniDragStartY = miniParams.y;
                miniMoved = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - miniDragStartRawX;
                float dy = event.getRawY() - miniDragStartRawY;
                if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) miniMoved = true;
                miniParams.x = miniDragStartX + Math.round(dx);
                miniParams.y = miniDragStartY + Math.round(dy);
                try {
                    windowManager.updateViewLayout(miniButton, miniParams);
                } catch (Exception ignored) {
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!miniMoved) restorePanel();
                return true;

            default:
                return true;
        }
    }

    private void removeMiniButton() {
        if (miniButton != null && windowManager != null) {
            try {
                windowManager.removeView(miniButton);
            } catch (Exception ignored) {
            }
        }
        miniButton = null;
        miniParams = null;
    }

    private void beginPointCapture() {
        if (running || captureOverlay != null) return;

        panel.setVisibility(View.GONE);
        if (targetMarker != null) targetMarker.setVisibility(View.GONE);

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
                Toast.makeText(this, "Ponto marcado — arraste a bolinha para ajustar", Toast.LENGTH_SHORT).show();
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

        final int markerSize = dp(38);

        if (targetMarker == null) {
            targetMarker = new View(this);

            GradientDrawable marker = new GradientDrawable();
            marker.setShape(GradientDrawable.OVAL);
            marker.setColor(Color.argb(175, 255, 45, 45));
            marker.setStroke(dp(3), Color.WHITE);
            targetMarker.setBackground(marker);
            targetMarker.setOnTouchListener((v, event) -> handleMarkerDrag(event));

            markerParams = new WindowManager.LayoutParams(
                    markerSize,
                    markerSize,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
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
                return;
            }
        } else {
            targetMarker.setVisibility(View.VISIBLE);
            moveMarkerToTarget();
        }

        setMarkerTouchable(!running);
    }

    private boolean handleMarkerDrag(MotionEvent event) {
        if (running || markerParams == null) return true;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                markerDragStartRawX = event.getRawX();
                markerDragStartRawY = event.getRawY();
                markerDragStartTargetX = targetX;
                markerDragStartTargetY = targetY;
                return true;

            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                DisplayMetrics dm = getResources().getDisplayMetrics();
                int newX = markerDragStartTargetX + Math.round(event.getRawX() - markerDragStartRawX);
                int newY = markerDragStartTargetY + Math.round(event.getRawY() - markerDragStartRawY);
                targetX = clamp(newX, 0, Math.max(0, dm.widthPixels - 1));
                targetY = clamp(newY, 0, Math.max(0, dm.heightPixels - 1));
                moveMarkerToTarget();
                if (status != null) {
                    status.setText("Ponto: X=" + targetX + "  Y=" + targetY);
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    Toast.makeText(this, "Ponto reposicionado", Toast.LENGTH_SHORT).show();
                }
                return true;

            default:
                return true;
        }
    }

    private void moveMarkerToTarget() {
        if (targetMarker == null || markerParams == null || windowManager == null) return;
        markerParams.x = targetX - markerParams.width / 2;
        markerParams.y = targetY - markerParams.height / 2;
        try {
            windowManager.updateViewLayout(targetMarker, markerParams);
        } catch (Exception ignored) {
        }
    }

    private void setMarkerTouchable(boolean touchable) {
        if (targetMarker == null || markerParams == null || windowManager == null) return;
        if (touchable) {
            markerParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            markerParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        try {
            windowManager.updateViewLayout(targetMarker, markerParams);
        } catch (Exception ignored) {
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
        if (panel != null && !panelMinimized) panel.setVisibility(View.VISIBLE);
    }

    private void startClicks() {
        if (!hasTarget || running) return;

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        total = clamp(prefs.getInt(MainActivity.KEY_QTY, 100), 1, 100);
        intervalMs = clamp(prefs.getInt(MainActivity.KEY_INTERVAL, 100), 10, 60000);
        unlimited = prefs.getBoolean(MainActivity.KEY_UNLIMITED, false);

        current = 0;
        running = true;
        setMarkerTouchable(false);
        markButton.setEnabled(false);
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        status.setText(unlimited
                ? "Executando 0/∞ • " + intervalMs + " ms"
                : "Executando 0/" + total + " • " + intervalMs + " ms");
        handler.post(clickRunnable);
    }

    private final Runnable clickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (!unlimited && current >= total) {
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
                            status.setText(unlimited
                                    ? "Cliques " + current + "/∞ • " + intervalMs + " ms"
                                    : "Cliques " + current + "/" + total + " • " + intervalMs + " ms");
                            if (!unlimited && current >= total) {
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
        setMarkerTouchable(true);
        markButton.setEnabled(true);
        startButton.setEnabled(hasTarget);
        stopButton.setEnabled(false);
        status.setText("Concluído: " + current + "/" + total);
        Toast.makeText(this, "Sequência concluída", Toast.LENGTH_SHORT).show();
    }

    private void stopClicks(String text) {
        running = false;
        handler.removeCallbacks(clickRunnable);
        setMarkerTouchable(true);
        if (markButton != null) markButton.setEnabled(true);
        if (startButton != null) startButton.setEnabled(hasTarget);
        if (stopButton != null) stopButton.setEnabled(false);
        if (status != null) {
            if (unlimited) {
                status.setText(text + " em " + current + " cliques");
            } else {
                status.setText(text + " em " + current + "/" + total);
            }
        }
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
        removeMiniButton();
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
