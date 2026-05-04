package com.dclient.client.gui;

import com.dclient.module.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Classic column-based ClickGUI (one panel per category). */
public class ClickGui extends Screen {
    private static final int PANEL_SPACING = 6;
    private static final int PANEL_START_Y = 46;
    private static final int SEARCH_H      = 22;
    private static final int SEARCH_W      = 180;
    private static final int SEARCH_X      = 10;
    private static final int SEARCH_Y      = 10;

    private final List<CategoryPanel> panels = new ArrayList<>();
    private String searchQuery = "";
    private boolean prevLeft = false, prevRight = false;
    public static String currentSearch = "";

    // Open animation
    private float openAnim = 0f;
    private long lastFrameMs = System.currentTimeMillis();

    public ClickGui() { super(Component.literal("DClient")); }

    @Override
    protected void init() {
        panels.clear();
        int x = SEARCH_X;
        for (Category cat : Category.values()) {
            panels.add(new CategoryPanel(cat, x, PANEL_START_Y));
            x += CategoryPanel.WIDTH + PANEL_SPACING;
        }
        openAnim = 0f;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        // If a text field is active, route control keys to it
        if (isAnyTextEditing()) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER
                    || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_BACKSPACE) {
                for (CategoryPanel panel : panels)
                    for (var sp : panel.getOpenSettingsPanels())
                        if (sp.isListening()) { sp.onKeyPressed(key); return true; }
            }
            // Printable chars go through charTyped path below
            char c = keyToChar(key, event.modifiers());
            if (c != 0) {
                for (CategoryPanel panel : panels)
                    for (var sp : panel.getOpenSettingsPanels())
                        if (sp.charTyped(c)) return true;
            }
            return true; // consume all keys while editing
        }

        // Key/bind listening (not text editing)
        if (isAnyListening()) { handleKeyListening(key); return true; }

        if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        char c = keyToChar(key, event.modifiers());
        if (c != 0) { searchQuery += c; return true; }
        return super.keyPressed(event);
    }

    private char keyToChar(int key, int mods) {
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
            return shift ? (char)('A' + key - GLFW.GLFW_KEY_A) : (char)('a' + key - GLFW.GLFW_KEY_A);
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            if (!shift) return (char)('0' + key - GLFW.GLFW_KEY_0);
            // Shift+number row
            return switch (key) {
                case GLFW.GLFW_KEY_1 -> '!'; case GLFW.GLFW_KEY_2 -> '@';
                case GLFW.GLFW_KEY_3 -> '#'; case GLFW.GLFW_KEY_4 -> '$';
                case GLFW.GLFW_KEY_5 -> '%'; case GLFW.GLFW_KEY_6 -> '^';
                case GLFW.GLFW_KEY_7 -> '&'; case GLFW.GLFW_KEY_8 -> '*';
                case GLFW.GLFW_KEY_9 -> '('; case GLFW.GLFW_KEY_0 -> ')';
                default -> 0;
            };
        }
        if (key == GLFW.GLFW_KEY_SPACE)     return ' ';
        if (key == GLFW.GLFW_KEY_PERIOD)    return shift ? '>' : '.';
        if (key == GLFW.GLFW_KEY_COMMA)     return shift ? '<' : ',';
        if (key == GLFW.GLFW_KEY_MINUS)     return shift ? '_' : '-';
        if (key == GLFW.GLFW_KEY_EQUAL)     return shift ? '+' : '=';
        if (key == GLFW.GLFW_KEY_SLASH)     return shift ? '?' : '/';
        if (key == GLFW.GLFW_KEY_SEMICOLON) return shift ? ':' : ';';
        if (key == GLFW.GLFW_KEY_APOSTROPHE)return shift ? '"' : '\'';
        if (key == GLFW.GLFW_KEY_LEFT_BRACKET)  return shift ? '{' : '[';
        if (key == GLFW.GLFW_KEY_RIGHT_BRACKET) return shift ? '}' : ']';
        if (key == GLFW.GLFW_KEY_BACKSLASH) return shift ? '|' : '\\';
        // Numpad
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9)
            return (char)('0' + key - GLFW.GLFW_KEY_KP_0);
        if (key == GLFW.GLFW_KEY_KP_DECIMAL) return '.';
        return 0;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long window = Minecraft.getInstance().getWindow().handle();
        int accent = ThemeUtil.accent();

        // ── Open animation ───────────────────────────────────────────────────
        long nowMs = System.currentTimeMillis();
        float dt = Math.min((nowMs - lastFrameMs) / 1000f, 0.1f);
        lastFrameMs = nowMs;
        if (openAnim < 1f) openAnim = Math.min(1f, openAnim + dt * 9f);
        float ease = openAnim < 1f ? (1f - (1f - openAnim) * (1f - openAnim)) : 1f;
        int panelOffsetY = openAnim < 1f ? (int)((1f - ease) * -20) : 0;
        int backdropAlpha = openAnim < 1f ? (int)(0xCC * ease) : 0xCC;
        gfx.fill(0, 0, width, height, (backdropAlpha << 24) | 0x000000);

        boolean leftDown  = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT)  == GLFW.GLFW_PRESS;
        boolean rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (leftDown) {
            if (!prevLeft) {
                // Check settings panels first — they float on top of category panels
                boolean settingsHandled = false;
                for (CategoryPanel p : panels) {
                    for (var sp : p.getOpenSettingsPanels()) {
                        if (mouseX >= sp.x && mouseX < sp.x + SettingsPanel.WIDTH
                            && mouseY >= sp.y && mouseY < sp.y + sp.getHeight()) {
                            sp.mouseClicked(mouseX, mouseY, 0);
                            settingsHandled = true;
                            break;
                        }
                    }
                    if (settingsHandled) break;
                }
                if (!settingsHandled) {
                    for (CategoryPanel p : panels) if (p.mouseClicked(mouseX, mouseY, 0)) break;
                }
            } else {
                for (CategoryPanel p : panels) if (p.mouseDragged(mouseX, mouseY)) break;
            }
        } else if (prevLeft) {
            for (CategoryPanel p : panels) p.mouseReleased();
        }
        if (rightDown && !prevRight) {
            // Check settings panels first for right-click too
            boolean settingsHandled = false;
            for (CategoryPanel p : panels) {
                for (var sp : p.getOpenSettingsPanels()) {
                    if (mouseX >= sp.x && mouseX < sp.x + SettingsPanel.WIDTH
                        && mouseY >= sp.y && mouseY < sp.y + sp.getHeight()) {
                        sp.mouseClicked(mouseX, mouseY, 1);
                        settingsHandled = true;
                        break;
                    }
                }
                if (settingsHandled) break;
            }
            if (!settingsHandled) {
                for (CategoryPanel p : panels) if (p.mouseClicked(mouseX, mouseY, 1)) break;
            }
        }
        prevLeft  = leftDown;
        prevRight = rightDown;

        // backdrop — already drawn with animation above

        // search bar — slides down with panels
        int sy = SEARCH_Y + panelOffsetY;
        gfx.fill(SEARCH_X, sy, SEARCH_X + SEARCH_W, sy + SEARCH_H, 0xFF0E0E0E);
        gfx.fill(SEARCH_X, sy, SEARCH_X + 2, sy + SEARCH_H, accent);
        gfx.fill(SEARCH_X + 2, sy + SEARCH_H - 1, SEARCH_X + SEARCH_W, sy + SEARCH_H, 0xFF2A2A2A);
        String displayText = searchQuery.isEmpty() ? "Search modules..." : searchQuery + "|";
        gfx.drawString(font, displayText, SEARCH_X + 8, sy + (SEARCH_H - 8) / 2,
            searchQuery.isEmpty() ? 0xFF3A3A3A : 0xFFDDDDDD, false);

        // Shift panels down for slide animation
        currentSearch = searchQuery;
        for (CategoryPanel panel : panels) {
            panel.y = PANEL_START_Y + panelOffsetY;
        }
        for (CategoryPanel panel : panels) panel.render(gfx, font, mouseX, mouseY);
        for (CategoryPanel panel : panels) panel.renderSettingsPanels(gfx, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // First check if hovering a settings panel
        for (CategoryPanel panel : panels) {
            for (var sp : panel.getOpenSettingsPanels()) {
                if (mouseX >= sp.x && mouseX < sp.x + SettingsPanel.WIDTH
                    && mouseY >= sp.y && mouseY < sp.y + sp.getHeight()) {
                    sp.scroll((int) -scrollY);
                    return true;
                }
            }
        }
        // Otherwise scroll the category panel
        for (CategoryPanel panel : panels) {
            if (mouseX >= panel.x && mouseX < panel.x + CategoryPanel.WIDTH) {
                panel.scroll((int) -scrollY); return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void handleKeyListening(int keyCode) {
        for (CategoryPanel panel : panels)
            for (var sp : panel.getOpenSettingsPanels())
                if (sp.isListening()) { sp.onKeyPressed(keyCode); return; }
    }
    /** True when a text input field is active (String setting being edited). */
    private boolean isAnyTextEditing() {
        for (CategoryPanel panel : panels)
            for (var sp : panel.getOpenSettingsPanels())
                if (sp.isTextEditing()) return true;
        return false;
    }
    /** True when a key-bind or key-setting listener is active (not text editing). */
    private boolean isAnyListening() {
        for (CategoryPanel panel : panels)
            for (var sp : panel.getOpenSettingsPanels())
                if (sp.isListening() && !sp.isTextEditing()) return true;
        return false;
    }

    @Override public boolean isPauseScreen() { return false; }
}
