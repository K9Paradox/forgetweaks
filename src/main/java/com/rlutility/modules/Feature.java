package com.rlutility.modules;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Describes a single toggleable feature for the GUI / HUD, including how well it behaves on a
 * multiplayer server. The compat tag is deliberately honest so you know at a glance whether a
 * toggle actually changes anything server-side or is purely cosmetic on your own client.
 *
 * <p>Features optionally carry a {@link #parent}: the name of the module they refine. The menu
 * draws sub-features and settings directly underneath their parent module instead of dumping
 * every option into one flat list - the layout convention of most utility clients.</p>
 */
public class Feature {

    public enum Category {
        COMBAT("Combat"),
        MOVEMENT("Movement"),
        SURVIVAL("Survival"),
        SKILLS("Skills"),
        EXPLOITS("Exploits"),
        VISUALS("Visuals"),
        HUD("HUD"),
        TOOLS("Tools");

        public final String title;

        Category(String title) {
            this.title = title;
        }
    }

    public enum Compat {
        /** Drives real C2S packets / container clicks - authoritative on any vanilla or modded server. */
        SERVER("SRV", 0xFF4ADE80, "Server-authoritative: works on multiplayer servers."),
        /** Needs the matching mod installed on the server (true for every RLCraft 2.9.3 server). */
        MODDED("MOD", 0xFF38BDF8, "Uses a mod's own network channel - server must run that mod (RLCraft does)."),
        /** Client-side only: visual/prediction. Harmless, but the server is unaffected. */
        LOCAL("CLI", 0xFFFACC15, "Client-side only - cosmetic/prediction, the server state is unchanged."),
        /** Client-side and likely to be rejected or flagged by a server / anti-cheat. */
        RISKY("!!", 0xFFF87171, "Client-side and easily rejected or flagged by servers. Use with care.");

        public final String badge;
        public final int color;
        public final String tooltip;

        Compat(String badge, int color, String tooltip) {
            this.badge = badge;
            this.color = color;
            this.tooltip = tooltip;
        }
    }

    public final String name;
    public final String desc;
    public final Category category;
    public final Compat compat;
    /** Name of the module this feature belongs to, or null for a top-level module. */
    public final String parent;
    /** Declaration order within the registry; the menu uses it to interleave options naturally. */
    public int order;
    private final Supplier<Boolean> getter;
    private final Consumer<Boolean> setter;

    public Feature(String name, String desc, Category category, Compat compat,
                   Supplier<Boolean> getter, Consumer<Boolean> setter) {
        this(name, desc, category, compat, null, getter, setter);
    }

    public Feature(String name, String desc, Category category, Compat compat, String parent,
                   Supplier<Boolean> getter, Consumer<Boolean> setter) {
        this.name = name;
        this.desc = desc;
        this.category = category;
        this.compat = compat;
        this.parent = parent;
        this.getter = getter;
        this.setter = setter;
    }

    public boolean isSubFeature() {
        return parent != null;
    }

    public boolean isEnabled() {
        Boolean b = getter.get();
        return b != null && b;
    }

    public void set(boolean value) {
        setter.accept(value);
        FeatureConfig.saveConfig();
    }

    public void toggle() {
        set(!isEnabled());
    }
}
