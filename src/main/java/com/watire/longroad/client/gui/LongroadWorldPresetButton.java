package com.watire.longroad.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(Dist.CLIENT)
public class LongroadWorldPresetButton {

    private static final Map<Integer, Button> buttonById = new HashMap<>();
    private static final Map<Integer, Boolean> shouldBeVisibleById = new HashMap<>();
    private static final Map<Integer, String> lastWorldPresetsById = new HashMap<>();

    private static int currentButtonId = -1;

    private static int tickCounter = 0;
    private static boolean forceCheck = false;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof CreateWorldScreen createWorldScreen) {
            cleanupAllButtons();
            createNewButton(createWorldScreen, event);
            checkAndUpdateButtonState(true);
        }
    }

    private static void cleanupAllButtons() {
        buttonById.clear();
        shouldBeVisibleById.clear();
        lastWorldPresetsById.clear();
        currentButtonId = -1;
    }

    private static Button findOurButton() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof CreateWorldScreen screen)) {
            return null;
        }

        try {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof Button button) {
                    Component message = button.getMessage();
                    if (message != null) {
                        String text = message.getString();
                        if (text.contains("长路") || text.contains("Longroad")) {
                            return button;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ 查找按钮失败: " + e.getMessage());
        }

        return null;
    }

    private static void createNewButton(CreateWorldScreen screen, ScreenEvent.Init.Post event) {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int x = screen.width / 2 - buttonWidth / 2;
        int y = 180;

        Button button = Button.builder(
                        Component.translatable("gui.longroad.world_preset_button"),
                        btn -> {
                            System.out.println("🖱️ 长路设置按钮被点击 - 按钮ID: " + System.identityHashCode(btn));
                            Minecraft.getInstance().setScreen(new LongroadWorldPresetScreen(screen));
                        }
                )
                .bounds(x, y, buttonWidth, buttonHeight)
                .build();

        button.visible = false;
        button.active = false;

        event.addListener(button);

        int buttonId = System.identityHashCode(button);
        buttonById.put(buttonId, button);
        shouldBeVisibleById.put(buttonId, false);
        currentButtonId = buttonId;

        String initialPreset = getCurrentWorldPreset(screen);
        lastWorldPresetsById.put(buttonId, initialPreset);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;

        if (forceCheck) {
            forceCheck = false;
            checkAndUpdateButtonState(true);
        }

        if (tickCounter < 1) return;
        tickCounter = 0;

        checkAndUpdateButtonState(false);
    }

    public static void forceCheck() {
        forceCheck = true;
    }

    private static void checkAndUpdateButtonState(boolean forceUpdate) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof CreateWorldScreen screen)) {
                return;
            }

            Button button = null;

            if (currentButtonId != -1) {
                button = buttonById.get(currentButtonId);
            }

            if (button == null) {
                button = findOurButton();
                if (button != null) {
                    int buttonId = System.identityHashCode(button);
                    buttonById.put(buttonId, button);
                    currentButtonId = buttonId;
                }
            }

            if (button == null) {
                return;
            }

            int buttonId = System.identityHashCode(button);

            String currentPreset = getCurrentWorldPreset(screen);
            String lastPreset = lastWorldPresetsById.getOrDefault(buttonId, "unknown");

            boolean isLongroadNow = isLongroadWorldType(currentPreset);
            boolean wasLongroadBefore = isLongroadWorldType(lastPreset);

            boolean needsUpdate = forceUpdate ||
                    !currentPreset.equals(lastPreset) ||
                    isLongroadNow != wasLongroadBefore ||
                    button.visible != isLongroadNow ||
                    button.active != isLongroadNow;

            if (needsUpdate) {
                button.visible = isLongroadNow;
                button.active = isLongroadNow;

                shouldBeVisibleById.put(buttonId, isLongroadNow);
                lastWorldPresetsById.put(buttonId, currentPreset);

                if (isLongroadNow) {
                    // 恢复为原位置（不左移）
                    int x = screen.width / 2 - 110;
                    int y = 180;
                    button.setX(x);
                    button.setY(y);
                } else {
                    button.setX(-1000);
                    button.setY(-1000);
                }
            }

        } catch (Exception e) {
            System.out.println("⚠️ 检查按钮状态时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String getCurrentWorldPreset(CreateWorldScreen screen) {
        try {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof CycleButton<?> cycleButton) {
                    Object value = cycleButton.getValue();

                    if (value instanceof WorldPreset) {
                        return value.toString();
                    }

                    if (value instanceof String) {
                        return (String) value;
                    }

                    return value.toString();
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ 获取世界预设时出错: " + e.getMessage());
        }

        return "unknown";
    }

    private static boolean isLongroadWorldType(String worldPresetId) {
        if (worldPresetId == null || worldPresetId.equals("unknown")) {
            return false;
        }

        return worldPresetId.contains("longroad") ||
                worldPresetId.contains("flat_grass") ||
                worldPresetId.contains("flatgrass") ||
                worldPresetId.contains("terrain");
    }

    @SubscribeEvent
    public static void onScreenClose(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof CreateWorldScreen) {
            cleanupAllButtons();
        }

        if (event.getScreen() instanceof LongroadWorldPresetScreen) {
            forceCheck();
        }
    }
}