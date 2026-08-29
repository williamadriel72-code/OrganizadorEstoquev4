package com.autoclicker.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
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
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FruitNinjaAssistService extends AccessibilityService {
    private static final String FRUIT_NINJA_PACKAGE = "com.halfbrick.fruitninjafree";
    private static final String PREFS = "fruit_guard_prefs";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelLp;
    private TextView mini;
    private WindowManager.LayoutParams miniLp;

    private TextView statusText;
    private TextView detectorText;
    private TextView marginText;
    private Button autoButton;
    private Button modeButton;

    private boolean safeMode = true;
    private boolean autoCut;
    private int safetyMarginDp = 110;
    private boolean gestureBusy;
    private long lastFrameTimestamp;

    private long lastFruitNinjaSeenAt;
    private long lastForegroundCheckAt;
    private boolean cachedFruitNinjaForeground;

    private long temporaryDetectorMessageUntil;
    private String temporaryDetectorMessage = "";

    private float dragDownX, dragDownY;
    private int dragStartX, dragStartY;

    private float miniDownX, miniDownY;
    private int miniStartX, miniStartY;
    private boolean miniMoved;

    private static final class RecentCut {
        float x, y;
        long at;
        RecentCut(float x, float y, long at) {
            this.x = x;
            this.y = y;
            this.at = at;
        }
    }

    private final ArrayList<RecentCut> recentCuts = new ArrayList<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        safeMode = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("safe_mode", true);
        autoCut = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("auto_cut", false);
        safetyMarginDp = clamp(
                getSharedPreferences(PREFS, MODE_PRIVATE).getInt("margin_dp", 110),
                70, 220);
        showPanel();
        handler.post(loop);
    }

    private void showPanel() {
        if (wm == null || panel != null) return;

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(7), dp(6), dp(7), dp(7));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(238, 8, 20, 12));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(2), Color.rgb(62, 205, 82));
        panel.setBackground(bg);

        TextView header = label("🍉 FRUIT GUARD • ARRASTE", 12, Color.WHITE);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setOnTouchListener((v, e) -> dragPanel(e));
        panel.addView(header, full(dp(38)));

        statusText = label("Aguardando Fruit Ninja", 10, Color.rgb(210, 255, 215));
        panel.addView(statusText, full(dp(34)));

        detectorText = label("Captura parada", 9, Color.LTGRAY);
        panel.addView(detectorText, full(dp(34)));

        TextView safeArea = label("UI PROTEGIDA • topo, bordas e rodapé bloqueados", 9,
                Color.rgb(255, 215, 120));
        panel.addView(safeArea, full(dp(30)));

        modeButton = button("");
        modeButton.setOnClickListener(v -> {
            if (gestureBusy) return;
            safeMode = !safeMode;
            savePrefs();
            refreshButtons();
        });
        panel.addView(modeButton, full(dp(40)));

        marginText = label("", 10, Color.WHITE);
        panel.addView(marginText, full(dp(27)));

        LinearLayout marginRow = row();
        Button minus = button("−10");
        minus.setOnClickListener(v -> {
            safetyMarginDp = clamp(safetyMarginDp - 10, 70, 220);
            savePrefs();
            refreshButtons();
        });
        marginRow.addView(minus, cell(dp(36)));
        Button plus = button("+10");
        plus.setOnClickListener(v -> {
            safetyMarginDp = clamp(safetyMarginDp + 10, 70, 220);
            savePrefs();
            refreshButtons();
        });
        marginRow.addView(plus, cell(dp(36)));
        panel.addView(marginRow, full(dp(38)));

        Button test = button("TESTAR GESTO SEGURO");
        test.setOnClickListener(v -> testAccessibilityGesture());
        panel.addView(test, full(dp(40)));

        autoButton = button("");
        autoButton.setOnClickListener(v -> {
            autoCut = !autoCut;
            if (autoCut) recentCuts.clear();
            savePrefs();
            refreshButtons();
        });
        panel.addView(autoButton, full(dp(42)));

        Button stop = button("PARAR CORTES");
        stop.setOnClickListener(v -> {
            autoCut = false;
            gestureBusy = false;
            savePrefs();
            refreshButtons();
        });
        panel.addView(stop, full(dp(40)));

        Button minimize = button("MINIMIZAR");
        minimize.setOnClickListener(v -> minimizePanel());
        panel.addView(minimize, full(dp(38)));

        panelLp = new WindowManager.LayoutParams(
                dp(250),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.START;
        panelLp.x = dp(8);
        panelLp.y = dp(60);
        wm.addView(panel, panelLp);
        refreshButtons();
    }

    private boolean dragPanel(MotionEvent e) {
        if (panelLp == null || panel == null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragDownX = e.getRawX();
                dragDownY = e.getRawY();
                dragStartX = panelLp.x;
                dragStartY = panelLp.y;
                return true;
            case MotionEvent.ACTION_MOVE:
                panelLp.x = Math.max(0, dragStartX + Math.round(e.getRawX() - dragDownX));
                panelLp.y = Math.max(0, dragStartY + Math.round(e.getRawY() - dragDownY));
                try { wm.updateViewLayout(panel, panelLp); } catch (Throwable ignored) {}
                return true;
            default:
                return true;
        }
    }

    private void minimizePanel() {
        if (panel == null || mini != null || wm == null) return;
        panel.setVisibility(View.GONE);
        mini = label("🍉", 20, Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.argb(245, 30, 150, 55));
        g.setStroke(dp(2), Color.WHITE);
        mini.setBackground(g);
        mini.setOnTouchListener((v, e) -> handleMini(e));

        miniLp = new WindowManager.LayoutParams(
                dp(48), dp(48),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        miniLp.gravity = Gravity.TOP | Gravity.START;
        miniLp.x = panelLp == null ? dp(8) : panelLp.x;
        miniLp.y = panelLp == null ? dp(60) : panelLp.y;
        try { wm.addView(mini, miniLp); } catch (Throwable ignored) { mini = null; }
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
                    miniLp.x = Math.max(0, miniStartX + Math.round(dx));
                    miniLp.y = Math.max(0, miniStartY + Math.round(dy));
                    try { wm.updateViewLayout(mini, miniLp); } catch (Throwable ignored) {}
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
        if (mini != null && wm != null) {
            try { wm.removeView(mini); } catch (Throwable ignored) {}
            mini = null;
            miniLp = null;
        }
        if (panel != null) panel.setVisibility(View.VISIBLE);
    }

    private void refreshButtons() {
        if (modeButton != null) {
            modeButton.setText(safeMode
                    ? "MODO: PROTEÇÃO MÁXIMA"
                    : "MODO: RÁPIDO • MAIS FRUTAS");
        }
        if (marginText != null) {
            marginText.setText("DISTÂNCIA DA BOMBA: " + safetyMarginDp + " dp");
        }
        if (autoButton != null) {
            autoButton.setText(autoCut
                    ? "CORTE AUTOMÁTICO: LIGADO"
                    : "CORTE AUTOMÁTICO: DESLIGADO");
        }
    }

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            try {
                boolean inGame = isFruitNinjaForeground();
                updateStatus(inGame);
                if (autoCut && !gestureBusy && FruitNinjaBus.captureRunning && inGame) {
                    FruitNinjaDetector.Result result = FruitNinjaBus.latestResult;
                    if (result != null && result.timestampMs != lastFrameTimestamp) {
                        lastFrameTimestamp = result.timestampMs;
                        performSafeCut(result);
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                handler.postDelayed(this, 18);
            }
        }
    };

    private boolean isFruitNinjaForeground() {
        long now = SystemClock.uptimeMillis();
        if (now - lastForegroundCheckAt < 140) {
            return cachedFruitNinjaForeground || now - lastFruitNinjaSeenAt < 1800;
        }
        lastForegroundCheckAt = now;

        boolean found = false;
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null
                    && FRUIT_NINJA_PACKAGE.contentEquals(root.getPackageName())) {
                found = true;
            }
        } catch (Throwable ignored) {}

        if (!found) {
            try {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo window : windows) {
                        if (window == null) continue;
                        if (!window.isActive() && !window.isFocused()) continue;
                        AccessibilityNodeInfo root = window.getRoot();
                        if (root == null || root.getPackageName() == null) continue;
                        if (FRUIT_NINJA_PACKAGE.contentEquals(root.getPackageName())) {
                            found = true;
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (found) lastFruitNinjaSeenAt = now;
        cachedFruitNinjaForeground = found || now - lastFruitNinjaSeenAt < 1800;
        return cachedFruitNinjaForeground;
    }

    private void updateStatus(boolean inGame) {
        if (detectorText != null) {
            long now = SystemClock.uptimeMillis();
            if (now < temporaryDetectorMessageUntil) {
                detectorText.setText(temporaryDetectorMessage);
            } else {
                detectorText.setText(FruitNinjaBus.captureStatus);
            }
        }
        if (statusText == null) return;

        if (!FruitNinjaBus.captureRunning) {
            statusText.setText("Abra o app e inicie a captura da tela");
        } else if (!inGame) {
            statusText.setText("Captura pronta • abra o Fruit Ninja");
        } else if (!autoCut) {
            statusText.setText("Fruit Ninja detectado • ative o corte");
        } else if (gestureBusy) {
            statusText.setText("Executando 1 corte seguro...");
        } else {
            FruitNinjaDetector.Result r = FruitNinjaBus.latestResult;
            if (r == null) {
                statusText.setText("Analisando a tela...");
            } else {
                String mode = safeMode ? "PROTEÇÃO" : "RÁPIDO";
                statusText.setText(
                        mode + " • " + r.fruits.size() + " fruta(s) • "
                                + r.bombs.size() + " perigo(s)");
            }
        }
    }

    private void testAccessibilityGesture() {
        if (gestureBusy) return;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float w = Math.max(1, dm.widthPixels);
        float h = Math.max(1, dm.heightPixels);

        Path path = new Path();
        path.moveTo(w * 0.42f, h * 0.74f);
        path.lineTo(w * 0.58f, h * 0.74f);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 140))
                .build();

        gestureBusy = true;
        showTemporaryDetectorMessage("TESTANDO GESTO SEGURO...", 1800);
        boolean accepted = dispatchGesture(
                gesture,
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        gestureBusy = false;
                        showTemporaryDetectorMessage("GESTO OK • Acessibilidade funcionando", 2600);
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        gestureBusy = false;
                        showTemporaryDetectorMessage("GESTO CANCELADO PELO ANDROID", 2600);
                    }
                },
                null);
        if (!accepted) {
            gestureBusy = false;
            showTemporaryDetectorMessage("GESTO RECUSADO • reative a Acessibilidade", 3000);
        }
    }

    private void showTemporaryDetectorMessage(String message, long durationMs) {
        temporaryDetectorMessage = message;
        temporaryDetectorMessageUntil = SystemClock.uptimeMillis() + durationMs;
        if (detectorText != null) detectorText.setText(message);
    }

    private void performSafeCut(FruitNinjaDetector.Result result) {
        if (result.fruits == null || result.fruits.isEmpty()) return;

        long now = SystemClock.uptimeMillis();
        // Nunca executa trajetória baseada em frame velho.
        if (now - result.timestampMs > 220) return;

        cleanupRecentCuts();

        final float left = result.width * 0.08f;
        final float right = result.width * 0.92f;
        final float top = result.height * 0.22f;
        final float bottom = result.height * 0.88f;

        final long lookAheadMs = safeMode ? 240L : 150L;
        final float baseMargin = dp(safetyMarginDp);
        final float bombMargin = baseMargin + dp(safeMode ? 38 : 20);
        final float confidenceMin = safeMode ? 0.62f : 0.54f;

        for (FruitNinjaLogic.Fruit fruit : result.fruits) {
            if (fruit == null || fruit.confidence < confidenceMin) continue;
            if (fruit.size < dp(12) || fruit.size > Math.min(result.width, result.height) * 0.26f) continue;
            if (wasRecentlyCut(fruit, now)) continue;
            if (!insideSafeArea(fruit.x, fruit.y, left, top, right, bottom)) continue;

            float halfLength = Math.max(dp(16), Math.min(dp(42), fruit.size * 0.38f));
            float[] slice = FruitNinjaLogic.chooseSafeSlice(
                    fruit,
                    result.bombs,
                    lookAheadMs,
                    bombMargin,
                    halfLength,
                    result.width,
                    result.height);
            if (slice == null) continue;

            // Trava absoluta: início, fim e centro da linha precisam permanecer na
            // área de jogo. Assim a automação não toca pausa, menu, barra do sistema
            // nem bordas usadas para voltar/sair.
            if (!insideSafeArea(slice[0], slice[1], left, top, right, bottom)
                    || !insideSafeArea(slice[2], slice[3], left, top, right, bottom)
                    || !insideSafeArea((slice[0] + slice[2]) * 0.5f,
                                       (slice[1] + slice[3]) * 0.5f,
                                       left, top, right, bottom)) {
                continue;
            }

            if (!FruitNinjaLogic.segmentClear(
                    slice[0], slice[1], slice[2], slice[3],
                    result.bombs,
                    lookAheadMs,
                    bombMargin + dp(safeMode ? 26 : 12))) {
                continue;
            }

            dispatchSingleCut(slice, fruit, now);
            return; // UM corte por vez. Evita bagunçar a tela com multitouch aleatório.
        }
    }

    private void dispatchSingleCut(float[] slice, FruitNinjaLogic.Fruit fruit, long now) {
        Path p = new Path();
        p.moveTo(slice[0], slice[1]);
        p.lineTo(slice[2], slice[3]);

        long duration = safeMode ? 72 : 55;
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, duration))
                .build();

        gestureBusy = true;
        recentCuts.add(new RecentCut(fruit.x, fruit.y, now));
        boolean accepted = dispatchGesture(
                gesture,
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        gestureBusy = false;
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        gestureBusy = false;
                    }
                },
                null);

        if (!accepted) {
            gestureBusy = false;
            showTemporaryDetectorMessage("Corte recusado pelo Android", 1600);
        }
    }

    private boolean insideSafeArea(float x, float y,
                                   float left, float top, float right, float bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private boolean wasRecentlyCut(FruitNinjaLogic.Fruit fruit, long now) {
        float gate = Math.max(dp(34), fruit.size * 1.05f);
        float gate2 = gate * gate;
        for (RecentCut c : recentCuts) {
            if (now - c.at > 360) continue;
            float dx = fruit.x - c.x;
            float dy = fruit.y - c.y;
            if (dx * dx + dy * dy <= gate2) return true;
        }
        return false;
    }

    private void cleanupRecentCuts() {
        long cutoff = SystemClock.uptimeMillis() - 650;
        Iterator<RecentCut> it = recentCuts.iterator();
        while (it.hasNext()) {
            if (it.next().at < cutoff) it.remove();
        }
    }

    private void savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean("safe_mode", safeMode)
                .putBoolean("auto_cut", autoCut)
                .putInt("margin_dp", safetyMarginDp)
                .apply();
    }

    private TextView label(String text, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(9);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private LinearLayout.LayoutParams full(int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        p.setMargins(0, dp(1), 0, dp(1));
        return p;
    }

    private LinearLayout.LayoutParams cell(int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, height, 1f);
        p.setMargins(dp(1), dp(1), dp(1), dp(1));
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = String.valueOf(event.getPackageName());
        if (FRUIT_NINJA_PACKAGE.equals(pkg)) {
            lastFruitNinjaSeenAt = SystemClock.uptimeMillis();
            cachedFruitNinjaForeground = true;
        }
    }

    @Override
    public void onInterrupt() {
        gestureBusy = false;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (mini != null && wm != null) {
            try { wm.removeView(mini); } catch (Throwable ignored) {}
        }
        if (panel != null && wm != null) {
            try { wm.removeView(panel); } catch (Throwable ignored) {}
        }
        mini = null;
        panel = null;
        super.onDestroy();
    }
}
