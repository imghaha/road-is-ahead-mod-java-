package com.watire.longroad.mixin;

import com.watire.longroad.config.BiomeConfigManager;
import com.watire.longroad.config.RoadType;
import com.watire.longroad.worldset.TerrainChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 TreeFeature.place 方法，阻止树木在随机小路旁 5 格内生成。
 */
@Mixin(TreeFeature.class)
public class MixinTreeFeature {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void onPlaceTree(FeaturePlaceContext<TreeConfiguration> context,
                             CallbackInfoReturnable<Boolean> cir) {
        // 仅当道路类型为随机小路时生效
        if (BiomeConfigManager.getRoadType() != RoadType.RANDOM) {
            return;
        }

        // 直接从上下文获取 ChunkGenerator，避免调用不存在的 getGenerator()
        ChunkGenerator generator = context.chunkGenerator();
        if (!(generator instanceof TerrainChunkGenerator terrainGen)) {
            return;
        }

        BlockPos origin = context.origin();
        long seed = context.level().getSeed();

        // 检查该位置是否在随机小路旁 5 格内（需要你在 TerrainChunkGenerator 中实现 isNearRandomPath）
        if (terrainGen.isNearRandomPath(origin.getX(), origin.getZ(), seed)) {
            cir.setReturnValue(false); // 取消树木生成
        }
    }
}