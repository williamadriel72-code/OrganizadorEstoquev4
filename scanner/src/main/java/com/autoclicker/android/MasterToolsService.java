package com.autoclicker.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MasterToolsService extends AccessibilityService {
    private static final String PREFS = "master_tools_v14";
    private static final int MODE_CLICK = 0;
    private static final int MODE_SWIPE = 1;
    private static final int MODE_DIAGONAL = 2;
    private static final int MODE_DUAL = 3;
    private static final int MODE_NINJA = 4;
    private static final int MAX_POINTS = 8;

    private WindowManager wm;
    private LinearLayout root;
    private ScrollView bodyScroll;
    private LinearLayout body;
    private WindowManager.LayoutParams panelLp;
    private TextView mini;
    private WindowManager.LayoutParams miniLp;
    private View captureOverlay;

    private TextView status;
    private TextView pointInfo;
    private TextView quantityInfo;
    private TextView durationInfo;
    private TextView swipeRepeatInfo;
    private Button modeButton;
    private Button startButton;
    private Button stopButton;
    private Button removePointButton;
    private Button unlimitedButton;
    private Button swipeUnlimitedButton;
    private LinearLayout clickControls;
    private LinearLayout swipeControls;
    private LinearLayout durationControls;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<ClickPoint> points = new ArrayList<>();

    private int mode = MODE_CLICK;
    private int durationMs = 350;
    private int clickLimit = 100;
    private boolean clickUnlimited;
    private int swipeLimit = 100;
    private boolean swipeUnlimited;
    private boolean running;
    private boolean gestureBusy;
    private long cycleCount;
    private boolean diagonalForward = true;
    private double ninjaPhase;

    private int selectedPointId = -1;
    private int nextPointId = 1;

    private int swipeStartX;
    private int swipeStartY;
    private int swipeEndX;
    private int swipeEndY;
    private TextView swipeStartMarker;
    private TextView swipeEndMarker;
    private WindowManager.LayoutParams swipeStartLp;
    private WindowManager.LayoutParams swipeEndLp;
    private SwipeLineView swipeLine;
    private WindowManager.LayoutParams swipeLineLp;

    private float dragDownX;
    private float dragDownY;
    private int dragStartPanelX;
    private int dragStartPanelY;
    private int lastPanelX = Integer.MIN_VALUE;
    private int lastPanelY = Integer.MIN_VALUE;

    private float markerDownX;
    private float markerDownY;
    private int markerStartX;
    private int markerStartY;

    private float miniDownX;
    private float miniDownY;
    private int miniStartX;
    private int miniStartY;
    private boolean miniMoved;

    private int screenW;
    private int screenH;

    private static class ClickPoint {
        int id;
        int x;
        int y;
        int intervalMs = 100;
        long count;
        long nextAt;
        TextView marker;
        WindowManager.LayoutParams lp;
        float downX;
        float downY;
        int startX;
        int startY;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        updateScreenSize();
        loadSettings();
        loadPoints();
        ensureSwipeDefaults();
        showPanel();
        showAllClickMarkers();
        showSwipeMarkers();
        refreshUi();
    }

    // ---------- PAINEL RESPONSIVO ----------

    private void showPanel() {
        if (root != null || wm == null) return;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(246, 12, 20, 34));
        bg.setCornerRadius(dp(14));
        root.setBackground(bg);

        TextView header = new TextView(this);
        header.setText("MASTER TOOLS  •  ARRASTE");
        header.setTextColor(Color.WHITE);
        header.setTextSize(12);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dp(5), dp(2), dp(5), dp(2));
        header.setOnTouchListener((v, e) -> dragPanel(e));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        bodyScroll = new ScrollView(this);
        bodyScroll.setFillViewport(false);
        bodyScroll.setVerticalScrollBarEnabled(true);
        bodyScroll.setScrollbarFadingEnabled(false);
        bodyScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(6), dp(2), dp(6), dp(8));
        bodyScroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(bodyScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView section = label("AUTOMAÇÃO DE TOQUES / DESLIZES", 10, Color.rgb(70, 185, 255));
        section.setTypeface(null, android.graphics.Typeface.BOLD);
        body.addView(section, full(dp(30)));

        status = label("Pronto", 10, Color.WHITE);
        body.addView(status, full(dp(32)));

        modeButton = button("MODO: AUTOCLICKER");
        modeButton.setOnClickListener(v -> setMode((mode + 1) % 5));
        body.addView(modeButton, full(dp(40)));

        LinearLayout row1 = row();
        row1.addView(modeSmall("AUTOCLICK", MODE_CLICK), cell(dp(36)));
        row1.addView(modeSmall("DESLIZE", MODE_SWIPE), cell(dp(36)));
        row1.addView(modeSmall("DIAGONAL", MODE_DIAGONAL), cell(dp(36)));
        body.addView(row1, full(dp(38)));

        LinearLayout row2 = row();
        row2.addView(modeSmall("2 HORIZONTAIS", MODE_DUAL), cell(dp(36)));
        row2.addView(modeSmall("NINJA", MODE_NINJA), cell(dp(36)));
        body.addView(row2, full(dp(38)));

        buildClickControls();
        buildSwipeControls();
        buildDurationControls();

        startButton = button("INICIAR AUTOMAÇÃO");
        startButton.setOnClickListener(v -> startAutomation());
        body.addView(startButton, full(dp(42)));

        stopButton = button("PARAR");
        stopButton.setOnClickListener(v -> stopAutomation("Parado"));
        body.addView(stopButton, full(dp(42)));

        Button minimize = button("MINIMIZAR");
        minimize.setOnClickListener(v -> minimizePanel());
        body.addView(minimize, full(dp(40)));

        panelLp = new WindowManager.LayoutParams(
                panelWidth(), panelHeight(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.START;
        panelLp.x = dp(8);
        panelLp.y = dp(28);
        clampPanelPosition();
        wm.addView(root, panelLp);
    }

    private void buildClickControls() {
        clickControls = new LinearLayout(this);
        clickControls.setOrientation(LinearLayout.VERTICAL);

        pointInfo = label("Nenhum ponto marcado", 10, Color.WHITE);
        clickControls.addView(pointInfo, full(dp(30)));

        LinearLayout r = row();
        Button add = button("+ PONTO");
        add.setOnClickListener(v -> beginPointCapture());
        r.addView(add, cell(dp(38)));
        removePointButton = button("REMOVER");
        removePointButton.setOnClickListener(v -> removeSelectedPoint());
        r.addView(removePointButton, cell(dp(38)));
        clickControls.addView(r, full(dp(40)));

        LinearLayout interval = row();
        interval.addView(intervalButton("−100", -100), cell(dp(34)));
        interval.addView(intervalButton("−10", -10), cell(dp(34)));
        interval.addView(intervalButton("+10", 10), cell(dp(34)));
        interval.addView(intervalButton("+100", 100), cell(dp(34)));
        clickControls.addView(interval, full(dp(36)));

        quantityInfo = label("Quantidade por ponto: 100", 10, Color.WHITE);
        clickControls.addView(quantityInfo, full(dp(28)));

        LinearLayout qty = row();
        Button qm = small("−10");
        qm.setOnClickListener(v -> adjustClickLimit(-10));
        qty.addView(qm, cell(dp(34)));
        Button qp = small("+10");
        qp.setOnClickListener(v -> adjustClickLimit(10));
        qty.addView(qp, cell(dp(34)));
        unlimitedButton = small("ILIMITADO");
        unlimitedButton.setOnClickListener(v -> {
            if (running) return;
            clickUnlimited = !clickUnlimited;
            saveSettings();
            refreshUi();
        });
        qty.addView(unlimitedButton, cell(dp(34)));
        clickControls.addView(qty, full(dp(36)));

        body.addView(clickControls);
    }

    private void buildSwipeControls() {
        swipeControls = new LinearLayout(this);
        swipeControls.setOrientation(LinearLayout.VERTICAL);

        TextView hint = label("1 = INÍCIO   •   2 = FIM\nArraste as bolinhas na tela", 10, Color.WHITE);
        swipeControls.addView(hint, full(dp(40)));

        LinearLayout tools = row();
        Button center = small("CENTRALIZAR");
        center.setOnClickListener(v -> resetSwipePoints());
        tools.addView(center, cell(dp(34)));
        Button invert = small("INVERTER");
        invert.setOnClickListener(v -> swapSwipePoints());
        tools.addView(invert, cell(dp(34)));
        swipeControls.addView(tools, full(dp(36)));

        swipeRepeatInfo = label("Repetições: 100", 10, Color.WHITE);
        swipeControls.addView(swipeRepeatInfo, full(dp(28)));

        LinearLayout rep = row();
        Button sm = small("−10");
        sm.setOnClickListener(v -> adjustSwipeLimit(-10));
        rep.addView(sm, cell(dp(34)));
        Button sp = small("+10");
        sp.setOnClickListener(v -> adjustSwipeLimit(10));
        rep.addView(sp, cell(dp(34)));
        swipeUnlimitedButton = small("ILIMITADO");
        swipeUnlimitedButton.setOnClickListener(v -> {
            if (running) return;
            swipeUnlimited = !swipeUnlimited;
            saveSettings();
            refreshUi();
        });
        rep.addView(swipeUnlimitedButton, cell(dp(34)));
        swipeControls.addView(rep, full(dp(36)));

        body.addView(swipeControls);
    }

    private void buildDurationControls() {
        durationControls = new LinearLayout(this);
        durationControls.setOrientation(LinearLayout.VERTICAL);
        durationInfo = label("Duração: 350 ms", 10, Color.WHITE);
        durationControls.addView(durationInfo, full(dp(28)));

        LinearLayout r = row();
        Button minus = small("−50 ms");
        minus.setOnClickListener(v -> adjustDuration(-50));
        r.addView(minus, cell(dp(34)));
        Button plus = small("+50 ms");
        plus.setOnClickListener(v -> adjustDuration(50));
        r.addView(plus, cell(dp(34)));
        durationControls.addView(r, full(dp(36)));
        body.addView(durationControls);
    }

    private boolean dragPanel(MotionEvent e) {
        if (panelLp == null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragDownX = e.getRawX();
                dragDownY = e.getRawY();
                dragStartPanelX = panelLp.x;
                dragStartPanelY = panelLp.y;
                lastPanelX = panelLp.x;
                lastPanelY = panelLp.y;
                return true;
            case MotionEvent.ACTION_MOVE:
                int nx = dragStartPanelX + Math.round(e.getRawX() - dragDownX);
                int ny = dragStartPanelY + Math.round(e.getRawY() - dragDownY);
                panelLp.x = clamp(nx, 0, Math.max(0, screenW - panelLp.width));
                panelLp.y = clamp(ny, 0, Math.max(0, screenH - panelLp.height));
                if (Math.abs(panelLp.x - lastPanelX) >= 1 || Math.abs(panelLp.y - lastPanelY) >= 1) {
                    lastPanelX = panelLp.x;
                    lastPanelY = panelLp.y;
                    try { wm.updateViewLayout(root, panelLp); } catch (Exception ignored) { }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }

    private int panelWidth() {
        return Math.min(dp(250), Math.max(dp(205), screenW - dp(20)));
    }

    private int panelHeight() {
        int available = Math.max(dp(210), screenH - dp(20));
        return Math.min(dp(520), available);
    }

    private void resizePanelForScreen() {
        if (panelLp == null || root == null) return;
        panelLp.width = panelWidth();
        panelLp.height = panelHeight();
        clampPanelPosition();
        try { wm.updateViewLayout(root, panelLp); } catch (Exception ignored) { }
        if (bodyScroll != null) bodyScroll.post(() -> bodyScroll.requestLayout());
    }

    private void clampPanelPosition() {
        if (panelLp == null) return;
        panelLp.x = clamp(panelLp.x, 0, Math.max(0, screenW - panelLp.width));
        panelLp.y = clamp(panelLp.y, 0, Math.max(0, screenH - panelLp.height));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int oldW = Math.max(1, screenW);
        int oldH = Math.max(1, screenH);
        updateScreenSize();

        float sx = screenW / (float) oldW;
        float sy = screenH / (float) oldH;
        for (ClickPoint p : points) {
            p.x = clamp(Math.round(p.x * sx), 0, screenW - 1);
            p.y = clamp(Math.round(p.y * sy), 0, screenH - 1);
            movePointMarker(p);
        }
        swipeStartX = clamp(Math.round(swipeStartX * sx), 0, screenW - 1);
        swipeStartY = clamp(Math.round(swipeStartY * sy), 0, screenH - 1);
        swipeEndX = clamp(Math.round(swipeEndX * sx), 0, screenW - 1);
        swipeEndY = clamp(Math.round(swipeEndY * sy), 0, screenH - 1);
        moveSwipeMarkers();
        resizeLineOverlay();
        resizePanelForScreen();
        saveSettings();
        savePoints();
    }

    private void minimizePanel() {
        if (root == null || mini != null) return;
        root.setVisibility(View.GONE);
        mini = new TextView(this);
        mini.setText("≡");
        mini.setTextColor(Color.WHITE);
        mini.setTextSize(18);
        mini.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(245, 25, 105, 225));
        bg.setStroke(dp(2), Color.WHITE);
        mini.setBackground(bg);
        mini.setOnTouchListener((v, e) -> handleMini(e));

        miniLp = new WindowManager.LayoutParams(
                dp(46), dp(46),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        miniLp.gravity = Gravity.TOP | Gravity.START;
        miniLp.x = panelLp.x;
        miniLp.y = panelLp.y;
        wm.addView(mini, miniLp);
    }

    private boolean handleMini(MotionEvent e) {
        if (miniLp == null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                miniDownX = e.getRawX();
                miniDownY = e.getRawY();
                miniStartX = miniLp.x;
                miniStartY = miniLp.y;
                miniMoved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - miniDownX;
                float dy = e.getRawY() - miniDownY;
                if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) miniMoved = true;
                if (miniMoved) {
                    miniLp.x = clamp(miniStartX + Math.round(dx), 0, Math.max(0, screenW - miniLp.width));
                    miniLp.y = clamp(miniStartY + Math.round(dy), 0, Math.max(0, screenH - miniLp.height));
                    try { wm.updateViewLayout(mini, miniLp); } catch (Exception ignored) { }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!miniMoved) restorePanel();
                return true;
            default:
                return true;
        }
    }

    private void restorePanel() {
        if (mini != null) {
            try { wm.removeView(mini); } catch (Exception ignored) { }
            mini = null;
            miniLp = null;
        }
        if (root != null) {
            resizePanelForScreen();
            root.setVisibility(View.VISIBLE);
        }
    }

    // ---------- MODOS ----------

    private void setMode(int newMode) {
        if (running) stopAutomation("Modo alterado");
        mode = clamp(newMode, MODE_CLICK, MODE_NINJA);
        saveSettings();
        refreshUi();
    }

    private void refreshUi() {
        if (modeButton != null) modeButton.setText("MODO: " + modeName());
        if (clickControls != null) clickControls.setVisibility(mode == MODE_CLICK ? View.VISIBLE : View.GONE);
        if (swipeControls != null) swipeControls.setVisibility(mode == MODE_SWIPE ? View.VISIBLE : View.GONE);
        if (durationControls != null) durationControls.setVisibility(mode == MODE_CLICK ? View.GONE : View.VISIBLE);

        ClickPoint selected = selectedPoint();
        if (selected == null && !points.isEmpty()) {
            selectedPointId = points.get(0).id;
            selected = points.get(0);
        }
        if (pointInfo != null) pointInfo.setText(selected == null ? "Nenhum ponto marcado" : "P" + selected.id + " • intervalo " + selected.intervalMs + " ms");
        if (quantityInfo != null) quantityInfo.setText(clickUnlimited ? "Quantidade: ILIMITADO" : "Quantidade por ponto: " + clickLimit);
        if (unlimitedButton != null) unlimitedButton.setText(clickUnlimited ? "LIMITADO" : "ILIMITADO");
        if (removePointButton != null) removePointButton.setEnabled(!running && selected != null);
        if (durationInfo != null) durationInfo.setText("Duração: " + durationMs + " ms");
        if (swipeRepeatInfo != null) swipeRepeatInfo.setText(swipeUnlimited ? "Repetições: ILIMITADO" : "Repetições: " + swipeLimit);
        if (swipeUnlimitedButton != null) swipeUnlimitedButton.setText(swipeUnlimited ? "LIMITADO" : "ILIMITADO");

        for (ClickPoint p : points) {
            if (p.marker != null) {
                p.marker.setVisibility(mode == MODE_CLICK ? View.VISIBLE : View.GONE);
                setPointTouchable(p, mode == MODE_CLICK && !running);
            }
        }
        setSwipeVisible(mode == MODE_SWIPE);
        setSwipeTouchable(mode == MODE_SWIPE && !running);

        if (status != null && !running) {
            if (mode == MODE_CLICK) status.setText(points.isEmpty() ? "Adicione um ponto" : points.size() + " ponto(s) pronto(s)");
            else if (mode == MODE_SWIPE) status.setText("Arraste 1 e 2 para escolher o deslize");
            else status.setText(modeName() + " pronto");
        }
        if (startButton != null) startButton.setEnabled(!running && (mode != MODE_CLICK || !points.isEmpty()));
        if (stopButton != null) stopButton.setEnabled(running);
    }

    private String modeName() {
        switch (mode) {
            case MODE_SWIPE: return "DESLIZE";
            case MODE_DIAGONAL: return "DIAGONAL";
            case MODE_DUAL: return "2 HORIZONTAIS";
            case MODE_NINJA: return "NINJA";
            default: return "AUTOCLICKER";
        }
    }

    private void adjustDuration(int delta) {
        if (running) return;
        durationMs = clamp(durationMs + delta, 80, 3000);
        saveSettings();
        refreshUi();
    }

    private void adjustClickLimit(int delta) {
        if (running) return;
        clickLimit = clamp(clickLimit + delta, 1, 10000);
        saveSettings();
        refreshUi();
    }

    private void adjustSwipeLimit(int delta) {
        if (running) return;
        swipeLimit = clamp(swipeLimit + delta, 1, 10000);
        saveSettings();
        refreshUi();
    }

    // ---------- AUTOCLICK ----------

    private void beginPointCapture() {
        if (running || mode != MODE_CLICK || captureOverlay != null || points.size() >= MAX_POINTS) return;
        root.setVisibility(View.GONE);
        setClickMarkersVisible(false);
        captureOverlay = new View(this);
        captureOverlay.setBackgroundColor(Color.argb(1, 0, 0, 0));
        captureOverlay.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                ClickPoint p = new ClickPoint();
                p.id = nextPointId++;
                p.x = clamp(Math.round(e.getRawX()), 0, screenW - 1);
                p.y = clamp(Math.round(e.getRawY()), 0, screenH - 1);
                points.add(p);
                selectedPointId = p.id;
                endCapture();
                showPointMarker(p);
                savePoints();
                refreshUi();
                Toast.makeText(this, "P" + p.id + " marcado. Arraste a bolinha para ajustar.", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        wm.addView(captureOverlay, lp);
        Toast.makeText(this, "Toque exatamente onde quer marcar", Toast.LENGTH_LONG).show();
    }

    private void endCapture() {
        if (captureOverlay != null) {
            try { wm.removeView(captureOverlay); } catch (Exception ignored) { }
            captureOverlay = null;
        }
        if (root != null) root.setVisibility(View.VISIBLE);
        setClickMarkersVisible(mode == MODE_CLICK);
    }

    private void showAllClickMarkers() {
        for (ClickPoint p : points) showPointMarker(p);
    }

    private void showPointMarker(ClickPoint p) {
        if (p.marker != null || wm == null) return;
        int s = dp(38);
        TextView marker = marker(String.valueOf(p.id), Color.rgb(220, 40, 50));
        marker.setOnTouchListener((v, e) -> handlePointDrag(p, e));
        p.marker = marker;
        p.lp = overlayLp(s, s, p.x - s / 2, p.y - s / 2, false);
        try { wm.addView(marker, p.lp); } catch (Exception ignored) { p.marker = null; p.lp = null; }
    }

    private boolean handlePointDrag(ClickPoint p, MotionEvent e) {
        if (running || p.lp == null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                selectedPointId = p.id;
                p.downX = e.getRawX();
                p.downY = e.getRawY();
                p.startX = p.x;
                p.startY = p.y;
                refreshUi();
                return true;
            case MotionEvent.ACTION_MOVE:
                p.x = clamp(p.startX + Math.round(e.getRawX() - p.downX), 0, screenW - 1);
                p.y = clamp(p.startY + Math.round(e.getRawY() - p.downY), 0, screenH - 1);
                movePointMarker(p);
                return true;
            case MotionEvent.ACTION_UP:
                savePoints();
                return true;
            default:
                return true;
        }
    }

    private void movePointMarker(ClickPoint p) {
        if (p.marker == null || p.lp == null) return;
        p.lp.x = p.x - p.lp.width / 2;
        p.lp.y = p.y - p.lp.height / 2;
        try { wm.updateViewLayout(p.marker, p.lp); } catch (Exception ignored) { }
    }

    private Button intervalButton(String text, int delta) {
        Button b = small(text);
        b.setOnClickListener(v -> {
            if (running) return;
            ClickPoint p = selectedPoint();
            if (p == null) return;
            p.intervalMs = clamp(p.intervalMs + delta, 10, 600000);
            savePoints();
            refreshUi();
        });
        return b;
    }

    private ClickPoint selectedPoint() {
        for (ClickPoint p : points) if (p.id == selectedPointId) return p;
        return null;
    }

    private void removeSelectedPoint() {
        if (running) return;
        ClickPoint p = selectedPoint();
        if (p == null) return;
        if (p.marker != null) try { wm.removeView(p.marker); } catch (Exception ignored) { }
        points.remove(p);
        selectedPointId = points.isEmpty() ? -1 : points.get(0).id;
        savePoints();
        refreshUi();
    }

    private void setClickMarkersVisible(boolean visible) {
        for (ClickPoint p : points) if (p.marker != null) p.marker.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setPointTouchable(ClickPoint p, boolean touchable) {
        if (p.marker == null || p.lp == null) return;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        p.lp.flags = flags;
        try { wm.updateViewLayout(p.marker, p.lp); } catch (Exception ignored) { }
    }

    // ---------- DESLIZE LIVRE ----------

    private void ensureSwipeDefaults() {
        if (swipeStartX <= 0 || swipeEndX <= 0) resetSwipePointsInternal(false);
    }

    private void showSwipeMarkers() {
        if (wm == null) return;
        if (swipeLine == null) {
            swipeLine = new SwipeLineView();
            swipeLineLp = overlayLp(screenW, screenH, 0, 0, true);
            try { wm.addView(swipeLine, swipeLineLp); } catch (Exception ignored) { swipeLine = null; swipeLineLp = null; }
        }
        if (swipeStartMarker == null) {
            int s = dp(42);
            swipeStartMarker = marker("1", Color.rgb(25, 170, 85));
            swipeStartMarker.setOnTouchListener((v, e) -> handleSwipeMarker(e, true));
            swipeStartLp = overlayLp(s, s, swipeStartX - s / 2, swipeStartY - s / 2, false);
            try { wm.addView(swipeStartMarker, swipeStartLp); } catch (Exception ignored) { swipeStartMarker = null; swipeStartLp = null; }
        }
        if (swipeEndMarker == null) {
            int s = dp(42);
            swipeEndMarker = marker("2", Color.rgb(235, 85, 35));
            swipeEndMarker.setOnTouchListener((v, e) -> handleSwipeMarker(e, false));
            swipeEndLp = overlayLp(s, s, swipeEndX - s / 2, swipeEndY - s / 2, false);
            try { wm.addView(swipeEndMarker, swipeEndLp); } catch (Exception ignored) { swipeEndMarker = null; swipeEndLp = null; }
        }
        setSwipeVisible(mode == MODE_SWIPE);
    }

    private boolean handleSwipeMarker(MotionEvent e, boolean start) {
        if (running) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                markerDownX = e.getRawX();
                markerDownY = e.getRawY();
                markerStartX = start ? swipeStartX : swipeEndX;
                markerStartY = start ? swipeStartY : swipeEndY;
                return true;
            case MotionEvent.ACTION_MOVE:
                int nx = clamp(markerStartX + Math.round(e.getRawX() - markerDownX), 0, screenW - 1);
                int ny = clamp(markerStartY + Math.round(e.getRawY() - markerDownY), 0, screenH - 1);
                if (start) { swipeStartX = nx; swipeStartY = ny; }
                else { swipeEndX = nx; swipeEndY = ny; }
                moveSwipeMarkers();
                return true;
            case MotionEvent.ACTION_UP:
                saveSettings();
                return true;
            default:
                return true;
        }
    }

    private void moveSwipeMarkers() {
        if (swipeStartMarker != null && swipeStartLp != null) {
            swipeStartLp.x = swipeStartX - swipeStartLp.width / 2;
            swipeStartLp.y = swipeStartY - swipeStartLp.height / 2;
            try { wm.updateViewLayout(swipeStartMarker, swipeStartLp); } catch (Exception ignored) { }
        }
        if (swipeEndMarker != null && swipeEndLp != null) {
            swipeEndLp.x = swipeEndX - swipeEndLp.width / 2;
            swipeEndLp.y = swipeEndY - swipeEndLp.height / 2;
            try { wm.updateViewLayout(swipeEndMarker, swipeEndLp); } catch (Exception ignored) { }
        }
        if (swipeLine != null) swipeLine.invalidate();
    }

    private void resizeLineOverlay() {
        if (swipeLine == null || swipeLineLp == null) return;
        swipeLineLp.width = screenW;
        swipeLineLp.height = screenH;
        try { wm.updateViewLayout(swipeLine, swipeLineLp); } catch (Exception ignored) { }
        swipeLine.invalidate();
    }

    private void resetSwipePoints() {
        if (running) return;
        resetSwipePointsInternal(true);
    }

    private void resetSwipePointsInternal(boolean save) {
        swipeStartX = Math.round(screenW * 0.25f);
        swipeStartY = Math.round(screenH * 0.60f);
        swipeEndX = Math.round(screenW * 0.75f);
        swipeEndY = Math.round(screenH * 0.40f);
        moveSwipeMarkers();
        if (save) saveSettings();
    }

    private void swapSwipePoints() {
        if (running) return;
        int x = swipeStartX, y = swipeStartY;
        swipeStartX = swipeEndX; swipeStartY = swipeEndY;
        swipeEndX = x; swipeEndY = y;
        moveSwipeMarkers();
        saveSettings();
    }

    private void setSwipeVisible(boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        if (swipeStartMarker != null) swipeStartMarker.setVisibility(vis);
        if (swipeEndMarker != null) swipeEndMarker.setVisibility(vis);
        if (swipeLine != null) swipeLine.setVisibility(vis);
    }

    private void setSwipeTouchable(boolean touchable) {
        setOverlayTouchable(swipeStartMarker, swipeStartLp, touchable);
        setOverlayTouchable(swipeEndMarker, swipeEndLp, touchable);
    }

    private void setOverlayTouchable(View view, WindowManager.LayoutParams lp, boolean touchable) {
        if (view == null || lp == null) return;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        lp.flags = flags;
        try { wm.updateViewLayout(view, lp); } catch (Exception ignored) { }
    }

    private class SwipeLineView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SwipeLineView() {
            super(MasterToolsService.this);
            paint.setColor(Color.argb(225, 0, 210, 255));
            paint.setStrokeWidth(dp(4));
            paint.setStrokeCap(Paint.Cap.ROUND);
            setBackgroundColor(Color.TRANSPARENT);
        }
        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawLine(swipeStartX, swipeStartY, swipeEndX, swipeEndY, paint);
            float dx = swipeEndX - swipeStartX;
            float dy = swipeEndY - swipeStartY;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 1) {
                float ux = dx / len, uy = dy / len;
                float px = -uy, py = ux;
                float a = dp(16), w = dp(8);
                c.drawLine(swipeEndX, swipeEndY, swipeEndX - ux * a + px * w, swipeEndY - uy * a + py * w, paint);
                c.drawLine(swipeEndX, swipeEndY, swipeEndX - ux * a - px * w, swipeEndY - uy * a - py * w, paint);
            }
        }
    }

    // ---------- EXECUÇÃO ----------

    private void startAutomation() {
        if (running) return;
        if (mode == MODE_CLICK && points.isEmpty()) {
            Toast.makeText(this, "Adicione pelo menos um ponto", Toast.LENGTH_SHORT).show();
            return;
        }
        running = true;
        gestureBusy = false;
        cycleCount = 0;
        diagonalForward = true;
        ninjaPhase = 0;
        long now = SystemClock.uptimeMillis();
        for (ClickPoint p : points) {
            p.count = 0;
            p.nextAt = now;
            setPointTouchable(p, false);
        }
        setSwipeTouchable(false);
        refreshUi();
        if (status != null) status.setText("Executando " + modeName());
        handler.post(automationRunnable);
    }

    private void stopAutomation(String message) {
        running = false;
        gestureBusy = false;
        handler.removeCallbacks(automationRunnable);
        for (ClickPoint p : points) setPointTouchable(p, mode == MODE_CLICK);
        setSwipeTouchable(mode == MODE_SWIPE);
        if (status != null) status.setText(message);
        refreshUi();
    }

    private final Runnable automationRunnable = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (gestureBusy) { handler.postDelayed(this, 8); return; }
            if (mode == MODE_CLICK) { runClickCycle(); return; }

            if (mode == MODE_SWIPE && !swipeUnlimited && cycleCount >= swipeLimit) {
                stopAutomation("Concluído");
                return;
            }

            GestureDescription g = buildGesture();
            if (g == null) { stopAutomation("Falha ao criar gesto"); return; }
            gestureBusy = true;
            boolean accepted = dispatchGesture(g, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription gestureDescription) {
                    gestureBusy = false;
                    if (!running) return;
                    cycleCount++;
                    if (mode == MODE_DIAGONAL) diagonalForward = !diagonalForward;
                    if (mode == MODE_NINJA) ninjaPhase += 0.37;
                    if (status != null) status.setText(modeName() + " • " + cycleCount + " gesto(s)");
                    handler.postDelayed(automationRunnable, 18);
                }
                @Override public void onCancelled(GestureDescription gestureDescription) {
                    gestureBusy = false;
                    if (running) handler.postDelayed(automationRunnable, 40);
                }
            }, null);
            if (!accepted) { gestureBusy = false; handler.postDelayed(this, 40); }
        }
    };

    private void runClickCycle() {
        boolean allDone = !clickUnlimited;
        long now = SystemClock.uptimeMillis();
        ClickPoint due = null;
        long nearest = Long.MAX_VALUE;
        for (ClickPoint p : points) {
            boolean done = !clickUnlimited && p.count >= clickLimit;
            if (!done) allDone = false;
            if (done) continue;
            if (p.nextAt <= now && due == null) due = p;
            nearest = Math.min(nearest, p.nextAt);
        }
        if (allDone) { stopAutomation("Concluído"); return; }
        if (due == null) {
            long delay = nearest == Long.MAX_VALUE ? 10 : Math.max(5, Math.min(100, nearest - now));
            handler.postDelayed(automationRunnable, delay);
            return;
        }
        final ClickPoint p = due;
        gestureBusy = true;
        boolean ok = dispatchGesture(tap(p.x, p.y, 45), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                gestureBusy = false;
                p.count++;
                p.nextAt = SystemClock.uptimeMillis() + p.intervalMs;
                if (status != null) status.setText("P" + p.id + ": " + p.count + (clickUnlimited ? "" : "/" + clickLimit));
                handler.postDelayed(automationRunnable, 5);
            }
            @Override public void onCancelled(GestureDescription gestureDescription) {
                gestureBusy = false;
                p.nextAt = SystemClock.uptimeMillis() + 30;
                handler.postDelayed(automationRunnable, 10);
            }
        }, null);
        if (!ok) { gestureBusy = false; p.nextAt = SystemClock.uptimeMillis() + 30; handler.postDelayed(automationRunnable, 10); }
    }

    private GestureDescription buildGesture() {
        float w = screenW, h = screenH;
        Path path = new Path();
        if (mode == MODE_SWIPE) {
            path.moveTo(swipeStartX, swipeStartY);
            path.lineTo(swipeEndX, swipeEndY);
        } else if (mode == MODE_DIAGONAL) {
            if (diagonalForward) { path.moveTo(w * .06f, h * .10f); path.lineTo(w * .94f, h * .90f); }
            else { path.moveTo(w * .94f, h * .90f); path.lineTo(w * .06f, h * .10f); }
        } else if (mode == MODE_DUAL) {
            Path a = new Path(); a.moveTo(w * .06f, h * .42f); a.lineTo(w * .94f, h * .42f);
            Path b = new Path(); b.moveTo(w * .94f, h * .58f); b.lineTo(w * .06f, h * .58f);
            return new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(a, 0, durationMs))
                    .addStroke(new GestureDescription.StrokeDescription(b, 0, durationMs)).build();
        } else if (mode == MODE_NINJA) {
            float cx = w * .5f, cy = h * .5f, rx = w * .44f, ry = h * .40f;
            for (int i = 0; i <= 50; i++) {
                double t = Math.PI * 2.0 * i / 50.0;
                float x = cx + rx * (float)Math.sin(2*t + ninjaPhase);
                float y = cy + ry * (float)Math.sin(3*t + 1.1 + ninjaPhase * 1.25);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
        } else return null;
        return new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs)).build();
    }

    private GestureDescription tap(float x, float y, long ms) {
        Path p = new Path(); p.moveTo(x, y);
        return new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p, 0, ms)).build();
    }

    // ---------- PERSISTÊNCIA ----------

    private void saveSettings() {
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        e.putInt("mode", mode).putInt("duration", durationMs)
                .putInt("click_limit", clickLimit).putBoolean("click_unlimited", clickUnlimited)
                .putInt("swipe_limit", swipeLimit).putBoolean("swipe_unlimited", swipeUnlimited)
                .putInt("swipe_sx", swipeStartX).putInt("swipe_sy", swipeStartY)
                .putInt("swipe_ex", swipeEndX).putInt("swipe_ey", swipeEndY).apply();
    }

    private void loadSettings() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        mode = clamp(p.getInt("mode", MODE_CLICK), MODE_CLICK, MODE_NINJA);
        durationMs = clamp(p.getInt("duration", 350), 80, 3000);
        clickLimit = clamp(p.getInt("click_limit", 100), 1, 10000);
        clickUnlimited = p.getBoolean("click_unlimited", false);
        swipeLimit = clamp(p.getInt("swipe_limit", 100), 1, 10000);
        swipeUnlimited = p.getBoolean("swipe_unlimited", false);
        swipeStartX = p.getInt("swipe_sx", 0); swipeStartY = p.getInt("swipe_sy", 0);
        swipeEndX = p.getInt("swipe_ex", 0); swipeEndY = p.getInt("swipe_ey", 0);
    }

    private void savePoints() {
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        e.putInt("point_count", points.size()).putInt("next_id", nextPointId);
        for (int i = 0; i < MAX_POINTS; i++) {
            if (i < points.size()) {
                ClickPoint p = points.get(i);
                e.putInt("p"+i+"id", p.id).putInt("p"+i+"x", p.x).putInt("p"+i+"y", p.y).putInt("p"+i+"i", p.intervalMs);
            } else {
                e.remove("p"+i+"id").remove("p"+i+"x").remove("p"+i+"y").remove("p"+i+"i");
            }
        }
        e.apply();
    }

    private void loadPoints() {
        SharedPreferences s = getSharedPreferences(PREFS, MODE_PRIVATE);
        int count = clamp(s.getInt("point_count", 0), 0, MAX_POINTS);
        nextPointId = Math.max(1, s.getInt("next_id", 1));
        for (int i=0;i<count;i++) {
            ClickPoint p = new ClickPoint();
            p.id = s.getInt("p"+i+"id", i+1);
            p.x = clamp(s.getInt("p"+i+"x", screenW/2), 0, screenW-1);
            p.y = clamp(s.getInt("p"+i+"y", screenH/2), 0, screenH-1);
            p.intervalMs = clamp(s.getInt("p"+i+"i", 100), 10, 600000);
            points.add(p);
        }
        if (!points.isEmpty()) selectedPointId = points.get(0).id;
    }

    // ---------- UI HELPERS ----------

    private TextView label(String text, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextColor(color); t.setTextSize(sp); t.setGravity(Gravity.CENTER);
        return t;
    }

    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setTextSize(10); b.setMinHeight(0); return b; }
    private Button small(String text) { Button b = button(text); b.setTextSize(9); b.setMinWidth(0); b.setMinimumWidth(0); return b; }
    private Button modeSmall(String text, int target) { Button b = small(text); b.setOnClickListener(v -> setMode(target)); return b; }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER); return r; }
    private LinearLayout.LayoutParams full(int h) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h); p.setMargins(0, dp(1),0,dp(1)); return p; }
    private LinearLayout.LayoutParams cell(int h) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,h,1f); p.setMargins(dp(1),dp(1),dp(1),dp(1)); return p; }

    private TextView marker(String text, int color) {
        TextView m = new TextView(this); m.setText(text); m.setTextColor(Color.WHITE); m.setTextSize(13); m.setGravity(Gravity.CENTER); m.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(Color.argb(235, Color.red(color), Color.green(color), Color.blue(color))); g.setStroke(dp(3), Color.WHITE); m.setBackground(g); return m;
    }

    private WindowManager.LayoutParams overlayLp(int w, int h, int x, int y, boolean notTouchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (notTouchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,flags,PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START; lp.x=x; lp.y=y; return lp;
    }

    private void updateScreenSize() { DisplayMetrics d = getResources().getDisplayMetrics(); screenW = d.widthPixels; screenH = d.heightPixels; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { stopAutomation("Interrompido"); }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (captureOverlay != null && wm != null) try { wm.removeView(captureOverlay); } catch (Exception ignored) { }
        for (ClickPoint p : points) if (p.marker != null && wm != null) try { wm.removeView(p.marker); } catch (Exception ignored) { }
        if (swipeStartMarker != null && wm != null) try { wm.removeView(swipeStartMarker); } catch (Exception ignored) { }
        if (swipeEndMarker != null && wm != null) try { wm.removeView(swipeEndMarker); } catch (Exception ignored) { }
        if (swipeLine != null && wm != null) try { wm.removeView(swipeLine); } catch (Exception ignored) { }
        if (mini != null && wm != null) try { wm.removeView(mini); } catch (Exception ignored) { }
        if (root != null && wm != null) try { wm.removeView(root); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
