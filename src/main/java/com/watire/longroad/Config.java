package com.watire.longroad;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = longroad.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    //static final ForgeConfigSpec SPEC = BUILDER.build();

    // 改为使用延迟初始化
    private static boolean logDirtBlockLoaded = false;
    private static int magicNumberLoaded = 42;
    private static String magicNumberIntroductionLoaded = "The magic number is... ";
    private static Set<Item> itemsLoaded = null;

    public static boolean logDirtBlock() {
        if (!logDirtBlockLoaded) {
            logDirtBlockLoaded = LOG_DIRT_BLOCK.get();
        }
        return logDirtBlockLoaded;
    }

    public static int magicNumber() {
        if (magicNumberLoaded == 42) {
            try {
                magicNumberLoaded = MAGIC_NUMBER.get();
            } catch (Exception e) {
                magicNumberLoaded = 42;
            }
        }
        return magicNumberLoaded;
    }

    public static String magicNumberIntroduction() {
        if (magicNumberIntroductionLoaded.equals("The magic number is... ")) {
            try {
                magicNumberIntroductionLoaded = MAGIC_NUMBER_INTRODUCTION.get();
            } catch (Exception e) {
                magicNumberIntroductionLoaded = "The magic number is... ";
            }
        }
        return magicNumberIntroductionLoaded;
    }

    public static Set<Item> items() {
        if (itemsLoaded == null) {
            try {
                itemsLoaded = ITEM_STRINGS.get().stream()
                        .map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName)))
                        .collect(Collectors.toSet());
            } catch (Exception e) {
                itemsLoaded = Set.of();
            }
        }
        return itemsLoaded;
    }

    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent.Loading event)
    {
        // ❌ 不要在这里调用 .get()！
        // ✅ 改为延迟初始化
        System.out.println("配置加载中...");
    }

    @SubscribeEvent
    static void onReload(final ModConfigEvent.Reloading event)
    {
        // 在重载时更新缓存值
        try {
            logDirtBlockLoaded = LOG_DIRT_BLOCK.get();
            magicNumberLoaded = MAGIC_NUMBER.get();
            magicNumberIntroductionLoaded = MAGIC_NUMBER_INTRODUCTION.get();
            itemsLoaded = ITEM_STRINGS.get().stream()
                    .map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName)))
                    .collect(Collectors.toSet());
            System.out.println("配置已重新加载");
        } catch (Exception e) {
            System.out.println("配置重载失败: " + e.getMessage());
        }
    }

    public static final ForgeConfigSpec.IntValue FORBIDDEN_RANGE;
    public static final ForgeConfigSpec.DoubleValue BASE_PROBABILITY;
    public static final ForgeConfigSpec.DoubleValue MIN_PROBABILITY;
    public static final ForgeConfigSpec.DoubleValue DECAY_START;
    public static final ForgeConfigSpec.DoubleValue DECAY_RATE;
    public static ForgeConfigSpec.IntValue GENERATION_RANGE;
    public static ForgeConfigSpec.IntValue CHECK_INTERVAL;
    public static final ForgeConfigSpec SPEC;
    static {
        BUILDER.push("structure_generation");

        FORBIDDEN_RANGE = BUILDER
                .comment("禁止生成的范围（从X=0开始的距离）")
                .defineInRange("forbiddenRange", 15, 0, 1000);

        BASE_PROBABILITY = BUILDER
                .comment("基础生成概率（0.0到1.0）")
                .defineInRange("baseProbability", 0.8, 0.0, 1.0);

        MIN_PROBABILITY = BUILDER
                .comment("最小生成概率（0.0到1.0）")
                .defineInRange("minProbability", 0.1, 0.0, 1.0);

        DECAY_START = BUILDER
                .comment("衰减起始距离（格）")
                .defineInRange("decayStart", 50.0, 0.0, 1000.0);

        DECAY_RATE = BUILDER
                .comment("衰减率（值越大衰减越慢）")
                .defineInRange("decayRate", 100.0, 1.0, 1000.0);

        GENERATION_RANGE = BUILDER
                .comment("生成范围（以玩家为中心的区块数）")
                .defineInRange("generationRange", 8, 1, 32);

        CHECK_INTERVAL = BUILDER
                .comment("检查间隔（游戏刻，20刻=1秒）")
                .defineInRange("checkInterval", 200, 20, 2000);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }





    public static int getGenerationRange() {
        return GENERATION_RANGE.get();
    }

    public static int getCheckInterval() {
        return CHECK_INTERVAL.get();
    }
    public static int getForbiddenRange() {
        return FORBIDDEN_RANGE.get();
    }

    public static double getBaseProbability() {
        return BASE_PROBABILITY.get();
    }

    public static double getMinProbability() {
        return MIN_PROBABILITY.get();
    }

    public static double getDecayStart() {
        return DECAY_START.get();
    }

    public static double getDecayRate() {
        return DECAY_RATE.get();
    }
}