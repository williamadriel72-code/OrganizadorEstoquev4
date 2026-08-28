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

public class MasterToolsServiceV15 extends AccessibilityService {
    private static final String PREFS = "master_tools_v14";
    private static final int MODE_CLICK = 0;
    private static final int MODE_SWIPE = 1;
    private static final int MODE_DIAGONAL = 2;
    private static final int MODE_DUAL = 3;
    private static final int MODE_NINJA = 4;
    private static final int MODE_COMBINED = 5;
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
    private TextView swipeRepeatInfo;
    private TextView duration1Info;
    private TextView duration2Info;
    private TextView gapInfo;
    private TextView orderInfo;
    private Button modeButton;
    private Button startButton;
    private Button stopButton;
    private Button removePointButton;
    private Button clickUnlimitedButton;
    private Button swipeUnlimitedButton;
    private LinearLayout clickControls;
    private LinearLayout swipeControls;
    private LinearLayout genericDurationControls;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<ClickPoint> points = new ArrayList<>();

    private int mode = MODE_CLICK;
    private int genericDurationMs = 350;
    private int swipe1DurationMs = 350;
    private int swipe2DurationMs = 350;
    private int swipeGapMs = 120;
    private boolean swipeFirstPairFirst = true;
    private int clickLimit = 100;
    private boolean clickUnlimited;
    private int swipeLimit = 100;
    private boolean swipeUnlimited;
    private boolean running;
    private boolean gestureBusy;
    private long swipeCycleCount;
    private long totalCombinedClicks;
    private int swipeStage;
    private boolean diagonalForward = true;
    private double ninjaPhase;

    private int selectedPointId = -1;
    private int nextPointId = 1;

    private final int[] sx = new int[4];
    private final int[] sy = new int[4];
    private final TextView[] swipeMarkers = new TextView[4];
    private final WindowManager.LayoutParams[] swipeMarkerLp = new WindowManager.LayoutParams[4];
    private SwipeLinesView swipeLines;
    private WindowManager.LayoutParams swipeLinesLp;

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

    // -------------------- PAINEL --------------------

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
        modeButton.setOnClickListener(v -> setMode((mode + 1) % 6));
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

        Button together = button("CLIQUE + DESLIZE AO MESMO TEMPO");
        together.setOnClickListener(v -> setMode(MODE_COMBINED));
        body.addView(together, full(dp(40)));

        buildClickControls();
        buildSwipeControls();
        buildGenericDurationControls();

        startButton = button("INICIAR AUTOMAÇÃO");
        startButton.setOnClickListener(v -> startAutomation());
        body.addView(startButton, full(dp(42)));

        stopButton = button("PARAR TUDO");
        stopButton.setOnClickListener(v -> stopAutomation("Parado"));
        body.addView(stopButton, full(dp(42)));

        Button minimize = button("MINIMIZAR");
        minimize.setOnClickListener(v -> minimizePanel());
        body.addView(minimize, full(dp(40)));

        panelLp = new WindowManager.LayoutParams(
                panelWidth(), panelHeight(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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

        TextView title = label("AUTOCLICK", 10, Color.rgb(255, 100, 100));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        clickControls.addView(title, full(dp(26)));

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
        clickUnlimitedButton = small("ILIMITADO");
        clickUnlimitedButton.setOnClickListener(v -> {
            if (running) return;
            clickUnlimited = !clickUnlimited;
            saveSettings();
            refreshUi();
        });
        qty.addView(clickUnlimitedButton, cell(dp(34)));
        clickControls.addView(qty, full(dp(36)));

        body.addView(clickControls);
    }

    private void buildSwipeControls() {
        swipeControls = new LinearLayout(this);
        swipeControls.setOrientation(LinearLayout.VERTICAL);

        TextView title = label("DESLIZES 1→2 E 3→4", 10, Color.rgb(0, 210, 255));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        swipeControls.addView(title, full(dp(26)));

        TextView hint = label("Arraste 1, 2, 3 e 4 livremente na tela", 9, Color.WHITE);
        swipeControls.addView(hint, full(dp(30)));

        LinearLayout tools1 = row();
        Button center = small("CENTRALIZAR");
        center.setOnClickListener(v -> resetSwipePoints());
        tools1.addView(center, cell(dp(34)));
        Button inv12 = small("INV 1↔2");
        inv12.setOnClickListener(v -> swapPair(0, 1));
        tools1.addView(inv12, cell(dp(34)));
        Button inv34 = small("INV 3↔4");
        inv34.setOnClickListener(v -> swapPair(2, 3));
        tools1.addView(inv34, cell(dp(34)));
        swipeControls.addView(tools1, full(dp(36)));

        orderInfo = label("Ordem: 1→2 depois 3→4", 9, Color.WHITE);
        swipeControls.addView(orderInfo, full(dp(26)));
        Button order = small("TROCAR ORDEM DOS DESLIZES");
        order.setOnClickListener(v -> {
            if (running) return;
            swipeFirstPairFirst = !swipeFirstPairFirst;
            saveSettings();
            refreshUi();
        });
        swipeControls.addView(order, full(dp(34)));

        duration1Info = label("Duração 1→2: 350 ms", 9, Color.WHITE);
        swipeControls.addView(duration1Info, full(dp(24)));
        LinearLayout d1 = row();
        Button d1m = small("−50 ms"); d1m.setOnClickListener(v -> adjustSwipeDuration(0, -50)); d1.addView(d1m, cell(dp(32)));
        Button d1p = small("+50 ms"); d1p.setOnClickListener(v -> adjustSwipeDuration(0, 50)); d1.addView(d1p, cell(dp(32)));
        swipeControls.addView(d1, full(dp(34)));

        duration2Info = label("Duração 3→4: 350 ms", 9, Color.WHITE);
        swipeControls.addView(duration2Info, full(dp(24)));
        LinearLayout d2 = row();
        Button d2m = small("−50 ms"); d2m.setOnClickListener(v -> adjustSwipeDuration(1, -50)); d2.addView(d2m, cell(dp(32)));
        Button d2p = small("+50 ms"); d2p.setOnClickListener(v -> adjustSwipeDuration(1, 50)); d2.addView(d2p, cell(dp(32)));
        swipeControls.addView(d2, full(dp(34)));

        gapInfo = label("Intervalo entre deslizes: 120 ms", 9, Color.WHITE);
        swipeControls.addView(gapInfo, full(dp(24)));
        LinearLayout gap = row();
        Button gm = small("−50 ms"); gm.setOnClickListener(v -> adjustSwipeGap(-50)); gap.addView(gm, cell(dp(32)));
        Button gp = small("+50 ms"); gp.setOnClickListener(v -> adjustSwipeGap(50)); gap.addView(gp, cell(dp(32)));
        swipeControls.addView(gap, full(dp(34)));

        swipeRepeatInfo = label("Repetições: 100", 10, Color.WHITE);
        swipeControls.addView(swipeRepeatInfo, full(dp(28)));
        LinearLayout rep = row();
        Button sm = small("−10"); sm.setOnClickListener(v -> adjustSwipeLimit(-10)); rep.addView(sm, cell(dp(34)));
        Button sp = small("+10"); sp.setOnClickListener(v -> adjustSwipeLimit(10)); rep.addView(sp, cell(dp(34)));
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

    private void buildGenericDurationControls() {
        genericDurationControls = new LinearLayout(this);
        genericDurationControls.setOrientation(LinearLayout.VERTICAL);
        TextView info = label("Duração dos gestos especiais", 9, Color.WHITE);
        genericDurationControls.addView(info, full(dp(24)));
        LinearLayout r = row();
        Button minus = small("−50 ms");
        minus.setOnClickListener(v -> {
            if (running) return;
            genericDurationMs = clamp(genericDurationMs - 50, 80, 3000);
            saveSettings();
        });
        r.addView(minus, cell(dp(34)));
        Button plus = small("+50 ms");
        plus.setOnClickListener(v -> {
            if (running) return;
            genericDurationMs = clamp(genericDurationMs + 50, 80, 3000);
            saveSettings();
        });
        r.addView(plus, cell(dp(34)));
        genericDurationControls.addView(r, full(dp(36)));
        body.addView(genericDurationControls);
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
                if (panelLp.x != lastPanelX || panelLp.y != lastPanelY) {
                    lastPanelX = panelLp.x;
                    lastPanelY = panelLp.y;
                    try { wm.updateViewLayout(root, panelLp); } catch (Exception ignored) { }
                }
                return true;
            default:
                return true;
        }
    }

    private int panelWidth() { return Math.min(dp(250), Math.max(dp(205), screenW - dp(20))); }
    private int panelHeight() { return Math.min(dp(520), Math.max(dp(210), screenH - dp(20))); }

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
        float scaleX = screenW / (float) oldW;
        float scaleY = screenH / (float) oldH;

        for (ClickPoint p : points) {
            p.x = clamp(Math.round(p.x * scaleX), 0, screenW - 1);
            p.y = clamp(Math.round(p.y * scaleY), 0, screenH - 1);
            movePointMarker(p);
        }
        for (int i = 0; i < 4; i++) {
            sx[i] = clamp(Math.round(sx[i] * scaleX), 0, screenW - 1);
            sy[i] = clamp(Math.round(sy[i] * scaleY), 0, screenH - 1);
        }
        moveAllSwipeMarkers();
        resizeSwipeLines();
        resizePanelForScreen();
        saveSettings();
        savePoints();
    }

    private void minimizePanel() {
        if (root == null || mini != null) return;
        root.setVisibility(View.GONE);
        mini = marker("≡", Color.rgb(25, 105, 225));
        mini.setTextSize(18);
        mini.setOnTouchListener((v, e) -> handleMini(e));
        miniLp = overlayLp(dp(46), dp(46), panelLp.x, panelLp.y, false);
        try { wm.addView(mini, miniLp); } catch (Exception ignored) { mini = null; miniLp = null; }
    }

    private boolean handleMini(MotionEvent e) {
        if (miniLp == null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                miniDownX = e.getRawX(); miniDownY = e.getRawY();
                miniStartX = miniLp.x; miniStartY = miniLp.y; miniMoved = false; return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - miniDownX, dy = e.getRawY() - miniDownY;
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
            default: return true;
        }
    }

    private void restorePanel() {
        if (mini != null) {
            try { wm.removeView(mini); } catch (Exception ignored) { }
            mini = null; miniLp = null;
        }
        if (root != null) { resizePanelForScreen(); root.setVisibility(View.VISIBLE); }
    }

    // -------------------- MODOS / UI --------------------

    private void setMode(int newMode) {
        if (running) stopAutomation("Modo alterado");
        mode = clamp(newMode, MODE_CLICK, MODE_COMBINED);
        saveSettings();
        refreshUi();
    }

    private boolean clickVisibleMode() { return mode == MODE_CLICK || mode == MODE_COMBINED; }
    private boolean swipeVisibleMode() { return mode == MODE_SWIPE || mode == MODE_COMBINED; }

    private void refreshUi() {
        if (modeButton != null) modeButton.setText("MODO: " + modeName());
        if (clickControls != null) clickControls.setVisibility(clickVisibleMode() ? View.VISIBLE : View.GONE);
        if (swipeControls != null) swipeControls.setVisibility(swipeVisibleMode() ? View.VISIBLE : View.GONE);
        if (genericDurationControls != null) genericDurationControls.setVisibility((mode == MODE_DIAGONAL || mode == MODE_DUAL || mode == MODE_NINJA) ? View.VISIBLE : View.GONE);

        ClickPoint selected = selectedPoint();
        if (selected == null && !points.isEmpty()) { selectedPointId = points.get(0).id; selected = points.get(0); }
        if (pointInfo != null) pointInfo.setText(selected == null ? "Nenhum ponto marcado" : "C" + selected.id + " • intervalo " + selected.intervalMs + " ms");
        if (quantityInfo != null) quantityInfo.setText(clickUnlimited ? "Quantidade: ILIMITADO" : "Quantidade por ponto: " + clickLimit);
        if (clickUnlimitedButton != null) clickUnlimitedButton.setText(clickUnlimited ? "LIMITADO" : "ILIMITADO");
        if (removePointButton != null) removePointButton.setEnabled(!running && selected != null);

        if (duration1Info != null) duration1Info.setText("Duração 1→2: " + swipe1DurationMs + " ms");
        if (duration2Info != null) duration2Info.setText("Duração 3→4: " + swipe2DurationMs + " ms");
        if (gapInfo != null) gapInfo.setText("Intervalo entre deslizes: " + swipeGapMs + " ms");
        if (orderInfo != null) orderInfo.setText(swipeFirstPairFirst ? "Ordem: 1→2 depois 3→4" : "Ordem: 3→4 depois 1→2");
        if (swipeRepeatInfo != null) swipeRepeatInfo.setText(swipeUnlimited ? "Repetições: ILIMITADO" : "Repetições: " + swipeLimit);
        if (swipeUnlimitedButton != null) swipeUnlimitedButton.setText(swipeUnlimited ? "LIMITADO" : "ILIMITADO");

        for (ClickPoint p : points) {
            if (p.marker != null) {
                p.marker.setVisibility(clickVisibleMode() ? View.VISIBLE : View.GONE);
                setPointTouchable(p, clickVisibleMode() && !running);
            }
        }
        setSwipeVisible(swipeVisibleMode());
        setSwipeTouchable(swipeVisibleMode() && !running);

        if (status != null && !running) {
            if (mode == MODE_CLICK) status.setText(points.isEmpty() ? "Adicione um ponto" : points.size() + " ponto(s) de clique pronto(s)");
            else if (mode == MODE_SWIPE) status.setText("Configure 1→2 e 3→4");
            else if (mode == MODE_COMBINED) status.setText(points.isEmpty() ? "Adicione clique(s) para executar junto" : "CLIQUE + DESLIZE prontos");
            else status.setText(modeName() + " pronto");
        }

        boolean needsClicks = mode == MODE_CLICK || mode == MODE_COMBINED;
        if (startButton != null) startButton.setEnabled(!running && (!needsClicks || !points.isEmpty()));
        if (stopButton != null) stopButton.setEnabled(running);
    }

    private String modeName() {
        switch (mode) {
            case MODE_SWIPE: return "DESLIZE 1-2 + 3-4";
            case MODE_DIAGONAL: return "DIAGONAL";
            case MODE_DUAL: return "2 HORIZONTAIS";
            case MODE_NINJA: return "NINJA";
            case MODE_COMBINED: return "CLIQUE + DESLIZE";
            default: return "AUTOCLICKER";
        }
    }

    private void adjustClickLimit(int d) { if (!running) { clickLimit = clamp(clickLimit + d, 1, 10000); saveSettings(); refreshUi(); } }
    private void adjustSwipeLimit(int d) { if (!running) { swipeLimit = clamp(swipeLimit + d, 1, 10000); saveSettings(); refreshUi(); } }
    private void adjustSwipeDuration(int which, int d) {
        if (running) return;
        if (which == 0) swipe1DurationMs = clamp(swipe1DurationMs + d, 80, 3000);
        else swipe2DurationMs = clamp(swipe2DurationMs + d, 80, 3000);
        saveSettings(); refreshUi();
    }
    private void adjustSwipeGap(int d) { if (!running) { swipeGapMs = clamp(swipeGapMs + d, 0, 5000); saveSettings(); refreshUi(); } }

    // -------------------- AUTOCLICK --------------------

    private void beginPointCapture() {
        if (running || !clickVisibleMode() || captureOverlay != null || points.size() >= MAX_POINTS) return;
        root.setVisibility(View.GONE);
        setClickMarkersVisible(false);
        setSwipeVisible(false);

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
                Toast.makeText(this, "Clique C" + p.id + " marcado", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        WindowManager.LayoutParams lp = overlayLp(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, 0, 0, false);
        wm.addView(captureOverlay, lp);
        Toast.makeText(this, "Toque onde o AutoClick deve clicar", Toast.LENGTH_LONG).show();
    }

    private void endCapture() {
        if (captureOverlay != null) {
            try { wm.removeView(captureOverlay); } catch (Exception ignored) { }
            captureOverlay = null;
        }
        if (root != null) root.setVisibility(View.VISIBLE);
        setClickMarkersVisible(clickVisibleMode());
        setSwipeVisible(swipeVisibleMode());
    }

    private void showAllClickMarkers() { for (ClickPoint p : points) showPointMarker(p); }

    private void showPointMarker(ClickPoint p) {
        if (p.marker != null || wm == null) return;
        int size = dp(38);
        TextView m = marker("C" + p.id, Color.rgb(220, 40, 50));
        m.setTextSize(11);
        m.setOnTouchListener((v, e) -> handlePointDrag(p, e));
        p.marker = m;
        p.lp = overlayLp(size, size, p.x - size / 2, p.y - size / 2, false);
        try { wm.addView(m, p.lp); } catch (Exception ignored) { p.marker = null; p.lp = null; }
    }

    private boolean handlePointDrag(ClickPoint p, MotionEvent e) {
        if (running || p.lp == null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                selectedPointId = p.id;
                p.downX = e.getRawX(); p.downY = e.getRawY(); p.startX = p.x; p.startY = p.y;
                refreshUi(); return true;
            case MotionEvent.ACTION_MOVE:
                p.x = clamp(p.startX + Math.round(e.getRawX() - p.downX), 0, screenW - 1);
                p.y = clamp(p.startY + Math.round(e.getRawY() - p.downY), 0, screenH - 1);
                movePointMarker(p); return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                savePoints(); return true;
            default: return true;
        }
    }

    private void movePointMarker(ClickPoint p) {
        if (p.marker == null || p.lp == null) return;
        p.lp.x = p.x - p.lp.width / 2; p.lp.y = p.y - p.lp.height / 2;
        try { wm.updateViewLayout(p.marker, p.lp); } catch (Exception ignored) { }
    }

    private Button intervalButton(String text, int delta) {
        Button b = small(text);
        b.setOnClickListener(v -> {
            if (running) return;
            ClickPoint p = selectedPoint();
            if (p == null) return;
            p.intervalMs = clamp(p.intervalMs + delta, 10, 600000);
            savePoints(); refreshUi();
        });
        return b;
    }

    private ClickPoint selectedPoint() { for (ClickPoint p : points) if (p.id == selectedPointId) return p; return null; }

    private void removeSelectedPoint() {
        if (running) return;
        ClickPoint p = selectedPoint();
        if (p == null) return;
        if (p.marker != null) try { wm.removeView(p.marker); } catch (Exception ignored) { }
        points.remove(p);
        selectedPointId = points.isEmpty() ? -1 : points.get(0).id;
        savePoints(); refreshUi();
    }

    private void setClickMarkersVisible(boolean v) { for (ClickPoint p : points) if (p.marker != null) p.marker.setVisibility(v ? View.VISIBLE : View.GONE); }

    private void setPointTouchable(ClickPoint p, boolean touchable) {
        if (p.marker == null || p.lp == null) return;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        p.lp.flags = flags;
        try { wm.updateViewLayout(p.marker, p.lp); } catch (Exception ignored) { }
    }

    // -------------------- 4 PONTOS DE DESLIZE --------------------

    private void ensureSwipeDefaults() {
        if (sx[0] <= 0 || sx[1] <= 0) resetSwipePointsInternal(false);
        if (sx[2] <= 0 || sx[3] <= 0) resetSecondPairInternal(false);
    }

    private void resetSwipePoints() {
        if (running) return;
        resetSwipePointsInternal(false);
        resetSecondPairInternal(true);
    }

    private void resetSwipePointsInternal(boolean save) {
        sx[0] = Math.round(screenW * .22f); sy[0] = Math.round(screenH * .62f);
        sx[1] = Math.round(screenW * .48f); sy[1] = Math.round(screenH * .38f);
        moveSwipeMarker(0); moveSwipeMarker(1);
        if (swipeLines != null) swipeLines.invalidate();
        if (save) saveSettings();
    }

    private void resetSecondPairInternal(boolean save) {
        sx[2] = Math.round(screenW * .55f); sy[2] = Math.round(screenH * .62f);
        sx[3] = Math.round(screenW * .82f); sy[3] = Math.round(screenH * .38f);
        moveSwipeMarker(2); moveSwipeMarker(3);
        if (swipeLines != null) swipeLines.invalidate();
        if (save) saveSettings();
    }

    private void swapPair(int a, int b) {
        if (running) return;
        int tx = sx[a], ty = sy[a]; sx[a] = sx[b]; sy[a] = sy[b]; sx[b] = tx; sy[b] = ty;
        moveSwipeMarker(a); moveSwipeMarker(b); if (swipeLines != null) swipeLines.invalidate(); saveSettings();
    }

    private void showSwipeMarkers() {
        if (wm == null) return;
        if (swipeLines == null) {
            swipeLines = new SwipeLinesView();
            swipeLinesLp = overlayLp(screenW, screenH, 0, 0, true);
            try { wm.addView(swipeLines, swipeLinesLp); } catch (Exception ignored) { swipeLines = null; swipeLinesLp = null; }
        }
        int[] colors = { Color.rgb(25,170,85), Color.rgb(235,85,35), Color.rgb(20,145,230), Color.rgb(160,70,220) };
        for (int i = 0; i < 4; i++) {
            if (swipeMarkers[i] == null) {
                final int index = i;
                int size = dp(42);
                swipeMarkers[i] = marker(String.valueOf(i + 1), colors[i]);
                swipeMarkers[i].setOnTouchListener((v, e) -> handleSwipeMarker(e, index));
                swipeMarkerLp[i] = overlayLp(size, size, sx[i] - size / 2, sy[i] - size / 2, false);
                try { wm.addView(swipeMarkers[i], swipeMarkerLp[i]); }
                catch (Exception ignored) { swipeMarkers[i] = null; swipeMarkerLp[i] = null; }
            }
        }
        setSwipeVisible(swipeVisibleMode());
    }

    private boolean handleSwipeMarker(MotionEvent e, int index) {
        if (running || swipeMarkerLp[index] == null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                markerDownX = e.getRawX(); markerDownY = e.getRawY(); markerStartX = sx[index]; markerStartY = sy[index]; return true;
            case MotionEvent.ACTION_MOVE:
                sx[index] = clamp(markerStartX + Math.round(e.getRawX() - markerDownX), 0, screenW - 1);
                sy[index] = clamp(markerStartY + Math.round(e.getRawY() - markerDownY), 0, screenH - 1);
                moveSwipeMarker(index); if (swipeLines != null) swipeLines.invalidate(); return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                saveSettings(); return true;
            default: return true;
        }
    }

    private void moveSwipeMarker(int i) {
        if (swipeMarkers[i] == null || swipeMarkerLp[i] == null) return;
        swipeMarkerLp[i].x = sx[i] - swipeMarkerLp[i].width / 2;
        swipeMarkerLp[i].y = sy[i] - swipeMarkerLp[i].height / 2;
        try { wm.updateViewLayout(swipeMarkers[i], swipeMarkerLp[i]); } catch (Exception ignored) { }
    }

    private void moveAllSwipeMarkers() { for (int i = 0; i < 4; i++) moveSwipeMarker(i); if (swipeLines != null) swipeLines.invalidate(); }

    private void resizeSwipeLines() {
        if (swipeLines == null || swipeLinesLp == null) return;
        swipeLinesLp.width = screenW; swipeLinesLp.height = screenH;
        try { wm.updateViewLayout(swipeLines, swipeLinesLp); } catch (Exception ignored) { }
        swipeLines.invalidate();
    }

    private void setSwipeVisible(boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        for (TextView m : swipeMarkers) if (m != null) m.setVisibility(vis);
        if (swipeLines != null) { swipeLines.setVisibility(vis); if (visible) swipeLines.invalidate(); }
    }

    private void setSwipeTouchable(boolean touchable) {
        for (int i = 0; i < 4; i++) setOverlayTouchable(swipeMarkers[i], swipeMarkerLp[i], touchable);
    }

    private void setOverlayTouchable(View v, WindowManager.LayoutParams lp, boolean touchable) {
        if (v == null || lp == null) return;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        lp.flags = flags;
        try { wm.updateViewLayout(v, lp); } catch (Exception ignored) { }
    }

    private class SwipeLinesView extends View {
        private final Paint p1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint p2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        SwipeLinesView() {
            super(MasterToolsServiceV15.this);
            p1.setColor(Color.argb(225, 0, 220, 160)); p1.setStrokeWidth(dp(4)); p1.setStrokeCap(Paint.Cap.ROUND);
            p2.setColor(Color.argb(225, 90, 150, 255)); p2.setStrokeWidth(dp(4)); p2.setStrokeCap(Paint.Cap.ROUND);
            setBackgroundColor(Color.TRANSPARENT);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            drawArrow(c, 0, 1, p1);
            drawArrow(c, 2, 3, p2);
        }
        private void drawArrow(Canvas c, int a, int b, Paint p) {
            c.drawLine(sx[a], sy[a], sx[b], sy[b], p);
            float dx = sx[b] - sx[a], dy = sy[b] - sy[a];
            float len = (float)Math.sqrt(dx*dx + dy*dy);
            if (len <= 1) return;
            float ux = dx/len, uy = dy/len, px = -uy, py = ux;
            float ar = dp(16), wing = dp(8);
            c.drawLine(sx[b], sy[b], sx[b]-ux*ar+px*wing, sy[b]-uy*ar+py*wing, p);
            c.drawLine(sx[b], sy[b], sx[b]-ux*ar-px*wing, sy[b]-uy*ar-py*wing, p);
        }
    }

    // -------------------- EXECUÇÃO --------------------

    private void startAutomation() {
        if (running) return;
        if ((mode == MODE_CLICK || mode == MODE_COMBINED) && points.isEmpty()) {
            Toast.makeText(this, "Adicione pelo menos um ponto de clique", Toast.LENGTH_SHORT).show();
            return;
        }
        running = true;
        gestureBusy = false;
        swipeCycleCount = 0;
        totalCombinedClicks = 0;
        swipeStage = 0;
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
        for (ClickPoint p : points) setPointTouchable(p, clickVisibleMode());
        setSwipeTouchable(swipeVisibleMode());
        if (status != null) status.setText(message);
        refreshUi();
    }

    private final Runnable automationRunnable = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (gestureBusy) { handler.postDelayed(this, 8); return; }
            if (mode == MODE_CLICK) { runClickCycle(); return; }
            if (mode == MODE_COMBINED) { runCombinedCycle(); return; }
            if (mode == MODE_SWIPE) { runDualSwipeCycle(); return; }

            GestureDescription g = buildSpecialGesture();
            if (g == null) { stopAutomation("Falha ao criar gesto"); return; }
            gestureBusy = true;
            boolean accepted = dispatchGesture(g, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription gestureDescription) {
                    gestureBusy = false;
                    if (!running) return;
                    swipeCycleCount++;
                    if (mode == MODE_DIAGONAL) diagonalForward = !diagonalForward;
                    if (mode == MODE_NINJA) ninjaPhase += .37;
                    if (status != null) status.setText(modeName() + " • " + swipeCycleCount + " gesto(s)");
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
        long now = SystemClock.uptimeMillis();
        ClickPoint due = null;
        long nearest = Long.MAX_VALUE;
        boolean allDone = !clickUnlimited;
        for (ClickPoint p : points) {
            boolean done = !clickUnlimited && p.count >= clickLimit;
            if (!done) allDone = false;
            if (done) continue;
            if (p.nextAt <= now && due == null) due = p;
            nearest = Math.min(nearest, p.nextAt);
        }
        if (allDone) { stopAutomation("Concluído"); return; }
        if (due == null) {
            handler.postDelayed(automationRunnable, nearest == Long.MAX_VALUE ? 10 : Math.max(5, Math.min(100, nearest-now)));
            return;
        }
        final ClickPoint point = due;
        gestureBusy = true;
        boolean ok = dispatchGesture(tap(point.x, point.y, 45), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                gestureBusy = false;
                point.count++;
                point.nextAt = SystemClock.uptimeMillis() + point.intervalMs;
                if (status != null) status.setText("C" + point.id + ": " + point.count + (clickUnlimited ? "" : "/" + clickLimit));
                handler.postDelayed(automationRunnable, 5);
            }
            @Override public void onCancelled(GestureDescription gestureDescription) {
                gestureBusy = false; point.nextAt = SystemClock.uptimeMillis() + 30; handler.postDelayed(automationRunnable, 10);
            }
        }, null);
        if (!ok) { gestureBusy = false; point.nextAt = SystemClock.uptimeMillis()+30; handler.postDelayed(automationRunnable,10); }
    }

    private void runDualSwipeCycle() {
        if (!swipeUnlimited && swipeCycleCount >= swipeLimit) { stopAutomation("Concluído"); return; }
        int pair = currentPairForStage();
        int duration = pair == 0 ? swipe1DurationMs : swipe2DurationMs;
        GestureDescription g = singleSwipeGesture(pair, duration);
        gestureBusy = true;
        boolean ok = dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                gestureBusy = false;
                if (!running) return;
                if (swipeStage == 0) {
                    swipeStage = 1;
                    handler.postDelayed(automationRunnable, swipeGapMs);
                } else {
                    swipeStage = 0;
                    swipeCycleCount++;
                    if (status != null) status.setText("DESLIZE • " + swipeCycleCount + " ciclo(s)");
                    handler.postDelayed(automationRunnable, 18);
                }
            }
            @Override public void onCancelled(GestureDescription gestureDescription) {
                gestureBusy = false; if (running) handler.postDelayed(automationRunnable, 40);
            }
        }, null);
        if (!ok) { gestureBusy = false; handler.postDelayed(automationRunnable,40); }
    }

    private void runCombinedCycle() {
        boolean swipesDone = !swipeUnlimited && swipeCycleCount >= swipeLimit;
        boolean clicksDone = clicksFinished();
        if (swipesDone && clicksDone) { stopAutomation("Concluído"); return; }

        if (swipesDone) {
            runClickCycle();
            return;
        }

        final int pair = currentPairForStage();
        final int duration = pair == 0 ? swipe1DurationMs : swipe2DurationMs;
        final ArrayList<ClickPoint> due = collectDueClickPoints();
        GestureDescription g = combinedGesture(pair, duration, due);
        gestureBusy = true;
        boolean ok = dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                gestureBusy = false;
                if (!running) return;
                long doneAt = SystemClock.uptimeMillis();
                for (ClickPoint p : due) {
                    if (!clickUnlimited && p.count >= clickLimit) continue;
                    p.count++;
                    totalCombinedClicks++;
                    p.nextAt = doneAt + p.intervalMs;
                }
                if (swipeStage == 0) {
                    swipeStage = 1;
                    if (status != null) status.setText("JUNTOS • deslize A • cliques " + totalCombinedClicks);
                    handler.postDelayed(automationRunnable, swipeGapMs);
                } else {
                    swipeStage = 0;
                    swipeCycleCount++;
                    if (status != null) status.setText("JUNTOS • " + swipeCycleCount + " ciclo(s) • " + totalCombinedClicks + " clique(s)");
                    handler.postDelayed(automationRunnable, 12);
                }
            }
            @Override public void onCancelled(GestureDescription gestureDescription) {
                gestureBusy = false; if (running) handler.postDelayed(automationRunnable,40);
            }
        }, null);
        if (!ok) { gestureBusy = false; handler.postDelayed(automationRunnable,40); }
    }

    private boolean clicksFinished() {
        if (clickUnlimited) return false;
        for (ClickPoint p : points) if (p.count < clickLimit) return false;
        return true;
    }

    private ArrayList<ClickPoint> collectDueClickPoints() {
        ArrayList<ClickPoint> due = new ArrayList<>();
        long now = SystemClock.uptimeMillis();
        for (ClickPoint p : points) {
            if (!clickUnlimited && p.count >= clickLimit) continue;
            if (p.nextAt <= now) due.add(p);
        }
        return due;
    }

    private int currentPairForStage() {
        if (swipeFirstPairFirst) return swipeStage == 0 ? 0 : 1;
        return swipeStage == 0 ? 1 : 0;
    }

    private GestureDescription combinedGesture(int pair, int duration, ArrayList<ClickPoint> due) {
        GestureDescription.Builder b = new GestureDescription.Builder();
        int a = pair == 0 ? 0 : 2;
        int z = a + 1;
        Path swipe = new Path(); swipe.moveTo(sx[a], sy[a]); swipe.lineTo(sx[z], sy[z]);
        b.addStroke(new GestureDescription.StrokeDescription(swipe, 0, duration));
        int added = 0;
        for (ClickPoint p : due) {
            if (added >= 8) break;
            Path tapPath = new Path(); tapPath.moveTo(p.x, p.y);
            b.addStroke(new GestureDescription.StrokeDescription(tapPath, 0, Math.min(45, duration)));
            added++;
        }
        return b.build();
    }

    private GestureDescription singleSwipeGesture(int pair, int duration) {
        int a = pair == 0 ? 0 : 2, b = a + 1;
        Path p = new Path(); p.moveTo(sx[a], sy[a]); p.lineTo(sx[b], sy[b]);
        return new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,duration)).build();
    }

    private GestureDescription buildSpecialGesture() {
        float w = screenW, h = screenH;
        Path path = new Path();
        if (mode == MODE_DIAGONAL) {
            if (diagonalForward) { path.moveTo(w*.06f,h*.10f); path.lineTo(w*.94f,h*.90f); }
            else { path.moveTo(w*.94f,h*.90f); path.lineTo(w*.06f,h*.10f); }
        } else if (mode == MODE_DUAL) {
            Path a = new Path(); a.moveTo(w*.06f,h*.42f); a.lineTo(w*.94f,h*.42f);
            Path b = new Path(); b.moveTo(w*.94f,h*.58f); b.lineTo(w*.06f,h*.58f);
            return new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(a,0,genericDurationMs))
                    .addStroke(new GestureDescription.StrokeDescription(b,0,genericDurationMs)).build();
        } else if (mode == MODE_NINJA) {
            float cx=w*.5f,cy=h*.5f,rx=w*.44f,ry=h*.40f;
            for (int i=0;i<=50;i++) {
                double t=Math.PI*2.0*i/50.0;
                float x=cx+rx*(float)Math.sin(2*t+ninjaPhase);
                float y=cy+ry*(float)Math.sin(3*t+1.1+ninjaPhase*1.25);
                if(i==0) path.moveTo(x,y); else path.lineTo(x,y);
            }
        } else return null;
        return new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,genericDurationMs)).build();
    }

    private GestureDescription tap(float x,float y,long ms) {
        Path p=new Path(); p.moveTo(x,y);
        return new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,ms)).build();
    }

    // -------------------- PERSISTÊNCIA --------------------

    private void saveSettings() {
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        e.putInt("mode", mode)
                .putInt("duration", genericDurationMs)
                .putInt("click_limit", clickLimit).putBoolean("click_unlimited", clickUnlimited)
                .putInt("swipe_limit", swipeLimit).putBoolean("swipe_unlimited", swipeUnlimited)
                .putInt("swipe1_duration", swipe1DurationMs).putInt("swipe2_duration", swipe2DurationMs)
                .putInt("swipe_gap", swipeGapMs).putBoolean("swipe_order_12_first", swipeFirstPairFirst)
                .putInt("swipe_sx", sx[0]).putInt("swipe_sy", sy[0])
                .putInt("swipe_ex", sx[1]).putInt("swipe_ey", sy[1])
                .putInt("swipe3x", sx[2]).putInt("swipe3y", sy[2])
                .putInt("swipe4x", sx[3]).putInt("swipe4y", sy[3]).apply();
    }

    private void loadSettings() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        mode = clamp(p.getInt("mode", MODE_CLICK), MODE_CLICK, MODE_COMBINED);
        genericDurationMs = clamp(p.getInt("duration", 350), 80, 3000);
        clickLimit = clamp(p.getInt("click_limit", 100),1,10000);
        clickUnlimited = p.getBoolean("click_unlimited", false);
        swipeLimit = clamp(p.getInt("swipe_limit",100),1,10000);
        swipeUnlimited = p.getBoolean("swipe_unlimited",false);
        swipe1DurationMs = clamp(p.getInt("swipe1_duration",350),80,3000);
        swipe2DurationMs = clamp(p.getInt("swipe2_duration",350),80,3000);
        swipeGapMs = clamp(p.getInt("swipe_gap",120),0,5000);
        swipeFirstPairFirst = p.getBoolean("swipe_order_12_first",true);
        sx[0] = p.getInt("swipe_sx",0); sy[0] = p.getInt("swipe_sy",0);
        sx[1] = p.getInt("swipe_ex",0); sy[1] = p.getInt("swipe_ey",0);
        sx[2] = p.getInt("swipe3x",0); sy[2] = p.getInt("swipe3y",0);
        sx[3] = p.getInt("swipe4x",0); sy[3] = p.getInt("swipe4y",0);
        for(int i=0;i<4;i++){ sx[i]=clamp(sx[i],0,Math.max(0,screenW-1)); sy[i]=clamp(sy[i],0,Math.max(0,screenH-1)); }
    }

    private void savePoints() {
        SharedPreferences.Editor e=getSharedPreferences(PREFS,MODE_PRIVATE).edit();
        e.putInt("point_count",points.size()).putInt("next_id",nextPointId);
        for(int i=0;i<MAX_POINTS;i++){
            if(i<points.size()){
                ClickPoint p=points.get(i);
                e.putInt("p"+i+"id",p.id).putInt("p"+i+"x",p.x).putInt("p"+i+"y",p.y).putInt("p"+i+"i",p.intervalMs);
            } else e.remove("p"+i+"id").remove("p"+i+"x").remove("p"+i+"y").remove("p"+i+"i");
        }
        e.apply();
    }

    private void loadPoints() {
        SharedPreferences s=getSharedPreferences(PREFS,MODE_PRIVATE);
        int count=clamp(s.getInt("point_count",0),0,MAX_POINTS);
        nextPointId=Math.max(1,s.getInt("next_id",1));
        for(int i=0;i<count;i++){
            ClickPoint p=new ClickPoint();
            p.id=s.getInt("p"+i+"id",i+1);
            p.x=clamp(s.getInt("p"+i+"x",screenW/2),0,screenW-1);
            p.y=clamp(s.getInt("p"+i+"y",screenH/2),0,screenH-1);
            p.intervalMs=clamp(s.getInt("p"+i+"i",100),10,600000);
            points.add(p);
        }
        if(!points.isEmpty()) selectedPointId=points.get(0).id;
    }

    // -------------------- HELPERS --------------------

    private TextView label(String text,int sp,int color){ TextView t=new TextView(this); t.setText(text); t.setTextColor(color); t.setTextSize(sp); t.setGravity(Gravity.CENTER); return t; }
    private Button button(String text){ Button b=new Button(this); b.setText(text); b.setTextSize(10); b.setMinHeight(0); b.setMinimumHeight(0); return b; }
    private Button small(String text){ Button b=button(text); b.setTextSize(9); b.setMinWidth(0); b.setMinimumWidth(0); return b; }
    private Button modeSmall(String text,int target){ Button b=small(text); b.setOnClickListener(v->setMode(target)); return b; }
    private LinearLayout row(){ LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER); return r; }
    private LinearLayout.LayoutParams full(int h){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,h); p.setMargins(0,dp(1),0,dp(1)); return p; }
    private LinearLayout.LayoutParams cell(int h){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,h,1f); p.setMargins(dp(1),dp(1),dp(1),dp(1)); return p; }

    private TextView marker(String text,int color){
        TextView m=new TextView(this); m.setText(text); m.setTextColor(Color.WHITE); m.setTextSize(13); m.setGravity(Gravity.CENTER); m.setTypeface(null,android.graphics.Typeface.BOLD);
        GradientDrawable g=new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(Color.argb(235,Color.red(color),Color.green(color),Color.blue(color))); g.setStroke(dp(3),Color.WHITE); m.setBackground(g); return m;
    }

    private WindowManager.LayoutParams overlayLp(int w,int h,int x,int y,boolean notTouchable){
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if(notTouchable) flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,flags,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START; lp.x=x; lp.y=y; return lp;
    }

    private void updateScreenSize(){ DisplayMetrics d=getResources().getDisplayMetrics(); screenW=d.widthPixels; screenH=d.heightPixels; }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    private int clamp(int v,int min,int max){ return Math.max(min,Math.min(max,v)); }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt(){ stopAutomation("Interrompido"); }

    @Override public void onDestroy(){
        running=false; handler.removeCallbacksAndMessages(null);
        if(captureOverlay!=null&&wm!=null) try{wm.removeView(captureOverlay);}catch(Exception ignored){}
        for(ClickPoint p:points) if(p.marker!=null&&wm!=null) try{wm.removeView(p.marker);}catch(Exception ignored){}
        for(TextView m:swipeMarkers) if(m!=null&&wm!=null) try{wm.removeView(m);}catch(Exception ignored){}
        if(swipeLines!=null&&wm!=null) try{wm.removeView(swipeLines);}catch(Exception ignored){}
        if(mini!=null&&wm!=null) try{wm.removeView(mini);}catch(Exception ignored){}
        if(root!=null&&wm!=null) try{wm.removeView(root);}catch(Exception ignored){}
        super.onDestroy();
    }
}
