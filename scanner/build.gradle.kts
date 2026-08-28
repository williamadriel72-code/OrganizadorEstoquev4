plugins {
    id("com.android.application")
}

android {
    namespace = "com.autoclicker.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.voicecontrol.master"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }
}

val configureReliableVoice by tasks.registering {
    doLast {
        val service = file("src/main/java/com/autoclicker/android/AutoClickService.java")
        var text = service.readText()

        if (!text.contains("VOICE_RELIABILITY_PATCH_V101")) {
            text = text.replace(
                "private boolean continuous;",
                "private boolean continuous = true; // VOICE_RELIABILITY_PATCH_V101\n    private String lastHeard = \"\";"
            )

            text = text.replace(
                """        setupSpeechRecognizer();
        showPanel();
    }""",
                """        setupSpeechRecognizer();
        showPanel();
        handler.postDelayed(() -> {
            continuous = true;
            if (continuousButton != null) continuousButton.setText("CONTÍNUO: LIGADO");
            startListening();
        }, 700);
    }"""
            )

            text = text.replace(
                "continuousButton = button(\"CONTÍNUO: DESLIGADO\");",
                "continuousButton = button(\"CONTÍNUO: LIGADO\");"
            )

            text = text.replace(
                """                    String command = matches.get(0);
                    updateListeningUi("Comando: " + command);
                    executeCommand(command);""",
                """                    String command = matches.get(0);
                    lastHeard = command;
                    updateListeningUi("OUVI: " + command);
                    executeCommand(command);"""
            )

            text = text.replace(
                """            @Override public void onError(int error) {
                listening = false;
                updateListeningUi("Toque no microfone e fale");
                if (continuous && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    scheduleContinuousRestart(700);
                }
            }""",
                """            @Override public void onError(int error) {
                listening = false;
                updateListeningUi("Falha de voz (" + error + "). Tentando novamente...");
                if (speechRecognizer != null && error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    try { speechRecognizer.cancel(); } catch (Exception ignored) { }
                }
                if (continuous && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    scheduleContinuousRestart(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1200 : 600);
                }
            }"""
            )

            text = text.replace(
                "intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);",
                """intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 650L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 450L);"""
            )

            text = text.replace(
                "String cmd = normalize(raw);",
                """String cmd = normalize(raw);
        if (cmd.startsWith("por favor ")) cmd = cmd.substring(10).trim();
        if (cmd.startsWith("pode ")) cmd = cmd.substring(5).trim();
        if (cmd.startsWith("voce pode ")) cmd = cmd.substring(9).trim();"""
            )

            text = text.replace(
                "equalsAny(cmd, \"voltar\", \"volte\")",
                "equalsAny(cmd, \"voltar\", \"volte\", \"volta\", \"ir para tras\", \"vai para tras\")"
            )
            text = text.replace(
                "equalsAny(cmd, \"inicio\", \"ir para inicio\", \"tela inicial\", \"home\")",
                "equalsAny(cmd, \"inicio\", \"ir para inicio\", \"tela inicial\", \"home\", \"va para inicio\", \"vai para inicio\", \"voltar para inicio\")"
            )
            text = text.replace(
                "containsAny(cmd, \"aumentar volume\", \"volume mais alto\", \"aumenta o volume\")",
                "containsAny(cmd, \"aumentar volume\", \"volume mais alto\", \"aumenta o volume\", \"aumente o volume\", \"sobe o volume\")"
            )
            text = text.replace(
                "containsAny(cmd, \"diminuir volume\", \"volume mais baixo\", \"abaixar volume\")",
                "containsAny(cmd, \"diminuir volume\", \"volume mais baixo\", \"abaixar volume\", \"abaixe o volume\", \"baixa o volume\")"
            )
            text = text.replace(
                "containsAny(cmd, \"ligar lanterna\", \"acender lanterna\")",
                "containsAny(cmd, \"ligar lanterna\", \"acender lanterna\", \"liga lanterna\", \"acende a lanterna\", \"acenda a lanterna\")"
            )
            text = text.replace(
                "containsAny(cmd, \"desligar lanterna\", \"apagar lanterna\")",
                "containsAny(cmd, \"desligar lanterna\", \"apagar lanterna\", \"desliga lanterna\", \"apaga a lanterna\", \"apague a lanterna\")"
            )

            text = text.replace(
                """if (cmd.startsWith("tocar em ") || cmd.startsWith("clicar em ")) {
            String text = raw.replaceFirst("(?i)^(tocar|clicar)\\\\s+em\\\\s+", "").trim();""",
                """if (cmd.startsWith("tocar em ") || cmd.startsWith("clicar em ") || cmd.startsWith("toque em ") || cmd.startsWith("clique em ")) {
            String text = raw.replaceFirst("(?i)^(tocar|clicar|toque|clique)\\\\s+em\\\\s+", "").trim();"""
            )
            text = text.replace(
                """if (cmd.startsWith("escrever ") || cmd.startsWith("digitar ")) {
            String text = raw.replaceFirst("(?i)^(escrever|digitar)\\\\s+", "").trim();""",
                """if (cmd.startsWith("escrever ") || cmd.startsWith("digitar ") || cmd.startsWith("escreva ") || cmd.startsWith("digite ")) {
            String text = raw.replaceFirst("(?i)^(escrever|digitar|escreva|digite)\\\\s+", "").trim();"""
            )
            text = text.replace(
                """if (cmd.startsWith("pesquisar ") || cmd.startsWith("buscar ")) {
            String query = raw.replaceFirst("(?i)^(pesquisar|buscar)\\\\s+", "").trim();""",
                """if (cmd.startsWith("pesquisar ") || cmd.startsWith("buscar ") || cmd.startsWith("pesquise ") || cmd.startsWith("busque ")) {
            String query = raw.replaceFirst("(?i)^(pesquisar|buscar|pesquise|busque)\\\\s+", "").trim();"""
            )
            text = text.replace(
                """if (cmd.startsWith("ligar para ") || cmd.startsWith("telefonar para ")) {
            String target = raw.replaceFirst("(?i)^(ligar|telefonar)\\\\s+para\\\\s+", "").trim();""",
                """if (cmd.startsWith("ligar para ") || cmd.startsWith("telefonar para ") || cmd.startsWith("ligue para ")) {
            String target = raw.replaceFirst("(?i)^(ligar|telefonar|ligue)\\\\s+para\\\\s+", "").trim();"""
            )
            text = text.replace(
                """if (cmd.startsWith("abrir ")) {
            String app = raw.replaceFirst("(?i)^abrir\\\\s+", "").trim();""",
                """if (cmd.startsWith("abrir ") || cmd.startsWith("abra ") || cmd.startsWith("abre ")) {
            String app = raw.replaceFirst("(?i)^(abrir|abra|abre)\\\\s+", "").trim();"""
            )

            text = text.replace(
                "return n.replaceAll(\"\\\\p{M}+\", \"\").trim().replaceAll(\"\\\\s+\", \" \" );",
                "return n.replaceAll(\"\\\\p{M}+\", \"\").replaceAll(\"[^a-z0-9+ ]\", \" \" ).trim().replaceAll(\"\\\\s+\", \" \" );"
            )

            text = text.replace(
                "if (status != null) status.setText(text);",
                "if (status != null) status.setText(lastHeard.isEmpty() ? text : (\"OUVI: \" + lastHeard + \"\\nAÇÃO: \" + text));"
            )

            service.writeText(text)
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(configureReliableVoice)
}
