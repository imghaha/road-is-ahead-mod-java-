package com.watire.longroad.client.gui;

import com.google.common.collect.ImmutableMap;
import com.watire.longroad.client.gui.LongroadWorldPresetScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.Component; // 需要导入Component
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import java.lang.reflect.Field;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(Dist.CLIENT)
public class LongroadWorldPresetButton {

    // 存储每个屏幕的按钮和状态
    private static final Map<CreateWorldScreen, ButtonData> screenData = new WeakHashMap<>();

    // 添加一个计数器，减少频繁检查
    private static int tickCounter = 0;

    // 存储按钮数据
    private static class ButtonData {
        Button button;
        boolean shouldBeVisible;
        String lastWorldPresetId;
        int x;
        int y;
        int width;
        int height;

        ButtonData(Button button, boolean shouldBeVisible, String lastWorldPresetId, int x, int y, int width, int height) {
            this.button = button;
            this.shouldBeVisible = shouldBeVisible;
            this.lastWorldPresetId = lastWorldPresetId;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        System.out.println("🔄 ScreenEvent.Init 触发: " + event.getScreen().getClass().getSimpleName());

        if (event.getScreen() instanceof CreateWorldScreen createWorldScreen) {
            System.out.println("✅ 检测到 CreateWorldScreen");

            // 清理旧的按钮数据（如果有）
            if (screenData.containsKey(createWorldScreen)) {
                System.out.println("🗑️ 清理旧的按钮数据");
                screenData.remove(createWorldScreen);
            }

            // 添加按钮（但可能不添加到屏幕中）
            addLongroadButton(event, createWorldScreen);

            // 立即检查一次世界类型
            checkAndUpdateButtonState(createWorldScreen);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 每10个tick检查一次（减少性能开销）
        tickCounter++;
        if (tickCounter < 10) return;
        tickCounter = 0;

        // 检查所有打开的CreateWorldScreen
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CreateWorldScreen createWorldScreen) {
            checkAndUpdateButtonState(createWorldScreen);
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        // 在渲染时也检查，确保按钮状态正确
        if (event.getScreen() instanceof CreateWorldScreen createWorldScreen) {
            // 确保按钮数据存在
            ButtonData data = screenData.get(createWorldScreen);
            if (data == null) {
                return;
            }

            // 如果按钮应该显示但不在屏幕上，尝试重新添加
            if (data.shouldBeVisible && !isButtonInScreen(createWorldScreen, data.button)) {
                System.out.println("⚠️ 按钮应该显示但不在屏幕上，尝试重新添加");
                // 这里不能直接添加，因为需要Init事件
            }
        }
    }

    private static void addLongroadButton(ScreenEvent.Init.Post event, CreateWorldScreen screen) {
        try {
            int buttonWidth = 220;
            int buttonHeight = 20;

            // 位置计算
            int x = screen.width / 2 - buttonWidth / 2;
            int y = 180; // 固定位置，避免重叠

            System.out.println("🎯 按钮位置: x=" + x + ", y=" + y);

            // 创建按钮 - 使用翻译键而不是硬编码字符串
            Button button = Button.builder(
                            Component.translatable("gui.longroad.world_preset_button"), // 使用翻译键
                            btn -> {
                                System.out.println("🖱️ 长路设置按钮被点击");
                                try {
                                    System.out.println("🔄 当前屏幕: " + Minecraft.getInstance().screen.getClass().getSimpleName());
                                    System.out.println("🔄 创建 LongroadWorldPresetScreen...");
                                    LongroadWorldPresetScreen newScreen = new LongroadWorldPresetScreen(screen);
                                    System.out.println("🔄 设置新屏幕...");
                                    Minecraft.getInstance().setScreen(newScreen);
                                    System.out.println("✅ 屏幕切换完成，新屏幕: " +
                                            (Minecraft.getInstance().screen != null ?
                                                    Minecraft.getInstance().screen.getClass().getSimpleName() : "null"));
                                } catch (Exception e) {
                                    System.out.println("❌ 打开设置界面失败: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                    )
                    .bounds(x, y, buttonWidth, buttonHeight)
                    .build();
            // 初始设置为不活动
            button.active = false;

            event.addListener(button);

            // 存储按钮数据
            String currentWorldPreset = getWorldPresetId(screen);
            screenData.put(screen, new ButtonData(button, false, currentWorldPreset, x, y, buttonWidth, buttonHeight));

            System.out.println("✅ 按钮添加成功，初始状态: 不活动");

        } catch (Exception e) {
            System.out.println("❌ 添加按钮失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 检查按钮是否在屏幕上
     */
    private static boolean isButtonInScreen(CreateWorldScreen screen, Button button) {
        try {
            List<? extends GuiEventListener> children = screen.children();
            return children.contains(button);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查并更新按钮的状态
     */
    private static void checkAndUpdateButtonState(CreateWorldScreen screen) {
        try {
            // 获取当前世界类型
            String currentWorldPreset = getWorldPresetId(screen);

            // 获取按钮数据
            ButtonData data = screenData.get(screen);
            if (data == null || data.button == null) {
                // 没有按钮数据，不处理
                return;
            }

            // 检查是否为长路世界
            boolean isLongroadWorld = isLongroadWorldType(currentWorldPreset);
            boolean shouldBeVisible = isLongroadWorld;

            // 如果状态需要改变
            if (data.shouldBeVisible != shouldBeVisible ||
                    !isSameWorldPreset(data.lastWorldPresetId, currentWorldPreset)) {

                System.out.println("🔄 更新按钮状态:");
                System.out.println("  之前世界类型: " + data.lastWorldPresetId);
                System.out.println("  当前世界类型: " + currentWorldPreset);
                System.out.println("  之前状态: " + (data.shouldBeVisible ? "显示" : "隐藏"));
                System.out.println("  新的状态: " + (shouldBeVisible ? "显示" : "隐藏"));

                // 更新按钮状态
                updateButtonVisibility(data.button, shouldBeVisible);
                data.shouldBeVisible = shouldBeVisible;
                data.lastWorldPresetId = currentWorldPreset;
            }

        } catch (Exception e) {
            System.out.println("⚠️ 检查按钮状态时出错: " + e.getMessage());
        }
    }

    /**
     * 更新按钮的可见性/可用性
     * 在Minecraft 1.20.1中，我们通过以下方式控制按钮:
     * 1. 设置active属性控制是否可交互
     * 2. 通过alpha值或位置来控制视觉上的可见性
     */
    private static void updateButtonVisibility(Button button, boolean visible) {
        if (button == null) return;

        // 控制是否可交互
        button.active = visible;

        // 在Minecraft中，没有直接的setVisible方法
        // 我们可以通过设置alpha值来模拟隐藏/显示
        // 或者移动按钮位置到屏幕外

        if (!visible) {
            // 方法1: 将按钮移动到屏幕外（简单有效）
            // 注意: 我们需要保存原始位置以便恢复
            // 这个方法需要在ButtonData中保存原始位置

            // 方法2: 设置按钮为透明（需要自定义渲染）
            // 这里我们使用方法1
        } else {
            // 恢复按钮位置
            // 这个方法需要在ButtonData中保存原始位置
        }

        // 我们可以在ButtonData中保存原始位置，然后在这里恢复
        // 但由于我们每次都重新检查，暂时只使用active控制
    }

    /**
     * 检查是否为长路世界类型
     */
    private static boolean isLongroadWorldType(String worldPresetId) {
        if (worldPresetId == null) return false;

        // 调试输出
        System.out.println("🔍 检查世界类型: " + worldPresetId);

        // 检查是否为特定的长路世界类型
        if (worldPresetId.equals("longroad:flat_grass_preset") ||
                worldPresetId.equals("generator.longroad.flat_grass_preset")) {
            return true;
        }

        // 检查是否包含长路标识
        if (worldPresetId.contains("longroad") ||
                worldPresetId.contains("flat_grass") ||
                worldPresetId.contains("terrain")) {
            return true;
        }

        return false;
    }

    /**
     * 比较两个世界类型是否相同
     */
    private static boolean isSameWorldPreset(String preset1, String preset2) {
        if (preset1 == null && preset2 == null) return true;
        if (preset1 == null || preset2 == null) return false;
        return preset1.equals(preset2);
    }

    /**
     * 检查是否使用特定的长路世界类型
     */
    private static boolean isLongroadWorld(CreateWorldScreen screen) {
        try {
            // 获取当前选择的世界预设
            String worldPresetId = getWorldPresetId(screen);
            System.out.println("🌍 当前世界预设ID: " + worldPresetId);

            return isLongroadWorldType(worldPresetId);

        } catch (Exception e) {
            System.out.println("⚠️ 检查世界类型时出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取当前选择的世界预设ID
     * 适用于 Minecraft 1.20.1
     */
    private static String getWorldPresetId(CreateWorldScreen screen) {
        try {
            // 方法1：尝试通过UI状态获取世界预设
            var uiState = screen.getUiState();

            // 在1.20.1中，CreateWorldScreen.UiState有一个worldType字段
            try {
                // 尝试常见的字段名
                String[] possibleFieldNames = {"worldType", "f_101010_", "preset"};

                for (String fieldName : possibleFieldNames) {
                    try {
                        Field worldTypeField = ObfuscationReflectionHelper.findField(
                                uiState.getClass(),
                                fieldName
                        );

                        if (worldTypeField != null) {
                            worldTypeField.setAccessible(true);
                            Object worldType = worldTypeField.get(uiState);

                            if (worldType instanceof ResourceKey) {
                                ResourceKey<?> key = (ResourceKey<?>) worldType;
                                String location = key.location().toString();
                                System.out.println("🔍 通过反射获取世界预设 (" + fieldName + "): " + location);
                                return location;
                            } else if (worldType != null) {
                                System.out.println("🔍 找到字段 " + fieldName + " 但类型不是ResourceKey: " + worldType.getClass().getName());
                            }
                        }
                    } catch (Exception e) {
                        // 继续尝试下一个字段名
                    }
                }
            } catch (Exception e) {
                // 静默失败，尝试其他方法
            }

            // 方法2：尝试从CycleButton中获取当前选择
            String presetFromButton = getWorldPresetFromCycleButton(screen);
            if (presetFromButton != null) {
                return presetFromButton;
            }

            // 方法3：通过生成器类名判断
            if (isLongroadGenerator(screen)) {
                System.out.println("🔍 通过生成器检测到长路世界");
                return "longroad:detected_by_generator";
            }

        } catch (Exception e) {
            System.out.println("❌ 获取世界预设ID时出错: " + e.getMessage());
            e.printStackTrace();
        }

        return "unknown";
    }

    /**
     * 从CycleButton中获取当前选择的世界预设
     */
    private static String getWorldPresetFromCycleButton(CreateWorldScreen screen) {
        try {
            // 修复泛型问题：使用通配符类型
            List<? extends GuiEventListener> children = screen.children();

            for (GuiEventListener child : children) {
                if (child instanceof CycleButton) {
                    CycleButton<?> cycleButton = (CycleButton<?>) child;

                    // 获取当前值
                    Object currentValue = cycleButton.getValue();

                    System.out.println("🔍 CycleButton值类型: " + currentValue.getClass().getName());
                    System.out.println("🔍 CycleButton值: " + currentValue);

                    // 如果是WorldPreset类型
                    if (currentValue instanceof WorldPreset) {
                        // WorldPreset的toString()通常包含其ID
                        String presetStr = currentValue.toString();
                        System.out.println("🔍 WorldPreset字符串: " + presetStr);

                        // 提取ID
                        if (presetStr.contains(":")) {
                            // 格式通常是 "WorldPreset{id=modid:preset_id}"
                            int start = presetStr.indexOf("id=");
                            if (start != -1) {
                                int end = presetStr.indexOf("}", start);
                                if (end != -1) {
                                    String id = presetStr.substring(start + 3, end);
                                    System.out.println("✅ 从WorldPreset提取ID: " + id);
                                    return id;
                                }
                            }
                        }
                    }

                    // 如果是ResourceKey类型
                    if (currentValue instanceof ResourceKey) {
                        ResourceKey<?> key = (ResourceKey<?>) currentValue;
                        String location = key.location().toString();
                        System.out.println("✅ 从CycleButton获取ResourceKey: " + location);
                        return location;
                    }

                    // 尝试通过toString获取更多信息
                    String strValue = currentValue.toString();
                    if (strValue.contains("longroad") || strValue.contains("flat_grass")) {
                        System.out.println("✅ 从字符串中检测到长路标识: " + strValue);
                        return strValue;
                    }
                }
            }

            System.out.println("⚠️ 未找到合适的CycleButton");

        } catch (Exception e) {
            System.out.println("❌ 从CycleButton获取世界预设时出错: " + e.getMessage());
        }

        return null;
    }

    /**
     * 通过生成器类名检测长路世界（备用方法）
     */
    private static boolean isLongroadGenerator(CreateWorldScreen screen) {
        try {
            WorldCreationContext context = screen.getUiState().getSettings();
            WorldDimensions dimensions = context.selectedDimensions();

            // 检查主世界生成器
            LevelStem overworldStem = dimensions.dimensions().get(LevelStem.OVERWORLD);
            if (overworldStem != null) {
                String generatorClassName = overworldStem.generator().getClass().getName();
                System.out.println("🔍 主世界生成器类名: " + generatorClassName);

                // 检查是否包含长路相关标识
                if (generatorClassName.contains("TerrainChunkGenerator") ||
                        generatorClassName.toLowerCase().contains("longroad") ||
                        generatorClassName.toLowerCase().contains("flatgrass") ||
                        generatorClassName.toLowerCase().contains("flat_grass")) {
                    return true;
                }
            }

        } catch (Exception e) {
            // 静默失败
        }

        return false;
    }

    @SubscribeEvent
    public static void onScreenClose(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof CreateWorldScreen screen) {
            screenData.remove(screen);
            System.out.println("🗑️ 清理缓存的按钮数据");
        }
    }
}