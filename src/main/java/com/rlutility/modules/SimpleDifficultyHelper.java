package com.rlutility.modules;

import com.charles445.simpledifficulty.api.SDCapabilities;
import com.charles445.simpledifficulty.api.thirst.IThirstCapability;
import com.charles445.simpledifficulty.api.config.ServerConfig;
import com.charles445.simpledifficulty.api.config.ServerOptions;
import com.charles445.simpledifficulty.item.ItemDrinkBase;
import com.charles445.simpledifficulty.network.MessageDrinkWater;
import com.charles445.simpledifficulty.network.PacketHandler;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Auto-hydration through SimpleDifficulty's own channels.
 *
 * <h3>How drinking works in this mod</h3>
 * For source blocks the client sends {@link MessageDrinkWater} (no payload); the server ray-traces
 * what the player is looking at and applies the drink - including, for dirty water, a server-rolled
 * Thirsty debuff and parasite chance. For carried drinks (juice, purified bottles, canteens) the
 * normal item-use packet is enough: the server runs the full drink like a held right-click.
 *
 * <h3>The trace, mirrored exactly</h3>
 * The first version of this handler gated on {@code Entity#rayTrace}, which only hits blocks with
 * collision boxes - fluids have none, so it never saw water and auto-drink silently did nothing.
 * The server's own trace is {@code world.rayTraceBlocks(eyes, eyes+look*reach/2, stopOnLiquid=true)}
 * plus a rain check, and that is what is reproduced here.
 *
 * <h3>What cannot be fixed from the client</h3>
 * Contamination rolls and temperature are computed and enforced entirely server-side; there is no
 * C2S packet carrying either. The honest levers are source choice (Safe Water Only, rain, carried
 * purified drinks) and the HUD readout.
 */
public class SimpleDifficultyHelper {

    private static boolean modLoaded = false;
    private int drinkCooldown = 0;
    /** While > 0 the server is consuming an item drink we started; do not interfere. */
    private int itemUseTicks = 0;

    /** Below this thirst level dirty sources become acceptable rather than dying of thirst. */
    private static final int EMERGENCY_THIRST = 4;

    static {
        modLoaded = Loader.isModLoaded("simpledifficulty");
    }

    public static boolean isModLoaded() {
        return modLoaded;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!modLoaded || !FeatureConfig.simpleDifficultyAutoHydrate || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null) return;
        if (player.isDead || player.getHealth() <= 0.0F) return;

        if (itemUseTicks > 0) {
            itemUseTicks--;
            return; // let the drink in the server's active hand finish
        }
        if (drinkCooldown > 0) {
            drinkCooldown--;
            return;
        }

        try {
            IThirstCapability thirst = SDCapabilities.getThirstData(player);
            if (thirst == null || thirst.getThirstLevel() >= 14) return;
            boolean emergency = thirst.getThirstLevel() <= EMERGENCY_THIRST;

            // 1. Carried drinks first - juice and purified bottles carry no contamination roll.
            if (tryDrinkItem(mc, player, emergency)) {
                drinkCooldown = 45;
                return;
            }

            // 2. Water source blocks the player is looking at.
            int waterKind = lookingAtWater(player);
            if (waterKind == 0) return;
            if (waterKind == 1 && FeatureConfig.simpleDifficultySafeWater && !emergency) {
                return; // dirty water: 75% Thirsty + parasite roll, applied server-side
            }
            if (PacketHandler.instance != null) {
                PacketHandler.instance.sendToServer(new MessageDrinkWater());
                drinkCooldown = 15;
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ drink items

    /**
     * Finds a drink item and uses it like a held right-click: select or swap it in, then send the
     * item-use packet. The server completes the drink after the item's use duration without any
     * further packets, exactly as if the player had held the button.
     */
    private boolean tryDrinkItem(Minecraft mc, EntityPlayerSP player, boolean emergency) {
        if (mc.playerController == null || mc.getConnection() == null) return false;

        int slot = findDrinkSlot(player, emergency);
        if (slot < 0) return false;

        try {
            if (slot <= 8) {
                // Hotbar: switch the selected slot server-side.
                player.inventory.currentItem = slot;
                mc.getConnection().sendPacket(new CPacketHeldItemChange(slot));
            } else {
                // Main inventory: swap the drink into the selected hotbar slot with real clicks.
                int hotbarSlot = 36 + player.inventory.currentItem;
                mc.playerController.windowClick(0, slot, 0, ClickType.PICKUP, player);
                mc.playerController.windowClick(0, hotbarSlot, 0, ClickType.PICKUP, player);
                mc.playerController.windowClick(0, slot, 0, ClickType.PICKUP, player);
            }
            mc.getConnection().sendPacket(new CPacketPlayerTryUseItem(EnumHand.MAIN_HAND));
            // ItemDrinkBase drinks take 32 ticks to consume server-side.
            itemUseTicks = 40;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Container slot of a drink worth using: 0-8 for hotbar entries (usable via held-item change),
     * 9-35 for main-inventory entries (need a swap). -1 when nothing qualifies.
     */
    private static int findDrinkSlot(EntityPlayerSP player, boolean emergency) {
        boolean safeOnly = FeatureConfig.simpleDifficultySafeWater && !emergency;
        for (int i = 0; i < 36; i++) {
            int containerSlot = i < 9 ? 36 + i : i;
            ItemStack stack = player.inventoryContainer.getSlot(containerSlot).getStack();
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemDrinkBase)) continue;
            ItemDrinkBase drink = (ItemDrinkBase) stack.getItem();
            try {
                // Covers empty canteens too - they report thirst level 0.
                if (drink.getThirstLevel(stack) <= 0) continue;
                if (safeOnly && drink.getDirtyChance(stack) > 0.0F) continue;
            } catch (Throwable ignored) {
                continue;
            }
            return i;
        }
        return -1;
    }

    // ------------------------------------------------------------- water trace

    /**
     * 0 = not looking at drinkable water, 1 = dirty (blocks.water), 2 = purified, 3 = rain.
     * Mirrors SimpleDifficulty's server-side traceWater so the packet we send actually finds
     * something to drink (fluid blocks have no collision box, so Entity#rayTrace never sees them).
     */
    private static int lookingAtWater(EntityPlayerSP player) {
        try {
            // Rain drinking: look straight up in the rain under open sky.
            if (player.rotationPitch < -75.0F
                    && player.world.isRainingAt(player.getPosition())
                    && player.world.canSeeSky(player.getPosition())
                    && rainDrinkable()) {
                return 3;
            }

            double reach;
            try {
                reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() * 0.5D;
            } catch (Throwable t) {
                reach = 2.5D;
            }
            Vec3d eye = player.getPositionEyes(1.0F);
            Vec3d look = player.getLook(1.0F);
            Vec3d target = eye.addVector(look.x * reach, look.y * reach, look.z * reach);
            RayTraceResult trace = player.world.rayTraceBlocks(eye, target, true);
            if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK || trace.getBlockPos() == null) {
                return 0;
            }
            Block block = player.world.getBlockState(trace.getBlockPos()).getBlock();
            if (block == Blocks.WATER) return 1;
            if (block.getRegistryName() != null
                    && block.getRegistryName().toString().toLowerCase().contains("purified")) {
                return 2;
            }
            return 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean rainDrinkable() {
        try {
            return ServerConfig.instance.getBoolean(ServerOptions.THIRST_DRINK_RAIN);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ---------------------------------------------------------------- HUD info

    /**
     * Compact HUD readout of the server-synced thirst and temperature values, e.g.
     * {@code W 16/20 T 13/25}, colour coded when either approaches a danger zone. Temperature is
     * computed and enforced entirely server-side, so watching this number is the honest way to
     * handle extreme climates - nothing a client can send will change it.
     * Returns an empty string when SimpleDifficulty is absent or the capability is unreadable.
     */
    public static String hudReadout() {
        if (!modLoaded) return "";
        try {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP player = mc.player;
            if (player == null) return "";

            StringBuilder sb = new StringBuilder();

            IThirstCapability thirst = SDCapabilities.getThirstData(player);
            if (thirst != null) {
                int water = thirst.getThirstLevel();
                char waterColor = water >= 12 ? 'a' : (water >= 6 ? 'e' : 'c');
                sb.append("\u00a7").append(waterColor).append("W ").append(water).append("/20");
            }

            com.charles445.simpledifficulty.api.temperature.ITemperatureCapability temp =
                    SDCapabilities.getTemperatureData(player);
            if (temp != null) {
                int level = temp.getTemperatureLevel();
                int off = Math.abs(level - 12);
                char tempColor = off >= 9 ? 'c' : (off >= 6 ? 'e' : 'b');
                if (sb.length() > 0) sb.append("  ");
                sb.append("\u00a7").append(tempColor).append("T ").append(level).append("/25");
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
