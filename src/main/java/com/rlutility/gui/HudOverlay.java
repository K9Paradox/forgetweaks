package com.rlutility.gui;

import com.rlutility.RLUtilityMod;
import com.rlutility.modules.Feature;
import com.rlutility.modules.FeatureConfig;
import com.rlutility.modules.FeatureRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Comparator;
import java.util.List;

/**
 * Lightweight in-game overlay: watermark, active-module list, a stats line and live target info.
 * Everything here is client-side cosmetics.
 */
public class HudOverlay extends Gui {

    private static final int ACCENT = 0xFFFFC24B;
    private static final int PANEL_BG = 0x90101418;
    private static final int TEXT = 0xFFE6EDF3;
    private static final int SUBTLE = 0xFF8B98A5;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!FeatureConfig.hudEnabled) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (mc.gameSettings.showDebugInfo || mc.currentScreen instanceof GuiUtilityMenu) return;

        ScaledResolution res = event.getResolution();
        FontRenderer fr = mc.fontRenderer;
        int screenWidth = res.getScaledWidth();

        int hudX = Math.max(0, Math.min(screenWidth - 20, FeatureConfig.hudX));
        int y = Math.max(0, FeatureConfig.hudY);
        if (FeatureConfig.hudWatermark) {
            String tag = "RLUtility";
            String ver = " v" + RLUtilityMod.VERSION + " \u00b7 RLCraft 2.9.3";
            int w = fr.getStringWidth(tag + ver);
            drawRect(hudX, y - 1, hudX + 4 + w, y + 10, PANEL_BG);
            fr.drawStringWithShadow(tag, hudX + 2, y + 1, ACCENT);
            fr.drawStringWithShadow(ver, hudX + 2 + fr.getStringWidth(tag), y + 1, SUBTLE);
            y += 13;
        }

        if (FeatureConfig.hudStats) {
            String stats = String.format("%d fps  \u00b7  %d ms  \u00b7  %.0f %.0f %.0f",
                    Minecraft.getDebugFPS(),
                    pingOf(mc),
                    mc.player.posX, mc.player.posY, mc.player.posZ);
            int w = fr.getStringWidth(stats);
            drawRect(hudX, y - 1, hudX + 4 + w, y + 10, PANEL_BG);
            fr.drawStringWithShadow(stats, hudX + 2, y + 1, TEXT);
            y += 13;

            // SimpleDifficulty survival readout (server-synced thirst + temperature).
            String env = com.rlutility.modules.SimpleDifficultyHelper.hudReadout();
            if (!env.isEmpty()) {
                int ew = fr.getStringWidth(env);
                drawRect(hudX, y - 1, hudX + 4 + ew, y + 10, PANEL_BG);
                fr.drawStringWithShadow(env, hudX + 2, y + 1, TEXT);
                y += 13;
            }
        }

        if (FeatureConfig.hudTargetInfo) {
            drawTargetInfo(mc, fr, y, hudX);
        }

        if (FeatureConfig.hudModuleList) {
            drawModuleList(fr, screenWidth);
        }
    }

    private void drawModuleList(FontRenderer fr, int screenWidth) {
        List<Feature> active = FeatureRegistry.activeForHud();
        if (active.isEmpty()) return;

        // Sorted longest-first, the classic clean array-list look.
        active.sort(Comparator.comparingInt((Feature f) -> fr.getStringWidth(f.name)).reversed());

        int y = Math.max(0, FeatureConfig.hudModuleY);
        int rightMargin = Math.max(0, FeatureConfig.hudModuleX);
        for (Feature f : active) {
            String label = f.name;
            int w = fr.getStringWidth(label);
            int x = screenWidth - w - rightMargin;

            drawRect(x - 2, y - 1, screenWidth - rightMargin + 2, y + 9, PANEL_BG);
            drawRect(screenWidth - rightMargin, y - 1, screenWidth - rightMargin + 1, y + 9, ACCENT);
            fr.drawStringWithShadow(label, x, y, TEXT);
            y += 11;
        }
    }

    private void drawTargetInfo(Minecraft mc, FontRenderer fr, int y, int hudX) {
        // Kill aura is gone; the HUD now reports whatever the crosshair is on.
        Entity target = null;
        net.minecraft.util.math.RayTraceResult hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.ENTITY) {
            target = hit.entityHit;
        }
        if (!(target instanceof EntityLivingBase) || target.isDead) return;

        EntityLivingBase living = (EntityLivingBase) target;
        float pct = living.getMaxHealth() <= 0 ? 0 : living.getHealth() / living.getMaxHealth();
        double dist = Math.sqrt(mc.player.getDistanceSq(target));

        String line = String.format("%s  %.1f/%.1f hp  \u00b7  %.1fm",
                living.getName(), living.getHealth(), living.getMaxHealth(), dist);
        int w = Math.max(fr.getStringWidth(line), 90);

        drawRect(hudX, y - 1, hudX + 4 + w, y + 16, PANEL_BG);
        fr.drawStringWithShadow(line, hudX + 2, y + 1, TEXT);

        int barX = 5;
        int barY = y + 11;
        drawRect(barX, barY, barX + w - 2, barY + 3, 0xFF2A2F36);
        int color = pct > 0.5F ? 0xFF4ADE80 : (pct > 0.25F ? 0xFFFACC15 : 0xFFF87171);
        drawRect(barX, barY, barX + (int) ((w - 2) * pct), barY + 3, color);
    }

    private int pingOf(Minecraft mc) {
        try {
            if (mc.getConnection() != null && mc.player != null) {
                net.minecraft.client.network.NetworkPlayerInfo info =
                        mc.getConnection().getPlayerInfo(mc.player.getUniqueID());
                if (info != null) return info.getResponseTime();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }
}
