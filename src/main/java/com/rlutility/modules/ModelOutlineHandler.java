package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Crisp wireframe outline of the entity's actual model, with adjustable thickness.
 *
 * <h3>Why not the vanilla glow</h3>
 * The previous implementation flagged entities as glowing and let {@code RenderGlobal} draw them.
 * That works, but vanilla's outline pass renders the silhouette into a framebuffer and runs a
 * blur/edge-detect composite over it - the result is a soft halo, and its thickness and softness are
 * baked into the shader. There is no parameter to harden it.
 *
 * <p>So this draws the outline directly instead: after the entity has rendered normally, its model
 * is rendered a second time in {@code GL_LINE} polygon mode with lighting and texturing off. That
 * traces the real model edges rather than a bounding box, and because it is ordinary line rasterising,
 * {@code glLineWidth} controls thickness exactly.</p>
 *
 * <h3>Recursion guard</h3>
 * Re-rendering from inside {@link RenderLivingEvent.Post} fires the same event again. A re-entrancy
 * flag makes the nested pass a no-op, otherwise the first outlined entity would recurse until the
 * stack overflowed.
 */
public class ModelOutlineHandler {

    /** True while we are inside our own second render pass. */
    private static boolean rendering = false;
    private static int outlined = 0;

    public static int getOutlinedCount() {
        return outlined;
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onRenderLivingPost(RenderLivingEvent.Post event) {
        if (rendering) return;
        if (!FeatureConfig.espModelOutline) return;

        EntityLivingBase entity = event.getEntity();
        if (entity == null || entity.isDead) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || entity == mc.player) return;

        double rangeSq = (double) FeatureConfig.espRange * FeatureConfig.espRange;
        if (mc.player.getDistanceSq(entity) > rangeSq) return;

        EspRenderHelper.Kind kind = EspRenderHelper.kindOfPublic(entity);
        // Style 4 is "Outline"; every other style is handled by the box renderer.
        if (kind == null || kind.style() != 4) return;

        Render renderer = event.getRenderer();
        if (renderer == null) return;

        float[] color = kind.color;
        float width = (float) Math.max(0.5D, Math.min(10.0D, FeatureConfig.espOutlineWidth));

        rendering = true;
        try {
            GlStateManager.pushMatrix();
            GlStateManager.pushAttrib();

            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            // Draw through walls when asked; otherwise respect depth so it sits on the model.
            if (FeatureConfig.espOutlineThroughWalls) {
                GlStateManager.disableDepth();
            }
            GlStateManager.depthMask(false);

            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
            GL11.glLineWidth(width);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);

            GlStateManager.color(color[0], color[1], color[2], 1.0F);

            renderer.doRender(entity, event.getX(), event.getY(), event.getZ(),
                    entity.rotationYaw, event.getPartialRenderTick());
        } catch (Throwable ignored) {
            // A model that dislikes being re-rendered must never take the frame down.
        } finally {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glLineWidth(1.0F);

            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableFog();
            GlStateManager.enableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
            rendering = false;
            outlined++;
        }
    }

    /** Blocks are not living entities, so they keep using the geometry styles. */
    public static boolean handles(Entity entity) {
        return entity instanceof EntityLivingBase;
    }
}
