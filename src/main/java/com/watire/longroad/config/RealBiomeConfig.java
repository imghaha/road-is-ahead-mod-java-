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
        System.out.println("🚀 RealBiomeConfig 初始化");

        // 使用绝对路径，避免任何歧义
        String gameDir = System.getProperty("user.dir");
        System.out.println("🎮 游戏目录: " + gameDir);

        CONFIG_FILE = Paths.get(gameDir, "config", "longroad_biome_config.txt");
        System.out.println("📁 配置文件路径: " + CONFIG_FILE.toAbsolutePath());

        // 预加载配置
        loadConfigIfNeeded();
    }

    // 加载配置 - 只在需要时加载
    private static void loadConfigIfNeeded() {
        synchronized (lock) {
            configReadCount.incrementAndGet();

            try {
                // 确保目录存在
                Files.createDirectories(CONFIG_FILE.getParent());

                if (Files.exists(CONFIG_FILE)) {
                    long currentModifiedTime = Files.getLastModifiedTime(CONFIG_FILE).toMillis();

                    // 如果文件没有变化，使用缓存
                    if (currentModifiedTime <= lastModifiedTime) {
                        configCacheHit.incrementAndGet();
                        logCacheStats();
                        return;
                    }

                    // 文件有变化，重新加载
                    lastModifiedTime = currentModifiedTime;
                    configVersion++;

                    // 读取所有行
                    List<String> lines = Files.readAllLines(CONFIG_FILE);

                    // 重置为默认值
                    biomeWidth = 1000;
                    variationRange = 50;

                    for (String line : lines) {
                        line = line.trim();

                        // 跳过空行和注释
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }

                        // 使用更健壮的解析方法
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
                                    }
                                } catch (NumberFormatException e) {
                                    System.err.println("❌ 解析数字失败: " + line);
                                }
                            }
                        }
                    }

                    System.out.println("✅ 配置重新加载成功 (版本 " + configVersion + ")");
                    System.out.println("  宽度: " + biomeWidth + ", 变化范围: " + variationRange);
                    logCacheStats();
                } else {
                    createDefaultConfig();
                    lastModifiedTime = Files.getLastModifiedTime(CONFIG_FILE).toMillis();
                }

            } catch (Exception e) {
                System.err.println("❌ 加载配置失败: " + e.getMessage());
                // 使用默认值
                biomeWidth = 1000;
                variationRange = 50;
            }
        }
    }

    // 创建默认配置
    private static void createDefaultConfig() {
        try {
            String content = "width=1000\nvariation=50\n# 长路世界配置\n# width: 群系宽度（10-10000）\n# variation: 变化范围（1-500）";
            Files.write(CONFIG_FILE, content.getBytes());
            System.out.println("📝 已创建默认配置文件");
        } catch (Exception e) {
            System.err.println("❌ 创建默认配置失败: " + e.getMessage());
        }
    }

    // 保存配置
    private static void saveConfig() {
        synchronized (lock) {
            try {
                String content = "width=" + biomeWidth + "\nvariation=" + variationRange + "\n# 长路世界配置\n# width: 群系宽度（10-10000）\n# variation: 变化范围（1-500）";
                Files.write(CONFIG_FILE, content.getBytes());
                lastModifiedTime = Files.getLastModifiedTime(CONFIG_FILE).toMillis();
                configVersion++;

                System.out.println("💾 配置已保存 (版本 " + configVersion + ")");
            } catch (Exception e) {
                System.err.println("❌ 保存配置失败: " + e.getMessage());
            }
        }
    }

    // 记录缓存统计
    private static void logCacheStats() {
        long reads = configReadCount.get();
        long hits = configCacheHit.get();
        double hitRate = reads > 0 ? (double) hits / reads * 100 : 0;

        if (reads % 100 == 0) { // 每100次读取记录一次
            System.out.println("📊 配置缓存统计: 读取 " + reads + " 次, 命中 " + hits + " 次, 命中率 " + String.format("%.1f", hitRate) + "%");
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

    // 设置配置
    public static void setConfig(int width, int variation) {
        synchronized (lock) {
            System.out.println("\n✏️  RealBiomeConfig.setConfig() 被调用");

            // 验证和限制
            width = Math.max(10, Math.min(10000, width));
            variation = Math.max(1, Math.min(500, variation));

            biomeWidth = width;
            variationRange = variation;

            System.out.println("  设置值: width=" + biomeWidth + ", variation=" + variationRange);

            // 保存到文件
            saveConfig();
        }
    }

    // 获取配置版本
    public static long getConfigVersion() {
        return configVersion;
    }
}