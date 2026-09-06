package com.watire.longroad.worldset;

import com.mojang.serialization.Codec;
import com.watire.longroad.longroad;
import com.watire.longroad.worldset.TerrainChunkGenerator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

public class ModWorldGen {
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, "longroad");

    public static final RegistryObject<Codec<? extends ChunkGenerator>> TERRAIN_GENERATOR =
            CHUNK_GENERATORS.register("terrainchunkgenerator", // 这是注册名，必须和数据包引用一致
                    () -> TerrainChunkGenerator.CODEC);
    public static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, longroad.MODID);
    public static final RegistryObject<Codec<? extends BiomeSource>> CUSTOM_BIOME_SOURCE =
                BIOME_SOURCES.register("custom_biome_source", // 名称必须和JSON里的“type”一致
                        () -> CustomBiomeSource.CODEC);


}