package com.watire.longroad.client.gui.terrainzblock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import com.watire.longroad.client.gui.biomeconfig.TheConfigForBiomeScreen;

public class TerrainBlockConfigScreen extends Screen {
    private final Screen parent;
    private final String biomeName;
    private final TheConfigForBiomeScreen.TerrainConfig terrainConfig;
    private final Consumer<TheConfigForBiomeScreen.TerrainConfig> saveCallback;
    private Button confirmButton;
    private Button cancelButton;

    private String errorMessage = "";
    private int errorTimer = 0;

    private final Map<Integer, EditBox> inputBoxes = new HashMap<>();
    private final Map<Integer, ItemStack> blockStacks = new HashMap<>();
    private final Map<Integer, Button> iconButtons = new HashMap<>();
    private final Map<Integer, StringWidget> blockNameWidgets = new HashMap<>();

    public TerrainBlockConfigScreen(Screen parent, String biomeName,
                                    TheConfigForBiomeScreen.TerrainConfig config,
                                    Consumer<TheConfigForBiomeScreen.TerrainConfig> saveCallback) {
        super(Component.translatable("gui.longroad.terrainzblock.title"));
        this.parent = parent;
        this.biomeName = biomeName;
        this.terrainConfig = config;
        this.saveCallback = saveCallback;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        Component biomeLabel = Component.translatable("gui.longroad.terrainzblock.editing_biome", biomeName);
        StringWidget biomeNameWidget = new StringWidget(
                centerX - 100, centerY - 80, 200, 20, biomeLabel, this.font
        );
        this.addRenderableWidget(biomeNameWidget);

        int midY = centerY - 40 - 15;
        int rightOffset = 50;
        int sectionHeight = 35;

        // 上层
        createConfigSection(centerX + rightOffset, midY,
                Component.translatable("gui.longroad.terrainzblock.upper"), 0,
                terrainConfig != null ? terrainConfig.getUpperBlock() : "grass_block");

        // 中层
        createConfigSection(centerX + rightOffset, midY + sectionHeight,
                Component.translatable("gui.longroad.terrainzblock.middle"), 1,
                terrainConfig != null ? terrainConfig.getMiddleBlock() : "dirt");

        // 下层
        createConfigSection(centerX + rightOffset, midY + sectionHeight * 2,
                Component.translatable("gui.longroad.terrainzblock.lower"), 2,
                terrainConfig != null ? terrainConfig.getLowerBlock() : "stone");

        int bottomY = midY + sectionHeight * 3 + 40;

        this.confirmButton = Button.builder(
                Component.translatable("gui.longroad.done"),
                btn -> {
                    saveConfig();
                    if (minecraft != null) {
                        minecraft.setScreen(parent);
                    }
                }
        ).bounds(centerX - 110, bottomY, 100, 20).build();
        this.addRenderableWidget(confirmButton);

        this.cancelButton = Button.builder(
                Component.translatable("gui.longroad.cancel"),
                btn -> {
                    if (minecraft != null) {
                        minecraft.setScreen(parent);
                    }
                }
        ).bounds(centerX + 10, bottomY, 100, 20).build();
        this.addRenderableWidget(cancelButton);
    }

    private void createConfigSection(int centerX, int y, Component layerName, int sectionIndex, String defaultBlock) {
        int inputWidth = 150;
        int iconSize = 20;
        int nameWidth = 100;
        int spacing = 10;

        int startX = centerX - (inputWidth + iconSize + nameWidth + spacing * 3) / 2;

        StringWidget layerLabel = new StringWidget(
                startX - 40, y + 2, 30, 16, layerName, this.font
        );
        this.addRenderableWidget(layerLabel);

        EditBox inputBox = new EditBox(this.font, startX, y, inputWidth, 20,
                Component.translatable("gui.longroad.terrainzblock.input_hint"));
        inputBox.setMaxLength(64);
        inputBox.setValue(defaultBlock);
        inputBox.setResponder(value -> parseBlock(value, sectionIndex));
        this.addRenderableWidget(inputBox);
        inputBoxes.put(sectionIndex, inputBox);

        Button iconButton = Button.builder(Component.literal(""), btn -> {})
                .bounds(startX + inputWidth + spacing, y, iconSize, 20).build();
        iconButton.active = false;
        this.addRenderableWidget(iconButton);
        iconButtons.put(sectionIndex, iconButton);

        StringWidget blockNameWidget = new StringWidget(
                startX + inputWidth + iconSize + spacing * 2, y + 2, nameWidth, 16,
                Component.literal(""), this.font
        );
        this.addRenderableWidget(blockNameWidget);
        blockNameWidgets.put(sectionIndex, blockNameWidget);

        blockStacks.put(sectionIndex, ItemStack.EMPTY);

        parseBlock(defaultBlock, sectionIndex);
    }

    private void parseBlock(String input, int sectionIndex) {
        if (input == null || input.trim().isEmpty()) {
            blockStacks.put(sectionIndex, ItemStack.EMPTY);
            StringWidget nameWidget = blockNameWidgets.get(sectionIndex);
            if (nameWidget != null) nameWidget.setMessage(Component.literal(""));
            return;
        }

        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        try {
            ResourceLocation blockId;
            if (!trimmed.contains(":")) {
                blockId = new ResourceLocation("minecraft", trimmed);
            } else {
                blockId = new ResourceLocation(trimmed);
            }

            Block block = BuiltInRegistries.BLOCK.get(blockId);
            if (block != Blocks.AIR) {
                Item item = block.asItem();
                if (item != Items.AIR) {
                    ItemStack stack = new ItemStack(item);
                    blockStacks.put(sectionIndex, stack);
                    StringWidget nameWidget = blockNameWidgets.get(sectionIndex);
                    if (nameWidget != null) {
                        nameWidget.setMessage(stack.getHoverName());
                    }
                    errorMessage = "";
                } else {
                    errorMessage = Component.translatable("gui.longroad.terrainzblock.no_item").getString();
                    blockStacks.put(sectionIndex, ItemStack.EMPTY);
                    StringWidget nameWidget = blockNameWidgets.get(sectionIndex);
                    if (nameWidget != null) nameWidget.setMessage(Component.literal(""));
                }
            } else {
                errorMessage = Component.translatable("gui.longroad.terrainzblock.not_found").getString();
                blockStacks.put(sectionIndex, ItemStack.EMPTY);
                StringWidget nameWidget = blockNameWidgets.get(sectionIndex);
                if (nameWidget != null) nameWidget.setMessage(Component.literal(""));
            }
        } catch (Exception e) {
            errorMessage = Component.translatable("gui.longroad.terrainzblock.invalid").getString();
            blockStacks.put(sectionIndex, ItemStack.EMPTY);
            StringWidget nameWidget = blockNameWidgets.get(sectionIndex);
            if (nameWidget != null) nameWidget.setMessage(Component.literal(""));
        }
        errorTimer = 100;
    }

    private void saveConfig() {
        String upper = inputBoxes.get(0).getValue();
        String middle = inputBoxes.get(1).getValue();
        String lower = inputBoxes.get(2).getValue();
        TheConfigForBiomeScreen.TerrainConfig newConfig =
                new TheConfigForBiomeScreen.TerrainConfig(upper, middle, lower);
        saveCallback.accept(newConfig);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        for (int i = 0; i < 3; i++) {
            ItemStack stack = blockStacks.get(i);
            if (stack != null && !stack.isEmpty()) {
                Button iconButton = iconButtons.get(i);
                if (iconButton != null) {
                    guiGraphics.renderItem(stack, iconButton.getX() + 2, iconButton.getY() + 2);
                }
            }
        }

        if (errorTimer > 0 && !errorMessage.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, errorMessage, this.width / 2, this.height / 2 + 55, 0xFF5555);
            errorTimer--;
        }
    }

    @Override
    public void tick() {
        super.tick();
        for (EditBox box : inputBoxes.values()) {
            if (box != null) box.tick();
        }
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