package com.rlutility.gui;

import com.rlutility.RLUtilityMod;
import com.rlutility.modules.AutoReforgerHandler;
import com.rlutility.modules.Feature;
import com.rlutility.modules.FeatureConfig;
import com.rlutility.modules.FeatureRegistry;
import com.rlutility.modules.DupeExploitHandler;
import com.rlutility.modules.EspRenderHelper;
import com.rlutility.modules.LevelUpExploitHandler;
import com.rlutility.modules.QuestExploitHandler;
import com.rlutility.modules.ReskillableHelper;
import com.rlutility.modules.XRayHandler;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The RLUtility hub.
 *
 * <p>Custom-drawn panel: category rail on the left, scrollable module list on the right, live
 * search, and inline numeric settings. The footer wraps the hovered row's description over several
 * lines rather than truncating it.</p>
 */
public class GuiUtilityMenu extends GuiScreen {

    // ---------------------------------------------------------------- palette
    // Flat dark palette with a single accent. Rows are near-transparent so the surface reads as
    // one panel instead of a stack of boxes, which is what made the old list look busy.
    private static final int COL_SHADOW    = 0x99000000;
    private static final int COL_PANEL     = 0xF20B0E14;
    private static final int COL_HEADER    = 0xFF11161F;
    private static final int COL_RAIL      = 0xFF0D1118;
    private static final int COL_ROW       = 0x00000000;
    private static final int COL_ROW_HOVER = 0x1AFFFFFF;
    private static final int COL_SEL       = 0x26FFC24B;
    private static final int COL_ACCENT    = 0xFFFFC24B;
    private static final int COL_TEXT      = 0xFFE8EEF5;
    private static final int COL_DIM       = 0xFF6B7684;
    private static final int COL_ON        = 0xFF4ADE80;
    private static final int COL_OFF       = 0xFF2A313B;
    private static final int COL_LINE      = 0xFF1A2130;
    private static final int COL_SECTION   = 0xFF8B97A6;

    /** Rail accent per category, so tabs are distinguishable at a glance. */
    private static int categoryColor(Feature.Category c) {
        switch (c) {
            case COMBAT:   return 0xFFF87171;
            case MOVEMENT: return 0xFF60A5FA;
            case SURVIVAL: return 0xFF4ADE80;
            case SKILLS:   return 0xFFC084FC;
            case EXPLOITS: return 0xFFFBBF24;
            case VISUALS:  return 0xFF22D3EE;
            case HUD:      return 0xFFF472B6;
            default:       return 0xFF94A3B8;
        }
    }

    // ----------------------------------------------------------------- layout
    private static final int PANEL_W = 480;
    private static final int PANEL_H = 300;
    private static final int HEADER_H = 34;
    private static final int FOOTER_H = 38;
    private static final int RAIL_W = 116;
    private static final int SCROLLBAR_W = 4;
    private static final int ROW_H = 20;
    private static final int ROW_GAP = 1;
    private static final int FOOTER_LINES = 3;

    // ------------------------------------------------------------------ state
    private static Feature.Category selectedCategory = Feature.Category.COMBAT;
    private static String searchQuery = "";

    private int panelX, panelY;
    private int scroll = 0;
    private int caretTimer = 0;
    private String hoveredDesc = null;

    private final List<Row> rows = new ArrayList<>();

    // ------------------------------------------------------------------- rows
    /** Wrapper so the nested row classes never touch Gui's protected static drawRect directly. */
    private static void rect(int left, int top, int right, int bottom, int color) {
        drawRect(left, top, right, bottom, color);
    }

    /**
     * Clip drawing to a rectangle. Without this a row that is only half scrolled into view still
     * draws in full and bleeds over the header and footer, which was the main source of overlap.
     * GL scissor coordinates are in real pixels from the bottom-left, hence the scale conversion.
     */
    private void beginClip(int left, int top, int right, int bottom) {
        ScaledResolution res = new ScaledResolution(mc);
        int scale = res.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left * scale,
                mc.displayHeight - bottom * scale,
                Math.max(0, (right - left)) * scale,
                Math.max(0, (bottom - top)) * scale);
    }

    private void endClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /** Trims text to fit, appending an ellipsis, so labels never run under the controls. */
    String fit(String text, int maxWidth) {
        if (fontRenderer.getStringWidth(text) <= maxWidth) return text;
        return fontRenderer.trimStringToWidth(text, Math.max(0, maxWidth - 6)) + "\u2026";
    }

    /**
     * Non-interactive divider naming a group of rows. Groups are derived automatically from the
     * "Prefix: Name" convention already used across the registry, so no feature needs annotating.
     */
    private static class SectionRow extends Row {
        SectionRow(String label) {
            super(label, null);
        }

        @Override boolean selectable() {
            return false;
        }

        @Override void render(GuiUtilityMenu gui, int x, int y, int width) {
            String text = label.toUpperCase();
            gui.drawText(text, x + 8, y + ROW_H - 11, COL_SECTION);
            int tx = x + 12 + gui.fontRenderer.getStringWidth(text);
            if (tx < x + width - 8) {
                rect(tx, y + ROW_H - 7, x + width - 8, y + ROW_H - 6, COL_LINE);
            }
        }
    }

    private abstract static class Row {
        String label;
        String desc;
        Row(String label, String desc) {
            this.label = label;
            this.desc = desc;
        }
        abstract void click(int button);
        void render(GuiUtilityMenu gui, int x, int y, int width) {}

        /** Section headers are skipped by hover and click handling. */
        boolean selectable() {
            return true;
        }
    }

    private static class ToggleRow extends Row {
        final Feature feature;
        ToggleRow(Feature f) {
            super(f.name, f.desc);
            this.feature = f;
        }
        @Override void click(int button) {
            feature.toggle();
        }
        @Override void render(GuiUtilityMenu gui, int x, int y, int width) {
            boolean on = feature.isEnabled();
            int pillW = 28;
            int px = x + width - pillW - 10;
            gui.drawText(gui.fit(feature.name, px - (x + 10) - 6), x + 10, y + 7, on ? COL_TEXT : COL_DIM);

            int py = y + 5;
            // Inset corners fake a rounded pill without a texture.
            rect(px + 1, py, px + pillW - 1, py + 10, on ? COL_ON : COL_OFF);
            rect(px, py + 1, px + pillW, py + 9, on ? COL_ON : COL_OFF);
            int knobX = on ? px + pillW - 9 : px + 1;
            rect(knobX, py + 1, knobX + 8, py + 9, 0xFF0B0E14);
        }
    }

    private static class SettingRow extends Row {
        final FeatureRegistry.Setting setting;
        SettingRow(FeatureRegistry.Setting s) {
            super(s.name, s.desc + "  \u00a78[left click +  \u00b7  right click -]");
            this.setting = s;
        }
        @Override void click(int button) {
            setting.adjust(button == 1 ? -1 : 1);
        }
        @Override void render(GuiUtilityMenu gui, int x, int y, int width) {
            String value = setting.value();
            int w = gui.fontRenderer.getStringWidth(value) + 14;
            int chipX = x + width - w - 10;
            gui.drawText(gui.fit(setting.name, chipX - (x + 10) - 6), x + 10, y + 7, COL_DIM);
            rect(chipX, y + 4, chipX + w, y + ROW_H - 4, 0x22FFC24B);
            gui.drawText(value, chipX + 7, y + 7, COL_ACCENT);
        }
    }

    private static class ActionRow extends Row {
        final Runnable action;
        final String value;
        ActionRow(String label, String desc, String value, Runnable action) {
            super(label, desc);
            this.value = value;
            this.action = action;
        }
        @Override void click(int button) {
            action.run();
        }
        @Override void render(GuiUtilityMenu gui, int x, int y, int width) {
            int labelLimit = width - 20;
            if (value != null) {
                int w = gui.fontRenderer.getStringWidth(value) + 14;
                int chipX = x + width - w - 10;
                labelLimit = chipX - (x + 10) - 6;
                rect(chipX, y + 4, chipX + w, y + ROW_H - 4, 0x22FFC24B);
                gui.drawText(value, chipX + 7, y + 7, COL_ACCENT);
            }
            gui.drawText(gui.fit(label, labelLimit), x + 10, y + 7, COL_TEXT);
        }
    }

    // --------------------------------------------------------------- lifecycle
    @Override
    public void initGui() {
        super.initGui();
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
        rebuildRows();
    }

    @Override
    public void updateScreen() {
        caretTimer++;
        if (toastTicks > 0) toastTicks--;
        if (saveFlash > 0) saveFlash--;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void rebuildRows() {
        rows.clear();

        if (!searchQuery.isEmpty()) {
            for (Feature f : FeatureRegistry.search(searchQuery)) rows.add(new ToggleRow(f));
            clampScroll();
            return;
        }

        if (selectedCategory == Feature.Category.TOOLS) {
            buildToolsRows();
            clampScroll();
            return;
        }

        addGrouped(FeatureRegistry.byCategory(selectedCategory));
        for (FeatureRegistry.Setting s : FeatureRegistry.settingsFor(selectedCategory)) rows.add(new SettingRow(s));
        clampScroll();
    }

    /**
     * Splits "Prefix: Name" rows into groups, emitting a section header when the prefix changes.
     * Long tabs stop reading as one undifferentiated wall of toggles.
     */
    private void addGrouped(List<Feature> features) {
        String lastGroup = null;
        for (Feature f : features) {
            int colon = f.name.indexOf(':');
            String group = colon > 0 ? f.name.substring(0, colon).trim() : null;
            if (group != null && !group.equals(lastGroup)) {
                rows.add(new SectionRow(group));
            }
            if (group == null && lastGroup != null) {
                lastGroup = null;
            } else {
                lastGroup = group;
            }
            rows.add(new ToggleRow(f));
        }
    }

    private void buildToolsRows() {
        addGrouped(FeatureRegistry.byCategory(Feature.Category.TOOLS));

        rows.add(new ActionRow("Edit XRay Blocks",
                "Pick which blocks XRay highlights. Add the block you are looking at, browse the full "
                        + "registry, or type an id. Wildcards like \"iceandfire:*\" work.",
                XRayHandler.targetCount() + " blocks", () -> mc.displayGuiScreen(new GuiTargetListEditor(
                        this, "XRay Blocks",
                        () -> FeatureConfig.xrayBlocks,
                        v -> { FeatureConfig.xrayBlocks = v; XRayHandler.forceRescan(); },
                        GuiUtilityMenu::allBlockIds,
                        XRayHandler::lookingAtBlockId))));

        rows.add(new ActionRow("ESP Categories",
                "One table for every ESP category: enable, style and colour side by side. Replaces "
                        + "the eleven separate style rows that were crowding this tab.",
                EspRenderHelper.Kind.values().length + " types",
                () -> mc.displayGuiScreen(new GuiEspConfig(this))));

        rows.add(new ActionRow("Edit ESP Entities",
                "Extra mobs to outline, on top of the category toggles. Look at a creature and add it.",
                EspRenderHelper.customEntities().size() + " entities", () -> mc.displayGuiScreen(new GuiTargetListEditor(
                        this, "ESP Entities",
                        () -> FeatureConfig.espCustomEntities,
                        v -> FeatureConfig.espCustomEntities = v,
                        GuiUtilityMenu::allEntityIds,
                        EspRenderHelper::lookingAtId))));

        rows.add(new ActionRow("Edit ESP Blocks",
                "Extra blocks and containers to outline, such as waystones or modded chests.",
                EspRenderHelper.customBlocks().size() + " blocks", () -> mc.displayGuiScreen(new GuiTargetListEditor(
                        this, "ESP Blocks",
                        () -> FeatureConfig.espCustomBlocks,
                        v -> FeatureConfig.espCustomBlocks = v,
                        GuiUtilityMenu::allBlockIds,
                        XRayHandler::lookingAtBlockId))));

        rows.add(new ActionRow("Magnet Whitelist",
                "When non-empty, only these items are pulled. Leave empty to pull everything.",
                "edit", () -> mc.displayGuiScreen(new GuiTargetListEditor(
                        this, "Magnet Whitelist",
                        () -> FeatureConfig.magnetWhitelist,
                        v -> FeatureConfig.magnetWhitelist = v,
                        GuiUtilityMenu::allItemIds, () -> null))));

        rows.add(new ActionRow("Magnet Blacklist",
                "Items the magnet always ignores, so your inventory stops filling with junk.",
                "edit", () -> mc.displayGuiScreen(new GuiTargetListEditor(
                        this, "Magnet Blacklist",
                        () -> FeatureConfig.magnetBlacklist,
                        v -> FeatureConfig.magnetBlacklist = v,
                        GuiUtilityMenu::allItemIds, () -> null))));

        if (ReskillableHelper.isModLoaded()) {
            rows.add(new ActionRow("Unlock Held Item",
                    "Buys exactly the Reskillable levels the item in your hand is missing. This spends "
                            + "real XP because the server validates every level-up - there is no free path.",
                    "buy", () -> announceResult(ReskillableHelper.unlockHeldItem())));
        }

        if (com.rlutility.modules.TrinketRaceHandler.isAvailable()) {
            rows.add(new ActionRow("Trinkets: Set Race",
                    "SelectRacePacket only checks null/none/blacklist. If the server enables the race "
                            + "selection menu the authorization check is short-circuited entirely; even "
                            + "when it is not, the authorization records THAT you may change race, never "
                            + "which one - so any pending authorization redeems for any race. "
                            + "Use /rlu race <race> [element].",
                    "list", () -> {
                        announceResult("\u00a76Races: \u00a7f" + String.join(", ",
                                com.rlutility.modules.TrinketRaceHandler.listNames()));
                        announceResult("\u00a77Apply with /rlu race <race> [element]");
                        mc.displayGuiScreen(null);
                    }));
        }

        if (QuestExploitHandler.isModLoaded()) {
            rows.add(new ActionRow("Quest Sweep",
                    "BetterQuesting's quest_action channel has no permission check, and its id array is "
                            + "client supplied. Runs a detection pass over every quest, then claims what "
                            + "became claimable. Rewards you already qualify for only - claim is validated.",
                    "run", QuestExploitHandler::sweepAll));
        }

        if (DupeExploitHandler.isModLoaded()) {
            rows.add(new ActionRow("Desync Dupe",
                    "Guided save-abort dupe. Relog for a clean rollback point, arm, bank your items, relog.",
                    DupeExploitHandler.isArmed() ? "ARMED" : "start", () -> {
                        DupeExploitHandler.begin();
                        mc.displayGuiScreen(null);
                    }));
        }

        byte spec = LevelUpExploitHandler.currentSpecialization();
        rows.add(new ActionRow("Level Up! 2 Specialization",
                "Free class change - the server only charges the reclass cost when the client asks it to. "
                        + "Left click cycles Mining / Crafting / Combat.",
                LevelUpExploitHandler.className(spec), LevelUpExploitHandler::cycleClass) {
            @Override void click(int button) {
                super.click(button);
                rebuildRows();
            }
        });

        rows.add(new ActionRow("Reforge Target", "Quality that Auto Reforge stops rolling at.",
                FeatureConfig.targetQuality, () -> {
            String[] presets = AutoReforgerHandler.QUALITY_PRESETS;
            int next = 0;
            for (int i = 0; i < presets.length; i++) {
                if (presets[i].equals(FeatureConfig.targetQuality)) {
                    next = (i + 1) % presets.length;
                    break;
                }
            }
            FeatureConfig.targetQuality = presets[next];
            FeatureConfig.saveConfig();
            rebuildRows();
        }));

        rows.add(new ActionRow("Level Up! 2 Target Level",
                "Level applied to every skill by the button below. 0 means each skill's own cap. Left click +1, right click -1.",
                String.valueOf(FeatureConfig.customLevelTarget), () -> {
            FeatureConfig.customLevelTarget = Math.min(1000, FeatureConfig.customLevelTarget + 1);
            FeatureConfig.saveConfig();
            rebuildRows();
        }) {
            @Override void click(int button) {
                if (button == 1) {
                    FeatureConfig.customLevelTarget = Math.max(0, FeatureConfig.customLevelTarget - 1);
                    FeatureConfig.saveConfig();
                    rebuildRows();
                } else {
                    super.click(button);
                }
            }
        });

        rows.add(new ActionRow("Apply Level To All Skills",
                "Sends the Level Up! 2 skill packet with button=-1 and levelSpend=0, so the server charges nothing.",
                "run", () -> LevelUpExploitHandler.setAllSkillsLevel(FeatureConfig.customLevelTarget)));

        rows.add(new ActionRow("Apply Safe Preset",
                "Level Up! 2 preset that skips the skills known to break movement.",
                "run", LevelUpExploitHandler::applySafePreset));

        rows.add(new ActionRow("Skill Tree Editor",
                "Per-skill Level Up! 2 editor.",
                "open", () -> this.mc.displayGuiScreen(new GuiLevelUpConfig(this))));

        rows.add(new ActionRow("Save Configuration",
                "Writes config/rlutility_features.cfg immediately.",
                saveFlash > 0 ? "\u2714 saved" : "save", () -> {
                    boolean ok = FeatureConfig.saveConfig();
                    // Report the real outcome and path; a silent catch used to hide write failures.
                    toast = (ok ? "\u00a7a\u2714 Saved: " : "\u00a7c\u2718 Save failed: ")
                            + FeatureConfig.lastSaveResult;
                    toastTicks = 120;
                    saveFlash = 120;
                    announceResult((ok ? "\u00a7aSaved \u00a77" : "\u00a7cSave FAILED \u00a77")
                            + FeatureConfig.lastSaveResult);
                    rebuildRows();
                }));
    }

    /** Transient confirmation shown in the footer. */
    private static String toast = null;
    private static int toastTicks = 0;
    /** Drives the checkmark that briefly replaces the Save row's chip. */
    private static int saveFlash = 0;

    private void clampScroll() {
        int maxScroll = Math.max(0, rows.size() * (ROW_H + ROW_GAP) - contentHeight());
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
    }

    private int contentHeight() {
        return PANEL_H - HEADER_H - FOOTER_H - 8;
    }

    // ------------------------------------------------------------------ render
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        hoveredDesc = null;

        int x = panelX, y = panelY;

        // drop shadow + body
        drawRect(x - 3, y - 3, x + PANEL_W + 3, y + PANEL_H + 3, COL_SHADOW);
        drawRect(x - 1, y - 1, x + PANEL_W + 1, y + PANEL_H + 1, COL_LINE);
        drawRect(x, y, x + PANEL_W, y + PANEL_H, COL_PANEL);
        drawRect(x, y, x + PANEL_W, y + HEADER_H, COL_HEADER);
        // Thin accent under the header, tinted to the active category.
        drawRect(x, y + HEADER_H - 1, x + PANEL_W, y + HEADER_H, categoryColor(selectedCategory));
        drawRect(x, y + HEADER_H, x + RAIL_W, y + PANEL_H - FOOTER_H, COL_RAIL);
        drawRect(x + RAIL_W, y + HEADER_H, x + RAIL_W + 1, y + PANEL_H - FOOTER_H, COL_LINE);
        drawRect(x, y + PANEL_H - FOOTER_H, x + PANEL_W, y + PANEL_H - FOOTER_H + 1, COL_LINE);

        drawHeader(x, y, mouseX, mouseY);
        drawRail(x, y, mouseX, mouseY);
        drawContent(x, y, mouseX, mouseY);
        drawFooter(x, y);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawHeader(int x, int y, int mouseX, int mouseY) {
        // Accent block + wordmark reads cleaner than a bold string alone.
        drawRect(x + 12, y + 11, x + 15, y + 23, COL_ACCENT);
        drawText("\u00a7lRLUtility", x + 21, y + 13, COL_TEXT);
        int versionX = x + 21 + fontRenderer.getStringWidth("RLUtility") + 10;
        String version = fit("v" + RLUtilityMod.VERSION + "  \u00b7  RLCraft 2.9.3", (x + PANEL_W - 178) - versionX - 8);
        drawText(version, versionX, y + 13, COL_DIM);

        // search field
        int sx = x + PANEL_W - 178;
        int sy = y + 9;
        boolean hover = inside(mouseX, mouseY, sx, sy, 148, 18);
        drawRect(sx, sy, sx + 148, sy + 17, hover ? 0x22FFFFFF : 0x14FFFFFF);
        drawRect(sx, sy + 16, sx + 148, sy + 17, hover ? COL_ACCENT : COL_LINE);
        drawText("\u00a78\u26B2", sx + 5, sy + 5, COL_DIM);
        String shown = searchQuery.isEmpty() ? "\u00a78Search modules..." : fit(searchQuery, 122);
        drawText(shown, sx + 16, sy + 5, searchQuery.isEmpty() ? COL_DIM : COL_TEXT);
        if (!searchQuery.isEmpty() && (caretTimer / 6) % 2 == 0) {
            int caretX = Math.min(sx + 16 + fontRenderer.getStringWidth(fit(searchQuery, 122)), sx + 142);
            drawRect(caretX + 1, sy + 4, caretX + 2, sy + 14, COL_ACCENT);
        }

        // close button
        int cxb = x + PANEL_W - 24;
        boolean hoverClose = inside(mouseX, mouseY, cxb, y + 9, 17, 17);
        drawRect(cxb, y + 9, cxb + 17, y + 26, hoverClose ? 0x33F87171 : 0x00000000);
        drawText("\u2715", cxb + 5, y + 13, hoverClose ? 0xFFF87171 : COL_DIM);
    }

    private void drawRail(int x, int y, int mouseX, int mouseY) {
        Feature.Category[] cats = Feature.Category.values();
        int ry = y + HEADER_H + 6;
        beginClip(x, y + HEADER_H, x + RAIL_W, y + PANEL_H - FOOTER_H);

        for (Feature.Category c : cats) {
            boolean selected = (c == selectedCategory) && searchQuery.isEmpty();
            boolean hover = inside(mouseX, mouseY, x + 4, ry, RAIL_W - 8, 22);

            int accent = categoryColor(c);

            if (selected) {
                drawRect(x + 4, ry, x + RAIL_W - 5, ry + 22, COL_SEL);
                // Full-height accent bar in the category's own colour.
                drawRect(x + 4, ry, x + 7, ry + 22, accent);
            } else if (hover) {
                drawRect(x + 4, ry, x + RAIL_W - 5, ry + 22, COL_ROW_HOVER);
                drawRect(x + 4, ry + 6, x + 6, ry + 16, accent);
            } else {
                // Dim dot keeps the colour association even when inactive.
                drawRect(x + 5, ry + 9, x + 8, ry + 12, (accent & 0x00FFFFFF) | 0x77000000);
            }

            int enabled = 0;
            List<Feature> inCat = FeatureRegistry.byCategory(c);
            for (Feature f : inCat) if (f.isEnabled()) enabled++;

            // Only show the count when something is on; "0/9" everywhere was pure noise.
            String count = enabled > 0 ? String.valueOf(enabled) : null;
            int countW = count == null ? 0 : fontRenderer.getStringWidth(count) + 12;
            drawText(fit(c.title, RAIL_W - 30 - countW), x + 15, ry + 7,
                    selected ? COL_TEXT : (hover ? COL_TEXT : COL_DIM));
            if (count != null) {
                int bx = x + RAIL_W - 11 - fontRenderer.getStringWidth(count);
                drawRect(bx - 4, ry + 5, bx + fontRenderer.getStringWidth(count) + 4, ry + 17,
                        (accent & 0x00FFFFFF) | 0x33000000);
                drawText(count, bx, ry + 8, accent);
            }
            ry += 24;
        }
        endClip();
    }

    private void drawContent(int x, int y, int mouseX, int mouseY) {
        int top = y + HEADER_H + 6;
        int bottom = y + PANEL_H - FOOTER_H - 6;
        int cx = x + RAIL_W + 8;
        int totalH = rows.size() * (ROW_H + ROW_GAP);
        int viewH = bottom - top;
        boolean needsBar = totalH > viewH;
        // Always leave the gutter free so rows do not sit underneath the scrollbar.
        int cw = (x + PANEL_W - 8) - cx - (needsBar ? SCROLLBAR_W + 4 : 0);

        if (rows.isEmpty()) {
            String msg = searchQuery.isEmpty()
                    ? "\u00a78Nothing in this category yet."
                    : "\u00a78No modules match \"" + searchQuery + "\"";
            drawText(msg, cx + 4, top + 12, COL_DIM);
            return;
        }

        beginClip(cx, top, cx + cw + (needsBar ? SCROLLBAR_W + 4 : 0), bottom);
        try {
            for (int i = 0; i < rows.size(); i++) {
                int ry = top + i * (ROW_H + ROW_GAP) - scroll;
                if (ry + ROW_H < top || ry > bottom) continue;

                Row row = rows.get(i);
                boolean hover = row.selectable() && inside(mouseX, mouseY, cx, ry, cw, ROW_H)
                        && mouseY >= top && mouseY <= bottom;

                if (hover) {
                    drawRect(cx, ry, cx + cw, ry + ROW_H, COL_ROW_HOVER);
                    drawRect(cx, ry, cx + 2, ry + ROW_H, COL_ACCENT);
                    hoveredDesc = row.desc;
                } else if (row.selectable() && COL_ROW != 0) {
                    drawRect(cx, ry, cx + cw, ry + ROW_H, COL_ROW);
                }
                row.render(this, cx, ry, cw);
            }
        } finally {
            endClip();
        }

        if (needsBar) {
            int barH = Math.max(20, (int) ((float) viewH / totalH * viewH));
            int barY = top + (int) ((float) scroll / (totalH - viewH) * (viewH - barH));
            int bx = x + PANEL_W - 8 - SCROLLBAR_W;
            drawRect(bx, top, bx + SCROLLBAR_W, bottom, 0x18FFFFFF);
            drawRect(bx, barY, bx + SCROLLBAR_W, barY + barH, COL_ACCENT);
        }
    }

    private void drawFooter(int x, int y) {
        int fy = y + PANEL_H - FOOTER_H + 5;

        // A confirmation toast outranks the hover description for a couple of seconds.
        if (toastTicks > 0 && toast != null) {
            drawText(toast, x + 10, fy, 0xFF6EE7A0);
            return;
        }

        String text = hoveredDesc != null
                ? hoveredDesc
                : "\u00a78Hover a row for details  \u00b7  type to search  \u00b7  scroll wheel to scroll  \u00b7  Esc to close";

        // Wrap instead of truncating: descriptions were being cut off mid-word.
        List<String> lines = fontRenderer.listFormattedStringToWidth(text, PANEL_W - 20);
        for (int i = 0; i < lines.size() && i < FOOTER_LINES; i++) {
            String line = lines.get(i);
            // If the text is longer than we can show, ellipsize the final visible line.
            if (i == FOOTER_LINES - 1 && lines.size() > FOOTER_LINES) {
                line = fontRenderer.trimStringToWidth(line, PANEL_W - 30) + "...";
            }
            drawText(line, x + 10, fy + i * 10, COL_DIM);
        }
    }

    private void announceResult(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("\u00a76[RLUtility] \u00a7r" + message));
        }
    }

    private static List<String> allBlockIds() {
        List<String> out = new ArrayList<>();
        for (net.minecraft.util.ResourceLocation key : net.minecraft.block.Block.REGISTRY.getKeys()) {
            out.add(key.toString());
        }
        java.util.Collections.sort(out);
        return out;
    }

    private static List<String> allItemIds() {
        List<String> out = new ArrayList<>();
        for (net.minecraft.util.ResourceLocation key : net.minecraft.item.Item.REGISTRY.getKeys()) {
            out.add(key.toString());
        }
        java.util.Collections.sort(out);
        return out;
    }

    private static List<String> allEntityIds() {
        List<String> out = new ArrayList<>();
        for (net.minecraft.util.ResourceLocation key : net.minecraft.entity.EntityList.getEntityNameList()) {
            out.add(key.toString());
        }
        java.util.Collections.sort(out);
        return out;
    }

    void drawText(String text, int x, int y, int color) {
        GlStateManager.enableAlpha();
        fontRenderer.drawStringWithShadow(text, x, y, color);
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ------------------------------------------------------------------- input
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        int x = panelX, y = panelY;

        // close
        if (inside(mouseX, mouseY, x + PANEL_W - 24, y + 9, 17, 17)) {
            closeMenu();
            return;
        }

        // clear search by clicking the field with right mouse
        if (inside(mouseX, mouseY, x + PANEL_W - 178, y + 6, 148, 18) && mouseButton == 1) {
            searchQuery = "";
            scroll = 0;
            rebuildRows();
            return;
        }

        // category rail
        int ry = y + HEADER_H + 6;
        for (Feature.Category c : Feature.Category.values()) {
            if (inside(mouseX, mouseY, x + 4, ry, RAIL_W - 8, 22)) {
                selectedCategory = c;
                searchQuery = "";
                scroll = 0;
                rebuildRows();
                return;
            }
            ry += 24;
        }

        // rows - geometry must mirror drawContent exactly or clicks land on the wrong row
        int top = y + HEADER_H + 6;
        int bottom = y + PANEL_H - FOOTER_H - 6;
        int cx = x + RAIL_W + 8;
        int totalH = rows.size() * (ROW_H + ROW_GAP);
        boolean needsBar = totalH > (bottom - top);
        int cw = (x + PANEL_W - 8) - cx - (needsBar ? SCROLLBAR_W + 4 : 0);
        if (mouseY < top || mouseY > bottom) return;

        for (int i = 0; i < rows.size(); i++) {
            int rowY = top + i * (ROW_H + ROW_GAP) - scroll;
            if (rowY < top || rowY + ROW_H > bottom) continue;
            if (!rows.get(i).selectable()) continue;
            if (inside(mouseX, mouseY, cx, rowY, cw, ROW_H)) {
                rows.get(i).click(mouseButton);
                return;
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        scroll += (wheel > 0 ? -1 : 1) * (ROW_H + ROW_GAP) * 2;
        clampScroll();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (!searchQuery.isEmpty()) {
                searchQuery = "";
                scroll = 0;
                rebuildRows();
            } else {
                closeMenu();
            }
            return;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (!searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                scroll = 0;
                rebuildRows();
            }
            return;
        }
        if (ChatAllowedCharacters.isAllowedCharacter(typedChar) && searchQuery.length() < 24) {
            searchQuery += typedChar;
            scroll = 0;
            rebuildRows();
        }
    }

    private void closeMenu() {
        FeatureConfig.saveConfig();
        this.mc.displayGuiScreen(null);
        if (this.mc.currentScreen == null) this.mc.setIngameFocus();
    }

    @Override
    public void onGuiClosed() {
        FeatureConfig.saveConfig();
    }
}
