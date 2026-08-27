plugins {
    id("com.android.application")
}

android {
    namespace = "com.autoclicker.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.autoclicker.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.1.1"
    }
}

// Protege o painel flutuante e o botão minimizado dos próprios cliques automáticos.
// A transformação é aplicada no workspace de build antes da compilação.
val protectFloatingControls by tasks.registering {
    doLast {
        val source = file("src/main/java/com/autoclicker/android/AutoClickService.java")
        var text = source.readText()

        if (!text.contains("private boolean isControlOverlayAt(")) {
            val beforeDispatch = """    private void dispatchPointClick(ClickPoint p) {"""
            val helper = """    private boolean isControlOverlayAt(int x, int y) {
        return isPointInsideView(panel, x, y, dp(8)) ||
                isPointInsideView(miniButton, x, y, dp(8));
    }

    private boolean isPointInsideView(View view, int x, int y, int margin) {
        if (view == null || view.getVisibility() != View.VISIBLE ||
                view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return x >= location[0] - margin &&
                x <= location[0] + view.getWidth() + margin &&
                y >= location[1] - margin &&
                y <= location[1] + view.getHeight() + margin;
    }

"""
            text = text.replace(beforeDispatch, helper + beforeDispatch)
        }

        val oldDue = """            if (due != null) {
                dispatchPointClick(due);
                return;
            }"""
        val newDue = """            if (due != null) {
                if (isControlOverlayAt(due.x, due.y)) {
                    due.nextAt = now + Math.max(50, Math.min(due.intervalMs, 250));
                    handler.post(multiClickRunnable);
                    return;
                }
                dispatchPointClick(due);
                return;
            }"""
        text = text.replace(oldDue, newDue)

        val oldDispatchStart = """    private void dispatchPointClick(ClickPoint p) {
        if (!running) return;
        dispatching = true;"""
        val newDispatchStart = """    private void dispatchPointClick(ClickPoint p) {
        if (!running) return;
        if (isControlOverlayAt(p.x, p.y)) {
            p.nextAt = SystemClock.uptimeMillis() + Math.max(50, Math.min(p.intervalMs, 250));
            handler.postDelayed(multiClickRunnable, 50);
            return;
        }
        dispatching = true;"""
        text = text.replace(oldDispatchStart, newDispatchStart)

        source.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(protectFloatingControls)
}
