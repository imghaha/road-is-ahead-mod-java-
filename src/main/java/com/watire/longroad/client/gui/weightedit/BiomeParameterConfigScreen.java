package com.watire.longroad.client.gui.weightedit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BiomeParameterConfigScreen extends Screen {
    private final Screen parent;
    private final String biomeName;
    private final ParameterSaveCallback callback;
    private final double currentWeight;          // double

    private EditBox parameterEditBox;
    private Button confirmButton;
    private Button cancelButton;

    private String currentValue = "";

    public interface ParameterSaveCallback {
        void onSave(String value);
        void onCancel();
    }

    public BiomeParameterConfigScreen(Screen parent, String biomeName, double currentWeight, ParameterSaveCallback callback) {
        super(Component.translatable("gui.longroad.biome_parameter.title"));
        this.parent = parent;
        this.biomeName = biomeName;
        this.currentWeight = currentWeight;
        this.callback = callback;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.parameterEditBox = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20,
                Component.translatable("gui.longroad.biome_parameter.input_hint"));
        this.parameterEditBox.setMaxLength(64);
        this.parameterEditBox.setValue(String.valueOf(currentWeight));   // 显示 double
        this.parameterEditBox.setResponder(value -> this.currentValue = value);
        // 过滤器：允许数字、小数点、负号（可根据需要调整）
        this.parameterEditBox.setFilter(s -> {
            if (s.isEmpty()) return true;
            return s.matches("-?\\d*\\.?\\d*");
        });
        this.addRenderableWidget(parameterEditBox);

        int buttonY = centerY + 30;
        int buttonWidth = 80;
        int spacing = 20;
        int totalWidth = buttonWidth * 2 + spacing;
        int startX = centerX - totalWidth / 2;

        this.confirmButton = Button.builder(
                Component.translatable("gui.longroad.save"),
                btn -> {
                    String valueToSave = currentValue.trim();
                    if (valueToSave.isEmpty()) {
                        // 输入为空，视为取消操作，直接返回上一屏
                        if (minecraft != null) {
                            minecraft.setScreen(parent);
                        }
                        return;
                    }
                    // 非空时调用回调保存
                    callback.onSave(valueToSave);
                    if (minecraft != null) {
                        minecraft.setScreen(parent);
                    }
                }
        ).bounds(startX, buttonY, buttonWidth, 20).build();
        this.addRenderableWidget(confirmButton);

        this.cancelButton = Button.builder(
                Component.translatable("gui.longroad.cancel"),
                btn -> {
                    callback.onCancel();
                    if (minecraft != null) {
                        minecraft.setScreen(parent);
                    }
                }
        ).bounds(startX + buttonWidth + spacing, buttonY, buttonWidth, 20).build();
        this.addRenderableWidget(cancelButton);

        this.setInitialFocus(parameterEditBox);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        Component prompt = Component.translatable("gui.longroad.biome_parameter.prompt", biomeName);
        guiGraphics.drawCenteredString(this.font, prompt, this.width / 2, this.height / 2 - 25, 0xAAAAAA);
    }

    @Override
    public void tick() {
        if (this.parameterEditBox != null) this.parameterEditBox.tick();
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