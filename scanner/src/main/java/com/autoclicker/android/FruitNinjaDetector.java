package com.autoclicker.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class FruitNinjaDetector {
    public interface PixelSource {
        int getArgb(int x, int y);
    }

    public static final class Result {
        public final List<FruitNinjaLogic.Bomb> bombs;
        public final List<FruitNinjaLogic.Fruit> fruits;
        public final long timestampMs;
        public final int analysisMs;
        public final int sampleStep;
        public final int width;
        public final int height;

        Result(List<FruitNinjaLogic.Bomb> bombs,
               List<FruitNinjaLogic.Fruit> fruits,
               long timestampMs,
               int analysisMs,
               int sampleStep,
               int width,
               int height) {
            this.bombs = bombs;
            this.fruits = fruits;
            this.timestampMs = timestampMs;
            this.analysisMs = analysisMs;
            this.sampleStep = sampleStep;
            this.width = width;
            this.height = height;
        }
    }

    private static final class RawBomb {
        float x, y, radius;
        RawBomb(float x, float y, float radius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    private static final class Track {
        float x, y, radius, vx, vy;
        long timestampMs;
        int misses;
    }

    private int[] previous;
    private int previousGridW;
    private int previousGridH;
    private final ArrayList<Track> tracks = new ArrayList<>();

    public Result analyze(PixelSource src, int width, int height, long timestampMs) {
        long started = System.nanoTime();
        if (src == null || width < 80 || height < 80) {
            return new Result(Collections.emptyList(), Collections.emptyList(), timestampMs, 0, 8, width, height);
        }

        int step = Math.max(5, Math.min(9, Math.min(width, height) / 180));
        int gw = Math.max(1, width / step);
        int gh = Math.max(1, height / step);
        int total = gw * gh;

        int[] current = new int[total];
        boolean[] moving = new boolean[total];
        boolean[] colorfulBase = new boolean[total];
        boolean[] darkBase = new boolean[total];
        boolean[] colorfulMoving = new boolean[total];
        boolean[] darkMoving = new boolean[total];

        // Faixa superior e bordas ficam proibidas para impedir que o detector trate
        // placar, pausa, menus e gestos do sistema como objetos do jogo.
        int leftCut = Math.max(0, (int)(width * 0.065f));
        int rightCut = Math.min(width, (int)(width * 0.935f));
        int topCut = Math.max(0, (int)(height * 0.18f));
        int bottomCut = Math.min(height, (int)(height * 0.91f));

        boolean havePrev = previous != null && previousGridW == gw && previousGridH == gh;

        for (int gy = 0; gy < gh; gy++) {
            int y = Math.min(height - 1, gy * step + step / 2);
            for (int gx = 0; gx < gw; gx++) {
                int x = Math.min(width - 1, gx * step + step / 2);
                int idx = gy * gw + gx;
                int c = src.getArgb(x, y);
                current[idx] = c;

                int r = (c >> 16) & 255;
                int g = (c >> 8) & 255;
                int b = c & 255;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int lum = (r * 3 + g * 6 + b) / 10;

                int diff = 255;
                if (havePrev) {
                    int p = previous[idx];
                    int pr = (p >> 16) & 255;
                    int pg = (p >> 8) & 255;
                    int pb = p & 255;
                    diff = (Math.abs(r - pr) + Math.abs(g - pg) + Math.abs(b - pb)) / 3;
                }

                boolean inPlay = x >= leftCut && x <= rightCut && y >= topCut && y <= bottomCut;
                boolean isMoving = havePrev && diff >= 15;
                moving[idx] = isMoving;

                colorfulBase[idx] = inPlay
                        && max >= 100
                        && (max - min) >= 50
                        && lum >= 58;

                // Mais permissivo que a versão anterior: bombas com reflexo, símbolo
                // vermelho ou iluminação ainda entram na máscara de perigo.
                darkBase[idx] = inPlay
                        && lum <= 128
                        && max <= 175
                        && (max - min) <= 125;
            }
        }

        if (havePrev) {
            for (int gy = 0; gy < gh; gy++) {
                for (int gx = 0; gx < gw; gx++) {
                    int idx = gy * gw + gx;
                    boolean nearMotion = false;
                    for (int oy = -2; oy <= 2 && !nearMotion; oy++) {
                        int ny = gy + oy;
                        if (ny < 0 || ny >= gh) continue;
                        for (int ox = -2; ox <= 2; ox++) {
                            int nx = gx + ox;
                            if (nx < 0 || nx >= gw) continue;
                            if (moving[ny * gw + nx]) {
                                nearMotion = true;
                                break;
                            }
                        }
                    }
                    colorfulMoving[idx] = colorfulBase[idx] && nearMotion;
                    darkMoving[idx] = darkBase[idx] && nearMotion;
                }
            }
        }

        ArrayList<RawBomb> rawBombs = findBombCandidates(
                src, width, height, step, gw, gh, darkMoving);
        List<FruitNinjaLogic.Bomb> bombs = updateTracks(rawBombs, timestampMs);

        ArrayList<FruitNinjaLogic.Fruit> fruits = findFruitCandidates(
                width, height, step, gw, gh, colorfulMoving, bombs,
                leftCut, rightCut, topCut, bottomCut);

        previous = current;
        previousGridW = gw;
        previousGridH = gh;

        int elapsedMs = (int)((System.nanoTime() - started) / 1_000_000L);
        return new Result(
                Collections.unmodifiableList(new ArrayList<>(bombs)),
                Collections.unmodifiableList(new ArrayList<>(fruits)),
                timestampMs,
                elapsedMs,
                step,
                width,
                height);
    }

    public Result analyzeArgb(final int[] argb, final int width, final int height, long timestampMs) {
        return analyze(new PixelSource() {
            @Override public int getArgb(int x, int y) {
                return argb[y * width + x];
            }
        }, width, height, timestampMs);
    }

    private ArrayList<RawBomb> findBombCandidates(
            PixelSource src,
            int width,
            int height,
            int step,
            int gw,
            int gh,
            boolean[] mask) {

        ArrayList<RawBomb> out = new ArrayList<>();
        boolean[] seen = new boolean[mask.length];
        int[] qx = new int[mask.length];
        int[] qy = new int[mask.length];

        for (int sy = 0; sy < gh; sy++) {
            for (int sx = 0; sx < gw; sx++) {
                int sidx = sy * gw + sx;
                if (!mask[sidx] || seen[sidx]) continue;

                int head = 0, tail = 0;
                qx[tail] = sx;
                qy[tail] = sy;
                tail++;
                seen[sidx] = true;

                int count = 0;
                int minX = sx, maxX = sx, minY = sy, maxY = sy;
                long sumX = 0, sumY = 0;

                while (head < tail) {
                    int x = qx[head], y = qy[head];
                    head++;
                    count++;
                    sumX += x;
                    sumY += y;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);

                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            if (ox == 0 && oy == 0) continue;
                            int nx = x + ox, ny = y + oy;
                            if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue;
                            int ni = ny * gw + nx;
                            if (mask[ni] && !seen[ni]) {
                                seen[ni] = true;
                                qx[tail] = nx;
                                qy[tail] = ny;
                                tail++;
                            }
                        }
                    }
                }

                if (count < 3 || count > 520) continue;
                int boxCells = Math.max(1, (maxX - minX + 1) * (maxY - minY + 1));
                float fill = count / (float) boxCells;
                float bw = (maxX - minX + 1) * step;
                float bh = (maxY - minY + 1) * step;
                if (bw < 16 || bh < 16 || bw > 260 || bh > 260) continue;
                float aspect = bw / Math.max(1f, bh);
                if (aspect < 0.45f || aspect > 2.20f || fill < 0.08f) continue;

                float cx = ((sumX / (float)count) + 0.5f) * step;
                float cy = ((sumY / (float)count) + 0.5f) * step;

                int px0 = clamp((minX * step) - step * 5, 0, width - 1);
                int py0 = clamp((minY * step) - step * 6, 0, height - 1);
                int px1 = clamp(((maxX + 1) * step) + step * 5, 0, width - 1);
                int py1 = clamp(((maxY + 1) * step) + step * 4, 0, height - 1);

                int hot = 0;
                int bright = 0;
                int redMark = 0;
                int dark = 0;
                int sampled = 0;
                int sample = Math.max(2, step / 2);
                for (int y = py0; y <= py1; y += sample) {
                    for (int x = px0; x <= px1; x += sample) {
                        int c = src.getArgb(x, y);
                        int r = (c >> 16) & 255;
                        int g = (c >> 8) & 255;
                        int b = c & 255;
                        int lum = (r * 3 + g * 6 + b) / 10;
                        sampled++;
                        if (lum < 115) dark++;
                        if (isHotFuseColor(r, g, b)) hot++;
                        if (r > 185 && g > 135 && b < 130) bright++;
                        if (r > 120 && r > g + 28 && r > b + 24) redMark++;
                    }
                }

                float darkRatio = sampled == 0 ? 0f : dark / (float) sampled;
                boolean fuseEvidence = hot + bright >= 1;
                boolean redEvidence = redMark >= 2;
                boolean darkRoundObject = fill >= 0.14f
                        && aspect >= 0.55f && aspect <= 1.85f
                        && count >= 5
                        && darkRatio >= 0.20f;

                // Proteção máxima: se parece com um objeto escuro móvel e arredondado,
                // tratamos como perigo mesmo sem conseguir enxergar a chama em um frame.
                if (!fuseEvidence && !redEvidence && !darkRoundObject) continue;

                float radius = Math.max(30f, Math.max(bw, bh) * 0.78f + 14f);
                out.add(new RawBomb(cx, cy, radius));
            }
        }

        Collections.sort(out, new Comparator<RawBomb>() {
            @Override public int compare(RawBomb a, RawBomb b) {
                return Float.compare(b.radius, a.radius);
            }
        });
        if (out.size() > 16) {
            return new ArrayList<>(out.subList(0, 16));
        }
        return out;
    }

    private ArrayList<FruitNinjaLogic.Fruit> findFruitCandidates(
            int width,
            int height,
            int step,
            int gw,
            int gh,
            boolean[] mask,
            List<FruitNinjaLogic.Bomb> bombs,
            int leftCut,
            int rightCut,
            int topCut,
            int bottomCut) {

        ArrayList<FruitNinjaLogic.Fruit> out = new ArrayList<>();
        boolean[] seen = new boolean[mask.length];
        int[] qx = new int[mask.length];
        int[] qy = new int[mask.length];

        for (int sy = 0; sy < gh; sy++) {
            for (int sx = 0; sx < gw; sx++) {
                int sidx = sy * gw + sx;
                if (!mask[sidx] || seen[sidx]) continue;

                int head = 0, tail = 0;
                qx[tail] = sx;
                qy[tail] = sy;
                tail++;
                seen[sidx] = true;

                int count = 0;
                int minX = sx, maxX = sx, minY = sy, maxY = sy;
                long sumX = 0, sumY = 0;

                while (head < tail) {
                    int x = qx[head], y = qy[head];
                    head++;
                    count++;
                    sumX += x;
                    sumY += y;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);

                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            if (ox == 0 && oy == 0) continue;
                            int nx = x + ox, ny = y + oy;
                            if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue;
                            int ni = ny * gw + nx;
                            if (mask[ni] && !seen[ni]) {
                                seen[ni] = true;
                                qx[tail] = nx;
                                qy[tail] = ny;
                                tail++;
                            }
                        }
                    }
                }

                if (count < 4 || count > 420) continue;
                int boxCells = Math.max(1, (maxX - minX + 1) * (maxY - minY + 1));
                float fill = count / (float) boxCells;
                float bw = (maxX - minX + 1) * step;
                float bh = (maxY - minY + 1) * step;
                if (bw < 20 || bh < 20 || bw > 190 || bh > 190) continue;
                float aspect = bw / Math.max(1f, bh);
                if (aspect < 0.42f || aspect > 2.40f || fill < 0.08f) continue;

                float cx = ((sumX / (float)count) + 0.5f) * step;
                float cy = ((sumY / (float)count) + 0.5f) * step;
                if (cx < leftCut || cx > rightCut || cy < topCut || cy > bottomCut) continue;

                float size = Math.max(20f, Math.max(bw, bh));
                float confidence = Math.min(1f,
                        0.28f + Math.min(0.42f, count / 45f) + Math.min(0.30f, fill * 0.55f));
                if (confidence < 0.50f) continue;

                boolean tooNearBomb = false;
                for (FruitNinjaLogic.Bomb b : bombs) {
                    float dx = cx - b.x;
                    float dy = cy - b.y;
                    float limit = b.radius + Math.max(52f, size * 0.72f);
                    if (dx * dx + dy * dy < limit * limit) {
                        tooNearBomb = true;
                        break;
                    }
                }
                if (!tooNearBomb) {
                    out.add(new FruitNinjaLogic.Fruit(cx, cy, size, confidence));
                }
            }
        }

        Collections.sort(out, new Comparator<FruitNinjaLogic.Fruit>() {
            @Override public int compare(FruitNinjaLogic.Fruit a, FruitNinjaLogic.Fruit b) {
                return Float.compare(b.confidence, a.confidence);
            }
        });
        if (out.size() > 12) {
            return new ArrayList<>(out.subList(0, 12));
        }
        return out;
    }

    private List<FruitNinjaLogic.Bomb> updateTracks(List<RawBomb> detections, long nowMs) {
        boolean[] used = new boolean[detections.size()];

        for (Track t : tracks) {
            int best = -1;
            float bestD2 = Float.MAX_VALUE;
            for (int i = 0; i < detections.size(); i++) {
                if (used[i]) continue;
                RawBomb d = detections.get(i);
                float dx = d.x - t.x;
                float dy = d.y - t.y;
                float d2 = dx * dx + dy * dy;
                float gate = Math.max(135f, t.radius * 3.8f);
                if (d2 < gate * gate && d2 < bestD2) {
                    bestD2 = d2;
                    best = i;
                }
            }

            if (best >= 0) {
                RawBomb d = detections.get(best);
                used[best] = true;
                float dt = Math.max(0.016f, Math.min(0.25f, (nowMs - t.timestampMs) / 1000f));
                float nvx = (d.x - t.x) / dt;
                float nvy = (d.y - t.y) / dt;
                t.vx = t.vx * 0.40f + nvx * 0.60f;
                t.vy = t.vy * 0.40f + nvy * 0.60f;
                t.x = d.x;
                t.y = d.y;
                t.radius = t.radius * 0.35f + d.radius * 0.65f;
                t.timestampMs = nowMs;
                t.misses = 0;
            } else {
                t.misses++;
            }
        }

        for (int i = 0; i < detections.size(); i++) {
            if (used[i]) continue;
            RawBomb d = detections.get(i);
            Track t = new Track();
            t.x = d.x;
            t.y = d.y;
            t.radius = d.radius;
            t.timestampMs = nowMs;
            tracks.add(t);
        }

        for (int i = tracks.size() - 1; i >= 0; i--) {
            if (tracks.get(i).misses > 4) tracks.remove(i);
        }

        ArrayList<FruitNinjaLogic.Bomb> out = new ArrayList<>();
        for (Track t : tracks) {
            if (t.misses > 3) continue;
            float speed = (float)Math.sqrt(t.vx * t.vx + t.vy * t.vy);
            float motionPadding = Math.min(100f, speed * 0.055f);
            float stalePadding = t.misses * 18f;
            out.add(new FruitNinjaLogic.Bomb(
                    t.x,
                    t.y,
                    Math.max(34f, t.radius + motionPadding + stalePadding),
                    t.vx,
                    t.vy,
                    nowMs));
        }
        return out;
    }

    private static boolean isHotFuseColor(int r, int g, int b) {
        return (r >= 135 && g >= 25 && g <= 190 && b <= 125)
                || (r >= 185 && g >= 115 && b <= 135)
                || (r >= 215 && g >= 175 && b <= 160);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
