package com.rlutility.modules;

import com.tmtravlr.qualitytools.QualityToolsMod;
import com.tmtravlr.qualitytools.network.CToSMessage;
import com.tmtravlr.qualitytools.reforging.ContainerReforgingStation;
import com.tmtravlr.qualitytools.reforging.GuiReforgingStation;
import com.tmtravlr.qualitytools.reforging.TileEntityReforgingStation;
import cursedflames.bountifulbaubles.block.GuiReforger;
import cursedflames.bountifulbaubles.block.TileReforger;
import cursedflames.bountifulbaubles.network.NBTPacket;
import cursedflames.bountifulbaubles.network.PacketHandler;
import cursedflames.bountifulbaubles.util.Util;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

public class AutoReforgerHandler {

    private int tickDelay = 0;

    public static final String[] QUALITY_PRESETS = {
        "Godly / Legendary",
        "Masterful / Undying",
        "Violent / Punishing",
        "Sweeping / Sharp",
        "Hearty / Healthy",
        "Any Positive Quality"
    };

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!FeatureConfig.autoReforge || event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        if (screen == null) return;

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        // Handle QualityTools Reforging Station
        if (Loader.isModLoaded("qualitytools") && screen instanceof GuiReforgingStation) {
            handleQualityTools((GuiReforgingStation) screen, mc);
            return;
        }

        // Handle Bountiful Baubles Reforger (Anvil GUI)
        if (Loader.isModLoaded("bountifulbaubles") && screen instanceof GuiReforger) {
            handleBountifulBaubles((GuiReforger) screen, mc);
        }
    }

    private void handleQualityTools(GuiReforgingStation screen, Minecraft mc) {
        try {
            Field tileField = ReflectionHelper.findField(GuiReforgingStation.class, "tileReforgingStation");
            TileEntityReforgingStation tile = (TileEntityReforgingStation) tileField.get(screen);
            if (tile == null) return;

            // Slot 0 is the item being reforged, Slot 1 is materials
            ContainerReforgingStation container = (ContainerReforgingStation) screen.inventorySlots;
            ItemStack toolStack = container.getSlot(0).getStack();
            ItemStack matStack = container.getSlot(1).getStack();

            if (toolStack.isEmpty() || matStack.isEmpty()) return;

            // Check if item already has target quality
            if (toolStack.hasTagCompound() && toolStack.getTagCompound().hasKey("Quality")) {
                NBTTagCompound qTag = toolStack.getTagCompound().getCompoundTag("Quality");
                String qName = qTag.getString("Name").toLowerCase();
                if (matchesSelectedQuality(qName)) {
                    return; // Done
                }
            }

            // Send packet to reforge
            PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
            buf.writeInt(1);
            buf.writeBlockPos(tile.getPos());
            buf.writeInt(tile.getWorld() != null ? tile.getWorld().provider.getDimension() : 0);
            QualityToolsMod.networkWrapper.sendToServer(new CToSMessage(buf));

            tickDelay = 6;
        } catch (Throwable ignored) {}
    }

    private void handleBountifulBaubles(GuiReforger screen, Minecraft mc) {
        try {
            Field tileField = ReflectionHelper.findField(GuiReforger.class, "te");
            TileReforger tile = (TileReforger) tileField.get(screen);
            if (tile == null) return;

            Container container = screen.inventorySlots;
            ItemStack toolStack = container.getSlot(0).getStack();
            ItemStack matStack = container.getSlot(1).getStack();

            if (toolStack.isEmpty() || matStack.isEmpty()) return;

            // Check if bauble already has target modifier
            if (toolStack.hasTagCompound() && toolStack.getTagCompound().hasKey("baubleModifier")) {
                String mod = toolStack.getTagCompound().getString("baubleModifier").toLowerCase();
                if (matchesSelectedModifier(mod)) {
                    return; // Target reached
                }
            }

            // Check player XP level vs required
            if (mc.player.experienceLevel < 1) return;

            // Send reforge packet
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("pos", Util.blockPosToNBT(tile.getPos()));
            PacketHandler.INSTANCE.sendToServer(new NBTPacket(tag, PacketHandler.HandlerIds.REFORGE.id));

            tickDelay = 6;
        } catch (Throwable ignored) {}
    }

    public static boolean matchesSelectedQuality(String qName) {
        String target = FeatureConfig.targetQuality;
        if (target == null || target.contains("Godly / Legendary")) {
            return qName.contains("godly") || qName.contains("legendary") || qName.contains("masterful");
        } else if (target.contains("Masterful / Undying")) {
            return qName.contains("masterful") || qName.contains("undying") || qName.contains("legendary");
        } else if (target.contains("Violent / Punishing")) {
            return qName.contains("violent") || qName.contains("punishing");
        } else if (target.contains("Sweeping / Sharp")) {
            return qName.contains("sweeping") || qName.contains("sharp") || qName.contains("keen");
        } else if (target.contains("Hearty / Healthy")) {
            return qName.contains("hearty") || qName.contains("healthy") || qName.contains("armored");
        } else {
            // Any positive quality
            return qName.contains("godly") || qName.contains("legendary") || qName.contains("masterful") ||
                   qName.contains("undying") || qName.contains("punishing") || qName.contains("sweeping") ||
                   qName.contains("hearty") || qName.contains("armored") || qName.contains("healthy") ||
                   qName.contains("violent") || qName.contains("quick") || qName.contains("keen");
        }
    }

    public static boolean matchesSelectedModifier(String mod) {
        String target = FeatureConfig.targetQuality;
        if (target == null || target.contains("Godly / Legendary") || target.contains("Masterful / Undying")) {
            return mod.contains("undying") || mod.contains("hearty") || mod.contains("armored");
        } else if (target.contains("Violent / Punishing")) {
            return mod.contains("punishing") || mod.contains("violent") || mod.contains("menacing");
        } else if (target.contains("Hearty / Healthy")) {
            return mod.contains("hearty") || mod.contains("armored") || mod.contains("healthy");
        } else {
            return mod.contains("undying") || mod.contains("punishing") || mod.contains("hearty") ||
                   mod.contains("armored") || mod.contains("menacing") || mod.contains("quick") ||
                   mod.contains("violent") || mod.contains("healthy");
        }
    }
}
