package com.watire.longroad.client.gui;

import com.watire.longroad.client.gui.biomeconfig.TheConfigForBiomeScreen;
import com.watire.longroad.config.BiomeConfigManager;
import com.watire.longroad.config.RoadType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import java.util.Arrays;
import java.util.List;

public class LongroadWorldPresetScreen extends Screen {
    private final Screen parent;
    private EditBox widthInput;
    private EditBox variationInput;
    private CycleButton<RoadType> roadTypeButton;
    private CycleButton<String> distributionButton;
    private Button doneButton;
    private Button cancelButton;

    private static final List<String> DISTRIBUTION_OPTIONS = Arrays.asList("z_axis_only", "random_scattered");

    private String currentWidthValue = "";
    private String currentVariationValue = "";

    public LongroadWorldPresetScreen(Screen parent) {
        super(Component.translatable("gui.longroad.world_preset.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        BiomeConfigManager.forceRefreshConfig();

        int currentWidth = BiomeConfigManager.getBiomeWidth();
        int currentVariation = BiomeConfigManager.getVariationRange();
        String currentDistribution = BiomeConfigManager.getBiomeDistribution();
        RoadType currentRoad = BiomeConfigManager.getRoadType();
        if (currentRoad == RoadType.STRAIGHT &&
                currentWidth == 1000 &&
                currentVariation == 50 &&
                "z_axis_only".equals(currentDistribution)) {
            currentRoad = RoadType.STRAIGHT;
        }
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int leftPanelX = this.width / 3;      // 左侧面板坐标
        int midPanelX = centerX;               // 中间面板坐标

        this.clearWidgets();

        // ========== 左侧面板：宽度与变化范围 ==========
        this.addRenderableWidget(new StringWidget(leftPanelX - 100, centerY - 72, 100, 20,
                Component.translatable("gui.longroad.biome_width"), this.font));

        this.widthInput = new EditBox(this.font, leftPanelX - 100, centerY - 52, 100, 20,
                Component.translatable("gui.longroad.biome_width"));
        this.currentWidthValue = String.valueOf(currentWidth);
        this.widthInput.setValue(this.currentWidthValue);
        this.widthInput.setResponder(value -> this.currentWidthValue = value);
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

        this.addRenderableWidget(new StringWidget(leftPanelX - 100, centerY - 22, 100, 20,
                Component.translatable("gui.longroad.variation_range"), this.font));

        this.variationInput = new EditBox(this.font, leftPanelX - 100, centerY - 2, 100, 20,
                Component.translatable("gui.longroad.variation_range"));
        this.currentVariationValue = String.valueOf(currentVariation);
        this.variationInput.setValue(this.currentVariationValue);
        this.variationInput.setResponder(value -> this.currentVariationValue = value);
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

        // ========== 中间面板：道路类型与群系分布 ==========
        this.addRenderableWidget(new StringWidget(midPanelX - 50, centerY - 72, 100, 20,
                Component.translatable("gui.longroad.road_type"), this.font));

        // 创建自定义顺序的列表
        List<RoadType> orderedTypes = Arrays.asList(
                RoadType.STRAIGHT,
                RoadType.RANDOM,
                RoadType.NONE
        );

        this.roadTypeButton = CycleButton.<RoadType>builder(
                        (type) -> Component.translatable(type.getTranslationKey())
                )
                .withValues(orderedTypes)
                .withInitialValue(currentRoad)
                .displayOnlyValue()
                .create(midPanelX - 40, centerY - 52, 80, 20, Component.empty());
        this.addRenderableWidget(this.roadTypeButton);

        this.addRenderableWidget(new StringWidget(midPanelX - 50, centerY - 22, 100, 20,
                Component.translatable("gui.longroad.biome_distribution"), this.font));

        this.distributionButton = CycleButton.<String>builder((name) -> {
                    if ("z_axis_only".equals(name))
                        return Component.translatable("gui.longroad.distribution.z_axis_only");
                    else
                        return Component.translatable("gui.longroad.distribution.random_scattered");
                })
                .withValues(DISTRIBUTION_OPTIONS)
                .withInitialValue(currentDistribution)
                .displayOnlyValue()
                .create(midPanelX - 40, centerY - 2, 80, 20, Component.empty());
        this.addRenderableWidget(this.distributionButton);

        // ========== 右侧边缘的新按钮（与道路类型同高、同宽） ==========
        Button biomeconfigButton = Button.builder(
                        Component.translatable("gui.longroad.biome_config_button"),
                        btn -> {
                            Minecraft.getInstance().setScreen(new TheConfigForBiomeScreen(this));
                        }
                )
                .bounds(this.width - 140, centerY - 52, 80, 20)  // 与 roadTypeButton 同高同宽，右侧留边距
                .build();
        this.addRenderableWidget(biomeconfigButton);

        // ========== 底部按钮（居中不变） ==========
        this.doneButton = Button.builder(
                Component.translatable("gui.longroad.done"),
                button -> {
                    saveConfigDirectly();
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(this.parent);
                    }
                }
        ).bounds(centerX - 100, centerY + 58, 200, 20).build();
        this.addRenderableWidget(this.doneButton);

        this.cancelButton = Button.builder(
                Component.translatable("gui.longroad.cancel"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(this.parent);
                    }
                }
        ).bounds(centerX - 100, centerY + 88, 200, 20).build();
        this.addRenderableWidget(this.cancelButton);

        this.setInitialFocus(this.widthInput);
    }

    private void saveConfigDirectly() {
        try {
            int width;
            int variation;
            String distribution = this.distributionButton.getValue();
            RoadType roadType = this.roadTypeButton.getValue();

            if (this.currentWidthValue.isEmpty()) {
                width = 1000;
            } else {
                try {
                    width = Integer.parseInt(this.currentWidthValue);
                    width = Math.max(10, Math.min(10000, width));
                } catch (NumberFormatException e) {
                    width = 1000;
                }
            }

            if (this.currentVariationValue.isEmpty()) {
                variation = 50;
            } else {
                try {
                    variation = Integer.parseInt(this.currentVariationValue);
                    variation = Math.max(1, Math.min(500, variation));
                } catch (NumberFormatException e) {
                    variation = 50;
                }
            }

            BiomeConfigManager.setNewConfig(width, variation, distribution, roadType);

            if (this.parent instanceof CreateWorldScreen || (this.minecraft != null && this.minecraft.screen instanceof CreateWorldScreen)) {
                LongroadWorldPresetButton.forceCheck();
            } else if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    if (this.minecraft.screen instanceof CreateWorldScreen) {
                        LongroadWorldPresetButton.forceCheck();
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("❌ 保存配置时发生错误: " + e.getMessage());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 30, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && this.parent != null) {
            this.minecraft.setScreen(this.parent);
        } else if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
        LongroadWorldPresetButton.forceCheck();
    }

    @Override
    public void tick() {
        if (this.widthInput != null) this.widthInput.tick();
        if (this.variationInput != null) this.variationInput.tick();
    }
}