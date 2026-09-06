package com.watire.longroad.util;

public class FeatureDecorationFlag {
    private static final ThreadLocal<Boolean> IS_DECORATING = ThreadLocal.withInitial(() -> false);

    public static void set(boolean value) {
        IS_DECORATING.set(value);
    }

    public static boolean isActive() {
        return IS_DECORATING.get();
    }
}