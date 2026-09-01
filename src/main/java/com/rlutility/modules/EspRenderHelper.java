package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public class EspRenderHelper {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.5F);

        // 1. TileEntity ESP (Chests, Spawners, Waystones)
        if (FeatureConfig.espChests || FeatureConfig.espSpawners || FeatureConfig.espWaystones) {
            for (TileEntity te : mc.world.loadedTileEntityList) {
                if (te == null) continue;
                BlockPos pos = te.getPos();
                double x = pos.getX() - viewerX;
                double y = pos.getY() - viewerY;
                double z = pos.getZ() - viewerZ;
                AxisAlignedBB bb = new AxisAlignedBB(x, y, z, x + 1.0, y + 1.0, z + 1.0);

                if (FeatureConfig.espChests && (te instanceof TileEntityChest || te instanceof TileEntityEnderChest)) {
                    renderBoundingBox(bb, 1.0f, 0.8f, 0.0f, 0.6f);
                } else if (FeatureConfig.espSpawners && te instanceof TileEntityMobSpawner) {
                    renderBoundingBox(bb, 0.9f, 0.1f, 0.1f, 0.7f);
                } else if (FeatureConfig.espWaystones && te.getClass().getName().toLowerCase().contains("waystone")) {
                    renderBoundingBox(bb, 0.1f, 0.9f, 0.9f, 0.7f);
                }
            }
        }

        // 2. Entity ESP (Dragons / Bosses)
        if (FeatureConfig.espDragons) {
            for (Entity entity : mc.world.loadedEntityList) {
                if (entity == null || entity == mc.player || entity.isDead) continue;
                String className = entity.getClass().getName().toLowerCase();
                if (entity instanceof EntityDragon || className.contains("dragon") || className.contains("seaserpent") || className.contains("cyclops")) {
                    AxisAlignedBB entityBb = entity.getEntityBoundingBox();
                    AxisAlignedBB bb = new AxisAlignedBB(
                            entityBb.minX - viewerX, entityBb.minY - viewerY, entityBb.minZ - viewerZ,
                            entityBb.maxX - viewerX, entityBb.maxY - viewerY, entityBb.maxZ - viewerZ
                    );
                    renderBoundingBox(bb, 0.8f, 0.0f, 1.0f, 0.8f);
                }
            }
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void renderBoundingBox(AxisAlignedBB bb, float r, float g, float b, float a) {
        RenderGlobal.drawSelectionBoundingBox(bb, r, g, b, a);
    }
}
