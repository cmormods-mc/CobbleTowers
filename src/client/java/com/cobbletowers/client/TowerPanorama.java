package com.cobbletowers.client;

import net.minecraft.client.gui.GuiGraphics;

/** Clipped pixel machinery vignette used by the terminal headers. */
final class TowerPanorama {
    private TowerPanorama() {}
    static void draw(GuiGraphics g,int x,int y,int w,int h,String region,long age,boolean animate) {
        if(w<1||h<1)return;
        int accent=region.contains("tideforge")?0xFF61C6CD:region.contains("rootvale")?0xFF8EAB68:region.contains("duskvale")?0xFFAB839E:0xFFFF8C00;
        g.enableScissor(x,y,x+w,y+h);
        g.fill(x,y,x+w,y+h,0xFF0D1219);
        // Deep server bays with recessed diagnostics and cable channels.
        for(int bx=x+8;bx<x+w;bx+=42) {
            g.fill(bx,y+8,bx+34,y+h-10,0xFF27323D);
            g.fill(bx+2,y+10,bx+32,y+h-12,0xFF10171F);
            for(int ry=y+15;ry<y+h-16;ry+=11) {
                g.fill(bx+5,ry,bx+29,ry+7,0xFF34424F);
                g.fill(bx+7,ry+2,bx+22,ry+5,0xFF101A23);
                g.fill(bx+25,ry+2,bx+27,ry+4,accent);
                g.fill(bx+9,ry+3,bx+16,ry+4,0xFF638995);
            }
            g.fill(bx-4,y,bx-2,y+h,0xFF3B4B57);
        }
        // Overhead fluorescent housing; no full-screen strobe.
        g.fill(x,y+3,x+w,y+8,0xFF344650);
        g.fill(x+8,y+4,x+w-8,y+6,0xFFB9E0DD);
        int floor=y+h-10;
        g.fill(x,floor,x+w,y+h,0xFF18212A);
        for(int tx=x;tx<x+w;tx+=12)g.fill(tx,floor+3,tx+7,floor+4,0xFF3C4A53);
        g.fill(x,floor,x+w,floor+1,0xFF61737D);
        for(int tx=x;tx<x+w;tx+=16)g.fill(tx,y+h-3,tx+8,y+h-1,0xFF89622B);
        boolean lit=!animate||!TowerUiSettings.motion||(age/900)%2==0;
        g.fill(x+w-7,y+10,x+w-4,y+13,lit?0xFFBA4938:0xFF482A28);
        g.disableScissor();
    }
}
