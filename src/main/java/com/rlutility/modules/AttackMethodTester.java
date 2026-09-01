package com.rlutility.modules;

import com.mujmajnkraft.bettersurvival.capabilities.nunchakucombo.INunchakuCombo;
import com.mujmajnkraft.bettersurvival.capabilities.nunchakucombo.NunchakuComboProvider;
import com.mujmajnkraft.bettersurvival.integration.RLCombatCompat;
import com.mujmajnkraft.bettersurvival.packet.BetterSurvivalPacketHandler;
import com.mujmajnkraft.bettersurvival.packet.MessageNunchakuSpinClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Controlled A/B test of every attack dispatch method, measured by actual target health loss.
 *
 * <h3>Why this exists</h3>
 * Three rounds were spent guessing which mechanism let the nunchaku triggerbot damage locked
 * targets, and each guess cost a full build cycle. This stops the guessing: it fires one method at a
 * time at a single target, samples the entity's health before and after each attempt, and prints a
 * table of which ones actually removed health.
 *
 * <p>It also settles the most important open question - whether there is a bypass at all. If
 * <em>every</em> method fails with a locked weapon but all of them succeed with an unlocked one,
 * then nothing here is bypassing Reskillable and the nunchaku simply is not level-locked for you.
 * That is a real possible outcome and the test will show it plainly rather than hiding it.</p>
 *
 * <p>Health is read from the client's copy of the entity, which the server syncs, so a drop is real
 * server-side damage and not a local illusion.</p>
 */
public class AttackMethodTester {

    private static final int SETTLE_TICKS = 25;

    private interface Attack {
        void run(EntityPlayerSP player, Entity target) throws Throwable;
    }

    private static final class Method0 {
        final String name;
        final String requires;
        final Attack attack;

        Method0(String name, String requires, Attack attack) {
            this.name = name;
            this.requires = requires;
            this.attack = attack;
        }
    }

    private static final List<Method0> METHODS = new ArrayList<>();

    static {
        METHODS.add(new Method0("vanilla playerController.attackEntity", null,
                (p, t) -> Minecraft.getMinecraft().playerController.attackEntity(p, t)));

        METHODS.add(new Method0("RLCombatCompat.attackEntityFromClient", "rlcombat",
                (p, t) -> RLCombatCompat.attackEntityFromClient(new RayTraceResult(t), p)));

        METHODS.add(new Method0("RLCombatCompat + nunchaku spin first", "rlcombat", (p, t) -> {
            // The working path also enabled the spin capability first; test that combination.
            try {
                INunchakuCombo combo = p.getCapability(NunchakuComboProvider.NUNCHAKUCOMBO_CAP, null);
                if (combo != null) {
                    BetterSurvivalPacketHandler.NETWORK.sendToServer(new MessageNunchakuSpinClient(true));
                }
            } catch (Throwable ignored) {}
            RLCombatCompat.attackEntityFromClient(new RayTraceResult(t), p);
        }));

        METHODS.add(new Method0("RLCombat PacketMainhandAttack", "rlcombat", (p, t) -> {
            Class<?> ph = Class.forName("bettercombat.mod.network.PacketHandler");
            Object inst = ph.getField("instance").get(null);
            Class<?> pk = Class.forName("bettercombat.mod.network.PacketMainhandAttack");
            Object msg = pk.getConstructor(int.class).newInstance(t.getEntityId());
            Method send = inst.getClass().getMethod("sendToServer",
                    net.minecraftforge.fml.common.network.simpleimpl.IMessage.class);
            send.invoke(inst, msg);
        }));

        METHODS.add(new Method0("Spartan PacketLongReachAttack", "spartanweaponry", (p, t) -> {
            Class<?> ph = Class.forName("com.oblivioussp.spartanweaponry.network.PacketHandler");
            Object inst = ph.getField("instance").get(null);
            Class<?> pk = Class.forName("com.oblivioussp.spartanweaponry.network.PacketLongReachAttack");
            Object msg = pk.getConstructor(int.class, float.class).newInstance(t.getEntityId(), 0.0F);
            Method send = inst.getClass().getMethod("sendToServer",
                    net.minecraftforge.fml.common.network.simpleimpl.IMessage.class);
            send.invoke(inst, msg);
        }));

        METHODS.add(new Method0("Ice and Fire MessagePlayerHitMultipart", "iceandfire", (p, t) -> {
            Class<?> iaf = Class.forName("com.github.alexthe666.iceandfire.IceAndFire");
            Object wrapper = iaf.getField("NETWORK_WRAPPER").get(null);
            Class<?> pk = Class.forName("com.github.alexthe666.iceandfire.message.MessagePlayerHitMultipart");
            Object msg = pk.getConstructor(int.class).newInstance(t.getEntityId());
            Method send = wrapper.getClass().getMethod("sendToServer",
                    net.minecraftforge.fml.common.network.simpleimpl.IMessage.class);
            send.invoke(wrapper, msg);
        }));

        METHODS.add(new Method0("raw CPacketUseEntity", null, (p, t) -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getConnection() != null) {
                mc.getConnection().sendPacket(
                        new net.minecraft.network.play.client.CPacketUseEntity(t));
                p.swingArm(EnumHand.MAIN_HAND);
            }
        }));
    }

    // ---------------------------------------------------------------- state

    private static boolean active = false;
    private static int index = -1;
    private static int timer = 0;
    private static Entity target = null;
    private static float healthBefore = 0;
    private static String heldAtStart = "";
    private static final List<String> results = new ArrayList<>();

    public static boolean isRunning() {
        return active;
    }

    public static void start() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) return;

        if (active) {
            chat("\u00a7eTest already running.");
            return;
        }

        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.ENTITY
                || !(hit.entityHit instanceof EntityLivingBase)) {
            chat("\u00a7cLook directly at a living entity, then run this again.");
            chat("\u00a77Use something tanky that will survive ~7 hits, and do not move.");
            return;
        }

        target = hit.entityHit;
        heldAtStart = player.getHeldItemMainhand().isEmpty() ? "(empty hand)"
                : String.valueOf(player.getHeldItemMainhand().getItem().getRegistryName());

        results.clear();
        active = true;
        index = -1;
        timer = 0;

        chat("\u00a76\u00a7lAttack method test starting");
        chat("\u00a77Target: \u00a7f" + target.getName() + "\u00a77  Weapon: \u00a7f" + heldAtStart);
        chat("\u00a77Lock state: " + (ReskillableHelper.canUseHeldItem()
                ? "\u00a7aunlocked" : "\u00a7cLOCKED")
                + (ReskillableHelper.lastLockResolved ? "" : " \u00a7e(unreadable)"));
        chat("\u00a77Keep looking at it and stand still. " + METHODS.size() + " methods, ~"
                + (METHODS.size() * SETTLE_TICKS / 20) + "s.");
    }

    public static void stop() {
        active = false;
        target = null;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || target == null || target.isDead) {
            if (active) {
                chat("\u00a7eTarget died or vanished - stopping. Partial results:");
                report();
            }
            stop();
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        // Score the method that just ran.
        if (index >= 0 && index < METHODS.size()) {
            float after = ((EntityLivingBase) target).getHealth();
            float delta = healthBefore - after;
            Method0 m = METHODS.get(index);
            results.add((delta > 0.01F
                    ? "\u00a7a\u2713 " + String.format("%.1f", delta) + " dmg  "
                    : "\u00a7c\u2717 no damage  ") + "\u00a7f" + m.name);
        }

        index++;
        if (index >= METHODS.size()) {
            report();
            stop();
            return;
        }

        Method0 method = METHODS.get(index);
        if (method.requires != null && !Loader.isModLoaded(method.requires)) {
            results.add("\u00a78- skipped (" + method.requires + " absent)  " + method.name);
            timer = 1;
            return;
        }

        healthBefore = ((EntityLivingBase) target).getHealth();
        chat("\u00a77[" + (index + 1) + "/" + METHODS.size() + "] \u00a7f" + method.name);
        try {
            method.attack.run(player, target);
        } catch (Throwable t) {
            results.add("\u00a78- threw " + t.getClass().getSimpleName() + "  " + method.name);
            timer = 1;
            return;
        }
        timer = SETTLE_TICKS;
    }

    private static void report() {
        chat("\u00a76\u00a7l--- Attack test results ---");
        chat("\u00a77Weapon: \u00a7f" + heldAtStart);
        for (String line : results) chat("  " + line);
        boolean any = false;
        for (String line : results) if (line.contains("\u2713")) any = true;
        if (any) {
            chat("\u00a7aAt least one method works - I will make that the default path.");
        } else {
            chat("\u00a7cNothing damaged the target with this weapon.");
            chat("\u00a77Now repeat with a weapon you CAN normally use. If everything passes then,");
            chat("\u00a77there is no packet bypass and the nunchaku simply is not locked for you.");
        }
    }

    private static void chat(String message) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null) {
            player.sendMessage(new TextComponentString("\u00a76[RLUtility] \u00a7r" + message));
        }
    }
}
