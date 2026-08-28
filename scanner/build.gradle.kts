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
        versionCode = 3
        versionName = "1.2.0"
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

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(removeVoiceControl)
}
