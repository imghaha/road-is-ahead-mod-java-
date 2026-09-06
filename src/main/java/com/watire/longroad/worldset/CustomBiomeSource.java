package com.watire.longroad.worldset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.watire.longroad.config.BiomeConfigManager;
import com.watire.longroad.config.LongRoadBiomeConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class CustomBiomeSource extends BiomeSource {

    public static final Codec<CustomBiomeSource> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    RegistryCodecs.homogeneousList(Registries.BIOME)
                            .fieldOf("biomes")
                            .forGetter(CustomBiomeSource::getBiomeHolderSet),
                    Codec.LONG.optionalFieldOf("seed", 0L).forGetter(s -> s.worldSeed)
            ).apply(instance, CustomBiomeSource::new)
    );

    private static final Queue<CustomBiomeSource> pendingSources = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean isLoadingNewWorld = new AtomicBoolean(false);
    private static HolderLookup.RegistryLookup<Biome> globalBiomeLookup;
    private static long globalSeed;

    private final HolderSet<Biome> biomeHolderSet;

    private List<Holder<Biome>> biomes;
    private List<Double> weights;
    private double[] cumulativeWeights;
    private Map<String, LongRoadBiomeConfig.TerrainConfig> terrainConfigMap;
    private Map<String, LongRoadBiomeConfig.PathConfig> pathConfigMap;
    private long worldSeed;
    private boolean initialized = false;
    private final Map<Long, Integer> biomeCache = new ConcurrentHashMap<>();

    private Holder<Biome> tempFallbackBiome;
    private Holder<Biome> registeredFallbackBiome;
    private boolean usingRegisteredBiomes = false;

    public CustomBiomeSource(HolderSet<Biome> biomes, long seed) {
        super();
        this.biomeHolderSet = biomes;
        this.worldSeed = seed;
        this.biomes = new ArrayList<>();
        this.weights = new ArrayList<>();
        this.terrainConfigMap = new HashMap<>();
        this.pathConfigMap = new HashMap<>();
        this.tempFallbackBiome = createTempFallbackBiome();

        if (biomes != null) {
            biomes.forEach(this.biomes::add);
            System.out.println("[LongRoad] CustomBiomeSource 创建，种子: " + seed + "，初始群系数: " + this.biomes.size());
        }

        pendingSources.add(this);
        if (globalBiomeLookup != null) {
            tryInitialize();
        }
    }

    public HolderSet<Biome> getBiomeHolderSet() {
        return biomeHolderSet;
    }

    @Override
    public Set<Holder<Biome>> possibleBiomes() {
        if (usingRegisteredBiomes && !biomes.isEmpty()) {
            return new HashSet<>(biomes);
        }
        if (biomeHolderSet != null) {
            Set<Holder<Biome>> set = new HashSet<>();
            biomeHolderSet.forEach(set::add);
            return set;
        }
        return Set.of(tempFallbackBiome);
    }

    @Override
    public Stream<Holder<Biome>> collectPossibleBiomes() {
        if (usingRegisteredBiomes && !biomes.isEmpty()) {
            return biomes.stream();
        }
        if (biomeHolderSet != null) {
            return biomeHolderSet.stream();
        }
        return Stream.of(tempFallbackBiome);
    }

    private void tryInitialize() {
        if (initialized || usingRegisteredBiomes) return;
        if (globalBiomeLookup != null) {
            initializeWithRegistry(globalBiomeLookup, globalSeed);
        }
    }

    private void initializeWithRegistry(HolderLookup.RegistryLookup<Biome> biomeLookup, long seed) {
        if (usingRegisteredBiomes) return;

        System.out.println("[LongRoad] 开始初始化 (种子: " + seed + ")");

        List<LongRoadBiomeConfig.BiomeData> dataList = LongRoadBiomeConfig.loadBiomeData();
        List<Holder<Biome>> newBiomes = new ArrayList<>();
        List<Double> newWeights = new ArrayList<>();
        Map<String, LongRoadBiomeConfig.TerrainConfig> newTerrainMap = new HashMap<>();
        Map<String, LongRoadBiomeConfig.PathConfig> newPathMap = new HashMap<>();

        ResourceKey<Biome> fallbackKey = ResourceKey.create(Registries.BIOME,
                new ResourceLocation("minecraft", "plains"));
        Optional<Holder.Reference<Biome>> fallbackOptional = biomeLookup.get(fallbackKey);
        if (fallbackOptional.isPresent()) {
            this.registeredFallbackBiome = fallbackOptional.get();
        }

        for (LongRoadBiomeConfig.BiomeData data : dataList) {
            String registryName = data.getRegistryName();
            if (!registryName.contains(":")) {
                registryName = "minecraft:" + registryName;
                System.out.println("[LongRoad] 自动补全命名空间: " + data.getRegistryName() + " -> " + registryName);
            }

            ResourceLocation id = new ResourceLocation(registryName);
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);

            Optional<Holder.Reference<Biome>> biomeHolder = biomeLookup.get(key);
            if (biomeHolder.isPresent()) {
                Holder<Biome> originalBiome = biomeHolder.get();
                newBiomes.add(originalBiome);
                newWeights.add(data.getWeight());
                newTerrainMap.put(registryName, data.getTerrain());
                if (data.getPath() != null) {
                    newPathMap.put(registryName, data.getPath());
                }
                System.out.println("[LongRoad] 加载群系: " + id + " 权重: " + data.getWeight());
            } else {
                System.err.println("[LongRoad] 警告：找不到群系 " + id);
            }
        }

        if (!newBiomes.isEmpty() || registeredFallbackBiome != null) {
            if (newBiomes.isEmpty() && registeredFallbackBiome != null) {
                newBiomes.add(registeredFallbackBiome);
                newWeights.add(1.0);
            }

            this.biomes = newBiomes;
            this.weights = newWeights;
            this.terrainConfigMap = newTerrainMap;
            this.pathConfigMap = newPathMap;

            if (!this.weights.isEmpty()) {
                this.cumulativeWeights = new double[this.weights.size()];
                double sum = 0.0;
                for (int i = 0; i < this.weights.size(); i++) {
                    sum += this.weights.get(i);
                    cumulativeWeights[i] = sum;
                }
                System.out.println("[LongRoad] 总权重: " + sum);
            }

            this.initialized = true;
            this.usingRegisteredBiomes = true;
            this.worldSeed = seed;

            System.out.println("[LongRoad] 初始化完成，共 " + newBiomes.size() + " 个群系");
        }
    }

    private int weightedRandomIndex(Random random) {
        if (cumulativeWeights == null || cumulativeWeights.length == 0) {
            return 0;
        }
        double total = cumulativeWeights[cumulativeWeights.length - 1];
        double r = random.nextDouble() * total;
        for (int i = 0; i < cumulativeWeights.length; i++) {
            if (r < cumulativeWeights[i]) {
                return i;
            }
        }
        return cumulativeWeights.length - 1;
    }

    private Holder<Biome> createTempFallbackBiome() {
        try {
            Biome.BiomeBuilder builder = new Biome.BiomeBuilder();
            builder.temperature(0.8f);
            builder.downfall(0.4f);
            builder.generationSettings(new BiomeGenerationSettings.PlainBuilder().build());
            builder.mobSpawnSettings(new MobSpawnSettings.Builder().build());
            builder.specialEffects(new BiomeSpecialEffects.Builder()
                    .waterColor(4159204)
                    .waterFogColor(329011)
                    .fogColor(12638463)
                    .skyColor(calculateSkyColor(0.8f))
                    .build());
            return Holder.direct(builder.build());
        } catch (Exception e) {
            Biome.BiomeBuilder builder = new Biome.BiomeBuilder();
            builder.temperature(0.8f);
            builder.downfall(0.4f);
            builder.generationSettings(new BiomeGenerationSettings.PlainBuilder().build());
            builder.mobSpawnSettings(new MobSpawnSettings.Builder().build());
            builder.specialEffects(new BiomeSpecialEffects.Builder()
                    .waterColor(4159204)
                    .waterFogColor(329011)
                    .fogColor(12638463)
                    .skyColor(0x7FA0FF)
                    .build());
            return Holder.direct(builder.build());
        }
    }

    private int calculateSkyColor(float temperature) {
        float f = temperature / 3.0F;
        f = Math.max(-1.0F, Math.min(1.0F, f));
        return ((int) Math.round(0x9C - f * 0x1E)) << 16 |
                ((int) Math.round(0xA0 - f * 0x1A)) << 8 |
                0xFF;
    }

    public static void onWorldLoading() {
        System.out.println("[LongRoad] ===== 新世界开始加载 =====");
        isLoadingNewWorld.set(true);
    }

    public static void onNewWorldLoaded(HolderLookup.RegistryLookup<Biome> biomeLookup, long seed) {
        System.out.println("[LongRoad] ===== 新世界加载完成 =====");
        System.out.println("[LongRoad] 种子: " + seed);
        System.out.println("[LongRoad] 当前队列大小: " + pendingSources.size());

        globalBiomeLookup = biomeLookup;
        globalSeed = seed;

        int successCount = 0;
        CustomBiomeSource source;
        while ((source = pendingSources.poll()) != null) {
            source.initializeWithRegistry(biomeLookup, seed);
            successCount++;
        }

        isLoadingNewWorld.set(false);
        System.out.println("[LongRoad] 初始化了 " + successCount + " 个实例");
    }

    public static void onWorldUnload() {
        System.out.println("[LongRoad] 世界卸载");
    }

    public static void reset() {
        System.out.println("[LongRoad] ===== 完全重置 =====");
        pendingSources.clear();
        globalBiomeLookup = null;
        globalSeed = 0;
        isLoadingNewWorld.set(false);
    }

    public void setWorldSeed(long seed) {
        if (this.worldSeed != seed) {
            this.worldSeed = seed;
            this.biomeCache.clear();
            System.out.println("[LongRoad] 更新世界种子: " + seed);
        }
    }

    public LongRoadBiomeConfig.TerrainConfig getTerrainConfig(String registryName) {
        LongRoadBiomeConfig.TerrainConfig config = terrainConfigMap.get(registryName);
        return config != null ? config : new LongRoadBiomeConfig.TerrainConfig("grass_block", "dirt", "stone");
    }

    public LongRoadBiomeConfig.PathConfig getPathConfig(String registryName) {
        LongRoadBiomeConfig.PathConfig pathConfig = pathConfigMap.get(registryName);
        return pathConfig != null ? pathConfig : new LongRoadBiomeConfig.PathConfig("dirt_path", "cobblestone", "grass_block");
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    private int calculateBiomeIndex(int x, int z) {
        int biomeWidth = BiomeConfigManager.getBiomeWidth();
        int variationRange = BiomeConfigManager.getVariationRange();
        String distribution = BiomeConfigManager.getBiomeDistribution();

        if ("random_scattered".equals(distribution)) {
            return calculateGridBiome(x, z, biomeWidth, variationRange);
        } else {
            int scaledBiomeWidth = Math.max(1, biomeWidth / 4);
            int scaledVariationRange = Math.max(0, variationRange / 4);
            if (scaledVariationRange == 0 && variationRange > 0) {
                scaledVariationRange = 1;
            }
            return calculateZAxisBiomeIndex(z, worldSeed, scaledBiomeWidth, scaledVariationRange);
        }
    }

    private int calculateGridBiome(int x, int z, int biomeWidth, int variationRange) {
        int cellSize = Math.max(8, biomeWidth);
        int cellX = Math.floorDiv(x, cellSize);
        int cellZ = Math.floorDiv(z, cellSize);

        double minDistSq = Double.POSITIVE_INFINITY;
        int nearestBiome = 0;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int neighborX = cellX + dx;
                int neighborZ = cellZ + dz;

                long hash = worldSeed ^ (neighborX * 374761393L + neighborZ * 668265263L);
                Random rand = new Random(hash);

                double offsetX = 0.3 + rand.nextDouble() * 0.4;
                double offsetZ = 0.3 + rand.nextDouble() * 0.4;

                double seedX = neighborX * cellSize + offsetX * cellSize;
                double seedZ = neighborZ * cellSize + offsetZ * cellSize;

                int biomeIdx = weightedRandomIndex(rand);

                double dxPos = x - seedX;
                double dzPos = z - seedZ;
                double distSq = dxPos * dxPos + dzPos * dzPos;
                distSq *= (0.9 + rand.nextDouble() * 0.2);

                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    nearestBiome = biomeIdx;
                }
            }
        }
        return nearestBiome;
    }

    private int calculateZAxisBiomeIndex(int z, long seed, int scaledBiomeWidth, int scaledVariationRange) {
        int baseRegionSize = Math.max(1, scaledBiomeWidth);
        int regionId = z / baseRegionSize;
        if (z < 0) {
            regionId = (z + 1) / baseRegionSize - 1;
        }

        long cacheKey = regionId;
        Integer cached = biomeCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Random random = new Random(seed ^ regionId);
        int biomeIndex = weightedRandomIndex(random);

        if (biomes.size() > 1) {
            long prevKey = regionId - 1;
            Integer prevBiome = biomeCache.get(prevKey);
            if (prevBiome != null && prevBiome == biomeIndex) {
                biomeIndex = (biomeIndex + 1) % biomes.size();
            }
        }

        if (biomeCache.size() > 1000) {
            Iterator<Long> iterator = biomeCache.keySet().iterator();
            int removeCount = 100;
            for (int i = 0; i < removeCount && iterator.hasNext(); i++) {
                iterator.next();
                iterator.remove();
            }
        }

        biomeCache.put(cacheKey, biomeIndex);
        return biomeIndex;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        if (usingRegisteredBiomes && initialized && !biomes.isEmpty()) {
            try {
                int biomeIndex = calculateBiomeIndex(x, z);
                biomeIndex = Math.max(0, Math.min(biomeIndex, biomes.size() - 1));
                return biomes.get(biomeIndex);
            } catch (Exception e) {
                System.err.println("[LongRoad] 错误: " + e.getMessage());
            }
        }
        return registeredFallbackBiome != null ? registeredFallbackBiome : tempFallbackBiome;
    }

    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        return getNoiseBiome(x, y, z, null);
    }

    public static long getGlobalSeed() {
        return globalSeed;
    }
}