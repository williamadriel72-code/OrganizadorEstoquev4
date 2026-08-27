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
    private Button modeButton;
    private Button directionButton;
    private Button markButton;
    private Button startButton;
    private Button stopButton;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean running;
    private boolean hasTarget;
    private boolean unlimited;
    private boolean panelMinimized;
    private boolean swipeMode;
    private String swipeDirection = "up";

    private int targetX;
    private int targetY;
    private long current;
    private int total = 100;
    private int intervalMs = 1000;
    private int swipeDurationMs = 250;

    private float dragStartRawX, dragStartRawY;
    private int dragStartX, dragStartY;

    private float markerDragStartRawX, markerDragStartRawY;
    private int markerDragStartTargetX, markerDragStartTargetY;

    private float miniDragStartRawX, miniDragStartRawY;
    private int miniDragStartX, miniDragStartY;
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
        title.setOnTouchListener((v, e) -> handlePanelDrag(e));
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        status = new TextView(this);
        status.setTextColor(Color.rgb(220, 225, 235));
        status.setGravity(Gravity.CENTER);
        status.setTextSize(12);
        panel.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        modeButton = makeButton("MODO");
        modeButton.setOnClickListener(v -> toggleMode());
        panel.addView(modeButton, buttonParams());

        directionButton = makeButton("DIREÇÃO");
        directionButton.setOnClickListener(v -> cycleDirection());
        panel.addView(directionButton, buttonParams());

        markButton = makeButton("MARCAR PONTO");
        markButton.setOnClickListener(v -> beginPointCapture());
        panel.addView(markButton, buttonParams());

        startButton = makeButton("INICIAR");
        startButton.setOnClickListener(v -> startActions());
        panel.addView(startButton, buttonParams());

        stopButton = makeButton("PARAR");
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> stopActions("Parado"));
        panel.addView(stopButton, buttonParams());

        Button minimizeButton = makeButton("MINIMIZAR");
        minimizeButton.setOnClickListener(v -> minimizePanel());
        panel.addView(minimizeButton, buttonParams());

        panelParams = new WindowManager.LayoutParams(
                dp(220),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = dp(12);
        panelParams.y = dp(100);

        windowManager.addView(panel, panelParams);
        refreshModeUi();
    }

    private void refreshModeUi() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        swipeMode = "swipe".equals(prefs.getString(MainActivity.KEY_MODE, "click"));
        swipeDirection = prefs.getString(MainActivity.KEY_DIRECTION, "up");

        if (modeButton != null) {
            modeButton.setText(swipeMode ? "MODO: DESLIZAR" : "MODO: CLIQUE");
            modeButton.setEnabled(!running);
        }
        if (directionButton != null) {
            directionButton.setVisibility(swipeMode ? View.VISIBLE : View.GONE);
            directionButton.setText("DIREÇÃO: " + directionName(swipeDirection));
            directionButton.setEnabled(!running);
        }
        if (markButton != null) {
            markButton.setVisibility(swipeMode ? View.GONE : View.VISIBLE);
            markButton.setEnabled(!running);
        }
        if (!running && status != null) {
            status.setText(swipeMode
                    ? "Deslizar " + directionName(swipeDirection) + " • toque INICIAR"
                    : (hasTarget ? "Ponto: X=" + targetX + " Y=" + targetY : "Marque um ponto"));
        }

        if (targetMarker != null && !running) {
            targetMarker.setVisibility(swipeMode ? View.GONE : View.VISIBLE);
            if (!swipeMode) setMarkerTouchable(true);
        }
    }

    private void toggleMode() {
        if (running) return;
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        boolean nextSwipe = !"swipe".equals(prefs.getString(MainActivity.KEY_MODE, "click"));
        prefs.edit().putString(MainActivity.KEY_MODE, nextSwipe ? "swipe" : "click").apply();
        refreshModeUi();
    }

    private void cycleDirection() {
        if (running) return;
        String next;
        switch (swipeDirection) {
            case "up": next = "down"; break;
            case "down": next = "left"; break;
            case "left": next = "right"; break;
            default: next = "up"; break;
        }
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .edit().putString(MainActivity.KEY_DIRECTION, next).apply();
        swipeDirection = next;
        refreshModeUi();
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
                try { windowManager.updateViewLayout(panel, panelParams); } catch (Exception ignored) {}
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
        refreshModeUi();
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
        miniButton.setOnTouchListener((v, e) -> handleMiniButtonTouch(e));

        miniParams = new WindowManager.LayoutParams(
                dp(54), dp(54),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        miniParams.gravity = Gravity.TOP | Gravity.START;
        miniParams.x = panelParams != null ? panelParams.x : dp(12);
        miniParams.y = panelParams != null ? panelParams.y : dp(100);

        try { windowManager.addView(miniButton, miniParams); }
        catch (Exception ignored) { miniButton = null; miniParams = null; }
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
                try { windowManager.updateViewLayout(miniButton, miniParams); } catch (Exception ignored) {}
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
            try { windowManager.removeView(miniButton); } catch (Exception ignored) {}
        }
        miniButton = null;
        miniParams = null;
    }

    private void beginPointCapture() {
        refreshModeUi();
        if (running || swipeMode || captureOverlay != null) return;

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
                refreshModeUi();
                Toast.makeText(this, "Ponto marcado — arraste a bolinha para ajustar",
                        Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, "Toque uma vez exatamente no ponto desejado",
                Toast.LENGTH_LONG).show();
    }

    private void endPointCapture() {
        if (captureOverlay != null) {
            try { windowManager.removeView(captureOverlay); } catch (Exception ignored) {}
            captureOverlay = null;
        }
        if (panel != null && !panelMinimized) panel.setVisibility(View.VISIBLE);
    }

    private void showTargetMarker() {
        if (windowManager == null || !hasTarget) return;
        final int size = dp(38);

        if (targetMarker == null) {
            targetMarker = new View(this);
            GradientDrawable marker = new GradientDrawable();
            marker.setShape(GradientDrawable.OVAL);
            marker.setColor(Color.argb(175, 255, 45, 45));
            marker.setStroke(dp(3), Color.WHITE);
            targetMarker.setBackground(marker);
            targetMarker.setOnTouchListener((v, e) -> handleMarkerDrag(e));

            markerParams = new WindowManager.LayoutParams(
                    size, size,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            markerParams.gravity = Gravity.TOP | Gravity.START;
            markerParams.x = targetX - size / 2;
            markerParams.y = targetY - size / 2;
            try { windowManager.addView(targetMarker, markerParams); }
            catch (Exception ignored) { targetMarker = null; markerParams = null; }
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
                targetX = clamp(markerDragStartTargetX +
                                Math.round(event.getRawX() - markerDragStartRawX),
                        0, Math.max(0, dm.widthPixels - 1));
                targetY = clamp(markerDragStartTargetY +
                                Math.round(event.getRawY() - markerDragStartRawY),
                        0, Math.max(0, dm.heightPixels - 1));
                moveMarkerToTarget();
                if (status != null) status.setText("Ponto: X=" + targetX + " Y=" + targetY);
                return true;
            default:
                return true;
        }
    }

    private void moveMarkerToTarget() {
        if (targetMarker == null || markerParams == null || windowManager == null) return;
        markerParams.x = targetX - markerParams.width / 2;
        markerParams.y = targetY - markerParams.height / 2;
        try { windowManager.updateViewLayout(targetMarker, markerParams); } catch (Exception ignored) {}
    }

    private void setMarkerTouchable(boolean touchable) {
        if (targetMarker == null || markerParams == null || windowManager == null) return;
        if (touchable) markerParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else markerParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try { windowManager.updateViewLayout(targetMarker, markerParams); } catch (Exception ignored) {}
    }

    private void removeTargetMarker() {
        if (targetMarker != null && windowManager != null) {
            try { windowManager.removeView(targetMarker); } catch (Exception ignored) {}
        }
        targetMarker = null;
        markerParams = null;
    }

    private void startActions() {
        if (running) return;

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        swipeMode = "swipe".equals(prefs.getString(MainActivity.KEY_MODE, "click"));
        swipeDirection = prefs.getString(MainActivity.KEY_DIRECTION, "up");
        total = clamp(prefs.getInt(MainActivity.KEY_QTY, 100), 1, 100);
        intervalMs = clamp(prefs.getInt(MainActivity.KEY_INTERVAL, 1000), 10, 600000);
        swipeDurationMs = clamp(prefs.getInt(MainActivity.KEY_SWIPE_DURATION, 250), 50, 5000);
        unlimited = prefs.getBoolean(MainActivity.KEY_UNLIMITED, false);

        if (!swipeMode && !hasTarget) {
            Toast.makeText(this, "No modo CLIQUE, marque um ponto primeiro.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        current = 0;
        running = true;
        setMarkerTouchable(false);
        if (targetMarker != null) {
            targetMarker.setVisibility(swipeMode ? View.GONE : View.VISIBLE);
        }

        modeButton.setEnabled(false);
        directionButton.setEnabled(false);
        markButton.setEnabled(false);
        startButton.setEnabled(false);
        stopButton.setEnabled(true);

        status.setText(runStatus());
        handler.post(actionRunnable);
    }

    private final Runnable actionRunnable = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (!unlimited && current >= total) {
                finishRun();
                return;
            }

            Path path = new Path();
            long duration;

            if (swipeMode) {
                createSwipePath(path, swipeDirection);
                duration = swipeDurationMs;
            } else {
                path.moveTo(targetX, targetY);
                duration = 1;
            }

            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                    .build();

            boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    if (!running) return;
                    current++;
                    status.setText(runStatus());
                    if (!unlimited && current >= total) finishRun();
                    else handler.postDelayed(actionRunnable, intervalMs);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    if (running) handler.postDelayed(actionRunnable, intervalMs);
                }
            }, null);

            if (!accepted && running) handler.postDelayed(this, intervalMs);
        }
    };

    private void createSwipePath(Path path, String direction) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float w = dm.widthPixels;
        float h = dm.heightPixels;
        float left = w * 0.20f;
        float right = w * 0.80f;
        float top = h * 0.25f;
        float bottom = h * 0.75f;
        float cx = w * 0.50f;
        float cy = h * 0.50f;

        switch (direction) {
            case "down":
                path.moveTo(cx, top);
                path.lineTo(cx, bottom);
                break;
            case "left":
                path.moveTo(right, cy);
                path.lineTo(left, cy);
                break;
            case "right":
                path.moveTo(left, cy);
                path.lineTo(right, cy);
                break;
            default:
                path.moveTo(cx, bottom);
                path.lineTo(cx, top);
                break;
        }
    }

    private String runStatus() {
        String noun = swipeMode ? "Passadas " : "Cliques ";
        String count = unlimited ? current + "/∞" : current + "/" + total;
        if (swipeMode) {
            return noun + count + " • " + directionName(swipeDirection) +
                    " • espera " + intervalMs + " ms";
        }
        return noun + count + " • " + intervalMs + " ms";
    }

    private void finishRun() {
        running = false;
        handler.removeCallbacks(actionRunnable);
        setMarkerTouchable(true);
        stopButton.setEnabled(false);
        startButton.setEnabled(true);
        modeButton.setEnabled(true);
        directionButton.setEnabled(true);
        markButton.setEnabled(true);
        Toast.makeText(this, "Sequência concluída", Toast.LENGTH_SHORT).show();
        refreshModeUi();
    }

    private void stopActions(String text) {
        running = false;
        handler.removeCallbacks(actionRunnable);
        setMarkerTouchable(true);

        if (stopButton != null) stopButton.setEnabled(false);
        if (startButton != null) startButton.setEnabled(true);
        if (modeButton != null) modeButton.setEnabled(true);
        if (directionButton != null) directionButton.setEnabled(true);
        if (markButton != null) markButton.setEnabled(true);

        if (status != null) status.setText(text + " em " + current +
                (swipeMode ? " passadas" : " cliques"));
        refreshModeUi();
    }

    private String directionName(String direction) {
        switch (direction) {
            case "down": return "BAIXO";
            case "left": return "ESQUERDA";
            case "right": return "DIREITA";
            default: return "CIMA";
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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp.setMargins(0, dp(2), 0, dp(2));
        return lp;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() {
        stopActions("Interrompido");
    }

    @Override
    public void onDestroy() {
        stopActions("Encerrado");
        endPointCapture();
        removeTargetMarker();
        removeMiniButton();
        if (panel != null && windowManager != null) {
            try { windowManager.removeView(panel); } catch (Exception ignored) {}
            panel = null;
        }
        super.onDestroy();
    }
}
