package com.rlutility.command;

import com.rlutility.gui.GuiUtilityMenu;
import com.rlutility.modules.FeatureConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

public class CommandRLUtility extends CommandBase {

    @Override
    public String getName() {
        return "rlu";
    }

    @Override
    public List<String> getAliases() {
        List<String> aliases = new ArrayList<String>();
        aliases.add("rlmenu");
        aliases.add("rlgui");
        return aliases;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/rlu | level <n> | tree <t> <n> | safe | max | class <t> | revert | xray [block|here] | unlock | buy <skill> [n]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        Minecraft mc = Minecraft.getMinecraft();
        if (args != null && args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("level") || sub.equals("lvl") || sub.equals("setlevel")) {
                int targetLvl = 10;
                if (args.length > 1) {
                    try {
                        targetLvl = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        targetLvl = 10;
                    }
                }
                final int lvl = targetLvl;
                mc.addScheduledTask(() -> {
                    com.rlutility.modules.FeatureConfig.customLevelTarget = lvl;
                    com.rlutility.modules.FeatureConfig.saveConfig();
                    com.rlutility.modules.LevelUpExploitHandler.setAllSkillsLevel(lvl);
                });
                return;
            } else if (sub.equals("tree") || sub.equals("settree")) {
                byte treeType = 0; // 0: Mining, 1: Crafting, 2: Combat
                if (args.length > 1) {
                    String tName = args[1].toLowerCase();
                    if (tName.startsWith("craft")) treeType = 1;
                    else if (tName.startsWith("comb")) treeType = 2;
                    else treeType = 0;
                }
                int targetLvl = 10;
                if (args.length > 2) {
                    try {
                        targetLvl = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        targetLvl = 10;
                    }
                }
                final byte t = treeType;
                final int lvl = targetLvl;
                mc.addScheduledTask(() -> {
                    com.rlutility.modules.LevelUpExploitHandler.setTreeLevel(t, lvl);
                });
                return;
            } else if (sub.equals("safe") || sub.equals("nosprint")) {
                mc.addScheduledTask(() -> {
                    com.rlutility.modules.LevelUpExploitHandler.applySafePreset();
                });
                return;
            } else if (sub.equals("custom") || sub.equals("skills")) {
                mc.addScheduledTask(() -> {
                    mc.displayGuiScreen(new com.rlutility.gui.GuiLevelUpConfig(null));
                });
                return;
            } else if (sub.equals("max") || sub.equals("maxlevel")) {
                // 0 means "each skill's own cap" rather than a flat 10.
                mc.addScheduledTask(com.rlutility.modules.LevelUpExploitHandler::maxAllSkills);
                return;
            } else if (sub.equals("class") || sub.equals("spec") || sub.equals("respec")) {
                byte spec = -1;
                if (args.length > 1) {
                    String cName = args[1].toLowerCase();
                    if (cName.startsWith("craft")) spec = 1;
                    else if (cName.startsWith("comb") || cName.startsWith("fight")) spec = 2;
                    else if (cName.startsWith("min")) spec = 0;
                }
                final byte target = spec;
                mc.addScheduledTask(() -> {
                    if (target < 0) com.rlutility.modules.LevelUpExploitHandler.cycleClass();
                    else com.rlutility.modules.LevelUpExploitHandler.changeClass(target);
                });
                return;
            } else if (sub.equals("xray")) {
                mc.addScheduledTask(() -> {
                    if (args.length > 1) {
                        // /rlu xray <blockid|here>  - "here" grabs whatever you are looking at
                        String id = args[1].equalsIgnoreCase("here")
                                ? com.rlutility.modules.XRayHandler.lookingAtBlockId()
                                : args[1];
                        if (id == null) {
                            say(mc, "\u00a7cNot looking at a block.");
                            return;
                        }
                        com.rlutility.modules.XRayHandler.toggleBlock(id);
                        say(mc, "\u00a7aXRay list now has "
                                + com.rlutility.modules.XRayHandler.targetCount() + " entries (toggled " + id + ").");
                    } else {
                        FeatureConfig.xrayEnabled = !FeatureConfig.xrayEnabled;
                        FeatureConfig.saveConfig();
                        com.rlutility.modules.XRayHandler.forceRescan();
                        say(mc, "\u00a7fXRay " + (FeatureConfig.xrayEnabled ? "\u00a7aENABLED" : "\u00a7cDISABLED"));
                    }
                });
                return;
            } else if (sub.equals("unlock")) {
                // Buys exactly the Reskillable levels the held item is missing.
                mc.addScheduledTask(() -> say(mc, com.rlutility.modules.ReskillableHelper.unlockHeldItem()));
                return;
            } else if (sub.equals("buy")) {
                // /rlu buy <skill> [levels]
                if (args.length < 2) {
                    mc.addScheduledTask(() -> say(mc, "\u00a7cUsage: /rlu buy <skill> [levels]"));
                    return;
                }
                final String skillName = args[1];
                final int count = args.length > 2 ? parseIntSafe(args[2], 1) : 1;
                mc.addScheduledTask(() -> {
                    codersafterdark.reskillable.api.skill.Skill skill =
                            com.rlutility.modules.ReskillableHelper.skillByName(skillName);
                    if (skill == null) {
                        say(mc, "\u00a7cUnknown skill '" + skillName + "'.");
                        return;
                    }
                    int bought = com.rlutility.modules.ReskillableHelper.buyLevels(skill, count);
                    say(mc, bought > 0
                            ? "\u00a7aBought " + bought + " level(s) of " + skill.getName() + "."
                            : "\u00a7cCould not buy any levels - not enough XP, or the skill is capped.");
                });
                return;
            } else if (sub.equals("revert") || sub.equals("clear")) {
                mc.addScheduledTask(com.rlutility.modules.LevelUpExploitHandler::clearPlanned);
                return;
            }
        }

        mc.addScheduledTask(() -> {
            mc.displayGuiScreen(new GuiUtilityMenu());
        });
    }

    private static int parseIntSafe(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void say(net.minecraft.client.Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                    "\u00a76[RLUtility] \u00a7r" + message));
        }
    }
}
