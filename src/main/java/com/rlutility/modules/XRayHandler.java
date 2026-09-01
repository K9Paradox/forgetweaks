package com.rlutility.modules;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Configurable XRay.
 *
 * <p>Rather than hiding terrain (which needs a coremod or a mixin to intercept block rendering),
 * this scans the loaded chunks for the blocks you asked for and draws them as glowing outlines
 * through walls. The visual result is the part of XRay people actually want, and it needs no
 * bytecode patching, no {@code loadRenderers()} thrash and no chunk re-mesh.</p>
 *
 * <p>Scanning walks {@link ExtendedBlockStorage} sections directly instead of calling
 * {@code world.getBlockState} per position: it skips empty sections wholesale and avoids allocating
 * a {@link BlockPos} for every candidate, which is what makes a 28-block radius affordable. The
 * scan is throttled to {@link FeatureConfig#xrayRescanTicks} and re-runs early when you cross into a
 * new chunk.</p>
 */
public class XRayHandler {

    private static final TargetList BLOCKS = new TargetList("XRay blocks");

    /** Flattened results: x, y, z triples. Read by the render thread, written on client tick. */
    private static final List<BlockPos> found = new ArrayList<>();

    private static int tickCounter = 0;
    private static long lastChunkKey = Long.MIN_VALUE;
    private static int lastScanCount = 0;

    // ------------------------------------------------------------------ API

    public static boolean isTargeted(String blockId) {
        return BLOCKS.contains(FeatureConfig.xrayBlocks, blockId);
    }

    public static List<String> targets() {
        return BLOCKS.entries(FeatureConfig.xrayBlocks);
    }

    public static int targetCount() {
        return BLOCKS.size(FeatureConfig.xrayBlocks);
    }

    public static int visibleCount() {
        return lastScanCount;
    }

    /** Adds or removes a block id and forces an immediate rescan. */
    public static void toggleBlock(String blockId) {
        FeatureConfig.xrayBlocks = TargetList.toggle(FeatureConfig.xrayBlocks, blockId);
        FeatureConfig.saveConfig();
        forceRescan();
    }

    public static void addBlock(String blockId) {
        FeatureConfig.xrayBlocks = TargetList.add(FeatureConfig.xrayBlocks, blockId);
        FeatureConfig.saveConfig();
        forceRescan();
    }

    public static void removeBlock(String blockId) {
        FeatureConfig.xrayBlocks = TargetList.remove(FeatureConfig.xrayBlocks, blockId);
        FeatureConfig.saveConfig();
        forceRescan();
    }

    public static void forceRescan() {
        tickCounter = 0;
        lastChunkKey = Long.MIN_VALUE;
    }

    /** Registry id of the block the crosshair is on, or null. */
    public static String lookingAtBlockId() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            RayTraceResult hit = mc.objectMouseOver;
            if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.getBlockPos() == null) return null;
            IBlockState state = mc.world.getBlockState(hit.getBlockPos());
            if (state == null || state.getBlock() == Blocks.AIR) return null;
            return idOf(state.getBlock());
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String idOf(Block block) {
        if (block == null || block.getRegistryName() == null) return null;
        return block.getRegistryName().toString();
    }

    // ----------------------------------------------------------- scanning

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (!FeatureConfig.xrayEnabled || mc.player == null || mc.world == null) {
            if (!found.isEmpty()) {
                synchronized (found) {
                    found.clear();
                }
                lastScanCount = 0;
            }
            return;
        }

        // Rescan on a timer, or immediately if we walked into a different chunk.
        long chunkKey = (((long) (mc.player.chunkCoordX)) << 32) ^ (mc.player.chunkCoordZ & 0xFFFFFFFFL);
        boolean movedChunk = chunkKey != lastChunkKey;
        if (!movedChunk && ++tickCounter < Math.max(5, FeatureConfig.xrayRescanTicks)) return;

        tickCounter = 0;
        lastChunkKey = chunkKey;
        scan(mc);
    }

    private static void scan(Minecraft mc) {
        EntityPlayerSP player = mc.player;
        World world = mc.world;
        List<BlockPos> results = new ArrayList<>();

        try {
            if (BLOCKS.isEmpty(FeatureConfig.xrayBlocks)) {
                synchronized (found) {
                    found.clear();
                }
                lastScanCount = 0;
                return;
            }

            int range = Math.max(4, Math.min(64, FeatureConfig.xrayRange));
            int px = (int) Math.floor(player.posX);
            int py = (int) Math.floor(player.posY);
            int pz = (int) Math.floor(player.posZ);

            int minY = Math.max(0, py - range);
            int maxY = Math.min(255, py + range);
            int chunkRadius = (range >> 4) + 1;
            int rangeSq = range * range;
            // Hard cap so a careless "minecraft:stone" entry cannot lock the game up.
            final int limit = 4000;

            for (int cx = -chunkRadius; cx <= chunkRadius && results.size() < limit; cx++) {
                for (int cz = -chunkRadius; cz <= chunkRadius && results.size() < limit; cz++) {
                    int chunkX = player.chunkCoordX + cx;
                    int chunkZ = player.chunkCoordZ + cz;
                    if (!world.isChunkGeneratedAt(chunkX, chunkZ)) continue;

                    Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
                    if (chunk == null || !chunk.isLoaded()) continue;

                    ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
                    int baseX = chunkX << 4;
                    int baseZ = chunkZ << 4;

                    for (ExtendedBlockStorage section : sections) {
                        // Skipping empty sections is where nearly all of the speed comes from.
                        if (section == null || section.isEmpty()) continue;
                        int baseY = section.getYLocation();
                        if (baseY + 15 < minY || baseY > maxY) continue;

                        for (int y = 0; y < 16; y++) {
                            int worldY = baseY + y;
                            if (worldY < minY || worldY > maxY) continue;
                            int dy = worldY - py;

                            for (int x = 0; x < 16; x++) {
                                int worldX = baseX + x;
                                int dx = worldX - px;
                                if (dx * dx > rangeSq) continue;

                                for (int z = 0; z < 16; z++) {
                                    int worldZ = baseZ + z;
                                    int dz = worldZ - pz;
                                    if (dx * dx + dy * dy + dz * dz > rangeSq) continue;

                                    IBlockState state = section.get(x, y, z);
                                    Block block = state.getBlock();
                                    if (block == Blocks.AIR) continue;

                                    String id = idOf(block);
                                    if (id == null || !BLOCKS.contains(FeatureConfig.xrayBlocks, id)) continue;

                                    results.add(new BlockPos(worldX, worldY, worldZ));
                                    if (results.size() >= limit) break;
                                }
                                if (results.size() >= limit) break;
                            }
                            if (results.size() >= limit) break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // A chunk unloading mid-scan should never be fatal.
        }

        synchronized (found) {
            found.clear();
            found.addAll(results);
        }
        lastScanCount = results.size();
    }

    // ---------------------------------------------------------- rendering

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!FeatureConfig.xrayEnabled) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        List<BlockPos> snapshot;
        synchronized (found) {
            if (found.isEmpty()) return;
            snapshot = new ArrayList<>(found);
        }

        final float partialTicks = event.getPartialTicks();
        final double viewerX = mc.getRenderManager().viewerPosX;
        final double viewerY = mc.getRenderManager().viewerPosY;
        final double viewerZ = mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GlStateManager.glLineWidth(1.6F);

        try {
            List<double[]> tracerTargets = FeatureConfig.xrayTracers ? new ArrayList<>() : null;

            for (BlockPos pos : snapshot) {
                float[] color = colorFor(mc.world.getBlockState(pos).getBlock());

                double x = pos.getX() - viewerX;
                double y = pos.getY() - viewerY;
                double z = pos.getZ() - viewerZ;

                RenderGlobal.drawSelectionBoundingBox(
                        new AxisAlignedBB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D),
                        color[0], color[1], color[2], 0.85F);

                if (tracerTargets != null) {
                    tracerTargets.add(new double[]{x + 0.5D, y + 0.5D, z + 0.5D, color[0], color[1], color[2]});
                }
            }

            if (tracerTargets != null && !tracerTargets.isEmpty()) {
                drawTracers(mc, partialTicks, tracerTargets);
            }
        } catch (Throwable ignored) {
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
            buffer.pos(sx, sy, sz).color((float) t[3], (float) t[4], (float) t[5], 0.45F).endVertex();
            buffer.pos(t[0], t[1], t[2]).color((float) t[3], (float) t[4], (float) t[5], 0.45F).endVertex();
        }
        tessellator.draw();
    }

    /** Stable per-block colour derived from the registry name, so ores stay visually distinct. */
    private static float[] colorFor(Block block) {
        String id = idOf(block);
        if (id == null) return new float[]{1.0F, 1.0F, 1.0F};
        if (id.contains("diamond")) return new float[]{0.35F, 0.95F, 0.95F};
        if (id.contains("emerald")) return new float[]{0.20F, 0.95F, 0.35F};
        if (id.contains("gold")) return new float[]{1.00F, 0.85F, 0.10F};
        if (id.contains("iron")) return new float[]{0.90F, 0.75F, 0.60F};
        if (id.contains("coal")) return new float[]{0.35F, 0.35F, 0.35F};
        if (id.contains("lapis")) return new float[]{0.20F, 0.35F, 0.95F};
        if (id.contains("redstone")) return new float[]{0.95F, 0.15F, 0.15F};
        if (id.contains("quartz")) return new float[]{0.95F, 0.92F, 0.88F};
        if (id.contains("silver")) return new float[]{0.80F, 0.85F, 0.90F};
        if (id.contains("copper")) return new float[]{0.85F, 0.50F, 0.25F};
        if (id.contains("sapphire")) return new float[]{0.25F, 0.55F, 1.00F};
        if (id.contains("spawner")) return new float[]{1.00F, 0.25F, 0.55F};
        if (id.contains("chest")) return new float[]{1.00F, 0.70F, 0.20F};

        int hash = id.hashCode();
        return new float[]{
                0.45F + ((hash & 0xFF) / 255.0F) * 0.55F,
                0.45F + (((hash >> 8) & 0xFF) / 255.0F) * 0.55F,
                0.45F + (((hash >> 16) & 0xFF) / 255.0F) * 0.55F
        };
    }
}
