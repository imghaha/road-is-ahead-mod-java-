package com.watire.longroad;

import com.mojang.logging.LogUtils;
import com.watire.longroad.Items.ModItems;
import com.watire.longroad.block.ModBlock;
import com.watire.longroad.worldset.CustomBiomeSource;
import com.watire.longroad.worldset.ModWorldGen;
import com.watire.longroad.worldset.TerrainChunkGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(longroad.MODID)
public class longroad {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "longroad";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // 删除这行，避免和 Minecraft 的 BIOMES 冲突
    public static final DeferredRegister<Biome> BIOMES = DeferredRegister.create(ForgeRegistries.BIOMES, MODID);


    public longroad(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        // Register the commonSetup method for modloading
        System.out.println("🚀 初始化长路Mod...");
        // 初始化配置系统
        System.out.println("📋 初始化配置系统...");
        System.out.println("  当前配置值:");
        System.out.println("    宽度: " + com.watire.longroad.config.BiomeConfigManager.getBiomeWidth());
        System.out.println("    变化范围: " + com.watire.longroad.config.BiomeConfigManager.getVariationRange());
        modEventBus.addListener(this::commonSetup);
        ModWorldGen.CHUNK_GENERATORS.register(modEventBus);
        ModWorldGen.BIOME_SOURCES.register(modEventBus);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BIOMES.register(modEventBus);
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        ModBlock.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }


    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");


        // 在主类 longroad.java 中确保注册了群系

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }

    // 添加事件监听器来设置种子
    @Mod.EventBusSubscriber(modid = MODID)
    public static class WorldEventHandler {
        @SubscribeEvent
        public static void onWorldLoad(LevelEvent.Load event) {
            // 只在服务器端处理
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                BiomeSource biomeSource = serverLevel.getChunkSource().getGenerator().getBiomeSource();

                // 如果使用的是我们的 CustomBiomeSource
                if (biomeSource instanceof CustomBiomeSource customSource) {
                    // 获取世界种子并设置
                    long seed = serverLevel.getSeed();
                    customSource.setWorldSeed(seed);

                    System.out.println("✅ 成功为 CustomBiomeSource 设置世界种子: " + seed);
                }
            }
        }
    }
}