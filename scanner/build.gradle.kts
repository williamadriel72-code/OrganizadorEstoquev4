plugins {
    id("com.android.application")
}

android {
    namespace = "com.autoclicker.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mastertools.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.3.0"
    }
}

val addMasterQuickModes = tasks.register("addMasterQuickModes") {
    doLast {
        val service = file("src/main/java/com/autoclicker/android/AutoClickService.java")
        var text = service.readText()

        if (!text.contains("MASTER_QUICK_MODES_V110")) {
            val oldBlock = """        modeButton = button("MODO");
        modeButton.setOnClickListener(v -> cycleMode());
        panel.addView(modeButton, buttonParams());

        clickControls = new LinearLayout(this);"""

            val newBlock = """        modeButton = button("MODO");
        modeButton.setOnClickListener(v -> cycleMode());
        panel.addView(modeButton, buttonParams());

        // MASTER_QUICK_MODES_V110
        LinearLayout quickModes1 = row();
        Button quickClick = smallButton("AUTOCLICK");
        quickClick.setOnClickListener(v -> setMode(MODE_CLICK));
        quickModes1.addView(quickClick, smallParams());
        Button quickSwipe = smallButton("DESLIZE");
        quickSwipe.setOnClickListener(v -> setMode(MODE_SWIPE));
        quickModes1.addView(quickSwipe, smallParams());
        Button quickDiagonal = smallButton("DIAGONAL");
        quickDiagonal.setOnClickListener(v -> setMode(MODE_DIAGONAL));
        quickModes1.addView(quickDiagonal, smallParams());
        panel.addView(quickModes1, fullWidth(dp(44)));

        LinearLayout quickModes2 = row();
        Button quickDual = button("2 HORIZONTAIS");
        quickDual.setOnClickListener(v -> setMode(MODE_DUAL));
        quickModes2.addView(quickDual, rowButtonParams());
        Button quickNinja = button("NINJA");
        quickNinja.setOnClickListener(v -> setMode(MODE_NINJA));
        quickModes2.addView(quickNinja, rowButtonParams());
        panel.addView(quickModes2, fullWidth(dp(46)));

        clickControls = new LinearLayout(this);"""

            if (!text.contains(oldBlock)) {
                throw GradleException("Bloco do seletor de modo não encontrado em AutoClickService.java")
            }

            text = text.replace(oldBlock, newBlock)
            service.writeText(text)
        }
    }
}

val removeVoiceControl = tasks.register("removeVoiceControl") {
    dependsOn(addMasterQuickModes)
    doLast {
        val service = file("src/main/java/com/autoclicker/android/AutoClickService.java")
        var text = service.readText()

        if (!text.contains("VOICE_REMOVED_V120")) {
            val connectOld = """        setupSpeechRecognizer();
        showPanel();
        showAllMarkers();
        handler.postDelayed(() -> {
            if (continuous && !workflowActive) startListening();
        }, 800);"""
            val connectNew = """        // VOICE_REMOVED_V120
        showPanel();
        showAllMarkers();"""
            if (!text.contains(connectOld)) {
                throw GradleException("Bloco de inicializacao de voz nao encontrado")
            }
            text = text.replace(connectOld, connectNew)

            val panelVoiceOld = """        voiceStatus = new TextView(this);
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

"""
            if (!text.contains(panelVoiceOld)) {
                throw GradleException("Controles de voz do painel nao encontrados")
            }
            text = text.replace(panelVoiceOld, "")

            text = text.replace("miniBubble.setText(listening ? \"●\" : \"🎤\");", "miniBubble.setText(\"≡\");")
            text = text.replace("Toast.makeText(this, \"Toque no 🎤 para ouvir • segure para abrir o painel\", Toast.LENGTH_SHORT).show();", "Toast.makeText(this, \"Toque para abrir o painel Master Tools\", Toast.LENGTH_SHORT).show();")
            text = text.replace("if (listening) stopListening(); else startListening();", "restorePanel();")

            service.writeText(text)
        }
    }
}

val enhancePanelAndSwipe = tasks.register("enhancePanelAndSwipe") {
    dependsOn(removeVoiceControl)
    doLast {
        val service = file("src/main/java/com/autoclicker/android/AutoClickService.java")
        var text = service.readText()

        if (!text.contains("CUSTOM_SWIPE_POINTS_V130")) {
            text = text.replace(
                "import android.graphics.Color;\n",
                "import android.graphics.Color;\nimport android.graphics.Canvas;\nimport android.graphics.Paint;\n"
            )

            val fieldsOld = """    private LinearLayout speedControls;

    private final Handler handler"""
            val fieldsNew = """    private LinearLayout speedControls;

    // CUSTOM_SWIPE_POINTS_V130
    private TextView swipeRepeatText;
    private Button swipeUnlimitedButton;
    private TextView swipeStartMarker;
    private TextView swipeEndMarker;
    private WindowManager.LayoutParams swipeStartParams;
    private WindowManager.LayoutParams swipeEndParams;
    private SwipeLineView swipeLineView;
    private WindowManager.LayoutParams swipeLineParams;
    private int swipeStartX;
    private int swipeStartY;
    private int swipeEndX;
    private int swipeEndY;
    private int swipeGestureLimit = 100;
    private boolean swipeUnlimited;
    private float swipeDragRawX;
    private float swipeDragRawY;
    private int swipeDragStartX;
    private int swipeDragStartY;

    private final Handler handler"""
            if (!text.contains(fieldsOld)) throw GradleException("Campos de automacao nao encontrados")
            text = text.replace(fieldsOld, fieldsNew)

            val classAnchor = """    @Override
    protected void onServiceConnected()"""
            val lineClass = """    private class SwipeLineView extends View {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SwipeLineView() {
            super(AutoClickService.this);
            linePaint.setColor(Color.argb(225, 0, 210, 255));
            linePaint.setStrokeWidth(dp(4));
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float sx = swipeStartX;
            float sy = swipeStartY;
            float ex = swipeEndX;
            float ey = swipeEndY;
            canvas.drawLine(sx, sy, ex, ey, linePaint);

            float dx = ex - sx;
            float dy = ey - sy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 1f) {
                float ux = dx / len;
                float uy = dy / len;
                float px = -uy;
                float py = ux;
                float arrow = dp(16);
                float wing = dp(8);
                canvas.drawLine(ex, ey, ex - ux * arrow + px * wing, ey - uy * arrow + py * wing, linePaint);
                canvas.drawLine(ex, ey, ex - ux * arrow - px * wing, ey - uy * arrow - py * wing, linePaint);
            }
        }
    }

    @Override
    protected void onServiceConnected()"""
            if (!text.contains(classAnchor)) throw GradleException("Ancora da classe de gesto nao encontrada")
            text = text.replace(classAnchor, lineClass)

            text = text.replace(
                "        showAllMarkers();\n",
                "        showAllMarkers();\n        showSwipeMarkers();\n"
            )

            text = text.replace(
                "panel.setPadding(dp(10), dp(8), dp(10), dp(12));",
                "panel.setPadding(dp(6), dp(5), dp(6), dp(7));"
            )

            val titleOld = """        TextView title = new TextView(this);
        title.setText("MASTER TOOLS  •  ARRASTE AQUI");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setOnTouchListener((v, e) -> dragPanel(e));
        panel.addView(title, fullWidth(dp(42)));"""
            val titleNew = """        LinearLayout dragHeader = new LinearLayout(this);
        dragHeader.setOrientation(LinearLayout.VERTICAL);
        dragHeader.setGravity(Gravity.CENTER);
        dragHeader.setPadding(dp(4), 0, dp(4), 0);
        dragHeader.setOnTouchListener((v, e) -> dragPanel(e));

        TextView title = new TextView(this);
        title.setText("MASTER TOOLS  •  ARRASTE");
        title.setTextColor(Color.WHITE);
        title.setTextSize(12);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setOnTouchListener((v, e) -> dragPanel(e));
        dragHeader.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        panel.addView(dragHeader, fullWidth(dp(32)));"""
            if (!text.contains(titleOld)) throw GradleException("Cabecalho do painel nao encontrado")
            text = text.replace(titleOld, titleNew)

            text = text.replace(
                "        divider.setGravity(Gravity.CENTER);\n        panel.addView(divider, fullWidth(dp(38)));",
                "        divider.setGravity(Gravity.CENTER);\n        divider.setOnTouchListener((v, e) -> dragPanel(e));\n        panel.addView(divider, fullWidth(dp(30)));"
            )

            val swipeOld = """        swipeControls = new LinearLayout(this);
        swipeControls.setOrientation(LinearLayout.VERTICAL);
        directionButton = button("DIREÇÃO");
        directionButton.setOnClickListener(v -> cycleSwipeDirection());
        swipeControls.addView(directionButton, buttonParams());
        panel.addView(swipeControls);"""
            val swipeNew = """        swipeControls = new LinearLayout(this);
        swipeControls.setOrientation(LinearLayout.VERTICAL);

        TextView swipeHint = new TextView(this);
        swipeHint.setText("1 = INÍCIO   •   2 = FIM\nArraste as bolinhas na tela");
        swipeHint.setTextColor(Color.WHITE);
        swipeHint.setTextSize(10);
        swipeHint.setGravity(Gravity.CENTER);
        swipeControls.addView(swipeHint, fullWidth(dp(38)));

        LinearLayout swipePointRow = row();
        Button resetSwipe = smallButton("CENTRALIZAR");
        resetSwipe.setOnClickListener(v -> resetSwipePoints());
        swipePointRow.addView(resetSwipe, smallParams());
        Button swapSwipe = smallButton("INVERTER");
        swapSwipe.setOnClickListener(v -> swapSwipePoints());
        swipePointRow.addView(swapSwipe, smallParams());
        swipeControls.addView(swipePointRow, fullWidth(dp(36)));

        swipeRepeatText = new TextView(this);
        swipeRepeatText.setTextColor(Color.WHITE);
        swipeRepeatText.setTextSize(10);
        swipeRepeatText.setGravity(Gravity.CENTER);
        swipeControls.addView(swipeRepeatText, fullWidth(dp(28)));

        LinearLayout swipeRepeatRow = row();
        Button swipeMinus = smallButton("−10");
        swipeMinus.setOnClickListener(v -> adjustSwipeGestureLimit(-10));
        swipeRepeatRow.addView(swipeMinus, smallParams());
        Button swipePlus = smallButton("+10");
        swipePlus.setOnClickListener(v -> adjustSwipeGestureLimit(10));
        swipeRepeatRow.addView(swipePlus, smallParams());
        swipeUnlimitedButton = smallButton("ILIMITADO");
        swipeUnlimitedButton.setOnClickListener(v -> {
            if (automationRunning) return;
            swipeUnlimited = !swipeUnlimited;
            saveSettings();
            refreshAutomationUi();
        });
        LinearLayout.LayoutParams swipeUnlimitedLp = new LinearLayout.LayoutParams(0, dp(34), 1.4f);
        swipeUnlimitedLp.setMargins(dp(1), 0, dp(1), 0);
        swipeRepeatRow.addView(swipeUnlimitedButton, swipeUnlimitedLp);
        swipeControls.addView(swipeRepeatRow, fullWidth(dp(36)));

        panel.addView(swipeControls);"""
            if (!text.contains(swipeOld)) throw GradleException("Controles antigos de deslize nao encontrados")
            text = text.replace(swipeOld, swipeNew)

            text = text.replace(
                "int height = Math.min(dp(690), Math.max(dp(420), dm.heightPixels - dp(80)));",
                "int height = Math.min(dp(560), Math.max(dp(350), dm.heightPixels - dp(90)));"
            )
            text = text.replace("                dp(292), height,", "                dp(254), height,")
            text = text.replace("                dp(58), dp(58),", "                dp(48), dp(48),")
            text = text.replace("miniBubble.setTextSize(23);", "miniBubble.setTextSize(18);")

            text = text.replace("panel.addView(quickModes1, fullWidth(dp(44)));", "panel.addView(quickModes1, fullWidth(dp(38)));")
            text = text.replace("panel.addView(quickModes2, fullWidth(dp(46)));", "panel.addView(quickModes2, fullWidth(dp(40)));")
            text = text.replace("panel.addView(autoStatus, fullWidth(dp(42)));", "panel.addView(autoStatus, fullWidth(dp(34)));")
            text = text.replace("clickControls.addView(selectedPointText, fullWidth(dp(36)));", "clickControls.addView(selectedPointText, fullWidth(dp(30)));")
            text = text.replace("speedControls.addView(speedText, fullWidth(dp(32)));", "speedControls.addView(speedText, fullWidth(dp(28)));")

            val refreshAnchor = """        for (ClickPoint p : points) {
            if (p.marker != null) {
                p.marker.setVisibility(mode == MODE_CLICK ? View.VISIBLE : View.GONE);
                if (mode == MODE_CLICK) setPointTouchable(p, !automationRunning);
            }
        }

        if (autoStatus != null && !automationRunning) {"""
            val refreshNew = """        for (ClickPoint p : points) {
            if (p.marker != null) {
                p.marker.setVisibility(mode == MODE_CLICK ? View.VISIBLE : View.GONE);
                if (mode == MODE_CLICK) setPointTouchable(p, !automationRunning);
            }
        }

        boolean swipeVisible = mode == MODE_SWIPE;
        setSwipeMarkersVisible(swipeVisible);
        setSwipeMarkersTouchable(swipeVisible && !automationRunning);
        if (swipeRepeatText != null) {
            swipeRepeatText.setText(swipeUnlimited ? "Repetições: ILIMITADO" : "Repetições: " + swipeGestureLimit);
        }
        if (swipeUnlimitedButton != null) {
            swipeUnlimitedButton.setText(swipeUnlimited ? "LIMITADO" : "ILIMITADO");
            swipeUnlimitedButton.setEnabled(!automationRunning);
        }

        if (autoStatus != null && !automationRunning) {"""
            if (!text.contains(refreshAnchor)) throw GradleException("Atualizacao visual dos pontos nao encontrada")
            text = text.replace(refreshAnchor, refreshNew)

            val startTouchAnchor = """        for (ClickPoint p : points) {
            p.count = 0;
            p.nextAt = now;
            setPointTouchable(p, false);
        }
        refreshAutomationUi();"""
            val startTouchNew = """        for (ClickPoint p : points) {
            p.count = 0;
            p.nextAt = now;
            setPointTouchable(p, false);
        }
        setSwipeMarkersTouchable(false);
        refreshAutomationUi();"""
            if (!text.contains(startTouchAnchor)) throw GradleException("Inicio da automacao nao encontrado")
            text = text.replace(startTouchAnchor, startTouchNew)

            text = text.replace(
                "        for (ClickPoint p : points) setPointTouchable(p, true);\n        if (autoStatus != null) autoStatus.setText(message);",
                "        for (ClickPoint p : points) setPointTouchable(p, true);\n        setSwipeMarkersTouchable(mode == MODE_SWIPE);\n        if (autoStatus != null) autoStatus.setText(message);"
            )

            val completedOld = """                    autoCycle++;
                    if (mode == MODE_DIAGONAL) diagonalForward = !diagonalForward;
                    if (mode == MODE_NINJA) ninjaPhase += 0.37;
                    if (autoStatus != null) autoStatus.setText(modeName() + " • " + autoCycle + " gesto(s)");
                    handler.postDelayed(automationRunnable, 18);"""
            val completedNew = """                    autoCycle++;
                    if (mode == MODE_SWIPE && !swipeUnlimited && autoCycle >= swipeGestureLimit) {
                        stopAutomation("Concluído • " + autoCycle + " deslize(s)");
                        return;
                    }
                    if (mode == MODE_DIAGONAL) diagonalForward = !diagonalForward;
                    if (mode == MODE_NINJA) ninjaPhase += 0.37;
                    if (autoStatus != null) autoStatus.setText(modeName() + " • " + autoCycle + " gesto(s)");
                    handler.postDelayed(automationRunnable, 18);"""
            if (!text.contains(completedOld)) throw GradleException("Callback de gesto nao encontrado")
            text = text.replace(completedOld, completedNew)

            val fixedSwipeOld = """        } else if (mode == MODE_SWIPE) {
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
            }"""
            val customSwipeNew = """        } else if (mode == MODE_SWIPE) {
            path.moveTo(swipeStartX, swipeStartY);
            path.lineTo(swipeEndX, swipeEndY);"""
            if (!text.contains(fixedSwipeOld)) throw GradleException("Gesto fixo de deslize nao encontrado")
            text = text.replace(fixedSwipeOld, customSwipeNew)

            val saveOld = """                .putInt("click_limit", clickLimit)
                .putBoolean("unlimited", unlimitedClicks)
                .apply();"""
            val saveNew = """                .putInt("click_limit", clickLimit)
                .putBoolean("unlimited", unlimitedClicks)
                .putInt("swipe_start_x", swipeStartX)
                .putInt("swipe_start_y", swipeStartY)
                .putInt("swipe_end_x", swipeEndX)
                .putInt("swipe_end_y", swipeEndY)
                .putInt("swipe_gesture_limit", swipeGestureLimit)
                .putBoolean("swipe_unlimited", swipeUnlimited)
                .apply();"""
            if (!text.contains(saveOld)) throw GradleException("Preferencias da automacao nao encontradas")
            text = text.replace(saveOld, saveNew)

            val loadOld = """        clickLimit = clamp(prefs.getInt("click_limit", 100), 1, 10000);
        unlimitedClicks = prefs.getBoolean("unlimited", false);
    }"""
            val loadNew = """        clickLimit = clamp(prefs.getInt("click_limit", 100), 1, 10000);
        unlimitedClicks = prefs.getBoolean("unlimited", false);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        swipeStartX = clamp(prefs.getInt("swipe_start_x", Math.round(dm.widthPixels * 0.30f)), 0, dm.widthPixels - 1);
        swipeStartY = clamp(prefs.getInt("swipe_start_y", Math.round(dm.heightPixels * 0.65f)), 0, dm.heightPixels - 1);
        swipeEndX = clamp(prefs.getInt("swipe_end_x", Math.round(dm.widthPixels * 0.70f)), 0, dm.widthPixels - 1);
        swipeEndY = clamp(prefs.getInt("swipe_end_y", Math.round(dm.heightPixels * 0.35f)), 0, dm.heightPixels - 1);
        swipeGestureLimit = clamp(prefs.getInt("swipe_gesture_limit", 100), 1, 10000);
        swipeUnlimited = prefs.getBoolean("swipe_unlimited", false);
    }"""
            if (!text.contains(loadOld)) throw GradleException("Leitura das preferencias nao encontrada")
            text = text.replace(loadOld, loadNew)

            val pointSection = """    // -------------------- PONTOS DO AUTOCLICKER --------------------"""
            val swipeMethods = """    // -------------------- PONTOS DO DESLIZE --------------------

    private void adjustSwipeGestureLimit(int delta) {
        if (automationRunning) return;
        swipeGestureLimit = clamp(swipeGestureLimit + delta, 1, 10000);
        saveSettings();
        refreshAutomationUi();
    }

    private void resetSwipePoints() {
        if (automationRunning) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        swipeStartX = Math.round(dm.widthPixels * 0.30f);
        swipeStartY = Math.round(dm.heightPixels * 0.65f);
        swipeEndX = Math.round(dm.widthPixels * 0.70f);
        swipeEndY = Math.round(dm.heightPixels * 0.35f);
        moveSwipeMarker(true);
        moveSwipeMarker(false);
        if (swipeLineView != null) swipeLineView.invalidate();
        saveSettings();
        refreshAutomationUi();
    }

    private void swapSwipePoints() {
        if (automationRunning) return;
        int tx = swipeStartX;
        int ty = swipeStartY;
        swipeStartX = swipeEndX;
        swipeStartY = swipeEndY;
        swipeEndX = tx;
        swipeEndY = ty;
        moveSwipeMarker(true);
        moveSwipeMarker(false);
        if (swipeLineView != null) swipeLineView.invalidate();
        saveSettings();
    }

    private void showSwipeMarkers() {
        if (windowManager == null) return;

        if (swipeLineView == null) {
            swipeLineView = new SwipeLineView();
            swipeLineParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            swipeLineParams.gravity = Gravity.TOP | Gravity.START;
            try { windowManager.addView(swipeLineView, swipeLineParams); }
            catch (Exception ignored) { swipeLineView = null; swipeLineParams = null; }
        }

        if (swipeStartMarker == null) swipeStartMarker = createSwipeMarker("1", true);
        if (swipeEndMarker == null) swipeEndMarker = createSwipeMarker("2", false);
        moveSwipeMarker(true);
        moveSwipeMarker(false);
        setSwipeMarkersVisible(mode == MODE_SWIPE);
        setSwipeMarkersTouchable(mode == MODE_SWIPE && !automationRunning);
    }

    private TextView createSwipeMarker(String label, boolean start) {
        int size = dp(42);
        TextView marker = new TextView(this);
        marker.setText(label);
        marker.setTextColor(Color.WHITE);
        marker.setTextSize(14);
        marker.setGravity(Gravity.CENTER);
        marker.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(start ? Color.argb(235, 25, 180, 80) : Color.argb(235, 255, 120, 20));
        bg.setStroke(dp(3), Color.WHITE);
        marker.setBackground(bg);
        marker.setOnTouchListener((v, e) -> handleSwipeMarkerTouch(start, e));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        if (start) swipeStartParams = params; else swipeEndParams = params;
        try { windowManager.addView(marker, params); }
        catch (Exception ignored) {
            if (start) swipeStartParams = null; else swipeEndParams = null;
            return null;
        }
        return marker;
    }

    private boolean handleSwipeMarkerTouch(boolean start, MotionEvent event) {
        if (automationRunning) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                swipeDragRawX = event.getRawX();
                swipeDragRawY = event.getRawY();
                swipeDragStartX = start ? swipeStartX : swipeEndX;
                swipeDragStartY = start ? swipeStartY : swipeEndY;
                return true;
            case MotionEvent.ACTION_MOVE:
                DisplayMetrics dm = getResources().getDisplayMetrics();
                int nx = swipeDragStartX + Math.round(event.getRawX() - swipeDragRawX);
                int ny = swipeDragStartY + Math.round(event.getRawY() - swipeDragRawY);
                nx = clamp(nx, 0, dm.widthPixels - 1);
                ny = clamp(ny, 0, dm.heightPixels - 1);
                if (start) {
                    swipeStartX = nx;
                    swipeStartY = ny;
                } else {
                    swipeEndX = nx;
                    swipeEndY = ny;
                }
                moveSwipeMarker(start);
                if (swipeLineView != null) swipeLineView.invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                saveSettings();
                return true;
            default:
                return true;
        }
    }

    private void moveSwipeMarker(boolean start) {
        TextView marker = start ? swipeStartMarker : swipeEndMarker;
        WindowManager.LayoutParams params = start ? swipeStartParams : swipeEndParams;
        if (marker == null || params == null) return;
        int x = start ? swipeStartX : swipeEndX;
        int y = start ? swipeStartY : swipeEndY;
        params.x = x - params.width / 2;
        params.y = y - params.height / 2;
        try { windowManager.updateViewLayout(marker, params); } catch (Exception ignored) { }
    }

    private void setSwipeMarkersVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (swipeStartMarker != null) swipeStartMarker.setVisibility(visibility);
        if (swipeEndMarker != null) swipeEndMarker.setVisibility(visibility);
        if (swipeLineView != null) {
            swipeLineView.setVisibility(visibility);
            if (visible) swipeLineView.invalidate();
        }
    }

    private void setSwipeMarkersTouchable(boolean touchable) {
        updateSwipeMarkerTouchable(swipeStartMarker, swipeStartParams, touchable);
        updateSwipeMarkerTouchable(swipeEndMarker, swipeEndParams, touchable);
    }

    private void updateSwipeMarkerTouchable(TextView marker, WindowManager.LayoutParams params, boolean touchable) {
        if (marker == null || params == null) return;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        params.flags = flags;
        try { windowManager.updateViewLayout(marker, params); } catch (Exception ignored) { }
    }

    // -------------------- PONTOS DO AUTOCLICKER --------------------"""
            if (!text.contains(pointSection)) throw GradleException("Secao de pontos nao encontrada")
            text = text.replace(pointSection, swipeMethods)

            val buttonHelpersOld = """    private Button button(String text) {
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
    }"""
            val buttonHelpersNew = """    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(10);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(9);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }"""
            if (!text.contains(buttonHelpersOld)) throw GradleException("Helpers de botoes nao encontrados")
            text = text.replace(buttonHelpersOld, buttonHelpersNew)
            text = text.replace("return fullWidth(dp(48));", "return fullWidth(dp(40));")
            text = text.replace("new LinearLayout.LayoutParams(0, dp(46), 1f)", "new LinearLayout.LayoutParams(0, dp(38), 1f)")
            text = text.replace("new LinearLayout.LayoutParams(0, dp(40), 1f)", "new LinearLayout.LayoutParams(0, dp(34), 1f)")

            val destroyAnchor = """        removeMiniBubble();
        if (panelRoot != null && windowManager != null) {"""
            val destroyNew = """        if (swipeStartMarker != null && windowManager != null) {
            try { windowManager.removeView(swipeStartMarker); } catch (Exception ignored) { }
            swipeStartMarker = null;
        }
        if (swipeEndMarker != null && windowManager != null) {
            try { windowManager.removeView(swipeEndMarker); } catch (Exception ignored) { }
            swipeEndMarker = null;
        }
        if (swipeLineView != null && windowManager != null) {
            try { windowManager.removeView(swipeLineView); } catch (Exception ignored) { }
            swipeLineView = null;
        }

        removeMiniBubble();
        if (panelRoot != null && windowManager != null) {"""
            if (!text.contains(destroyAnchor)) throw GradleException("Limpeza do painel nao encontrada")
            text = text.replace(destroyAnchor, destroyNew)

            service.writeText(text)
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(enhancePanelAndSwipe)
}
