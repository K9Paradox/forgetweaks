package com.rlutility.proxy;

import com.rlutility.command.CommandQuestExploit;
import com.rlutility.command.CommandRLUtility;
import com.rlutility.command.CommandRaceExploit;
import com.rlutility.command.CommandDesyncExploit;
import com.rlutility.command.CommandSkillExploit;
import com.rlutility.command.CommandUnlockAll;
import com.rlutility.gui.GuiUtilityMenu;
import com.rlutility.gui.HudOverlay;
import com.rlutility.modules.AntiKnockbackHandler;
import com.rlutility.modules.AutoArmorHandler;
import com.rlutility.modules.AutoCritHandler;
import com.rlutility.modules.AutoEatHandler;
import com.rlutility.modules.AutoLootHandler;
import com.rlutility.modules.AutoReforgerHandler;
import com.rlutility.modules.AutoRespawnHandler;
import com.rlutility.modules.DebuffPurgerHandler;
import com.rlutility.modules.EspRenderHelper;
import com.rlutility.modules.FastMineHelper;
import com.rlutility.modules.FastTriageHandler;
import com.rlutility.modules.FeatureConfig;
import com.rlutility.modules.FirstAidHelper;
import com.rlutility.modules.ItemMagnetHandler;
import com.rlutility.modules.JesusHandler;
import com.rlutility.modules.KillAuraHandler;
import com.rlutility.modules.LevelUpExploitHandler;
import com.rlutility.modules.LocksHelper;
import com.rlutility.modules.MovementEventHandler;
import com.rlutility.modules.NoFallHandler;
import com.rlutility.modules.NoSlowdownHandler;
import com.rlutility.modules.ReskillableHelper;
import com.rlutility.modules.SimpleDifficultyHelper;
import com.rlutility.modules.StepSpeedHandler;
import com.rlutility.modules.TimerHandler;
import com.rlutility.modules.TriggerbotHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.input.Keyboard;

public class ClientProxy extends CommonProxy {

    public static KeyBinding openGuiKey;
    public static KeyBinding toggleKillAuraKey;
    public static KeyBinding toggleHudKey;

    @Override
    public void init() {
        super.init();

        openGuiKey = new KeyBinding("key.rlutility.opengui", Keyboard.KEY_RSHIFT, "category.rlutility");
        toggleKillAuraKey = new KeyBinding("key.rlutility.killaura", Keyboard.KEY_R, "category.rlutility");
        toggleHudKey = new KeyBinding("key.rlutility.hud", Keyboard.KEY_H, "category.rlutility");
        ClientRegistry.registerKeyBinding(openGuiKey);
        ClientRegistry.registerKeyBinding(toggleKillAuraKey);
        ClientRegistry.registerKeyBinding(toggleHudKey);

        MinecraftForge.EVENT_BUS.register(this);

        // ---- server-authoritative combat / movement -------------------------
        MinecraftForge.EVENT_BUS.register(new AutoCritHandler());
        MinecraftForge.EVENT_BUS.register(new KillAuraHandler());
        MinecraftForge.EVENT_BUS.register(new AntiKnockbackHandler());
        MinecraftForge.EVENT_BUS.register(new NoFallHandler());
        MinecraftForge.EVENT_BUS.register(new NoSlowdownHandler());
        MinecraftForge.EVENT_BUS.register(new StepSpeedHandler());
        MinecraftForge.EVENT_BUS.register(new JesusHandler());
        MinecraftForge.EVENT_BUS.register(new TimerHandler());
        MinecraftForge.EVENT_BUS.register(new MovementEventHandler());
        MinecraftForge.EVENT_BUS.register(new TriggerbotHandler());

        // ---- server-authoritative inventory automation ----------------------
        MinecraftForge.EVENT_BUS.register(new FastTriageHandler());
        MinecraftForge.EVENT_BUS.register(new AutoArmorHandler());
        MinecraftForge.EVENT_BUS.register(new AutoEatHandler());
        MinecraftForge.EVENT_BUS.register(new AutoLootHandler());
        MinecraftForge.EVENT_BUS.register(new AutoRespawnHandler());
        MinecraftForge.EVENT_BUS.register(new FastMineHelper());

        // ---- mod-channel exploits (server must run the mod, RLCraft does) ---
        MinecraftForge.EVENT_BUS.register(new AutoReforgerHandler());
        MinecraftForge.EVENT_BUS.register(new LocksHelper());
        MinecraftForge.EVENT_BUS.register(new ItemMagnetHandler());
        MinecraftForge.EVENT_BUS.register(new FirstAidHelper());
        MinecraftForge.EVENT_BUS.register(new SimpleDifficultyHelper());

        // ---- client-side visuals / quality of life --------------------------
        MinecraftForge.EVENT_BUS.register(new DebuffPurgerHandler());
        MinecraftForge.EVENT_BUS.register(new ReskillableHelper());
        MinecraftForge.EVENT_BUS.register(new EspRenderHelper());
        MinecraftForge.EVENT_BUS.register(new HudOverlay());

        ClientCommandHandler.instance.registerCommand(new CommandRLUtility());
        ClientCommandHandler.instance.registerCommand(new CommandSkillExploit());
        ClientCommandHandler.instance.registerCommand(new CommandRaceExploit());
        ClientCommandHandler.instance.registerCommand(new CommandQuestExploit());
        ClientCommandHandler.instance.registerCommand(new CommandUnlockAll());
        ClientCommandHandler.instance.registerCommand(new CommandDesyncExploit());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        // Reports whether the server actually accepted the last Level Up! 2 skill packet.
        LevelUpExploitHandler.tick();

        if (mc.currentScreen != null) return;

        if (openGuiKey != null && openGuiKey.isPressed()) {
            mc.displayGuiScreen(new GuiUtilityMenu());
        }
        if (toggleKillAuraKey != null && toggleKillAuraKey.isPressed()) {
            FeatureConfig.killAura = !FeatureConfig.killAura;
            FeatureConfig.saveConfig();
            announce(mc, "Kill Aura", FeatureConfig.killAura);
        }
        if (toggleHudKey != null && toggleHudKey.isPressed()) {
            FeatureConfig.hudEnabled = !FeatureConfig.hudEnabled;
            FeatureConfig.saveConfig();
            announce(mc, "HUD", FeatureConfig.hudEnabled);
        }
    }

    /** Make sure the timer never survives a disconnect. */
    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        TimerHandler.reset();
    }

    private void announce(Minecraft mc, String name, boolean state) {
        if (mc.player == null) return;
        mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                "\u00a76[RLUtility] \u00a7f" + name + (state ? " \u00a7aENABLED" : " \u00a7cDISABLED")));
    }
}
