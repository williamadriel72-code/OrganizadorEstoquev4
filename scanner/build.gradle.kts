plugins {
    id("com.android.application")
}

android {
    namespace = "com.autoclicker.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.autoclicker.ninja"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.3"
    }
}

val configureDualHorizontal by tasks.registering {
    doLast {
        val activity = file("src/main/java/com/autoclicker/android/MainActivity.java")
        var activityText = activity.readText()

        activityText = activityText.replace(
            "private static final String[] PATTERNS = {\n            \"TELA TODA\", \"CÍRCULO\", \"ZIGUE-ZAGUE\"\n    };",
            "private static final String[] PATTERNS = {\n            \"2 DESLIZES HORIZONTAIS\"\n    };"
        )
        activityText = activityText.replace(
            "patternSpinner.setSelection(clamp(prefs.getInt(KEY_PATTERN, 0), 0, 2));",
            "patternSpinner.setSelection(0);"
        )
        activityText = activityText.replace(
            "Faz arrastos contínuos pela tela, como cortes de Fruit Ninja. O botão PARAR fica flutuando enquanto estiver rodando.",
            "Faz 2 deslizes horizontais ao mesmo tempo: um para a direita e outro para a esquerda. Roda até você tocar em PARAR."
        )
        activityText = activityText.replace(
            "int pattern = patternSpinner.getSelectedItemPosition();",
            "int pattern = 0;"
        )
        activityText = activityText.replace(
            "boolean unlimited = unlimitedCheck.isChecked();",
            "boolean unlimited = true;"
        )
        activityText = activityText.replace(
            "root.addView(label(\"Tempo total rodando (segundos)\"), fullWidth(dp(30)));",
            ""
        )
        activityText = activityText.replace(
            "root.addView(secondsInput, fullWidth(dp(48)));",
            ""
        )
        activityText = activityText.replace(
            "root.addView(unlimitedCheck, fullWidth(dp(56)));",
            ""
        )
        activityText = activityText.replace(
            "Sugestão: comece com 380 ms e pausa de 20 ms. Se ficar rápido demais, aumente o valor de velocidade. Durante a execução, toque no botão vermelho PARAR para interromper.",
            "Os dois deslizes são horizontais e acontecem juntos em sentidos opostos. O movimento fica rodando até você tocar em PARAR."
        )
        activity.writeText(activityText)

        val service = file("src/main/java/com/autoclicker/android/AutoClickService.java")
        var serviceText = service.readText()

        serviceText = serviceText.replace(
            "private WindowManager.LayoutParams stopParams;",
            "private WindowManager.LayoutParams stopParams;\n    private TextView miniBubble;\n    private WindowManager.LayoutParams miniParams;"
        )

        serviceText = serviceText.replace(
            """        startButton = button("INICIAR NINJA");
        startButton.setOnClickListener(v -> startNinja());
        panel.addView(startButton, buttonParams());

        Button close = button("FECHAR PAINEL");""",
            """        startButton = button("INICIAR NINJA");
        startButton.setOnClickListener(v -> startNinja());
        panel.addView(startButton, buttonParams());

        Button minimize = button("MINIMIZAR");
        minimize.setOnClickListener(v -> minimizePanel());
        panel.addView(minimize, buttonParams());

        Button close = button("FECHAR PAINEL");"""
        )

        serviceText = serviceText.replace(
            "pattern = clamp(prefs.getInt(MainActivity.KEY_PATTERN, 0), 0, 2);",
            "pattern = 0;"
        )
        serviceText = serviceText.replace(
            "unlimited = prefs.getBoolean(MainActivity.KEY_UNLIMITED, false);",
            "unlimited = true;"
        )

        serviceText = serviceText.replace(
            """            Path path = buildNinjaPath();
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, gestureMs))
                    .build();""",
            """            GestureDescription gesture = buildHorizontalGesture();"""
        )

        val methodRegex = Regex(
            "(?s)    private Path buildNinjaPath\\(\\) \\{.*?\\n    \\}\\n\\n    private void showStopBubble"
        )
        val newMethod = """    private GestureDescription buildHorizontalGesture() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float width = dm.widthPixels;
        float height = dm.heightPixels;

        float left = width * 0.05f;
        float right = width * 0.95f;
        float yTop = height * 0.42f;
        float yBottom = height * 0.58f;

        Path toRight = new Path();
        toRight.moveTo(left, yTop);
        toRight.lineTo(right, yTop);

        Path toLeft = new Path();
        toLeft.moveTo(right, yBottom);
        toLeft.lineTo(left, yBottom);

        return new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(toRight, 0, gestureMs))
                .addStroke(new GestureDescription.StrokeDescription(toLeft, 0, gestureMs))
                .build();
    }

    private void minimizePanel() {
        if (panel == null || miniBubble != null) return;
        panel.setVisibility(View.GONE);
        showMiniBubble();
    }

    private void showMiniBubble() {
        if (windowManager == null || miniBubble != null) return;
        miniBubble = new TextView(this);
        miniBubble.setText("≡");
        miniBubble.setTextColor(Color.WHITE);
        miniBubble.setTextSize(25);
        miniBubble.setGravity(Gravity.CENTER);
        miniBubble.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(240, 35, 105, 220));
        bg.setStroke(dp(2), Color.WHITE);
        miniBubble.setBackground(bg);
        miniBubble.setOnClickListener(v -> restorePanel());

        miniParams = new WindowManager.LayoutParams(
                dp(54), dp(54),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        miniParams.gravity = Gravity.TOP | Gravity.START;
        miniParams.x = panelParams != null ? panelParams.x : dp(12);
        miniParams.y = panelParams != null ? panelParams.y : dp(90);

        try { windowManager.addView(miniBubble, miniParams); }
        catch (Exception ignored) { miniBubble = null; miniParams = null; }
    }

    private void restorePanel() {
        removeMiniBubble();
        if (panel != null) panel.setVisibility(View.VISIBLE);
    }

    private void removeMiniBubble() {
        if (miniBubble != null && windowManager != null) {
            try { windowManager.removeView(miniBubble); }
            catch (Exception ignored) { }
        }
        miniBubble = null;
        miniParams = null;
    }

    private void showStopBubble"""
        serviceText = serviceText.replace(methodRegex, newMethod)

        val patternRegex = Regex(
            "(?s)    private String patternName\\(\\) \\{.*?\\n    \\}\\n\\n    private Button button"
        )
        serviceText = serviceText.replace(
            patternRegex,
            """    private String patternName() {
        return "2 HORIZONTAIS • ILIMITADO";
    }

    private Button button"""
        )

        serviceText = serviceText.replace(
            "removeStopBubble();\n        if (panel != null && windowManager != null)",
            "removeStopBubble();\n        removeMiniBubble();\n        if (panel != null && windowManager != null)"
        )

        service.writeText(serviceText)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(configureDualHorizontal)
}
