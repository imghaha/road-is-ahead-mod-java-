package com.watire.longroad;

import com.mojang.logging.LogUtils;
import com.watire.longroad.Items.ModItems;
import com.watire.longroad.block.ModBlock;
import com.watire.longroad.config.BiomeConfigManager;
import com.watire.longroad.config.LongRoadBiomeConfig;
import com.watire.longroad.worldset.CustomBiomeSource;
import com.watire.longroad.worldset.ModWorldGen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
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

@Mod(longroad.MODID)
public class longroad {
    public static final String MODID = "longroad";
    public static final Logger LOGGER = LogUtils.getLogger();

    public longroad(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        System.out.println("🚀 初始化长路Mod...");

        // 初始化配置系统 - 注意：BiomeConfigManager 通过 static 块自动加载
        System.out.println("📋 配置系统已准备");
        System.out.println("  当前配置值:");
        System.out.println("    宽度: " + BiomeConfigManager.getBiomeWidth());
        System.out.println("    变化范围: " + BiomeConfigManager.getVariationRange());
        System.out.println("    分布: " + BiomeConfigManager.getBiomeDistribution());
        System.out.println("    道路类型: " + BiomeConfigManager.getRoadType().getConfigName());

        // 加载自定义群系配置
        LongRoadBiomeConfig.loadBiomeData();

        modEventBus.addListener(this::commonSetup);

        ModWorldGen.CHUNK_GENERATORS.register(modEventBus);
        ModWorldGen.BIOME_SOURCES.register(modEventBus);
        BIOMES.register(modEventBus);
        ModBlock.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);

        // 注册 Forge 事件总线
        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("长路Mod通用设置完成");
    }

    // ========== 世界事件处理 ==========

    /**
     * 世界开始加载时调用
     */
    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            System.out.println("[LongRoad] 🌍 世界开始加载 - 维度: " + serverLevel.dimension().location());
            System.out.println("[LongRoad] 种子: " + serverLevel.getSeed());

            // 刷新配置缓存
            BiomeConfigManager.forceRefreshConfig();

            // 标记新世界开始加载
            CustomBiomeSource.onWorldLoading();

            // 延迟一点执行初始化，确保所有 CustomBiomeSource 实例都已创建
            serverLevel.getServer().execute(() -> {
                try {
                    // 给点时间让所有实例创建完成
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // 获取群系注册表
                RegistryAccess registryAccess = serverLevel.registryAccess();
                HolderLookup.RegistryLookup<Biome> biomeLookup = registryAccess.lookupOrThrow(Registries.BIOME);

                // 通知 CustomBiomeSource 新世界加载完成
                CustomBiomeSource.onNewWorldLoaded(biomeLookup, serverLevel.getSeed());

                // 为当前世界的 BiomeSource 设置种子
                BiomeSource biomeSource = serverLevel.getChunkSource().getGenerator().getBiomeSource();
                if (biomeSource instanceof CustomBiomeSource customSource) {
                    customSource.setWorldSeed(serverLevel.getSeed());
                    System.out.println("[LongRoad] ✅ 为当前世界的 CustomBiomeSource 设置种子: " + serverLevel.getSeed());
                }
            });
        }
    }

    /**
     * 世界卸载时调用
     */
    @SubscribeEvent
    public void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            System.out.println("[LongRoad] 🌍 世界卸载 - 维度: " + serverLevel.dimension().location());
            CustomBiomeSource.onWorldUnload();
        }
    }

    /**
     * 世界保存时调用
     */
    @SubscribeEvent
    public void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel) {
            // 刷新配置缓存，确保配置更新
            BiomeConfigManager.forceRefreshConfig();
        }
    }

    // ========== 服务器事件 ==========

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        System.out.println("[LongRoad] 🚀 服务器启动中...");
        BiomeConfigManager.forceRefreshConfig();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        System.out.println("[LongRoad] 💤 服务器关闭，重置所有状态");
        CustomBiomeSource.reset();
    }

    // ========== 客户端事件 ==========

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("长路Mod客户端设置完成");
            LOGGER.info("玩家名: {}", Minecraft.getInstance().getUser().getName());
        }
    }

    // ========== 群系注册 ==========

    public static final DeferredRegister<Biome> BIOMES =
            DeferredRegister.create(ForgeRegistries.BIOMES, MODID);

    public static final RegistryObject<Biome> FALLBACK_BIOME = BIOMES.register("fallback_biome",
            () -> new Biome.BiomeBuilder()
                    .temperature(0.8f)
                    .downfall(0.4f)
                    .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                    .generationSettings(new BiomeGenerationSettings.PlainBuilder().build())
                    .specialEffects(new BiomeSpecialEffects.Builder()
                            .waterColor(4159204)
                            .waterFogColor(329011)
                            .fogColor(12638463)
                            .skyColor(calculateSkyColor(0.8f))
                            .build())
                    .build()
    );

    private static int calculateSkyColor(float temperature) {
        float f = temperature / 3.0F;
        f = Math.max(-1.0F, Math.min(1.0F, f));
        return ((int)Math.round(0x9C - f * 0x1E)) << 16 |
                ((int)Math.round(0xA0 - f * 0x1A)) << 8 |
                0xFF;
    }
}