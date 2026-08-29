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

    private static void drawClassicBomb(int[] img,int w,int h,int x,int y) {
        drawCircle(img,w,h,x,y,29,0xffe22d2d);
        drawCircle(img,w,h,x,y,22,0xff171a20);
        drawCircle(img,w,h,x+19,y-19,5,0xffffa12d);
        drawCircle(img,w,h,x+23,y-23,3,0xffffffff);
    }

    private static void drawPurpleBomb(int[] img,int w,int h,int x,int y) {
        drawCircle(img,w,h,x,y,30,0xffb84cff);
        drawCircle(img,w,h,x,y,22,0xff171822);
        drawCircle(img,w,h,x+3,y,5,0xfff4efff);
        drawCircle(img,w,h,x+22,y-19,4,0xffdba2ff);
        drawCircle(img,w,h,x+25,y-23,3,0xffffffff);
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

        int w=480,h=320;
        int bg=0xff4d2b18;
        int[] frame1=new int[w*h];
        int[] frame2=new int[w*h];
        int[] frame3=new int[w*h];
        Arrays.fill(frame1,bg);
        Arrays.fill(frame2,bg);
        Arrays.fill(frame3,bg);

        drawClassicBomb(frame1,w,h,125,205);
        drawClassicBomb(frame2,w,h,139,194);
        drawClassicBomb(frame3,w,h,153,183);

        drawCircle(frame1,w,h,355,215,23,0xff39c954);
        drawCircle(frame2,w,h,345,201,23,0xff39c954);
        drawCircle(frame3,w,h,335,187,23,0xff39c954);

        FruitNinjaDetector d = new FruitNinjaDetector();
        d.analyzeArgb(frame1,w,h,1000);
        FruitNinjaDetector.Result r2 = d.analyzeArgb(frame2,w,h,1040);
        FruitNinjaDetector.Result r = d.analyzeArgb(frame3,w,h,1080);

        check(!r2.bombs.isEmpty(), "classic moving bomb should be detected by second frame");
        check(!r.bombs.isEmpty(), "classic moving bomb should remain tracked");
        check(!r.fruits.isEmpty(), "moving fruit should survive temporal tracking");
        FruitNinjaLogic.Bomb db = r.bombs.get(0);
        check(db.vx > 0, "tracked classic bomb should have positive x velocity");
        check(db.predictedX(100) > db.x, "prediction should move classic bomb forward");

        int[] p1=new int[w*h];
        int[] p2=new int[w*h];
        int[] p3=new int[w*h];
        Arrays.fill(p1,bg);
        Arrays.fill(p2,bg);
        Arrays.fill(p3,bg);
        drawPurpleBomb(p1,w,h,210,210);
        drawPurpleBomb(p2,w,h,224,196);
        drawPurpleBomb(p3,w,h,238,182);

        FruitNinjaDetector arcade = new FruitNinjaDetector();
        arcade.analyzeArgb(p1,w,h,2000);
        FruitNinjaDetector.Result pr2 = arcade.analyzeArgb(p2,w,h,2040);
        FruitNinjaDetector.Result pr3 = arcade.analyzeArgb(p3,w,h,2080);
        check(!pr2.bombs.isEmpty(), "purple arcade bomb should be detected by second frame");
        check(!pr3.bombs.isEmpty(), "purple arcade bomb should remain tracked");
        FruitNinjaLogic.Bomb pb = pr3.bombs.get(0);
        check(!FruitNinjaLogic.pointClear(pb.x,pb.y,pr3.bombs,120,20), "purple bomb center must be unsafe");

        // Um flash/respingo que aparece apenas em um quadro não pode virar alvo de corte.
        int[] e1=new int[w*h];
        int[] e2=new int[w*h];
        int[] e3=new int[w*h];
        Arrays.fill(e1,bg);
        Arrays.fill(e2,bg);
        Arrays.fill(e3,bg);
        drawCircle(e2,w,h,300,190,20,0xffffcc33);
        FruitNinjaDetector effects = new FruitNinjaDetector();
        effects.analyzeArgb(e1,w,h,3000);
        FruitNinjaDetector.Result er2 = effects.analyzeArgb(e2,w,h,3040);
        FruitNinjaDetector.Result er3 = effects.analyzeArgb(e3,w,h,3080);
        check(er2.fruits.isEmpty(), "single-frame effect must not be cut immediately");
        check(er3.fruits.isEmpty(), "single-frame effect must disappear without becoming fruit");

        System.out.println("FruitNinjaLogicSelfTest OK classicBombs="+r.bombs.size()+
                ", purpleBombs="+pr3.bombs.size()+", fruits="+r.fruits.size()+
                ", analysisMs="+r.analysisMs);
    }
}
