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
        float x, y, radius, score;
        RawBomb(float x, float y, float radius, float score) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.score = score;
        }
    }

    private static final class RawFruit {
        float x, y, size, confidence;
        RawFruit(float x, float y, float size, float confidence) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.confidence = confidence;
        }
    }

    private static final class BombTrack {
        float x, y, radius, vx, vy;
        long timestampMs;
        int misses;
    }

    private static final class FruitTrack {
        float x, y, size, confidence, vx, vy;
        long timestampMs;
        int hits;
        int misses;
    }

    private int[] previous;
    private int previousGridW;
    private int previousGridH;
    private final ArrayList<BombTrack> bombTracks = new ArrayList<>();
    private final ArrayList<FruitTrack> fruitTracks = new ArrayList<>();

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
        boolean[] purpleBase = new boolean[total];
        boolean[] redBase = new boolean[total];
        boolean[] colorfulMoving = new boolean[total];
        boolean[] darkMoving = new boolean[total];
        boolean[] purpleMoving = new boolean[total];
        boolean[] redMoving = new boolean[total];

        // Mantém HUD, pausa, navegação do sistema e bordas fora da análise.
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
                boolean isMoving = havePrev && diff >= 14;
                moving[idx] = isMoving;

                colorfulBase[idx] = inPlay
                        && max >= 96
                        && (max - min) >= 46
                        && lum >= 54;

                darkBase[idx] = inPlay
                        && lum <= 135
                        && max <= 185;

                purpleBase[idx] = inPlay && isPurple(r, g, b);
                redBase[idx] = inPlay && isBombRed(r, g, b);
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
                    purpleMoving[idx] = purpleBase[idx] && nearMotion;
                    redMoving[idx] = redBase[idx] && nearMotion;
                }
            }
        }

        ArrayList<RawBomb> rawBombs = findDarkBombCandidates(
                src, width, height, step, gw, gh, darkMoving);
        rawBombs.addAll(findArcBombCandidates(
                src, width, height, step, gw, gh, purpleMoving, true));
        rawBombs.addAll(findArcBombCandidates(
                src, width, height, step, gw, gh, redMoving, false));
        rawBombs = mergeBombCandidates(rawBombs);

        List<FruitNinjaLogic.Bomb> bombs = updateBombTracks(rawBombs, timestampMs);

        ArrayList<RawFruit> rawFruits = findFruitCandidates(
                width, height, step, gw, gh, colorfulMoving,
                leftCut, rightCut, topCut, bottomCut, bombs);
        List<FruitNinjaLogic.Fruit> fruits = updateFruitTracks(rawFruits, timestampMs, bombs);

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

    private ArrayList<RawBomb> findDarkBombCandidates(
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

                if (count < 3 || count > 560) continue;
                int boxCells = Math.max(1, (maxX - minX + 1) * (maxY - minY + 1));
                float fill = count / (float) boxCells;
                float bw = (maxX - minX + 1) * step;
                float bh = (maxY - minY + 1) * step;
                if (bw < 15 || bh < 15 || bw > 270 || bh > 270) continue;
                float aspect = bw / Math.max(1f, bh);
                if (aspect < 0.42f || aspect > 2.35f || fill < 0.07f) continue;

                float cx = ((sumX / (float)count) + 0.5f) * step;
                float cy = ((sumY / (float)count) + 0.5f) * step;

                ScanEvidence ev = scanEvidence(src, width, height,
                        minX * step - step * 6,
                        minY * step - step * 7,
                        (maxX + 1) * step + step * 6,
                        (maxY + 1) * step + step * 5,
                        Math.max(2, step / 2));

                boolean coloredArc = ev.red >= 2 || ev.purple >= 2;
                boolean sparks = ev.white >= 2 || ev.hot >= 1;
                boolean compactDark = fill >= 0.16f
                        && aspect >= 0.58f && aspect <= 1.72f
                        && count >= 6
                        && ev.darkRatio >= 0.18f;

                if (!coloredArc && !(compactDark && sparks)) continue;

                float score = 0.35f
                        + Math.min(0.25f, fill * 0.7f)
                        + (coloredArc ? 0.25f : 0f)
                        + (sparks ? 0.15f : 0f);
                float radius = Math.max(32f, Math.max(bw, bh) * 0.82f + 16f);
                out.add(new RawBomb(cx, cy, radius, Math.min(1f, score)));
            }
        }
        return out;
    }

    private ArrayList<RawBomb> findArcBombCandidates(
            PixelSource src,
            int width,
            int height,
            int step,
            int gw,
            int gh,
            boolean[] mask,
            boolean purpleArc) {

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

                if (count < 2 || count > 380) continue;
                float bw = (maxX - minX + 1) * step;
                float bh = (maxY - minY + 1) * step;
                if (bw < 10 || bh < 10 || bw > 240 || bh > 240) continue;

                ScanEvidence ev = scanEvidence(src, width, height,
                        minX * step - step * 7,
                        minY * step - step * 7,
                        (maxX + 1) * step + step * 7,
                        (maxY + 1) * step + step * 7,
                        Math.max(2, step / 2));

                // A bomba Arcade é preta com aro roxo, “-10” claro e faíscas lilás.
                // A Classic usa o mesmo corpo escuro com aro vermelho e faíscas claras.
                int arcPixels = purpleArc ? ev.purple : ev.red;
                boolean enoughArc = arcPixels >= 2;
                boolean darkCore = ev.darkRatio >= (purpleArc ? 0.08f : 0.10f);
                boolean brightMark = ev.white >= 1 || ev.hot >= 1;
                if (!enoughArc || !darkCore || !brightMark) continue;

                float cx = ((sumX / (float)count) + 0.5f) * step;
                float cy = ((sumY / (float)count) + 0.5f) * step;
                float radius = Math.max(38f, Math.max(bw, bh) * 0.95f + 24f);
                float score = purpleArc ? 0.96f : 0.92f;
                out.add(new RawBomb(cx, cy, radius, score));
            }
        }
        return out;
    }

    private ArrayList<RawBomb> mergeBombCandidates(ArrayList<RawBomb> input) {
        Collections.sort(input, new Comparator<RawBomb>() {
            @Override public int compare(RawBomb a, RawBomb b) {
                return Float.compare(b.score, a.score);
            }
        });

        ArrayList<RawBomb> out = new ArrayList<>();
        for (RawBomb d : input) {
            RawBomb match = null;
            for (RawBomb k : out) {
                float dx = d.x - k.x;
                float dy = d.y - k.y;
                float gate = Math.max(54f, Math.min(d.radius, k.radius) * 1.45f);
                if (dx * dx + dy * dy <= gate * gate) {
                    match = k;
                    break;
                }
            }
            if (match == null) {
                out.add(new RawBomb(d.x, d.y, d.radius, d.score));
            } else {
                float wa = Math.max(0.2f, match.score);
                float wb = Math.max(0.2f, d.score);
                float sum = wa + wb;
                match.x = (match.x * wa + d.x * wb) / sum;
                match.y = (match.y * wa + d.y * wb) / sum;
                match.radius = Math.max(match.radius, d.radius);
                match.score = Math.max(match.score, d.score);
            }
        }
        if (out.size() > 16) return new ArrayList<>(out.subList(0, 16));
        return out;
    }

    private ArrayList<RawFruit> findFruitCandidates(
            int width,
            int height,
            int step,
            int gw,
            int gh,
            boolean[] mask,
            int leftCut,
            int rightCut,
            int topCut,
            int bottomCut,
            List<FruitNinjaLogic.Bomb> bombs) {

        ArrayList<RawFruit> out = new ArrayList<>();
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

                if (count < 4 || count > 360) continue;
                int boxCells = Math.max(1, (maxX - minX + 1) * (maxY - minY + 1));
                float fill = count / (float) boxCells;
                float bw = (maxX - minX + 1) * step;
                float bh = (maxY - minY + 1) * step;
                if (bw < 20 || bh < 20 || bw > 180 || bh > 180) continue;
                float aspect = bw / Math.max(1f, bh);

                // Respingos e trilhas do corte normalmente são finos, quebrados e pouco compactos.
                if (aspect < 0.48f || aspect > 2.10f || fill < 0.13f) continue;

                float cx = ((sumX / (float)count) + 0.5f) * step;
                float cy = ((sumY / (float)count) + 0.5f) * step;
                if (cx < leftCut || cx > rightCut || cy < topCut || cy > bottomCut) continue;

                float size = Math.max(20f, Math.max(bw, bh));
                float confidence = Math.min(1f,
                        0.30f
                                + Math.min(0.34f, count / 48f)
                                + Math.min(0.24f, fill * 0.62f)
                                + (aspect >= 0.70f && aspect <= 1.45f ? 0.10f : 0f));
                if (confidence < 0.52f) continue;

                // Bombas roxas também têm pixels coloridos. Nunca as deixe entrar como fruta.
                if (!pointFarFromBombs(cx, cy, size, bombs)) continue;

                out.add(new RawFruit(cx, cy, size, confidence));
            }
        }

        Collections.sort(out, new Comparator<RawFruit>() {
            @Override public int compare(RawFruit a, RawFruit b) {
                return Float.compare(b.confidence, a.confidence);
            }
        });
        if (out.size() > 20) return new ArrayList<>(out.subList(0, 20));
        return out;
    }

    private List<FruitNinjaLogic.Bomb> updateBombTracks(List<RawBomb> detections, long nowMs) {
        boolean[] used = new boolean[detections.size()];

        for (BombTrack t : bombTracks) {
            int best = -1;
            float bestD2 = Float.MAX_VALUE;
            for (int i = 0; i < detections.size(); i++) {
                if (used[i]) continue;
                RawBomb d = detections.get(i);
                float dx = d.x - t.x;
                float dy = d.y - t.y;
                float d2 = dx * dx + dy * dy;
                float gate = Math.max(150f, t.radius * 4.0f);
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
                t.vx = t.vx * 0.35f + nvx * 0.65f;
                t.vy = t.vy * 0.35f + nvy * 0.65f;
                t.x = d.x;
                t.y = d.y;
                t.radius = Math.max(t.radius * 0.40f + d.radius * 0.60f, d.radius);
                t.timestampMs = nowMs;
                t.misses = 0;
            } else {
                t.misses++;
            }
        }

        for (int i = 0; i < detections.size(); i++) {
            if (used[i]) continue;
            RawBomb d = detections.get(i);
            BombTrack t = new BombTrack();
            t.x = d.x;
            t.y = d.y;
            t.radius = d.radius;
            t.timestampMs = nowMs;
            bombTracks.add(t);
        }

        for (int i = bombTracks.size() - 1; i >= 0; i--) {
            if (bombTracks.get(i).misses > 5) bombTracks.remove(i);
        }

        ArrayList<FruitNinjaLogic.Bomb> out = new ArrayList<>();
        for (BombTrack t : bombTracks) {
            if (t.misses > 4) continue;
            float speed = (float)Math.sqrt(t.vx * t.vx + t.vy * t.vy);
            float motionPadding = Math.min(125f, speed * 0.060f);
            float stalePadding = t.misses * 22f;
            out.add(new FruitNinjaLogic.Bomb(
                    t.x,
                    t.y,
                    Math.max(42f, t.radius + motionPadding + stalePadding),
                    t.vx,
                    t.vy,
                    nowMs));
        }
        return out;
    }

    private List<FruitNinjaLogic.Fruit> updateFruitTracks(
            List<RawFruit> detections,
            long nowMs,
            List<FruitNinjaLogic.Bomb> bombs) {

        boolean[] used = new boolean[detections.size()];
        for (FruitTrack t : fruitTracks) {
            int best = -1;
            float bestD2 = Float.MAX_VALUE;
            for (int i = 0; i < detections.size(); i++) {
                if (used[i]) continue;
                RawFruit d = detections.get(i);
                float dx = d.x - t.x;
                float dy = d.y - t.y;
                float d2 = dx * dx + dy * dy;
                float gate = Math.max(72f, Math.max(t.size, d.size) * 2.2f);
                if (d2 < gate * gate && d2 < bestD2) {
                    bestD2 = d2;
                    best = i;
                }
            }

            if (best >= 0) {
                RawFruit d = detections.get(best);
                used[best] = true;
                float dt = Math.max(0.016f, Math.min(0.25f, (nowMs - t.timestampMs) / 1000f));
                float nvx = (d.x - t.x) / dt;
                float nvy = (d.y - t.y) / dt;
                t.vx = t.vx * 0.45f + nvx * 0.55f;
                t.vy = t.vy * 0.45f + nvy * 0.55f;
                t.x = d.x;
                t.y = d.y;
                t.size = t.size * 0.35f + d.size * 0.65f;
                t.confidence = Math.max(t.confidence * 0.70f, d.confidence);
                t.timestampMs = nowMs;
                t.hits++;
                t.misses = 0;
            } else {
                t.misses++;
            }
        }

        for (int i = 0; i < detections.size(); i++) {
            if (used[i]) continue;
            RawFruit d = detections.get(i);
            FruitTrack t = new FruitTrack();
            t.x = d.x;
            t.y = d.y;
            t.size = d.size;
            t.confidence = d.confidence;
            t.timestampMs = nowMs;
            t.hits = 1;
            fruitTracks.add(t);
        }

        for (int i = fruitTracks.size() - 1; i >= 0; i--) {
            FruitTrack t = fruitTracks.get(i);
            if (t.misses > 2 || nowMs - t.timestampMs > 260) fruitTracks.remove(i);
        }

        ArrayList<FruitNinjaLogic.Fruit> out = new ArrayList<>();
        for (FruitTrack t : fruitTracks) {
            // Só corta algo que tenha sobrevivido a pelo menos dois quadros.
            // Isso elimina a maior parte de respingos, flash do corte e partículas.
            if (t.hits < 2 || t.misses != 0 || t.confidence < 0.56f) continue;
            if (!pointFarFromBombs(t.x, t.y, t.size, bombs)) continue;
            out.add(new FruitNinjaLogic.Fruit(t.x, t.y, t.size, t.confidence));
        }

        Collections.sort(out, new Comparator<FruitNinjaLogic.Fruit>() {
            @Override public int compare(FruitNinjaLogic.Fruit a, FruitNinjaLogic.Fruit b) {
                return Float.compare(b.confidence, a.confidence);
            }
        });
        if (out.size() > 12) return new ArrayList<>(out.subList(0, 12));
        return out;
    }

    private boolean pointFarFromBombs(
            float x,
            float y,
            float size,
            List<FruitNinjaLogic.Bomb> bombs) {
        if (bombs == null || bombs.isEmpty()) return true;
        for (FruitNinjaLogic.Bomb b : bombs) {
            float dx = x - b.x;
            float dy = y - b.y;
            float limit = Math.max(52f, b.radius) + Math.max(34f, size * 0.45f);
            if (dx * dx + dy * dy <= limit * limit) return false;
        }
        return true;
    }

    private static final class ScanEvidence {
        int red;
        int purple;
        int white;
        int hot;
        int dark;
        int sampled;
        float darkRatio;
    }

    private ScanEvidence scanEvidence(
            PixelSource src,
            int width,
            int height,
            int x0,
            int y0,
            int x1,
            int y1,
            int sample) {

        ScanEvidence ev = new ScanEvidence();
        x0 = clamp(x0, 0, width - 1);
        y0 = clamp(y0, 0, height - 1);
        x1 = clamp(x1, 0, width - 1);
        y1 = clamp(y1, 0, height - 1);

        for (int y = y0; y <= y1; y += sample) {
            for (int x = x0; x <= x1; x += sample) {
                int c = src.getArgb(x, y);
                int r = (c >> 16) & 255;
                int g = (c >> 8) & 255;
                int b = c & 255;
                int lum = (r * 3 + g * 6 + b) / 10;
                ev.sampled++;
                if (lum < 118) ev.dark++;
                if (isBombRed(r, g, b)) ev.red++;
                if (isPurple(r, g, b)) ev.purple++;
                if (r >= 190 && g >= 190 && b >= 190) ev.white++;
                if (isHotFuseColor(r, g, b)) ev.hot++;
            }
        }
        ev.darkRatio = ev.sampled == 0 ? 0f : ev.dark / (float)ev.sampled;
        return ev;
    }

    private static boolean isPurple(int r, int g, int b) {
        return (b >= 105 && r >= 80 && b >= g + 30 && r >= g + 18)
                || (r >= 130 && b >= 145 && g <= 125 && r + b >= 300);
    }

    private static boolean isBombRed(int r, int g, int b) {
        return r >= 125 && r >= g + 34 && r >= b + 24 && g <= 155;
    }

    private static boolean isHotFuseColor(int r, int g, int b) {
        return (r >= 135 && g >= 25 && g <= 195 && b <= 135)
                || (r >= 185 && g >= 115 && b <= 145)
                || (r >= 215 && g >= 175 && b <= 170);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
