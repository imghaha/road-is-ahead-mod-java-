package com.watire.longroad.block;

import com.watire.longroad.block.special.RailingBlock;
import com.watire.longroad.block.special.RailingTopBlock;
import com.watire.longroad.block.special.Wire;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlock {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, "longroad");

    // Railing 注册 - 使用自定义类
    public static final RegistryObject<Block> RAILING = BLOCKS.register("railing",
            () -> new RailingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)  // 金属外观
                    .strength(0.5F, 0.0F)      // 中等硬度，高抗爆
                    .sound(SoundType.WOOD)    // 金属声音
                    .noOcclusion()             // 重要！不遮挡光线
                    .requiresCorrectToolForDrops()  // 需要正确工具
            ));
    public static final RegistryObject<Block> RAILING_TOP = BLOCKS.register("railing_top",
            () -> new RailingTopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.5F, 0.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.DESTROY)
            ));
    public static final RegistryObject<Block> WIRE = BLOCKS.register("wire",
            () -> new Wire(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .strength(0.0F, 0.0F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.DESTROY)
            ));
}