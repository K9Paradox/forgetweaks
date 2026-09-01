package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public class ItemMagnetHandler {

    private static final double VACUUM_RADIUS = 6.0;
    private static boolean isItemPhysicLoaded = false;
    private static Class<?> pickupPacketClass = null;
    private static Constructor<?> pickupPacketConstructor = null;
    private static Method sendPacketToServerMethod = null;
    private int packetCooldown = 0;

    static {
        try {
            if (Loader.isModLoaded("itemphysic")) {
                pickupPacketClass = Class.forName("com.creativemd.itemphysic.packet.PickupPacket");
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

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!FeatureConfig.clientItemVacuum || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        if (packetCooldown > 0) {
            packetCooldown--;
        }

        AxisAlignedBB searchBox = new AxisAlignedBB(
                mc.player.posX - VACUUM_RADIUS, mc.player.posY - VACUUM_RADIUS, mc.player.posZ - VACUUM_RADIUS,
                mc.player.posX + VACUUM_RADIUS, mc.player.posY + VACUUM_RADIUS, mc.player.posZ + VACUUM_RADIUS
        );

        List<EntityItem> nearbyItems = mc.world.getEntitiesWithinAABB(EntityItem.class, searchBox);
        for (EntityItem item : nearbyItems) {
            if (item == null || item.isDead || item.cannotPickup()) continue;

            // If ItemPhysic is active, send authoritative server pickup packets directly
            if (isItemPhysicLoaded && packetCooldown == 0) {
                try {
                    Object packet = pickupPacketConstructor.newInstance(item.getUniqueID(), false);
                    sendPacketToServerMethod.invoke(null, packet);
                    packetCooldown = 2; // rate-limit packet dispatch
                } catch (Throwable ignored) {
                }
            }

            // Visual momentum pull towards player
            double dx = mc.player.posX - item.posX;
            double dy = (mc.player.posY + mc.player.getEyeHeight() / 2.0) - item.posY;
            double dz = mc.player.posZ - item.posZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (dist > 0.2) {
                double speed = 0.4;
                item.motionX = (dx / dist) * speed;
                item.motionY = (dy / dist) * speed;
                item.motionZ = (dz / dist) * speed;
                item.setNoPickupDelay();
            }
        }
    }
}
