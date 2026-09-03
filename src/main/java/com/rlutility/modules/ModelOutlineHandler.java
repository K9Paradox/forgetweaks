package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Crisp wireframe outline of the entity's actual model, with adjustable thickness.
 *
 * <h3>Why not the vanilla glow</h3>
 * Vanilla's glow pass renders the silhouette into a framebuffer and runs a blur/edge-detect
 * composite over it - a soft halo with baked-in thickness. This draws the outline directly
 * instead: the entity's model is rendered a second time in {@code GL_LINE} polygon mode with
 * lighting and texturing off. That traces the real model edges rather than a bounding box, and
 * because it is ordinary line rasterising, {@code glLineWidth} controls thickness exactly.
 *
 * <h3>Why outlines used to be white</h3>
 * The previous implementation set the GL colour and then called {@code renderer.doRender(...)}.
 * Nearly every {@code RenderLivingBase#doRender} implementation resets the GL colour to white
 * inside the call (for its own texture tinting), so the colour we set was clobbered before the
 * model drew - every living outline came out white no matter the category colour. The fix is to
 * skip {@code doRender} entirely and drive the renderer's model directly:
 * {@code getMainModel().render(entity, limbSwing, ..., 0.0625F)}, reproducing the same transform
 * sequence vanilla uses (translate, body-yaw rotation, the Y-flip scale) so the wireframe sits
 * exactly on the rendered mob. Whatever colour is in the GL state when the model draws is the
 * colour of the outline.
 *
 * <h3>Fallback</h3>
 * Entities whose renderer has no readable main model get a coloured box wireframe instead, so
 * "Outline" style never renders nothing.
 *
 * <h3>Recursion guard</h3>
 * Re-rendering from inside {@link RenderLivingEvent.Post} fires the same event again. A
 * re-entrancy flag makes the nested pass a no-op, otherwise the first outlined entity would
 * recurse until the stack overflowed.
 */
public class ModelOutlineHandler {

    /** True while we are inside our own second render pass. */
    private static boolean rendering = false;
    private static int outlined = 0;

    public static int getOutlinedCount() {
        return outlined;
    }

    @SubscribeEvent
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
        float partialTicks = event.getPartialRenderTick();

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

            // The colour is set right before the model draws and nothing in between resets it,
            // which is exactly what the old doRender path failed to guarantee.
            GlStateManager.color(color[0], color[1], color[2], 1.0F);

            boolean drawn = false;
            if (renderer instanceof RenderLivingBase) {
                drawn = drawModelOutline((RenderLivingBase<?>) renderer, entity,
                        event.getX(), event.getY(), event.getZ(), partialTicks);
            }
            if (!drawn) {
                drawBoxFallback(entity, event.getX(), event.getY(), event.getZ(), color);
            }
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

    /**
     * Drives the renderer's main model directly, with the same transform sequence vanilla uses in
     * {@code doRender}: translate to the render position, rotate by the interpolated body yaw,
     * flip the Y axis (models are authored Y-down). Returns false when no model is available so
     * the caller can fall back to a box.
     */
    private static boolean drawModelOutline(RenderLivingBase<?> renderer, EntityLivingBase entity,
                                            double x, double y, double z, float partialTicks) {
        ModelBase model;
        try {
            model = renderer.getMainModel();
        } catch (Throwable t) {
            return false;
        }
        if (model == null) return false;

        // Interpolated animation state, mirroring RenderLivingBase#doRender.
        float limbSwingAmount = entity.prevLimbSwingAmount
                + (entity.limbSwingAmount - entity.prevLimbSwingAmount) * partialTicks;
        float limbSwing = entity.limbSwing - entity.limbSwingAmount * (1.0F - partialTicks);
        if (limbSwingAmount > 1.0F) limbSwingAmount = 1.0F;
        float ageInTicks = entity.ticksExisted + partialTicks;
        float headYaw = interpolateRotation(entity.prevRotationYawHead, entity.rotationYawHead, partialTicks);
        float bodyYaw = interpolateRotation(entity.prevRenderYawOffset, entity.renderYawOffset, partialTicks);
        float netHeadYaw = headYaw - bodyYaw;
        float headPitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;

        model.isChild = entity.isChild();
        model.isRiding = entity.isRiding();
        try {
            model.swingProgress = entity.getSwingProgress(partialTicks);
        } catch (Throwable ignored) {}

        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(180.0F - bodyYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        // Child proportions are handled by the model itself through model.isChild above.

        try {
            // Pose the model parts first (heads, tails, wings), then trace the geometry.
            model.setLivingAnimations(entity, limbSwing, limbSwingAmount, partialTicks);
        } catch (Throwable ignored) {
            // Some modded models are picky; an unposed outline is better than none.
        }
        try {
            model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, 0.0625F);
        } catch (Throwable t) {
            return false;
        }
        return true;
    }

    /** Colored box for entities whose model could not be traced. */
    private static void drawBoxFallback(Entity entity, double x, double y, double z, float[] color) {
        try {
            AxisAlignedBB raw = entity.getEntityBoundingBox();
            double dx = x - entity.posX;
            double dy = y - entity.posY;
            double dz = z - entity.posZ;
            RenderGlobal.drawSelectionBoundingBox(
                    raw.offset(dx, dy, dz), color[0], color[1], color[2], 0.85F);
        } catch (Throwable ignored) {}
    }

    /** Vanilla's own interpolation helper, duplicated to stay independent of mapping changes. */
    private static float interpolateRotation(float lastYaw, float yaw, float partialTicks) {
        float f = MathHelper.wrapDegrees(yaw - lastYaw);
        return lastYaw + f * partialTicks;
    }

    /** Blocks are not living entities, so they keep using the geometry styles. */
    public static boolean handles(Entity entity) {
        return entity instanceof EntityLivingBase;
    }
}
