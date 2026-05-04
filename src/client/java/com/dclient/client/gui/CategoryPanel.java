package com.dclient.client.gui;

import com.dclient.module.Module;
import com.dclient.module.Category;
import com.dclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.*;

public class CategoryPanel {
    public static final int WIDTH    = 130;
    public static final int HEADER_H = 24;
    public static final int ROW_H    = 16;
    private static final int PAD     = 6;

    // Colors
    private static final int C_BG        = 0xF00A0A0A;
    private static final int C_HEADER    = 0xFF0E0E0E;
    private static final int C_HDR_TXT   = 0xFFFFFFFF;
    private static final int C_HOVER     = 0xFF161616;
    private static final int C_OFF       = 0xFF606060;
    private static final int C_BORDER    = 0xFF222222;
    private static final int C_SEPARATOR = 0xFF1A1A1A;
    private static final int C_ARROW     = 0xFF444444;

    public final Category category;
    public int x, y;
    private final List<Module> allModules;
    private final Map<Integer, SettingsPanel> openSettings = new HashMap<>();
    private int scrollOffset = 0;

    // Per-row hover animation
    private final Map<Integer, Float> rowHover = new HashMap<>();
    // Panel open animation (stagger)
    private float panelAnim = 0f;
    private long lastFrameMs = System.currentTimeMillis();

    public CategoryPanel(Category category, int x, int y) {
        this.category = category;
        this.x = x;
        this.y = y;
        this.allModules = ModuleManager.getByCategory(category);
        this.panelAnim = 0f;
        this.lastFrameMs = System.currentTimeMillis();
    }

    private List<Module> getVisible(String search) {
        if (search == null || search.isEmpty()) return allModules;
        String q = search.toLowerCase();
        return allModules.stream().filter(m -> m.name.toLowerCase().contains(q)).toList();
    }

    public void scroll(int delta) { scrollOffset = Math.max(0, scrollOffset + delta); }
    public Collection<SettingsPanel> getOpenSettingsPanels() { return openSettings.values(); }

    public void render(GuiGraphics gfx, Font font, int mouseX, int mouseY) {
        int accent = ThemeUtil.accent();
        List<Module> modules = getVisible(ClickGui.currentSearch);
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int maxPanelH = screenH - y - 6;

        int maxScroll = Math.max(0, modules.size() - (maxPanelH - HEADER_H) / ROW_H);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int visibleRows = (maxPanelH - HEADER_H) / ROW_H;
        int panelH = HEADER_H;
        for (int i = 0; i < visibleRows && (i + scrollOffset) < modules.size(); i++) panelH += ROW_H;
        panelH = Math.min(panelH, maxPanelH);

        // Panel open animation
        long nowMs = System.currentTimeMillis();
        float dt = Math.min((nowMs - lastFrameMs) / 1000f, 0.1f);
        lastFrameMs = nowMs;
        if (panelAnim < 1f) panelAnim = Math.min(1f, panelAnim + dt * 10f);
        float ease = panelAnim < 1f ? (1f - (1f - panelAnim) * (1f - panelAnim)) : 1f;
        int animH = (int)(panelH * ease);
        int animAlpha = (int)(0xF0 * ease);

        // Drop shadow
        gfx.fill(x + 3, y + 3, x + WIDTH + 3, y + animH + 3, (int)(0x44 * ease) << 24);

        // Panel background — fade in with animation
        gfx.fill(x, y, x + WIDTH, y + animH, (animAlpha << 24) | (C_BG & 0x00FFFFFF));

        // Left accent bar
        gfx.fill(x, y, x + 2, y + animH, accent);

        // Borders
        gfx.fill(x + 2, y,            x + WIDTH, y + 1,        C_BORDER);
        gfx.fill(x + 2, y + animH - 1, x + WIDTH, y + animH,   C_BORDER);
        gfx.fill(x + WIDTH - 1, y,    x + WIDTH, y + animH,    C_BORDER);

        // Header
        gfx.fill(x + 2, y, x + WIDTH - 1, y + HEADER_H, C_HEADER);
        gfx.fill(x + 2, y + HEADER_H - 1, x + WIDTH - 1, y + HEADER_H, accent);
        String catName = category.name.toUpperCase();
        gfx.drawString(font, catName,
            x + 2 + (WIDTH - 2) / 2 - font.width(catName) / 2,
            y + (HEADER_H - 8) / 2,
            C_HDR_TXT, false);

        // Scroll indicators
        if (scrollOffset > 0)
            gfx.drawString(font, "\u25B2", x + WIDTH / 2 - 3, y + HEADER_H + 1, C_ARROW, false);
        if (scrollOffset < maxScroll)
            gfx.drawString(font, "\u25BC", x + WIDTH / 2 - 3, y + panelH - 10, C_ARROW, false);

        // Clip to panel bounds
        gfx.enableScissor(x + 2, y + HEADER_H, x + WIDTH - 1, y + animH - 1);

        int curY = y + HEADER_H;
        for (int i = 0; i < visibleRows && (i + scrollOffset) < modules.size(); i++) {
            int modIdx = i + scrollOffset;
            Module mod = modules.get(modIdx);
            int rowY = curY;
            if (rowY + ROW_H > y + panelH) break;

            boolean hov = mouseX >= x + 2 && mouseX < x + WIDTH - 1
                       && mouseY >= rowY && mouseY < rowY + ROW_H;

            // Smooth per-row hover
            float rh = rowHover.getOrDefault(i, 0f);
            rh += ((hov ? 1f : 0f) - rh) * Math.min(1f, dt * 16f);
            rowHover.put(i, rh);

            if (rh > 0.01f) {
                int ha = (int)(rh * 0xFF);
                gfx.fill(x + 2, rowY, x + WIDTH - 1, rowY + ROW_H, (ha << 24) | (C_HOVER & 0x00FFFFFF));
                // Subtle left accent line
                if (rh > 0.4f) {
                    int la = (int)((rh - 0.4f) / 0.6f * 0xFF);
                    gfx.fill(x + 2, rowY, x + 3, rowY + ROW_H, (la << 24) | (accent & 0x00FFFFFF));
                }
            }

            if (i > 0) gfx.fill(x + 4, rowY, x + WIDTH - 2, rowY + 1, C_SEPARATOR);

            if (mod.isEnabled()) {
                int dotX = x + 6;
                int dotY = rowY + ROW_H / 2 - 2;
                gfx.fill(dotX, dotY, dotX + 4, dotY + 4, accent);
            }

            int textX = x + 14;
            int textColor = mod.isEnabled() ? 0xFFEEEEEE : C_OFF;
            gfx.drawString(font, mod.name, textX, rowY + (ROW_H - 8) / 2, textColor, false);

            boolean hasSettings = !mod.getSettings().isEmpty();
            if (hasSettings) {
                boolean open = openSettings.containsKey(modIdx);
                String arrow = open ? "\u25BE" : "\u25B8";
                gfx.drawString(font, arrow,
                    x + WIDTH - PAD - font.width(arrow) - 1,
                    rowY + (ROW_H - 8) / 2,
                    open ? accent : C_ARROW, false);
            }

            curY += ROW_H;
        }

        gfx.disableScissor();
    }

    public void renderSettingsPanels(GuiGraphics gfx, Font font, int mouseX, int mouseY) {
        List<Module> modules = getVisible(ClickGui.currentSearch);
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int maxPanelH = screenH - y - 6;
        int visibleRows = (maxPanelH - HEADER_H) / ROW_H;

        for (int i = 0; i < visibleRows && (i + scrollOffset) < modules.size(); i++) {
            int modIdx = i + scrollOffset;
            if (!openSettings.containsKey(modIdx)) continue;
            int rowY = y + HEADER_H + i * ROW_H;
            SettingsPanel sp = openSettings.get(modIdx);

            // Flip left if panel would go off the right edge
            int spX = x + WIDTH + 4;
            if (spX + SettingsPanel.WIDTH > screenW - 4) {
                spX = x - SettingsPanel.WIDTH - 4;
            }

            // Clamp vertically so panel doesn't go off the bottom
            int spY = rowY;
            int spH = sp.getHeight();
            if (spY + spH > screenH - 4) {
                spY = Math.max(4, screenH - 4 - spH);
            }

            sp.x = spX;
            sp.y = spY;
            sp.render(gfx, font, mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<Module> modules = getVisible(ClickGui.currentSearch);
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int maxPanelH = screenH - y - 6;
        int visibleRows = (maxPanelH - HEADER_H) / ROW_H;

        int curY = y + HEADER_H;
        for (int i = 0; i < visibleRows && (i + scrollOffset) < modules.size(); i++) {
            int modIdx = i + scrollOffset;
            int rowY = curY;
            curY += ROW_H;

            // Settings panel click first
            if (openSettings.containsKey(modIdx)) {
                SettingsPanel sp = openSettings.get(modIdx);
                int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                int spX = x + WIDTH + 4;
                if (spX + SettingsPanel.WIDTH > sw - 4) spX = x - SettingsPanel.WIDTH - 4;
                int spY = y + HEADER_H + i * ROW_H;
                int spH = sp.getHeight();
                if (spY + spH > sh - 4) spY = Math.max(4, sh - 4 - spH);
                sp.x = spX; sp.y = spY;
                if (mouseX >= spX && mouseX < spX + SettingsPanel.WIDTH
                    && mouseY >= spY && mouseY < spY + spH) {
                    sp.mouseClicked(mouseX, mouseY, button);
                    return true;
                }
            }

            // Module row
            if (mouseX >= x + 2 && mouseX < x + WIDTH - 1 && mouseY >= rowY && mouseY < rowY + ROW_H) {
                if (button == 1) {
                    // Right-click: toggle settings panel
                    if (openSettings.containsKey(modIdx)) {
                        openSettings.remove(modIdx);
                    } else {
                        var settings = modules.get(modIdx).getSettings();
                        if (!settings.isEmpty())
                            openSettings.put(modIdx, new SettingsPanel(modules.get(modIdx), x, rowY));
                    }
                } else {
                    modules.get(modIdx).toggle();
                }
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        for (SettingsPanel sp : openSettings.values())
            if (sp.mouseDragged(mouseX, mouseY)) return true;
        return false;
    }

    public void mouseReleased() {
        for (SettingsPanel sp : openSettings.values()) sp.mouseReleased();
    }

    public List<Module> getModules() { return allModules; }
    public void closeAllSettings() { openSettings.clear(); }
}
