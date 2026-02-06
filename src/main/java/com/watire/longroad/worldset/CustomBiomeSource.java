package com.watire.longroad.worldset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.watire.longroad.config.BiomeConfigManager;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.*;
import java.util.stream.Stream;

public class CustomBiomeSource extends BiomeSource {

    public static final Codec<CustomBiomeSource> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    RegistryCodecs.homogeneousList(Registries.BIOME)
                            .fieldOf("biomes")
                            .forGetter(CustomBiomeSource::getBiomeHolderSet)
            ).apply(instance, CustomBiomeSource::new)
    );

    private final HolderSet<Biome> biomeHolderSet;
    private final List<Holder<Biome>> biomeList;
    private long worldSeed = 0L;

    // 本地配置缓存
    private int cachedBiomeWidth = 10;
    private int cachedVariationRange = 1;
    private long lastConfigCheckTime = 0;
    private static final long CONFIG_CHECK_INTERVAL = 10000; // 10秒检查一次配置

    // 简单缓存：存储最近计算的区域
    private final Map<Integer, Integer> regionBiomeCache = new HashMap<>();

    // 调试计数器
    private int debugCounter = 0;
    private int lastDebugRegionId = -1;

    public CustomBiomeSource(HolderSet<Biome> biomes) {
        super();

        System.out.println("\n🌍 ========== CustomBiomeSource 创建 ==========");

        this.biomeHolderSet = biomes;

        // 初始化配置
        updateCachedConfig();
        System.out.println("初始配置: width=" + cachedBiomeWidth + " ± " + cachedVariationRange);

        if (biomes != null) {
            this.biomeList = new ArrayList<>();
            for (Holder<Biome> holder : biomes) {
                this.biomeList.add(holder);
            }
            System.out.println("可用群系数: " + biomeList.size());
        } else {
            this.biomeList = List.of();
            System.out.println("❌ 警告：没有传入任何群系！");
        }

        System.out.println("======================================\n");
    }

    public void setWorldSeed(long seed) {
        if (this.worldSeed == 0L) {
            this.worldSeed = seed;
            System.out.println("🎯 设置世界种子: " + seed);
            updateCachedConfig();
            regionBiomeCache.clear();
        }
    }

    // 更新缓存的配置
    private void updateCachedConfig() {
        cachedBiomeWidth = BiomeConfigManager.getBiomeWidth();
        cachedVariationRange = BiomeConfigManager.getVariationRange();
        lastConfigCheckTime = System.currentTimeMillis();
    }

    // 检查并更新配置（如果需要）
    private void checkAndUpdateConfig() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastConfigCheckTime > CONFIG_CHECK_INTERVAL) {
            int newWidth = BiomeConfigManager.getBiomeWidth();
            int newVariation = BiomeConfigManager.getVariationRange();

            if (newWidth != cachedBiomeWidth || newVariation != cachedVariationRange) {
                cachedBiomeWidth = newWidth;
                cachedVariationRange = newVariation;
                lastConfigCheckTime = currentTime;

                System.out.println("🔄 配置更新: width=" + cachedBiomeWidth + " ± " + cachedVariationRange);

                // 清空缓存
                regionBiomeCache.clear();
            }
        }
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        if (biomeList.isEmpty()) {
            return getDefaultBiome();
        }

        long effectiveSeed = worldSeed;
        if (effectiveSeed == 0) {
            effectiveSeed = 123456789L;
        }

        // 检查配置更新
        checkAndUpdateConfig();

        // 使用缓存的配置值
        int biomeWidth = cachedBiomeWidth;
        int variationRange = cachedVariationRange;

        // 重要：由于Minecraft每4格采样一次，我们需要将配置除以4
        // 这样用户配置10格，实际上就是10格（而不是40格）
        int scaledBiomeWidth = Math.max(1, biomeWidth / 4);
        int scaledVariationRange = Math.max(0, variationRange / 4);

        // 如果缩放后为0，至少给1个采样点的变化
        if (scaledVariationRange == 0 && variationRange > 0) {
            scaledVariationRange = 1;
        }

        // 计算生物群系索引
        int biomeIndex = calculateBiomeIndex(z, effectiveSeed, scaledBiomeWidth, scaledVariationRange);

        // 安全检查
        if (biomeIndex < 0 || biomeIndex >= biomeList.size()) {
            biomeIndex = 0;
        }

        // 调试输出（每1000次采样输出一次）
        debugCounter++;
        if (debugCounter % 1000 == 0) {
            int regionId = z / Math.max(1, scaledBiomeWidth);
            if (z < 0) {
                regionId = (z + 1) / Math.max(1, scaledBiomeWidth) - 1;
            }

            if (regionId != lastDebugRegionId) {
                lastDebugRegionId = regionId;
            }
        }

        return biomeList.get(biomeIndex);
    }

    // 简化的生物群系索引计算方法
    private int calculateBiomeIndex(int z, long seed, int scaledBiomeWidth, int scaledVariationRange) {
        // 1. 使用缩放后的宽度
        int baseRegionSize = scaledBiomeWidth;

        // 2. 为每个区域生成固定的随机偏移
        int regionId = z / Math.max(1, baseRegionSize);

        // 处理负数z坐标
        if (z < 0) {
            regionId = (z + 1) / Math.max(1, baseRegionSize) - 1;
        }

        // 3. 检查缓存
        Integer cachedBiome = regionBiomeCache.get(regionId);
        if (cachedBiome != null) {
            return cachedBiome;
        }

        // 4. 计算区域大小（带随机变化）
        Random sizeRandom = new Random(seed ^ regionId ^ 0x12345678L);
        int actualRegionSize = baseRegionSize;
        if (scaledVariationRange > 0) {
            int offset = sizeRandom.nextInt(scaledVariationRange * 2 + 1) - scaledVariationRange;
            actualRegionSize = Math.max(1, baseRegionSize + offset); // 最小为1
        }

        // 5. 为区域选择生物群系
        Random biomeRandom = new Random(seed ^ regionId);
        int biomeIndex = biomeRandom.nextInt(biomeList.size());

        // 6. 避免与前一个区域相同（如果有多个生物群系）
        if (biomeList.size() > 1) {
            int prevRegionId = regionId - 1;
            Integer prevBiomeIndex = regionBiomeCache.get(prevRegionId);

            if (prevBiomeIndex != null && biomeIndex == prevBiomeIndex) {
                // 切换到不同的群系
                biomeIndex = (biomeIndex + 1) % biomeList.size();
            }
        }

        // 7. 缓存结果
        regionBiomeCache.put(regionId, biomeIndex);

        // 8. 限制缓存大小，避免内存泄漏
        if (regionBiomeCache.size() > 1000) {
            // 删除最早的100个条目
            Iterator<Integer> iterator = regionBiomeCache.keySet().iterator();
            int count = 0;
            while (iterator.hasNext() && count < 100) {
                iterator.next();
                iterator.remove();
                count++;
            }
        }

        return biomeIndex;
    }

    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        return getNoiseBiome(x, y, z, null);
    }

    private Holder<Biome> getDefaultBiome() {
        return biomeList.isEmpty() ? null : biomeList.get(0);
    }

    public HolderSet<Biome> getBiomeHolderSet() {
        return biomeHolderSet;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomeHolderSet != null ? biomeHolderSet.stream() : Stream.empty();
    }

    @Override
    public Set<Holder<Biome>> possibleBiomes() {
        Set<Holder<Biome>> set = new HashSet<>();
        if (biomeHolderSet != null) {
            biomeHolderSet.forEach(set::add);
        }
        return set;
    }
}