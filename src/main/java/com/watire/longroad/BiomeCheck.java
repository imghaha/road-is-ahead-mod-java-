package com.watire.longroad;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

import static com.watire.longroad.longroad.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BiomeCheck {
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            System.out.println("========== [LongRoad] 生物群系存在性检查 ==========");

            // 检查 ForgeRegistries.BIOMES
            ResourceLocation plainsId = new ResourceLocation("minecraft:plains");
            Biome forgeBiome = ForgeRegistries.BIOMES.getValue(plainsId);
            System.out.println("==================================================");
        });
    }
}