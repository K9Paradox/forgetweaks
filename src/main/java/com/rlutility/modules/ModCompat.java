package com.rlutility.modules;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod detection by <b>class presence</b> rather than mod id.
 *
 * <h3>Why</h3>
 * Every RLCombat integration in this project was gated on {@code Loader.isModLoaded("rlcombat")},
 * and the attack test proved that id does not exist - every RLCombat method reported
 * "skipped (rlcombat absent)". The mod's classes live under {@code bettercombat.mod}, so the id is
 * something else entirely. That single wrong string silently disabled the integration everywhere,
 * including inside the triggerbot, which is why its nunchaku branch was actually falling through to
 * plain {@code playerController.attackEntity}.
 *
 * <p>Checking for the class we are about to call cannot drift out of sync with reality the way a
 * hardcoded id can, so that is what these helpers do.</p>
 */
public final class ModCompat {

    private ModCompat() {}

    public static boolean classExists(String name) {
        try {
            Class.forName(name, false, ModCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** First of the candidates that resolves, or null. */
    public static Class<?> firstClass(String... candidates) {
        for (String name : candidates) {
            try {
                return Class.forName(name, false, ModCompat.class.getClassLoader());
            } catch (Throwable ignored) {}
        }
        return null;
    }

    // ------------------------------------------------------------- features

    /** RLCombat / Better Combat, whatever it calls itself. */
    public static boolean hasRLCombat() {
        return classExists("bettercombat.mod.network.PacketHandler")
                || classExists("bettercombat.mod.handler.EventHandlers")
                || Loader.isModLoaded("rlcombat")
                || Loader.isModLoaded("bettercombat");
    }

    /** Better Survival's RLCombat bridge, used by the working nunchaku attack path. */
    public static boolean hasRLCombatCompat() {
        return classExists("com.mujmajnkraft.bettersurvival.integration.RLCombatCompat");
    }

    public static Class<?> spartanPacketHandler() {
        return firstClass(
                "com.oblivioussp.spartanweaponry.network.PacketHandler",
                "com.oblivioussp.spartanweaponry.network.NetworkHandler",
                "com.oblivioussp.spartanweaponry.util.PacketHandler");
    }

    public static Class<?> spartanLongReachPacket() {
        return firstClass(
                "com.oblivioussp.spartanweaponry.network.PacketLongReachAttack",
                "com.oblivioussp.spartanweaponry.network.message.PacketLongReachAttack",
                "com.oblivioussp.spartanweaponry.network.MessageLongReachAttack",
                "com.oblivioussp.spartanweaponry.network.message.MessageLongReachAttack");
    }

    // ---------------------------------------------------------------- listing

    /** Every loaded mod id, for /rlu mods - so ids are never guessed again. */
    public static List<String> loadedMods(String filter) {
        List<String> out = new ArrayList<>();
        try {
            for (ModContainer mod : Loader.instance().getModList()) {
                String id = mod.getModId();
                if (filter != null && !filter.isEmpty()
                        && !id.toLowerCase().contains(filter.toLowerCase())
                        && !mod.getName().toLowerCase().contains(filter.toLowerCase())) {
                    continue;
                }
                out.add(id + " \u00a78(" + mod.getName() + " " + mod.getVersion() + ")");
            }
        } catch (Throwable t) {
            out.add("failed to list mods: " + t);
        }
        java.util.Collections.sort(out);
        return out;
    }
}
