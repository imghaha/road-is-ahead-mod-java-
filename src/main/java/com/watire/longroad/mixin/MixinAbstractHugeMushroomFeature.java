package com.watire.longroad.mixin;

import com.watire.longroad.config.BiomeConfigManager;
import com.watire.longroad.config.RoadType;
import com.watire.longroad.worldset.TerrainChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHugeMushroomFeature.class)
public class MixinAbstractHugeMushroomFeature {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void onPlaceHugeMushroom(FeaturePlaceContext<HugeMushroomFeatureConfiguration> context,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (BiomeConfigManager.getRoadType() != RoadType.RANDOM) return;
        ChunkGenerator generator = context.chunkGenerator();
        if (!(generator instanceof TerrainChunkGenerator terrainGen)) return;
        BlockPos origin = context.origin();
        long seed = context.level().getSeed();
        if (terrainGen.isNearRandomPath(origin.getX(), origin.getZ(), seed)) {
            cir.setReturnValue(false);
        }
    }
}