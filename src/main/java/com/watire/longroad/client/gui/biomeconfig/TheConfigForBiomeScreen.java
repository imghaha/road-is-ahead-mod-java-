package com.watire.longroad.client.gui.biomeconfig;

import com.watire.longroad.client.gui.biomeedit.BiomeConfigEditScreen;
import com.watire.longroad.client.gui.pathconfusion.PathConfusionConfigScreen;
import com.watire.longroad.config.LongRoadBiomeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class TheConfigForBiomeScreen extends Screen {
    private final Screen parent;
    private BiomeConfigList configList;
    private Button addButton;
    private Button editButton;
    private Button deleteButton;
    private Button doneButton;
    private Button cancelButton;
    private Button saveEntryButton;

    private boolean isEditingNewEntry = false;
    private EditBox entryEditBox;
    private String tempEntryName = "";
    private int editingEntryIndex = -1;

    private final List<BiomeEntry> biomeEntries = new ArrayList<>();

    public TheConfigForBiomeScreen(Screen parent) {
        super(Component.translatable("gui.longroad.biome_config.title"));
        this.parent = parent;
        initializeBiomes();
    }

    public static class BiomeEntry {
        private String registryName;          // 注册名（现可修改）
        private String displayName;            // 显示名
        private double weight;                 // 权重
        private TerrainConfig terrainConfig;   // 地形配置
        private PathConfusionConfigScreen.PathConfig pathConfig; // 路径混淆配置

        // 构造函数：仅注册名和显示名，其他使用默认值
        public BiomeEntry(String registryName, String displayName) {
            this.registryName = registryName;
            this.displayName = displayName;
            this.weight = 1.0;
            this.terrainConfig = new TerrainConfig("grass_block", "dirt", "stone");
            this.pathConfig = new PathConfusionConfigScreen.PathConfig("grass_block", "dirt", "stone");
        }

        // 构造函数：完整初始化
        public BiomeEntry(String registryName, String displayName,
                          String pathBlock, String confusion1, String confusion2) {
            this.registryName = registryName;
            this.displayName = displayName;
            this.weight = 1.0;
            this.terrainConfig = new TerrainConfig("grass_block", "dirt", "stone");
            this.pathConfig = new PathConfusionConfigScreen.PathConfig(pathBlock, confusion1, confusion2);
        }

        // Getter 和 Setter
        public String getRegistryName() { return registryName; }
        public void setRegistryName(String registryName) { this.registryName = registryName; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }

        public TerrainConfig getTerrainConfig() { return terrainConfig; }
        public void setTerrainConfig(TerrainConfig terrainConfig) { this.terrainConfig = terrainConfig; }

        public PathConfusionConfigScreen.PathConfig getPathConfig() { return pathConfig; }
        public void setPathConfig(PathConfusionConfigScreen.PathConfig pathConfig) { this.pathConfig = pathConfig; }
    }

    public static class TerrainConfig {
        private String upperBlock;
        private String middleBlock;
        private String lowerBlock;

        public TerrainConfig(String upper, String middle, String lower) {
            this.upperBlock = upper;
            this.middleBlock = middle;
            this.lowerBlock = lower;
        }

        public String getUpperBlock() { return upperBlock; }
        public String getMiddleBlock() { return middleBlock; }
        public String getLowerBlock() { return lowerBlock; }

        public void setUpperBlock(String block) { this.upperBlock = block; }
        public void setMiddleBlock(String block) { this.middleBlock = block; }
        public void setLowerBlock(String block) { this.lowerBlock = block; }

        // 复制当前对象
        public TerrainConfig copy() {
            return new TerrainConfig(upperBlock, middleBlock, lowerBlock);
        }
    }

    private void initializeBiomes() {
        List<LongRoadBiomeConfig.BiomeData> loadedData = LongRoadBiomeConfig.loadBiomeData();
        if (loadedData != null && !loadedData.isEmpty()) {
            for (LongRoadBiomeConfig.BiomeData data : loadedData) {
                TerrainConfig tc = new TerrainConfig(
                        data.getTerrain().getUpper(),
                        data.getTerrain().getMiddle(),
                        data.getTerrain().getLower()
                );
                BiomeEntry entry = new BiomeEntry(data.getRegistryName(), data.getDisplayName());
                entry.setWeight(data.getWeight());
                entry.setTerrainConfig(tc);
                if (data.getPath() != null) {
                    entry.setPathConfig(new PathConfusionConfigScreen.PathConfig(
                            data.getPath().getPathBlock(),
                            data.getPath().getConfusion1(),
                            data.getPath().getConfusion2()
                    ));
                }
                biomeEntries.add(entry);
                System.out.println("[LongRoad] 加载群系: " + data.getRegistryName() + " 权重=" + data.getWeight());
            }
        } else {
            // 默认配置（略，可复用之前的代码）
            createDefaultBiomes();
        }
    }

    private void createDefaultBiomes() {
        // 此处简写，实际请参照 LongRoadBiomeConfig.createDefaultConfig
        biomeEntries.add(new BiomeEntry("minecraft:plains", "plains"));
        saveBiomeConfigs();
    }

    private void saveBiomeConfigs() {
        System.out.println("[LongRoad] ===== 开始保存配置 =====");
        List<LongRoadBiomeConfig.BiomeData> dataList = new ArrayList<>();
        for (BiomeEntry entry : biomeEntries) {
            LongRoadBiomeConfig.TerrainConfig tc = new LongRoadBiomeConfig.TerrainConfig(
                    entry.getTerrainConfig().getUpperBlock(),
                    entry.getTerrainConfig().getMiddleBlock(),
                    entry.getTerrainConfig().getLowerBlock()
            );
            LongRoadBiomeConfig.PathConfig pc = new LongRoadBiomeConfig.PathConfig(
                    entry.getPathConfig().getPathBlock(),
                    entry.getPathConfig().getConfusion1(),
                    entry.getPathConfig().getConfusion2()
            );
            LongRoadBiomeConfig.BiomeData data = new LongRoadBiomeConfig.BiomeData(
                    entry.getRegistryName(),
                    entry.getDisplayName(),
                    entry.getWeight(),
                    tc,
                    pc
            );
            dataList.add(data);
            System.out.println("  准备保存: " + entry.getRegistryName() + " 权重=" + entry.getWeight());
        }
        LongRoadBiomeConfig.saveBiomeData(dataList);
        System.out.println("[LongRoad] ===== 保存完成 =====");
    }

    public double getWeight(String registryName) {
        for (BiomeEntry entry : biomeEntries) {
            if (entry.getRegistryName().equals(registryName)) {
                return entry.getWeight();
            }
        }
        System.out.println("[LongRoad] getWeight: 未找到 " + registryName + "，返回默认 1.0");
        return 1.0;
    }

    public void updateWeight(String registryName, double weight) {
        System.out.println("[LongRoad] updateWeight 被调用: " + registryName + " = " + weight);
        for (BiomeEntry entry : biomeEntries) {
            if (entry.getRegistryName().equals(registryName)) {
                entry.setWeight(weight);
                System.out.println("  -> 已更新条目: " + entry.getDisplayName());
                return;
            }
        }
        System.out.println("  -> 错误：未找到注册名为 " + registryName + " 的条目！");
    }

    public TerrainConfig getTerrainConfig(String registryName) {
        for (BiomeEntry entry : biomeEntries) {
            if (entry.getRegistryName().equals(registryName)) {
                return entry.getTerrainConfig().copy();
            }
        }
        return new TerrainConfig("grass_block", "dirt", "stone");
    }

    public void updateTerrainConfig(String registryName, TerrainConfig newConfig) {
        for (BiomeEntry entry : biomeEntries) {
            if (entry.getRegistryName().equals(registryName)) {
                entry.setTerrainConfig(newConfig);
                break;
            }
        }
    }

    public PathConfusionConfigScreen.PathConfig getPathConfig(String registryName) {
        for (BiomeEntry entry : biomeEntries) {
            if (entry.getRegistryName().equals(registryName)) {
                return new PathConfusionConfigScreen.PathConfig(
                        entry.getPathConfig().getPathBlock(),
                        entry.getPathConfig().getConfusion1(),
                        entry.getPathConfig().getConfusion2()
                );
            }
        }
        return new PathConfusionConfigScreen.PathConfig("grass_block", "dirt", "stone");
    }

    public void updatePathConfig(String registryName, PathConfusionConfigScreen.PathConfig newConfig) {
        for (BiomeEntry entry : biomeEntries) {
            if (entry.getRegistryName().equals(registryName)) {
                entry.setPathConfig(newConfig);
                break;
            }
        }
    }

    // ---------- GUI 生命周期 ----------
    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int yOffset = -30;
        int rightMargin = 60;
        int listWidth = this.width - 2 * rightMargin;
        int listHeight = 100;
        int listY0 = centerY - listHeight / 2 + yOffset;
        int listY1 = listY0 + listHeight;

        this.configList = new BiomeConfigList(this.minecraft, listWidth, listHeight, listY0, listY1, 20, this::isValidBiome);
        this.configList.setLeftPos(centerX - listWidth / 2);

        for (BiomeEntry entry : biomeEntries) {
            this.configList.addEntry(entry.getRegistryName(), entry.getDisplayName());
        }
        this.addRenderableWidget(this.configList);

        int buttonY = listY1 + 10;
        int opButtonWidth = 60;
        int spacing = 10;
        int totalOpWidth = opButtonWidth * 3 + spacing * 2;
        int startX = centerX - totalOpWidth / 2;

        this.addButton = Button.builder(
                Component.translatable("gui.longroad.biome_config.add"),
                btn -> startEditingNewEntry()
        ).bounds(startX, buttonY, opButtonWidth, 20).build();
        this.addRenderableWidget(addButton);

        this.editButton = Button.builder(
                Component.translatable("gui.longroad.biome_config.edit"),
                btn -> {
                    BiomeConfigList.Entry selected = configList.getSelected();
                    if (selected == null) {
                        if (minecraft != null && minecraft.player != null) {
                            minecraft.player.sendSystemMessage(Component.literal("请先选择一个群系！"));
                        }
                        return;
                    }
                    String registryName = selected.getRegistryName();
                    String displayName = selected.getDisplayName();

                    int index = -1;
                    for (int i = 0; i < biomeEntries.size(); i++) {
                        if (biomeEntries.get(i).getRegistryName().equals(registryName)) {
                            index = i;
                            break;
                        }
                    }
                    if (index == -1) return;

                    final int finalIndex = index;
                    Minecraft.getInstance().setScreen(new BiomeConfigEditScreen(
                            this,
                            registryName,
                            displayName,
                            finalIndex,
                            new BiomeConfigEditScreen.BiomeEditCallback() {
                                @Override
                                public void onSave(int saveIndex, String newName) {
                                    if (saveIndex >= 0 && saveIndex < biomeEntries.size()) {
                                        // 自动补全命名空间
                                        String processedName = newName.trim();
                                        if (!processedName.contains(":")) {
                                            processedName = "minecraft:" + processedName;
                                        }
                                        BiomeEntry e = biomeEntries.get(saveIndex);
                                        e.setRegistryName(processedName);   // 更新注册名
                                        e.setDisplayName(newName);          // 显示名保持用户输入的原样（不带前缀）
                                        rebuildList();
                                        selectBiomeByDisplayName(newName);
                                    }
                                }
                                @Override
                                public void onCancel() {}
                            }
                    ));
                }
        ).bounds(startX + opButtonWidth + spacing, buttonY, opButtonWidth, 20).build();
        this.addRenderableWidget(editButton);

        this.deleteButton = Button.builder(
                Component.translatable("gui.longroad.biome_config.delete"),
                btn -> {
                    BiomeConfigList.Entry selected = configList.getSelected();
                    if (selected != null) {
                        String registryName = selected.getRegistryName();
                        int index = -1;
                        for (int i = 0; i < biomeEntries.size(); i++) {
                            if (biomeEntries.get(i).getRegistryName().equals(registryName)) {
                                index = i;
                                break;
                            }
                        }
                        if (index != -1) {
                            biomeEntries.remove(index);
                            rebuildList();
                            if (isEditingNewEntry && editingEntryIndex == index) {
                                cancelEditing();
                            }
                        }
                    }
                }
        ).bounds(startX + (opButtonWidth + spacing) * 2, buttonY, opButtonWidth, 20).build();
        this.addRenderableWidget(deleteButton);

        this.saveEntryButton = Button.builder(
                Component.translatable("gui.longroad.biome_config.save"),
                btn -> saveCurrentEditingEntry()
        ).bounds(centerX - 20, buttonY, 40, 20).build();
        this.saveEntryButton.visible = false;
        this.addRenderableWidget(saveEntryButton);

        int editBoxY = buttonY + 30;
        this.entryEditBox = new EditBox(this.font, centerX - 60, editBoxY, 120, 20,
                Component.translatable("gui.longroad.biome_config.editbox.tooltip"));
        this.entryEditBox.setMaxLength(64);
        this.entryEditBox.setVisible(false);
        this.entryEditBox.setResponder(value -> this.tempEntryName = value);
        this.addRenderableWidget(entryEditBox);

        int bottomButtonWidth = 100;
        int bottomSpacing = 10;
        int totalBottomWidth = bottomButtonWidth * 2 + bottomSpacing;
        int bottomStartX = centerX - totalBottomWidth / 2;
        int bottomY = editBoxY + 40;

        this.doneButton = Button.builder(
                Component.translatable("gui.longroad.done"),
                btn -> {
                    System.out.println("[LongRoad] 完成按钮被点击");
                    if (isEditingNewEntry) cancelEditing();
                    saveBiomeConfigs();          // 保存到JSON文件
                    if (minecraft != null) minecraft.setScreen(parent);
                }
        ).bounds(bottomStartX, bottomY, bottomButtonWidth, 20).build();
        this.addRenderableWidget(doneButton);

        this.cancelButton = Button.builder(
                Component.translatable("gui.longroad.cancel"),
                btn -> {
                    if (minecraft != null) minecraft.setScreen(parent);
                }
        ).bounds(bottomStartX + bottomButtonWidth + bottomSpacing, bottomY, bottomButtonWidth, 20).build();
        this.addRenderableWidget(cancelButton);

        this.setInitialFocus(configList);
    }

    private boolean isValidBiome(String registryName) {
        return true;
    }

    private void selectBiomeByDisplayName(String displayName) {
        List<BiomeConfigList.Entry> entries = configList.children();
        for (BiomeConfigList.Entry entry : entries) {
            if (entry.getDisplayName().equals(displayName)) {
                configList.setSelected(entry);
                break;
            }
        }
    }

    private void rebuildList() {
        this.removeWidget(this.configList);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int yOffset = -30;
        int rightMargin = 60;
        int listWidth = this.width - 2 * rightMargin;
        int listHeight = 100;
        int listY0 = centerY - listHeight / 2 + yOffset;
        int listY1 = listY0 + listHeight;

        this.configList = new BiomeConfigList(this.minecraft, listWidth, listHeight, listY0, listY1, 20, this::isValidBiome);
        this.configList.setLeftPos(centerX - listWidth / 2);

        for (BiomeEntry entry : biomeEntries) {
            this.configList.addEntry(entry.getRegistryName(), entry.getDisplayName());
        }
        this.addRenderableWidget(this.configList);
    }

    private void startEditingNewEntry() {
        if (isEditingNewEntry) cancelEditing();
        isEditingNewEntry = true;
        editingEntryIndex = -1;
        tempEntryName = "";
        setupEditingUI();
    }

    private void setupEditingUI() {
        addButton.visible = false;
        editButton.visible = false;
        deleteButton.visible = false;
        saveEntryButton.visible = true;
        entryEditBox.setVisible(true);
        entryEditBox.setValue("");
        this.setFocused(entryEditBox);
    }

    private void saveCurrentEditingEntry() {
        if (!isEditingNewEntry) return;
        String nameToSave = tempEntryName.trim();
        if (nameToSave.isEmpty()) {
            nameToSave = Component.translatable("gui.longroad.biome.unknown").getString();
        }
        // 处理注册名：补全 minecraft: 前缀
        String registryName = nameToSave;
        if (!registryName.contains(":")) {
            registryName = "minecraft:" + registryName;
        }
        if (editingEntryIndex == -1) {
            BiomeEntry newEntry = new BiomeEntry(registryName, nameToSave, "dirt_path", "grass_block", "cobblestone");
            biomeEntries.add(newEntry);
            rebuildList();
            selectBiomeByDisplayName(nameToSave);
            cancelEditing();
        } else {
            if (editingEntryIndex >= 0 && editingEntryIndex < biomeEntries.size()) {
                biomeEntries.get(editingEntryIndex).setDisplayName(nameToSave);
                rebuildList();
                selectBiomeByDisplayName(nameToSave);
                cancelEditing();
            }
        }
    }

    private void cancelEditing() {
        isEditingNewEntry = false;
        editingEntryIndex = -1;
        tempEntryName = "";
        saveEntryButton.visible = false;
        entryEditBox.setVisible(false);
        entryEditBox.setValue("");
        addButton.visible = true;
        editButton.visible = true;
        deleteButton.visible = true;
        this.setFocused(configList);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        if (configList != null) {
            BiomeConfigList.Entry hovered = configList.getHoveredEntry(mouseX, mouseY);
            if (hovered != null && !isValidBiome(hovered.getRegistryName())) {
                guiGraphics.renderTooltip(font, Component.literal("无效的群系名称"), mouseX, mouseY);
            }
        }
        if (isEditingNewEntry) {
            String prompt = editingEntryIndex == -1 ?
                    Component.translatable("gui.longroad.biome_config.add_prompt").getString() :
                    Component.translatable("gui.longroad.biome_config.edit_prompt").getString();
            guiGraphics.drawCenteredString(this.font, prompt, this.width / 2, entryEditBox.getY() - 15, 0xAAAAAA);
        }
    }

    @Override
    public void tick() {
        if (this.entryEditBox != null) this.entryEditBox.tick();
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