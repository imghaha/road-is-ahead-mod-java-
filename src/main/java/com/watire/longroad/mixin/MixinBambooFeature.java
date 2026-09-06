package com.watire.longroad.mixin;

import com.watire.longroad.config.BiomeConfigManager;
import com.watire.longroad.config.RoadType;
import com.watire.longroad.worldset.TerrainChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.BambooFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BambooFeature.class)
public class MixinBambooFeature {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void onPlaceBamboo(FeaturePlaceContext<NoneFeatureConfiguration> context,
                               CallbackInfoReturnable<Boolean> cir) {
        // 仅当道路类型为随机小路时生效
        if (BiomeConfigManager.getRoadType() != RoadType.RANDOM) {
            return;
        }

        ChunkGenerator generator = context.chunkGenerator();
        if (!(generator instanceof TerrainChunkGenerator terrainGen)) {
            return;
        }

        BlockPos origin = context.origin();
        long seed = context.level().getSeed();

        // 判断是否在路边 5 格内（中心距离 ≤ 10）
        if (terrainGen.isNearRandomPath(origin.getX(), origin.getZ(), seed)) {
            cir.setReturnValue(false); // 取消竹子生成
        }
    }
}