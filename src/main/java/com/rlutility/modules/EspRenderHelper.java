package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * ESP / tracer rendering.
 *
 * <p>Rewritten for performance and stability: the world lists are snapshotted before iterating
 * (the old code iterated {@code loadedTileEntityList} live, which can throw
 * {@code ConcurrentModificationException} on chunk load), everything is distance-culled against a
 * configurable range, and tracers are drawn in a single batched draw call.</p>
 */
public class EspRenderHelper {

    private static final float[] COLOR_CHEST    = {1.00F, 0.80F, 0.00F};
    private static final float[] COLOR_SPAWNER  = {0.90F, 0.10F, 0.10F};
    private static final float[] COLOR_WAYSTONE = {0.10F, 0.90F, 0.90F};
    private static final float[] COLOR_BOSS     = {0.80F, 0.00F, 1.00F};
    private static final float[] COLOR_HOSTILE  = {1.00F, 0.35F, 0.35F};
    private static final float[] COLOR_PLAYER   = {0.30F, 0.70F, 1.00F};
    private static final float[] COLOR_ITEM     = {0.60F, 1.00F, 0.60F};
    private static final float[] COLOR_CUSTOM   = {1.00F, 0.45F, 0.90F};
    private static final float[] COLOR_MODDED   = {0.65F, 0.45F, 1.00F};

    private static final TargetList CUSTOM_ENTITIES = new TargetList("ESP entities");
    private static final TargetList CUSTOM_BLOCKS = new TargetList("ESP blocks");

    /** Registry id of an entity, e.g. "iceandfire:dragon_fire". Null for unregistered entities. */
    public static String idOf(Entity entity) {
        try {
            net.minecraft.util.ResourceLocation key = EntityList.getKey(entity);
            return key == null ? null : key.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Registry id of the block backing a tile entity. */
    public static String idOf(TileEntity te) {
        try {
            net.minecraft.block.Block block = te.getBlockType();
            if (block == null || block.getRegistryName() == null) return null;
            return block.getRegistryName().toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (!anyEspEnabled()) return;

        final float partialTicks = event.getPartialTicks();
        final double viewerX = mc.getRenderManager().viewerPosX;
        final double viewerY = mc.getRenderManager().viewerPosY;
        final double viewerZ = mc.getRenderManager().viewerPosZ;
        final double range = FeatureConfig.espRange;
        final double rangeSq = range * range;

        // Collected here so tracers can be drawn in one batch afterwards.
        List<double[]> tracerTargets = new ArrayList<>();

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GlStateManager.glLineWidth(1.5F);

        try {
            // ---------------------------------------------------------- blocks
            if (FeatureConfig.espChests || FeatureConfig.espSpawners || FeatureConfig.espWaystones
                    || FeatureConfig.espAllContainers || !CUSTOM_BLOCKS.isEmpty(FeatureConfig.espCustomBlocks)) {
                List<TileEntity> tiles = new ArrayList<>(mc.world.loadedTileEntityList);
                for (TileEntity te : tiles) {
                    if (te == null || te.isInvalid()) continue;
                    BlockPos pos = te.getPos();
                    if (mc.player.getDistanceSq(pos) > rangeSq) continue;

                    float[] color = colorFor(te);
                    if (color == null) continue;

                    double x = pos.getX() - viewerX;
                    double y = pos.getY() - viewerY;
                    double z = pos.getZ() - viewerZ;
                    drawStyled(new AxisAlignedBB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D), color, 0.7F);

                    if (FeatureConfig.espTracers) {
                        tracerTargets.add(new double[]{x + 0.5D, y + 0.5D, z + 0.5D, color[0], color[1], color[2]});
                    }
                }
            }

            // -------------------------------------------------------- entities
            if (FeatureConfig.espDragons || FeatureConfig.espHostiles
                    || FeatureConfig.espPlayers || FeatureConfig.espItems
                    || FeatureConfig.espModdedMobs || !CUSTOM_ENTITIES.isEmpty(FeatureConfig.espCustomEntities)) {
                List<Entity> entities = new ArrayList<>(mc.world.loadedEntityList);
                for (Entity entity : entities) {
                    if (entity == null || entity == mc.player || entity.isDead) continue;
                    if (mc.player.getDistanceSq(entity) > rangeSq) continue;

                    float[] color = colorFor(entity);
                    if (color == null) continue;

                    double ix = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - entity.posX;
                    double iy = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - entity.posY;
                    double iz = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - entity.posZ;

                    AxisAlignedBB raw = entity.getEntityBoundingBox();
                    AxisAlignedBB bb = new AxisAlignedBB(
                            raw.minX + ix - viewerX, raw.minY + iy - viewerY, raw.minZ + iz - viewerZ,
                            raw.maxX + ix - viewerX, raw.maxY + iy - viewerY, raw.maxZ + iz - viewerZ);

                    drawStyled(bb, color, 0.85F);

                    if (FeatureConfig.espTracers) {
                        tracerTargets.add(new double[]{
                                (bb.minX + bb.maxX) / 2.0D, (bb.minY + bb.maxY) / 2.0D, (bb.minZ + bb.maxZ) / 2.0D,
                                color[0], color[1], color[2]});
                    }
                }
            }

            if (FeatureConfig.espTracers && !tracerTargets.isEmpty()) {
                drawTracers(mc, partialTicks, tracerTargets);
            }
        } catch (Throwable ignored) {
            // Never let a render hiccup crash the game.
        } finally {
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GlStateManager.enableLighting();
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    /**
     * Draws a highlight in the configured style.
     *
     * <p>0 = full box, 1 = corner brackets, 2 = translucent fill plus outline, 3 = flat outline on
     * the ground. A true per-pixel model glow needs either a stencil pass or a shader hooked into
     * the entity renderer; that is a much larger change and cannot be validated without running the
     * game, so these are geometry-based styles that are safe to ship.</p>
     */
    private static void drawStyled(AxisAlignedBB bb, float[] color, float alpha) {
        int style = FeatureConfig.espStyle;
        float width = (float) Math.max(0.5D, Math.min(6.0D, FeatureConfig.espLineWidth));
        GlStateManager.glLineWidth(width);

        if (style == 2) {
            drawFilled(bb, color, 0.18F * alpha);
            RenderGlobal.drawSelectionBoundingBox(bb, color[0], color[1], color[2], alpha);
            return;
        }
        if (style == 1) {
            drawCorners(bb, color, alpha);
            return;
        }
        if (style == 3) {
            AxisAlignedBB flat = new AxisAlignedBB(bb.minX, bb.minY, bb.minZ,
                    bb.maxX, bb.minY + 0.02D, bb.maxZ);
            RenderGlobal.drawSelectionBoundingBox(flat, color[0], color[1], color[2], alpha);
            return;
        }
        RenderGlobal.drawSelectionBoundingBox(bb, color[0], color[1], color[2], alpha);
    }

    /** Corner brackets: reads much cleaner than a full box when many targets overlap. */
    private static void drawCorners(AxisAlignedBB bb, float[] color, float alpha) {
        double lx = (bb.maxX - bb.minX) * 0.25D;
        double ly = (bb.maxY - bb.minY) * 0.25D;
        double lz = (bb.maxZ - bb.minZ) * 0.25D;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int cx = 0; cx < 2; cx++) {
            for (int cy = 0; cy < 2; cy++) {
                for (int cz = 0; cz < 2; cz++) {
                    double x = cx == 0 ? bb.minX : bb.maxX;
                    double y = cy == 0 ? bb.minY : bb.maxY;
                    double z = cz == 0 ? bb.minZ : bb.maxZ;
                    double dx = cx == 0 ? lx : -lx;
                    double dy = cy == 0 ? ly : -ly;
                    double dz = cz == 0 ? lz : -lz;
                    vertex(buf, x, y, z, color, alpha);
                    vertex(buf, x + dx, y, z, color, alpha);
                    vertex(buf, x, y, z, color, alpha);
                    vertex(buf, x, y + dy, z, color, alpha);
                    vertex(buf, x, y, z, color, alpha);
                    vertex(buf, x, y, z + dz, color, alpha);
                }
            }
        }
        tess.draw();
    }

    private static void drawFilled(AxisAlignedBB bb, float[] color, float alpha) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        double[][] faces = {
                {bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ},
                {bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ}
        };
        for (double[] f : faces) {
            vertex(buf, f[0], f[1], f[2], color, alpha);
            vertex(buf, f[3], f[1], f[2], color, alpha);
            vertex(buf, f[3], f[4], f[5], color, alpha);
            vertex(buf, f[0], f[4], f[5], color, alpha);
        }
        // Vertical faces
        double[][] sides = {
                {bb.minX, bb.minZ, bb.maxX, bb.minZ}, {bb.maxX, bb.minZ, bb.maxX, bb.maxZ},
                {bb.maxX, bb.maxZ, bb.minX, bb.maxZ}, {bb.minX, bb.maxZ, bb.minX, bb.minZ}
        };
        for (double[] sd : sides) {
            vertex(buf, sd[0], bb.minY, sd[1], color, alpha);
            vertex(buf, sd[2], bb.minY, sd[3], color, alpha);
            vertex(buf, sd[2], bb.maxY, sd[3], color, alpha);
            vertex(buf, sd[0], bb.maxY, sd[1], color, alpha);
        }
        tess.draw();
    }

    private static void vertex(BufferBuilder buf, double x, double y, double z, float[] c, float a) {
        buf.pos(x, y, z).color(c[0], c[1], c[2], a).endVertex();
    }

    private static void drawTracers(Minecraft mc, float partialTicks, List<double[]> targets) {
        Vec3d look = mc.getRenderViewEntity() != null
                ? mc.getRenderViewEntity().getLook(partialTicks)
                : mc.player.getLook(partialTicks);

        double sx = look.x;
        double sy = mc.player.getEyeHeight() + look.y;
        double sz = look.z;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        for (double[] t : targets) {
            buffer.pos(sx, sy, sz).color((float) t[3], (float) t[4], (float) t[5], 0.55F).endVertex();
            buffer.pos(t[0], t[1], t[2]).color((float) t[3], (float) t[4], (float) t[5], 0.55F).endVertex();
        }
        tessellator.draw();
    }

    private static float[] colorFor(TileEntity te) {
        if (FeatureConfig.espChests
                && (te instanceof TileEntityChest || te instanceof TileEntityEnderChest || te instanceof TileEntityShulkerBox)) {
            return COLOR_CHEST;
        }
        if (FeatureConfig.espSpawners && te instanceof TileEntityMobSpawner) return COLOR_SPAWNER;
        if (FeatureConfig.espWaystones && te.getClass().getName().toLowerCase().contains("waystone")) return COLOR_WAYSTONE;

        // Anything with an inventory - catches modded chests, barrels, backpacks on stands, etc.
        if (FeatureConfig.espAllContainers && isContainer(te)) return COLOR_CHEST;
        if (CUSTOM_BLOCKS.contains(FeatureConfig.espCustomBlocks, idOf(te))) return COLOR_CUSTOM;
        return null;
    }

    private static float[] colorFor(Entity entity) {
        String id = idOf(entity);

        // The explicit list wins, so you can pin one specific mob even with the broad toggles off.
        if (CUSTOM_ENTITIES.contains(FeatureConfig.espCustomEntities, id)) return COLOR_CUSTOM;

        if (FeatureConfig.espDragons && isBoss(entity)) return COLOR_BOSS;
        if (FeatureConfig.espPlayers && entity instanceof EntityPlayer) return COLOR_PLAYER;
        if (FeatureConfig.espHostiles && (entity instanceof IMob) && entity instanceof EntityLivingBase) return COLOR_HOSTILE;
        if (FeatureConfig.espItems && entity instanceof EntityItem) return COLOR_ITEM;

        if (FeatureConfig.espAllContainers && isContainerEntity(entity)) return COLOR_CHEST;

        if (FeatureConfig.espModdedMobs && entity instanceof EntityLivingBase
                && id != null && !id.startsWith("minecraft:")) {
            return COLOR_MODDED;
        }
        return null;
    }

    /**
     * Anything that actually holds items. A plain "instanceof IInventory" missed a lot: modded
     * chests expose their inventory only through the ITEM_HANDLER capability, and several have a
     * size of zero until first opened.
     */
    private static boolean isContainer(TileEntity te) {
        try {
            if (te instanceof IInventory && ((IInventory) te).getSizeInventory() > 0) return true;
            if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) return true;
            // Last resort: the block name. Catches barrels, crates, urns, lockers and similar.
            String id = idOf(te);
            if (id != null) {
                String n = id.toLowerCase();
                if (n.contains("chest") || n.contains("barrel") || n.contains("crate")
                        || n.contains("urn") || n.contains("locker") || n.contains("safe")
                        || n.contains("strongbox") || n.contains("shulker") || n.contains("sack")
                        || n.contains("basket") || n.contains("box")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** Entity-based containers: chest minecarts, chest boats, item frames and similar. */
    private static boolean isContainerEntity(Entity entity) {
        try {
            if (entity instanceof net.minecraft.inventory.IInventory) return true;
            if (entity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isBoss(Entity entity) {
        if (entity instanceof EntityDragon) return true;
        String name = entity.getClass().getName().toLowerCase();
        return name.contains("dragon") || name.contains("seaserpent") || name.contains("cyclops")
                || name.contains("hydra") || name.contains("gorgon") || name.contains("myrmex")
                || name.contains("wither");
    }

    private static boolean anyEspEnabled() {
        return FeatureConfig.espChests || FeatureConfig.espSpawners || FeatureConfig.espWaystones
                || FeatureConfig.espDragons || FeatureConfig.espHostiles || FeatureConfig.espPlayers
                || FeatureConfig.espItems || FeatureConfig.espModdedMobs || FeatureConfig.espAllContainers
                || !CUSTOM_ENTITIES.isEmpty(FeatureConfig.espCustomEntities)
                || !CUSTOM_BLOCKS.isEmpty(FeatureConfig.espCustomBlocks);
    }

    // ------------------------------------------------------------ list editing

    public static java.util.List<String> customEntities() {
        return CUSTOM_ENTITIES.entries(FeatureConfig.espCustomEntities);
    }

    public static java.util.List<String> customBlocks() {
        return CUSTOM_BLOCKS.entries(FeatureConfig.espCustomBlocks);
    }

    public static void toggleEntity(String id) {
        FeatureConfig.espCustomEntities = TargetList.toggle(FeatureConfig.espCustomEntities, id);
        FeatureConfig.saveConfig();
    }

    public static void toggleBlock(String id) {
        FeatureConfig.espCustomBlocks = TargetList.toggle(FeatureConfig.espCustomBlocks, id);
        FeatureConfig.saveConfig();
    }

    /** Registry id of whatever the crosshair is on - entity first, then block. */
    public static String lookingAtId() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            net.minecraft.util.math.RayTraceResult hit = mc.objectMouseOver;
            if (hit == null) return null;
            if (hit.entityHit != null) return idOf(hit.entityHit);
            if (hit.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK && hit.getBlockPos() != null) {
                net.minecraft.block.Block block = mc.world.getBlockState(hit.getBlockPos()).getBlock();
                return block.getRegistryName() == null ? null : block.getRegistryName().toString();
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
