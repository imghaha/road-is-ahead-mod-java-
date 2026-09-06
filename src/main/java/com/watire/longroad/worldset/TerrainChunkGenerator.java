package com.watire.longroad.worldset;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.watire.longroad.block.ModBlock;
import com.watire.longroad.config.BiomeConfigManager;
import com.watire.longroad.config.LongRoadBiomeConfig;
import com.watire.longroad.config.RoadType;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class TerrainChunkGenerator extends ChunkGenerator {

    public static final Codec<TerrainChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource)
            ).apply(instance, TerrainChunkGenerator::new)
    );

    private static final int BASE_HEIGHT = 72;
    private static final int WATER_LEVEL = 63;
    private static final int ROAD_RIGHT = 5;
    private static final int LAMP_OFFSET = 6;
    private static final int ROAD_GRID_SPACING = 1000;
    private static final int CELL_SIZE = 3200;
    private static final int CLEAR_HEIGHT = 4;
    private static final int CLEAR_EXPAND = 2; // 路口清理区域向外扩展格数

    private final Map<Long, List<PathCurve>> cellCurves = new ConcurrentHashMap<>();

    private final Cache<Long, Boolean> pathCache = CacheBuilder.newBuilder()
            .maximumSize(20000)
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .build();
    private final Cache<Long, Boolean> nearPathCache = CacheBuilder.newBuilder()
            .maximumSize(20000)
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .build();
    private final Cache<Long, RoadSegment> roadSegmentCache = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .build();

    private static final Map<String, BlockState> BLOCK_CACHE = new ConcurrentHashMap<>();

    // 多层噪声 - 像海浪一样起伏
    private PerlinNoise waveNoise1;   // 大浪 - 长波长
    private PerlinNoise waveNoise2;   // 中浪 - 中等波长
    private PerlinNoise waveNoise3;   // 小浪 - 短波长
    private PerlinNoise detailNoise;  // 细节涟漪
    private PerlinNoise warpNoise;    // 扭曲让波浪更自然
    private PerlinNoise amplitudeNoise; // 幅度调制，决定山区/平原

    public static boolean IS_LONGROAD_WORLD = false;

    private long cachedWorldSeed = 0L;
    private volatile boolean noiseInitialized = false;

    private enum CurveDirection { X, Z }

    private static class PathCurve {
        final CurveDirection direction;
        final int start, end;
        final double base;
        final double amplitude;
        final double frequency;
        final double phase;

        PathCurve(CurveDirection dir, int start, int end, double base, double amp, double freq, double phase) {
            this.direction = dir;
            this.start = start;
            this.end = end;
            this.base = base;
            this.amplitude = amp;
            this.frequency = freq;
            this.phase = phase;
        }

        double getCenter(int primaryCoord) {
            return base + amplitude * Math.sin(primaryCoord * frequency + phase);
        }

        boolean containsPrimary(int coord) {
            return coord >= start && coord <= end;
        }
    }

    private static class RoadSegment {
        final boolean isVertical;
        final int baseCoord;
        final int startCoord;
        final int endCoord;
        final boolean active;

        RoadSegment(boolean isVertical, int baseCoord, int startCoord, int endCoord, boolean active) {
            this.isVertical = isVertical;
            this.baseCoord = baseCoord;
            this.startCoord = startCoord;
            this.endCoord = endCoord;
            this.active = active;
        }
    }

    // ==================== 修复后的 PerlinNoise ====================
    private static class PerlinNoise {
        private final int[] perm;
        private final double freq;

        public PerlinNoise(long seed, double freq) {
            this.freq = freq;
            Random rand = new Random(seed);
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = i;
            for (int i = 255; i > 0; i--) {
                int j = rand.nextInt(i + 1);
                int tmp = p[i];
                p[i] = p[j];
                p[j] = tmp;
            }
            perm = new int[512];
            for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
        }

        private double fade(double t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }

        private double lerp(double a, double b, double t) {
            return a + t * (b - a);
        }

        private double grad(int hash, double x, double y) {
            int h = hash & 7;
            double u = h < 4 ? x : y;
            double v = h < 4 ? y : x;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }

        public double noise(double x, double y) {
            double nx = x * freq;
            double ny = y * freq;
            int xi = (int) Math.floor(nx) & 255;
            int yi = (int) Math.floor(ny) & 255;
            double xf = nx - Math.floor(nx);
            double yf = ny - Math.floor(ny);
            double u = fade(xf);
            double v = fade(yf);

            int aaa = perm[perm[xi] + yi];
            int aba = perm[perm[xi] + yi + 1];
            int baa = perm[perm[xi + 1] + yi];
            int bba = perm[perm[xi + 1] + yi + 1];

            double x1 = lerp(grad(aaa, xf, yf), grad(baa, xf - 1, yf), u);
            double x2 = lerp(grad(aba, xf, yf - 1), grad(bba, xf - 1, yf - 1), u);
            return lerp(x1, x2, v);
        }
    }

    public boolean isValidIntersection(int gridX, int gridZ, long seed) {
        return !(gridX == 0 && gridZ == 0);
    }

    private boolean isInIntersectionPavement(int worldX, int worldZ, long seed) {
        int gridX = Math.round((float)worldX / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        int gridZ = Math.round((float)worldZ / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        return Math.abs(worldX - gridX) <= ROAD_RIGHT && Math.abs(worldZ - gridZ) <= ROAD_RIGHT && isValidIntersection(gridX, gridZ, seed);
    }

    private int getIntersectionBaseHeight(int worldX, int worldZ, long seed) {
        int gridX = Math.round((float)worldX / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        int gridZ = Math.round((float)worldZ / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        int vHeight = getVerticalRoadBaseHeight(gridZ, gridX, seed);
        int hHeight = getHorizontalRoadBaseHeight(gridX, gridZ, seed);
        return (vHeight + hHeight) / 2;
    }

    public RoadSegment getRoadSegment(int worldX, int worldZ, long seed) {
        int nearestXGrid = Math.round((float)worldX / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        if (Math.abs(worldX - nearestXGrid) <= ROAD_RIGHT + 1) {
            RoadSegment verticalSeg = getVerticalRoadSegment(nearestXGrid, worldZ, seed);
            if (verticalSeg.active) return verticalSeg;
            RoadSegment rawVSeg = getVerticalRoadSegmentRaw(nearestXGrid, worldZ, seed);
            if (rawVSeg.active) {
                int nearestZGrid = Math.round((float)worldZ / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
                if (Math.abs(worldZ - nearestZGrid) <= ROAD_RIGHT + 1) {
                    RoadSegment hSeg = getHorizontalRoadSegmentRaw(nearestZGrid, worldX, seed);
                    if (hSeg.active) {
                        return new RoadSegment(true, nearestXGrid, rawVSeg.startCoord, rawVSeg.endCoord, true);
                    }
                }
            }
        }
        int nearestZGrid = Math.round((float)worldZ / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        if (Math.abs(worldZ - nearestZGrid) <= ROAD_RIGHT + 1) {
            RoadSegment horizontalSeg = getHorizontalRoadSegment(nearestZGrid, worldX, seed);
            if (horizontalSeg.active) return horizontalSeg;
            RoadSegment rawHSeg = getHorizontalRoadSegmentRaw(nearestZGrid, worldX, seed);
            if (rawHSeg.active) {
                int nearestXGrid2 = Math.round((float)worldX / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
                if (Math.abs(worldX - nearestXGrid2) <= ROAD_RIGHT + 1) {
                    RoadSegment vSeg = getVerticalRoadSegmentRaw(nearestXGrid2, worldZ, seed);
                    if (vSeg.active){
                        return new RoadSegment(false, nearestZGrid, rawHSeg.startCoord, rawHSeg.endCoord, true);
                    }
                }
            }
        }
        return null;
    }

    private RoadSegment getVerticalRoadSegment(int gridX, int worldZ, long seed) {
        int segmentIndex = Math.floorDiv(worldZ, ROAD_GRID_SPACING);
        int segmentStart = segmentIndex * ROAD_GRID_SPACING;
        int segmentEnd = segmentStart + ROAD_GRID_SPACING;
        long segmentKey = ((long)gridX << 32) ^ ((long)segmentIndex << 16) ^ seed ^ 0x1111L;
        RoadSegment cached = roadSegmentCache.getIfPresent(segmentKey);
        if (cached != null) return cached;
        RoadSegment segment = new RoadSegment(true, gridX, segmentStart, segmentEnd, true);
        roadSegmentCache.put(segmentKey, segment);
        return segment;
    }

    private RoadSegment getHorizontalRoadSegment(int gridZ, int worldX, long seed) {
        int segmentIndex = Math.floorDiv(worldX, ROAD_GRID_SPACING);
        int segmentStart = segmentIndex * ROAD_GRID_SPACING;
        int segmentEnd = segmentStart + ROAD_GRID_SPACING;
        long segmentKey = ((long)gridZ << 32) ^ ((long)segmentIndex << 16) ^ seed ^ 0x2222L;
        RoadSegment cached = roadSegmentCache.getIfPresent(segmentKey);
        if (cached != null) return cached;
        RoadSegment segment = new RoadSegment(false, gridZ, segmentStart, segmentEnd, true);
        roadSegmentCache.put(segmentKey, segment);
        return segment;
    }

    private RoadSegment getVerticalRoadSegmentRaw(int gridX, int worldZ, long seed) {
        int segmentIndex = Math.floorDiv(worldZ, ROAD_GRID_SPACING);
        int segmentStart = segmentIndex * ROAD_GRID_SPACING;
        int segmentEnd = segmentStart + ROAD_GRID_SPACING;
        return new RoadSegment(true, gridX, segmentStart, segmentEnd, true);
    }

    private RoadSegment getHorizontalRoadSegmentRaw(int gridZ, int worldX, long seed) {
        int segmentIndex = Math.floorDiv(worldX, ROAD_GRID_SPACING);
        int segmentStart = segmentIndex * ROAD_GRID_SPACING;
        int segmentEnd = segmentStart + ROAD_GRID_SPACING;
        return new RoadSegment(false, gridZ, segmentStart, segmentEnd, true);
    }

    // ==================== 修改后的地形噪声 ====================
    private int calculateRawHeight(int worldX, int worldZ, long seed) {
        ensureNoiseInitialized(seed);

        // 温和扭曲，只让起伏稍微自然弯曲
        double warpX = warpNoise.noise(worldX, worldZ) * 80.0;
        double warpZ = warpNoise.noise(worldX + 10000, worldZ + 10000) * 80.0;
        double wx = worldX + warpX;
        double wz = worldZ + warpZ;

        // 幅度调制：让有的区域起伏大，有的区域起伏小（但整体依然开阔）
        double ampMod = amplitudeNoise.noise(wx, wz) * 0.5 + 0.5; // 0.5~1.0

        // 多层叠加，振幅大但频率极低
        double h = BASE_HEIGHT;
        h += waveNoise1.noise(wx, wz) * 65.0 * ampMod;        // 主起伏，±65
        h += waveNoise2.noise(wx + 5000, wz + 5000) * 35.0 * ampMod; // 次级，±35
        h += waveNoise3.noise(wx + 10000, wz + 10000) * 18.0;  // 小起伏，±18
        h += detailNoise.noise(wx + 20000, wz + 20000) * 4.0;  // 细节，±4（几乎不影响坡度）

        // 宽松钳制，只防极端值
        if (h > BASE_HEIGHT + 90) h = BASE_HEIGHT + 90;
        if (h < BASE_HEIGHT - 90) h = BASE_HEIGHT - 90;

        return (int) Math.round(h);
    }

    private int getVerticalRoadBaseHeight(int worldZ, int roadBaseX, long seed) {
        int smoothRadius = 12;
        double totalWeight = 0;
        double weightedSum = 0;
        for (int d = -smoothRadius; d <= smoothRadius; d++) {
            int sampleZ = worldZ + d;
            double weight = Math.exp(-(d * d) / 50.0);
            weightedSum += calculateRawHeight(roadBaseX, sampleZ, seed) * weight;
            totalWeight += weight;
        }
        int height = (int) Math.round(weightedSum / totalWeight);
        return Math.max(-20, Math.min(180, height));
    }

    private int getHorizontalRoadBaseHeight(int worldX, int roadBaseZ, long seed) {
        int smoothRadius = 12;
        double totalWeight = 0;
        double weightedSum = 0;
        for (int d = -smoothRadius; d <= smoothRadius; d++) {
            int sampleX = worldX + d;
            double weight = Math.exp(-(d * d) / 50.0);
            weightedSum += calculateRawHeight(sampleX, roadBaseZ, seed) * weight;
            totalWeight += weight;
        }
        int height = (int) Math.round(weightedSum / totalWeight);
        return Math.max(-20, Math.min(180, height));
    }

    private boolean shouldPlaceTopSlabVertical(int worldZ, int roadBaseX, long seed) {
        int curr = getVerticalRoadBaseHeight(worldZ, roadBaseX, seed);
        int next = getVerticalRoadBaseHeight(worldZ + 1, roadBaseX, seed);
        int prev = getVerticalRoadBaseHeight(worldZ - 1, roadBaseX, seed);
        return curr < next || curr < prev;
    }

    private boolean shouldPlaceTopSlabHorizontal(int worldX, int roadBaseZ, long seed) {
        int curr = getHorizontalRoadBaseHeight(worldX, roadBaseZ, seed);
        int next = getHorizontalRoadBaseHeight(worldX + 1, roadBaseZ, seed);
        int prev = getHorizontalRoadBaseHeight(worldX - 1, roadBaseZ, seed);
        return curr < next || curr < prev;
    }

    private List<PathCurve> generateCurvesForCell(int cellX, int cellZ, long seed) {
        Random rand = new Random(seed ^ (long)cellX * 341873128712L ^ (long)cellZ * 132897987541L);
        List<PathCurve> curves = new ArrayList<>();
        int cellMinX = cellX * CELL_SIZE;
        int cellMinZ = cellZ * CELL_SIZE;
        int subDiv = 5;
        int subSize = CELL_SIZE / subDiv;
        if (cellX == 0 && cellZ == 0) {
            boolean isVertical = rand.nextBoolean();
            double amp = rand.nextDouble() * 60 + 20;
            double freq = rand.nextDouble() * 0.01 + 0.005;
            double phase = rand.nextDouble() * 2 * Math.PI;
            if (isVertical) {
                curves.add(new PathCurve(CurveDirection.Z, -CELL_SIZE, CELL_SIZE, -amp * Math.sin(phase), amp, freq, phase));
            } else {
                curves.add(new PathCurve(CurveDirection.X, -CELL_SIZE, CELL_SIZE, -amp * Math.sin(phase), amp, freq, phase));
            }
        }
        for (int si = 0; si < subDiv; si++) {
            for (int sj = 0; sj < subDiv; sj++) {
                if (rand.nextDouble() < 0.75) {
                    CurveDirection dir = rand.nextBoolean() ? CurveDirection.Z : CurveDirection.X;
                    int length = rand.nextInt(1001) + 3200;
                    if (dir == CurveDirection.Z) {
                        int subMinZ = cellMinZ + sj * subSize;
                        int startZ = subMinZ + rand.nextInt(subSize);
                        int endZ = startZ + length;
                        double baseX = cellMinX + si * subSize + rand.nextDouble() * subSize;
                        curves.add(new PathCurve(dir, startZ, endZ, baseX, rand.nextDouble() * 50 + 30, rand.nextDouble() * 0.015 + 0.005, rand.nextDouble() * 2 * Math.PI));
                    } else {
                        int subMinX = cellMinX + si * subSize;
                        int startX = subMinX + rand.nextInt(subSize);
                        int endX = startX + length;
                        double baseZ = cellMinZ + sj * subSize + rand.nextDouble() * subSize;
                        curves.add(new PathCurve(dir, startX, endX, baseZ, rand.nextDouble() * 50 + 30, rand.nextDouble() * 0.015 + 0.005, rand.nextDouble() * 2 * Math.PI));
                    }
                }
            }
        }
        return curves;
    }

    private long getCacheKey(int cellX, int cellZ, long seed) { return seed ^ ((long)cellX << 32) ^ (cellZ & 0xffffffffL); }
    private int getCellX(int worldX) { return Math.floorDiv(worldX, CELL_SIZE); }
    private int getCellZ(int worldZ) { return Math.floorDiv(worldZ, CELL_SIZE); }

    private boolean isOnRandomPath(int worldX, int worldZ, long seed) {
        long cacheKey = ((long)worldX << 32) ^ (worldZ & 0xffffffffL) ^ seed;
        Boolean cached = pathCache.getIfPresent(cacheKey);
        if (cached != null) return cached;
        int cellX = getCellX(worldX);
        int cellZ = getCellZ(worldZ);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int curCellX = cellX + dx;
                int curCellZ = cellZ + dz;
                long key = getCacheKey(curCellX, curCellZ, seed);
                List<PathCurve> curves = cellCurves.get(key);
                if (curves == null) {
                    curves = generateCurvesForCell(curCellX, curCellZ, seed);
                    cellCurves.put(key, curves);
                }
                for (PathCurve curve : curves) {
                    if (curve.direction == CurveDirection.Z && curve.containsPrimary(worldZ)) {
                        if (Math.abs(worldX - Math.round(curve.getCenter(worldZ))) <= 2) {
                            pathCache.put(cacheKey, true);
                            return true;
                        }
                    } else if (curve.direction == CurveDirection.X && curve.containsPrimary(worldX)) {
                        if (Math.abs(worldZ - Math.round(curve.getCenter(worldX))) <= 2) {
                            pathCache.put(cacheKey, true);
                            return true;
                        }
                    }
                }
            }
        }
        pathCache.put(cacheKey, false);
        return false;
    }

    public TerrainChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
        IS_LONGROAD_WORLD = true;
    }

    public TerrainChunkGenerator(BiomeSource biomeSource, long seed) {
        super(biomeSource);
        this.cachedWorldSeed = seed;
        if (biomeSource instanceof CustomBiomeSource) {
            ((CustomBiomeSource) biomeSource).setWorldSeed(seed);
        }
        initNoise(seed);
        noiseInitialized = true;
        IS_LONGROAD_WORLD = true;
    }

    // ==================== 修改后的噪声初始化 ====================
    private void initNoise(long seed) {
        if (seed == 0L) seed = 123456789L;
        // 超长波 → 起伏跨越数百甚至上千格，坡度极缓
        waveNoise1 = new PerlinNoise(seed + 100, 0.0003);   // 周期 ~3333 格
        waveNoise2 = new PerlinNoise(seed + 200, 0.0008);   // 周期 ~1250 格
        waveNoise3 = new PerlinNoise(seed + 300, 0.002);    // 周期 ~500 格
        detailNoise = new PerlinNoise(seed + 400, 0.005);   // 周期 ~200 格（仅微起伏）
        warpNoise = new PerlinNoise(seed + 500, 0.0004);    // 极低扭曲，防止局部陡变
        amplitudeNoise = new PerlinNoise(seed + 600, 0.0002);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() { return CODEC; }

    private long getWorldSeed(WorldGenRegion region, RandomState randomState) {
        long globalSeed = CustomBiomeSource.getGlobalSeed();
        if (globalSeed != 0L) return globalSeed;
        if (region != null) {
            long regionSeed = region.getSeed();
            if (regionSeed != 0L) return regionSeed;
        }
        return 123456789L;
    }

    private void ensureNoiseInitialized(long seed) {
        if (!noiseInitialized && seed != 0L) {
            initNoise(seed);
            noiseInitialized = true;
        }
    }

    private int calculateSmoothedHeight(int worldX, int worldZ, long seed) {
        return calculateRawHeight(worldX, worldZ, seed);
    }

    // ==================== 修改后的道路生成方法 ====================
    private void generateStraightRoadColumn(ChunkAccess chunk, int worldX, int worldZ,
                                            int surfaceHeight, WorldGenRegion region, long seed) {
        BlockState deepslateTile = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState polishedDiorite = Blocks.POLISHED_DIORITE.defaultBlockState();
        BlockState deepslateTileSlab = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
        BlockState polishedDioriteSlab = Blocks.POLISHED_DIORITE_SLAB.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();

        int gridX = Math.round((float)worldX / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        int gridZ = Math.round((float)worldZ / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        boolean isValidInter = isValidIntersection(gridX, gridZ, seed);
        boolean isFullCross = (Math.abs(worldX - gridX) <= ROAD_RIGHT && Math.abs(worldZ - gridZ) <= ROAD_RIGHT) && isValidInter;

        // 路口铺装区域
        boolean insideIntersectionRect = Math.abs(worldX - gridX) <= ROAD_RIGHT && Math.abs(worldZ - gridZ) <= ROAD_RIGHT;
        // 路口清理区域（扩大一圈）
        boolean insideClearRect = Math.abs(worldX - gridX) <= ROAD_RIGHT + CLEAR_EXPAND && Math.abs(worldZ - gridZ) <= ROAD_RIGHT + CLEAR_EXPAND;

        if (isFullCross) {
            int h = getIntersectionBaseHeight(worldX, worldZ, seed);
            h = Math.max(region.getMinBuildHeight() + 1, Math.min(region.getMaxBuildHeight() - 1, h));
            BlockPos surfacePos = new BlockPos(worldX, h, worldZ);
            BlockState existing = chunk.getBlockState(surfacePos);
            if (existing.isAir() || existing.is(Blocks.GRASS_BLOCK) || existing.is(Blocks.DIRT)
                    || existing.is(Blocks.STONE) || existing.is(Blocks.WATER)
                    || existing.is(Blocks.GRASS) || existing.is(Blocks.TALL_GRASS)
                    || existing.is(Blocks.FERN) || existing.is(Blocks.LARGE_FERN)) {
                chunk.setBlockState(surfacePos, deepslateTile, false);
            }
            for (int y = h - 1; y >= h - 3; y--) {
                BlockPos belowPos = new BlockPos(worldX, y, worldZ);
                if (chunk.getBlockState(belowPos).isAir())
                    chunk.setBlockState(belowPos, stone, false);
            }
            for (int y = h - 4; y >= region.getMinBuildHeight(); y--) {
                BlockPos belowPos = new BlockPos(worldX, y, worldZ);
                if (chunk.getBlockState(belowPos).isAir())
                    chunk.setBlockState(belowPos, stone, false);
            }
            // 清理（使用扩大区域）
            if (insideClearRect) {
                for (int y = h + 1; y <= h + CLEAR_HEIGHT; y++) {
                    BlockPos abovePos = new BlockPos(worldX, y, worldZ);
                    BlockState aboveState = chunk.getBlockState(abovePos);
                    if (!aboveState.isAir() && !aboveState.is(Blocks.WATER)
                            && !aboveState.is(ModBlock.RAILING.get())
                            && !aboveState.is(ModBlock.RAILING_TOP.get())) {
                        chunk.setBlockState(abovePos, Blocks.AIR.defaultBlockState(), false);
                    }
                }
            }
            return;
        }

        if (insideIntersectionRect && isValidInter) {
            int actualHeight = getIntersectionBaseHeight(worldX, worldZ, seed);
            actualHeight = Math.max(region.getMinBuildHeight() + 1, Math.min(region.getMaxBuildHeight() - 1, actualHeight));
            BlockPos surfacePos = new BlockPos(worldX, actualHeight, worldZ);
            BlockState existing = chunk.getBlockState(surfacePos);
            if (existing.isAir() || existing.is(Blocks.GRASS_BLOCK) || existing.is(Blocks.DIRT)
                    || existing.is(Blocks.STONE) || existing.is(Blocks.WATER)
                    || existing.is(Blocks.GRASS) || existing.is(Blocks.TALL_GRASS)
                    || existing.is(Blocks.FERN) || existing.is(Blocks.LARGE_FERN)) {
                chunk.setBlockState(surfacePos, deepslateTile, false);
            }
            for (int y = actualHeight - 1; y >= actualHeight - 3; y--) {
                BlockPos belowPos = new BlockPos(worldX, y, worldZ);
                if (chunk.getBlockState(belowPos).isAir())
                    chunk.setBlockState(belowPos, stone, false);
            }
            for (int y = actualHeight - 4; y >= region.getMinBuildHeight(); y--) {
                BlockPos belowPos = new BlockPos(worldX, y, worldZ);
                if (chunk.getBlockState(belowPos).isAir())
                    chunk.setBlockState(belowPos, stone, false);
            }
            // 清理（使用扩大区域）
            if (insideClearRect) {
                for (int y = actualHeight + 1; y <= actualHeight + CLEAR_HEIGHT; y++) {
                    BlockPos abovePos = new BlockPos(worldX, y, worldZ);
                    BlockState aboveState = chunk.getBlockState(abovePos);
                    if (!aboveState.isAir() && !aboveState.is(Blocks.WATER)
                            && !aboveState.is(ModBlock.RAILING.get())
                            && !aboveState.is(ModBlock.RAILING_TOP.get())) {
                        chunk.setBlockState(abovePos, Blocks.AIR.defaultBlockState(), false);
                    }
                }
            }
            return;
        }

        RoadSegment segment = getRoadSegment(worldX, worldZ, seed);
        if (segment == null) return;

        int distFromCenter;
        boolean isVertical = segment.isVertical;
        int baseCoord = segment.baseCoord;

        if (isVertical) distFromCenter = Math.abs(worldX - baseCoord);
        else distFromCenter = Math.abs(worldZ - baseCoord);

        if (distFromCenter > LAMP_OFFSET) return;

        boolean needTopSlab;
        if (isVertical) needTopSlab = shouldPlaceTopSlabVertical(worldZ, baseCoord, seed);
        else needTopSlab = shouldPlaceTopSlabHorizontal(worldX, baseCoord, seed);

        // ===== 路面方块 / 台阶放置 =====
        if (distFromCenter == 0) {
            if (needTopSlab) {
                chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), stone, false);
                chunk.setBlockState(new BlockPos(worldX, surfaceHeight + 1, worldZ),
                        polishedDioriteSlab.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM), false);
            } else {
                chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), polishedDiorite, false);
            }
        } else if (distFromCenter <= ROAD_RIGHT) {
            if (needTopSlab) {
                chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), stone, false);
                chunk.setBlockState(new BlockPos(worldX, surfaceHeight + 1, worldZ),
                        deepslateTileSlab.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM), false);
            } else {
                chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), deepslateTile, false);
            }
        } else if (distFromCenter == LAMP_OFFSET) {
            chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), stone, false);
        }

        // 填充下方石头
        for (int y = surfaceHeight - 1; y >= surfaceHeight - 3; y--) {
            if (chunk.getBlockState(new BlockPos(worldX, y, worldZ)).isAir())
                chunk.setBlockState(new BlockPos(worldX, y, worldZ), stone, false);
        }
        for (int y = surfaceHeight - 4; y >= region.getMinBuildHeight(); y--) {
            if (chunk.getBlockState(new BlockPos(worldX, y, worldZ)).isAir())
                chunk.setBlockState(new BlockPos(worldX, y, worldZ), stone, false);
        }

        // ===== 清空上方 =====
        int clearStartY;
        if (needTopSlab) {
            clearStartY = surfaceHeight + 2;
        } else {
            clearStartY = surfaceHeight + 1;
        }
        for (int y = clearStartY; y <= surfaceHeight + CLEAR_HEIGHT; y++) {
            BlockPos clearPos = new BlockPos(worldX, y, worldZ);
            BlockState clearState = chunk.getBlockState(clearPos);
            if (!clearState.isAir() && !clearState.is(Blocks.WATER)
                    && !clearState.is(ModBlock.RAILING.get())
                    && !clearState.is(ModBlock.RAILING_TOP.get())) {
                chunk.setBlockState(clearPos, Blocks.AIR.defaultBlockState(), false);
            }
        }
    }

    private void processIntersectionEdges(ChunkAccess chunk, long seed) {
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();
        int endX = startX + 15;
        int endZ = startZ + 15;

        int minGridX = ((startX - ROAD_GRID_SPACING) / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        int maxGridX = ((endX + ROAD_GRID_SPACING) / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        int minGridZ = ((startZ - ROAD_GRID_SPACING) / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        int maxGridZ = ((endZ + ROAD_GRID_SPACING) / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;

        BlockState deepslateTile = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState deepslateTileSlab = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState stoneSlab = Blocks.STONE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);

        for (int gridX = minGridX; gridX <= maxGridX; gridX += ROAD_GRID_SPACING) {
            for (int gridZ = minGridZ; gridZ <= maxGridZ; gridZ += ROAD_GRID_SPACING) {
                if (!isValidIntersection(gridX, gridZ, seed)) continue;

                int interH = getIntersectionBaseHeight(gridX, gridZ, seed);

                RoadSegment northSeg = getVerticalRoadSegment(gridX, gridZ - ROAD_GRID_SPACING, seed);
                boolean hasNorth = northSeg.active;
                RoadSegment southSeg = getVerticalRoadSegment(gridX, gridZ, seed);
                boolean hasSouth = southSeg.active;
                RoadSegment westSeg = getHorizontalRoadSegment(gridZ, gridX - ROAD_GRID_SPACING, seed);
                boolean hasWest = westSeg.active;
                RoadSegment eastSeg = getHorizontalRoadSegment(gridZ, gridX, seed);
                boolean hasEast = eastSeg.active;

                if (gridX == -1000 && gridZ == 0) hasEast = false;
                if (gridX == 1000 && gridZ == 0) hasWest = false;

                fillIntersectionPavement(chunk, gridX, gridZ, interH, seed,
                        startX, startZ, endX, endZ,
                        deepslateTile, deepslateTileSlab, stone);

                if (hasNorth) processOneEdge(chunk, gridX, gridZ, interH, seed,
                        startX, startZ, endX, endZ,
                        deepslateTile, deepslateTileSlab, stone, false, false);
                if (hasSouth) processOneEdge(chunk, gridX, gridZ, interH, seed,
                        startX, startZ, endX, endZ,
                        deepslateTile, deepslateTileSlab, stone, true, false);
                if (hasWest) processOneEdge(chunk, gridX, gridZ, interH, seed,
                        startX, startZ, endX, endZ,
                        deepslateTile, deepslateTileSlab, stone, false, true);
                if (hasEast) processOneEdge(chunk, gridX, gridZ, interH, seed,
                        startX, startZ, endX, endZ,
                        deepslateTile, deepslateTileSlab, stone, true, true);

                int outerN = gridZ - ROAD_RIGHT - 1;
                int outerS = gridZ + ROAD_RIGHT + 1;
                int outerW = gridX - ROAD_RIGHT - 1;
                int outerE = gridX + ROAD_RIGHT + 1;

                if (!hasNorth) {
                    for (int x = gridX - ROAD_RIGHT; x <= gridX + ROAD_RIGHT; x++) {
                        if (x < startX || x > endX || outerN < startZ || outerN > endZ) continue;
                        chunk.setBlockState(new BlockPos(x, interH, outerN), stone, false);
                    }
                }
                if (!hasSouth) {
                    for (int x = gridX - ROAD_RIGHT; x <= gridX + ROAD_RIGHT; x++) {
                        if (x < startX || x > endX || outerS < startZ || outerS > endZ) continue;
                        chunk.setBlockState(new BlockPos(x, interH, outerS), stone, false);
                    }
                }
                if (!hasWest) {
                    for (int z = gridZ - ROAD_RIGHT; z <= gridZ + ROAD_RIGHT; z++) {
                        if (z < startZ || z > endZ || outerW < startX || outerW > endX) continue;
                        chunk.setBlockState(new BlockPos(outerW, interH, z), stone, false);
                    }
                }
                if (!hasEast) {
                    for (int z = gridZ - ROAD_RIGHT; z <= gridZ + ROAD_RIGHT; z++) {
                        if (z < startZ || z > endZ || outerE < startX || outerE > endX) continue;
                        chunk.setBlockState(new BlockPos(outerE, interH, z), stone, false);
                    }
                }
            }
        }
    }

    private void processOneEdge(ChunkAccess chunk, int gridX, int gridZ, int interH, long seed,
                                int startX, int startZ, int endX, int endZ,
                                BlockState roadBlock, BlockState roadSlab, BlockState stone,
                                boolean positive, boolean isHorizontal) {
        BlockState stoneSlab = Blocks.STONE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);

        if (isHorizontal) {
            int worldZ = positive ? gridZ + LAMP_OFFSET : gridZ - LAMP_OFFSET;
            if (worldZ < startZ || worldZ > endZ) return;

            int edgeH = getVerticalRoadBaseHeight(worldZ, gridX, seed);
            edgeH = Math.max(-64, Math.min(310, edgeH));
            int diff = edgeH - interH;

            int a = 1;
            for (int x = gridX - LAMP_OFFSET; x <= gridX + LAMP_OFFSET; x++) {
                if (x < startX || x > endX) { a++; continue; }

                BlockState actualSlab = roadSlab;
                if (a == 1 || a == 13) actualSlab = stoneSlab;

                if (diff == 1) {
                    chunk.setBlockState(new BlockPos(x, edgeH, worldZ), actualSlab, false);
                    chunk.setBlockState(new BlockPos(x, interH, worldZ), stone, false);
                    for (int y = interH - 1; y >= interH - 3; y--)
                        chunk.setBlockState(new BlockPos(x, y, worldZ), stone, false);
                } else if (diff == -1) {
                    chunk.setBlockState(new BlockPos(x, interH, worldZ), actualSlab, false);
                    chunk.setBlockState(new BlockPos(x, edgeH, worldZ), stone, false);
                    for (int y = edgeH - 1; y >= edgeH - 3; y--)
                        chunk.setBlockState(new BlockPos(x, y, worldZ), stone, false);
                } else {
                    chunk.setBlockState(new BlockPos(x, interH, worldZ), roadBlock, false);
                    for (int y = interH - 1; y >= interH - 3; y--)
                        chunk.setBlockState(new BlockPos(x, y, worldZ), stone, false);
                }
                a++;
            }
        } else {
            int worldX = positive ? gridX + LAMP_OFFSET : gridX - LAMP_OFFSET;
            if (worldX < startX || worldX > endX) return;

            int edgeH = getHorizontalRoadBaseHeight(worldX, gridZ, seed);
            edgeH = Math.max(-64, Math.min(310, edgeH));
            int diff = edgeH - interH;

            int a = 1;
            for (int z = gridZ - LAMP_OFFSET; z <= gridZ + LAMP_OFFSET; z++) {
                if (z < startZ || z > endZ) { a++; continue; }

                BlockState actualSlab = roadSlab;
                if (a == 1 || a == 13) actualSlab = stoneSlab;

                if (diff == 1) {
                    chunk.setBlockState(new BlockPos(worldX, edgeH, z), actualSlab, false);
                    chunk.setBlockState(new BlockPos(worldX, interH, z), stone, false);
                    for (int y = interH - 1; y >= interH - 3; y--)
                        chunk.setBlockState(new BlockPos(worldX, y, z), stone, false);
                } else if (diff == -1) {
                    chunk.setBlockState(new BlockPos(worldX, interH, z), actualSlab, false);
                    chunk.setBlockState(new BlockPos(worldX, edgeH, z), stone, false);
                    for (int y = edgeH - 1; y >= edgeH - 3; y--)
                        chunk.setBlockState(new BlockPos(worldX, y, z), stone, false);
                } else {
                    chunk.setBlockState(new BlockPos(worldX, interH, z), roadBlock, false);
                    for (int y = interH - 1; y >= interH - 3; y--)
                        chunk.setBlockState(new BlockPos(worldX, y, z), stone, false);
                }
                a++;
            }
        }
    }

    // ==================== 修改后的 fillIntersectionPavement ====================
    private void fillIntersectionPavement(ChunkAccess chunk, int gridX, int gridZ, int interH, long seed,
                                          int startX, int startZ, int endX, int endZ,
                                          BlockState roadBlock, BlockState roadSlab, BlockState stone) {
        for (int x = gridX - ROAD_RIGHT; x <= gridX + ROAD_RIGHT; x++) {
            for (int z = gridZ - ROAD_RIGHT; z <= gridZ + ROAD_RIGHT; z++) {
                if (x < startX || x > endX || z < startZ || z > endZ) continue;

                int localH = getIntersectionBaseHeight(x, z, seed);
                localH = Math.max(-64, Math.min(310, localH));

                BlockPos surfacePos = new BlockPos(x, localH, z);
                BlockState existing = chunk.getBlockState(surfacePos);

                if (existing.isAir() || existing.is(Blocks.GRASS_BLOCK) || existing.is(Blocks.DIRT)
                        || existing.is(Blocks.STONE) || existing.is(Blocks.WATER)
                        || existing.is(Blocks.GRASS) || existing.is(Blocks.TALL_GRASS)
                        || existing.is(Blocks.FERN) || existing.is(Blocks.LARGE_FERN)) {
                    chunk.setBlockState(surfacePos, roadBlock, false);
                }

                for (int y = localH - 1; y >= localH - 3; y--) {
                    BlockPos belowPos = new BlockPos(x, y, z);
                    if (chunk.getBlockState(belowPos).isAir())
                        chunk.setBlockState(belowPos, stone, false);
                }
                for (int y = localH - 4; y >= chunk.getMinBuildHeight(); y--) {
                    BlockPos belowPos = new BlockPos(x, y, z);
                    if (chunk.getBlockState(belowPos).isAir())
                        chunk.setBlockState(belowPos, stone, false);
                }
            }
        }

        // 清空路口上方（扩大 CLEAR_EXPAND 圈）
        for (int x = gridX - ROAD_RIGHT - CLEAR_EXPAND; x <= gridX + ROAD_RIGHT + CLEAR_EXPAND; x++) {
            for (int z = gridZ - ROAD_RIGHT - CLEAR_EXPAND; z <= gridZ + ROAD_RIGHT + CLEAR_EXPAND; z++) {
                if (x < startX || x > endX || z < startZ || z > endZ) continue;
                for (int y = interH + 1; y <= interH + CLEAR_HEIGHT; y++) {
                    BlockPos clearPos = new BlockPos(x, y, z);
                    BlockState clearState = chunk.getBlockState(clearPos);
                    if (!clearState.isAir() && !clearState.is(Blocks.WATER)) {
                        chunk.setBlockState(clearPos, Blocks.AIR.defaultBlockState(), false);
                    }
                }
            }
        }
    }

    private void generateStraightRoadLamp(ChunkAccess chunk, int worldX, int worldZ,
                                          int surfaceHeight, long seed) {
        RoadSegment segment = getRoadSegment(worldX, worldZ, seed);
        if (segment == null) return;

        boolean isVertical = segment.isVertical;
        int baseCoord = segment.baseCoord;

        boolean needTopSlab;
        if (isVertical) needTopSlab = shouldPlaceTopSlabVertical(worldZ, baseCoord, seed);
        else needTopSlab = shouldPlaceTopSlabHorizontal(worldX, baseCoord, seed);

        int roadHeight;
        if (isVertical) roadHeight = getVerticalRoadBaseHeight(worldZ, baseCoord, seed);
        else roadHeight = getHorizontalRoadBaseHeight(worldX, baseCoord, seed);

        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState stoneSlab = Blocks.STONE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);

        int baseY;
        if (needTopSlab) {
            chunk.setBlockState(new BlockPos(worldX, roadHeight + 1, worldZ), stoneSlab, false);
            chunk.setBlockState(new BlockPos(worldX, roadHeight, worldZ), stone, false);
            baseY = roadHeight + 2;
        } else {
            chunk.setBlockState(new BlockPos(worldX, roadHeight, worldZ), stone, false);
            baseY = roadHeight + 1;
        }

        for (int y = baseY - 2; y >= baseY - 5; y--) {
            if (y >= chunk.getMinBuildHeight() && chunk.getBlockState(new BlockPos(worldX, y, worldZ)).isAir())
                chunk.setBlockState(new BlockPos(worldX, y, worldZ), stone, false);
        }

        boolean shouldPlaceLamp;
        if (isVertical) shouldPlaceLamp = (worldZ % 12 == 0);
        else shouldPlaceLamp = (worldX % 12 == 0);

        if (shouldPlaceLamp && !needTopSlab) {
            for (int i = 0; i <= 4; i++) {
                chunk.setBlockState(new BlockPos(worldX, baseY + i, worldZ),
                        ModBlock.RAILING.get().defaultBlockState(), false);
            }
            BlockState topState = ModBlock.RAILING_TOP.get().defaultBlockState();
            if (isVertical) {
                topState = topState.setValue(BlockStateProperties.HORIZONTAL_FACING,
                        worldX < baseCoord ? Direction.NORTH : Direction.SOUTH);
            } else {
                topState = topState.setValue(BlockStateProperties.HORIZONTAL_FACING,
                        worldZ < baseCoord ? Direction.WEST : Direction.EAST);
            }
            chunk.setBlockState(new BlockPos(worldX, baseY + 5, worldZ), topState, false);
        }
    }

    private void generateGridLamps(ChunkAccess chunk, long seed) {
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;

                if (isOnIntersectionEdge(worldX, worldZ, seed)) continue;

                RoadSegment segment = getRoadSegment(worldX, worldZ, seed);
                if (segment != null && segment.active) {
                    if (!segment.isVertical && segment.baseCoord == 0 &&
                            (segment.startCoord == -1000 || segment.startCoord == 0)) continue;

                    int distFromCenter = segment.isVertical ? Math.abs(worldX - segment.baseCoord) : Math.abs(worldZ - segment.baseCoord);

                    if (distFromCenter == LAMP_OFFSET) {
                        int surfaceHeight = getBlendedHeight(worldX, worldZ, seed);
                        surfaceHeight = Math.max(-64, Math.min(310, surfaceHeight));
                        generateStraightRoadLamp(chunk, worldX, worldZ, surfaceHeight, seed);
                    }
                }
            }
        }
    }

    private void generateLampGroundColumn(ChunkAccess chunk, int worldX, int worldZ,
                                          int surfaceHeight, WorldGenRegion region) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), stone, false);
        for (int y = surfaceHeight - 1; y >= surfaceHeight - 4; y--)
            chunk.setBlockState(new BlockPos(worldX, y, worldZ), stone, false);
        for (int y = surfaceHeight - 5; y >= region.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(worldX, y, worldZ);
            if (chunk.getBlockState(pos).isAir())
                chunk.setBlockState(pos, stone, false);
        }
        for (int y = surfaceHeight + 1; y <= surfaceHeight + CLEAR_HEIGHT; y++) {
            BlockPos clearPos = new BlockPos(worldX, y, worldZ);
            BlockState clearState = chunk.getBlockState(clearPos);
            if (!clearState.isAir() && !clearState.is(Blocks.WATER)
                    && !clearState.is(ModBlock.RAILING.get())
                    && !clearState.is(ModBlock.RAILING_TOP.get())) {
                chunk.setBlockState(clearPos, Blocks.AIR.defaultBlockState(), false);
            }
        }
    }

    private boolean isOnIntersectionEdge(int worldX, int worldZ, long seed) {
        int gridX = Math.round((float)worldX / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        int gridZ = Math.round((float)worldZ / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        return Math.abs(worldX - gridX) == LAMP_OFFSET && Math.abs(worldZ - gridZ) <= ROAD_RIGHT;
    }

    private BlockState getBlockFromName(String name, BlockState defaultBlock) {
        if (name == null || name.isEmpty()) return defaultBlock;
        BlockState cached = BLOCK_CACHE.get(name);
        if (cached != null) return cached;
        try {
            ResourceLocation blockId = name.contains(":") ? new ResourceLocation(name) : new ResourceLocation("minecraft", name);
            Block block = BuiltInRegistries.BLOCK.get(blockId);
            if (block != null && block != Blocks.AIR) {
                BlockState state = block.defaultBlockState();
                BLOCK_CACHE.put(name, state);
                return state;
            }
        } catch (Exception e) {}
        BLOCK_CACHE.put(name, defaultBlock);
        return defaultBlock;
    }

    private boolean shouldPlaceMixture(int worldX, int worldZ, long seed, int chanceDenom) {
        long mix = (worldX * 341873128L) ^ (worldZ * 132897987L) ^ seed;
        mix = (mix ^ (mix >>> 30)) * 0xbf58476d1ce4e5b9L;
        mix = (mix ^ (mix >>> 27)) * 0x94d049bb133111ebL;
        mix = mix ^ (mix >>> 31);
        return (int)((mix & Long.MAX_VALUE) % chanceDenom) == 0;
    }

    private void generateTerrainColumn(ChunkAccess chunk, int worldX, int worldZ,
                                       int surfaceHeight, WorldGenRegion region,
                                       LongRoadBiomeConfig.TerrainConfig terrainConfig) {
        BlockState upper = getBlockFromName(terrainConfig.getUpper(), Blocks.GRASS_BLOCK.defaultBlockState());
        BlockState middle = getBlockFromName(terrainConfig.getMiddle(), Blocks.DIRT.defaultBlockState());
        BlockState lower = getBlockFromName(terrainConfig.getLower(), Blocks.STONE.defaultBlockState());
        chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), upper, false);
        for (int y = surfaceHeight - 1; y >= surfaceHeight - 4; y--)
            chunk.setBlockState(new BlockPos(worldX, y, worldZ), middle, false);
        for (int y = surfaceHeight - 5; y >= region.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(worldX, y, worldZ);
            if (chunk.getBlockState(pos).isAir())
                chunk.setBlockState(pos, lower, false);
        }
    }

    private void generateRandomPathColumn(ChunkAccess chunk, int worldX, int worldZ,
                                          int surfaceHeight, WorldGenRegion region,
                                          LongRoadBiomeConfig.PathConfig pathConfig, long seed) {
        Holder<Biome> biomeHolder = region.getBiome(new BlockPos(worldX, 64, worldZ));
        ResourceLocation biomeId = region.registryAccess().registryOrThrow(Registries.BIOME).getKey(biomeHolder.value());
        String registryName = biomeId != null ? biomeId.toString() : "minecraft:plains";
        LongRoadBiomeConfig.TerrainConfig terrainConfig = BiomeConfigManager.getTerrainConfig(registryName);
        BlockState dirt = getBlockFromName(terrainConfig.getMiddle(), Blocks.DIRT.defaultBlockState());
        BlockState stone = getBlockFromName(terrainConfig.getLower(), Blocks.STONE.defaultBlockState());
        BlockState path = getBlockFromName(pathConfig.getPathBlock(), Blocks.DIRT_PATH.defaultBlockState());
        BlockState c1 = getBlockFromName(pathConfig.getConfusion1(), Blocks.COBBLESTONE.defaultBlockState());
        BlockState c2 = getBlockFromName(pathConfig.getConfusion2(), Blocks.GRASS_BLOCK.defaultBlockState());
        if (shouldPlaceMixture(worldX, worldZ, seed, 4))
            chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), c1, false);
        else if (shouldPlaceMixture(worldX, worldZ, seed, 5))
            chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), c2, false);
        else
            chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), path, false);
        for (int y = surfaceHeight - 1; y >= surfaceHeight - 2; y--)
            chunk.setBlockState(new BlockPos(worldX, y, worldZ), dirt, false);
        for (int y = surfaceHeight - 3; y >= region.getMinBuildHeight(); y--)
            chunk.setBlockState(new BlockPos(worldX, y, worldZ), stone, false);
    }

    // ==================== buildSurface ====================
    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
                             RandomState randomState, ChunkAccess chunk) {
        long worldSeed = getWorldSeed(region, randomState);
        if (this.cachedWorldSeed != worldSeed) {
            this.cachedWorldSeed = worldSeed;
            if (biomeSource instanceof CustomBiomeSource) {
                ((CustomBiomeSource) biomeSource).setWorldSeed(worldSeed);
            }
            ensureNoiseInitialized(worldSeed);
        }

        RoadType roadType = BiomeConfigManager.getRoadType();
        Registry<Biome> biomeRegistry = region.registryAccess().registryOrThrow(Registries.BIOME);
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();

        CustomBiomeSource customBiomeSource = null;
        if (biomeSource instanceof CustomBiomeSource) customBiomeSource = (CustomBiomeSource) biomeSource;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;

                Holder<Biome> biomeHolder = region.getBiome(new BlockPos(worldX, 64, worldZ));
                ResourceLocation biomeId = biomeRegistry.getKey(biomeHolder.value());
                String registryName = biomeId != null ? biomeId.toString() : "minecraft:plains";

                LongRoadBiomeConfig.TerrainConfig terrainConfig;
                LongRoadBiomeConfig.PathConfig pathConfig;
                if (customBiomeSource != null) {
                    terrainConfig = customBiomeSource.getTerrainConfig(registryName);
                    pathConfig = customBiomeSource.getPathConfig(registryName);
                } else {
                    terrainConfig = BiomeConfigManager.getTerrainConfig(registryName);
                    pathConfig = BiomeConfigManager.getPathConfig(registryName);
                }

                int realSurfaceHeight = calculateRawHeight(worldX, worldZ, worldSeed);
                realSurfaceHeight = Math.max(region.getMinBuildHeight() + 1, Math.min(region.getMaxBuildHeight() - 1, realSurfaceHeight));

                int roadHeight = getBlendedHeight(worldX, worldZ, worldSeed);
                roadHeight = Math.max(region.getMinBuildHeight() + 1, Math.min(region.getMaxBuildHeight() - 1, roadHeight));

                boolean isInRoad = false;
                boolean isLampPos = false;
                boolean isRandomPath = false;

                if (roadType == RoadType.STRAIGHT) {
                    int gridX = Math.round((float)worldX / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
                    int gridZ = Math.round((float)worldZ / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;

                    boolean onVerticalEdge = Math.abs(worldX - gridX) == LAMP_OFFSET && Math.abs(worldZ - gridZ) <= ROAD_RIGHT;
                    boolean onHorizontalEdge = Math.abs(worldZ - gridZ) == LAMP_OFFSET && Math.abs(worldX - gridX) <= ROAD_RIGHT;
                    boolean onAnyEdge = onVerticalEdge || onHorizontalEdge;
                    boolean insideIntersection = Math.abs(worldX - gridX) <= ROAD_RIGHT && Math.abs(worldZ - gridZ) <= ROAD_RIGHT;

                    boolean bypassEdge = false;
                    if (gridX == 1000 && gridZ == 0 && onHorizontalEdge && worldZ == gridZ - LAMP_OFFSET && Math.abs(worldX - gridX) <= ROAD_RIGHT) {
                        bypassEdge = true;
                    } else if (gridX == -1000 && gridZ == 0 && onHorizontalEdge && worldZ == gridZ + LAMP_OFFSET && Math.abs(worldX - gridX) <= ROAD_RIGHT) {
                        bypassEdge = true;
                    }

                    boolean doRoadLogic = !onAnyEdge || bypassEdge;

                    if (doRoadLogic) {
                        if (insideIntersection) {
                            if (isValidIntersection(gridX, gridZ, worldSeed)) isInRoad = true;
                        } else {
                            int nearestXGrid = Math.round((float)worldX / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
                            int nearestZGrid = Math.round((float)worldZ / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
                            boolean isOnVerticalRoad = Math.abs(worldX - nearestXGrid) <= ROAD_RIGHT + 1;
                            boolean isOnHorizontalRoad = Math.abs(worldZ - nearestZGrid) <= ROAD_RIGHT + 1;

                            if (isOnVerticalRoad) {
                                RoadSegment segment = getVerticalRoadSegment(nearestXGrid, worldZ, worldSeed);
                                if (segment != null && segment.active) {
                                    int distFromCenter = Math.abs(worldX - segment.baseCoord);
                                    isInRoad = distFromCenter <= ROAD_RIGHT;
                                    isLampPos = distFromCenter == LAMP_OFFSET;
                                }
                            } else if (isOnHorizontalRoad) {
                                RoadSegment segment = getHorizontalRoadSegment(nearestZGrid, worldX, worldSeed);
                                if (segment != null && segment.active) {
                                    if (segment.baseCoord == 0 && (segment.startCoord == -1000 || segment.startCoord == 0)) {
                                        isInRoad = false;
                                        isLampPos = false;
                                    } else {
                                        int distFromCenter = Math.abs(worldZ - segment.baseCoord);
                                        isInRoad = distFromCenter <= ROAD_RIGHT;
                                        isLampPos = distFromCenter == LAMP_OFFSET;
                                    }
                                }
                            }
                        }
                    }

                    if (gridX == 0 && gridZ == 0) {
                        if (Math.abs(worldX) <= ROAD_RIGHT && Math.abs(worldZ) <= ROAD_RIGHT + 1) {
                            isInRoad = true;
                            isLampPos = false;
                        } else if (Math.abs(worldX) == LAMP_OFFSET && Math.abs(worldZ) <= ROAD_RIGHT) {
                            isInRoad = false;
                            isLampPos = true;
                        }
                    }
                } else if (roadType == RoadType.RANDOM) {
                    isRandomPath = isOnRandomPath(worldX, worldZ, worldSeed);
                }

                if (roadType != RoadType.NONE && (isInRoad || isLampPos)) {
                    generateTerrainColumn(chunk, worldX, worldZ, realSurfaceHeight, region, terrainConfig);
                } else if (!isRandomPath) {
                    generateTerrainColumn(chunk, worldX, worldZ, realSurfaceHeight, region, terrainConfig);
                }

                if (isInRoad) {
                    generateStraightRoadColumn(chunk, worldX, worldZ, roadHeight, region, worldSeed);
                } else if (isLampPos) {
                    generateLampGroundColumn(chunk, worldX, worldZ, roadHeight, region);
                } else if (isRandomPath) {
                    generateRandomPathColumn(chunk, worldX, worldZ, realSurfaceHeight, region, pathConfig, worldSeed);
                }
            }
        }

        if (roadType == RoadType.STRAIGHT) {
            processIntersectionEdges(chunk, worldSeed);
            generateGridLamps(chunk, worldSeed);
        } else if (roadType == RoadType.RANDOM) {
            generateRandomPathLamps(chunk, worldSeed);
        }

        forceUpdateHeightmaps(chunk, worldSeed);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender,
                                                        RandomState randomState,
                                                        StructureManager structureManager,
                                                        ChunkAccess chunk) {
        long worldSeed = getWorldSeed(null, randomState);
        if (this.cachedWorldSeed != worldSeed) {
            this.cachedWorldSeed = worldSeed;
            if (biomeSource instanceof CustomBiomeSource) {
                ((CustomBiomeSource) biomeSource).setWorldSeed(worldSeed);
            }
            ensureNoiseInitialized(worldSeed);
        }

        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();
        int[] surfaceHeights = new int[256];

        for (int i = 0; i < 256; i++) {
            int worldX = startX + i / 16;
            int worldZ = startZ + i % 16;
            int height = calculateSmoothedHeight(worldX, worldZ, worldSeed);
            surfaceHeights[i] = Math.max(minY + 6, Math.min(maxY - 1, height));
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int sh = surfaceHeights[x * 16 + z];
                    if (y <= minY + 5) {
                        chunk.setBlockState(new BlockPos(startX + x, y, startZ + z), Blocks.BEDROCK.defaultBlockState(), false);
                    } else if (y < sh - 4) {
                        if (chunk.getBlockState(new BlockPos(startX + x, y, startZ + z)).isAir()) {
                            chunk.setBlockState(new BlockPos(startX + x, y, startZ + z), Blocks.STONE.defaultBlockState(), false);
                        }
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    private int getBlendedHeight(int worldX, int worldZ, long seed) {
        int rawHeight = calculateRawHeight(worldX, worldZ, seed);
        rawHeight = Math.max(-64, Math.min(310, rawHeight));

        if (BiomeConfigManager.getRoadType() != RoadType.STRAIGHT) return rawHeight;

        int gridX = Math.round((float)worldX / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        int gridZ = Math.round((float)worldZ / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;

        if (Math.abs(worldX - gridX) <= ROAD_RIGHT && Math.abs(worldZ - gridZ) <= ROAD_RIGHT && isValidIntersection(gridX, gridZ, seed)) {
            return getIntersectionBaseHeight(worldX, worldZ, seed);
        }

        RoadSegment segment = getRoadSegment(worldX, worldZ, seed);
        if (segment != null && segment.active) {
            if (!segment.isVertical && segment.baseCoord == 0 &&
                    (segment.startCoord == -1000 || segment.startCoord == 0)) {
                return rawHeight;
            }

            int distFromCenter = segment.isVertical ? Math.abs(worldX - segment.baseCoord) : Math.abs(worldZ - segment.baseCoord);

            if (distFromCenter <= ROAD_RIGHT || distFromCenter == LAMP_OFFSET) {
                if (segment.isVertical) {
                    int height = getVerticalRoadBaseHeight(worldZ, segment.baseCoord, seed);
                    return Math.max(-64, Math.min(310, height));
                } else {
                    int height = getHorizontalRoadBaseHeight(worldX, segment.baseCoord, seed);
                    return Math.max(-64, Math.min(310, height));
                }
            }
        }
        return rawHeight;
    }

    private void forceUpdateHeightmaps(ChunkAccess chunk, long worldSeed) {
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();

        for (Heightmap.Types type : Heightmap.Types.values()) {
            Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(type);
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                    heightmap.update(x, minY - 1, z, Blocks.AIR.defaultBlockState());
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                int surfaceHeight = Math.max(minY + 6, Math.min(maxY - 1, calculateSmoothedHeight(worldX, worldZ, worldSeed)));
                int topY = surfaceHeight;
                for (int y = surfaceHeight; y >= minY; y--) {
                    if (!chunk.getBlockState(new BlockPos(worldX, y, worldZ)).isAir()) { topY = y; break; }
                }
                BlockState state = chunk.getBlockState(new BlockPos(worldX, topY, worldZ));
                chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE).update(x, topY, z, state);
                chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).update(x, topY, z, state);
                chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING).update(x, topY, z, state);
                chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES).update(x, topY, z, state);
                chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR).update(x, surfaceHeight, z, Blocks.STONE.defaultBlockState());
                chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG).update(x, surfaceHeight, z, Blocks.STONE.defaultBlockState());
            }
        }
    }

    private void generateRandomPathLamps(ChunkAccess chunk, long seed) {
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                int surfaceHeight = calculateSmoothedHeight(worldX, worldZ, seed);
                int cellX = getCellX(worldX);
                int cellZ = getCellZ(worldZ);
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int curCellX = cellX + dx;
                        int curCellZ = cellZ + dz;
                        long key = getCacheKey(curCellX, curCellZ, seed);
                        List<PathCurve> curves = cellCurves.get(key);
                        if (curves == null) {
                            curves = generateCurvesForCell(curCellX, curCellZ, seed);
                            cellCurves.put(key, curves);
                        }
                        for (PathCurve curve : curves) {
                            if (curve.direction == CurveDirection.Z && curve.containsPrimary(worldZ)) {
                                int intCenterX = (int) Math.round(curve.getCenter(worldZ));
                                if (Math.abs(worldX - intCenterX) == 3 && worldZ % 30 == 0) {
                                    Random sectionRand = new Random((long)worldZ * 132897987L ^ seed ^ (long)curve.start);
                                    if (worldX == (sectionRand.nextBoolean() ? intCenterX - 3 : intCenterX + 3) && sectionRand.nextDouble() < 0.2) {
                                        placeRandomPathLamp(chunk, worldX, worldZ, surfaceHeight);
                                    }
                                }
                            } else if (curve.direction == CurveDirection.X && curve.containsPrimary(worldX)) {
                                int intCenterZ = (int) Math.round(curve.getCenter(worldX));
                                if (Math.abs(worldZ - intCenterZ) == 3 && worldX % 30 == 0) {
                                    Random sectionRand = new Random((long)worldX * 341873L ^ seed ^ (long)curve.start);
                                    if (worldZ == (sectionRand.nextBoolean() ? intCenterZ - 3 : intCenterZ + 3) && sectionRand.nextDouble() < 0.2) {
                                        placeRandomPathLamp(chunk, worldX, worldZ, surfaceHeight);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void placeRandomPathLamp(ChunkAccess chunk, int worldX, int worldZ, int surfaceHeight) {
        BlockState pillar = ((worldX + worldZ) & 1) == 0 ? Blocks.OAK_FENCE.defaultBlockState() : Blocks.STONE_BRICK_WALL.defaultBlockState();
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false);
        chunk.setBlockState(new BlockPos(worldX, surfaceHeight + 1, worldZ), pillar, false);
        chunk.setBlockState(new BlockPos(worldX, surfaceHeight + 2, worldZ), lantern, false);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor level, RandomState randomState) {
        long seed = getWorldSeed(null, randomState);
        int height = calculateSmoothedHeight(x, z, seed) + 1;
        return Math.max(level.getMinBuildHeight() + 1, Math.min(level.getMaxBuildHeight() - 1, height));
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        long seed = getWorldSeed(null, randomState);
        int surfaceHeight = calculateSmoothedHeight(x, z, seed);
        int minY = level.getMinBuildHeight();
        BlockState[] states = new BlockState[Math.max(1, surfaceHeight - minY + 1)];
        for (int i = 0; i < states.length; i++) {
            int y = minY + i;
            if (y <= minY + 5) states[i] = Blocks.BEDROCK.defaultBlockState();
            else if (y < surfaceHeight) states[i] = Blocks.STONE.defaultBlockState();
            else states[i] = Blocks.GRASS_BLOCK.defaultBlockState();
        }
        return new NoiseColumn(minY, states);
    }

    public boolean isNearRandomPath(int worldX, int worldZ, long seed) {
        long cacheKey = ((long)worldX << 32) ^ (worldZ & 0xffffffffL) ^ seed;
        Boolean cached = nearPathCache.getIfPresent(cacheKey);
        if (cached != null) return cached;
        int cellX = getCellX(worldX);
        int cellZ = getCellZ(worldZ);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int curCellX = cellX + dx;
                int curCellZ = cellZ + dz;
                long key = getCacheKey(curCellX, curCellZ, seed);
                List<PathCurve> curves = cellCurves.get(key);
                if (curves == null) {
                    curves = generateCurvesForCell(curCellX, curCellZ, seed);
                    cellCurves.put(key, curves);
                }
                for (PathCurve curve : curves) {
                    if (curve.direction == CurveDirection.Z && curve.containsPrimary(worldZ) && Math.abs(worldX - curve.getCenter(worldZ)) <= 10) {
                        nearPathCache.put(cacheKey, true);
                        return true;
                    }
                    if (curve.direction == CurveDirection.X && curve.containsPrimary(worldX) && Math.abs(worldZ - curve.getCenter(worldX)) <= 10) {
                        nearPathCache.put(cacheKey, true);
                        return true;
                    }
                }
            }
        }
        nearPathCache.put(cacheKey, false);
        return false;
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
                             BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk, GenerationStep.Carving step) {}

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos pos) {
        long seed = getWorldSeed(null, randomState);
        int surfaceHeight = calculateSmoothedHeight(pos.getX(), pos.getZ(), seed);
        RoadType roadType = BiomeConfigManager.getRoadType();
        RoadSegment segment = getRoadSegment(pos.getX(), pos.getZ(), seed);
        String roadInfo = "无道路";
        String intersectionInfo = "";
        if (segment != null && segment.active) {
            roadInfo = segment.isVertical ? "垂直(南北)" : "水平(东西)";
            roadInfo += " 坐标:" + segment.baseCoord;
            int gridX = Math.round((float)pos.getX() / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
            int gridZ = Math.round((float)pos.getZ() / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
            boolean isValid = isValidIntersection(gridX, gridZ, seed);
            intersectionInfo = isValid ? " §c[十字路口]" : (isInIntersectionPavement(pos.getX(), pos.getZ(), seed) ? " §6[路口区域]" : " §a[普通]");
        }
        list.add("§6=== LongRoad ===");
        list.add("§a类型: " + (roadType == RoadType.STRAIGHT ? "网格公路" : roadType == RoadType.RANDOM ? "随机小路" : "无道路"));
        list.add("§a当前: " + roadInfo + intersectionInfo);
        list.add("§a高度: " + surfaceHeight);
        list.add("§a坐标: (" + pos.getX() + ", " + pos.getZ() + ")");
    }

    public boolean isOnPath(int x, int z, long seed) {
        RoadType roadType = BiomeConfigManager.getRoadType();
        if (roadType == RoadType.STRAIGHT) {
            int gridX = Math.round((float)x / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
            int gridZ = Math.round((float)z / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
            if (Math.abs(x - gridX) <= ROAD_RIGHT && Math.abs(z - gridZ) <= ROAD_RIGHT && isValidIntersection(gridX, gridZ, seed))
                return true;
            RoadSegment segment = getRoadSegment(x, z, seed);
            return segment != null && segment.active;
        }
        return roadType == RoadType.RANDOM && isOnRandomPath(x, z, seed);
    }

    public boolean isRoadPavement(int worldX, int worldZ, long seed) {
        if (BiomeConfigManager.getRoadType() != RoadType.STRAIGHT) return false;

        // 检查是否在交叉路口铺装区域内
        int gridX = Math.round((float) worldX / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        int gridZ = Math.round((float) worldZ / ROAD_GRID_SPACING) * ROAD_GRID_SPACING;
        if (Math.abs(worldX - gridX) <= ROAD_RIGHT && Math.abs(worldZ - gridZ) <= ROAD_RIGHT
                && isValidIntersection(gridX, gridZ, seed)) {
            return true;
        }

        // 检查是否在普通道路段的路面上
        RoadSegment segment = getRoadSegment(worldX, worldZ, seed);
        if (segment != null && segment.active) {
            int dist = segment.isVertical ? Math.abs(worldX - segment.baseCoord)
                    : Math.abs(worldZ - segment.baseCoord);
            return dist <= ROAD_RIGHT;
        }
        return false;
    }

    // 新增：判断是否为路灯位置（道路两侧相距 LAMP_OFFSET 处）
    public boolean isLampPosition(int worldX, int worldZ, long seed) {
        if (BiomeConfigManager.getRoadType() != RoadType.STRAIGHT) return false;

        RoadSegment segment = getRoadSegment(worldX, worldZ, seed);
        if (segment != null && segment.active) {
            int dist = segment.isVertical ? Math.abs(worldX - segment.baseCoord)
                    : Math.abs(worldZ - segment.baseCoord);
            return dist == LAMP_OFFSET;
        }
        return false;
    }

    @Override public int getSeaLevel() { return WATER_LEVEL; }
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    @Override public int getGenDepth() { return 512; }
    @Override public int getMinY() { return -64; }
    @Override public int getSpawnHeight(LevelHeightAccessor level) {
        return getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE, level, null);
    }
}