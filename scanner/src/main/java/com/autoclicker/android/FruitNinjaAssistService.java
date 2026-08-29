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
    private int safetyMarginDp = 72;
    private boolean gestureBusy;
    private long lastFrameTimestamp;

    // A versão anterior dependia apenas do último AccessibilityEvent.
    // Overlays, notificações e System UI podiam sobrescrever o pacote ativo,
    // fazendo o modo ficar ligado sem executar cortes. Agora combinamos:
    // raiz da janela ativa + lista de janelas + último evento real do Fruit Ninja.
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
                getSharedPreferences(PREFS, MODE_PRIVATE).getInt("margin_dp", 72),
                30, 160);
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
            safetyMarginDp = clamp(safetyMarginDp - 10, 30, 160);
            savePrefs();
            refreshButtons();
        });
        marginRow.addView(minus, cell(dp(36)));
        Button plus = button("+10");
        plus.setOnClickListener(v -> {
            safetyMarginDp = clamp(safetyMarginDp + 10, 30, 160);
            savePrefs();
            refreshButtons();
        });
        marginRow.addView(plus, cell(dp(36)));
        panel.addView(marginRow, full(dp(38)));

        Button test = button("TESTAR GESTO");
        test.setOnClickListener(v -> testAccessibilityGesture());
        panel.addView(test, full(dp(40)));

        autoButton = button("");
        autoButton.setOnClickListener(v -> {
            autoCut = !autoCut;
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
                dp(245),
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
                    ? "MODO: SEGURO • EVITA MAIS AS BOMBAS"
                    : "MODO: AGRESSIVO • MAIS FRUTAS");
        }
        if (marginText != null) {
            marginText.setText("MARGEM EXTRA DA BOMBA: " + safetyMarginDp + " dp");
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
                        performSafeCuts(result);
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
            statusText.setText("Cortando frutas seguras...");
        } else {
            FruitNinjaDetector.Result r = FruitNinjaBus.latestResult;
            if (r == null) {
                statusText.setText("Analisando a tela...");
            } else {
                String mode = safeMode ? "SEGURO" : "AGRESSIVO";
                statusText.setText(
                        mode + " • " + r.fruits.size() + " fruta(s) • "
                                + r.bombs.size() + " bomba(s)");
            }
        }
    }

    private void testAccessibilityGesture() {
        if (gestureBusy) return;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float w = Math.max(1, dm.widthPixels);
        float h = Math.max(1, dm.heightPixels);

        Path path = new Path();
        path.moveTo(w * 0.38f, h * 0.82f);
        path.lineTo(w * 0.62f, h * 0.82f);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 160))
                .build();

        gestureBusy = true;
        showTemporaryDetectorMessage("TESTANDO GESTO...", 1800);
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

    private void performSafeCuts(FruitNinjaDetector.Result result) {
        if (result.fruits == null || result.fruits.isEmpty()) return;

        cleanupRecentCuts();
        final long lookAheadMs = safeMode ? 150L : 90L;
        final float marginPx = dp(safetyMarginDp);
        final int maxCuts = safeMode ? 3 : 5;
        final long now = SystemClock.uptimeMillis();

        GestureDescription.Builder builder = new GestureDescription.Builder();
        int added = 0;

        for (FruitNinjaLogic.Fruit fruit : result.fruits) {
            if (added >= maxCuts) break;
            if (wasRecentlyCut(fruit, now)) continue;

            float[] slice = FruitNinjaLogic.chooseSafeSlice(
                    fruit,
                    result.bombs,
                    lookAheadMs,
                    marginPx,
                    Math.max(dp(20), fruit.size * 0.46f),
                    result.width,
                    result.height);
            if (slice == null) continue;

            if (!FruitNinjaLogic.segmentClear(
                    slice[0], slice[1], slice[2], slice[3],
                    result.bombs,
                    lookAheadMs,
                    marginPx + dp(safeMode ? 8 : 2))) {
                continue;
            }

            Path p = new Path();
            p.moveTo(slice[0], slice[1]);
            p.lineTo(slice[2], slice[3]);
            long duration = safeMode ? 62 : 45;
            builder.addStroke(new GestureDescription.StrokeDescription(p, 0, duration));
            recentCuts.add(new RecentCut(fruit.x, fruit.y, now));
            added++;
        }

        if (added == 0) return;

        gestureBusy = true;
        boolean accepted = dispatchGesture(
                builder.build(),
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

    private boolean wasRecentlyCut(FruitNinjaLogic.Fruit fruit, long now) {
        float gate = Math.max(dp(28), fruit.size * 0.85f);
        float gate2 = gate * gate;
        for (RecentCut c : recentCuts) {
            if (now - c.at > 260) continue;
            float dx = fruit.x - c.x;
            float dy = fruit.y - c.y;
            if (dx * dx + dy * dy <= gate2) return true;
        }
        return false;
    }

    private void cleanupRecentCuts() {
        long cutoff = SystemClock.uptimeMillis() - 450;
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
