package com.rlutility;

import com.rlutility.modules.FeatureConfig;
import com.rlutility.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = RLUtilityMod.MODID, name = RLUtilityMod.NAME, version = RLUtilityMod.VERSION,
     clientSideOnly = true, acceptableRemoteVersions = "*")
public class RLUtilityMod {

    public static final String MODID = "rlutility";
    public static final String NAME = "RLUtility";
    public static final String VERSION = "1.7.0";

    @Mod.Instance(MODID)
    public static RLUtilityMod instance;

    @SidedProxy(
        clientSide = "com.rlutility.proxy.ClientProxy",
        serverSide = "com.rlutility.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Config lives in Forge's config dir and never touches a client-only class,
        // so this stays safe even if the jar ends up on a dedicated server.
        FeatureConfig.init(event.getModConfigurationDirectory());
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Every handler in this mod is client-side; registration happens in ClientProxy so a
        // dedicated server never class-loads net.minecraft.client.
        proxy.init();
    }
}
