package com.rlutility.gui;

import com.rlutility.modules.FeatureConfig;
import com.rlutility.modules.LevelUpExploitHandler;
import levelup2.api.IPlayerSkill;
import levelup2.skills.SkillRegistry;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-skill editor for the Level Up! 2 exploit.
 *
 * <p>Shows your <em>real</em> current level next to the level that will be sent, so it is obvious
 * what a "send" is actually going to change. Nothing is transmitted until you press Send.</p>
 */
public class GuiLevelUpConfig extends GuiScreen {

    private static final int PANEL_W = 440;
    private static final int PANEL_H = 272;
    private static final int MAX_VISIBLE = 6;
    private static final int ROW_H = 24;

    private final GuiScreen parent;
    private int selectedTree = 0; // 0: All, 1: Mining, 2: Crafting, 3: Combat
    private int scrollOffset = 0;
    private final List<SkillEntry> currentEntries = new ArrayList<>();

    public static class SkillEntry {
        public final String skillName;
        public final String displayName;
        public final byte skillType; // 0: Mining, 1: Crafting, 2: Combat
        public final int defaultMaxLevel;

        public SkillEntry(String skillName, String displayName, byte skillType, int defaultMaxLevel) {
            this.skillName = skillName;
            this.displayName = displayName;
            this.skillType = skillType;
            this.defaultMaxLevel = defaultMaxLevel;
        }
    }

    public GuiLevelUpConfig(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        super.initGui();
        rebuildSkillsList();
        rebuildButtons();
    }

    private void rebuildSkillsList() {
        currentEntries.clear();
        if (!Loader.isModLoaded("levelup2")) return;

        try {
            List<IPlayerSkill> skills = SkillRegistry.getSkillRegistry();
            if (skills == null) return;
            for (IPlayerSkill skill : skills) {
                if (skill == null || skill.getSkillName() == null) continue;
                byte type = skill.getSkillType();
                if (selectedTree != 0 && (selectedTree - 1) != type) continue;

                String rawName = skill.getSkillName();
                String transKey = rawName + ".name";
                String name = I18n.hasKey(transKey) ? I18n.format(transKey) : rawName.replace("levelup:", "");
                currentEntries.add(new SkillEntry(rawName, name, type, skill.getMaxLevel()));
            }
        } catch (Throwable ignored) {}
    }

    private void rebuildButtons() {
        this.buttonList.clear();

        int startX = (this.width - PANEL_W) / 2;
        int startY = (this.height - PANEL_H) / 2;

        // Tree filter tabs
        String[] treeNames = {"All Skills", "Mining", "Crafting", "Combat"};
        int tabW = 80;
        for (int i = 0; i < treeNames.length; i++) {
            String title = (selectedTree == i)
                    ? TextFormatting.GOLD + "" + TextFormatting.BOLD + treeNames[i]
                    : TextFormatting.GRAY + treeNames[i];
            GuiButton b = new GuiButton(10 + i, startX + 10 + i * (tabW + 4), startY + 28, tabW, 18, title);
            b.enabled = (selectedTree != i);
            this.buttonList.add(b);
        }
        this.buttonList.add(new GuiButton(50, startX + 350, startY + 28, 80, 18, TextFormatting.GREEN + "Max Tab"));

        int listStartY = startY + 52;
        for (int i = 0; i < MAX_VISIBLE; i++) {
            int entryIndex = scrollOffset + i;
            if (entryIndex >= currentEntries.size()) break;

            SkillEntry entry = currentEntries.get(entryIndex);
            int rowY = listStartY + i * ROW_H;
            int planned = LevelUpExploitHandler.getPlannedLevel(entry.skillName);
            int current = LevelUpExploitHandler.getCurrentLevel(entry.skillName);

            TextFormatting levelColor = planned == current
                    ? TextFormatting.GRAY
                    : (planned > current ? TextFormatting.GREEN : TextFormatting.RED);

            int btnBase = 1000 + entryIndex * 10;
            // [-10] [-1] [planned level / click to revert] [+1] [+10] [Cap] [0]
            this.buttonList.add(new GuiButton(btnBase, startX + 175, rowY, 26, 18, TextFormatting.RED + "-10"));
            this.buttonList.add(new GuiButton(btnBase + 1, startX + 203, rowY, 22, 18, TextFormatting.RED + "-1"));
            this.buttonList.add(new GuiButton(btnBase + 2, startX + 227, rowY, 44, 18, levelColor + "" + planned));
            this.buttonList.add(new GuiButton(btnBase + 3, startX + 273, rowY, 22, 18, TextFormatting.GREEN + "+1"));
            this.buttonList.add(new GuiButton(btnBase + 4, startX + 297, rowY, 26, 18, TextFormatting.GREEN + "+10"));
            this.buttonList.add(new GuiButton(btnBase + 5, startX + 325, rowY, 48, 18, TextFormatting.YELLOW + "Cap " + entry.defaultMaxLevel));
            this.buttonList.add(new GuiButton(btnBase + 6, startX + 375, rowY, 32, 18, TextFormatting.GRAY + "0"));
        }

        if (scrollOffset > 0) {
            this.buttonList.add(new GuiButton(60, startX + 412, listStartY, 20, 18, "^"));
        }
        if (scrollOffset + MAX_VISIBLE < currentEntries.size()) {
            this.buttonList.add(new GuiButton(61, startX + 412, listStartY + (MAX_VISIBLE - 1) * ROW_H, 20, 18, "v"));
        }

        // Exploit options row
        int optY = startY + 52 + MAX_VISIBLE * ROW_H + 6;
        byte spec = LevelUpExploitHandler.currentSpecialization();
        this.buttonList.add(new GuiButton(74, startX + 10, optY, 118, 20,
                TextFormatting.AQUA + "Class: " + TextFormatting.WHITE + LevelUpExploitHandler.className(spec)));
        this.buttonList.add(new GuiButton(75, startX + 133, optY, 132, 20,
                "Keep Class " + onOff(FeatureConfig.levelUpPreserveClass)));
        this.buttonList.add(new GuiButton(76, startX + 270, optY, 100, 20,
                "Cap " + onOff(FeatureConfig.levelUpClampToMax)));
        this.buttonList.add(new GuiButton(77, startX + 375, optY, 55, 20, TextFormatting.GRAY + "Revert"));

        // Bottom actions
        int bottomY = startY + PANEL_H - 24;
        this.buttonList.add(new GuiButton(70, startX + 10, bottomY, 110, 20, TextFormatting.AQUA + "Safe (No Sprint)"));
        this.buttonList.add(new GuiButton(71, startX + 125, bottomY, 80, 20, TextFormatting.YELLOW + "Reset All 0"));
        this.buttonList.add(new GuiButton(72, startX + 210, bottomY, 140, 20,
                TextFormatting.LIGHT_PURPLE + "" + TextFormatting.BOLD + "Send Exploit (0-XP)"));
        this.buttonList.add(new GuiButton(73, startX + 355, bottomY, 75, 20, TextFormatting.WHITE + "Back"));
    }

    private static String onOff(boolean value) {
        return value ? TextFormatting.GREEN + "ON" : TextFormatting.RED + "OFF";
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= 10 && button.id <= 13) {
            selectedTree = button.id - 10;
            scrollOffset = 0;
            rebuildSkillsList();
            rebuildButtons();
            return;
        }

        switch (button.id) {
            case 50:
                for (SkillEntry entry : currentEntries) {
                    LevelUpExploitHandler.setSkillLevel(entry.skillName, entry.defaultMaxLevel);
                }
                rebuildButtons();
                return;
            case 60:
                scrollOffset = Math.max(0, scrollOffset - 1);
                rebuildButtons();
                return;
            case 61:
                scrollOffset = Math.min(Math.max(0, currentEntries.size() - MAX_VISIBLE), scrollOffset + 1);
                rebuildButtons();
                return;
            case 70:
                LevelUpExploitHandler.applySafePreset();
                rebuildButtons();
                return;
            case 71:
                LevelUpExploitHandler.resetAllSkillsToZero();
                rebuildButtons();
                return;
            case 72:
                LevelUpExploitHandler.sendConfiguredSkillsPacket();
                return;
            case 73:
                this.mc.displayGuiScreen(parent);
                return;
            case 74:
                LevelUpExploitHandler.cycleClass();
                rebuildButtons();
                return;
            case 75:
                FeatureConfig.levelUpPreserveClass = !FeatureConfig.levelUpPreserveClass;
                FeatureConfig.saveConfig();
                rebuildButtons();
                return;
            case 76:
                FeatureConfig.levelUpClampToMax = !FeatureConfig.levelUpClampToMax;
                FeatureConfig.saveConfig();
                rebuildButtons();
                return;
            case 77:
                LevelUpExploitHandler.clearPlanned();
                rebuildButtons();
                return;
            default:
                break;
        }

        if (button.id >= 1000) {
            int entryIndex = (button.id - 1000) / 10;
            int subId = (button.id - 1000) % 10;
            if (entryIndex >= currentEntries.size()) return;

            SkillEntry entry = currentEntries.get(entryIndex);
            int cur = LevelUpExploitHandler.getPlannedLevel(entry.skillName);

            switch (subId) {
                case 0: cur = Math.max(0, cur - 10); break;
                case 1: cur = Math.max(0, cur - 1); break;
                case 2: cur = LevelUpExploitHandler.getCurrentLevel(entry.skillName); break; // revert this row
                case 3: cur = cur + 1; break;
                case 4: cur = cur + 10; break;
                case 5: cur = entry.defaultMaxLevel; break;
                case 6: cur = 0; break;
                default: return;
            }

            LevelUpExploitHandler.setSkillLevel(entry.skillName, cur);
            rebuildButtons();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (dWheel == 0) return;
        if (dWheel > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else {
            scrollOffset = Math.min(Math.max(0, currentEntries.size() - MAX_VISIBLE), scrollOffset + 1);
        }
        rebuildButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int startX = (this.width - PANEL_W) / 2;
        int startY = (this.height - PANEL_H) / 2;

        drawDefaultBackground();
        drawRect(startX, startY, startX + PANEL_W, startY + PANEL_H, 0xD012161E);
        drawRect(startX + 2, startY + 2, startX + PANEL_W - 2, startY + 24, 0xEE1E2430);

        String title = TextFormatting.GOLD + "" + TextFormatting.BOLD + "Level Up! 2 Skill Editor"
                + TextFormatting.DARK_GRAY + " | " + TextFormatting.WHITE + "0-XP skill packet";
        drawCenteredString(this.fontRenderer, title, this.width / 2, startY + 8, 0xFFFFFF);

        int listStartY = startY + 52;
        for (int i = 0; i < MAX_VISIBLE; i++) {
            int entryIndex = scrollOffset + i;
            if (entryIndex >= currentEntries.size()) break;
            SkillEntry entry = currentEntries.get(entryIndex);
            int rowY = listStartY + i * ROW_H + 5;

            String typePrefix = entry.skillType == 0 ? TextFormatting.BLUE + "[M] "
                    : entry.skillType == 1 ? TextFormatting.GREEN + "[C] "
                    : TextFormatting.RED + "[F] ";

            String label = typePrefix + TextFormatting.WHITE + entry.displayName;
            if (this.fontRenderer.getStringWidth(label) > 130) {
                label = this.fontRenderer.trimStringToWidth(label, 122) + "...";
            }
            this.fontRenderer.drawStringWithShadow(label, startX + 10, rowY, 0xFFFFFF);

            // Real, server-side level for this skill.
            String now = TextFormatting.DARK_GRAY + "now " + LevelUpExploitHandler.getCurrentLevel(entry.skillName);
            this.fontRenderer.drawStringWithShadow(now,
                    startX + 172 - this.fontRenderer.getStringWidth(now) - 4, rowY, 0xFFFFFF);
        }

        if (currentEntries.isEmpty()) {
            drawCenteredString(this.fontRenderer,
                    TextFormatting.GRAY + "Level Up! 2 is not loaded, or its skill registry is empty.",
                    this.width / 2, listStartY + 30, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
