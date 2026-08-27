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
        versionCode = 3
        versionName = "1.0.2"
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
            "Faz 2 deslizes horizontais ao mesmo tempo: um vai para a direita e o outro para a esquerda."
        )
        activityText = activityText.replace(
            "int pattern = patternSpinner.getSelectedItemPosition();",
            "int pattern = 0;"
        )
        activityText = activityText.replace(
            "Sugestão: comece com 380 ms e pausa de 20 ms. Se ficar rápido demais, aumente o valor de velocidade. Durante a execução, toque no botão vermelho PARAR para interromper.",
            "Os dois deslizes são horizontais e acontecem juntos em sentidos opostos. Comece com 380 ms e pausa de 20 ms."
        )
        activity.writeText(activityText)

        val service = file("src/main/java/com/autoclicker/android/AutoClickService.java")
        var serviceText = service.readText()

        serviceText = serviceText.replace(
            "pattern = clamp(prefs.getInt(MainActivity.KEY_PATTERN, 0), 0, 2);",
            "pattern = 0;"
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

    private void showStopBubble"""
        serviceText = serviceText.replace(methodRegex, newMethod)

        val patternRegex = Regex(
            "(?s)    private String patternName\\(\\) \\{.*?\\n    \\}\\n\\n    private Button button"
        )
        serviceText = serviceText.replace(
            patternRegex,
            """    private String patternName() {
        return "2 HORIZONTAIS";
    }

    private Button button"""
        )

        service.writeText(serviceText)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(configureDualHorizontal)
}
