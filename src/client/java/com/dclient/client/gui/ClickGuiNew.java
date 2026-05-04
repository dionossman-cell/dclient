package com.dclient.client.gui;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Single-window ClickGUI:
 *
 *  ┌─────────────────────────────────────────────────────┐
 *  │  DClient          [search________________]          │  ← top bar
 *  ├──────────┬──────────────────────────────────────────┤
 *  │ COMBAT   │  Module A          ●                     │
 *  │ MISC     │  Module B                                │
 *  │ DONUT    │  Module C          ●                     │
 *  │ RENDER   │  ...                                     │
 *  │ VISUALS  │                                          │
 *  │ CLIENT   │                                          │
 *  └──────────┴──────────────────────────────────────────┘
 *
 *  Right-clicking a module opens a SettingsPanel flyout to the right of the window.
 */
public class ClickGuiNew extends Screen {

    // Window dimensions — computed in init()
    private int winX, winY, winW, winH;
    private static final int WIN_W       = 480;
    private static final int WIN_H       = 300;
    private static final int SIDEBAR_W   = 80;
    private static final int TOP_BAR_H   = 30;
    private static final int ROW_H       = 18;
    private static final int PAD         = 8;

    // Colors
    private static final int C_WIN_BG    = 0xF2080808;
    private static final int C_SIDEBAR   = 0xF20B0B0B;
    private static final int C_TOP       = 0xFF0A0A0A;
    private static final int C_BORDER    = 0xFF1E1E1E;
    private static final int C_SEP       = 0xFF141414;
    private static final int C_ROW_HOVER = 0xFF111111;
    private static final int C_CAT_SEL   = 0xFF0F0F0F;
    private static final int C_TEXT      = 0xFFBBBBBB;
    private static final int C_DIM       = 0xFF444444;
    private static final int C_WHITE     = 0xFFEEEEEE;

    private Category selectedCategory = Category.COMBAT;
    private String searchQuery = "";
    private int moduleScroll = 0;

    // Tab switch animation — slide in from right
    private float listOffsetX = 0f;
    private float listOffsetTarget = 0f;
    private long lastFrameTime = System.currentTimeMillis();

    // GUI open animation — fade + scale in
    private float openAnim = 0f;        // 0 = closed, 1 = fully open
    private long openTime = -1;

    // Per-row hover animation — smooth highlight per module row
    private final Map<Integer, Float> rowHover = new HashMap<>();

    // Sidebar category hover animation
    private final Map<Integer, Float> catHover = new HashMap<>();
    // Sidebar selected category indicator slide (Y position)
    private float sidebarIndicatorY = -1f;
    private float sidebarIndicatorTargetY = -1f;

    // Settings panel open animation
    private float settingsPanelAnim = 0f;
    private Module lastOpenModule = null;

    // Open settings panel (one at a time)
    private Module openSettingsModule = null;
    private SettingsPanel openSettingsPanel = null;

    private boolean prevLeft = false, prevRight = false;
    public static String currentSearch = ""; // kept for SettingsPanel compat

    public ClickGuiNew() { super(Component.literal("DClient")); }

    @Override
    protected void init() {
        winX = (width  - WIN_W) / 2;
        winY = (height - WIN_H) / 2;
        winW = WIN_W;
        winH = WIN_H;
        moduleScroll = 0;
        openAnim = 0f;
        openTime = System.currentTimeMillis();
        rowHover.clear();
        catHover.clear();
        sidebarIndicatorY = -1f;
        sidebarIndicatorTargetY = -1f;
    }

    // ── input ────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (openSettingsPanel != null && openSettingsPanel.isListening()) {
            openSettingsPanel.onKeyPressed(key);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (openSettingsPanel != null) { openSettingsPanel = null; openSettingsModule = null; return true; }
            onClose(); return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            moduleScroll = 0; return true;
        }
        char c = keyToChar(key, event.modifiers());
        if (c != 0) { searchQuery += c; moduleScroll = 0; return true; }
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
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        // Scroll settings panel if hovering it
        if (openSettingsPanel != null
            && mx >= openSettingsPanel.x && mx < openSettingsPanel.x + SettingsPanel.WIDTH
            && my >= openSettingsPanel.y && my < openSettingsPanel.y + openSettingsPanel.getHeight()) {
            openSettingsPanel.scroll((int) -sy);
            return true;
        }
        if (isInModuleList(mx, my)) {
            moduleScroll = Math.max(0, moduleScroll + (int)-sy);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // ── render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long window = Minecraft.getInstance().getWindow().handle();
        int accent = ThemeUtil.accent();

        // ── Open animation — only compute when not fully open ────────────────
        long now0 = System.currentTimeMillis();
        float dt = Math.min((now0 - lastFrameTime) / 1000f, 0.1f);
        lastFrameTime = now0;
        if (openAnim < 1f) openAnim = Math.min(1f, openAnim + dt * 8f);
        float ease = openAnim < 1f ? (1f - (1f - openAnim) * (1f - openAnim) * (1f - openAnim)) : 1f;

        // Backdrop
        int backdropAlpha = openAnim < 1f ? (int)(0xBB * ease) : 0xBB;
        gfx.fill(0, 0, width, height, (backdropAlpha << 24) | 0x000000);

        // Window bounds — only scale during animation, use fixed size when done
        if (openAnim < 1f) {
            float scale = 0.92f + ease * 0.08f;
            int cx = width / 2, centerY = height / 2;
            int sw = (int)(WIN_W * scale), sh = (int)(WIN_H * scale);
            winX = cx - sw / 2; winY = centerY - sh / 2; winW = sw; winH = sh;
        } else {
            // Fixed position — no recalculation every frame
            winX = (width - WIN_W) / 2; winY = (height - WIN_H) / 2; winW = WIN_W; winH = WIN_H;
        }

        boolean leftDown  = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT)  == GLFW.GLFW_PRESS;
        boolean rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (leftDown && !prevLeft)   handleClick(mouseX, mouseY, 0);
        if (rightDown && !prevRight) handleClick(mouseX, mouseY, 1);
        if (leftDown && prevLeft && openSettingsPanel != null)
            openSettingsPanel.mouseDragged(mouseX, mouseY);
        if (!leftDown && prevLeft && openSettingsPanel != null)
            openSettingsPanel.mouseReleased();

        prevLeft  = leftDown;
        prevRight = rightDown;

        // ── window shadow ───────────────────────────────────────────────────
        gfx.fill(winX + 4, winY + 4, winX + winW + 4, winY + winH + 4, 0x55000000);

        // ── window background ───────────────────────────────────────────────
        gfx.fill(winX, winY, winX + winW, winY + winH, C_WIN_BG);

        // ── top bar ─────────────────────────────────────────────────────────
        gfx.fill(winX, winY, winX + winW, winY + TOP_BAR_H, C_TOP);
        // Accent line — animated during open, full width when done
        if (openAnim < 1f) {
            int accentW = (int)(winW * ease);
            int accentMid = winX + winW / 2;
            gfx.fill(accentMid - accentW/2, winY + TOP_BAR_H - 1, accentMid + accentW/2, winY + TOP_BAR_H, accent);
        } else {
            gfx.fill(winX, winY + TOP_BAR_H - 1, winX + winW, winY + TOP_BAR_H, accent);
        }

        // Client name
        gfx.drawString(font, "DClient", winX + PAD, winY + (TOP_BAR_H - 8) / 2, accent, false);

        // Search box (right side of top bar)
        int sbX = winX + winW - 130 - PAD;
        int sbY = winY + 4;
        int sbW = 130, sbH = TOP_BAR_H - 8;
        gfx.fill(sbX, sbY, sbX + sbW, sbY + sbH, 0xFF0D0D0D);
        gfx.fill(sbX, sbY + sbH - 1, sbX + sbW, sbY + sbH, accent);
        String searchDisplay = searchQuery.isEmpty() ? "search..." : searchQuery + "|";
        int searchColor = searchQuery.isEmpty() ? 0xFF333333 : C_TEXT;
        gfx.drawString(font, searchDisplay, sbX + 5, sbY + (sbH - 8) / 2, searchColor, false);

        // ── sidebar ─────────────────────────────────────────────────────────
        int sideX = winX;
        int sideY = winY + TOP_BAR_H;
        int sideH = winH - TOP_BAR_H;
        gfx.fill(sideX, sideY, sideX + SIDEBAR_W, sideY + sideH, C_SIDEBAR);
        gfx.fill(sideX + SIDEBAR_W - 1, sideY, sideX + SIDEBAR_W, sideY + sideH, C_BORDER);

        Category[] cats = Category.values();
        int catRowH = sideH / cats.length;

        // Sliding accent indicator — animates to selected category
        int selIdx = 0;
        for (int i = 0; i < cats.length; i++) if (cats[i] == selectedCategory) { selIdx = i; break; }
        float targetIndY = sideY + selIdx * catRowH;
        if (sidebarIndicatorY < 0) sidebarIndicatorY = targetIndY;
        sidebarIndicatorY += (targetIndY - sidebarIndicatorY) * Math.min(1f, dt * 22f);
        gfx.fill(sideX, (int)sidebarIndicatorY, sideX + 2, (int)sidebarIndicatorY + catRowH, accent);

        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            int catY = sideY + i * catRowH;
            boolean sel = cat == selectedCategory;
            boolean hov = mouseX >= sideX && mouseX < sideX + SIDEBAR_W - 1
                       && mouseY >= catY && mouseY < catY + catRowH;

            // Smooth per-category hover brightness
            float ch = catHover.getOrDefault(i, 0f);
            ch += ((hov ? 1f : 0f) - ch) * Math.min(1f, dt * 16f);
            catHover.put(i, ch);

            if (sel) gfx.fill(sideX + 2, catY, sideX + SIDEBAR_W - 1, catY + catRowH, C_CAT_SEL);
            else if (ch > 0.01f) {
                int ha = (int)(ch * 0x12);
                gfx.fill(sideX + 2, catY, sideX + SIDEBAR_W - 1, catY + catRowH, (ha << 24) | 0xFFFFFF);
            }

            if (i > 0) gfx.fill(sideX + 4, catY, sideX + SIDEBAR_W - 4, catY + 1, C_SEP);

            int catColor = sel ? accent : lerpColor(C_DIM, C_TEXT, ch);
            String catName = cat.name;
            gfx.drawString(font, catName,
                sideX + (SIDEBAR_W - font.width(catName)) / 2,
                catY + (catRowH - 8) / 2,
                catColor, false);
        }

        // ── module list ─────────────────────────────────────────────────────
        int listX = winX + SIDEBAR_W;
        int listY = winY + TOP_BAR_H;
        int listW = winW - SIDEBAR_W;
        int listH = winH - TOP_BAR_H;

        // Animate listOffsetX toward 0 (tab slide animation)
        if (Math.abs(listOffsetX - listOffsetTarget) > 0.5f) {
            listOffsetX += (listOffsetTarget - listOffsetX) * Math.min(1f, dt * 18f);
        } else {
            listOffsetX = listOffsetTarget;
        }
        int slideX = (int) listOffsetX;

        // Animate settings panel open
        if (openSettingsModule != null) {
            if (openSettingsModule != lastOpenModule) { settingsPanelAnim = 0f; lastOpenModule = openSettingsModule; }
            settingsPanelAnim = Math.min(1f, settingsPanelAnim + dt * 10f);
        } else {
            settingsPanelAnim = 0f; lastOpenModule = null;
        }

        // Clip module list to its bounds
        gfx.enableScissor(listX, listY, listX + listW, listY + listH);

        List<Module> modules = getVisibleModules();
        int maxVisible = listH / ROW_H;
        int maxScroll = Math.max(0, modules.size() - maxVisible);
        moduleScroll = Math.max(0, Math.min(moduleScroll, maxScroll));

        for (int i = 0; i < maxVisible && (i + moduleScroll) < modules.size(); i++) {
            Module mod = modules.get(i + moduleScroll);
            int ry = listY + i * ROW_H;
            int rx = listX + slideX;
            boolean hov = isInModuleList(mouseX, mouseY) && mouseY >= ry && mouseY < ry + ROW_H;
            boolean isOpen = mod == openSettingsModule;

            // Smooth per-row hover animation
            float rh = rowHover.getOrDefault(i, 0f);
            rh += (((hov || isOpen) ? 1f : 0f) - rh) * Math.min(1f, dt * 18f);
            rowHover.put(i, rh);

            if (rh > 0.01f) {
                int rowAlpha = (int)(rh * 0xFF);
                int rowBg = (rowAlpha << 24) | (C_ROW_HOVER & 0x00FFFFFF);
                gfx.fill(rx, ry, rx + listW, ry + ROW_H, rowBg);
                // Subtle left accent line on hover
                if (rh > 0.3f) {
                    int lineAlpha = (int)((rh - 0.3f) / 0.7f * 0xFF);
                    gfx.fill(rx, ry, rx + 1, ry + ROW_H, (lineAlpha << 24) | (accent & 0x00FFFFFF));
                }
            }
            if (i > 0) gfx.fill(rx + 4, ry, rx + listW - 4, ry + 1, C_SEP);

            // Enabled indicator — smooth color between off/on
            int dotColor = mod.isEnabled() ? accent : 0xFF2A2A2A;
            gfx.fill(rx + 6, ry + ROW_H / 2 - 2, rx + 10, ry + ROW_H / 2 + 2, dotColor);

            int nameColor = mod.isEnabled() ? C_WHITE : C_TEXT;
            gfx.drawString(font, mod.name, rx + 16, ry + (ROW_H - 8) / 2, nameColor, false);

            if (mod.getBind() != -1) {
                String bindStr = SettingsPanel.getKeyName(mod.getBind());
                gfx.drawString(font, bindStr, rx + listW - 22 - font.width(bindStr),
                    ry + (ROW_H - 8) / 2, 0xFF3A3A3A, false);
            }

            if (!mod.getSettings().isEmpty()) {
                String chev = isOpen ? "\u25BE" : "\u25B8";
                gfx.drawString(font, chev, rx + listW - 12, ry + (ROW_H - 8) / 2,
                    isOpen ? accent : lerpColor(C_DIM, C_TEXT, rh * 0.6f), false);
            }
        }

        if (maxScroll > 0) {
            int dotX = listX + listW - 4;
            for (int i = 0; i < maxVisible; i++) {
                int dotY = listY + i * ROW_H + ROW_H / 2 - 1;
                boolean active = (i + moduleScroll) < modules.size();
                gfx.fill(dotX, dotY, dotX + 2, dotY + 2, active ? 0xFF333333 : 0xFF1A1A1A);
            }
        }

        gfx.disableScissor();

        //  outer border ────────────────────────────────────────────────────
        gfx.fill(winX, winY, winX + winW, winY + 1, C_BORDER);
        gfx.fill(winX, winY + winH - 1, winX + winW, winY + winH, C_BORDER);
        gfx.fill(winX, winY, winX + 1, winY + winH, C_BORDER);
        gfx.fill(winX + winW - 1, winY, winX + winW, winY + winH, C_BORDER);

        // ── settings flyout ─────────────────────────────────────────────────
        if (openSettingsPanel != null) {
            int spX = winX + winW + 4;
            int spH = openSettingsPanel.getHeight();
            // Flip left if it would go off screen
            if (spX + SettingsPanel.WIDTH > width - 4) spX = winX - SettingsPanel.WIDTH - 4;
            // Clamp vertically
            int spY = winY;
            if (spY + spH > height - 4) spY = Math.max(4, height - 4 - spH);
            openSettingsPanel.x = spX;
            openSettingsPanel.y = spY;
            openSettingsPanel.render(gfx, font, mouseX, mouseY);
        }
    }

    // ── click handling ───────────────────────────────────────────────────────

    private void handleClick(int mx, int my, int button) {
        // Settings flyout — handle clicks inside it
        if (openSettingsPanel != null) {
            int spX = winX + winW + 4;
            if (spX + SettingsPanel.WIDTH > width - 4) spX = winX - SettingsPanel.WIDTH - 4;
            int spH = openSettingsPanel.getHeight();
            int spY = winY;
            if (spY + spH > height - 4) spY = Math.max(4, height - 4 - spH);
            openSettingsPanel.x = spX; openSettingsPanel.y = spY;
            if (mx >= spX && mx < spX + SettingsPanel.WIDTH
                && my >= spY && my < spY + spH) {
                openSettingsPanel.mouseClicked(mx, my, button);
                return;
            }
            boolean inModuleList = isInModuleList(mx, my);
            if (!inModuleList) {
                openSettingsPanel = null;
                openSettingsModule = null;
            }
        }

        // Sidebar category select
        Category[] cats = Category.values();
        int sideY = winY + TOP_BAR_H;
        int sideH = winH - TOP_BAR_H;
        int catRowH = sideH / cats.length;
        if (mx >= winX && mx < winX + SIDEBAR_W - 1) {
            for (int i = 0; i < cats.length; i++) {
                int catY = sideY + i * catRowH;
                if (my >= catY && my < catY + catRowH) {
                    if (cats[i] != selectedCategory) {
                        // Slide in from right
                        listOffsetX = 30f;
                        listOffsetTarget = 0f;
                    }
                    selectedCategory = cats[i];
                    moduleScroll = 0;
                    openSettingsPanel = null;
                    openSettingsModule = null;
                    return;
                }
            }
        }

        // Module list
        if (isInModuleList(mx, my)) {
            List<Module> modules = getVisibleModules();
            int listY = winY + TOP_BAR_H;
            int idx = (my - listY) / ROW_H + moduleScroll;
            if (idx >= 0 && idx < modules.size()) {
                Module mod = modules.get(idx);
                if (button == 0) {
                    mod.toggle();
                } else {
                    // Right-click: toggle settings panel
                    if (openSettingsModule == mod) {
                        openSettingsPanel = null;
                        openSettingsModule = null;
                    } else if (!mod.getSettings().isEmpty()) {
                        openSettingsModule = mod;
                        openSettingsPanel = new SettingsPanel(mod, winX + winW + 4, winY);
                    }
                }
            }
        }
    }

    private boolean isInModuleList(double mx, double my) {
        int listX = winX + SIDEBAR_W;
        int listY = winY + TOP_BAR_H;
        return mx >= listX && mx < winX + winW && my >= listY && my < winY + winH;
    }

    private List<Module> getVisibleModules() {
        List<Module> base = searchQuery.isEmpty()
            ? ModuleManager.getByCategory(selectedCategory)
            : ModuleManager.getAll().stream()
                .filter(m -> m.name.toLowerCase().contains(searchQuery.toLowerCase()))
                .toList();
        return base;
    }

    @Override public boolean isPauseScreen() { return false; }

    /** Linearly interpolate between two ARGB colors by t (0-1). */
    private static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = ar + (int)((br - ar) * t);
        int rg = ag + (int)((bg - ag) * t);
        int rb = ab + (int)((bb - ab) * t);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }
}
