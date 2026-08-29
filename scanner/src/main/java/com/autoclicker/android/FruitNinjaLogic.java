package com.autoclicker.android;

import java.util.ArrayList;
import java.util.List;

public final class FruitNinjaLogic {
    private FruitNinjaLogic() {}

    public static final class Bomb {
        public final float x;
        public final float y;
        public final float radius;
        public final float vx;
        public final float vy;
        public final long timestampMs;

        public Bomb(float x, float y, float radius, float vx, float vy, long timestampMs) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.vx = vx;
            this.vy = vy;
            this.timestampMs = timestampMs;
        }

        public float predictedX(long lookAheadMs) {
            return x + vx * (lookAheadMs / 1000f);
        }

        public float predictedY(long lookAheadMs) {
            return y + vy * (lookAheadMs / 1000f);
        }
    }

    public static final class Fruit {
        public final float x;
        public final float y;
        public final float size;
        public final float confidence;

        public Fruit(float x, float y, float size, float confidence) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.confidence = confidence;
        }
    }

    public static float distancePointToSegment(
            float px, float py,
            float x1, float y1,
            float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len2 = dx * dx + dy * dy;
        if (len2 <= 0.0001f) {
            float ax = px - x1;
            float ay = py - y1;
            return (float) Math.sqrt(ax * ax + ay * ay);
        }
        float t = ((px - x1) * dx + (py - y1) * dy) / len2;
        t = Math.max(0f, Math.min(1f, t));
        float qx = x1 + t * dx;
        float qy = y1 + t * dy;
        float ax = px - qx;
        float ay = py - qy;
        return (float) Math.sqrt(ax * ax + ay * ay);
    }

    public static boolean segmentClear(
            float x1, float y1, float x2, float y2,
            List<Bomb> bombs,
            long lookAheadMs,
            float extraMarginPx) {
        if (bombs == null || bombs.isEmpty()) return true;
        for (Bomb b : bombs) {
            float bx = b.predictedX(lookAheadMs);
            float by = b.predictedY(lookAheadMs);
            float danger = Math.max(24f, b.radius) + Math.max(0f, extraMarginPx);
            if (distancePointToSegment(bx, by, x1, y1, x2, y2) <= danger) {
                return false;
            }
        }
        return true;
    }

    public static boolean pointClear(
            float x, float y,
            List<Bomb> bombs,
            long lookAheadMs,
            float extraMarginPx) {
        return segmentClear(x, y, x, y, bombs, lookAheadMs, extraMarginPx);
    }

    public static float[] chooseSafeSlice(
            Fruit fruit,
            List<Bomb> bombs,
            long lookAheadMs,
            float extraMarginPx,
            float halfLengthPx,
            int screenW,
            int screenH) {
        if (fruit == null) return null;
        float half = Math.max(18f, Math.max(halfLengthPx, fruit.size * 0.42f));
        float[] angles = new float[] {0f, 90f, 45f, -45f, 22.5f, -22.5f, 67.5f, -67.5f};

        for (float deg : angles) {
            double rad = Math.toRadians(deg);
            float dx = (float) Math.cos(rad) * half;
            float dy = (float) Math.sin(rad) * half;
            float x1 = clamp(fruit.x - dx, 1, Math.max(1, screenW - 2));
            float y1 = clamp(fruit.y - dy, 1, Math.max(1, screenH - 2));
            float x2 = clamp(fruit.x + dx, 1, Math.max(1, screenW - 2));
            float y2 = clamp(fruit.y + dy, 1, Math.max(1, screenH - 2));
            if (segmentClear(x1, y1, x2, y2, bombs, lookAheadMs, extraMarginPx)) {
                return new float[]{x1, y1, x2, y2};
            }
        }
        return null;
    }

    public static List<Fruit> filterSafeFruits(
            List<Fruit> fruits,
            List<Bomb> bombs,
            long lookAheadMs,
            float extraMarginPx) {
        ArrayList<Fruit> out = new ArrayList<>();
        if (fruits == null) return out;
        for (Fruit fruit : fruits) {
            if (pointClear(fruit.x, fruit.y, bombs, lookAheadMs, extraMarginPx)) {
                out.add(fruit);
            }
        }
        return out;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
