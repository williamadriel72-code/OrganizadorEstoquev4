package com.autoclicker.android;

public final class FruitNinjaBus {
    private FruitNinjaBus() {}

    public static volatile boolean captureRunning;
    public static volatile FruitNinjaDetector.Result latestResult;
    public static volatile String captureStatus = "Captura parada";
}
