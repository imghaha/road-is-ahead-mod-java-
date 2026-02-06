package com.watire.longroad.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.watire.longroad.config.BiomeConfigManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LongroadWorldPresetScreen extends Screen {
    private final Screen parent;
    private EditBox widthInput;
    private EditBox variationInput;

    // 添加字段保存当前输入值
    private String currentWidthValue = "";
    private String currentVariationValue = "";

    public LongroadWorldPresetScreen(Screen parent) {
        super(Component.translatable("gui.longroad.world_preset.title"));
        this.parent = parent;

        System.out.println("🔄 LongroadWorldPresetScreen 创建，父屏幕: " +
                (parent != null ? parent.getClass().getSimpleName() : "null"));

        // 在构造函数中打印当前配置状态
        System.out.println("📋 构造函数中检查配置:");
        System.out.println("  当前宽度: " + BiomeConfigManager.getBiomeWidth());
        System.out.println("  当前变化范围: " + BiomeConfigManager.getVariationRange());
    }

    @Override
    protected void init() {
        super.init();

        System.out.println("🔄 LongroadWorldPresetScreen.init() 开始，屏幕尺寸: " + this.width + "x" + this.height);

        // 强制刷新配置，确保获取最新值
        System.out.println("🔄 强制刷新配置管理器缓存");
        BiomeConfigManager.forceRefreshConfig();

        // 从配置管理器获取最新值
        int currentWidth = BiomeConfigManager.getBiomeWidth();
        int currentVariation = BiomeConfigManager.getVariationRange();

        System.out.println("📋 init()中获取的配置:");
        System.out.println("  宽度: " + currentWidth);
        System.out.println("  变化范围: " + currentVariation);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 清除所有现有组件（确保干净的状态）
        this.clearWidgets();

        // 设置Tab顺序组
        this.children().clear();

        // 移除标题StringWidget，我们将在render方法中绘制

        // 群系宽度输入框标签 - 总共上移42像素（之前20+现在22）
        this.addRenderableWidget(
                new StringWidget(
                        centerX - 100, centerY - 72, 200, 20,  // 原来centerY - 30 → centerY - 72
                        Component.translatable("gui.longroad.world_preset.biome_width"),
                        this.font
                )
        );

        this.widthInput = new EditBox(this.font, centerX - 100, centerY - 52, 200, 20,  // 原来centerY - 10 → centerY - 52
                Component.translatable("gui.longroad.world_preset.biome_width"));

        // 每次都从配置管理器获取最新值
        this.currentWidthValue = String.valueOf(currentWidth);
        System.out.println("📊 GUI初始化: 设置宽度输入框值为 " + this.currentWidthValue);
        this.widthInput.setValue(this.currentWidthValue);

        // 监听输入变化
        this.widthInput.setResponder(value -> {
            this.currentWidthValue = value;
            System.out.println("📝 宽度输入变化: " + value);
        });

        this.widthInput.setFilter(s -> {
            if (s.isEmpty()) return true;
            try {
                int val = Integer.parseInt(s);
                return val >= 10 && val <= 10000;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        this.addRenderableWidget(this.widthInput);

        // 变化范围输入框标签 - 总共上移42像素
        this.addRenderableWidget(
                new StringWidget(
                        centerX - 100, centerY - 22, 200, 20,  // 原来centerY + 20 → centerY - 22
                        Component.translatable("gui.longroad.world_preset.variation_range"),
                        this.font
                )
        );

        this.variationInput = new EditBox(this.font, centerX - 100, centerY - 2, 200, 20,  // 原来centerY + 40 → centerY - 2
                Component.translatable("gui.longroad.world_preset.variation_range"));

        // 每次都从配置管理器获取最新值
        this.currentVariationValue = String.valueOf(currentVariation);
        System.out.println("📊 GUI初始化: 设置变化范围输入框值为 " + this.currentVariationValue);
        this.variationInput.setValue(this.currentVariationValue);

        // 监听输入变化
        this.variationInput.setResponder(value -> {
            this.currentVariationValue = value;
            System.out.println("📝 变化范围输入变化: " + value);
        });

        this.variationInput.setFilter(s -> {
            if (s.isEmpty()) return true;
            try {
                int val = Integer.parseInt(s);
                return val >= 1 && val <= 500;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        this.addRenderableWidget(this.variationInput);

        // 说明文本 - 总共上移42像素
        this.addRenderableWidget(
                new StringWidget(
                        centerX - 150, centerY + 28, 300, 40,  // 原来centerY + 70 → centerY + 28
                        Component.translatable("gui.longroad.world_preset.description"),
                        this.font
                )
        );

        // 保存按钮 - 总共上移42像素
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> this.saveAndExit()
        ).bounds(centerX - 100, centerY + 78, 200, 20).build());  // 原来centerY + 120 → centerY + 78

        // 取消按钮 - 总共上移42像素
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                button -> this.onClose()
        ).bounds(centerX - 100, centerY + 108, 200, 20).build());  // 原来centerY + 150 → centerY + 108

        // 设置初始焦点
        this.setInitialFocus(this.widthInput);

        // 打印当前状态
        BiomeConfigManager.printStatus();

        System.out.println("✅ LongroadWorldPresetScreen.init() 完成，添加了 " +
                this.children().size() + " 个组件");
    }

    private void saveAndExit() {
        try {
            System.out.println("\n💾 保存配置 - 开始");
            System.out.println("  输入框内容:");
            System.out.println("    宽度: '" + this.currentWidthValue + "'");
            System.out.println("    变化范围: '" + this.currentVariationValue + "'");

            int width;
            int variation;

            // 解析输入
            if (this.currentWidthValue.isEmpty()) {
                width = 1000;
                System.out.println("  宽度为空，使用默认值 1000");
            } else {
                try {
                    width = Integer.parseInt(this.currentWidthValue);
                    width = Math.max(10, Math.min(10000, width));
                    System.out.println("  解析宽度: " + width);
                } catch (NumberFormatException e) {
                    width = 1000;
                    System.out.println("⚠️  宽度解析失败，使用默认值 1000");
                }
            }

            if (this.currentVariationValue.isEmpty()) {
                variation = 50;
                System.out.println("  变化范围为空，使用默认值 50");
            } else {
                try {
                    variation = Integer.parseInt(this.currentVariationValue);
                    variation = Math.max(1, Math.min(500, variation));
                    System.out.println("  解析变化范围: " + variation);
                } catch (NumberFormatException e) {
                    variation = 50;
                    System.out.println("⚠️  变化范围解析失败，使用默认值 50");
                }
            }

            System.out.println("  最终配置值:");
            System.out.println("    宽度: " + width);
            System.out.println("    变化范围: " + variation);

            // 保存到管理器
            System.out.println("🎯 调用 BiomeConfigManager.setNewConfig()");
            BiomeConfigManager.setNewConfig(width, variation);

            System.out.println("✅ 配置保存完成");

            // 立即验证保存的值
            System.out.println("🔍 验证保存的值:");
            System.out.println("  宽度: " + BiomeConfigManager.getBiomeWidth());
            System.out.println("  变化范围: " + BiomeConfigManager.getVariationRange());

            // 返回到上一界面
            this.onClose();
        } catch (Exception e) {
            System.err.println("❌ 保存配置时发生严重错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染原版背景（泥土背景）
        this.renderBackground(guiGraphics);

        // 调用父类的render方法（这会渲染所有组件）
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 只绘制一个标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 30, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        System.out.println("🔙 LongroadWorldPresetScreen.onClose() 被调用");
        if (this.minecraft != null && this.parent != null) {
            this.minecraft.setScreen(this.parent);
        } else if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void tick() {
        // 确保输入框可以更新
        if (this.widthInput != null) {
            this.widthInput.tick();
        }
        if (this.variationInput != null) {
            this.variationInput.tick();
        }
    }
}