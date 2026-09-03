package com.rlutility.modules;

import com.charles445.simpledifficulty.api.SDCapabilities;
import com.charles445.simpledifficulty.api.thirst.IThirstCapability;
import com.charles445.simpledifficulty.network.MessageDrinkWater;
import com.charles445.simpledifficulty.network.PacketHandler;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Auto-hydration through SimpleDifficulty's own drinking channel.
 *
 * <h3>How drinking works in this mod</h3>
 * The client sends {@link MessageDrinkWater} (it carries no data); the server ray-traces what the
 * player is looking at, and if it is water applies the drink: thirst refill, and for dirty water
 * a 75% chance of the Thirsty debuff plus a parasite roll. Both effects are decided server-side,
 * so there is no packet to edit to avoid them - the only real protection is choosing safe water.
 *
 * <h3>What this handler does with that</h3>
 * It mirrors the server's ray-trace locally so packets are only sent while actually looking at
 * water, and with {@code Safe Water Only} enabled it refuses dirty water ({@code minecraft:water})
 * unless thirst is already critical - purified water is always accepted.
 */
public class SimpleDifficultyHelper {

    private static boolean modLoaded = false;
    private int drinkCooldown = 0;

    /** Below this thirst level a dirty source becomes acceptable rather than dying of thirst. */
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

        if (drinkCooldown > 0) {
            drinkCooldown--;
            return;
        }

        try {
            IThirstCapability thirst = SDCapabilities.getThirstData(player);
            if (thirst == null || thirst.getThirstLevel() >= 14) return;

            int waterKind = lookingAtWater(player);
            if (waterKind == 0) return;                    // not looking at water; server would no-op
            if (waterKind == 1                             // dirty water
                    && FeatureConfig.simpleDifficultySafeWater
                    && thirst.getThirstLevel() > EMERGENCY_THIRST) {
                return;                                    // wait for purified water instead
            }

            if (PacketHandler.instance != null) {
                PacketHandler.instance.sendToServer(new MessageDrinkWater());
                drinkCooldown = 15;
            }
        } catch (Throwable ignored) {
        }
    }

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

    /**
     * 0 = not looking at drinkable water, 1 = dirty (blocks.water), 2 = purified.
     * Mirrors the server-side trace so we never spend a packet staring at a wall.
     */
    private static int lookingAtWater(EntityPlayerSP player) {
        try {
            RayTraceResult trace = player.rayTrace(4.0D, 1.0F);
            if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK || trace.getBlockPos() == null) {
                return 0;
            }
            Block block = player.world.getBlockState(trace.getBlockPos()).getBlock();
            if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) return 1;
            if (block.getRegistryName() != null
                    && block.getRegistryName().toString().toLowerCase().contains("purified")) {
                return 2;
            }
            return 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
