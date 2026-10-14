package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.text.TextComponentString;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trinkets and Baubles race spoofing.
 *
 * <h3>The hole</h3>
 * {@code SelectRacePacket} is one of the few genuinely client-to-server packets in the mod, and its
 * server handler validates far less than it looks like it does:
 *
 * <pre>
 * public void handleServerSafe(NetHandlerPlayServer server) {
 *     final EntityRace race = EntityRace.getRaceById(this.raceId);
 *     final Element primaryElement = Element.getById(this.primaryElementId);
 *     if (!this.isValidSelection(race, primaryElement)) return;      // null / none / blacklist only
 *     Capabilities.getEntityProperties(server.player, properties -&gt; {
 *         if (!TrinketsConfig.SERVER.RACES.SELECTION_MENU &amp;&amp; !properties.isRaceSelectionAuthorized()) {
 *             return;
 *         }
 *         properties.setOriginalRaceCache(new RaceCache(race, primaryElement));
 *         properties.consumeRaceSelectionAuthorization();
 *         properties.scheduleResync();
 *     });
 * }
 * </pre>
 *
 * Two separate weaknesses:
 *
 * <ol>
 *   <li><b>If the server has {@code RACES.SELECTION_MENU} enabled</b>, the short circuit means the
 *       authorization check never runs at all. Any client can set its race and element to anything
 *       not on the blacklist, for free, as often as it likes.</li>
 *   <li><b>Even with the menu disabled</b>, the authorization is a single boolean - it records
 *       <em>that</em> you may change race, never <em>which</em> race you were entitled to. So one
 *       authorization from any race-change item can be redeemed for whatever race you name. The
 *       packet is the only thing that decides.</li>
 * </ol>
 *
 * <p>This is server-authoritative in the way that matters: the race is written into the player's
 * capability on the server and resynced, so all of its stat effects are real.</p>
 *
 * <p>Everything is reflective so a missing or renamed class degrades to a clear message rather than
 * a crash.</p>
 */
public final class TrinketRaceHandler {

    private TrinketRaceHandler() {}

    private static boolean resolved = false;
    private static Class<?> raceClass;
    private static Class<?> elementClass;
    private static Constructor<?> packetCtor;
    private static Object network;
    private static Method sendToServer;
    private static String resolveError = null;

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            raceClass = Class.forName("xzeroair.trinkets.races.EntityRace");
            elementClass = Class.forName("xzeroair.trinkets.traits.elements.Element");

            Class<?> packetClass = Class.forName("xzeroair.trinkets.network.SelectRacePacket");
            packetCtor = packetClass.getConstructor(raceClass, elementClass);

            Class<?> handler = Class.forName("xzeroair.trinkets.network.NetworkHandler");
            network = handler.getField("INSTANCE").get(null);

            Class<?> basic = Class.forName("xzeroair.trinkets.network.BasicPacket");
            sendToServer = network.getClass().getMethod("sendToServer", basic);
        } catch (Throwable t) {
            resolveError = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    public static boolean isAvailable() {
        resolve();
        return resolveError == null && packetCtor != null && sendToServer != null;
    }

    public static String getError() {
        resolve();
        return resolveError;
    }

    // ------------------------------------------------------------ discovery

    /** Race ids are dense and small, so probing them is the simplest way to enumerate. */
    public static Map<String, Object> races() {
        Map<String, Object> out = new LinkedHashMap<>();
        resolve();
        if (raceClass == null) return out;
        try {
            Method getRaceById = raceClass.getMethod("getRaceById", int.class);
            Method isNone = findNoArg(raceClass, "isNone");
            for (int id = 0; id < 64; id++) {
                Object race = getRaceById.invoke(null, id);
                if (race == null) continue;
                if (isNone != null && Boolean.TRUE.equals(isNone.invoke(race))) continue;
                String name = nameOf(race);
                if (name != null && !out.containsKey(name.toLowerCase())) {
                    out.put(name.toLowerCase(), race);
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    public static Map<String, Object> elements() {
        Map<String, Object> out = new LinkedHashMap<>();
        resolve();
        if (elementClass == null) return out;
        try {
            Method getById = elementClass.getMethod("getById", int.class);
            for (int id = 0; id < 32; id++) {
                Object element = getById.invoke(null, id);
                if (element == null) continue;
                String name = nameOf(element);
                if (name != null && !out.containsKey(name.toLowerCase())) {
                    out.put(name.toLowerCase(), element);
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static Method findNoArg(Class<?> c, String name) {
        try {
            return c.getMethod(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String nameOf(Object obj) {
        for (String m : new String[]{"getName", "getRegistryName", "toString"}) {
            try {
                Object v = obj.getClass().getMethod(m).invoke(obj);
                if (v != null) {
                    String s = String.valueOf(v);
                    int colon = s.indexOf(':');
                    if (colon >= 0) s = s.substring(colon + 1);
                    if (!s.isEmpty()) return s;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    // ---------------------------------------------------------------- action

    public static List<String> listNames() {
        List<String> out = new ArrayList<>(races().keySet());
        java.util.Collections.sort(out);
        return out;
    }

    /** Sends the race selection. Returns a human-readable outcome. */
    public static String select(String raceName, String elementName) {
        resolve();
        if (!isAvailable()) {
            return "\u00a7cTrinkets race API unavailable: " + resolveError;
        }

        Map<String, Object> races = races();
        Object race = races.get(raceName == null ? "" : raceName.toLowerCase().trim());
        if (race == null) {
            return "\u00a7cUnknown race '" + raceName + "'. Known: " + String.join(", ", listNames());
        }

        Map<String, Object> elements = elements();
        Object element = null;
        if (elementName != null && !elementName.trim().isEmpty()) {
            element = elements.get(elementName.toLowerCase().trim());
            if (element == null) {
                return "\u00a7cUnknown element '" + elementName + "'. Known: "
                        + String.join(", ", elements.keySet());
            }
        } else if (!elements.isEmpty()) {
            element = elements.values().iterator().next();
        }
        if (element == null) {
            return "\u00a7cNo element could be resolved; the packet requires a non-null element.";
        }

        try {
            Object packet = packetCtor.newInstance(race, element);
            sendToServer.invoke(network, packet);
        } catch (Throwable t) {
            return "\u00a7cFailed to send race packet: " + t;
        }

        return "\u00a7aRequested race \u00a7f" + raceName + "\u00a7a with element \u00a7f"
                + nameOf(element) + "\u00a77. If the server has the selection menu enabled this "
                + "applies immediately; otherwise it consumes one pending race authorization.";
    }

    static void chat(String message) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null) {
            player.sendMessage(new TextComponentString("\u00a76[RLUtility] \u00a7r" + message));
        }
    }
}
