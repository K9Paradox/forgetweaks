package com.rlutility.gui;

import com.rlutility.RLUtilityMod;
import com.rlutility.modules.AutoReforgerHandler;
import com.rlutility.modules.Feature;
import com.rlutility.modules.FeatureConfig;
import com.rlutility.modules.FeatureRegistry;
import com.rlutility.modules.LevelUpExploitHandler;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
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
    private static final int COL_SHADOW    = 0x66000000;
    private static final int COL_PANEL     = 0xF00C1017;
    private static final int COL_HEADER    = 0xFF131A24;
    private static final int COL_RAIL      = 0xFF0F141C;
    private static final int COL_ROW       = 0x14FFFFFF;
    private static final int COL_ROW_HOVER = 0x2AFFFFFF;
    private static final int COL_SEL       = 0x33FFC24B;
    private static final int COL_ACCENT    = 0xFFFFC24B;
    private static final int COL_TEXT      = 0xFFE6EDF3;
    private static final int COL_DIM       = 0xFF7D8894;
    private static final int COL_ON        = 0xFF4ADE80;
    private static final int COL_OFF       = 0xFF3A424C;
    private static final int COL_LINE      = 0xFF1E2733;

    // ----------------------------------------------------------------- layout
    private static final int PANEL_W = 470;
    private static final int PANEL_H = 280;
    private static final int HEADER_H = 30;
    private static final int FOOTER_H = 38;
    private static final int RAIL_W = 106;
    private static final int ROW_H = 20;
    private static final int ROW_GAP = 2;
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

    private abstract static class Row {
        String label;
        String desc;
        Row(String label, String desc) {
            this.label = label;
            this.desc = desc;
        }
        abstract void click(int button);
        void render(GuiUtilityMenu gui, int x, int y, int width) {}
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
            gui.drawText(feature.name, x + 8, y + 6, on ? COL_TEXT : COL_DIM);

            // toggle pill
            int px = x + width - 38;
            int py = y + 5;
            rect(px, py, px + 28, py + 10, on ? COL_ON : COL_OFF);
            int knobX = on ? px + 18 : px + 1;
            rect(knobX, py + 1, knobX + 9, py + 9, 0xFF0C1017);
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
            gui.drawText("\u00a77" + setting.name, x + 8, y + 6, COL_DIM);
            String value = setting.value();
            int w = gui.fontRenderer.getStringWidth(value) + 14;
            rect(x + width - w - 6, y + 3, x + width - 6, y + ROW_H - 3, 0x22FFC24B);
            gui.drawText(value, x + width - w - 6 + 7, y + 6, COL_ACCENT);
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
            gui.drawText(label, x + 8, y + 6, COL_TEXT);
            if (value != null) {
                int w = gui.fontRenderer.getStringWidth(value) + 14;
                rect(x + width - w - 6, y + 3, x + width - 6, y + ROW_H - 3, 0x22FFC24B);
                gui.drawText(value, x + width - w - 6 + 7, y + 6, COL_ACCENT);
            }
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

        for (Feature f : FeatureRegistry.byCategory(selectedCategory)) rows.add(new ToggleRow(f));
        for (FeatureRegistry.Setting s : FeatureRegistry.settingsFor(selectedCategory)) rows.add(new SettingRow(s));
        clampScroll();
    }

    private void buildToolsRows() {
        for (Feature f : FeatureRegistry.byCategory(Feature.Category.TOOLS)) rows.add(new ToggleRow(f));

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
                "save", FeatureConfig::saveConfig));
    }

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
        drawRect(x - 2, y - 2, x + PANEL_W + 2, y + PANEL_H + 2, COL_SHADOW);
        drawRect(x, y, x + PANEL_W, y + PANEL_H, COL_PANEL);
        drawRect(x, y, x + PANEL_W, y + HEADER_H, COL_HEADER);
        drawRect(x, y + HEADER_H - 1, x + PANEL_W, y + HEADER_H, COL_ACCENT);
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
        drawText("\u00a7lRLUtility", x + 10, y + 7, COL_ACCENT);
        drawText("v" + RLUtilityMod.VERSION + "  \u00b7  RLCraft 2.9.3", x + 10 + fontRenderer.getStringWidth("RLUtility") + 8, y + 7, COL_DIM);

        // search field
        int sx = x + PANEL_W - 168;
        int sy = y + 6;
        boolean hover = inside(mouseX, mouseY, sx, sy, 140, 18);
        drawRect(sx, sy, sx + 140, sy + 18, hover ? 0x33FFFFFF : 0x22FFFFFF);
        String shown = searchQuery.isEmpty() ? "\u00a78Search modules..." : searchQuery;
        drawText(shown, sx + 6, sy + 5, searchQuery.isEmpty() ? COL_DIM : COL_TEXT);
        if (!searchQuery.isEmpty() && (caretTimer / 6) % 2 == 0) {
            int cx = sx + 6 + fontRenderer.getStringWidth(searchQuery);
            drawRect(cx + 1, sy + 4, cx + 2, sy + 14, COL_ACCENT);
        }

        // close button
        int cxb = x + PANEL_W - 22;
        boolean hoverClose = inside(mouseX, mouseY, cxb, y + 6, 16, 18);
        drawRect(cxb, y + 6, cxb + 16, y + 24, hoverClose ? 0x44F87171 : 0x00000000);
        drawText("\u2715", cxb + 5, y + 11, hoverClose ? 0xFFF87171 : COL_DIM);
    }

    private void drawRail(int x, int y, int mouseX, int mouseY) {
        Feature.Category[] cats = Feature.Category.values();
        int ry = y + HEADER_H + 6;

        for (Feature.Category c : cats) {
            boolean selected = (c == selectedCategory) && searchQuery.isEmpty();
            boolean hover = inside(mouseX, mouseY, x + 4, ry, RAIL_W - 8, 22);

            if (selected) {
                drawRect(x + 4, ry, x + RAIL_W - 4, ry + 22, COL_SEL);
                drawRect(x + 4, ry, x + 6, ry + 22, COL_ACCENT);
            } else if (hover) {
                drawRect(x + 4, ry, x + RAIL_W - 4, ry + 22, COL_ROW_HOVER);
            }

            int enabled = 0;
            List<Feature> inCat = FeatureRegistry.byCategory(c);
            for (Feature f : inCat) if (f.isEnabled()) enabled++;

            drawText(c.title, x + 13, ry + 7, selected ? COL_ACCENT : COL_TEXT);
            if (!inCat.isEmpty()) {
                String count = enabled + "/" + inCat.size();
                drawText("\u00a78" + count, x + RAIL_W - 10 - fontRenderer.getStringWidth(count), ry + 7, COL_DIM);
            }
            ry += 24;
        }
    }

    private void drawContent(int x, int y, int mouseX, int mouseY) {
        int cx = x + RAIL_W + 5;
        int cw = PANEL_W - RAIL_W - 11;
        int top = y + HEADER_H + 5;
        int bottom = y + PANEL_H - FOOTER_H - 3;

        if (rows.isEmpty()) {
            drawText("\u00a78No modules match \"" + searchQuery + "\"", cx + 8, top + 10, COL_DIM);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            int ry = top + i * (ROW_H + ROW_GAP) - scroll;
            if (ry + ROW_H < top || ry > bottom) continue; // simple culling, no scissor needed

            Row row = rows.get(i);
            boolean hover = inside(mouseX, mouseY, cx, ry, cw, ROW_H)
                    && mouseY >= top && mouseY <= bottom;

            drawRect(cx, ry, cx + cw, ry + ROW_H, hover ? COL_ROW_HOVER : COL_ROW);
            row.render(this, cx, ry, cw);
            if (hover) hoveredDesc = row.desc;
        }

        // scrollbar
        int totalH = rows.size() * (ROW_H + ROW_GAP);
        int viewH = bottom - top;
        if (totalH > viewH) {
            int barH = Math.max(16, (int) ((float) viewH / totalH * viewH));
            int barY = top + (int) ((float) scroll / (totalH - viewH) * (viewH - barH));
            drawRect(x + PANEL_W - 5, top, x + PANEL_W - 3, bottom, 0x22FFFFFF);
            drawRect(x + PANEL_W - 5, barY, x + PANEL_W - 3, barY + barH, COL_ACCENT);
        }
    }

    private void drawFooter(int x, int y) {
        int fy = y + PANEL_H - FOOTER_H + 5;
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
        if (inside(mouseX, mouseY, x + PANEL_W - 22, y + 6, 16, 18)) {
            closeMenu();
            return;
        }

        // clear search by clicking the field with right mouse
        if (inside(mouseX, mouseY, x + PANEL_W - 168, y + 6, 140, 18) && mouseButton == 1) {
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

        // rows
        int cx = x + RAIL_W + 5;
        int cw = PANEL_W - RAIL_W - 11;
        int top = y + HEADER_H + 5;
        int bottom = y + PANEL_H - FOOTER_H - 3;
        if (mouseY < top || mouseY > bottom) return;

        for (int i = 0; i < rows.size(); i++) {
            int rowY = top + i * (ROW_H + ROW_GAP) - scroll;
            if (rowY + ROW_H < top || rowY > bottom) continue;
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
