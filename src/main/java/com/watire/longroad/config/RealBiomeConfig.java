package com.watire.longroad.config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class RealBiomeConfig {
    // 唯一正确的配置值存储
    private static int biomeWidth = 1000;
    private static int variationRange = 50;
    private static String biomeDistribution = "z_axis_only";
    private static RoadType roadType = RoadType.STRAIGHT;

    // 配置缓存和版本控制
    private static final Object lock = new Object();
    private static long lastModifiedTime = 0;
    private static long configVersion = 0;

    // 性能计数器
    private static final AtomicLong configReadCount = new AtomicLong(0);
    private static final AtomicLong configCacheHit = new AtomicLong(0);

    // 配置文件路径
    private static final Path CONFIG_FILE;

    static {
        String gameDir = System.getProperty("user.dir");
        CONFIG_FILE = Paths.get(gameDir, "config", "longroad_biome_config.txt");
        loadConfigIfNeeded();
    }

    private static void loadConfigIfNeeded() {
        synchronized (lock) {
            configReadCount.incrementAndGet();

            try {
                Files.createDirectories(CONFIG_FILE.getParent());

                if (Files.exists(CONFIG_FILE)) {
                    long currentModifiedTime = Files.getLastModifiedTime(CONFIG_FILE).toMillis();

                    if (currentModifiedTime <= lastModifiedTime) {
                        configCacheHit.incrementAndGet();
                        logCacheStats();
                        return;
                    }

                    lastModifiedTime = currentModifiedTime;
                    configVersion++;

                    List<String> lines = Files.readAllLines(CONFIG_FILE);

                    // 重置为默认值
                    biomeWidth = 1000;
                    variationRange = 50;
                    biomeDistribution = "z_axis_only";
                    roadType = RoadType.STRAIGHT;

                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;

                        if (line.contains("=")) {
                            String[] parts = line.split("=", 2);
                            if (parts.length == 2) {
                                String key = parts[0].trim();
                                String value = parts[1].trim();

                                try {
                                    if (key.equalsIgnoreCase("width")) {
                                        biomeWidth = Integer.parseInt(value);
                                    } else if (key.equalsIgnoreCase("variation")) {
                                        variationRange = Integer.parseInt(value);
                                    } else if (key.equalsIgnoreCase("distribution")) {
                                        if ("random_scattered".equals(value) || "z_axis_only".equals(value)) {
                                            biomeDistribution = value;
                                        } else {
                                            System.err.println("⚠️ 无效的分布方式，使用默认值: " + value);
                                        }
                                    } else if (key.equalsIgnoreCase("road_type")) {
                                        roadType = RoadType.fromConfigName(value);
                                    } else if (key.equalsIgnoreCase("generate_road")) {
                                        // 兼容旧配置
                                        boolean old = Boolean.parseBoolean(value);
                                        roadType = old ? RoadType.STRAIGHT : RoadType.NONE;
                                    }
                                } catch (NumberFormatException e) {
                                    // 忽略
                                }
                            }
                        }
                    }
                } else {
                    createDefaultConfig();
                    lastModifiedTime = Files.getLastModifiedTime(CONFIG_FILE).toMillis();
                }
            } catch (Exception e) {
                // 使用默认值
            }
        }
    }

    private static void createDefaultConfig() {
        try {
            String content = "width=1000\n" +
                    "variation=50\n" +
                    "distribution=z_axis_only\n" +
                    "road_type=half_slab\n" +
                    "# 长路世界配置\n" +
                    "# width: 群系宽度（10-10000）\n" +
                    "# variation: 变化范围（1-500）\n" +
                    "# distribution: 群系分布方式 (z_axis_only 或 random_scattered)\n" +
                    "# road_type: 道路类型 (straight, random, none, half_slab)";
            Files.write(CONFIG_FILE, content.getBytes());
        } catch (Exception e) {
            System.err.println("❌ 创建默认配置失败: " + e.getMessage());
        }
    }

    private static void saveConfig() {
        synchronized (lock) {
            try {
                String content = "width=" + biomeWidth + "\n" +
                        "variation=" + variationRange + "\n" +
                        "distribution=" + biomeDistribution + "\n" +
                        "road_type=" + roadType.getConfigName() + "\n" +
                        "# 长路世界配置\n" +
                        "# width: 群系宽度（10-10000）\n" +
                        "# variation: 变化范围（1-500）\n" +
                        "# distribution: 群系分布方式 (z_axis_only 或 random_scattered)\n" +
                        "# road_type: 道路类型 (straight, random, none, half_slab)";
                Files.write(CONFIG_FILE, content.getBytes());
                lastModifiedTime = Files.getLastModifiedTime(CONFIG_FILE).toMillis();
                configVersion++;
            } catch (Exception e) {
                System.err.println("❌ 保存配置失败: " + e.getMessage());
            }
        }
    }

    private static void logCacheStats() {
        long reads = configReadCount.get();
        long hits = configCacheHit.get();
        if (reads % 100 == 0) {
            // 可选输出
        }
    }

    // === 公共API ===

    public static int getBiomeWidth() {
        loadConfigIfNeeded();
        return biomeWidth;
    }

    public static int getVariationRange() {
        loadConfigIfNeeded();
        return variationRange;
    }

    public static String getBiomeDistribution() {
        loadConfigIfNeeded();
        return biomeDistribution;
    }

    public static RoadType getRoadType() {
        loadConfigIfNeeded();
        return roadType;
    }

    public static void setConfig(int width, int variation, String distribution, RoadType roadType) {
        synchronized (lock) {
            width = Math.max(10, Math.min(10000, width));
            variation = Math.max(1, Math.min(500, variation));

            if (!"random_scattered".equals(distribution) && !"z_axis_only".equals(distribution)) {
                distribution = "z_axis_only";
            }

            biomeWidth = width;
            variationRange = variation;
            biomeDistribution = distribution;
            RealBiomeConfig.roadType = roadType;

            saveConfig();
        }
    }

    public static long getConfigVersion() {
        return configVersion;
    }
}