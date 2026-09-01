package com.rlutility.command;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CommandUnlockAll extends CommandBase {

    @Override
    public String getName() {
        return "unlockall";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("clearrequirements");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/unlockall";
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
        if (mc.player == null) return;

        if (!Loader.isModLoaded("reskillable")) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[RLUtility] Reskillable mod is not loaded."));
            return;
        }

        try {
            Class<?> levelLockHandlerClass = Class.forName("codersafterdark.reskillable.base.LevelLockHandler");
            Field locksField = levelLockHandlerClass.getDeclaredField("locks");
            locksField.setAccessible(true);
            Map<?, ?> locksMap = (Map<?, ?>) locksField.get(null);
            int clearedCount = locksMap.size();
            locksMap.clear();

            Field fuzzyField = levelLockHandlerClass.getDeclaredField("fuzzyLockInfo");
            fuzzyField.setAccessible(true);
            Map<?, ?> fuzzyMap = (Map<?, ?>) fuzzyField.get(null);
            fuzzyMap.clear();

            sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "[RLUtility] " + TextFormatting.GREEN + "Cleared " + clearedCount + " Reskillable requirement locks! All tools, weapons, and armor can now be used/mined without level gates."));
        } catch (Throwable t) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[RLUtility] Failed to clear requirement locks: " + t.getMessage()));
        }
    }
}
