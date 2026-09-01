package com.rlutility.gui;

import com.rlutility.modules.AutoReforgerHandler;
import com.rlutility.modules.FeatureConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;

public class GuiUtilityMenu extends GuiScreen {

    private enum Tab {
        COMBAT("Combat & Defense"),
        MOVEMENT("Movement"),
        EXPLOITS("Mod Exploits"),
        VISUALS("Visuals & ESP");

        final String title;
        Tab(String title) {
            this.title = title;
        }
    }

    private Tab currentTab = Tab.COMBAT;
    private final List<FeatureToggle> toggles = new ArrayList<>();

    private static class FeatureToggle {
        final String name;
        final String desc;
        final java.util.function.Supplier<Boolean> getter;
        final java.util.function.Consumer<Boolean> setter;

        FeatureToggle(String name, String desc, java.util.function.Supplier<Boolean> getter, java.util.function.Consumer<Boolean> setter) {
            this.name = name;
            this.desc = desc;
            this.getter = getter;
            this.setter = setter;
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        rebuildAllButtons();
    }

    private void rebuildAllButtons() {
        this.buttonList.clear();
        toggles.clear();

        int panelWidth = 400;
        int panelHeight = 230;
        int startX = (this.width - panelWidth) / 2;
        int startY = (this.height - panelHeight) / 2;

        // 1. Build Tab Navigation Buttons (Top Header)
        int tabCount = Tab.values().length;
        int tabWidth = (panelWidth - 20) / tabCount;
        for (int i = 0; i < tabCount; i++) {
            Tab t = Tab.values()[i];
            String tabText = (t == currentTab)
                    ? TextFormatting.GOLD + "" + TextFormatting.BOLD + t.title
                    : TextFormatting.GRAY + t.title;
            GuiButton tabBtn = new GuiButton(100 + i, startX + 10 + i * tabWidth, startY + 30, tabWidth - 4, 20, tabText);
            tabBtn.enabled = (t != currentTab);
            this.buttonList.add(tabBtn);
        }

        // 2. Populate Feature Toggles according to currentTab
        if (currentTab == Tab.COMBAT) {
            toggles.add(new FeatureToggle("Auto Criticals", "Spoofs fall-packet on hit for 100% crit multiplier", () -> FeatureConfig.autoCriticals, v -> FeatureConfig.autoCriticals = v));
            toggles.add(new FeatureToggle("Level Damage Bypass", "Bypasses Reskillable attack lock on weapons", () -> FeatureConfig.levelDamageBypass, v -> FeatureConfig.levelDamageBypass = v));
            toggles.add(new FeatureToggle("Nunchaku Triggerbot", "Continuous spinning DPS packet shredder", () -> FeatureConfig.autoTriggerbot, v -> FeatureConfig.autoTriggerbot = v));
            toggles.add(new FeatureToggle("FirstAid Auto-Triage", "0-tick background body/head healing", () -> FeatureConfig.firstAidAutoHeal, v -> FeatureConfig.firstAidAutoHeal = v));
            toggles.add(new FeatureToggle("Fast Triage", "Accelerate healing ticks on limbs", () -> FeatureConfig.fastTriage, v -> FeatureConfig.fastTriage = v));
        } else if (currentTab == Tab.MOVEMENT) {
            toggles.add(new FeatureToggle("No Fall Damage", "Cancels downward momentum impact packets", () -> FeatureConfig.noFall, v -> FeatureConfig.noFall = v));
            toggles.add(new FeatureToggle("Step Assist & Speed", "Step up full blocks smoothly", () -> FeatureConfig.stepSpeed, v -> FeatureConfig.stepSpeed = v));
            toggles.add(new FeatureToggle("Creative Flight", "Enables flight capabilities", () -> FeatureConfig.creativeFly, v -> FeatureConfig.creativeFly = v));
            toggles.add(new FeatureToggle("No Slowdown", "Bypass cobweb, drawing and eating slowdown", () -> FeatureConfig.noSlowdown, v -> FeatureConfig.noSlowdown = v));
        } else if (currentTab == Tab.EXPLOITS) {
            toggles.add(new FeatureToggle("Fast Mine / Break", "Restores speed & eliminates block hit delay", () -> FeatureConfig.fastMine, v -> FeatureConfig.fastMine = v));
            toggles.add(new FeatureToggle("Auto Reforger", "Auto-rolls QualityTools & Bountiful Baubles", () -> FeatureConfig.autoReforge, v -> FeatureConfig.autoReforge = v));
            toggles.add(new FeatureToggle("Auto Lockpick", "Instantly solves Locks tumbler puzzles", () -> FeatureConfig.autoLockpick, v -> FeatureConfig.autoLockpick = v));
            toggles.add(new FeatureToggle("Item Vacuum", "Remote siphon loot packets through walls", () -> FeatureConfig.clientItemVacuum, v -> FeatureConfig.clientItemVacuum = v));
            toggles.add(new FeatureToggle("Debuff Neutralizer", "Auto-clears potion and dizzy shake debuffs", () -> FeatureConfig.clientDebuffNeutralizer, v -> FeatureConfig.clientDebuffNeutralizer = v));
            toggles.add(new FeatureToggle("Reskillable Bypass", "Bypasses client skill requirement lockouts", () -> FeatureConfig.reskillableBypass, v -> FeatureConfig.reskillableBypass = v));
            toggles.add(new FeatureToggle("Auto Hydrate", "Auto-drinks water without opening inventory", () -> FeatureConfig.simpleDifficultyAutoHydrate, v -> FeatureConfig.simpleDifficultyAutoHydrate = v));
        } else if (currentTab == Tab.VISUALS) {
            toggles.add(new FeatureToggle("Chest ESP", "Highlights normal and Ender chests through walls", () -> FeatureConfig.espChests, v -> FeatureConfig.espChests = v));
            toggles.add(new FeatureToggle("Mob Spawner ESP", "Highlights dungeon and battle tower spawners", () -> FeatureConfig.espSpawners, v -> FeatureConfig.espSpawners = v));
            toggles.add(new FeatureToggle("Waystone ESP", "Highlights discovered and wild waystones", () -> FeatureConfig.espWaystones, v -> FeatureConfig.espWaystones = v));
            toggles.add(new FeatureToggle("Dragon & Boss ESP", "Highlights underground dragon dens & sea serpents", () -> FeatureConfig.espDragons, v -> FeatureConfig.espDragons = v));
        }

        // 3. Build Two-Column Grid for Toggles with Fixed width 185px <= 200px
        int btnStartY = startY + 56;
        int btnWidth = 185;
        int btnHeight = 20;
        int colGap = 10;
        int rowGap = 24;

        for (int i = 0; i < toggles.size(); i++) {
            FeatureToggle toggle = toggles.get(i);
            int col = i % 2;
            int row = i / 2;
            int x = startX + 10 + col * (btnWidth + colGap);
            int y = btnStartY + row * rowGap;
            String status = toggle.getter.get() ? TextFormatting.GREEN + "[ON]" : TextFormatting.RED + "[OFF]";
            this.buttonList.add(new GuiButton(i, x, y, btnWidth, btnHeight, toggle.name + " " + status));
        }

        // Special Config Buttons for Auto Reforger Quality Target & LevelUp 2 Exploit (Exploits Tab)
        if (currentTab == Tab.EXPLOITS) {
            int extraRowY = btnStartY + ((toggles.size() + 1) / 2) * rowGap;
            this.buttonList.add(new GuiButton(300, startX + 10, extraRowY, 185, 20, TextFormatting.YELLOW + "Reforge: " + TextFormatting.WHITE + FeatureConfig.targetQuality));
            this.buttonList.add(new GuiButton(310, startX + 205, extraRowY, 185, 20, TextFormatting.GOLD + "" + TextFormatting.BOLD + "Skill Tree Editor..."));
            
            // LevelUp 2 Custom Level Controls: [-] [Level: X] [+] [Apply]
            int lvlY = extraRowY + rowGap;
            this.buttonList.add(new GuiButton(302, startX + 10, lvlY, 28, 20, TextFormatting.RED + "-5"));
            this.buttonList.add(new GuiButton(303, startX + 40, lvlY, 28, 20, TextFormatting.RED + "-1"));
            this.buttonList.add(new GuiButton(304, startX + 70, lvlY, 65, 20, TextFormatting.AQUA + "Lvl: " + TextFormatting.WHITE + FeatureConfig.customLevelTarget));
            this.buttonList.add(new GuiButton(305, startX + 137, lvlY, 28, 20, TextFormatting.GREEN + "+1"));
            this.buttonList.add(new GuiButton(306, startX + 167, lvlY, 28, 20, TextFormatting.GREEN + "+5"));

            this.buttonList.add(new GuiButton(301, startX + 205, lvlY, 185, 20, TextFormatting.LIGHT_PURPLE + "" + TextFormatting.BOLD + "Apply Level [" + FeatureConfig.customLevelTarget + "] (0-XP)"));
        }

        // 4. Close Button at bottom
        this.buttonList.add(new GuiButton(200, startX + (panelWidth - 120) / 2, startY + panelHeight - 24, 120, 20, TextFormatting.WHITE + "Close Menu"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= 100 && button.id < 100 + Tab.values().length) {
            int tabIndex = button.id - 100;
            currentTab = Tab.values()[tabIndex];
            rebuildAllButtons();
        } else if (button.id == 200) {
            this.mc.displayGuiScreen(null);
            if (this.mc.currentScreen == null) {
                this.mc.setIngameFocus();
            }
        } else if (button.id == 300) {
            // Cycle through Quality Presets
            String[] presets = AutoReforgerHandler.QUALITY_PRESETS;
            int nextIdx = 0;
            for (int i = 0; i < presets.length; i++) {
                if (presets[i].equals(FeatureConfig.targetQuality)) {
                    nextIdx = (i + 1) % presets.length;
                    break;
                }
            }
            FeatureConfig.targetQuality = presets[nextIdx];
            FeatureConfig.saveConfig();
            button.displayString = TextFormatting.YELLOW + "Reforge: " + TextFormatting.WHITE + FeatureConfig.targetQuality;
        } else if (button.id == 310) {
            this.mc.displayGuiScreen(new GuiLevelUpConfig(this));
            return;
        } else if (button.id == 301) {
            com.rlutility.modules.LevelUpExploitHandler.setAllSkillsLevel(FeatureConfig.customLevelTarget);
        } else if (button.id == 302) {
            FeatureConfig.customLevelTarget = Math.max(0, FeatureConfig.customLevelTarget - 5);
            FeatureConfig.saveConfig();
            rebuildAllButtons();
        } else if (button.id == 303) {
            FeatureConfig.customLevelTarget = Math.max(0, FeatureConfig.customLevelTarget - 1);
            FeatureConfig.saveConfig();
            rebuildAllButtons();
        } else if (button.id == 304) {
            // Preset cycle: 5 -> 10 -> 25 -> 50 -> 100 -> 255 -> 0
            int cur = FeatureConfig.customLevelTarget;
            if (cur < 5) FeatureConfig.customLevelTarget = 5;
            else if (cur < 10) FeatureConfig.customLevelTarget = 10;
            else if (cur < 25) FeatureConfig.customLevelTarget = 25;
            else if (cur < 50) FeatureConfig.customLevelTarget = 50;
            else if (cur < 100) FeatureConfig.customLevelTarget = 100;
            else if (cur < 255) FeatureConfig.customLevelTarget = 255;
            else FeatureConfig.customLevelTarget = 10;
            FeatureConfig.saveConfig();
            rebuildAllButtons();
        } else if (button.id == 305) {
            FeatureConfig.customLevelTarget = Math.min(1000, FeatureConfig.customLevelTarget + 1);
            FeatureConfig.saveConfig();
            rebuildAllButtons();
        } else if (button.id == 306) {
            FeatureConfig.customLevelTarget = Math.min(1000, FeatureConfig.customLevelTarget + 5);
            FeatureConfig.saveConfig();
            rebuildAllButtons();
        } else if (button.id >= 0 && button.id < toggles.size()) {
            FeatureToggle toggle = toggles.get(button.id);
            toggle.setter.accept(!toggle.getter.get());
            FeatureConfig.saveConfig();
            String status = toggle.getter.get() ? TextFormatting.GREEN + "[ON]" : TextFormatting.RED + "[OFF]";
            button.displayString = toggle.name + " " + status;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int panelWidth = 400;
        int panelHeight = 230;
        int startX = (this.width - panelWidth) / 2;
        int startY = (this.height - panelHeight) / 2;

        // Dark translucent glass panel background centered
        drawDefaultBackground();
        drawRect(startX, startY, startX + panelWidth, startY + panelHeight, 0xD012161E);
        drawRect(startX + 2, startY + 2, startX + panelWidth - 2, startY + 24, 0xEE1E2430);

        // Header Title
        String title = TextFormatting.GOLD + "" + TextFormatting.BOLD + "RLUtility v1.0.0 " + TextFormatting.DARK_GRAY + "| " + TextFormatting.WHITE + "RLCraft 2.9.3 Exploits Hub";
        drawCenteredString(this.fontRenderer, title, this.width / 2, startY + 8, 0xFFFFFF);

        // Draw standard GUI buttons
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Tooltip rendering for hovered buttons
        for (GuiButton b : this.buttonList) {
            if (b.id >= 0 && b.id < toggles.size() && b.isMouseOver()) {
                FeatureToggle toggle = toggles.get(b.id);
                drawHoveringText(toggle.desc, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
