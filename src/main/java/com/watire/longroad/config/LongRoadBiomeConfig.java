package com.watire.longroad.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LongRoadBiomeConfig {
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get();
    private static final Path BIOME_CONFIG_FILE = CONFIG_DIR.resolve("longroad_biomes.json");

    private static BiomeConfigData configData = null;
    private static List<BiomeData> biomeDataList = null;
    private static long lastModified = 0;

    public static class BiomeConfigData {
        private List<BiomeData> biomes;
        public List<BiomeData> getBiomes() { return biomes; }
        public void setBiomes(List<BiomeData> biomes) { this.biomes = biomes; }
    }

    public static class BiomeData {
        private String registryName;
        private String displayName;
        private double weight;          // 改为 double
        private TerrainConfig terrain;
        private PathConfig path;

        public BiomeData() {}

        public BiomeData(String registryName, String displayName, double weight, TerrainConfig terrain, PathConfig path) {
            this.registryName = registryName;
            this.displayName = displayName;
            this.weight = weight;
            this.terrain = terrain;
            this.path = path;
        }

        public BiomeData(String registryName, String displayName, double weight, TerrainConfig terrain) {
            this(registryName, displayName, weight, terrain,
                    new PathConfig("dirt_path", "cobblestone", "grass_block"));
        }

        public String getRegistryName() { return registryName; }
        public void setRegistryName(String registryName) { this.registryName = registryName; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }

        public TerrainConfig getTerrain() { return terrain; }
        public void setTerrain(TerrainConfig terrain) { this.terrain = terrain; }

        public PathConfig getPath() { return path; }
        public void setPath(PathConfig path) { this.path = path; }

        public String getPathBlock() { return path != null ? path.getPathBlock() : "dirt_path"; }
        public String getConfusion1() { return path != null ? path.getConfusion1() : "cobblestone"; }
        public String getConfusion2() { return path != null ? path.getConfusion2() : "grass_block"; }
    }

    public static class TerrainConfig {
        private String upper;
        private String middle;
        private String lower;
        public TerrainConfig() {}
        public TerrainConfig(String upper, String middle, String lower) {
            this.upper = upper;
            this.middle = middle;
            this.lower = lower;
        }
        public String getUpper() { return upper; }
        public void setUpper(String upper) { this.upper = upper; }
        public String getMiddle() { return middle; }
        public void setMiddle(String middle) { this.middle = middle; }
        public String getLower() { return lower; }
        public void setLower(String lower) { this.lower = lower; }
    }

    public static class PathConfig {
        private String pathBlock;
        private String confusion1;
        private String confusion2;
        public PathConfig() {}
        public PathConfig(String pathBlock, String confusion1, String confusion2) {
            this.pathBlock = pathBlock;
            this.confusion1 = confusion1;
            this.confusion2 = confusion2;
        }
        public String getPathBlock() { return pathBlock; }
        public void setPathBlock(String pathBlock) { this.pathBlock = pathBlock; }
        public String getConfusion1() { return confusion1; }
        public void setConfusion1(String confusion1) { this.confusion1 = confusion1; }
        public String getConfusion2() { return confusion2; }
        public void setConfusion2(String confusion2) { this.confusion2 = confusion2; }
    }

    /**
     * 加载生物群系配置
     */
    public static synchronized List<BiomeData> loadBiomeData() {
        // 如果配置文件不存在，创建默认配置
        if (!Files.exists(BIOME_CONFIG_FILE)) {
            System.out.println("[LongRoad] 配置文件不存在，创建默认配置");
            return createDefaultConfig();
        }

        try {
            long mod = Files.getLastModifiedTime(BIOME_CONFIG_FILE).toMillis();
            // 如果文件未修改且缓存存在，直接返回缓存
            if (biomeDataList != null && mod <= lastModified) {
                System.out.println("[LongRoad] 使用缓存配置，最后修改时间: " + lastModified);
                return biomeDataList;
            }

            String json = Files.readString(BIOME_CONFIG_FILE);
            System.out.println("[LongRoad] 读取配置文件: " + BIOME_CONFIG_FILE.toAbsolutePath());

            // 尝试新格式（带 biomes 包装）
            try {
                Type type = new TypeToken<BiomeConfigData>(){}.getType();
                BiomeConfigData newConfig = new Gson().fromJson(json, type);
                if (newConfig != null && newConfig.getBiomes() != null && !newConfig.getBiomes().isEmpty()) {
                    for (BiomeData data : newConfig.getBiomes()) {
                        validateBiomeData(data);
                    }
                    biomeDataList = newConfig.getBiomes();
                    configData = newConfig;
                    lastModified = mod;
                    System.out.println("[LongRoad] 成功加载 " + biomeDataList.size() + " 个群系配置 (新格式)");
                    return biomeDataList;
                }
            } catch (Exception e) {
                System.out.println("[LongRoad] 尝试加载旧格式配置...");
            }

            // 尝试旧格式（直接数组）
            try {
                Type oldType = new TypeToken<List<BiomeData>>(){}.getType();
                List<BiomeData> oldList = new Gson().fromJson(json, oldType);
                if (oldList != null && !oldList.isEmpty()) {
                    for (BiomeData data : oldList) {
                        validateBiomeData(data);
                    }
                    biomeDataList = oldList;
                    saveBiomeData(oldList); // 转为新格式保存
                    lastModified = Files.getLastModifiedTime(BIOME_CONFIG_FILE).toMillis();
                    System.out.println("[LongRoad] 成功加载 " + biomeDataList.size() + " 个群系配置 (旧格式，已转换)");
                    return biomeDataList;
                }
            } catch (Exception e) {
                System.err.println("[LongRoad] 配置文件格式错误: " + e.getMessage());
            }

            // 解析失败，使用默认配置（但不覆盖文件）
            System.err.println("[LongRoad] 无法解析配置文件，返回默认配置（文件未被覆盖）");
            return createDefaultConfig(false); // 不保存到文件

        } catch (Exception e) {
            System.err.println("[LongRoad] 加载配置失败: " + e.getMessage());
            e.printStackTrace();
            return createDefaultConfig(false);
        }
    }

    private static void validateBiomeData(BiomeData data) {
        if (data.weight <= 0) data.weight = 1.0;
        if (data.path == null) {
            if (data.registryName != null) {
                if (data.registryName.contains("desert")) {
                    data.path = new PathConfig("sandstone", "stone", "sand");
                } else if (data.registryName.contains("deep_dark")) {
                    data.path = new PathConfig("deepslate_bricks", "cracked_deepslate_bricks", "deepslate");
                } else {
                    data.path = new PathConfig("dirt_path", "cobblestone", "grass_block");
                }
            } else {
                data.path = new PathConfig("dirt_path", "cobblestone", "grass_block");
            }
        }
        if (data.terrain == null) {
            if (data.registryName != null && data.registryName.contains("desert")) {
                data.terrain = new TerrainConfig("sand", "sandstone", "stone");
            } else {
                data.terrain = new TerrainConfig("grass_block", "dirt", "stone");
            }
        }
    }

    /**
     * 创建默认配置（可选是否保存到文件）
     */
    private static List<BiomeData> createDefaultConfig() {
        return createDefaultConfig(true);
    }

    private static List<BiomeData> createDefaultConfig(boolean saveToFile) {
        List<BiomeData> defaults = new ArrayList<>();

        PathConfig defaultPath = new PathConfig("dirt_path", "grass_block", "cobblestone");
        PathConfig desertPath = new PathConfig("cobblestone", "sandstone", "sand");
        PathConfig deepDarkPath = new PathConfig("deepslate_bricks", "cracked_deepslate_bricks", "deepslate");

        defaults.add(new BiomeData("minecraft:plains", "plains", 10.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:desert", "desert", 8.0,
                new TerrainConfig("sand", "sandstone", "stone"), desertPath));
        defaults.add(new BiomeData("minecraft:forest", "forest", 10.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:snowy_plains", "snowy_plains", 5.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:jungle", "jungle", 5.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:flower_forest", "flower_forest", 4.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:windswept_hills", "windswept_hills", 5.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:snowy_taiga", "snowy_taiga", 5.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:sunflower_plains", "sunflower_plains", 3.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:cherry_grove", "cherry_grove", 3.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:bamboo_jungle", "bamboo_jungle", 3.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:deep_dark", "deep_dark", 2.0,
                new TerrainConfig("stone", "stone", "stone"), deepDarkPath));
        defaults.add(new BiomeData("minecraft:taiga", "taiga", 8.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:old_growth_pine_taiga", "old_growth_pine_taiga", 4.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:old_growth_spruce_taiga", "old_growth_spruce_taiga", 4.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:swamp", "swamp", 5.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:mangrove_swamp", "mangrove_swamp", 4.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:birch_forest", "birch_forest", 5.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:old_growth_birch_forest", "old_growth_birch_forest", 4.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:dark_forest", "dark_forest", 4.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));
        defaults.add(new BiomeData("minecraft:meadow", "meadow", 5.0,
                new TerrainConfig("grass_block", "dirt", "stone"), defaultPath));

        if (saveToFile) {
            saveBiomeData(defaults);
        }
        return defaults;
    }

    /**
     * 保存生物群系配置
     */
    public static synchronized void saveBiomeData(List<BiomeData> data) {
        try {
            BiomeConfigData saveData = new BiomeConfigData();
            saveData.setBiomes(data);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(saveData);
            Files.writeString(BIOME_CONFIG_FILE, json);

            // 更新缓存并记录文件修改时间
            biomeDataList = data;
            configData = saveData;
            lastModified = Files.getLastModifiedTime(BIOME_CONFIG_FILE).toMillis();

            System.out.println("[LongRoad] 群系配置已保存至: " + BIOME_CONFIG_FILE.toAbsolutePath());
            System.out.println("[LongRoad] 共保存 " + data.size() + " 个群系");
        } catch (IOException e) {
            System.err.println("[LongRoad] 保存配置失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static synchronized void reloadConfig() {
        lastModified = 0;
        biomeDataList = null;
        configData = null;
        loadBiomeData();
        System.out.println("[LongRoad] 配置已重新加载");
    }

    public static Path getConfigFile() {
        return BIOME_CONFIG_FILE;
    }
}