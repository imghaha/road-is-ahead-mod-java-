package com.watire.longroad.sthcheck;

import com.watire.longroad.config.LongRoadBiomeConfig;
import com.watire.longroad.sthcheck.BiomeCheckData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber
public class BiomeCheckHandler {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide) return; // 只在服务端执行

        ServerPlayer player = (ServerPlayer) event.getEntity();
        Level level = player.level();

        BiomeCheckData data = BiomeCheckData.get(level);
        if (data == null || data.hasShownWarning()) return;

        // 从世界中获取群系注册表（此时世界已加载，注册表一定有效）
        Registry<Biome> biomeRegistry = level.registryAccess()
                .registry(Registries.BIOME)
                .orElse(null);

        if (biomeRegistry == null) {
            // 理论上不会发生，但以防万一
            player.sendSystemMessage(Component.literal("§c[LongRoad] 无法获取群系注册表，跳过检查。"));
            data.setShownWarning(true);
            return;
        }

        // 加载配置中的所有群系
        var biomeDataList = LongRoadBiomeConfig.loadBiomeData();
        if (biomeDataList == null || biomeDataList.isEmpty()) {
            data.setShownWarning(true);
            return;
        }

        List<String> invalidBiomes = new ArrayList<>();

        for (var biomeData : biomeDataList) {
            String registryName = biomeData.getRegistryName();
            try {
                ResourceLocation key = new ResourceLocation(registryName);
                // 检查注册表中是否存在该 key
                boolean exists = biomeRegistry.containsKey(key);
                // 输出调试信息（可在正式版注释掉）
                System.out.println("[LongRoad] 检查群系: " + registryName + " -> " + exists);
                if (!exists) {
                    invalidBiomes.add(registryName);
                }
            } catch (Exception e) {
                // 格式错误，如 "minecraft:" 或包含非法字符
                System.out.println("[LongRoad] 群系名称格式错误: " + registryName);
                invalidBiomes.add(registryName + " (格式错误)");
            }
        }

        // 如果有无效群系，发送聊天消息
        if (!invalidBiomes.isEmpty()) {
            Component message = Component.translatable("longroad.biome.warning.title")
                    .withStyle(ChatFormatting.RED);
            for (String name : invalidBiomes) {
                message = Component.literal("")
                        .append(message)
                        .append(Component.literal("\n- " + name).withStyle(ChatFormatting.GRAY));
            }
            player.sendSystemMessage(message);
        } else {
            // 没有无效群系，可以静默处理
            System.out.println("[LongRoad] 所有群系均有效。");
        }

        data.setShownWarning(true);
    }
}