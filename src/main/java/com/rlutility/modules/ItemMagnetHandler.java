package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Item magnet / vacuum.
 *
 * <p>Everything is configurable now: radius, pull speed, an optional whitelist and a blacklist, and
 * an "only my drops" mode. Filters are matched against the item's registry id through
 * {@link TargetList}, so {@code minecraft:*} or {@code iceandfire:*} work as well as exact ids.</p>
 *
 * <p>Note on honesty: the momentum nudge is client-side only, and on a vanilla server the pickup
 * itself still happens server-side when the item's real position reaches you. With ItemPhysic
 * present we additionally send its {@code PickupPacket}, which <em>is</em> server-authoritative.
 * Without ItemPhysic this mostly helps items that are already within the server's pickup radius.</p>
 */
public class ItemMagnetHandler {

    private static final TargetList WHITELIST = new TargetList("Magnet whitelist");
    private static final TargetList BLACKLIST = new TargetList("Magnet blacklist");

    private static boolean isItemPhysicLoaded = false;
    private static Constructor<?> pickupPacketConstructor = null;
    private static Method sendPacketToServerMethod = null;

    private int packetCooldown = 0;

    static {
        try {
            if (Loader.isModLoaded("itemphysic")) {
                Class<?> pickupPacketClass = Class.forName("com.creativemd.itemphysic.packet.PickupPacket");
                pickupPacketConstructor = pickupPacketClass.getConstructor(UUID.class, boolean.class);
                Class<?> packetHandlerClass = Class.forName("com.creativemd.creativecore.common.packet.PacketHandler");
                Class<?> corePacketClass = Class.forName("com.creativemd.creativecore.common.packet.CreativeCorePacket");
                sendPacketToServerMethod = packetHandlerClass.getMethod("sendPacketToServer", corePacketClass);
                isItemPhysicLoaded = true;
            }
        } catch (Throwable ignored) {
            isItemPhysicLoaded = false;
        }
    }

    /** True when this drop passes the configured filters. */
    public static boolean shouldPull(EntityItem entity) {
        try {
            ItemStack stack = entity.getItem();
            if (stack == null || stack.isEmpty()) return false;
            if (stack.getItem().getRegistryName() == null) return false;
            String id = stack.getItem().getRegistryName().toString();

            if (!WHITELIST.isEmpty(FeatureConfig.magnetWhitelist)
                    && !WHITELIST.contains(FeatureConfig.magnetWhitelist, id)) {
                return false;
            }
            if (BLACKLIST.contains(FeatureConfig.magnetBlacklist, id)) return false;

            if (FeatureConfig.magnetOnlyMine) {
                // EntityItem#getOwner returns a player *name* in 1.12.2, not a UUID string.
                String owner = entity.getOwner();
                if (owner != null && !owner.isEmpty()) {
                    String self = Minecraft.getMinecraft().player.getName();
                    if (!owner.equalsIgnoreCase(self)) return false;
                }
            }
            return true;
        } catch (Throwable ignored) {
            return true;
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!FeatureConfig.clientItemVacuum || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        if (packetCooldown > 0) packetCooldown--;

        double radius = Math.max(1.0D, Math.min(32.0D, FeatureConfig.magnetRadius));
        double speed = Math.max(0.05D, Math.min(2.0D, FeatureConfig.magnetSpeed));

        AxisAlignedBB searchBox = new AxisAlignedBB(
                mc.player.posX - radius, mc.player.posY - radius, mc.player.posZ - radius,
                mc.player.posX + radius, mc.player.posY + radius, mc.player.posZ + radius);

        List<EntityItem> nearbyItems = mc.world.getEntitiesWithinAABB(EntityItem.class, searchBox);
        for (EntityItem item : nearbyItems) {
            if (item == null || item.isDead || item.cannotPickup()) continue;
            if (!shouldPull(item)) continue;

            if (isItemPhysicLoaded && packetCooldown == 0) {
                try {
                    Object packet = pickupPacketConstructor.newInstance(item.getUniqueID(), false);
                    sendPacketToServerMethod.invoke(null, packet);
                    packetCooldown = 2; // rate-limit packet dispatch
                } catch (Throwable ignored) {
                }
            }

            double dx = mc.player.posX - item.posX;
            double dy = (mc.player.posY + mc.player.getEyeHeight() / 2.0D) - item.posY;
            double dz = mc.player.posZ - item.posZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (dist > 0.2D) {
                item.motionX = (dx / dist) * speed;
                item.motionY = (dy / dist) * speed;
                item.motionZ = (dz / dist) * speed;
                item.setNoPickupDelay();
            }
        }
    }
}
