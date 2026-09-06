package com.watire.longroad.client.gui.biomeedit;

import com.watire.longroad.client.gui.biomeconfig.TheConfigForBiomeScreen;
import com.watire.longroad.client.gui.pathconfusion.PathConfusionConfigScreen;
import com.watire.longroad.client.gui.terrainzblock.TerrainBlockConfigScreen;
import com.watire.longroad.client.gui.weightedit.BiomeParameterConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BiomeConfigEditScreen extends Screen {
    private final Screen parent;
    private final String registryName;
    private final String displayName;
    private final int editIndex;
    private final BiomeEditCallback callback;

    private EditBox nameEditBox;
    private Button confirmButton;
    private Button cancelButton;
    private Button resetButton;
    private Button terrainButton;
    private Button biomeButton;

    private String currentName;

    public interface BiomeEditCallback {
        void onSave(int index, String newName);  // newName 同时用于 registryName 和 displayName
        void onCancel();
    }

    public BiomeConfigEditScreen(Screen parent, String registryName, String displayName, int editIndex, BiomeEditCallback callback) {
        super(Component.translatable("gui.longroad.biome_config.edit.title"));
        this.parent = parent;
        this.registryName = registryName;
        this.displayName = displayName;
        this.editIndex = editIndex;
        this.callback = callback;
        this.currentName = displayName;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.nameEditBox = new EditBox(this.font, centerX - 100, centerY - 60, 200, 20,
                Component.translatable("gui.longroad.biome_config.editbox.tooltip"));
        this.nameEditBox.setMaxLength(64);
        this.nameEditBox.setValue(displayName);
        this.nameEditBox.setResponder(value -> this.currentName = value);
        this.addRenderableWidget(nameEditBox);

        int midButtonY = centerY - 20;
        int buttonWidth = 60;
        int spacing = 20;
        int totalWidth = buttonWidth * 3 + spacing * 2;
        int startX = centerX - totalWidth / 2;

        this.terrainButton = Button.builder(
                Component.translatable("gui.longroad.biome_config.terrain_button"),
                btn -> {
                    if (minecraft != null) {
                        TheConfigForBiomeScreen parentScreen = (TheConfigForBiomeScreen) parent;
                        TheConfigForBiomeScreen.TerrainConfig config = parentScreen.getTerrainConfig(registryName);
                        minecraft.setScreen(new TerrainBlockConfigScreen(
                                this,
                                registryName,
                                config,
                                newConfig -> parentScreen.updateTerrainConfig(registryName, newConfig)
                        ));
                    }
                }
        ).bounds(startX, midButtonY, buttonWidth, 20).build();
        this.terrainButton.setTooltip(Tooltip.create(Component.translatable("gui.longroad.biome_config.tooltip.terrain")));
        this.addRenderableWidget(terrainButton);

        // 权重按钮：获取 double 权重，保存时解析为 double
        this.biomeButton = Button.builder(
                Component.translatable("gui.longroad.biome_config.biome_button"),
                btn -> {
                    if (minecraft != null) {
                        double weight = ((TheConfigForBiomeScreen) parent).getWeight(registryName);
                        minecraft.setScreen(new BiomeParameterConfigScreen(
                                this,
                                registryName,
                                weight,
                                new BiomeParameterConfigScreen.ParameterSaveCallback() {
                                    @Override
                                    public void onSave(String value) {
                                        try {
                                            double w = Double.parseDouble(value.trim());
                                            updateWeight(registryName, w);   // 调用内部方法
                                        } catch (NumberFormatException e) {}
                                    }
                                    @Override
                                    public void onCancel() {}
                                }
                        ));
                    }
                }
        ).bounds(startX + buttonWidth + spacing, midButtonY, buttonWidth, 20).build();
        this.biomeButton.setTooltip(Tooltip.create(Component.translatable("gui.longroad.biome_config.tooltip.biome")));
        this.addRenderableWidget(biomeButton);

        Button pathButton = Button.builder(
                Component.translatable("gui.longroad.biome_config.path_button"),
                btn -> {
                    if (minecraft != null) {
                        TheConfigForBiomeScreen parentScreen = (TheConfigForBiomeScreen) parent;
                        PathConfusionConfigScreen.PathConfig config = parentScreen.getPathConfig(registryName);
                        minecraft.setScreen(new PathConfusionConfigScreen(
                                this,
                                registryName,
                                config,
                                newConfig -> parentScreen.updatePathConfig(registryName, newConfig)
                        ));
                    }
                }
        ).bounds(startX + (buttonWidth + spacing) * 2, midButtonY, buttonWidth, 20).build();
        pathButton.setTooltip(Tooltip.create(Component.translatable("gui.longroad.biome_config.tooltip.path")));
        this.addRenderableWidget(pathButton);

        int bottomButtonY = centerY + 20;
        int bottomSpacing = 10;
        int bottomTotalWidth = buttonWidth * 3 + bottomSpacing * 2;
        int bottomStartX = centerX - bottomTotalWidth / 2;

        this.resetButton = Button.builder(
                Component.translatable("gui.longroad.biome_config.reset"),
                btn -> {
                    this.currentName = displayName;
                    this.nameEditBox.setValue(displayName);
                }
        ).bounds(bottomStartX, bottomButtonY, buttonWidth, 20).build();
        this.addRenderableWidget(resetButton);

        this.confirmButton = Button.builder(
                Component.translatable("gui.longroad.biome_config.confirm"),
                btn -> {
                    String newName = currentName.trim();
                    if (newName.isEmpty()) {
                        newName = Component.translatable("gui.longroad.biome.unknown").getString();
                    }
                    callback.onSave(editIndex, newName);  // 传入 newName
                    if (minecraft != null) {
                        minecraft.setScreen(parent);
                    }
                }
        ).bounds(bottomStartX + buttonWidth + bottomSpacing, bottomButtonY, buttonWidth, 20).build();
        this.addRenderableWidget(confirmButton);

        this.cancelButton = Button.builder(
                Component.translatable("gui.longroad.cancel"),
                btn -> {
                    callback.onCancel();
                    if (minecraft != null) {
                        minecraft.setScreen(parent);
                    }
                }
        ).bounds(bottomStartX + (buttonWidth + bottomSpacing) * 2, bottomButtonY, buttonWidth, 20).build();
        this.addRenderableWidget(cancelButton);

        this.setInitialFocus(nameEditBox);
    }

    // 新增内部方法，正确更新权重
    private void updateWeight(String registryName, double weight) {
        ((TheConfigForBiomeScreen) parent).updateWeight(registryName, weight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 20, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.longroad.biome_config.edit.prompt"),
                centerX, centerY - 75, 0xAAAAAA);
    }

    @Override
    public void tick() {
        if (this.nameEditBox != null) this.nameEditBox.tick();
    }

    @Override
    public void onClose() {
        if (minecraft != null && parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }
}