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
        versionCode = 2
        versionName = "1.1.0"
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

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(addMasterQuickModes)
}
