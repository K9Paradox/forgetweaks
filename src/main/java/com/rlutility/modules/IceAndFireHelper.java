package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Gateway for crafting Ice and Fire packets on its own network channel.
 *
 * <h3>Why this exists</h3>
 * Ice and Fire 1.8.4 (the exact build RLCraft 2.9.3 ships) registers a set of client-to-server
 * packets through LLibrary's {@code @NetworkWrapper} system and processes several of them with
 * little or no validation. Audited against the {@code 1.8.4-1.12.2} source branch:
 *
 * <ul>
 *   <li>{@code MessagePlayerHitMultipart} (discriminator 13) - the server performs a real weapon
 *       attack ({@code attackTargetEntityWithCurrentItem}) on any entity the client names within
 *       100 blocks. That is a ranged melee hit: full damage, enchantments and sweep included.</li>
 *   <li>{@code MessageMultipartInteract} (discriminator 10) - the server applies
 *       {@code attackEntityFrom(causeMobDamage(player), message.dmg)} where {@code dmg} is a
 *       float read straight from the packet. Arbitrary damage at up to 100 blocks.</li>
 *   <li>{@code MessageStoneStatue} (discriminator 3) - with a gorgon head in the main hand the
 *       server toggles the stone-petrified flag on any entity by id, no range limit at all.</li>
 *   <li>{@code MessageSirenSong} (discriminator 8) - toggles any siren's singing state by id,
 *       no range limit. Lets us silence sirens remotely.</li>
 *   <li>{@code MessageStartRidingMob} (discriminator 19) - starts/stops riding any
 *       {@code ISyncMount + EntityTameable} entity by id with no ownership check.</li>
 * </ul>
 *
 * <h3>How we send them</h3>
 * We never touch a wire format ourselves. The helper resolves Ice and Fire's own
 * {@code IceAndFire.NETWORK_WRAPPER} (a Forge {@link SimpleNetworkWrapper}, channel name
 * {@code "iceandfire"} - LLibrary names the channel after the mod id) and constructs the mod's
 * real message classes reflectively. {@code SimpleNetworkWrapper.sendToServer} then looks up the
 * discriminator by message class, so even if the discriminator table ever shifted we would still
 * be routed correctly - as long as the class names and field names hold, which they do across
 * the entire 1.8.x line.
 */
public final class IceAndFireHelper {

    private IceAndFireHelper() {}

    private static boolean resolved = false;
    private static boolean available = false;
    private static SimpleNetworkWrapper wrapper = null;

    private static Class<?> msgHitMultipart = null;
    private static Class<?> msgMultipartInteract = null;
    private static Class<?> msgStoneStatue = null;
    private static Class<?> msgSirenSong = null;
    private static Class<?> msgStartRiding = null;
    private static Class<?> syncMountClass = null;

    private static boolean warned = false;

    /** Resolves Ice and Fire's network wrapper and message classes once. */
    public static synchronized boolean available() {
        if (resolved) return available;
        resolved = true;
        try {
            Class<?> modClass = Class.forName("com.github.alexthe666.iceandfire.IceAndFire");
            Object w = modClass.getField("NETWORK_WRAPPER").get(null);
            if (!(w instanceof SimpleNetworkWrapper)) return false;
            wrapper = (SimpleNetworkWrapper) w;

            msgHitMultipart = Class.forName("com.github.alexthe666.iceandfire.message.MessagePlayerHitMultipart");
            msgMultipartInteract = Class.forName("com.github.alexthe666.iceandfire.message.MessageMultipartInteract");
            msgStoneStatue = Class.forName("com.github.alexthe666.iceandfire.message.MessageStoneStatue");
            msgSirenSong = Class.forName("com.github.alexthe666.iceandfire.message.MessageSirenSong");
            msgStartRiding = Class.forName("com.github.alexthe666.iceandfire.message.MessageStartRidingMob");
            syncMountClass = Class.forName("com.github.alexthe666.iceandfire.entity.ISyncMount");
            available = true;
        } catch (Throwable t) {
            available = false;
        }
        return available;
    }

    /** One-time chat notice for when Ice and Fire is missing (client without the mod). */
    public static void warnUnavailable() {
        if (warned) return;
        warned = true;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    "\u00a7c[RLUtility]\u00a7e Ice and Fire packets unavailable - mod classes not found."));
        }
    }

    private static Object build(Class<?> clazz, String[] fields, Object[] values) {
        if (clazz == null) return null;
        try {
            Object msg = clazz.getDeclaredConstructor().newInstance();
            for (int i = 0; i < fields.length; i++) {
                Field f = clazz.getField(fields[i]);
                f.set(msg, values[i]);
            }
            return msg;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean send(Object msg) {
        if (msg == null || wrapper == null) return false;
        try {
            wrapper.sendToServer((IMessage) msg);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------- messages

    /** Server-side: real weapon attack on the named entity within 100 blocks. */
    public static boolean sendHit(int entityId) {
        return send(build(msgHitMultipart,
                new String[] { "creatureID", "extraData" },
                new Object[] { entityId, 0 }));
    }

    /** Server-side: applies {@code damage} straight to the named living entity within 100 blocks. */
    public static boolean sendDamage(int entityId, float damage) {
        return send(build(msgMultipartInteract,
                new String[] { "creatureID", "dmg" },
                new Object[] { entityId, damage }));
    }

    /** Server-side: toggles the gorgon petrification flag on any entity; requires gorgon head held. */
    public static boolean sendStoneStatue(int entityId, boolean petrify) {
        return send(build(msgStoneStatue,
                new String[] { "entityId", "isStone" },
                new Object[] { entityId, petrify }));
    }

    /** Server-side: toggles a siren's singing state. */
    public static boolean sendSirenSong(int entityId, boolean singing) {
        return send(build(msgSirenSong,
                new String[] { "sirenId", "isSinging" },
                new Object[] { entityId, singing }));
    }

    /** Server-side: mount/unmount any ISyncMount + EntityTameable by id. */
    public static boolean sendStartRiding(int entityId, boolean ride) {
        return send(build(msgStartRiding,
                new String[] { "dragonId", "ride" },
                new Object[] { entityId, ride }));
    }

    /** True when the entity implements Ice and Fire's ISyncMount interface. */
    public static boolean isSyncMount(Entity e) {
        return syncMountClass != null && syncMountClass.isInstance(e);
    }

    // ------------------------------------------------------------ targeting

    /**
     * Ray-cast along the player's look vector for up to {@code range} blocks and return the
     * nearest living entity whose (border-grown) hitbox the ray touches, honoring block
     * occlusion. The server handlers accept targets out to 100 blocks, but 1.12's vanilla
     * {@code objectMouseOver} only sees entities within normal reach, so we trace ourselves.
     */
    public static EntityLivingBase entityOnCrosshair(EntityPlayerSP player, double range) {
        Vec3d eye = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        Vec3d end = eye.addVector(look.x * range, look.y * range, look.z * range);

        double limit = range;
        RayTraceResult blockHit = player.world.rayTraceBlocks(eye, end, false, true, false);
        if (blockHit != null && blockHit.typeOfHit == RayTraceResult.Type.BLOCK) {
            limit = eye.distanceTo(blockHit.hitVec);
        }

        AxisAlignedBB scan = player.getEntityBoundingBox()
                .expand(look.x * range, look.y * range, look.z * range).grow(1.0D);
        List<Entity> candidates = player.world.getEntitiesInAABBexcluding(player, scan,
                e -> e != null && !e.isDead && e.canBeCollidedWith() && e instanceof EntityLivingBase);

        EntityLivingBase best = null;
        double bestDist = limit;
        for (Entity e : candidates) {
            AxisAlignedBB bb = e.getEntityBoundingBox().grow(e.getCollisionBorderSize() + 0.3D);
            RayTraceResult hit = bb.calculateIntercept(eye, end);
            if (hit == null || hit.hitVec == null) continue;
            double d = eye.distanceTo(hit.hitVec);
            if (d <= bestDist) {
                bestDist = d;
                best = (EntityLivingBase) e;
            }
        }
        return best;
    }

    /** Snapshot of loaded entities whose class simple name matches, within radius of the player. */
    public static List<Entity> nearbyByClassName(EntityPlayerSP player, double radius, String simpleNameSuffix) {
        List<Entity> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Entity e : new ArrayList<>(player.world.loadedEntityList)) {
            if (e == null || e.isDead || e == player) continue;
            if (!e.getClass().getSimpleName().endsWith(simpleNameSuffix)) continue;
            if (player.getDistanceSq(e) > r2) continue;
            out.add(e);
        }
        return out;
    }
}
