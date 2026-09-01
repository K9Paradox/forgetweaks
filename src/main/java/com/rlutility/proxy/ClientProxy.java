package com.rlutility.proxy;

import com.rlutility.command.*;
import com.rlutility.gui.GuiUtilityMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class ClientProxy extends CommonProxy {

    public static KeyBinding openGuiKey;

    @Override
    public void init() {
        super.init();
        openGuiKey = new KeyBinding("key.rlutility.opengui", Keyboard.KEY_RSHIFT, "category.rlutility");
        ClientRegistry.registerKeyBinding(openGuiKey);
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new CommandRLUtility());
        ClientCommandHandler.instance.registerCommand(new CommandSkillExploit());
        ClientCommandHandler.instance.registerCommand(new CommandRaceExploit());
        ClientCommandHandler.instance.registerCommand(new CommandQuestExploit());
        ClientCommandHandler.instance.registerCommand(new CommandUnlockAll());
        ClientCommandHandler.instance.registerCommand(new CommandDesyncExploit());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world != null && mc.player != null) {
                if (openGuiKey != null && openGuiKey.isPressed()) {
                    if (mc.currentScreen == null) {
                        mc.displayGuiScreen(new GuiUtilityMenu());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (openGuiKey != null && openGuiKey.isPressed()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(new GuiUtilityMenu());
            }
        }
    }
}
