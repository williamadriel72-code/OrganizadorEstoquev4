package com.autoclicker.android;

import java.util.Arrays;
import java.util.List;

public final class FruitNinjaLogicSelfTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void drawCircle(int[] img, int w, int h, int cx, int cy, int radius, int color) {
        int r2 = radius * radius;
        for (int y = Math.max(0, cy-radius); y < Math.min(h, cy+radius+1); y++) {
            for (int x = Math.max(0, cx-radius); x < Math.min(w, cx+radius+1); x++) {
                int dx=x-cx, dy=y-cy;
                if (dx*dx+dy*dy <= r2) img[y*w+x]=color;
            }
        }
    }

    public static void main(String[] args) {
        FruitNinjaLogic.Bomb b = new FruitNinjaLogic.Bomb(100, 100, 30, 100, 0, 0);
        List<FruitNinjaLogic.Bomb> bombs = Arrays.asList(b);
        check(!FruitNinjaLogic.segmentClear(50,100,150,100,bombs,100,10), "segment through bomb must be blocked");
        check(FruitNinjaLogic.segmentClear(50,20,150,20,bombs,100,10), "far segment must be safe");

        FruitNinjaLogic.Fruit f = new FruitNinjaLogic.Fruit(100, 25, 40, 1f);
        float[] slice = FruitNinjaLogic.chooseSafeSlice(f,bombs,100,10,24,320,180);
        check(slice != null, "safe fruit should get a slice");
        check(FruitNinjaLogic.segmentClear(slice[0],slice[1],slice[2],slice[3],bombs,100,10), "chosen slice must be safe");

        int w=320,h=180;
        int bg=0xff4d2b18;
        int[] frame1=new int[w*h];
        int[] frame2=new int[w*h];
        int[] frame3=new int[w*h];
        Arrays.fill(frame1,bg);
        Arrays.fill(frame2,bg);
        Arrays.fill(frame3,bg);

        drawCircle(frame1,w,h,120,100,20,0xff181818);
        drawCircle(frame2,w,h,132,92,20,0xff181818);
        drawCircle(frame3,w,h,144,84,20,0xff181818);
        drawCircle(frame1,w,h,136,82,5,0xffff8a20);
        drawCircle(frame2,w,h,148,74,5,0xffff8a20);
        drawCircle(frame3,w,h,160,66,5,0xffff8a20);

        drawCircle(frame1,w,h,230,105,22,0xff39c954);
        drawCircle(frame2,w,h,222,94,22,0xff39c954);
        drawCircle(frame3,w,h,250,83,22,0xff39c954);

        FruitNinjaDetector d = new FruitNinjaDetector();
        d.analyzeArgb(frame1,w,h,1000);
        FruitNinjaDetector.Result r2 = d.analyzeArgb(frame2,w,h,1040);
        FruitNinjaDetector.Result r = d.analyzeArgb(frame3,w,h,1080);

        check(!r2.bombs.isEmpty(), "moving bomb should be detected by second frame");
        check(!r.bombs.isEmpty(), "moving bomb should remain tracked");
        check(!r.fruits.isEmpty(), "moving fruit should be detected");
        FruitNinjaLogic.Bomb db = r.bombs.get(0);
        check(db.vx > 0, "tracked bomb should have positive x velocity");
        check(db.predictedX(100) > db.x, "prediction should move bomb forward");

        System.out.println("FruitNinjaLogicSelfTest OK: bombs="+r.bombs.size()+", fruits="+r.fruits.size()+", analysisMs="+r.analysisMs);
    }
}
