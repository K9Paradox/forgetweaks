package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Un-cancels Reskillable's lock on the <b>client</b> event bus.
 *
 * <h3>Why this is required, and why calling it a placebo was wrong</h3>
 * Reskillable's {@code LevelLockHandler} is a <em>common</em> handler: it is registered on both
 * sides and its {@code @SubscribeEvent(priority = HIGH)} methods run on your own client too.
 * {@code LeftClickBlock}, {@code RightClickBlock}, {@code RightClickItem} and {@code EntityInteract}
 * are all fired client-side first, and when the client cancels them the vanilla code path returns
 * early - so <b>no packet is ever sent to the server at all</b>.
 *
 * <p>That makes this handler necessary but not sufficient:</p>
 * <ul>
 *   <li><b>Necessary</b> - without it you cannot mine, place, or right-click anything the lock
 *       covers, because your own client refuses before the server is ever consulted. This is
 *       exactly the "can't break blocks or interact with entities at all" regression caused by
 *       removing it.</li>
 *   <li><b>Not sufficient</b> - once the packet does go out, the server runs the same check against
 *       its own copy of your levels and can still refuse. Block breaking usually survives because it
 *       is heavily client-predicted; melee damage does not, which is what
 *       {@link ReskillableAttackBypass} is for.</li>
 * </ul>
 *
 * <p>Deliberately absent: the old code also wrote {@code info.setLevel(32)} into the local
 * {@code PlayerData}. That is left out on purpose. It desynced the client's idea of your levels from
 * the server's, and it would make {@link ReskillableHelper#canUseHeldItem()} report "unlocked" for
 * everything - which would silently disable the attack bypass, since that only arms when it knows
 * the item is locked.</p>
 */
public class ReskillableClientUnlock {

    private static final boolean MOD_LOADED = Loader.isModLoaded("reskillable");

    private static int unCancelled = 0;

    /** Diagnostic counter: how many lock cancellations we have reverted this session. */
    public static int getUnCancelCount() {
        return unCancelled;
    }

    public static boolean isModLoaded() {
        return MOD_LOADED;
    }

    private static boolean active() {
        return MOD_LOADED && FeatureConfig.reskillableBypass;
    }

    /** Only ever touch the client bus; the server copy of this event is not ours to override. */
    private static boolean clientSide(net.minecraft.entity.Entity entity) {
        return entity != null && entity.world != null && entity.world.isRemote;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (active() && event.isCanceled() && clientSide(event.getEntityPlayer())) {
            event.setCanceled(false);
            unCancelled++;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (active() && event.isCanceled() && clientSide(event.getEntityPlayer())) {
            event.setCanceled(false);
            // Forge also gates the two sub-actions; both must be permitted or the click is a no-op.
            event.setUseBlock(net.minecraftforge.fml.common.eventhandler.Event.Result.ALLOW);
            event.setUseItem(net.minecraftforge.fml.common.eventhandler.Event.Result.ALLOW);
            unCancelled++;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (active() && event.isCanceled() && clientSide(event.getEntityPlayer())) {
            event.setCanceled(false);
            event.setUseBlock(net.minecraftforge.fml.common.eventhandler.Event.Result.ALLOW);
            event.setUseItem(net.minecraftforge.fml.common.eventhandler.Event.Result.ALLOW);
            unCancelled++;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (active() && event.isCanceled() && clientSide(event.getEntityPlayer())) {
            event.setCanceled(false);
            unCancelled++;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (active() && event.isCanceled() && clientSide(event.getEntityPlayer())) {
            event.setCanceled(false);
            unCancelled++;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (active() && event.isCanceled() && event.getWorld() != null && event.getWorld().isRemote) {
            event.setCanceled(false);
            unCancelled++;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLivingAttack(LivingAttackEvent event) {
        if (!active() || !event.isCanceled()) return;
        if (event.getEntity() == null || event.getEntity().world == null) return;
        if (!event.getEntity().world.isRemote) return;
        // Client-side only: keeps local hit feedback alive. The server decides the real outcome.
        event.setCanceled(false);
        unCancelled++;
    }

    static Minecraft mc() {
        return Minecraft.getMinecraft();
    }
}
