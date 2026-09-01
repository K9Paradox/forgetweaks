package com.rlutility.command;

import com.rlutility.gui.GuiUtilityMenu;
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
        return "/rlu (or /rlu level <number>, /rlu tree <mining/crafting/combat> <lvl>, /rlu safe, /rlu max, /rlmenu, /rlgui)";
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
                mc.addScheduledTask(() -> {
                    com.rlutility.modules.LevelUpExploitHandler.setAllSkillsLevel(10);
                });
                return;
            }
        }

        mc.addScheduledTask(() -> {
            mc.displayGuiScreen(new GuiUtilityMenu());
        });
    }
}
