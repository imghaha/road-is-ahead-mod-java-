package com.watire.longroad.mixin;

import com.watire.longroad.config.BiomeConfigManager;
import com.watire.longroad.config.RoadType;
import com.watire.longroad.worldset.TerrainChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SnowAndFreezeFeature.class)
public class SnowAndFreezeFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void removeSnowOnRoad(FeaturePlaceContext<NoneFeatureConfiguration> context,
                                  CallbackInfoReturnable<Boolean> cir) {
        WorldGenLevel level = context.level();
        ChunkGenerator generator = level.getLevel().getChunkSource().getGenerator();

        if (!(generator instanceof TerrainChunkGenerator terrainGenerator)) {
            return;
        }

        BlockPos origin = context.origin();
        long worldSeed = level.getLevel().getSeed();
        RoadType roadType = BiomeConfigManager.getRoadType();

        // 仅处理笔直公路类型
        if (roadType != RoadType.STRAIGHT) {
            return;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                int worldX = origin.getX() + i;
                int worldZ = origin.getZ() + j;

                // 判断是否为公路路面或路灯位置
                boolean isRoad = terrainGenerator.isRoadPavement(worldX, worldZ, worldSeed);
                boolean isLamp = terrainGenerator.isLampPosition(worldX, worldZ, worldSeed);

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ);
                mutablePos.set(worldX, surfaceY, worldZ);
                belowPos.set(worldX, surfaceY - 1, worldZ);

                Biome biome = level.getBiome(mutablePos).value();

                if (isRoad || isLamp) {
                    // 清除地面雪
                    if (level.getBlockState(mutablePos).is(Blocks.SNOW)) {
                        level.setBlock(mutablePos, Blocks.AIR.defaultBlockState(), 2);
                    }
                    // 清除下方方块的 snowy 属性
                    BlockState belowState = level.getBlockState(belowPos);
                    if (belowState.hasProperty(SnowyDirtBlock.SNOWY)) {
                        level.setBlock(belowPos, belowState.setValue(SnowyDirtBlock.SNOWY, false), 2);
                    }

                    // 路灯位置还需清除柱子上方的雪（最多 6 格）
                    if (isLamp) {
                        for (int y = 1; y <= 6; y++) {
                            BlockPos lampPartPos = new BlockPos(worldX, surfaceY + y, worldZ);
                            if (level.getBlockState(lampPartPos).is(Blocks.SNOW)) {
                                level.setBlock(lampPartPos, Blocks.AIR.defaultBlockState(), 2);
                            }
                        }
                    }
                } else {
                    // 非公路区域：正常生成雪和冰
                    if (biome.shouldFreeze(level, belowPos, false)) {
                        if (level.getBlockState(belowPos).is(Blocks.WATER)) {
                            level.setBlock(belowPos, Blocks.ICE.defaultBlockState(), 2);
                        }
                    }
                    if (biome.shouldSnow(level, mutablePos)) {
                        level.setBlock(mutablePos, Blocks.SNOW.defaultBlockState(), 2);
                        BlockState belowState = level.getBlockState(belowPos);
                        if (belowState.hasProperty(SnowyDirtBlock.SNOWY)) {
                            level.setBlock(belowPos, belowState.setValue(SnowyDirtBlock.SNOWY, true), 2);
                        }
                    }
                }
            }
        }

        cir.setReturnValue(true);
    }
}