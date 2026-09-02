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
import net.minecraft.entity.item.EntityMinecartContainer;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * ESP rendering, rebuilt around a per-category {@link Kind}.
 *
 * <p>The previous version had two structural problems this rewrite fixes:</p>
 * <ul>
 *   <li>A single global style applied to every category at once, so you could not have, say, boxes
 *       on chests and brackets on mobs. Style is now a property of the {@link Kind}, alongside its
 *       colour and its enabled flag, so each category is configured independently.</li>
 *   <li>"All containers" highlighted pigs. The entity check used
 *       {@code hasCapability(ITEM_HANDLER_CAPABILITY)}, but every {@link EntityLivingBase} exposes
 *       that for its armour and hand slots - so it matched essentially all mobs. Container matching
 *       is now restricted to genuine container entities and explicitly excludes living entities.</li>
 * </ul>
 */
public class EspRenderHelper {

    /** A highlight category: what it matches, what colour it is, and how it is drawn. */
    public enum Kind {
        CHEST("Chest", 1.00F, 0.80F, 0.00F),
        SPAWNER("Spawner", 0.90F, 0.10F, 0.10F),
        WAYSTONE("Waystone", 0.10F, 0.90F, 0.90F),
        CONTAINER("Container", 1.00F, 0.65F, 0.30F),
        CUSTOM_BLOCK("Custom Block", 1.00F, 0.45F, 0.90F),
        BOSS("Boss", 0.80F, 0.00F, 1.00F),
        HOSTILE("Hostile", 1.00F, 0.35F, 0.35F),
        PLAYER("Player", 0.30F, 0.70F, 1.00F),
        ITEM("Item", 0.60F, 1.00F, 0.60F),
        MODDED("Modded Mob", 0.65F, 0.45F, 1.00F),
        CUSTOM_ENTITY("Custom Mob", 1.00F, 0.45F, 0.90F);

        public final String title;
        public final float[] color;

        Kind(String title, float r, float g, float b) {
            this.title = title;
            this.color = new float[]{r, g, b};
        }

        /** Per-kind style index into FeatureConfig.espStyles. */
        public int style() {
            int[] styles = FeatureConfig.espStyleArray();
            int i = ordinal();
            return i < styles.length ? Math.max(0, Math.min(3, styles[i])) : 0;
        }
    }

    private static final TargetList CUSTOM_ENTITIES = new TargetList("ESP entities");
    private static final TargetList CUSTOM_BLOCKS = new TargetList("ESP blocks");

    // ------------------------------------------------------------------ ids

    public static String idOf(Entity entity) {
        try {
            ResourceLocation key = EntityList.getKey(entity);
            return key == null ? null : key.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String idOf(TileEntity te) {
        try {
            net.minecraft.block.Block block = te.getBlockType();
            if (block == null || block.getRegistryName() == null) return null;
            return block.getRegistryName().toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    // --------------------------------------------------------------- render

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (!anyEspEnabled()) return;

        final float partialTicks = event.getPartialTicks();
        final double viewerX = mc.getRenderManager().viewerPosX;
        final double viewerY = mc.getRenderManager().viewerPosY;
        final double viewerZ = mc.getRenderManager().viewerPosZ;
        final double rangeSq = (double) FeatureConfig.espRange * FeatureConfig.espRange;

        List<double[]> tracerTargets = new ArrayList<>();

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        try {
            // ---------------------------------------------------------- blocks
            List<TileEntity> tiles = new ArrayList<>(mc.world.loadedTileEntityList);
            for (TileEntity te : tiles) {
                if (te == null || te.isInvalid()) continue;
                BlockPos pos = te.getPos();
                if (mc.player.getDistanceSq(pos) > rangeSq) continue;

                Kind kind = kindOf(te);
                if (kind == null) continue;

                double x = pos.getX() - viewerX;
                double y = pos.getY() - viewerY;
                double z = pos.getZ() - viewerZ;
                AxisAlignedBB bb = new AxisAlignedBB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
                drawStyled(bb, kind, 0.75F);

                if (FeatureConfig.espTracers) {
                    tracerTargets.add(new double[]{x + 0.5D, y + 0.5D, z + 0.5D,
                            kind.color[0], kind.color[1], kind.color[2]});
                }
            }

            // -------------------------------------------------------- entities
            List<Entity> entities = new ArrayList<>(mc.world.loadedEntityList);
            for (Entity entity : entities) {
                if (entity == null || entity == mc.player || entity.isDead) continue;
                if (mc.player.getDistanceSq(entity) > rangeSq) continue;

                Kind kind = kindOf(entity);
                if (kind == null) continue;

                double ix = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - entity.posX;
                double iy = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - entity.posY;
                double iz = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - entity.posZ;

                AxisAlignedBB raw = entity.getEntityBoundingBox();
                AxisAlignedBB bb = new AxisAlignedBB(
                        raw.minX + ix - viewerX, raw.minY + iy - viewerY, raw.minZ + iz - viewerZ,
                        raw.maxX + ix - viewerX, raw.maxY + iy - viewerY, raw.maxZ + iz - viewerZ);

                drawStyled(bb, kind, 0.85F);

                if (FeatureConfig.espTracers) {
                    tracerTargets.add(new double[]{
                            (bb.minX + bb.maxX) / 2.0D, (bb.minY + bb.maxY) / 2.0D, (bb.minZ + bb.maxZ) / 2.0D,
                            kind.color[0], kind.color[1], kind.color[2]});
                }
            }

            if (!tracerTargets.isEmpty()) drawTracers(mc, partialTicks, tracerTargets);
        } catch (Throwable ignored) {
        } finally {
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GlStateManager.glLineWidth(1.0F);
            GlStateManager.enableLighting();
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    // ------------------------------------------------------------ matching

    private static Kind kindOf(TileEntity te) {
        if (FeatureConfig.espChests
                && (te instanceof TileEntityChest || te instanceof TileEntityEnderChest
                    || te instanceof TileEntityShulkerBox)) {
            return Kind.CHEST;
        }
        if (FeatureConfig.espSpawners && te instanceof TileEntityMobSpawner) return Kind.SPAWNER;
        if (FeatureConfig.espWaystones
                && te.getClass().getName().toLowerCase().contains("waystone")) {
            return Kind.WAYSTONE;
        }
        if (FeatureConfig.espAllContainers && isContainerTile(te)) return Kind.CONTAINER;
        if (CUSTOM_BLOCKS.contains(FeatureConfig.espCustomBlocks, idOf(te))) return Kind.CUSTOM_BLOCK;
        return null;
    }

    private static Kind kindOf(Entity entity) {
        String id = idOf(entity);

        if (CUSTOM_ENTITIES.contains(FeatureConfig.espCustomEntities, id)) return Kind.CUSTOM_ENTITY;
        if (FeatureConfig.espDragons && isBoss(entity)) return Kind.BOSS;
        if (FeatureConfig.espPlayers && entity instanceof EntityPlayer) return Kind.PLAYER;
        if (FeatureConfig.espHostiles && entity instanceof IMob && entity instanceof EntityLivingBase) {
            return Kind.HOSTILE;
        }
        if (FeatureConfig.espItems && entity instanceof EntityItem) return Kind.ITEM;
        if (FeatureConfig.espAllContainers && isContainerEntity(entity)) return Kind.CONTAINER;
        if (FeatureConfig.espModdedMobs && entity instanceof EntityLivingBase
                && id != null && !id.startsWith("minecraft:")) {
            return Kind.MODDED;
        }
        return null;
    }

    /** Tile entities that really hold items. */
    private static boolean isContainerTile(TileEntity te) {
        try {
            if (te instanceof IInventory && ((IInventory) te).getSizeInventory() > 0) return true;
            if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) return true;
            String id = idOf(te);
            if (id != null) {
                String n = id.toLowerCase();
                return n.contains("chest") || n.contains("barrel") || n.contains("crate")
                        || n.contains("urn") || n.contains("locker") || n.contains("safe")
                        || n.contains("strongbox") || n.contains("shulker") || n.contains("basket");
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Genuine container entities only.
     *
     * <p>The old test asked whether the entity exposed {@code ITEM_HANDLER_CAPABILITY}, which every
     * living entity does - Forge wraps their armour and hand slots in one. That is why enabling
     * "all containers" lit up every pig, cow and zombie in render distance. Living entities are now
     * excluded outright and only real storage entities qualify.</p>
     */
    private static boolean isContainerEntity(Entity entity) {
        if (entity instanceof EntityLivingBase) return false;
        if (entity instanceof EntityMinecartContainer) return true;
        if (entity instanceof IInventory && ((IInventory) entity).getSizeInventory() > 0) return true;
        String id = idOf(entity);
        if (id != null) {
            String n = id.toLowerCase();
            return n.contains("chest") || n.contains("crate") || n.contains("barrel");
        }
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

    // -------------------------------------------------------------- drawing

    private static void drawStyled(AxisAlignedBB bb, Kind kind, float alpha) {
        float[] color = kind.color;
        GlStateManager.glLineWidth((float) Math.max(0.5D, Math.min(6.0D, FeatureConfig.espLineWidth)));

        switch (kind.style()) {
            case 1:
                drawCorners(bb, color, alpha);
                return;
            case 2:
                drawFilled(bb, color, 0.18F * alpha);
                RenderGlobal.drawSelectionBoundingBox(bb, color[0], color[1], color[2], alpha);
                return;
            case 3:
                RenderGlobal.drawSelectionBoundingBox(
                        new AxisAlignedBB(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY + 0.02D, bb.maxZ),
                        color[0], color[1], color[2], alpha);
                return;
            default:
                RenderGlobal.drawSelectionBoundingBox(bb, color[0], color[1], color[2], alpha);
        }
    }

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
        for (double y : new double[]{bb.minY, bb.maxY}) {
            vertex(buf, bb.minX, y, bb.minZ, color, alpha);
            vertex(buf, bb.maxX, y, bb.minZ, color, alpha);
            vertex(buf, bb.maxX, y, bb.maxZ, color, alpha);
            vertex(buf, bb.minX, y, bb.maxZ, color, alpha);
        }
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

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (double[] t : targets) {
            buf.pos(sx, sy, sz).color((float) t[3], (float) t[4], (float) t[5], 0.55F).endVertex();
            buf.pos(t[0], t[1], t[2]).color((float) t[3], (float) t[4], (float) t[5], 0.55F).endVertex();
        }
        tess.draw();
    }

    // -------------------------------------------------------- list editing

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
