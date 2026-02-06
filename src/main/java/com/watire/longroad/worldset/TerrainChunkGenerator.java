package com.watire.longroad.worldset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.watire.longroad.block.ModBlock;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class TerrainChunkGenerator extends ChunkGenerator {

    public static final Codec<TerrainChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource)
            ).apply(instance, TerrainChunkGenerator::new)
    );

    private static final int BASE_HEIGHT = 62;
    private static final int ROAD_WIDTH = 11;
    private static final int ROAD_LEFT = -5;
    private static final int ROAD_RIGHT = 5;

    // 世界种子缓存
    private long worldSeed = 0L;

    public TerrainChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
                             RandomState randomState, ChunkAccess chunk) {
        // 存储世界种子
        this.worldSeed = region.getSeed();

        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        Registry<Biome> biomeRegistry = region.registryAccess().registryOrThrow(Registries.BIOME);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.getPos().getMinBlockX() + x;
                int worldZ = chunk.getPos().getMinBlockZ() + z;

                // 使用改进的平滑高度计算（考虑区块边界）
                int surfaceHeight = calculateSmoothedHeight(worldX, worldZ, region.getSeed());

                Holder<Biome> biomeHolder = region.getBiome(new BlockPos(worldX, 64, worldZ));
                ResourceLocation biomeId = biomeRegistry.getKey(biomeHolder.value());

                if (biomeId == null) {
                    biomeId = new ResourceLocation("minecraft:plains");
                }

                String biomePath = biomeId.getPath();
                boolean isDesert = biomePath.contains("desert");
                boolean isSnowy = biomePath.contains("snow") || biomePath.contains("snowy");

                boolean isInRoad = isInRoadArea(worldX);

                // 生成地形柱
                if (isInRoad) {
                    generateRoadColumn(chunk, worldX, worldZ, surfaceHeight, region);
                }
                else if (isDesert) {
                    generateDesertColumn(chunk, worldX, worldZ, surfaceHeight, region);
                }
                else if (isSnowy) {
                    generateSnowyPlainsColumn(chunk, worldX, worldZ, surfaceHeight, region);
                } else {
                    generatePlainsColumn(chunk, worldX, worldZ, surfaceHeight, region);
                }
            }
        }

        // 生成路灯
        generateLamps(chunk, region.getSeed());

        // 修复：手动设置高度图
        forceUpdateHeightmaps(chunk);
    }

    // ========== 改进的平滑高度计算 ==========
    private int calculateSmoothedHeight(int worldX, int worldZ, long seed) {
        // 基础高度计算
        double height = calculateBaseHeight(worldX, worldZ, seed);

        // 如果是区块边界，进行额外平滑
        if (isChunkBorder(worldX, worldZ)) {
            height = applyBorderSmoothing(worldX, worldZ, seed, height);
        }

        // 限制高度范围
        int finalHeight = (int)Math.round(height);
        finalHeight = Math.max(BASE_HEIGHT - 2, Math.min(BASE_HEIGHT + 2, finalHeight));

        return finalHeight;
    }

    // ========== 基础高度计算 ==========
    private double calculateBaseHeight(int worldX, int worldZ, long seed) {
        double height = BASE_HEIGHT;

        // 使用更平滑的噪声参数
        double noiseXZ = balancedNoise(worldX * 0.001, worldZ * 0.001, seed); // 更低的频率
        height += noiseXZ * 1.0; // 更小的振幅

        // 更平缓的波动
        height += Math.sin(worldZ * 0.0008) * 0.5; // 更低频率，更小振幅
        height += Math.cos(worldX * 0.001) * 0.3;

        // 更细微的对角线变化
        height += Math.sin((worldX + worldZ) * 0.0005) * 0.2;

        return height;
    }

    // ========== 检查是否是区块边界 ==========
    private boolean isChunkBorder(int worldX, int worldZ) {
        int localX = worldX & 15;
        int localZ = worldZ & 15;
        return localX == 0 || localX == 15 || localZ == 0 || localZ == 15;
    }

    // ========== 应用边界平滑 ==========
    private double applyBorderSmoothing(int worldX, int worldZ, long seed, double currentHeight) {
        // 对区块边界位置，使用双线性插值确保平滑

        // 计算当前区块的坐标
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;

        // 获取相邻区块的对应位置
        double totalHeight = currentHeight;
        int count = 1;

        // 采样周围4个方向的相邻位置
        int[][] offsets = {
                {-1, 0}, {1, 0}, {0, -1}, {0, 1},  // 正交方向
                {-1, -1}, {-1, 1}, {1, -1}, {1, 1} // 对角线方向
        };

        for (int[] offset : offsets) {
            int neighborX = worldX + offset[0];
            int neighborZ = worldZ + offset[1];

            // 计算相邻位置的基础高度
            double neighborHeight = calculateBaseHeight(neighborX, neighborZ, seed);
            totalHeight += neighborHeight;
            count++;
        }

        // 返回平均值
        return totalHeight / count;
    }

    // ========== 平衡噪声函数 ==========
    private double balancedNoise(double x, double z, long seed) {
        return simplePerlin(x * 0.7, z * 1.3, seed);
    }

    // ========== 简单的Perlin噪声 ==========
    private double simplePerlin(double x, double z, long seed) {
        int xi = (int)Math.floor(x);
        int zi = (int)Math.floor(z);

        double xf = x - xi;
        double zf = z - zi;

        double u = smoothStep(xf);
        double v = smoothStep(zf);

        double g00 = gradHash(xi, zi, seed);
        double g10 = gradHash(xi + 1, zi, seed);
        double g01 = gradHash(xi, zi + 1, seed);
        double g11 = gradHash(xi + 1, zi + 1, seed);

        double nx0 = lerp(u, dot(g00, xf, zf), dot(g10, xf - 1, zf));
        double nx1 = lerp(u, dot(g01, xf, zf - 1), dot(g11, xf - 1, zf - 1));

        return lerp(v, nx0, nx1);
    }

    private double smoothStep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private double gradHash(int x, int z, long seed) {
        long hash = (long)x * 374761393L + (long)z * 668265263L + seed;
        hash = (hash ^ (hash >> 33)) * 0xc4ceb9fe1a85ec53L;
        hash = (hash ^ (hash >> 33)) * 0xff51afd7ed558ccdL;
        hash = hash ^ (hash >> 33);

        return ((hash & 0x7FFFFFFF) / 1073741823.5) - 1.0;
    }

    private double dot(double g, double x, double z) {
        return g * (x + z * 0.7);
    }

    private double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    // ========== 强制更新高度图 ==========
    private void forceUpdateHeightmaps(ChunkAccess chunk) {
        // 清除现有高度图数据
        for (Heightmap.Types type : Heightmap.Types.values()) {
            Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(type);
            // 重置高度图
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    heightmap.update(x, chunk.getMinBuildHeight() - 1, z, Blocks.AIR.defaultBlockState());
                }
            }
        }

        // 重新计算高度图
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.getPos().getMinBlockX() + x;
                int worldZ = chunk.getPos().getMinBlockZ() + z;

                // 获取实际地形高度
                int surfaceHeight = calculateSmoothedHeight(worldX, worldZ, worldSeed);

                // 关键修复：高度图应该返回地表上方第一个空气方块的位置
                // 所以我们需要找到从顶部向下的第一个非空气方块
                int heightmapHeight = findTopNonAirBlock(chunk, worldX, worldZ, surfaceHeight + 2);

                // 获取该位置的方块状态
                BlockPos heightmapPos = new BlockPos(worldX, heightmapHeight, worldZ);
                BlockState heightmapState = chunk.getBlockState(heightmapPos);

                if (heightmapState.isAir()) {
                    // 如果是空气，向下找第一个非空气方块
                    for (int y = heightmapHeight - 1; y >= chunk.getMinBuildHeight(); y--) {
                        BlockPos pos = new BlockPos(worldX, y, worldZ);
                        BlockState state = chunk.getBlockState(pos);
                        if (!state.isAir()) {
                            heightmapHeight = y;
                            heightmapState = state;
                            break;
                        }
                    }
                }

                // 更新所有高度图
                if (!heightmapState.isAir()) {
                    chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE)
                            .update(x, heightmapHeight, z, heightmapState);
                    chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG)
                            .update(x, heightmapHeight, z, heightmapState);
                    chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING)
                            .update(x, heightmapHeight, z, heightmapState);
                    chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES)
                            .update(x, heightmapHeight, z, heightmapState);
                    chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR)
                            .update(x, surfaceHeight, z, Blocks.STONE.defaultBlockState());
                    chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG)
                            .update(x, surfaceHeight, z, Blocks.STONE.defaultBlockState());
                }
            }
        }
    }

    // ========== 查找顶部非空气方块 ==========
    private int findTopNonAirBlock(ChunkAccess chunk, int worldX, int worldZ, int startY) {
        // 从startY开始向下找第一个非空气方块
        for (int y = Math.min(startY, chunk.getMaxBuildHeight()); y >= chunk.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(worldX, y, worldZ);
            if (!chunk.getBlockState(pos).isAir()) {
                return y;
            }
        }
        return chunk.getMinBuildHeight();
    }

    // ========== 正确的高度图实现 ==========
    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor level, RandomState randomState) {
        // 使用存储的世界种子
        long seed = this.worldSeed;
        if (seed == 0L && randomState != null) {
            seed = extractSeedFromRandomState(randomState);
        }
        if (seed == 0L) {
            seed = ((long)x * 341873128712L) ^ ((long)z * 132897987541L);
        }

        // 计算高度（使用与地形生成相同的方法）
        int surfaceHeight = calculateSmoothedHeight(x, z, seed);

        // 关键修复：不同的高度图类型需要不同的返回值
        switch (heightmap) {
            case WORLD_SURFACE:
            case WORLD_SURFACE_WG:
                // 返回地表方块上方第一个空气方块的位置
                // 这通常是 surfaceHeight + 1
                return surfaceHeight + 1;

            case MOTION_BLOCKING:
            case MOTION_BLOCKING_NO_LEAVES:
                // 返回运动阻挡方块的位置
                // 对于实体，这通常是地表方块的位置
                return surfaceHeight;

            case OCEAN_FLOOR:
            case OCEAN_FLOOR_WG:
                // 返回海底方块的位置
                return surfaceHeight;

            default:
                return surfaceHeight + 1;
        }
    }

    // ========== 从RandomState提取种子 ==========
    private long extractSeedFromRandomState(RandomState randomState) {
        if (randomState == null) {
            return 123456789L;
        }
        return randomState.hashCode() ^ 0x5DEECE66DL;
    }

    // ========== 道路区域判断 ==========
    private boolean isInRoadArea(int worldX) {
        return worldX >= ROAD_LEFT && worldX <= ROAD_RIGHT;
    }

    // ========== 路灯位置判断 ==========
    private boolean isAtLampPosition_left(int worldX, int worldZ) {
        return worldX == 6;
    }

    private boolean isAtLampPosition_right(int worldX, int worldZ) {
        return worldX == -6;
    }

    // ========== 生成路灯 ==========
    private void generateLamps(ChunkAccess chunk, long worldSeed) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.getPos().getMinBlockX() + x;
                int worldZ = chunk.getPos().getMinBlockZ() + z;

                int surfaceHeight = calculateSmoothedHeight(worldX, worldZ, worldSeed);

                boolean atLamp_left = isAtLampPosition_left(worldX, worldZ);
                boolean atLamp_right = isAtLampPosition_right(worldX, worldZ);

                if (atLamp_left) {
                    generateRoadLamp_left(chunk, worldX, worldZ, surfaceHeight);
                }
                else if (atLamp_right){
                    generateRoadLamp_right(chunk, worldX, worldZ, surfaceHeight);
                }
            }
        }
    }

    // ========== 路灯生成方法 ==========
    private void generateRoadLamp_left(ChunkAccess chunk, int worldX, int worldZ, int surfaceHeight) {
        // 灯柱基座（石头）
        BlockState stone = Blocks.STONE.defaultBlockState();
        chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), stone, false);
        if(worldZ%12==0){
            // 灯柱（从地面+1开始，共5格）
            for (int i = 1; i <= 5; i++) {
                BlockPos lampPos = new BlockPos(worldX, surfaceHeight + i, worldZ);
                chunk.setBlockState(lampPos, ModBlock.RAILING.get().defaultBlockState(), false);
            }

            // 灯柱顶部（地面+6）
            BlockPos topPos = new BlockPos(worldX, surfaceHeight + 6, worldZ);
            chunk.setBlockState(topPos, ModBlock.RAILING_TOP.get().defaultBlockState(), false);
        }
    }

    private void generateRoadLamp_right(ChunkAccess chunk, int worldX, int worldZ, int surfaceHeight) {
        // 灯柱基座（石头）
        BlockState stone = Blocks.STONE.defaultBlockState();
        chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), stone, false);
        if(worldZ%12==0) {
            // 灯柱（从地面+1开始，共5格）
            for (int i = 1; i <= 5; i++) {
                BlockPos lampPos = new BlockPos(worldX, surfaceHeight + i, worldZ);
                chunk.setBlockState(lampPos, ModBlock.RAILING.get().defaultBlockState(), false);
            }

            // 灯柱顶部（有方向，地面+6）
            BlockPos topPos = new BlockPos(worldX, surfaceHeight + 6, worldZ);
            BlockState topState = ModBlock.RAILING_TOP.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
            chunk.setBlockState(topPos, topState, false);
        }
    }

    // ========== 道路生成 ==========
    private void generateRoadColumn(ChunkAccess chunk, int worldX, int worldZ,
                                    int surfaceHeight, WorldGenRegion region) {
        // 道路高度就是地形高度
        int roadHeight = surfaceHeight;
        BlockState black_road = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState white_road = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();

        // 道路表面
        if (worldX == 0) {
            chunk.setBlockState(new BlockPos(worldX, roadHeight, worldZ), white_road, false);
        } else {
            chunk.setBlockState(new BlockPos(worldX, roadHeight, worldZ), black_road, false);
        }

        // 道路基础（表面下3层用石头）
        for (int y = roadHeight - 1; y >= roadHeight - 3; y--) {
            chunk.setBlockState(new BlockPos(worldX, y, worldZ), stone, false);
        }

        // 底部填充石头
        for (int y = roadHeight - 4; y >= region.getMinBuildHeight(); y--) {
            chunk.setBlockState(new BlockPos(worldX, y, worldZ), stone, false);
        }
    }

    // ========== 平原地形 ==========
    private void generatePlainsColumn(ChunkAccess chunk, int worldX, int worldZ,
                                      int surfaceHeight, WorldGenRegion region) {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();

        chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), grass, false);

        for (int y = surfaceHeight - 1; y >= surfaceHeight - 3; y--) {
            chunk.setBlockState(new BlockPos(worldX, y, worldZ), dirt, false);
        }

        for (int y = surfaceHeight - 4; y >= region.getMinBuildHeight(); y--) {
            chunk.setBlockState(new BlockPos(worldX, y, worldZ), stone, false);
        }
    }

    // ========== 沙漠地形 ==========
    private void generateDesertColumn(ChunkAccess chunk, int worldX, int worldZ,
                                      int surfaceHeight, WorldGenRegion region) {
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState sandstone = Blocks.SANDSTONE.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();

        chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), sand, false);

        for (int y = surfaceHeight - 1; y >= surfaceHeight - 3; y--) {
            chunk.setBlockState(new BlockPos(worldX, y, worldZ), sandstone, false);
        }

        for (int y = surfaceHeight - 4; y >= region.getMinBuildHeight(); y--) {
            chunk.setBlockState(new BlockPos(worldX, y, worldZ), stone, false);
        }
    }

    // ========== 雪原地形 ==========
    private void generateSnowyPlainsColumn(ChunkAccess chunk, int worldX, int worldZ,
                                           int surfaceHeight, WorldGenRegion region) {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();

        chunk.setBlockState(new BlockPos(worldX, surfaceHeight, worldZ), grass, false);

        for (int y = surfaceHeight - 1; y >= surfaceHeight - 3; y--) {
            chunk.setBlockState(new BlockPos(worldX, y, worldZ), dirt, false);
        }

        for (int y = surfaceHeight - 4; y >= region.getMinBuildHeight(); y--) {
            chunk.setBlockState(new BlockPos(worldX, y, worldZ), stone, false);
        }
    }

    // ========== getBaseColumn 方法 ==========
    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        // 使用相同的方法计算高度
        long seed = this.worldSeed;
        if (seed == 0L && randomState != null) {
            seed = extractSeedFromRandomState(randomState);
        }
        if (seed == 0L) {
            seed = ((long)x * 341873128712L) ^ ((long)z * 132897987541L);
        }

        int surfaceHeight = calculateSmoothedHeight(x, z, seed);

        int minY = level.getMinBuildHeight();
        int arraySize = Math.max(1, surfaceHeight - minY + 1);
        BlockState[] states = new BlockState[arraySize];

        for (int i = 0; i < states.length; i++) {
            int y = minY + i;
            if (y == surfaceHeight) {
                if (isInRoadArea(x)) {
                    if (x == 0) {
                        states[i] = Blocks.WHITE_CONCRETE.defaultBlockState();
                    } else {
                        states[i] = Blocks.GRAY_CONCRETE.defaultBlockState();
                    }
                } else {
                    states[i] = Blocks.GRASS_BLOCK.defaultBlockState();
                }
            } else if (y >= surfaceHeight - 3 && y < surfaceHeight) {
                if (isInRoadArea(x)) {
                    states[i] = Blocks.STONE.defaultBlockState();
                } else {
                    states[i] = Blocks.DIRT.defaultBlockState();
                }
            } else {
                states[i] = Blocks.STONE.defaultBlockState();
            }
        }
        return new NoiseColumn(minY, states);
    }

    // ========== 其他必要的方法 ==========
    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
                             BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk, GenerationStep.Carving step) {
        // 不生成洞穴
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender,
                                                        RandomState randomState,
                                                        StructureManager structureManager,
                                                        ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos pos) {
        list.add("LongRoad 地形生成器 - 终极修复版");
        list.add("地形：平滑边界 + 正确高度图");

        // 计算高度
        long seed = this.worldSeed;
        if (seed == 0L && randomState != null) {
            seed = extractSeedFromRandomState(randomState);
        }
        if (seed == 0L) {
            seed = ((long)pos.getX() * 341873128712L) ^ ((long)pos.getZ() * 132897987541L);
        }

        int surfaceHeight = calculateSmoothedHeight(pos.getX(), pos.getZ(), seed);

        // 显示当前坐标的高度信息
        list.add("地表方块高度：" + surfaceHeight);
        list.add("WORLD_SURFACE高度图：" + (surfaceHeight + 1));
        list.add("MOTION_BLOCKING高度图：" + surfaceHeight);
        list.add("道路区域：" + (isInRoadArea(pos.getX()) ? "是" : "否"));
        list.add("高度范围：" + (BASE_HEIGHT - 2) + " 到 " + (BASE_HEIGHT + 2));
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // 留空
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        // 返回地表上方的高度
        return getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE, level, null);
    }
}