package com.watire.longroad.worldset;

import java.util.Random;

// 修复后的 PerlinNoise2D 内部类
class PerlinNoise2D {
    private final int[] permutation;

    public PerlinNoise2D(long seed) {
        Random random = new Random(seed);

        // 初始化256个值
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }

        // Fisher-Yates洗牌算法
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = p[i];
            p[i] = p[j];
            p[j] = temp;
        }

        // 创建512长度的permutation数组（前256是随机排列，后256是重复）
        permutation = new int[512];
        for (int i = 0; i < 256; i++) {
            permutation[i] = p[i];
            permutation[i + 256] = p[i]; // 重复一次
        }
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : 0);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    public double noise(double x, double y) {
        // 确保X和Y在0-255范围内
        int X = ((int)Math.floor(x) & 255);
        int Y = ((int)Math.floor(y) & 255);

        x -= Math.floor(x);
        y -= Math.floor(y);

        double u = fade(x);
        double v = fade(y);

        // 使用& 255确保索引安全
        int A = (permutation[X & 255] + Y) & 255;
        int B = (permutation[(X + 1) & 255] + Y) & 255;

        return lerp(v,
                lerp(u,
                        grad(permutation[A], x, y),
                        grad(permutation[B], x - 1, y)
                ),
                lerp(u,
                        grad(permutation[A + 1], x, y - 1),
                        grad(permutation[B + 1], x - 1, y - 1)
                )
        );
    }

    public double fractal(int octaves, double x, double y, double persistence, double scale) {
        double total = 0;
        double frequency = scale;
        double amplitude = 1;
        double maxValue = 0;

        for (int i = 0; i < octaves; i++) {
            total += noise(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= 2;
        }

        return total / maxValue;
    }
}