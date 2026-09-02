package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * True per-pixel model outlines, by reusing vanilla's own entity-outline shader.
 *
 * <h3>How this works</h3>
 * Minecraft already renders a clean silhouette around any entity it considers "glowing" - that is
 * the spectator/glowing-effect outline, drawn by {@code RenderGlobal} into a dedicated framebuffer
 * and composited with an edge-detect shader. It follows the actual model, not a bounding box, which
 * is exactly the effect a hand-rolled box cannot produce.
 *
 * <p>Rather than writing a stencil or shader pass of our own - which cannot be validated without
 * running the game and risks a black screen on failure - we simply mark the entities we care about
 * as glowing on the client. Vanilla does the rendering.</p>
 *
 * <h3>The catch, and the fix</h3>
 * {@code Entity#setGlowing} does not work client-side:
 *
 * <pre>
 * public void setGlowing(boolean glowingIn) {
 *     this.glowing = glowingIn;
 *     if (!this.world.isRemote) this.setFlag(6, this.glowing);
 * }
 * public boolean isGlowing() {
 *     return this.world.isRemote ? this.getFlag(6) : this.glowing;
 * }
 * </pre>
 *
 * On a client world the setter writes a field the getter never reads - the getter consults data
 * watcher flag 6 instead. So we set that flag directly on the entity's local {@code DataManager}.
 * Nothing is transmitted: the client data manager is a local mirror, so this is purely visual and
 * invisible to the server.
 *
 * <p>The flag is re-applied every tick because a server-side entity update can overwrite the byte.</p>
 *
 * <h3>Colour</h3>
 * Vanilla derives outline colour from the entity's scoreboard team. To get per-category colours we
 * put each entity into a lightweight client-side team named after its {@link EspRenderHelper.Kind}.
 * Teams are created on the client scoreboard only.
 */
public class GlowEspHandler {

    private static final int GLOWING_FLAG = 6;

    private static DataParameter<Byte> flagsParam = null;
    private static boolean resolved = false;
    private static boolean failed = false;

    /** Entities we have marked, so they can be un-marked when they stop matching. */
    private static final Set<Integer> marked = new HashSet<>();

    public static boolean isAvailable() {
        resolve();
        return !failed;
    }

    public static int getMarkedCount() {
        return marked.size();
    }

    @SuppressWarnings("unchecked")
    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Field f = ReflectionHelper.findField(Entity.class, "FLAGS", "field_184240_ax");
            f.setAccessible(true);
            flagsParam = (DataParameter<Byte>) f.get(null);
            failed = flagsParam == null;
        } catch (Throwable t) {
            failed = true;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            marked.clear();
            return;
        }

        resolve();
        if (failed) return;

        if (!FeatureConfig.espGlowOutline) {
            if (!marked.isEmpty()) clearAll(mc);
            return;
        }

        Set<Integer> stillMarked = new HashSet<>();
        double rangeSq = (double) FeatureConfig.espRange * FeatureConfig.espRange;

        try {
            for (Entity entity : new java.util.ArrayList<>(mc.world.loadedEntityList)) {
                if (entity == null || entity == mc.player || entity.isDead) continue;
                if (mc.player.getDistanceSq(entity) > rangeSq) continue;

                EspRenderHelper.Kind kind = EspRenderHelper.kindOfPublic(entity);
                // Style 4 is "Glow" - only categories set to it get the shader outline.
                if (kind == null || kind.style() != 4) continue;

                setGlowing(entity, true);
                applyTeamColour(mc, entity, kind);
                stillMarked.add(entity.getEntityId());
            }

            // Anything that dropped out of range or stopped matching must be cleared.
            for (Integer id : marked) {
                if (stillMarked.contains(id)) continue;
                Entity entity = mc.world.getEntityByID(id);
                if (entity != null) setGlowing(entity, false);
            }
        } catch (Throwable ignored) {
        }

        marked.clear();
        marked.addAll(stillMarked);
    }

    private static void clearAll(Minecraft mc) {
        try {
            for (Integer id : marked) {
                Entity entity = mc.world.getEntityByID(id);
                if (entity != null) setGlowing(entity, false);
            }
        } catch (Throwable ignored) {}
        marked.clear();
    }

    /** Writes data watcher flag 6 directly, which is what isGlowing() reads on the client. */
    private static void setGlowing(Entity entity, boolean glowing) {
        try {
            byte flags = entity.getDataManager().get(flagsParam);
            byte updated = glowing
                    ? (byte) (flags | (1 << GLOWING_FLAG))
                    : (byte) (flags & ~(1 << GLOWING_FLAG));
            if (updated != flags) {
                entity.getDataManager().set(flagsParam, updated);
            }
        } catch (Throwable ignored) {}
    }

    /** Client-only scoreboard team, purely so vanilla picks a colour for the outline. */
    private static void applyTeamColour(Minecraft mc, Entity entity, EspRenderHelper.Kind kind) {
        if (!FeatureConfig.espGlowColors) return;
        try {
            Scoreboard scoreboard = mc.world.getScoreboard();
            if (scoreboard == null) return;

            String teamName = "rlu_" + kind.name().toLowerCase();
            ScorePlayerTeam team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.createTeam(teamName);
                team.setColor(colourFor(kind));
                team.setPrefix(colourFor(kind).toString());
            }

            String member = entity instanceof net.minecraft.entity.player.EntityPlayer
                    ? entity.getName()
                    : entity.getCachedUniqueIdString();
            if (!team.getMembershipCollection().contains(member)) {
                scoreboard.addPlayerToTeam(member, teamName);
            }
        } catch (Throwable ignored) {
            // A server-managed scoreboard can refuse edits; the outline still renders, just white.
        }
    }

    private static TextFormatting colourFor(EspRenderHelper.Kind kind) {
        switch (kind) {
            case CHEST: return TextFormatting.GOLD;
            case SPAWNER: return TextFormatting.RED;
            case WAYSTONE: return TextFormatting.AQUA;
            case CONTAINER: return TextFormatting.YELLOW;
            case BOSS: return TextFormatting.LIGHT_PURPLE;
            case HOSTILE: return TextFormatting.DARK_RED;
            case PLAYER: return TextFormatting.BLUE;
            case ITEM: return TextFormatting.GREEN;
            case MODDED: return TextFormatting.DARK_PURPLE;
            default: return TextFormatting.WHITE;
        }
    }

    /** Clear marks on disconnect so nothing leaks into the next world. */
    public static void reset() {
        marked.clear();
    }
}
