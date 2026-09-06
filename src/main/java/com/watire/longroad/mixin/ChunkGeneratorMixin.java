package com.watire.longroad.mixin;

import com.watire.longroad.config.BiomeConfigManager;
import com.watire.longroad.config.RoadType;
import com.watire.longroad.worldset.TerrainChunkGenerator;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
    @Inject(method = "createStructures", at = @At("HEAD"), cancellable = true)
    private void preventStructuresInSpawnArea(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager,
            CallbackInfo ci
    ) {
        ChunkGenerator self = (ChunkGenerator) (Object) this;

        // 1. 检查是否为长路世界
        if (!(self instanceof TerrainChunkGenerator terrainGenerator)) {
            return;
        }

        // 2. 获取道路类型
        RoadType roadType = BiomeConfigManager.getRoadType();
        if (roadType == RoadType.NONE) {
            return;
        }

        ChunkPos pos = chunk.getPos();

        // 3. 根据道路类型执行不同拦截逻辑
        if (roadType == RoadType.STRAIGHT) {
            // 公路：拦截出生点附近区块（原逻辑）
            if (pos.x >= -2 && pos.x <= 2) {
                //System.out.println("[MIXIN] STRAIGHT: blocking structure in chunk " + pos);
                ci.cancel();
            }
        } else if (roadType == RoadType.RANDOM) {
            // 小路：使用公共方法 isOnPath 检查
            // 注意：isOnPath 是公共方法，它内部调用 isOnRandomPath
            long seed = 0L; // 种子会在 terrainGenerator 内部处理

            // 检查区块内是否有任何点在小路上
            for (int dx = 0; dx < 16; dx += 4) {
                for (int dz = 0; dz < 16; dz += 4) {
                    int worldX = pos.getMinBlockX() + dx;
                    int worldZ = pos.getMinBlockZ() + dz;

                    if (terrainGenerator.isOnPath(worldX, worldZ, seed)) {
                        //System.out.println("[MIXIN] RANDOM: blocking structure in chunk " + pos + " (near path)");
                        ci.cancel();
                        return;
                    }
                }
            }
        }
    }
}