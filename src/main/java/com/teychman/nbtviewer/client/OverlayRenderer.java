package com.teychman.nbtviewer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Mod.EventBusSubscriber(modid = "nbtviewer", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class OverlayRenderer {

    private static List<String> currentLines = Collections.emptyList();
    private static String lastTag = "";
    private static int scrollOffset = 0;
    private static final int PADDING = 3;
    private static final float SCALE = 0.75f;

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        Slot hovered = screen.getSlotUnderMouse();
        if (hovered == null) return;

        ItemStack stack = hovered.getItem();
        if (stack.isEmpty()) return;

        CompoundTag tag = stack.getTag();
        if (tag == null) return;

        String tagString = tag.toString();
        if (!tagString.equals(lastTag)) {
            lastTag = tagString;
            scrollOffset = 0;
            currentLines = formatNbtLines(tagString);
        }

        // Защита от выхода за пределы
        int maxVisible = getMaxVisibleLines(Minecraft.getInstance());
        scrollOffset = Mth.clamp(scrollOffset, 0, Math.max(0, currentLines.size() - maxVisible));

        renderPanel(event.getGuiGraphics(), currentLines, scrollOffset);
    }

    @SubscribeEvent
    public static void onMouseScroll(ScreenEvent.MouseScrolled event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        // Проверяем нажат ли Ctrl
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean isCtrlDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL);

        if (!isCtrlDown) return; // Если Ctrl не зажат — не вмешиваемся

        Slot hovered = screen.getSlotUnderMouse();
        if (hovered == null) return;

        ItemStack stack = hovered.getItem();
        if (stack.isEmpty() || currentLines.isEmpty()) return;

        int maxVisible = getMaxVisibleLines(Minecraft.getInstance());
        if (currentLines.size() <= maxVisible) return;

        double delta = event.getScrollDelta();

        if (delta > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (delta < 0) {
            scrollOffset = Math.min(currentLines.size() - maxVisible, scrollOffset + 1);
        }

        event.setCanceled(true); // Перехватываем только при активном Ctrl и NBT
    }


    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        if (event.getButton() == 1 && com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                Minecraft.getInstance().getWindow().getWindow(),
                com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL)) {
            Slot hovered = screen.getSlotUnderMouse();
            if (hovered == null) return;

            ItemStack stack = hovered.getItem();
            if (!stack.isEmpty() && stack.hasTag()) {
                String fullTag = Objects.requireNonNull(stack.getTag()).toString();
                Minecraft.getInstance().keyboardHandler.setClipboard(fullTag);
                Objects.requireNonNull(Minecraft.getInstance().player).displayClientMessage(
                        net.minecraft.network.chat.Component.literal("NBT скопирован в буфер обмена")
                                .withStyle(ChatFormatting.GREEN),
                        true
                );
                event.setCanceled(true);
            }
        }
    }

    private static List<String> formatNbtLines(String raw) {
        String withBreaks = raw.replace("{", "{\n").replace("}", "\n}").replace(",", ",\n");
        String[] arr = withBreaks.split("\n");
        List<String> out = new ArrayList<>();
        for (String line : arr) {
            out.add(line.trim());
        }
        return out;
    }

    private static void renderPanel(GuiGraphics graphics, List<String> lines, int offset) {
        Minecraft mc = Minecraft.getInstance();
        int lineHeight = (int)(mc.font.lineHeight * SCALE);
        int maxVisible = getMaxVisibleLines(mc);

        // Безопасные границы подсписка
        int safeOffset = Mth.clamp(offset, 0, Math.max(0, lines.size() - 1));
        int endIndex = Math.min(lines.size(), safeOffset + maxVisible);
        List<String> visible = lines.subList(safeOffset, endIndex);

        int width = 0;
        for (String l : visible) {
            width = Math.max(width, (int)(mc.font.width(stripFormatting(l)) * SCALE));
        }
        width += PADDING * 2;
        int height = lineHeight * visible.size() + PADDING * 2;

        int x0 = 1;
        int y0 = 1;
        int x1 = x0 + width;
        int y1 = y0 + height;

        graphics.fill(x0, y0, x1, y1, 0xA0000000);

        RenderSystem.enableBlend();
        graphics.pose().pushPose();
        graphics.pose().scale(SCALE, SCALE, 1.0f);
        int scaledX = (int)(x0 / SCALE) + PADDING;
        int y = (int)(y0 / SCALE) + PADDING;
        for (String line : visible) {
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                String key = parts[0] + ":";
                String val = parts[1];
                graphics.drawString(mc.font, key, scaledX, y, ChatFormatting.GOLD.getColor(), false);
                graphics.drawString(mc.font, val, scaledX + mc.font.width(key), y, ChatFormatting.WHITE.getColor(), false);
            } else {
                graphics.drawString(mc.font, line, scaledX, y, ChatFormatting.WHITE.getColor(), false);
            }
            y += mc.font.lineHeight;
        }
        graphics.pose().popPose();
    }

    private static int getMaxVisibleLines(Minecraft mc) {
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        return Mth.clamp((int)((screenHeight - 2 * PADDING) / (mc.font.lineHeight * SCALE)), 1, 100);
    }

    private static String stripFormatting(String text) {
        return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }
}
