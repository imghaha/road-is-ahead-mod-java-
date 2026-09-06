package com.watire.longroad.config;

public enum RoadType {
    STRAIGHT("straight", "gui.longroad.road_type.straight"),
    RANDOM("random", "gui.longroad.road_type.random"),
    NONE("none", "gui.longroad.road_type.none");

    private final String configName;
    private final String translationKey;

    // 构造函数 - 注意这里接收两个 String 参数
    RoadType(String configName, String translationKey) {
        this.configName = configName;
        this.translationKey = translationKey;
    }

    public String getConfigName() {
        return configName;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public static RoadType fromConfigName(String name) {
        for (RoadType type : values()) {
            if (type.configName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return STRAIGHT; // 默认
    }
}