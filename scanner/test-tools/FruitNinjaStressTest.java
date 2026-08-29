package com.autoclicker.android;

import java.util.Arrays;
import java.util.Random;

public final class FruitNinjaStressTest {
    private static void circle(int[] a,int w,int h,int cx,int cy,int r,int c){
        int rr=r*r;
        for(int y=Math.max(0,cy-r);y<Math.min(h,cy+r+1);y++){
            for(int x=Math.max(0,cx-r);x<Math.min(w,cx+r+1);x++){
                int dx=x-cx,dy=y-cy;
                if(dx*dx+dy*dy<=rr)a[y*w+x]=c;
            }
        }
    }

    private static void purpleBomb(int[] p,int w,int h,int x,int y){
        circle(p,w,h,x,y,28,0xffb84cff);
        circle(p,w,h,x,y,21,0xff171822);
        circle(p,w,h,x+3,y,4,0xffffffff);
        circle(p,w,h,x+22,y-20,4,0xffdba2ff);
        circle(p,w,h,x+25,y-23,3,0xffffffff);
    }

    public static void main(String[] args){
        int w=640,h=360;
        FruitNinjaDetector d=new FruitNinjaDetector();
        Random random=new Random(42);
        long max=0,sum=0;
        for(int f=0;f<120;f++){
            int[] p=new int[w*h];
            Arrays.fill(p,0xff4e2c19);
            for(int i=0;i<7;i++){
                int x=(60+i*75+f*(3+i)%130)%w;
                int y=100+(i*31+f*4)%210;
                int[] cs={0xff45c95a,0xffffb12b,0xffe94242,0xff8cd337,0xffbf4fd3};
                circle(p,w,h,x,y,18+(i%3)*4,cs[i%cs.length]);
            }
            for(int i=0;i<2;i++){
                int x=140+i*190+(f*7)%80;
                int y=105+i*70+(f*5)%95;
                circle(p,w,h,x,y,29,0xffe22d2d);
                circle(p,w,h,x,y,22,0xff181818);
                circle(p,w,h,x+20,y-20,5,0xffff8a20);
                circle(p,w,h,x+24,y-24,3,0xffffffff);
            }
            int px=260+(f*6)%120;
            int py=105+(f*4)%135;
            purpleBomb(p,w,h,px,py);

            long started=System.nanoTime();
            FruitNinjaDetector.Result q=d.analyzeArgb(p,w,h,1000+f*40);
            long ms=(System.nanoTime()-started)/1_000_000L;
            max=Math.max(max,ms);
            sum+=ms;
            if(q.bombs.size()>16 || q.fruits.size()>12) {
                throw new AssertionError("candidate cap broken");
            }
        }
        double avg=sum/120.0;
        if(avg>30.0) throw new AssertionError("detector too slow: "+avg+" ms");
        System.out.println("FruitNinjaStressTest OK avg="+avg+" ms max="+max+" ms");
    }
}
