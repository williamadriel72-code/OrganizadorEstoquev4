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
        versionCode = 2
        versionName = "1.0.1"
    }
}

val configureStraightLine by tasks.registering {
    doLast {
        val activity = file("src/main/java/com/autoclicker/android/MainActivity.java")
        var activityText = activity.readText()

        activityText = activityText.replace(
            "private static final String[] PATTERNS = {\n            \"TELA TODA\", \"CÍRCULO\", \"ZIGUE-ZAGUE\"\n    };",
            "private static final String[] PATTERNS = {\n            \"LINHA RETA • CANTO A CANTO\"\n    };"
        )
        activityText = activityText.replace(
            "patternSpinner.setSelection(clamp(prefs.getInt(KEY_PATTERN, 0), 0, 2));",
            "patternSpinner.setSelection(0);"
        )
        activityText = activityText.replace(
            "Faz arrastos contínuos pela tela, como cortes de Fruit Ninja. O botão PARAR fica flutuando enquanto estiver rodando.",
            "Faz uma linha reta de um canto ao outro da tela e volta pela mesma linha continuamente."
        )
        activityText = activityText.replace(
            "int pattern = patternSpinner.getSelectedItemPosition();",
            "int pattern = 0;"
        )
        activity.writeText(activityText)

        val service = file("src/main/java/com/autoclicker/android/AutoClickService.java")
        var serviceText = service.readText()

        serviceText = serviceText.replace(
            "pattern = clamp(prefs.getInt(MainActivity.KEY_PATTERN, 0), 0, 2);",
            "pattern = 0;"
        )

        val methodRegex = Regex(
            "(?s)    private Path buildNinjaPath\\(\\) \\{.*?\\n    \\}\\n\\n    private void showStopBubble"
        )
        val newMethod = """    private Path buildNinjaPath() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float width = dm.widthPixels;
        float height = dm.heightPixels;

        // Linha reta diagonal de canto a canto. Alterna o sentido a cada ciclo.
        float marginX = width * 0.035f;
        float marginY = height * 0.055f;
        float x1 = marginX;
        float y1 = marginY;
        float x2 = width - marginX;
        float y2 = height - marginY;
        boolean reverse = (cycle % 2L) == 1L;

        Path path = new Path();
        if (reverse) {
            path.moveTo(x2, y2);
            path.lineTo(x1, y1);
        } else {
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
        }
        return path;
    }

    private void showStopBubble"""
        serviceText = serviceText.replace(methodRegex, newMethod)

        val patternRegex = Regex(
            "(?s)    private String patternName\\(\\) \\{.*?\\n    \\}\\n\\n    private Button button"
        )
        serviceText = serviceText.replace(
            patternRegex,
            """    private String patternName() {
        return "LINHA RETA";
    }

    private Button button"""
        )

        service.writeText(serviceText)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(configureStraightLine)
}
