package com.watire.longroad.mixin;

import net.minecraft.world.level.levelgen.feature.LakeFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import com.watire.longroad.worldset.TerrainChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LakeFeature.class)
public class MixinLakeFeature {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void cancelLavaLake(FeaturePlaceContext<LakeFeature.Configuration> context,
                                CallbackInfoReturnable<Boolean> cir) {
        LakeFeature.Configuration config = context.config();

        // 检查是否是岩浆湖（流体是岩浆）
        if (config.fluid().getState(context.random(), context.origin()).getBlock() == Blocks.LAVA) {
            WorldGenLevel level = context.level();
            //System.out.println("[LongRoad] Cancelling lava lake at " + context.origin());
            cir.setReturnValue(false);
        }
    }
}