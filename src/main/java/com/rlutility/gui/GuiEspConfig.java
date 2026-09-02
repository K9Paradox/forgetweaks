package com.rlutility.gui;

import com.rlutility.modules.EspRenderHelper;
import com.rlutility.modules.FeatureConfig;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Compact ESP configuration table.
 *
 * <p>The Visuals tab had grown one row per category for the style alone - eleven near-identical
 * lines of "Style: X" that pushed everything else out of view. Categories are a grid of the same
 * three properties, so they belong in a grid: one row per category, with its enable toggle, style
 * and colour swatch side by side.</p>
 *
 * <p>Layout follows the convention most utility clients use for this kind of matrix: a fixed header
 * naming the columns, one line per entry, and click targets aligned into columns so the eye can
 * scan down a single property instead of reading every row.</p>
 */
public class GuiEspConfig extends GuiScreen {

    private static final int PANEL_W = 400;
    private static final int ROW_H = 18;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 30;

    private static final String[] STYLE_NAMES = {"Box", "Corners", "Filled", "Footprint", "Outline"};

    private final GuiScreen parent;

    public GuiEspConfig(GuiScreen parent) {
        this.parent = parent;
    }

    private int panelH() {
        return HEADER_H + EspRenderHelper.Kind.values().length * ROW_H + FOOTER_H;
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelY() {
        return (this.height - panelH()) / 2;
    }

    // ------------------------------------------------------------- toggles

    private static boolean enabled(EspRenderHelper.Kind kind) {
        switch (kind) {
            case CHEST: return FeatureConfig.espChests;
            case SPAWNER: return FeatureConfig.espSpawners;
            case WAYSTONE: return FeatureConfig.espWaystones;
            case CONTAINER: return FeatureConfig.espAllContainers;
            case BOSS: return FeatureConfig.espDragons;
            case HOSTILE: return FeatureConfig.espHostiles;
            case PLAYER: return FeatureConfig.espPlayers;
            case ITEM: return FeatureConfig.espItems;
            case MODDED: return FeatureConfig.espModdedMobs;
            default: return true; // custom lists are driven by their contents
        }
    }

    private static void toggle(EspRenderHelper.Kind kind) {
        switch (kind) {
            case CHEST: FeatureConfig.espChests = !FeatureConfig.espChests; break;
            case SPAWNER: FeatureConfig.espSpawners = !FeatureConfig.espSpawners; break;
            case WAYSTONE: FeatureConfig.espWaystones = !FeatureConfig.espWaystones; break;
            case CONTAINER: FeatureConfig.espAllContainers = !FeatureConfig.espAllContainers; break;
            case BOSS: FeatureConfig.espDragons = !FeatureConfig.espDragons; break;
            case HOSTILE: FeatureConfig.espHostiles = !FeatureConfig.espHostiles; break;
            case PLAYER: FeatureConfig.espPlayers = !FeatureConfig.espPlayers; break;
            case ITEM: FeatureConfig.espItems = !FeatureConfig.espItems; break;
            case MODDED: FeatureConfig.espModdedMobs = !FeatureConfig.espModdedMobs; break;
            default: break;
        }
    }

    /** Custom-list categories have no on/off of their own; the list decides. */
    private static boolean toggleable(EspRenderHelper.Kind kind) {
        return kind != EspRenderHelper.Kind.CUSTOM_BLOCK && kind != EspRenderHelper.Kind.CUSTOM_ENTITY;
    }

    // -------------------------------------------------------------- input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        int x = panelX();
        int y = panelY() + HEADER_H;
        EspRenderHelper.Kind[] kinds = EspRenderHelper.Kind.values();

        for (int i = 0; i < kinds.length; i++) {
            int rowY = y + i * ROW_H;
            if (mouseY < rowY || mouseY >= rowY + ROW_H) continue;
            EspRenderHelper.Kind kind = kinds[i];

            if (mouseX >= x + 10 && mouseX < x + 150 && toggleable(kind)) {
                toggle(kind);
                return;
            }
            if (mouseX >= x + 160 && mouseX < x + 260) {
                // Right click steps backwards so a mis-click is one click to undo.
                FeatureConfig.cycleEspStyle(kind.ordinal(), mouseButton == 1 ? -1 : 1);
                return;
            }
            return;
        }

        int footY = panelY() + panelH() - FOOTER_H + 6;
        if (mouseY >= footY && mouseY < footY + 18) {
            if (mouseX >= x + 10 && mouseX < x + 110) {
                for (EspRenderHelper.Kind k : kinds) FeatureConfig.cycleEspStyle(k.ordinal(), 0);
                setAll(4);
                return;
            }
            if (mouseX >= x + 118 && mouseX < x + 218) {
                setAll(1);
                return;
            }
            if (mouseX >= x + PANEL_W - 80 && mouseX < x + PANEL_W - 10) {
                FeatureConfig.saveConfig();
                this.mc.displayGuiScreen(parent);
            }
        }
    }

    private static void setAll(int style) {
        for (EspRenderHelper.Kind k : EspRenderHelper.Kind.values()) {
            int cur = k.style();
            FeatureConfig.cycleEspStyle(k.ordinal(), ((style - cur) % 5 + 5) % 5);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            FeatureConfig.saveConfig();
            this.mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    // -------------------------------------------------------------- render

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int x = panelX();
        int y = panelY();
        int h = panelH();

        drawDefaultBackground();
        drawRect(x, y, x + PANEL_W, y + h, 0xD012161E);
        drawRect(x, y, x + PANEL_W, y + 22, 0xEE1E2430);
        drawRect(x, y + 21, x + PANEL_W, y + 22, 0xFFFFC24B);

        drawCenteredString(this.fontRenderer,
                TextFormatting.GOLD + "" + TextFormatting.BOLD + "ESP Categories",
                this.width / 2, y + 7, 0xFFFFFF);

        // Column headers
        this.fontRenderer.drawStringWithShadow(TextFormatting.DARK_GRAY + "CATEGORY", x + 10, y + 30, 0xFFFFFF);
        this.fontRenderer.drawStringWithShadow(TextFormatting.DARK_GRAY + "STYLE", x + 160, y + 30, 0xFFFFFF);
        this.fontRenderer.drawStringWithShadow(TextFormatting.DARK_GRAY + "COLOUR", x + 280, y + 30, 0xFFFFFF);

        EspRenderHelper.Kind[] kinds = EspRenderHelper.Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            EspRenderHelper.Kind kind = kinds[i];
            int rowY = y + HEADER_H + i * ROW_H;
            boolean hovered = mouseY >= rowY && mouseY < rowY + ROW_H
                    && mouseX >= x && mouseX < x + PANEL_W;
            if (hovered) drawRect(x + 4, rowY, x + PANEL_W - 4, rowY + ROW_H - 2, 0x14FFFFFF);

            boolean on = enabled(kind);
            String name = (toggleable(kind)
                    ? (on ? TextFormatting.GREEN + "\u2714 " : TextFormatting.DARK_GRAY + "\u2718 ")
                    : TextFormatting.DARK_GRAY + "\u2022 ")
                    + (on ? TextFormatting.WHITE : TextFormatting.DARK_GRAY) + kind.title;
            this.fontRenderer.drawStringWithShadow(name, x + 10, rowY + 5, 0xFFFFFF);

            int style = kind.style();
            drawRect(x + 158, rowY + 2, x + 258, rowY + ROW_H - 4, 0x22FFC24B);
            this.fontRenderer.drawStringWithShadow(
                    (style == 4 ? TextFormatting.AQUA : TextFormatting.GOLD) + STYLE_NAMES[style],
                    x + 164, rowY + 5, 0xFFFFFF);

            // Colour swatch straight from the Kind, so it always matches what renders.
            int argb = 0xFF000000
                    | ((int) (kind.color[0] * 255) << 16)
                    | ((int) (kind.color[1] * 255) << 8)
                    | (int) (kind.color[2] * 255);
            drawRect(x + 280, rowY + 4, x + 310, rowY + ROW_H - 5, argb);
        }

        int footY = y + h - FOOTER_H + 6;
        drawRect(x + 10, footY, x + 110, footY + 18, 0x2233FF77);
        this.fontRenderer.drawStringWithShadow(TextFormatting.WHITE + "All \u2192 Outline", x + 18, footY + 5, 0xFFFFFF);
        drawRect(x + 118, footY, x + 218, footY + 18, 0x22FFFFFF);
        this.fontRenderer.drawStringWithShadow(TextFormatting.WHITE + "All \u2192 Corners", x + 126, footY + 5, 0xFFFFFF);
        drawRect(x + PANEL_W - 80, footY, x + PANEL_W - 10, footY + 18, 0x22FFC24B);
        this.fontRenderer.drawStringWithShadow(TextFormatting.GOLD + "Done", x + PANEL_W - 58, footY + 5, 0xFFFFFF);

        this.fontRenderer.drawStringWithShadow(
                TextFormatting.DARK_GRAY + "Click name to toggle \u00b7 click style to cycle (right-click reverses)",
                x + 10, y + h - 11, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
