package com.rlutility.gui;

import com.rlutility.modules.FeatureConfig;
import com.rlutility.modules.LevelUpExploitHandler;
import levelup2.api.IPlayerSkill;
import levelup2.skills.SkillRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;

import java.io.IOException;
import java.util.*;

public class GuiLevelUpConfig extends GuiScreen {

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
            if (skills != null) {
                for (IPlayerSkill skill : skills) {
                    if (skill == null || skill.getSkillName() == null) continue;
                    byte type = skill.getSkillType();
                    if (selectedTree != 0 && (selectedTree - 1) != type) {
                        continue;
                    }
                    String rawName = skill.getSkillName();
                    String transKey = rawName + ".name";
                    String name = I18n.hasKey(transKey) ? I18n.format(transKey) : rawName.replace("levelup:", "");
                    currentEntries.add(new SkillEntry(rawName, name, type, skill.getMaxLevel()));
                }
            }
        } catch (Throwable ignored) {}
    }

    private void rebuildButtons() {
        this.buttonList.clear();

        int panelWidth = 440;
        int panelHeight = 250;
        int startX = (this.width - panelWidth) / 2;
        int startY = (this.height - panelHeight) / 2;

        // Tree filter tabs
        String[] treeNames = {"All Skills", "Mining", "Crafting", "Combat"};
        int tabW = 80;
        for (int i = 0; i < treeNames.length; i++) {
            String title = (selectedTree == i) ? TextFormatting.GOLD + "" + TextFormatting.BOLD + treeNames[i] : TextFormatting.GRAY + treeNames[i];
            GuiButton b = new GuiButton(10 + i, startX + 10 + i * (tabW + 4), startY + 28, tabW, 18, title);
            b.enabled = (selectedTree != i);
            this.buttonList.add(b);
        }

        // Tree Batch Actions: Max Selected, Reset 0, Apply All
        this.buttonList.add(new GuiButton(50, startX + 350, startY + 28, 80, 18, TextFormatting.GREEN + "Max Tab"));

        // Skill list rows (Max 6 rows visible at a time)
        int maxVisible = 6;
        int rowH = 24;
        int listStartY = startY + 52;

        for (int i = 0; i < maxVisible; i++) {
            int entryIndex = scrollOffset + i;
            if (entryIndex >= currentEntries.size()) break;

            SkillEntry entry = currentEntries.get(entryIndex);
            int rowY = listStartY + i * rowH;
            int curLevel = LevelUpExploitHandler.getSkillLevel(entry.skillName, entry.defaultMaxLevel);

            int btnBase = 1000 + entryIndex * 10;
            // [-10] [-1] [Level Display] [+1] [+10] [Max] [Zero]
            this.buttonList.add(new GuiButton(btnBase, startX + 175, rowY, 26, 18, TextFormatting.RED + "-10"));
            this.buttonList.add(new GuiButton(btnBase + 1, startX + 203, rowY, 22, 18, TextFormatting.RED + "-1"));
            this.buttonList.add(new GuiButton(btnBase + 2, startX + 227, rowY, 44, 18, TextFormatting.AQUA + "" + curLevel));
            this.buttonList.add(new GuiButton(btnBase + 3, startX + 273, rowY, 22, 18, TextFormatting.GREEN + "+1"));
            this.buttonList.add(new GuiButton(btnBase + 4, startX + 297, rowY, 26, 18, TextFormatting.GREEN + "+10"));
            this.buttonList.add(new GuiButton(btnBase + 5, startX + 325, rowY, 48, 18, TextFormatting.YELLOW + "Cap (" + entry.defaultMaxLevel + ")"));
            this.buttonList.add(new GuiButton(btnBase + 6, startX + 375, rowY, 32, 18, TextFormatting.GRAY + "0"));
        }

        // Scroll Up / Down buttons if skills exceed visible
        if (scrollOffset > 0) {
            this.buttonList.add(new GuiButton(60, startX + 412, listStartY, 20, 18, "^"));
        }
        if (scrollOffset + maxVisible < currentEntries.size()) {
            this.buttonList.add(new GuiButton(61, startX + 412, listStartY + (maxVisible - 1) * rowH, 20, 18, "v"));
        }

        // Bottom Actions: [Safe Preset (Sprint=0)] [Max All] [Send to Server (0-XP)] [Back]
        int bottomY = startY + panelHeight - 24;
        this.buttonList.add(new GuiButton(70, startX + 10, bottomY, 110, 20, TextFormatting.AQUA + "Safe (No Sprint)"));
        this.buttonList.add(new GuiButton(71, startX + 125, bottomY, 80, 20, TextFormatting.YELLOW + "Reset All 0"));
        this.buttonList.add(new GuiButton(72, startX + 210, bottomY, 140, 20, TextFormatting.LIGHT_PURPLE + "" + TextFormatting.BOLD + "Send Exploit (0-XP)"));
        this.buttonList.add(new GuiButton(73, startX + 355, bottomY, 75, 20, TextFormatting.WHITE + "Back"));
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

        if (button.id == 50) {
            for (SkillEntry entry : currentEntries) {
                LevelUpExploitHandler.setSkillLevel(entry.skillName, entry.defaultMaxLevel);
            }
            rebuildButtons();
            return;
        }

        if (button.id == 60) {
            scrollOffset = Math.max(0, scrollOffset - 1);
            rebuildButtons();
            return;
        }
        if (button.id == 61) {
            scrollOffset = Math.min(Math.max(0, currentEntries.size() - 6), scrollOffset + 1);
            rebuildButtons();
            return;
        }

        if (button.id == 70) {
            LevelUpExploitHandler.applySafePreset();
            rebuildButtons();
            return;
        }

        if (button.id == 71) {
            LevelUpExploitHandler.resetAllSkillsToZero();
            rebuildButtons();
            return;
        }

        if (button.id == 72) {
            LevelUpExploitHandler.sendConfiguredSkillsPacket();
            return;
        }

        if (button.id == 73) {
            this.mc.displayGuiScreen(parent);
            return;
        }

        if (button.id >= 1000) {
            int entryIndex = (button.id - 1000) / 10;
            int subId = (button.id - 1000) % 10;
            if (entryIndex < currentEntries.size()) {
                SkillEntry entry = currentEntries.get(entryIndex);
                int cur = LevelUpExploitHandler.getSkillLevel(entry.skillName, entry.defaultMaxLevel);
                if (subId == 0) cur = Math.max(0, cur - 10);
                else if (subId == 1) cur = Math.max(0, cur - 1);
                else if (subId == 2) {
                    if (cur == 0) cur = entry.defaultMaxLevel;
                    else if (cur == entry.defaultMaxLevel) cur = 20;
                    else if (cur == 20) cur = 50;
                    else if (cur == 50) cur = 100;
                    else if (cur == 100) cur = 255;
                    else cur = 0;
                }
                else if (subId == 3) cur = Math.min(1000, cur + 1);
                else if (subId == 4) cur = Math.min(1000, cur + 10);
                else if (subId == 5) cur = entry.defaultMaxLevel;
                else if (subId == 6) cur = 0;

                LevelUpExploitHandler.setSkillLevel(entry.skillName, cur);
                rebuildButtons();
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (dWheel != 0) {
            if (dWheel > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else {
                scrollOffset = Math.min(Math.max(0, currentEntries.size() - 6), scrollOffset + 1);
            }
            rebuildButtons();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int panelWidth = 440;
        int panelHeight = 250;
        int startX = (this.width - panelWidth) / 2;
        int startY = (this.height - panelHeight) / 2;

        drawDefaultBackground();
        drawRect(startX, startY, startX + panelWidth, startY + panelHeight, 0xD012161E);
        drawRect(startX + 2, startY + 2, startX + panelWidth - 2, startY + 24, 0xEE1E2430);

        String title = TextFormatting.GOLD + "" + TextFormatting.BOLD + "Level Up! 2 Skill Customizer" + TextFormatting.DARK_GRAY + " | " + TextFormatting.WHITE + "Granular Exploits";
        drawCenteredString(this.fontRenderer, title, this.width / 2, startY + 8, 0xFFFFFF);

        int maxVisible = 6;
        int rowH = 24;
        int listStartY = startY + 52;
        for (int i = 0; i < maxVisible; i++) {
            int entryIndex = scrollOffset + i;
            if (entryIndex >= currentEntries.size()) break;
            SkillEntry entry = currentEntries.get(entryIndex);
            int rowY = listStartY + i * rowH + 5;

            String typePrefix = entry.skillType == 0 ? TextFormatting.BLUE + "[Mining] " :
                    entry.skillType == 1 ? TextFormatting.GREEN + "[Crafting] " : TextFormatting.RED + "[Combat] ";

            String label = typePrefix + TextFormatting.WHITE + entry.displayName;
            if (this.fontRenderer.getStringWidth(label) > 165) {
                label = this.fontRenderer.trimStringToWidth(label, 155) + "...";
            }
            this.fontRenderer.drawStringWithShadow(label, startX + 10, rowY, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}