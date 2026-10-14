package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Counter for Ice and Fire sirens.
 *
 * <h3>What the siren does, exactly</h3>
 * Every second a singing (on-land, non-aggressive) siren re-checks every living entity within
 * 50 blocks and, unless it wears earplugs, marks it charmed in its {@code SirenEntityProperties}.
 * Charmed players are pulled toward the siren by a motion blend every tick - applied both
 * client-side and, crucially, <em>server-side</em>, where it is enforced through the normal
 * movement pipeline. Once within 5 blocks the siren turns aggressive and attacks.
 *
 * <h3>What can and cannot be blocked from the client</h3>
 * Because the pull runs on the server too, no client mod can delete it outright. Two things do
 * work:
 * <ul>
 *   <li><b>Earplugs</b> - the charm check clears instantly for anyone wearing them. This handler
 *       auto-equips earplugs from your inventory into the helmet slot with real clicks, which is
 *       the genuine fix.</li>
 *   <li><b>Out-running the pull</b> - the siren only blends your velocity ~0.05 blocks/tick toward
 *       itself while sprinting away covers ~0.28 blocks/tick, so constant movement away wins.
 *       When you have no earplugs this handler drives that movement automatically.</li>
 * </ul>
 */
public class SirenGuardHandler {

    // Reflection handles for Ice and Fire / LLibrary, resolved lazily and cached.
    private static boolean resolved = false;
    private static Class<?> sirenPropsClass = null;
    private static Object propsHandler = null;
    private static Method getPropsMethod = null;
    private static Field charmedField = null;
    private static Field sirenIdField = null;

    private boolean warned = false;
    private int earplugCooldown = 0;

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            sirenPropsClass = Class.forName("com.github.alexthe666.iceandfire.entity.SirenEntityProperties");
            Class<?> handlerClass = Class.forName("net.ilexiconn.llibrary.server.entity.EntityPropertiesHandler");
            propsHandler = handlerClass.getField("INSTANCE").get(null);
            getPropsMethod = handlerClass.getMethod("getProperties", Entity.class, Class.class);
            charmedField = sirenPropsClass.getField("isCharmed");
            sirenIdField = sirenPropsClass.getField("sirenID");
        } catch (Throwable t) {
            sirenPropsClass = null;
        }
    }

    public static boolean isAvailable() {
        resolve();
        return sirenPropsClass != null;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!FeatureConfig.sirenGuard || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null || player.isDead) return;

        resolve();
        if (sirenPropsClass == null) return;
        if (earplugCooldown > 0) earplugCooldown--;

        try {
            Object props = getPropsMethod.invoke(propsHandler, player, sirenPropsClass);
            if (props == null) return;
            boolean charmed = charmedField.getBoolean(props);
            int sirenId = sirenIdField.getInt(props);

            if (!charmed) {
                warned = false;
                return;
            }

            Entity siren = sirenId == 0 ? null : mc.world.getEntityByID(sirenId);
            if (siren == null || siren.isDead) return;

            // 1. The real fix: wear earplugs. The server's charm check clears the moment they are on.
            if (tryEquipEarplugs(mc, player)) {
                if (!warned) {
                    warned = true;
                    player.sendMessage(new TextComponentString("\u00a76[RLUtility] \u00a7aSiren song "
                            + "detected - auto-equipping earplugs."));
                }
                return;
            }

            // 2. No earplugs: run away. Warn once per charm episode.
            if (!warned) {
                warned = true;
                player.sendMessage(new TextComponentString("\u00a76[RLUtility] \u00a7cSiren song! No "
                        + "earplugs found - auto-running away. Craft iceandfire earplugs to end this "
                        + "permanently."));
            }
            evade(mc, player, siren);
        } catch (Throwable ignored) {
        }
    }

    /** Equips earplugs into the helmet slot if we have them and the slot is not already plugged. */
    private boolean tryEquipEarplugs(Minecraft mc, EntityPlayerSP player) {
        try {
            Item earplugs = Item.getByNameOrId("iceandfire:earplugs");
            if (earplugs == null || earplugs == Items.AIR) return false;

            ItemStack helmet = player.inventoryContainer.getSlot(5).getStack();
            if (!helmet.isEmpty() && helmet.getItem() == earplugs) return true; // already plugged

            if (earplugCooldown > 0 || mc.playerController == null) return false;

            for (int i = 9; i <= 44; i++) {
                ItemStack stack = player.inventoryContainer.getSlot(i).getStack();
                if (!stack.isEmpty() && stack.getItem() == earplugs) {
                    // Pick up earplugs, swap with helmet, park the old helmet back.
                    mc.playerController.windowClick(0, i, 0, ClickType.PICKUP, player);
                    mc.playerController.windowClick(0, 5, 0, ClickType.PICKUP, player);
                    mc.playerController.windowClick(0, i, 0, ClickType.PICKUP, player);
                    earplugCooldown = 40;
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Drives movement directly away from the siren without touching the camera: movement input is
     * decomposed into forward/strafe components relative to the current facing so the resulting
     * acceleration points exactly opposite the siren. Also jumps over obstacles and sprints.
     */
    private void evade(Minecraft mc, EntityPlayerSP player, Entity siren) {
        // Direction the escape vector points at, in yaw space.
        double dx = player.posX - siren.posX;
        double dz = player.posZ - siren.posZ;
        float awayYaw = (float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;

        float relRad = MathHelper.wrapDegrees(awayYaw - player.rotationYaw) * 0.017453292F;
        float forward = MathHelper.cos(relRad);
        float strafe = MathHelper.sin(relRad);

        // Cover every possible input-read ordering: the input object, the entity fields vanilla
        // copies it into, and a direct motion nudge.
        player.movementInput.moveForward = forward;
        player.movementInput.moveStrafe = strafe;
        player.moveForward = forward;
        player.moveStrafing = strafe;

        if (player.collidedHorizontally && player.onGround) {
            player.movementInput.jump = true;
            player.motionY = Math.max(player.motionY, 0.42D);
        }

        // Small extra shove so even slow clients beat the siren's 0.05/tick blend.
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.01D) {
            player.motionX += (dx / len) * 0.05D;
            player.motionZ += (dz / len) * 0.05D;
        }

        player.setSprinting(true);
    }

    /**
     * Second evasion pass at the latest possible moment. Depending on the exact event order the
     * player's movement input can be re-derived from the keyboard after our tick handler wrote it,
     * so the escape vector is re-applied here - at LOWEST priority this runs after Ice and Fire's
     * own charm handler has blended its pull into the motion.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!FeatureConfig.sirenGuard) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.getEntityLiving() != player || player.isDead) return;

        Entity siren = currentSiren(mc, player);
        if (siren != null) evade(mc, player, siren);
    }

    private static Entity currentSiren(Minecraft mc, EntityPlayerSP player) {
        try {
            resolve();
            if (sirenPropsClass == null) return null;
            Object props = getPropsMethod.invoke(propsHandler, player, sirenPropsClass);
            if (props == null || !charmedField.getBoolean(props)) return null;
            int id = sirenIdField.getInt(props);
            Entity siren = id == 0 ? null : mc.world.getEntityByID(id);
            return (siren == null || siren.isDead) ? null : siren;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** For diagnostics (/rlu diag style output). */
    public static boolean isCharmedNow() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null) return false;
            resolve();
            if (sirenPropsClass == null) return false;
            Object props = getPropsMethod.invoke(propsHandler, mc.player, sirenPropsClass);
            return props != null && charmedField.getBoolean(props);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
