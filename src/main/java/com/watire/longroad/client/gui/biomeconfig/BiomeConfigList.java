package com.watire.longroad.client.gui.biomeconfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;

public class BiomeConfigList extends ObjectSelectionList<BiomeConfigList.Entry> {

    @FunctionalInterface
    public interface BiomeValidator {
        boolean isValid(String registryName);
    }

    private final BiomeValidator validator;

    public BiomeConfigList(Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight, BiomeValidator validator) {
        super(minecraft, width, height, y0, y1, itemHeight);
        this.validator = validator;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getRight() - 6;
    }

    public void addEntry(String registryName, String displayName) {
        this.addEntry(new Entry(registryName, displayName, validator));
    }

    public void removeSelected() {
        Entry selected = getSelected();
        if (selected != null) {
            this.removeEntry(selected);
        }
    }

    public List<String> getAllDisplayNames() {
        return this.children().stream().map(Entry::getDisplayName).toList();
    }

    public Entry getHoveredEntry(double mouseX, double mouseY) {
        return super.getEntryAtPosition(mouseX, mouseY);
    }

    public class Entry extends ObjectSelectionList.Entry<Entry> {
        private final String registryName;
        private final String displayName;
        private final Component displayComponent;
        private final BiomeValidator validator;

        public Entry(String registryName, String displayName, BiomeValidator validator) {
            this.registryName = registryName;
            this.displayName = displayName;
            this.displayComponent = Component.literal(displayName);
            this.validator = validator;
        }

        public String getRegistryName() {
            return registryName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            boolean valid = validator.isValid(registryName); // 使用注册名验证
            int textX = left + 5;

            if (!valid) {
                guiGraphics.drawString(Minecraft.getInstance().font, "⚠", left + 1, top + 2, 0xFF0000);
                textX = left + 15;
            }

            guiGraphics.drawString(Minecraft.getInstance().font, displayComponent, textX, top + 2, 0xFFFFFF);
        }

        @Override
        public Component getNarration() {
            return displayComponent;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                BiomeConfigList.this.setSelected(this);
                return true;
            }
            return false;
        }
    }
}