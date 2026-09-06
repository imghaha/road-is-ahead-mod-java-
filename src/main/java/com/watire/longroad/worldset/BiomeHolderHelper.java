package com.watire.longroad.worldset;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.BiomeGenerationSettings;

import java.util.Optional;

public class BiomeHolderHelper {

    private static Holder<Biome> fallbackBiome = null;

    public static Holder<Biome> getFallbackBiome() {
        if (fallbackBiome != null) {
            return fallbackBiome;
        }

        try {
            // 方法1: 尝试创建占位群系
            Biome placeholderBiome = createPlaceholderBiome();
            fallbackBiome = Holder.direct(placeholderBiome);
            System.out.println("[LongRoad] 创建占位群系成功");
            return fallbackBiome;
        } catch (Exception e) {
            System.err.println("[LongRoad] 创建占位群系失败，使用最终保底方案");
            e.printStackTrace();

            // 最终保底：返回null，但调用方必须处理
            return null;
        }
    }

    private static Biome createPlaceholderBiome() {
        // 使用正确的1.20.1 API
        Biome.BiomeBuilder builder = new Biome.BiomeBuilder();

        // 设置基本属性
        builder.temperature(0.8f);
        builder.downfall(0.4f);

        // 设置生物生成设置（空）
        builder.mobSpawnSettings(new MobSpawnSettings.Builder().build());

        // 设置生成设置（空）
        builder.generationSettings(new BiomeGenerationSettings.PlainBuilder().build());

        // 设置特效
        builder.specialEffects(new BiomeSpecialEffects.Builder()
                .waterColor(4159204)
                .waterFogColor(329011)
                .fogColor(12638463)
                .skyColor(calculateSkyColor(0.8f))
                .build());

        return builder.build();
    }

    private static int calculateSkyColor(float temperature) {
        float f = temperature / 3.0F;
        f = Math.max(-1.0F, Math.min(1.0F, f));
        return ((int)Math.round(0x9C - f * 0x1E)) << 16 |
                ((int)Math.round(0xA0 - f * 0x1A)) << 8 |
                0xFF;
    }

    public static Holder<Biome> getBiomeHolder(ResourceLocation id, RegistryAccess registryAccess) {
        if (registryAccess != null) {
            Optional<Registry<Biome>> biomeRegistry = registryAccess.registry(Registries.BIOME);
            if (biomeRegistry.isPresent()) {
                ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
                Optional<Holder.Reference<Biome>> holder = biomeRegistry.get().getHolder(key);
                if (holder.isPresent()) {
                    return holder.get();
                }
            }
        }

        // 如果无法获取，返回null，让调用方决定
        return null;
    }
}