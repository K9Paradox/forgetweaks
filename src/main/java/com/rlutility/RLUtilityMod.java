package com.rlutility;

import com.rlutility.modules.*;
import com.rlutility.proxy.CommonProxy;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = RLUtilityMod.MODID, name = RLUtilityMod.NAME, version = RLUtilityMod.VERSION)
public class RLUtilityMod {

    public static final String MODID = "rlutility";
    public static final String NAME = "RLUtility Mod";
    public static final String VERSION = "1.3.0";

    @Mod.Instance(MODID)
    public static RLUtilityMod instance;

    @SidedProxy(
        clientSide = "com.rlutility.proxy.ClientProxy",
        serverSide = "com.rlutility.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        FeatureConfig.loadConfig();
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();

        // Register Server-Compatible Packet / Combat / Movement Handlers
        MinecraftForge.EVENT_BUS.register(new AutoCritHandler());
        MinecraftForge.EVENT_BUS.register(new NoFallHandler());
        MinecraftForge.EVENT_BUS.register(new NoSlowdownHandler());
        MinecraftForge.EVENT_BUS.register(new FastTriageHandler());
        MinecraftForge.EVENT_BUS.register(new StepSpeedHandler());
        MinecraftForge.EVENT_BUS.register(new MovementEventHandler());
        MinecraftForge.EVENT_BUS.register(new TriggerbotHandler());

        // Register Mod-Specific Client Exploits & Helpers
        MinecraftForge.EVENT_BUS.register(new AutoReforgerHandler());
        MinecraftForge.EVENT_BUS.register(new LocksHelper());
        MinecraftForge.EVENT_BUS.register(new DebuffPurgerHandler());
        MinecraftForge.EVENT_BUS.register(new ItemMagnetHandler());

        // Register Advanced Exploits & Visuals
        MinecraftForge.EVENT_BUS.register(new ReskillableHelper());
        MinecraftForge.EVENT_BUS.register(new FastMineHelper());
        MinecraftForge.EVENT_BUS.register(new FirstAidHelper());
        MinecraftForge.EVENT_BUS.register(new SimpleDifficultyHelper());
        MinecraftForge.EVENT_BUS.register(new EspRenderHelper());
    }
}
