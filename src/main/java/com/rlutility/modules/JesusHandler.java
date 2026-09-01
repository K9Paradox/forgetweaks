package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Water walk ("Jesus").
 *
 * <p>Movement is client-authoritative, so holding the player at the liquid surface is accepted by
 * the server. Sneaking drops you in, which keeps it usable rather than annoying.</p>
 */
public class JesusHandler {

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !FeatureConfig.waterWalk) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null || event.player != player) return;
        if (player.isSneaking() || player.isRiding() || player.isSpectator()) return;
        if (player.capabilities.isFlying) return;

        World world = mc.world;
        AxisAlignedBB box = player.getEntityBoundingBox();

        boolean liquidBelow = containsLiquid(world, box.offset(0.0D, -0.08D, 0.0D));
        boolean liquidAtFeet = containsLiquid(world, box.offset(0.0D, 0.02D, 0.0D));

        if (liquidBelow && !liquidAtFeet) {
            // Standing on the surface: stop sinking.
            if (player.motionY < 0.0D) player.motionY = 0.0D;
            player.onGround = true;
            player.fallDistance = 0.0F;
        } else if (liquidAtFeet && player.isInWater()) {
            // Submerged: float back up to the surface instead of swimming.
            player.motionY = 0.12D;
            player.fallDistance = 0.0F;
        }
    }

    private static boolean containsLiquid(World world, AxisAlignedBB box) {
        BlockPos min = new BlockPos(Math.floor(box.minX), Math.floor(box.minY), Math.floor(box.minZ));
        BlockPos max = new BlockPos(Math.floor(box.maxX), Math.floor(box.maxY), Math.floor(box.maxZ));

        for (BlockPos pos : BlockPos.getAllInBoxMutable(min, max)) {
            if (world.getBlockState(pos).getMaterial().isLiquid()) return true;
        }
        return false;
    }
}
