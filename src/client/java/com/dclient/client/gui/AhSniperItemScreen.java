package com.dclient.client.gui;

import com.dclient.module.modules.donut.AhSniper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Item picker screen for AH Sniper.
 * Shows a searchable grid of all items/blocks — click one to select it.
 */
public class AhSniperItemScreen extends Screen {

    private static final int CELL  = 20;
    private static final int PAD   = 8;
    private static final int COLS  = 14;
    private static final int ROWS  = 8;
    private static final int PANEL_W = COLS * CELL + PAD * 2;
    private static final int SEARCH_H = 22;
    private static final int HEADER_H = 28;

    private final AhSniper module;
    private final Screen parent;

    private String search = "";
    private int scroll = 0;
    private List<Item> filtered = new ArrayList<>();
    private int hoveredIdx = -1;
    private boolean prevLeft = false;

    public AhSniperItemScreen(AhSniper module, Screen parent) {
        super(Component.literal("Select Item"));
        this.module = module;
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildList();
    }

    private void rebuildList() {
        filtered.clear();
        String q = search.toLowerCase();
        for (Item item : BuiltInRegistries.ITEM) {
            String name = item.getName().getString().toLowerCase();
            var key = BuiltInRegistries.ITEM.getKey(item);
            String id = key != null ? key.getPath() : "";
            if (q.isEmpty() || name.contains(q) || id.contains(q)) {
                filtered.add(item);
            }
        }
        scroll = 0;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long window = Minecraft.getInstance().getWindow().handle();
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (leftDown && !prevLeft) handleClick(mouseX, mouseY);
        prevLeft = leftDown;

        int accent = ThemeUtil.accent();
        int panelH = HEADER_H + SEARCH_H + PAD + ROWS * CELL + PAD;
        int px = (width - PANEL_W) / 2;
        int py = (height - panelH) / 2;

        // Backdrop
        gfx.fill(0, 0, width, height, 0xCC000000);

        // Panel shadow
        gfx.fill(px + 3, py + 3, px + PANEL_W + 3, py + panelH + 3, 0x44000000);

        // Panel background
        gfx.fill(px, py, px + PANEL_W, py + panelH, 0xF20A0A0A);
        gfx.fill(px, py, px + 2, py + panelH, accent);
        gfx.fill(px + 2, py, px + PANEL_W, py + 1, 0xFF222222);
        gfx.fill(px + 2, py + panelH - 1, px + PANEL_W, py + panelH, 0xFF222222);
        gfx.fill(px + PANEL_W - 1, py, px + PANEL_W, py + panelH, 0xFF222222);

        // Header
        gfx.fill(px + 2, py, px + PANEL_W - 1, py + HEADER_H, 0xFF0D0D0D);
        gfx.fill(px + 2, py + HEADER_H - 1, px + PANEL_W - 1, py + HEADER_H, accent);
        gfx.drawString(font, "SELECT ITEM", px + PAD, py + (HEADER_H - 8) / 2, accent, false);

        // Current selection
        String cur = module.itemName.getValue();
        String curDisplay = "Current: " + (cur.isEmpty() ? "none" : cur);
        gfx.drawString(font, curDisplay,
            px + PANEL_W - PAD - font.width(curDisplay) - 1,
            py + (HEADER_H - 8) / 2, 0xFF555555, false);

        // Search box
        int sy = py + HEADER_H + 4;
        gfx.fill(px + PAD, sy, px + PANEL_W - PAD, sy + SEARCH_H, 0xFF141414);
        gfx.fill(px + PAD, sy + SEARCH_H - 1, px + PANEL_W - PAD, sy + SEARCH_H, accent);
        String hint = search.isEmpty() ? "Search items..." : search + "|";
        int hintColor = search.isEmpty() ? 0xFF444444 : 0xFFFFFFFF;
        gfx.drawString(font, hint, px + PAD + 4, sy + (SEARCH_H - 8) / 2, hintColor, false);

        // Item grid
        int gridY = sy + SEARCH_H + PAD;
        int startIdx = scroll * COLS;
        hoveredIdx = -1;

        gfx.enableScissor(px + PAD, gridY, px + PANEL_W - PAD, gridY + ROWS * CELL);

        for (int i = 0; i < COLS * ROWS; i++) {
            int idx = startIdx + i;
            if (idx >= filtered.size()) break;
            int col = i % COLS;
            int row = i / COLS;
            int cx = px + PAD + col * CELL;
            int cy = gridY + row * CELL;

            boolean hov = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
            if (hov) hoveredIdx = idx;

            // Cell background
            if (hov) gfx.fill(cx, cy, cx + CELL, cy + CELL, 0xFF1A1A1A);

            // Item icon
            Item item = filtered.get(idx);
            gfx.renderItem(new ItemStack(item), cx + 2, cy + 2);

            // Highlight selected item
            if (item.getName().getString().equalsIgnoreCase(module.itemName.getValue())
                    || (BuiltInRegistries.ITEM.getKey(item) != null
                        && BuiltInRegistries.ITEM.getKey(item).getPath().replace("_", " ")
                            .equalsIgnoreCase(module.itemName.getValue()))) {
                gfx.fill(cx, cy, cx + CELL, cy + 1, accent);
                gfx.fill(cx, cy + CELL - 1, cx + CELL, cy + CELL, accent);
                gfx.fill(cx, cy, cx + 1, cy + CELL, accent);
                gfx.fill(cx + CELL - 1, cy, cx + CELL, cy + CELL, accent);
            }
        }

        gfx.disableScissor();

        // Scroll indicators
        int totalRows = (int) Math.ceil(filtered.size() / (double) COLS);
        int maxScroll = Math.max(0, totalRows - ROWS);
        if (scroll > 0)
            gfx.drawCenteredString(font, "\u25B2", px + PANEL_W - 8, gridY + 2, 0xFF555555);
        if (scroll < maxScroll)
            gfx.drawCenteredString(font, "\u25BC", px + PANEL_W - 8, gridY + ROWS * CELL - 10, 0xFF555555);

        // Tooltip for hovered item
        if (hoveredIdx >= 0 && hoveredIdx < filtered.size()) {
            Item item = filtered.get(hoveredIdx);
            String name = item.getName().getString();
            var key = BuiltInRegistries.ITEM.getKey(item);
            String id = key != null ? key.toString() : "";
            int tw = Math.max(font.width(name), font.width(id)) + 8;
            int tx = Math.min(mouseX + 8, width - tw - 4);
            int ty = mouseY - 24;
            gfx.fill(tx, ty, tx + tw, ty + 20, 0xFF0A0A0A);
            gfx.fill(tx, ty, tx + 2, ty + 20, accent);
            gfx.drawString(font, name, tx + 4, ty + 3, 0xFFEEEEEE, false);
            gfx.drawString(font, id, tx + 4, ty + 12, 0xFF555555, false);
        }

        // Footer hint
        gfx.drawCenteredString(font, "Click to select  •  Scroll to browse  •  Esc to close",
            width / 2, py + panelH - PAD - 8, 0xFF333333);
    }

    private void handleClick(int mouseX, int mouseY) {
        if (hoveredIdx >= 0 && hoveredIdx < filtered.size()) {
            Item item = filtered.get(hoveredIdx);
            // Store as the display name (lowercase) for the command
            var key = BuiltInRegistries.ITEM.getKey(item);
            String name = key != null ? key.getPath().replace("_", " ") : item.getName().getString().toLowerCase();
            module.itemName.setValue(name);
            Minecraft.getInstance().setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!search.isEmpty()) { search = search.substring(0, search.length() - 1); rebuildList(); }
            return true;
        }
        char c = keyToChar(key, event.modifiers());
        if (c != 0) { search += c; rebuildList(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int totalRows = (int) Math.ceil(filtered.size() / (double) COLS);
        int maxScroll = Math.max(0, totalRows - ROWS);
        scroll = Math.max(0, Math.min(scroll + (int) -sy, maxScroll));
        return true;
    }

    private char keyToChar(int key, int mods) {
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
            return shift ? (char)('A' + key - GLFW.GLFW_KEY_A) : (char)('a' + key - GLFW.GLFW_KEY_A);
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return (char)('0' + key - GLFW.GLFW_KEY_0);
        if (key == GLFW.GLFW_KEY_SPACE) return ' ';
        if (key == GLFW.GLFW_KEY_MINUS) return shift ? '_' : '-';
        return 0;
    }

    @Override public boolean isPauseScreen() { return false; }
}
