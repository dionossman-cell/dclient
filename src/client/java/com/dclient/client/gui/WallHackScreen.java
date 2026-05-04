package com.dclient.client.gui;

import com.dclient.module.modules.render.BlockESP;
import com.dclient.module.modules.render.WallHack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class WallHackScreen extends Screen {
    private static final int COL_W = 160, ROW_H = 14, COLS = 4;
    private static final int PADDING = 8, HEADER_H = 50, FOOTER_H = 30;
    private static final int COLOR_BG = 0xDD0A0A0A, COLOR_BORDER = 0xFF333333;
    private static final int COLOR_ON = 0xFF44FF44, COLOR_OFF = 0xFFAAAAAA;
    private static final int COLOR_HOVER = 0xFF1E1E1E, COLOR_BTN = 0xFF222244, COLOR_BTN_H = 0xFF333366;
    private static final int COLOR_SEARCH = 0xFF1A1A1A;

    private final WallHack module;
    private List<String> filtered = new ArrayList<>();
    private String search = "";
    private int scrollOffset = 0;
    private boolean prevLeft = false;

    public WallHackScreen(WallHack module) {
        super(Component.literal("Wall Hack"));
        this.module = module;
        updateFilter();
    }

    private void updateFilter() {
        filtered.clear();
        String q = search.toLowerCase();
        for (String name : BlockESP.getAllBlocks().keySet())
            if (q.isEmpty() || name.toLowerCase().contains(q)) filtered.add(name);
        scrollOffset = 0;
    }

    private int visibleRows() { return Math.max(1, (height - HEADER_H - FOOTER_H) / (ROW_H + 2)); }
    private int maxScroll()   { return Math.max(0, (filtered.size() + COLS - 1) / COLS - visibleRows()); }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1); updateFilter(); return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        char c = keyToChar(key, event.modifiers());
        if (c != 0) { search += c; updateFilter(); return true; }
        return super.keyPressed(event);
    }

    private char keyToChar(int key, int mods) {
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
            return shift ? (char)('A' + key - GLFW.GLFW_KEY_A) : (char)('a' + key - GLFW.GLFW_KEY_A);
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return (char)('0' + key - GLFW.GLFW_KEY_0);
        if (key == GLFW.GLFW_KEY_SPACE) return ' ';
        return 0;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (leftDown && !prevLeft) handleClick(mouseX, mouseY);
        prevLeft = leftDown;

        gfx.fill(0, 0, width, height, COLOR_BG);
        gfx.drawCenteredString(font, "Wall Hack - Select Transparent Blocks", width / 2, PADDING, 0xFFFFFFFF);

        int sx = PADDING, sy = PADDING + 14, sw = width - PADDING * 2, sh = 16;
        gfx.fill(sx, sy, sx + sw, sy + sh, COLOR_SEARCH);
        gfx.fill(sx, sy, sx + sw, sy + 1, COLOR_BORDER);
        gfx.fill(sx, sy + sh - 1, sx + sw, sy + sh, COLOR_BORDER);
        String sd = search.isEmpty() ? "Search blocks..." : search + "|";
        gfx.drawString(font, sd, sx + 4, sy + (sh - 8) / 2, search.isEmpty() ? 0xFF666666 : 0xFFFFFFFF, false);

        int vr = visibleRows();
        for (int row = scrollOffset; row < Math.min(scrollOffset + vr, (filtered.size() + COLS - 1) / COLS); row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = row * COLS + col;
                if (idx >= filtered.size()) break;
                String name = filtered.get(idx);
                int bx = PADDING + col * (COL_W + 2), by = HEADER_H + (row - scrollOffset) * (ROW_H + 2);
                boolean enabled = module.targetBlockNames.contains(name);
                boolean hovered = mouseX >= bx && mouseX < bx + COL_W && mouseY >= by && mouseY < by + ROW_H;
                gfx.fill(bx, by, bx + COL_W, by + ROW_H, hovered ? COLOR_HOVER : 0xFF111111);
                gfx.fill(bx, by, bx + COL_W, by + 1, COLOR_BORDER);
                float[] c = BlockESP.getBlockColor(name);
                gfx.fill(bx + 2, by + 3, bx + 9, by + ROW_H - 3,
                    0xFF000000 | ((int)(c[0]*255) << 16) | ((int)(c[1]*255) << 8) | (int)(c[2]*255));
                String label = (enabled ? "\u2611 " : "\u2610 ") + name;
                while (font.width(label) > COL_W - 14 && label.length() > 4) label = label.substring(0, label.length() - 1);
                gfx.drawString(font, label, bx + 12, by + (ROW_H - 8) / 2, enabled ? COLOR_ON : COLOR_OFF, false);
            }
        }

        if (maxScroll() > 0) {
            String s = "Scroll \u2191\u2193 | " + scrollOffset + "/" + maxScroll();
            gfx.drawString(font, s, width - PADDING - font.width(s), HEADER_H - 10, 0xFF666666, false);
        }
        gfx.drawString(font, "Selected: " + module.targetBlockNames.size(), PADDING, HEADER_H - 10, 0xFF888888, false);

        int btnY = height - FOOTER_H + 6, btnW = 90, gap = 4, bx0 = (width - (btnW * 3 + gap * 2)) / 2;
        drawBtn(gfx, mouseX, mouseY, bx0, btnY, btnW, "Select All", 0xFFFFFFFF);
        drawBtn(gfx, mouseX, mouseY, bx0 + btnW + gap, btnY, btnW, "Deselect All", 0xFFFFAAAA);
        drawBtn(gfx, mouseX, mouseY, bx0 + (btnW + gap) * 2, btnY, btnW, "Apply & Close", 0xFF44FF44);
    }

    private void drawBtn(GuiGraphics gfx, int mx, int my, int bx, int by, int bw, String label, int col) {
        boolean h = mx >= bx && mx < bx + bw && my >= by && my < by + 18;
        gfx.fill(bx, by, bx + bw, by + 18, h ? COLOR_BTN_H : COLOR_BTN);
        gfx.fill(bx, by, bx + bw, by + 1, COLOR_BORDER);
        gfx.fill(bx, by + 17, bx + bw, by + 18, COLOR_BORDER);
        gfx.drawCenteredString(font, label, bx + bw / 2, by + 5, col);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int) sy)); return true;
    }

    private void handleClick(int mx, int my) {
        int vr = visibleRows();
        for (int row = scrollOffset; row < Math.min(scrollOffset + vr, (filtered.size() + COLS - 1) / COLS); row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = row * COLS + col;
                if (idx >= filtered.size()) break;
                String name = filtered.get(idx);
                int bx = PADDING + col * (COL_W + 2), by = HEADER_H + (row - scrollOffset) * (ROW_H + 2);
                if (mx >= bx && mx < bx + COL_W && my >= by && my < by + ROW_H) {
                    if (module.targetBlockNames.contains(name)) module.targetBlockNames.remove(name);
                    else module.targetBlockNames.add(name);
                    module.onBlockSelectionChanged();
                    return;
                }
            }
        }
        int btnY = height - FOOTER_H + 6, btnW = 90, gap = 4, bx0 = (width - (btnW * 3 + gap * 2)) / 2;
        if (my >= btnY && my < btnY + 18) {
            if (mx >= bx0 && mx < bx0 + btnW) { module.targetBlockNames.addAll(filtered); module.onBlockSelectionChanged(); }
            else if (mx >= bx0 + btnW + gap && mx < bx0 + btnW * 2 + gap) { module.targetBlockNames.removeAll(filtered); module.onBlockSelectionChanged(); }
            else if (mx >= bx0 + (btnW + gap) * 2 && mx < bx0 + (btnW + gap) * 2 + btnW) onClose();
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
