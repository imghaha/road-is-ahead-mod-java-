package com.watire.longroad.config;

import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class BiomeConfigManager {

    // 简化实现，直接转发给 RealBiomeConfig
    // 改进缓存机制：为每个值独立缓存

    // 初始化
    static {
        System.out.println("🎮 BiomeConfigManager 初始化");
        // 预加载配置
        RealBiomeConfig.getBiomeWidth();
        RealBiomeConfig.getVariationRange();
    }

    // 设置新配置 - 直接转发
    public static void setNewConfig(int width, int variation) {
        System.out.println("\n🎯 BiomeConfigManager.setNewConfig() 被调用");

        // 清除缓存，强制下次重新读取
        clearCache();

        // 直接保存到文件
        RealBiomeConfig.setConfig(width, variation);
    }

    // 独立的缓存变量
    private static int cachedWidth = 1000;
    private static int cachedVariation = 50;
    private static long widthLastGetTime = 0;
    private static long variationLastGetTime = 0;
    private static final long CACHE_DURATION = 1000; // 1秒缓存

    public static int getBiomeWidth() {
        long currentTime = System.currentTimeMillis();

        // 如果缓存过期，重新读取
        if (currentTime - widthLastGetTime > CACHE_DURATION) {
            //System.out.println("📊 BiomeConfigManager.getBiomeWidth(): 缓存过期，重新读取");
            cachedWidth = RealBiomeConfig.getBiomeWidth();
            widthLastGetTime = currentTime;
            System.out.println("  读取到的宽度: " + cachedWidth);
        } else {
            //System.out.println("📊 BiomeConfigManager.getBiomeWidth(): 使用缓存值: " + cachedWidth);
        }
        return cachedWidth;
    }

    public static int getVariationRange() {
        long currentTime = System.currentTimeMillis();

        // 如果缓存过期，重新读取
        if (currentTime - variationLastGetTime > CACHE_DURATION) {
            //System.out.println("📊 BiomeConfigManager.getVariationRange(): 缓存过期，重新读取");
            cachedVariation = RealBiomeConfig.getVariationRange();
            variationLastGetTime = currentTime;
            System.out.println("  读取到的变化范围: " + cachedVariation);
        } else {
            //System.out.println("📊 BiomeConfigManager.getVariationRange(): 使用缓存值: " + cachedVariation);
        }
        return cachedVariation;
    }

    // 清除缓存，强制重新读取
    private static void clearCache() {
        System.out.println("🗑️ BiomeConfigManager: 清除缓存");
        widthLastGetTime = 0;
        variationLastGetTime = 0;
        // 可以不清除缓存值，因为它们会被新值覆盖
    }

    // 世界加载时的事件
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        System.out.println("\n🌍 世界加载事件触发");
        // 清除缓存，强制下一次获取时重新读取
        clearCache();
    }

    // 强制刷新配置（不缓存）
    public static void forceRefreshConfig() {
        System.out.println("🔄 BiomeConfigManager: 强制刷新配置");
        clearCache();
        // 立即读取最新值
        cachedWidth = RealBiomeConfig.getBiomeWidth();
        cachedVariation = RealBiomeConfig.getVariationRange();
        widthLastGetTime = System.currentTimeMillis();
        variationLastGetTime = System.currentTimeMillis();

        System.out.println("  刷新后宽度: " + cachedWidth);
        System.out.println("  刷新后变化范围: " + cachedVariation);
    }

    // 打印当前状态
    public static void printStatus() {
        System.out.println("\n🔧 BiomeConfigManager 状态:");
        System.out.println("  缓存宽度: " + cachedWidth + ", 最后更新时间: " + widthLastGetTime);
        System.out.println("  缓存变化范围: " + cachedVariation + ", 最后更新时间: " + variationLastGetTime);
        System.out.println("  实际配置: width=" + getBiomeWidth() + ", variation=" + getVariationRange());
    }
}