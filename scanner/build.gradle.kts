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
        versionCode = 3
        versionName = "1.1.0"
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
                """if (cmd.startsWith("abrir ")) {
            String app = raw.replaceFirst("(?i)^abrir\\\\s+", "").trim();""",
                """if (cmd.startsWith("abrir ") || cmd.startsWith("abra ") || cmd.startsWith("abre ")) {
            String app = raw.replaceFirst("(?i)^(abrir|abra|abre)\\\\s+", "").trim();"""
            )

            text = text.replace(
                "if (status != null) status.setText(text);",
                "if (status != null) status.setText(lastHeard.isEmpty() ? text : (\"OUVI: \" + lastHeard + \"\\nAÇÃO: \" + text));"
            )

            service.writeText(text)
        }
    }
}

val configureHandsMode by tasks.registering {
    dependsOn(configureReliableVoice)
    doLast {
        val service = file("src/main/java/com/autoclicker/android/AutoClickService.java")
        var text = service.readText()

        if (!text.contains("HANDS_MODE_WHATSAPP_V110")) {
            text = text.replace(
                "import android.graphics.Path;",
                "import android.graphics.Path;\nimport android.graphics.Rect;"
            )

            text = text.replace(
                "private boolean miniMoved;",
                """private boolean miniMoved;

    // HANDS_MODE_WHATSAPP_V110
    private String pendingWhatsAppContact = "";
    private String pendingWhatsAppMessage = "";
    private int whatsAppStep = 0;
    private int whatsAppRetries = 0;"""
            )

            text = text.replace(
                """        if (equalsAny(cmd, "apagar texto", "limpar texto", "limpar campo")) { clearFocusedText(); return; }""",
                """        java.util.regex.Matcher whats = java.util.regex.Pattern.compile(
                "(?i)^(?:mandar|manda|mande|enviar|envie)\\\\s+(?:uma\\\\s+)?(?:mensagem\\\\s+)?(?:no\\\\s+whatsapp\\\\s+)?(?:para|pra)\\\\s+(.+?)\\\\s+(?:dizendo|falando|escrevendo|com\\\\s+a\\\\s+mensagem)\\\\s+(.+)$"
        ).matcher(raw.trim());
        if (whats.find()) {
            sendWhatsAppMessage(whats.group(1).trim(), whats.group(2).trim());
            return;
        }

        if (equalsAny(cmd, "apagar texto", "limpar texto", "limpar campo")) { clearFocusedText(); return; }"""
            )

            text = text.replace(
                """    private void adjustVolume(int direction) {""",
                """    private void sendWhatsAppMessage(String contact, String message) {
        pendingWhatsAppContact = contact;
        pendingWhatsAppMessage = message;
        whatsAppStep = 0;
        whatsAppRetries = 0;

        PackageManager pm = getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage("com.whatsapp");
        if (launch == null) launch = pm.getLaunchIntentForPackage("com.whatsapp.w4b");
        if (launch == null) {
            feedback("WhatsApp não encontrado");
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
        feedback("Abrindo WhatsApp para " + contact);
        handler.postDelayed(this::runWhatsAppHands, 1400);
    }

    private void runWhatsAppHands() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            retryWhatsAppHands("Esperando o WhatsApp abrir");
            return;
        }

        if (whatsAppStep == 0) {
            if (clickSearchControl(root)) {
                whatsAppStep = 1;
                whatsAppRetries = 0;
                handler.postDelayed(this::runWhatsAppHands, 450);
            } else {
                retryWhatsAppHands("Procurando botão Pesquisar");
            }
            return;
        }

        if (whatsAppStep == 1) {
            AccessibilityNodeInfo edit = findAnyEditable(root);
            if (edit != null && setNodeText(edit, pendingWhatsAppContact)) {
                feedback("Digitando contato: " + pendingWhatsAppContact);
                whatsAppStep = 2;
                whatsAppRetries = 0;
                handler.postDelayed(this::runWhatsAppHands, 850);
            } else {
                retryWhatsAppHands("Esperando campo de pesquisa");
            }
            return;
        }

        if (whatsAppStep == 2) {
            if (clickMatchingText(root, pendingWhatsAppContact)) {
                feedback("Abrindo conversa com " + pendingWhatsAppContact);
                whatsAppStep = 3;
                whatsAppRetries = 0;
                handler.postDelayed(this::runWhatsAppHands, 1000);
            } else {
                retryWhatsAppHands("Procurando contato " + pendingWhatsAppContact);
            }
            return;
        }

        if (whatsAppStep == 3) {
            AccessibilityNodeInfo edit = findMessageEditable(root);
            if (edit == null) edit = findAnyEditable(root);
            if (edit != null && setNodeText(edit, pendingWhatsAppMessage)) {
                feedback("Mensagem digitada");
                whatsAppStep = 4;
                whatsAppRetries = 0;
                handler.postDelayed(this::runWhatsAppHands, 500);
            } else {
                retryWhatsAppHands("Esperando campo da mensagem");
            }
            return;
        }

        if (whatsAppStep == 4) {
            if (clickSendControl(root)) {
                feedback("Mensagem enviada para " + pendingWhatsAppContact);
                whatsAppStep = 5;
                pendingWhatsAppContact = "";
                pendingWhatsAppMessage = "";
            } else {
                retryWhatsAppHands("Procurando botão Enviar");
            }
        }
    }

    private void retryWhatsAppHands(String statusText) {
        whatsAppRetries++;
        if (whatsAppRetries > 12) {
            feedback("Não consegui concluir: " + statusText);
            whatsAppStep = -1;
            return;
        }
        feedback(statusText + " (" + whatsAppRetries + ")");
        handler.postDelayed(this::runWhatsAppHands, 500);
    }

    private boolean clickSearchControl(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo node = findByDescription(root, "pesquisar");
        if (node == null) node = findByDescription(root, "search");
        if (node != null && clickNodeOrParent(node)) return true;
        return clickMatchingText(root, "Pesquisar") || clickMatchingText(root, "Search");
    }

    private boolean clickSendControl(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo node = findByDescription(root, "enviar");
        if (node == null) node = findByDescription(root, "send");
        if (node != null && clickNodeOrParent(node)) return true;
        if (clickMatchingText(root, "Enviar") || clickMatchingText(root, "Send")) return true;

        // Último recurso: toque no local típico do botão Enviar, como um dedo na tela.
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        dispatchTap(w * 0.92f, h * 0.88f);
        return true;
    }

    private boolean clickMatchingText(AccessibilityNodeInfo root, String wanted) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(wanted);
        if (nodes == null || nodes.isEmpty()) return false;
        String normalizedWanted = normalize(wanted);
        AccessibilityNodeInfo fallback = null;
        for (AccessibilityNodeInfo node : nodes) {
            CharSequence value = node.getText();
            if (value == null) value = node.getContentDescription();
            if (value != null && normalize(value.toString()).equals(normalizedWanted)) {
                if (clickNodeOrParent(node)) return true;
            }
            if (fallback == null) fallback = node;
        }
        return fallback != null && clickNodeOrParent(fallback);
    }

    private AccessibilityNodeInfo findAnyEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isVisibleToUser()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findAnyEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findMessageEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isVisibleToUser()) {
            String hint = "";
            if (Build.VERSION.SDK_INT >= 26 && node.getHintText() != null) hint = normalize(node.getHintText().toString());
            String desc = node.getContentDescription() == null ? "" : normalize(node.getContentDescription().toString());
            if (hint.contains("mensagem") || hint.contains("message") || desc.contains("mensagem") || desc.contains("message")) return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findMessageEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private boolean setNodeText(AccessibilityNodeInfo node, String value) {
        if (node == null) return false;
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private void dispatchTap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80))
                .build();
        dispatchGesture(gesture, null, null);
    }

    private void adjustVolume(int direction) {"""
            )

            text = text.replace(
                """        while (current != null && depth < 8) {
            if (current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            current = current.getParent();
            depth++;
        }
        return false;""",
                """        AccessibilityNodeInfo original = current;
        while (current != null && depth < 8) {
            if (current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            current = current.getParent();
            depth++;
        }
        if (original != null && original.isVisibleToUser()) {
            Rect bounds = new Rect();
            original.getBoundsInScreen(bounds);
            if (!bounds.isEmpty()) {
                dispatchTap(bounds.exactCenterX(), bounds.exactCenterY());
                return true;
            }
        }
        return false;"""
            )

            service.writeText(text)
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(configureHandsMode)
}
