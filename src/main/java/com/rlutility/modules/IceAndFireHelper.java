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
 * RLCraft 2.9.3 pins Ice and Fire to CurseForge file 2693547 - that is
 * <b>iceandfire-1.7.1-1.12.2</b> (March 2019), not the later 1.8.x line. Audited against the
 * actual 1.7.1 source, the mod registers these client-to-server packets through LLibrary's
 * {@code @NetworkWrapper} system and processes several of them with zero validation:
 *
 * <ul>
 *   <li>{@code MessagePlayerHitMultipart} - the server performs a real weapon attack
 *       ({@code attackTargetEntityWithCurrentItem}) on any {@code EntityLivingBase} the client
 *       names by id. No range check, no ownership check. Ranged melee with full enchantments.</li>
 *   <li>{@code MessageMultipartInteract} - the server applies
 *       {@code attackEntityFrom(causeMobDamage(player), message.dmg)} where {@code dmg} is a
 *       float read straight from the packet. Arbitrary damage, no range check.</li>
 *   <li>{@code MessageStoneStatue} - sets the {@code StoneEntityProperties.isStone} flag on any
 *       {@code EntityLiving} by id. No range check and no held-item requirement in 1.7.1.</li>
 *   <li>{@code MessageSirenSong} - calls {@code setSinging(boolean)} on any siren by id,
 *       no range check.</li>
 *   <li>{@code MessageDragonArmor} - calls {@code setArmorInSlot(index, type)} on any
 *       {@code EntityDragonBase} by id - no ownership check, no item consumed. Type 0 strips
 *       armor, 3 is diamond-tier.</li>
 * </ul>
 *
 * Note: 1.7.1 has NO {@code MessageStartRidingMob} (that message only exists in 1.8.x), so a
 * mount-hijack exploit is impossible on this build.
 *
 * <h3>How we send them</h3>
 * We never touch a wire format ourselves. The helper resolves Ice and Fire's own
 * {@code IceAndFire.NETWORK_WRAPPER} (a Forge {@link SimpleNetworkWrapper}, channel name
 * {@code "iceandfire"}) and constructs the mod's real message classes reflectively.
 * {@code SimpleNetworkWrapper.sendToServer} looks up the discriminator by message class, so we
 * are always routed correctly regardless of the table order. Each message class is resolved
 * independently: if a future rebuild drops or renames one of them only that module degrades.
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
    private static Class<?> msgDragonArmor = null;

    private static boolean warned = false;

    private static Class<?> tryClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Resolves Ice and Fire's network wrapper and message classes once. */
    public static synchronized boolean available() {
        if (resolved) return available;
        resolved = true;
        try {
            Class<?> modClass = Class.forName("com.github.alexthe666.iceandfire.IceAndFire");
            Object w = modClass.getField("NETWORK_WRAPPER").get(null);
            if (w instanceof SimpleNetworkWrapper) {
                wrapper = (SimpleNetworkWrapper) w;
            }
        } catch (Throwable t) {
            wrapper = null;
        }
        if (wrapper == null) {
            available = false;
            return false;
        }

        msgHitMultipart = tryClass("com.github.alexthe666.iceandfire.message.MessagePlayerHitMultipart");
        msgMultipartInteract = tryClass("com.github.alexthe666.iceandfire.message.MessageMultipartInteract");
        msgStoneStatue = tryClass("com.github.alexthe666.iceandfire.message.MessageStoneStatue");
        msgSirenSong = tryClass("com.github.alexthe666.iceandfire.message.MessageSirenSong");
        msgDragonArmor = tryClass("com.github.alexthe666.iceandfire.message.MessageDragonArmor");

        available = msgHitMultipart != null || msgMultipartInteract != null
                || msgStoneStatue != null || msgSirenSong != null || msgDragonArmor != null;
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

    /** Server-side: real weapon attack on the named entity (handler has no range check). */
    public static boolean sendHit(int entityId) {
        return send(build(msgHitMultipart,
                new String[] { "creatureID" },
                new Object[] { entityId }));
    }

    /** Server-side: applies {@code damage} straight to the named living entity. */
    public static boolean sendDamage(int entityId, float damage) {
        return send(build(msgMultipartInteract,
                new String[] { "creatureID", "dmg" },
                new Object[] { entityId, damage }));
    }

    /** Server-side: toggles the petrification flag on any EntityLiving. No item needed in 1.7.1. */
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

    /**
     * Server-side: sets one armor slot on any dragon, no ownership check and no item consumed.
     * Slots: 0 head, 1 neck, 2 body, 3 tail. Type: 0 none, 1 iron, 2 gold, 3 diamond.
     */
    public static boolean sendDragonArmor(int entityId, int slot, int armorType) {
        return send(build(msgDragonArmor,
                new String[] { "dragonId", "armor_index", "armor_type" },
                new Object[] { entityId, slot, armorType }));
    }

    // ------------------------------------------------------------ targeting

    /**
     * Ray-cast along the player's look vector for up to {@code range} blocks and return the
     * nearest living entity whose (border-grown) hitbox the ray touches, honoring block
     * occlusion. The server handlers accept targets at any distance, but 1.12's vanilla
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
