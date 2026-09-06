package com.watire.longroad.config;

import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber
public class BiomeConfigManager {

    static {
        RealBiomeConfig.getBiomeWidth();
        RealBiomeConfig.getVariationRange();
        RealBiomeConfig.getBiomeDistribution();
        RealBiomeConfig.getRoadType();
    }

    public static void setNewConfig(int width, int variation, String distribution, RoadType roadType) {
        clearCache();
        RealBiomeConfig.setConfig(width, variation, distribution, roadType);
    }

    private static int cachedWidth = 1000;
    private static int cachedVariation = 50;
    private static String cachedDistribution = "z_axis_only";
    private static RoadType cachedRoadType = RoadType.STRAIGHT;
    private static long widthLastGetTime = 0;
    private static long variationLastGetTime = 0;
    private static long distributionLastGetTime = 0;
    private static long roadTypeLastGetTime = 0;
    private static final long CACHE_DURATION = 1000;

    public static int getBiomeWidth() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - widthLastGetTime > CACHE_DURATION) {
            cachedWidth = RealBiomeConfig.getBiomeWidth();
            widthLastGetTime = currentTime;
        }
        return cachedWidth;
    }

    public static int getVariationRange() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - variationLastGetTime > CACHE_DURATION) {
            cachedVariation = RealBiomeConfig.getVariationRange();
            variationLastGetTime = currentTime;
        }
        return cachedVariation;
    }

    public static String getBiomeDistribution() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - distributionLastGetTime > CACHE_DURATION) {
            cachedDistribution = RealBiomeConfig.getBiomeDistribution();
            distributionLastGetTime = currentTime;
        }
        return cachedDistribution;
    }

    public static RoadType getRoadType() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - roadTypeLastGetTime > CACHE_DURATION) {
            cachedRoadType = RealBiomeConfig.getRoadType();
            roadTypeLastGetTime = currentTime;
        }
        return cachedRoadType;
    }

    public static boolean isRoadEnabled() {
        return getRoadType() != RoadType.NONE;
    }

    // ========== 新增：获取地形配置 ==========
    public static LongRoadBiomeConfig.TerrainConfig getTerrainConfig(String registryName) {
        List<LongRoadBiomeConfig.BiomeData> data = LongRoadBiomeConfig.loadBiomeData();
        for (LongRoadBiomeConfig.BiomeData d : data) {
            if (d.getRegistryName().equals(registryName)) {
                return d.getTerrain();
            }
        }
        // 如果未找到，返回默认地形配置
        return new LongRoadBiomeConfig.TerrainConfig("grass_block", "dirt", "stone");
    }

    private static void clearCache() {
        widthLastGetTime = 0;
        variationLastGetTime = 0;
        distributionLastGetTime = 0;
        roadTypeLastGetTime = 0;
    }

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        clearCache();
    }

    public static void forceRefreshConfig() {
        clearCache();
        cachedWidth = RealBiomeConfig.getBiomeWidth();
        cachedVariation = RealBiomeConfig.getVariationRange();
        cachedDistribution = RealBiomeConfig.getBiomeDistribution();
        cachedRoadType = RealBiomeConfig.getRoadType();
        widthLastGetTime = System.currentTimeMillis();
        variationLastGetTime = System.currentTimeMillis();
        distributionLastGetTime = System.currentTimeMillis();
        roadTypeLastGetTime = System.currentTimeMillis();
    }

    // ========== 新增：获取路径混淆配置 ==========
    public static LongRoadBiomeConfig.PathConfig getPathConfig(String registryName) {
        List<LongRoadBiomeConfig.BiomeData> data = LongRoadBiomeConfig.loadBiomeData();
        for (LongRoadBiomeConfig.BiomeData d : data) {
            if (d.getRegistryName().equals(registryName)) {
                return d.getPath();
            }
        }
        // 如果未找到，返回默认路径配置
        return new LongRoadBiomeConfig.PathConfig("dirt_path", "grass_block", "cobblestone");
    }
}