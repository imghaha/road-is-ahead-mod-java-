package com.watire.longroad.Items;

import com.watire.longroad.block.ModBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "longroad");

    public static final RegistryObject<Item> RAILING_ITEM = ITEMS.register("railing",
            () -> new BlockItem(ModBlock.RAILING.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> RAILING_TOP_ITEM = ITEMS.register("railing_top",
            () -> new BlockItem(ModBlock.RAILING_TOP.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> WIRE_ITEM = ITEMS.register("wire",
            () -> new BlockItem(ModBlock.WIRE.get(),
                    new Item.Properties()));
}