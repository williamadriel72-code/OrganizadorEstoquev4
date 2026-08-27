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

import java.util.ArrayList;

public class AutoClickService extends AccessibilityService {
    private static final int MAX_POINTS = 8;
    private static final String KEY_POINT_COUNT = "multi_point_count";
    private static final String KEY_POINT_NEXT_ID = "multi_point_next_id";

    private WindowManager windowManager;
    private LinearLayout panel;
    private View captureOverlay;
    private TextView miniButton;
    private WindowManager.LayoutParams panelParams;
    private WindowManager.LayoutParams miniParams;
    private TextView status;
    private TextView selectedPointText;
    private Button modeButton;
    private Button directionButton;
    private Button addPointButton;
    private Button removePointButton;
    private Button startButton;
    private Button stopButton;
    private LinearLayout pointControls;
    private LinearLayout timeControls;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<ClickPoint> points = new ArrayList<>();

    private boolean running;
    private boolean unlimited;
    private boolean panelMinimized;
    private boolean swipeMode;
    private boolean dispatching;
    private String swipeDirection = "up";

    private long current;
    private int total = 100;
    private int intervalMs = 1000;
    private int swipeDurationMs = 250;
    private int nextPointId = 1;
    private int selectedPointId = -1;

    private float dragStartRawX, dragStartRawY;
    private int dragStartX, dragStartY;
    private float miniDragStartRawX, miniDragStartRawY;
    private int miniDragStartX, miniDragStartY;
    private boolean miniMoved;

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
        boolean moved;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        loadPoints();
        showPanel();
        showAllPointMarkers();
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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        modeButton = makeButton("MODO");
        modeButton.setOnClickListener(v -> toggleMode());
        panel.addView(modeButton, buttonParams());

        directionButton = makeButton("DIREÇÃO");
        directionButton.setOnClickListener(v -> cycleDirection());
        panel.addView(directionButton, buttonParams());

        pointControls = new LinearLayout(this);
        pointControls.setOrientation(LinearLayout.VERTICAL);

        selectedPointText = new TextView(this);
        selectedPointText.setTextColor(Color.WHITE);
        selectedPointText.setTextSize(13);
        selectedPointText.setGravity(Gravity.CENTER);
        selectedPointText.setPadding(dp(3), dp(5), dp(3), dp(5));
        pointControls.addView(selectedPointText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        LinearLayout addRemoveRow = new LinearLayout(this);
        addRemoveRow.setOrientation(LinearLayout.HORIZONTAL);

        addPointButton = makeButton("+ PONTO");
        addPointButton.setOnClickListener(v -> beginPointCapture());
        addRemoveRow.addView(addPointButton, rowButtonParams());

        removePointButton = makeButton("REMOVER");
        removePointButton.setOnClickListener(v -> removeSelectedPoint());
        addRemoveRow.addView(removePointButton, rowButtonParams());
        pointControls.addView(addRemoveRow);

        timeControls = new LinearLayout(this);
        timeControls.setOrientation(LinearLayout.HORIZONTAL);
        timeControls.setGravity(Gravity.CENTER);

        Button minus100 = smallButton("−100");
        minus100.setOnClickListener(v -> adjustSelectedInterval(-100));
        timeControls.addView(minus100, smallButtonParams());

        Button minus10 = smallButton("−10");
        minus10.setOnClickListener(v -> adjustSelectedInterval(-10));
        timeControls.addView(minus10, smallButtonParams());

        Button plus10 = smallButton("+10");
        plus10.setOnClickListener(v -> adjustSelectedInterval(10));
        timeControls.addView(plus10, smallButtonParams());

        Button plus100 = smallButton("+100");
        plus100.setOnClickListener(v -> adjustSelectedInterval(100));
        timeControls.addView(plus100, smallButtonParams());

        pointControls.addView(timeControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        panel.addView(pointControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

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
                dp(248), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = dp(12);
        panelParams.y = dp(90);

        windowManager.addView(panel, panelParams);
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

    private void refreshModeUi() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        swipeMode = "swipe".equals(prefs.getString(MainActivity.KEY_MODE, "click"));
        swipeDirection = prefs.getString(MainActivity.KEY_DIRECTION, "up");

        if (modeButton != null) {
            modeButton.setText(swipeMode ? "MODO: DESLIZAR" : "MODO: MULTICLIQUE");
            modeButton.setEnabled(!running);
        }
        if (directionButton != null) {
            directionButton.setVisibility(swipeMode ? View.VISIBLE : View.GONE);
            directionButton.setText("DIREÇÃO: " + directionName(swipeDirection));
            directionButton.setEnabled(!running);
        }
        if (pointControls != null) pointControls.setVisibility(swipeMode ? View.GONE : View.VISIBLE);

        for (ClickPoint p : points) {
            if (p.marker != null) {
                p.marker.setVisibility(swipeMode ? View.GONE : View.VISIBLE);
                if (!swipeMode && !running) setPointTouchable(p, true);
            }
        }

        refreshPointUi();

        if (!running && status != null) {
            if (swipeMode) {
                status.setText("Deslizar " + directionName(swipeDirection) + " • toque INICIAR");
            } else {
                status.setText(points.isEmpty()
                        ? "Adicione até " + MAX_POINTS + " pontos"
                        : points.size() + " ponto(s) • cada um com seu tempo");
            }
        }

        if (startButton != null) startButton.setEnabled(!running && (swipeMode || !points.isEmpty()));
    }

    private void refreshPointUi() {
        ClickPoint selected = getSelectedPoint();
        if (selected == null && !points.isEmpty()) {
            selectedPointId = points.get(0).id;
            selected = points.get(0);
        }

        if (selectedPointText != null) {
            selectedPointText.setText(selected == null
                    ? "Nenhum ponto selecionado"
                    : "P" + selected.id + "  •  " + selected.intervalMs + " ms");
        }
        if (removePointButton != null) removePointButton.setEnabled(!running && selected != null);
        if (addPointButton != null) addPointButton.setEnabled(!running && points.size() < MAX_POINTS);
        if (timeControls != null) {
            for (int i = 0; i < timeControls.getChildCount(); i++) {
                timeControls.getChildAt(i).setEnabled(!running && selected != null);
            }
        }
        refreshMarkerStyles();
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

    private void beginPointCapture() {
        if (running || swipeMode || captureOverlay != null || points.size() >= MAX_POINTS) return;

        if (panel != null) panel.setVisibility(View.GONE);
        setAllMarkersVisible(false);

        captureOverlay = new View(this);
        captureOverlay.setBackgroundColor(Color.argb(1, 0, 0, 0));
        captureOverlay.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
                ClickPoint p = new ClickPoint();
                p.id = nextPointId++;
                p.x = Math.round(event.getRawX());
                p.y = Math.round(event.getRawY());
                p.intervalMs = clamp(prefs.getInt(MainActivity.KEY_INTERVAL, 1000), 10, 600000);
                points.add(p);
                selectedPointId = p.id;
                savePoints();

                endPointCapture();
                showPointMarker(p);
                setAllMarkersVisible(true);
                refreshModeUi();
                Toast.makeText(this, "P" + p.id + " adicionado — arraste para ajustar", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, "Toque onde deseja adicionar o novo ponto", Toast.LENGTH_LONG).show();
    }

    private void endPointCapture() {
        if (captureOverlay != null) {
            try { windowManager.removeView(captureOverlay); } catch (Exception ignored) {}
            captureOverlay = null;
        }
        if (panel != null && !panelMinimized) panel.setVisibility(View.VISIBLE);
    }

    private void showAllPointMarkers() {
        for (ClickPoint p : points) showPointMarker(p);
        setAllMarkersVisible(!swipeMode);
        refreshMarkerStyles();
    }

    private void showPointMarker(ClickPoint p) {
        if (windowManager == null || p.marker != null) return;
        final int size = dp(42);

        TextView marker = new TextView(this);
        marker.setText(String.valueOf(p.id));
        marker.setTextColor(Color.WHITE);
        marker.setTextSize(14);
        marker.setGravity(Gravity.CENTER);
        marker.setTypeface(null, android.graphics.Typeface.BOLD);
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
        if (running || p.params == null) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                selectedPointId = p.id;
                p.dragStartRawX = event.getRawX();
                p.dragStartRawY = event.getRawY();
                p.dragStartX = p.x;
                p.dragStartY = p.y;
                p.moved = false;
                refreshPointUi();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - p.dragStartRawX;
                float dy = event.getRawY() - p.dragStartRawY;
                if (Math.abs(dx) > dp(3) || Math.abs(dy) > dp(3)) p.moved = true;
                DisplayMetrics dm = getResources().getDisplayMetrics();
                p.x = clamp(p.dragStartX + Math.round(dx), 0, Math.max(0, dm.widthPixels - 1));
                p.y = clamp(p.dragStartY + Math.round(dy), 0, Math.max(0, dm.heightPixels - 1));
                movePointMarker(p);
                return true;
            case MotionEvent.ACTION_UP:
                savePoints();
                refreshPointUi();
                return true;
            default:
                return true;
        }
    }

    private void movePointMarker(ClickPoint p) {
        if (p.params == null) return;
        p.params.x = p.x - p.params.width / 2;
        p.params.y = p.y - p.params.height / 2;
        if (p.marker != null && windowManager != null) {
            try { windowManager.updateViewLayout(p.marker, p.params); } catch (Exception ignored) {}
        }
    }

    private void setPointTouchable(ClickPoint p, boolean touchable) {
        if (p.marker == null || p.params == null || windowManager == null) return;
        if (touchable) p.params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else p.params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try { windowManager.updateViewLayout(p.marker, p.params); } catch (Exception ignored) {}
    }

    private void setAllPointsTouchable(boolean touchable) {
        for (ClickPoint p : points) setPointTouchable(p, touchable);
    }

    private void setAllMarkersVisible(boolean visible) {
        for (ClickPoint p : points) if (p.marker != null) p.marker.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void refreshMarkerStyles() {
        for (ClickPoint p : points) {
            if (p.marker == null) continue;
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            if (p.id == selectedPointId) {
                bg.setColor(Color.argb(220, 30, 120, 255));
                bg.setStroke(dp(3), Color.YELLOW);
            } else {
                bg.setColor(Color.argb(200, 235, 55, 55));
                bg.setStroke(dp(2), Color.WHITE);
            }
            p.marker.setBackground(bg);
        }
    }

    private ClickPoint getSelectedPoint() {
        for (ClickPoint p : points) if (p.id == selectedPointId) return p;
        return null;
    }

    private void adjustSelectedInterval(int delta) {
        if (running) return;
        ClickPoint p = getSelectedPoint();
        if (p == null) return;
        p.intervalMs = clamp(p.intervalMs + delta, 10, 600000);
        savePoints();
        refreshPointUi();
    }

    private void removeSelectedPoint() {
        if (running) return;
        ClickPoint selected = getSelectedPoint();
        if (selected == null) return;
        removePointMarker(selected);
        points.remove(selected);
        selectedPointId = points.isEmpty() ? -1 : points.get(0).id;
        savePoints();
        refreshModeUi();
    }

    private void removePointMarker(ClickPoint p) {
        if (p.marker != null && windowManager != null) {
            try { windowManager.removeView(p.marker); } catch (Exception ignored) {}
        }
        p.marker = null;
        p.params = null;
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

        if (!swipeMode && points.isEmpty()) {
            Toast.makeText(this, "Adicione pelo menos um ponto", Toast.LENGTH_SHORT).show();
            return;
        }

        running = true;
        current = 0;
        dispatching = false;
        setAllPointsTouchable(false);
        setRunButtons(true);

        if (swipeMode) {
            status.setText("Deslizando 0" + (unlimited ? "/∞" : "/" + total));
            handler.post(swipeRunnable);
        } else {
            long now = SystemClock.uptimeMillis();
            for (ClickPoint p : points) {
                p.count = 0;
                p.nextAt = now;
            }
            status.setText("Multiclique iniciado • " + points.size() + " pontos");
            handler.post(multiClickRunnable);
        }
    }

    private final Runnable multiClickRunnable = new Runnable() {
        @Override public void run() {
            if (!running || swipeMode || dispatching) return;

            long now = SystemClock.uptimeMillis();
            ClickPoint due = null;
            long earliest = Long.MAX_VALUE;
            boolean unfinished = false;

            for (ClickPoint p : points) {
                if (!unlimited && p.count >= total) continue;
                unfinished = true;
                if (p.nextAt <= now) {
                    if (due == null || p.nextAt < due.nextAt) due = p;
                } else if (p.nextAt < earliest) {
                    earliest = p.nextAt;
                }
            }

            if (!unfinished) {
                finishRun();
                return;
            }

            if (due != null) {
                dispatchPointClick(due);
                return;
            }

            long wait = earliest == Long.MAX_VALUE ? 10 : Math.max(1, earliest - now);
            handler.postDelayed(this, wait);
        }
    };

    private void dispatchPointClick(ClickPoint p) {
        if (!running) return;
        dispatching = true;

        Path path = new Path();
        path.moveTo(p.x, p.y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 1))
                .build();

        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                dispatching = false;
                if (!running) return;
                p.count++;
                p.nextAt = SystemClock.uptimeMillis() + p.intervalMs;
                updateMultiStatus();
                handler.post(multiClickRunnable);
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                dispatching = false;
                if (!running) return;
                p.nextAt = SystemClock.uptimeMillis() + 10;
                handler.post(multiClickRunnable);
            }
        }, null);

        if (!accepted) {
            dispatching = false;
            p.nextAt = SystemClock.uptimeMillis() + 10;
            handler.postDelayed(multiClickRunnable, 10);
        }
    }

    private void updateMultiStatus() {
        long sum = 0;
        for (ClickPoint p : points) sum += p.count;
        status.setText(unlimited
                ? "Cliques totais: " + sum + " • " + points.size() + " pontos"
                : "Cliques totais: " + sum + "/" + ((long) total * points.size()));
    }

    private final Runnable swipeRunnable = new Runnable() {
        @Override public void run() {
            if (!running || !swipeMode || dispatching) return;
            if (!unlimited && current >= total) {
                finishRun();
                return;
            }

            dispatching = true;
            DisplayMetrics dm = getResources().getDisplayMetrics();
            float cx = dm.widthPixels / 2f;
            float cy = dm.heightPixels / 2f;
            float distanceX = dm.widthPixels * 0.34f;
            float distanceY = dm.heightPixels * 0.28f;
            float endX = cx;
            float endY = cy;

            switch (swipeDirection) {
                case "down": endY = cy + distanceY; break;
                case "left": endX = cx - distanceX; break;
                case "right": endX = cx + distanceX; break;
                default: endY = cy - distanceY; break;
            }

            Path path = new Path();
            path.moveTo(cx, cy);
            path.lineTo(endX, endY);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, swipeDurationMs))
                    .build();

            boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    dispatching = false;
                    if (!running) return;
                    current++;
                    status.setText("Deslizes " + current + (unlimited ? "/∞" : "/" + total));
                    if (!unlimited && current >= total) finishRun();
                    else handler.postDelayed(swipeRunnable, intervalMs);
                }

                @Override public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    dispatching = false;
                    if (running) handler.postDelayed(swipeRunnable, intervalMs);
                }
            }, null);

            if (!accepted) {
                dispatching = false;
                handler.postDelayed(this, intervalMs);
            }
        }
    };

    private void setRunButtons(boolean isRunning) {
        if (modeButton != null) modeButton.setEnabled(!isRunning);
        if (directionButton != null) directionButton.setEnabled(!isRunning);
        if (addPointButton != null) addPointButton.setEnabled(!isRunning && points.size() < MAX_POINTS);
        if (removePointButton != null) removePointButton.setEnabled(!isRunning && getSelectedPoint() != null);
        if (timeControls != null) {
            for (int i = 0; i < timeControls.getChildCount(); i++)
                timeControls.getChildAt(i).setEnabled(!isRunning && getSelectedPoint() != null);
        }
        if (startButton != null) startButton.setEnabled(!isRunning && (swipeMode || !points.isEmpty()));
        if (stopButton != null) stopButton.setEnabled(isRunning);
    }

    private void finishRun() {
        running = false;
        dispatching = false;
        handler.removeCallbacks(multiClickRunnable);
        handler.removeCallbacks(swipeRunnable);
        setAllPointsTouchable(true);
        setRunButtons(false);
        if (status != null) status.setText("Concluído");
        refreshModeUi();
        Toast.makeText(this, "Sequência concluída", Toast.LENGTH_SHORT).show();
    }

    private void stopActions(String text) {
        running = false;
        dispatching = false;
        handler.removeCallbacks(multiClickRunnable);
        handler.removeCallbacks(swipeRunnable);
        setAllPointsTouchable(true);
        setRunButtons(false);
        if (status != null) status.setText(text);
        refreshModeUi();
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
                PixelFormat.TRANSLUCENT);
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

    private void savePoints() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        SharedPreferences.Editor e = prefs.edit();
        e.putInt(KEY_POINT_COUNT, points.size());
        e.putInt(KEY_POINT_NEXT_ID, nextPointId);
        for (int i = 0; i < MAX_POINTS; i++) {
            e.remove("mp_" + i + "_id");
            e.remove("mp_" + i + "_x");
            e.remove("mp_" + i + "_y");
            e.remove("mp_" + i + "_interval");
        }
        for (int i = 0; i < points.size(); i++) {
            ClickPoint p = points.get(i);
            e.putInt("mp_" + i + "_id", p.id);
            e.putInt("mp_" + i + "_x", p.x);
            e.putInt("mp_" + i + "_y", p.y);
            e.putInt("mp_" + i + "_interval", p.intervalMs);
        }
        e.apply();
    }

    private void loadPoints() {
        points.clear();
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        int count = clamp(prefs.getInt(KEY_POINT_COUNT, 0), 0, MAX_POINTS);
        nextPointId = Math.max(1, prefs.getInt(KEY_POINT_NEXT_ID, 1));
        DisplayMetrics dm = getResources().getDisplayMetrics();
        for (int i = 0; i < count; i++) {
            ClickPoint p = new ClickPoint();
            p.id = prefs.getInt("mp_" + i + "_id", i + 1);
            p.x = clamp(prefs.getInt("mp_" + i + "_x", dm.widthPixels / 2), 0, Math.max(0, dm.widthPixels - 1));
            p.y = clamp(prefs.getInt("mp_" + i + "_y", dm.heightPixels / 2), 0, Math.max(0, dm.heightPixels - 1));
            p.intervalMs = clamp(prefs.getInt("mp_" + i + "_interval", 1000), 10, 600000);
            points.add(p);
            nextPointId = Math.max(nextPointId, p.id + 1);
        }
        selectedPointId = points.isEmpty() ? -1 : points.get(0).id;
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

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(10);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp.setMargins(0, dp(2), 0, dp(2));
        return lp;
    }

    private LinearLayout.LayoutParams rowButtonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lp.setMargins(dp(1), dp(1), dp(1), dp(1));
        return lp;
    }

    private LinearLayout.LayoutParams smallButtonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lp.setMargins(dp(1), dp(1), dp(1), dp(1));
        return lp;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() {
        stopActions("Interrompido");
    }

    @Override public void onDestroy() {
        stopActions("Encerrado");
        endPointCapture();
        removeMiniButton();
        for (ClickPoint p : new ArrayList<>(points)) removePointMarker(p);
        if (panel != null && windowManager != null) {
            try { windowManager.removeView(panel); } catch (Exception ignored) {}
            panel = null;
        }
        super.onDestroy();
    }
}
