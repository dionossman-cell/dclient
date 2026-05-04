package com.dclient.friends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Friend list management screen.
 * - Shows all current friends
 * - Type a name and press Enter to add
 * - Click a friend to remove them
 * - Scroll if the list is long
 */
public class FriendListScreen extends Screen {

    private static final int ROW_H    = 18;
    private static final int PAD      = 8;
    private static final int PANEL_W  = 260;
    private static final int INPUT_H  = 22;
    private static final int HEADER_H = 30;

    private String inputBuffer = "";
    private int scrollOffset = 0;
    private int hoveredIdx = -1;

    private final Screen parent;

    public FriendListScreen(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
    }

    private boolean prevLeft = false;

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long window = Minecraft.getInstance().getWindow().handle();
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (leftDown && !prevLeft) handleClick(mouseX, mouseY);
        prevLeft = leftDown;

        // Backdrop
        gfx.fill(0, 0, width, height, 0xCC000000);

        int px = (width - PANEL_W) / 2;
        int py = (height - 300) / 2;
        int pw = PANEL_W;
        int ph = 300;

        // Panel background
        gfx.fill(px, py, px + pw, py + ph, 0xF00A0A0A);
        // Left accent bar
        int accent = com.dclient.client.gui.ThemeUtil.accent();
        gfx.fill(px, py, px + 2, py + ph, accent);
        // Borders
        gfx.fill(px + 2, py, px + pw, py + 1, 0xFF222222);
        gfx.fill(px + 2, py + ph - 1, px + pw, py + ph, 0xFF222222);
        gfx.fill(px + pw - 1, py, px + pw, py + ph, 0xFF222222);

        // Header
        gfx.fill(px + 2, py, px + pw - 1, py + HEADER_H, 0xFF0D0D0D);
        gfx.fill(px + 2, py + HEADER_H - 1, px + pw - 1, py + HEADER_H, accent);
        gfx.drawCenteredString(font, "FRIENDS", px + pw / 2, py + (HEADER_H - 8) / 2, accent);

        // Input box
        int iy = py + HEADER_H + PAD;
        gfx.fill(px + PAD, iy, px + pw - PAD, iy + INPUT_H, 0xFF141414);
        gfx.fill(px + PAD, iy + INPUT_H - 1, px + pw - PAD, iy + INPUT_H, accent);
        String hint = inputBuffer.isEmpty() ? "Type a name and press Enter..." : inputBuffer + "|";
        int hintColor = inputBuffer.isEmpty() ? 0xFF444444 : 0xFFFFFFFF;
        gfx.drawString(font, hint, px + PAD + 4, iy + (INPUT_H - 8) / 2, hintColor, false);

        // Friend list
        List<String> all = FriendManager.getAll();
        int listY = iy + INPUT_H + PAD;
        int listH = ph - HEADER_H - PAD - INPUT_H - PAD - PAD;
        int visRows = listH / ROW_H;

        // Clamp scroll
        int maxScroll = Math.max(0, all.size() - visRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Scissor the list area
        gfx.enableScissor(px + 2, listY, px + pw - 1, listY + listH);

        hoveredIdx = -1;
        for (int i = 0; i < visRows && (i + scrollOffset) < all.size(); i++) {
            int idx = i + scrollOffset;
            String name = all.get(idx);
            int ry = listY + i * ROW_H;

            boolean hov = mouseX >= px + PAD && mouseX < px + pw - PAD
                       && mouseY >= ry && mouseY < ry + ROW_H;
            if (hov) hoveredIdx = idx;

            if (hov) gfx.fill(px + 2, ry, px + pw - 1, ry + ROW_H, 0xFF161616);
            if (i > 0) gfx.fill(px + PAD, ry, px + pw - PAD, ry + 1, 0xFF1A1A1A);

            // Green dot
            gfx.fill(px + PAD + 2, ry + ROW_H / 2 - 2, px + PAD + 6, ry + ROW_H / 2 + 2, 0xFF44FF44);

            // Name
            gfx.drawString(font, name, px + PAD + 10, ry + (ROW_H - 8) / 2, 0xFFEEEEEE, false);

            // Remove hint on hover
            if (hov) {
                String rem = "click to remove";
                gfx.drawString(font, rem, px + pw - PAD - font.width(rem) - 2,
                    ry + (ROW_H - 8) / 2, 0xFFFF4444, false);
            }
        }

        if (all.isEmpty()) {
            gfx.drawCenteredString(font, "No friends yet", px + pw / 2, listY + listH / 2 - 4, 0xFF444444);
        }

        gfx.disableScissor();

        // Scroll indicators
        if (scrollOffset > 0)
            gfx.drawCenteredString(font, "\u25B2", px + pw - 10, listY + 2, 0xFF555555);
        if (scrollOffset < maxScroll)
            gfx.drawCenteredString(font, "\u25BC", px + pw - 10, listY + listH - 10, 0xFF555555);

        // Footer hint
        gfx.drawCenteredString(font, "Press Esc to close", px + pw / 2, py + ph - PAD - 8, 0xFF333333);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            String name = inputBuffer.trim();
            if (!name.isEmpty()) {
                FriendManager.add(name);
                inputBuffer = "";
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!inputBuffer.isEmpty())
                inputBuffer = inputBuffer.substring(0, inputBuffer.length() - 1);
            return true;
        }
        // Type characters
        char c = keyToChar(key, event.modifiers());
        if (c != 0) { inputBuffer += c; return true; }
        return super.keyPressed(event);
    }

    private void handleClick(int mouseX, int mouseY) {
        if (hoveredIdx >= 0 && hoveredIdx < FriendManager.size()) {
            List<String> all = FriendManager.getAll();
            if (hoveredIdx < all.size()) FriendManager.remove(all.get(hoveredIdx));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, scrollOffset + (int) -scrollY);
        return true;
    }

    private char keyToChar(int key, int mods) {
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
            return shift ? (char)('A' + key - GLFW.GLFW_KEY_A) : (char)('a' + key - GLFW.GLFW_KEY_A);
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            if (!shift) return (char)('0' + key - GLFW.GLFW_KEY_0);
        }
        if (key == GLFW.GLFW_KEY_SPACE) return ' ';
        if (key == GLFW.GLFW_KEY_MINUS) return shift ? '_' : '-';
        return 0;
    }

    @Override public boolean isPauseScreen() { return false; }
}
