package com.cobbletowers.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL30;

/** Captures the scene once per menu frame. Panels sample the copy, never their render target. */
public final class TowerShader {
    private static ShaderInstance shader;
    private static TextureTarget scene;
    private static boolean failed;
    private static float time;
    private TowerShader() {}
    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            shader=null;failed=false;
            try { context.register(ResourceLocation.fromNamespaceAndPath("cobbletowers","tower_ui"),
                DefaultVertexFormat.POSITION_TEX_COLOR,loaded->shader=loaded);
            } catch(Exception e){failed=true;org.slf4j.LoggerFactory.getLogger("cobbletowers-ui").warn("Industrial shader unavailable",e);}
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client->{if(scene!=null){scene.destroyBuffers();scene=null;}});
    }
    public static void draw(GuiGraphics g,int width,int height,TowerUi.Theme theme,long age) {
        time=TowerUiSettings.motion?(age%120000L)/1000f:0;
        if(shader==null||failed||!TowerUiSettings.shaders){g.fill(0,0,width,height,0xFF0D0E12);return;}
        g.flush();
        var target=Minecraft.getInstance().getMainRenderTarget();
        int read=GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            if(scene==null)scene=new TextureTarget(target.width,target.height,false,Minecraft.ON_OSX);
            else if(scene.width!=target.width||scene.height!=target.height)scene.resize(target.width,target.height,Minecraft.ON_OSX);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);
            GL30.glBlitFramebuffer(0,0,target.width,target.height,0,0,scene.width,scene.height,GL30.GL_COLOR_BUFFER_BIT,GL30.GL_NEAREST);
        } finally {GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);}
        quad(g,0,0,width,height,theme.accent,0,-1);
    }
    public static void panel(GuiGraphics g,int x,int y,int w,int h,int accent,float hover) {
        if(w<4||h<4)return;
        if(shader!=null&&!failed&&TowerUiSettings.shaders&&scene!=null) {
            quad(g,x,y,w,h,accent,hover,1);
        } else g.fill(x,y,x+w,y+h,0xFF181B22);
        // Pixel-aligned steel casing remains available when shaders are disabled.
        g.fill(x,y,x+w,y+1,0xFF53616B);
        g.fill(x,y+h-1,x+w,y+h,0xFF050709);
        g.fill(x,y,x+1,y+h,0xFF39434D);
        g.fill(x+w-1,y,x+w,y+h,0xFF050709);
        int corner=Math.min(7,Math.min(w,h)/3);
        for(int cx:new int[]{x,x+w-corner}) {
            g.fill(cx,y,cx+corner,y+2,hover>.1f?accent:0xFF68747B);
            g.fill(cx,y+h-2,cx+corner,y+h,0xFF39434D);
        }
        if(h>35&&w>35) for(int cx:new int[]{x+3,x+w-5}) for(int cy:new int[]{y+4,y+h-6}) {
            g.fill(cx,cy,cx+2,cy+2,0xFF75818A);
            g.fill(cx,cy+1,cx+2,cy+2,0xFF303941);
        }
    }

    private static void quad(GuiGraphics g,int x,int y,int w,int h,int accent,float hover,float mode) {
        g.flush();var previous=RenderSystem.getShader();boolean blended=GL30.glIsEnabled(GL30.GL_BLEND);
        try {
            shader.setSampler("Scene",scene.getColorTextureId());
            shader.safeGetUniform("UiTime").set(time);
            shader.safeGetUniform("UiSize").set((float)w,(float)h);
            shader.safeGetUniform("FrameSize").set((float)scene.width,(float)scene.height);
            shader.safeGetUniform("UiAccent").set(((accent>>16)&255)/255f,((accent>>8)&255)/255f,(accent&255)/255f);
            shader.safeGetUniform("UiMode").set(mode);shader.safeGetUniform("UiHover").set(hover);
            shader.safeGetUniform("UiBlur").set(TowerUiSettings.blur);
            shader.safeGetUniform("UiOpacity").set(TowerUiSettings.opacity);
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShader(()->shader);
            var m=g.pose().last().pose();var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            b.addVertex(m,x,y,0).setUv(0,0).setColor(-1);b.addVertex(m,x,y+h,0).setUv(0,1).setColor(-1);
            b.addVertex(m,x+w,y+h,0).setUv(1,1).setColor(-1);b.addVertex(m,x+w,y,0).setUv(1,0).setColor(-1);
            BufferUploader.drawWithShader(b.buildOrThrow());
        } catch(RuntimeException e){failed=true;org.slf4j.LoggerFactory.getLogger("cobbletowers-ui").warn("Industrial draw failed",e);}
        finally {RenderSystem.setShader(()->previous);if(!blended)RenderSystem.disableBlend();}
    }
}
