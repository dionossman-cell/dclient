package com.dclient.client.gui;

import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsPanel {
    public static final int WIDTH  = 170;
    private static final int ROW_H = 18;
    private static final int PAD   = 6;
    private static final int HEADER_H = 22;
    // Max visible height before scrolling kicks in
    private static final int MAX_H = 260;

    // Colors
    private static final int C_BG        = 0xF20A0A0A;
    private static final int C_HEADER    = 0xFF0D0D0D;
    private static final int C_BORDER    = 0xFF222222;
    private static final int C_SEP       = 0xFF181818;
    private static final int C_TEXT      = 0xFFCCCCCC;
    private static final int C_DIM       = 0xFF555555;
    private static final int C_HOVER     = 0xFF141414;
    private static final int C_TRACK     = 0xFF1E1E1E;
    private static final int C_TOGGLE_BG = 0xFF252525;
    private static final int C_BTN       = 0xFF181818;
    private static final int C_BIND      = 0xFF5599FF;
    private static final int C_LISTEN    = 0xFFFFAA33;
    private static final String[] MODE_NAMES = {"3D", "2D", "Corner", "Skel"};

    public final Module module;
    public int x, y;
    private boolean listeningForBind = false;
    private Setting<Integer> listeningKeySetting = null;
    private Setting<?> draggingSetting = null;
    // Text input state for String settings
    private Setting<String> editingSetting = null;
    private String editBuffer = "";
    // Vertical scroll (in pixels)
    private int scrollOffset = 0;
    // Per-toggle animation state (setting name -> knob position 0-1)
    private final Map<String, Float> toggleAnims = new HashMap<>();
    private long lastToggleFrameMs = System.currentTimeMillis();

    public SettingsPanel(Module module, int x, int y) {
        this.module = module;
        this.x = x;
        this.y = y;
        this.lastToggleFrameMs = System.currentTimeMillis();
    }

    /** Called when the mouse wheel is scrolled over this panel. */
    public void scroll(int delta) {
        int fullH = getFullHeight();
        int visH  = Math.min(fullH, MAX_H);
        int maxScroll = Math.max(0, fullH - visH + HEADER_H);
        scrollOffset = Math.max(0, Math.min(scrollOffset + delta * ROW_H, maxScroll));
    }

    // ── type helpers ─────────────────────────────────────────────────────────

    private int getModeCount() {
        if (module instanceof com.dclient.module.modules.render.PlayerESP) return 4;
        return 3;
    }
    private boolean isModeButton(Setting<?> s) {
        return s.name.equals("Mode") && s.getDefaultValue() instanceof Integer && !s.hasBounds();
    }
    private boolean isKeySetting(Setting<?> s) {
        return s.getDefaultValue() instanceof Integer && s.name.toLowerCase().contains("key");
    }
    private boolean isSlider(Setting<?> s) {
        return s.hasBounds() && (s.getDefaultValue() instanceof Float || s.getDefaultValue() instanceof Integer);
    }
    private boolean hasBlockSelector() {
        return module instanceof com.dclient.module.modules.render.BlockESP
            || module instanceof com.dclient.module.modules.render.WallHack;
    }
    private boolean hasHudEditor() {
        return module instanceof com.dclient.module.modules.render.HUD;
    }
    private boolean hasItemPicker() {
        return module instanceof com.dclient.module.modules.donut.AhSniper;
    }

    // ── height ───────────────────────────────────────────────────────────────

    /** Total uncapped content height. */
    public int getFullHeight() {
        int h = HEADER_H + PAD;
        for (Setting<?> s : module.getSettings()) {
            if (isModeButton(s)) h += ROW_H + ROW_H + 2;
            else h += ROW_H;
        }
        h += ROW_H; // bind
        if (hasBlockSelector()) h += ROW_H;
        if (hasHudEditor())     h += ROW_H;
        if (hasItemPicker())    h += ROW_H;
        return h + PAD;
    }

    /** Visible height — capped at MAX_H so it never goes off screen. */
    public int getHeight() {
        return Math.min(getFullHeight(), MAX_H);
    }

    // ── render ────────────────────────────────────────────────────────────────

    public void render(GuiGraphics gfx, Font font, int mouseX, int mouseY) {
        int accent = ThemeUtil.accent();
        int visH = getHeight();
        int fullH = getFullHeight();
        boolean scrollable = fullH > visH;

        // Update toggle animation dt
        long nowMs = System.currentTimeMillis();
        float dt = Math.min((nowMs - lastToggleFrameMs) / 1000f, 0.1f);
        lastToggleFrameMs = nowMs;

        // Drop shadow
        gfx.fill(x + 3, y + 3, x + WIDTH + 3, y + visH + 3, 0x44000000);

        // Background
        gfx.fill(x, y, x + WIDTH, y + visH, C_BG);

        // Left accent bar
        gfx.fill(x, y, x + 2, y + visH, accent);

        // Borders
        gfx.fill(x + 2, y,            x + WIDTH, y + 1,       C_BORDER);
        gfx.fill(x + 2, y + visH - 1, x + WIDTH, y + visH,    C_BORDER);
        gfx.fill(x + WIDTH - 1, y,    x + WIDTH, y + visH,    C_BORDER);

        // Header — always visible, not scrolled
        gfx.fill(x + 2, y, x + WIDTH - 1, y + HEADER_H, C_HEADER);
        gfx.fill(x + 2, y + HEADER_H - 1, x + WIDTH - 1, y + HEADER_H, accent);
        String title = module.name.toUpperCase();
        gfx.drawString(font, title, x + PAD + 2, y + (HEADER_H - 8) / 2, accent, false);

        // Scroll indicator dots
        if (scrollable) {
            if (scrollOffset > 0)
                gfx.drawString(font, "\u25B2", x + WIDTH - 10, y + HEADER_H + 2, 0xFF555555, false);
            if (scrollOffset < fullH - visH)
                gfx.drawString(font, "\u25BC", x + WIDTH - 10, y + visH - 10, 0xFF555555, false);
        }

        // Enable scissor so rows don't draw outside the panel
        gfx.enableScissor(x + 2, y + HEADER_H, x + WIDTH - 1, y + visH - 1);

        // Content starts below header, offset by scroll
        int cy = y + HEADER_H + PAD - scrollOffset;
        List<Setting<?>> settings = module.getSettings();
        for (int si = 0; si < settings.size(); si++) {
            Setting<?> s = settings.get(si);
            // Skip rows fully above the visible area
            int rowH = isModeButton(s) ? ROW_H * 2 + 2 : ROW_H;
            if (cy + rowH < y + HEADER_H) { cy += rowH; continue; }
            // Stop drawing rows fully below the visible area
            if (cy > y + visH) break;

            boolean hov = mouseX >= x + 2 && mouseX < x + WIDTH - 1
                       && mouseY >= cy && mouseY < cy + ROW_H
                       && mouseY >= y + HEADER_H && mouseY < y + visH;

            // Row separator
            if (si > 0) gfx.fill(x + 4, cy, x + WIDTH - 2, cy + 1, C_SEP);

            if (isModeButton(s)) {
                gfx.drawString(font, "Mode", x + PAD + 2, cy + (ROW_H - 8) / 2, C_DIM, false);
                cy += ROW_H;
                @SuppressWarnings("unchecked") Setting<Integer> is = (Setting<Integer>) s;
                int modeCount = getModeCount();
                int totalW = WIDTH - PAD * 2 - 2;
                int bw = totalW / modeCount;
                for (int m = 0; m < modeCount; m++) {
                    int bx = x + PAD + 2 + m * bw;
                    boolean active = is.getValue() == m;
                    gfx.fill(bx, cy + 2, bx + bw - 1, cy + ROW_H - 2, active ? ThemeUtil.accentDark() : C_BTN);
                    if (active) gfx.fill(bx, cy + 2, bx + bw - 1, cy + 3, accent);
                    int tw = font.width(MODE_NAMES[m]);
                    gfx.drawString(font, MODE_NAMES[m],
                        bx + (bw - 1 - tw) / 2, cy + (ROW_H - 4 - 8) / 2,
                        active ? accent : C_DIM, false);
                }
                cy += ROW_H + 2;

            } else if (isSlider(s)) {
                if (hov || draggingSetting == s) gfx.fill(x + 2, cy, x + WIDTH - 1, cy + ROW_H, C_HOVER);
                renderSlider(gfx, font, s, cy, accent);
                cy += ROW_H;

            } else if (isKeySetting(s)) {
                if (hov) gfx.fill(x + 2, cy, x + WIDTH - 1, cy + ROW_H, C_HOVER);
                boolean listening = listeningKeySetting != null && listeningKeySetting == s;
                @SuppressWarnings("unchecked") Setting<Integer> is = (Setting<Integer>) s;
                String label = truncate(s.name, font, 80);
                gfx.drawString(font, label, x + PAD + 2, cy + (ROW_H - 8) / 2, C_TEXT, false);
                String kn = listening ? "..." : getKeyName(is.getValue());
                gfx.drawString(font, kn, x + WIDTH - PAD - font.width(kn) - 1,
                    cy + (ROW_H - 8) / 2, listening ? C_LISTEN : C_BIND, false);
                cy += ROW_H;

            } else if (s.getDefaultValue() instanceof Boolean) {
                if (hov) gfx.fill(x + 2, cy, x + WIDTH - 1, cy + ROW_H, C_HOVER);
                renderToggle(gfx, font, s, cy, accent);
                cy += ROW_H;

            } else if (s.getDefaultValue() instanceof String) {
                if (hov) gfx.fill(x + 2, cy, x + WIDTH - 1, cy + ROW_H, C_HOVER);
                String label = truncate(s.name, font, 65);
                gfx.drawString(font, label, x + PAD + 2, cy + (ROW_H - 8) / 2, C_TEXT, false);
                @SuppressWarnings("unchecked") Setting<String> ss = (Setting<String>) s;
                boolean isEditing = editingSetting == s;
                if (ss.options != null && ss.options.length > 1) {
                    String display = "\u25C2 " + ss.getValue() + " \u25B8";
                    gfx.drawString(font, display,
                        x + WIDTH - PAD - font.width(display) - 1,
                        cy + (ROW_H - 8) / 2, accent, false);
                } else if (isEditing) {
                    int inputX = x + PAD + 2 + font.width(label) + 4;
                    int inputW = WIDTH - PAD - 2 - font.width(label) - 4 - PAD;
                    gfx.fill(inputX - 1, cy + 2, inputX + inputW, cy + ROW_H - 2, 0xFF1A1A1A);
                    gfx.fill(inputX - 1, cy + ROW_H - 3, inputX + inputW, cy + ROW_H - 2, accent);
                    String buf = editBuffer + "|";
                    String truncBuf = truncate(buf, font, inputW - 2);
                    gfx.drawString(font, truncBuf, inputX + 1, cy + (ROW_H - 8) / 2, 0xFFFFFFFF, false);
                } else {
                    String val = ss.getValue().isEmpty() ? "\u2014" : ss.getValue();
                    String truncVal = truncate(val, font, WIDTH - PAD - 2 - font.width(label) - 8);
                    gfx.drawString(font, truncVal,
                        x + WIDTH - PAD - font.width(truncVal) - 1,
                        cy + (ROW_H - 8) / 2, accent, false);
                }
                cy += ROW_H;
            } else {
                if (hov) gfx.fill(x + 2, cy, x + WIDTH - 1, cy + ROW_H, C_HOVER);
                String label = truncate(s.name, font, 90);
                gfx.drawString(font, label, x + PAD + 2, cy + (ROW_H - 8) / 2, C_TEXT, false);
                String val = formatVal(s);
                String display = "\u25C2 " + val + " \u25B8";
                gfx.drawString(font, display,
                    x + WIDTH - PAD - font.width(display) - 1,
                    cy + (ROW_H - 8) / 2, accent, false);
                cy += ROW_H;
            }
        }

        // Separator before bind
        gfx.fill(x + 4, cy, x + WIDTH - 2, cy + 1, C_SEP);

        // Bind row
        boolean bHov = mouseX >= x + 2 && mouseX < x + WIDTH - 1
                    && mouseY >= cy && mouseY < cy + ROW_H
                    && mouseY >= y + HEADER_H && mouseY < y + visH;
        if (bHov) gfx.fill(x + 2, cy, x + WIDTH - 1, cy + ROW_H, C_HOVER);
        gfx.drawString(font, "Bind", x + PAD + 2, cy + (ROW_H - 8) / 2, C_DIM, false);
        String bt = listeningForBind ? "..." : (module.getBind() == -1 ? "None" : getKeyName(module.getBind()));
        gfx.drawString(font, bt, x + WIDTH - PAD - font.width(bt) - 1,
            cy + (ROW_H - 8) / 2, listeningForBind ? C_LISTEN : C_BIND, false);
        cy += ROW_H;

        // Action buttons
        if (hasBlockSelector()) { renderActionBtn(gfx, font, cy, "Select Blocks", accent, mouseX, mouseY); cy += ROW_H; }
        if (hasHudEditor())     { renderActionBtn(gfx, font, cy, "Edit HUD", 0xFFFFAA44, mouseX, mouseY); cy += ROW_H; }
        if (hasItemPicker())    { renderActionBtn(gfx, font, cy, "Pick Item", accent, mouseX, mouseY); }

        // Disable scissor
        gfx.disableScissor();
    }

    private void renderSlider(GuiGraphics gfx, Font font, Setting<?> s, int cy, int accent) {
        String label = truncate(s.name, font, 55);
        gfx.drawString(font, label, x + PAD + 2, cy + (ROW_H - 8) / 2, C_TEXT, false);

        String val = formatVal(s);
        int valW = font.width(val);
        gfx.drawString(font, val, x + WIDTH - PAD - valW - 1, cy + (ROW_H - 8) / 2, accent, false);

        int labelW = font.width(label);
        int trackX = x + PAD + 2 + labelW + 4;
        int trackW = WIDTH - PAD - 2 - labelW - 4 - PAD - valW - 4;
        int trackY = cy + ROW_H / 2 - 2;

        if (trackW > 8) {
            float pct = getSliderPct(s);
            int fillW = Math.max(0, (int)(pct * trackW));
            // Track background
            gfx.fill(trackX, trackY, trackX + trackW, trackY + 4, C_TRACK);
            // Fill
            if (fillW > 0) gfx.fill(trackX, trackY, trackX + fillW, trackY + 4, ThemeUtil.accent(160));
            // Thumb — 2px wide bright line
            int thumbX = trackX + fillW;
            gfx.fill(thumbX - 1, cy + 4, thumbX + 1, cy + ROW_H - 4, accent);
        }
    }

    private void renderToggle(GuiGraphics gfx, Font font, Setting<?> s, int cy, int accent) {
        @SuppressWarnings("unchecked") Setting<Boolean> bs = (Setting<Boolean>) s;
        boolean on = bs.getValue();
        String label = truncate(s.name, font, 100);
        gfx.drawString(font, label, x + PAD + 2, cy + (ROW_H - 8) / 2, on ? C_TEXT : C_DIM, false);

        // Animate knob position: 0 = off (left), 1 = on (right)
        float t = toggleAnims.getOrDefault(s.name, on ? 1f : 0f);
        float target = on ? 1f : 0f;
        t += (target - t) * Math.min(1f, lastToggleFrameMs > 0 ? 0.25f : 1f);
        // Use dt-based lerp: advance by fixed step per frame
        if (Math.abs(t - target) > 0.01f) t += (target - t) * 0.3f;
        else t = target;
        toggleAnims.put(s.name, t);

        // Pill: 26×10
        int pw = 26, ph = 10;
        int px = x + WIDTH - PAD - pw - 1;
        int py = cy + (ROW_H - ph) / 2;

        // Pill background — interpolate between off and on color
        int bgOff = C_TOGGLE_BG, bgOn = ThemeUtil.accent(140);
        int pillBg = lerpColor(bgOff, bgOn, t);
        gfx.fill(px, py, px + pw, py + ph, pillBg);

        // Knob — slides from left to right
        int knobTravel = pw - ph;
        int kx = px + 1 + (int)(t * knobTravel);
        int knobColor = lerpColor(C_DIM, accent, t);
        gfx.fill(kx, py + 1, kx + ph - 2, py + ph - 1, knobColor);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = ar + (int)((br - ar) * t);
        int rg = ag + (int)((bg - ag) * t);
        int rb = ab + (int)((bb - ab) * t);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }

    private void renderActionBtn(GuiGraphics gfx, Font font, int cy, String label, int color, int mx, int my) {
        boolean hov = mx >= x + PAD && mx < x + WIDTH - PAD && my >= cy + 2 && my < cy + ROW_H - 2;
        gfx.fill(x + PAD, cy + 2, x + WIDTH - PAD, cy + ROW_H - 2, hov ? ThemeUtil.accentDark() : C_BTN);
        if (hov) gfx.fill(x + PAD, cy + 2, x + WIDTH - PAD, cy + 3, ThemeUtil.accent());
        gfx.drawCenteredString(font, label, x + WIDTH / 2, cy + (ROW_H - 8) / 2, color);
    }

    // ── mouse ─────────────────────────────────────────────────────────────────

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int visH = getHeight();
        // Ignore clicks outside the visible panel area
        if (mouseY < y + HEADER_H || mouseY > y + visH) return false;

        int cy = y + HEADER_H + PAD - scrollOffset;
        for (Setting<?> s : module.getSettings()) {
            if (isModeButton(s)) {
                cy += ROW_H;
                @SuppressWarnings("unchecked") Setting<Integer> is = (Setting<Integer>) s;
                int modeCount = getModeCount();
                int bw = (WIDTH - PAD * 2 - 2) / modeCount;
                for (int m = 0; m < modeCount; m++) {
                    int bx = x + PAD + 2 + m * bw;
                    if (mouseX >= bx && mouseX < bx + bw - 1 && mouseY >= cy + 2 && mouseY < cy + ROW_H - 2) {
                        is.setValue(m); return true;
                    }
                }
                cy += ROW_H + 2;

            } else if (isSlider(s)) {
                if (mouseX >= x + 2 && mouseX < x + WIDTH - 1 && mouseY >= cy && mouseY < cy + ROW_H) {
                    draggingSetting = s;
                    applySliderDrag(s, (int) mouseX, null);
                    return true;
                }
                cy += ROW_H;

            } else if (isKeySetting(s)) {
                if (mouseX >= x + 2 && mouseX < x + WIDTH - 1 && mouseY >= cy && mouseY < cy + ROW_H) {
                    @SuppressWarnings("unchecked") Setting<Integer> is = (Setting<Integer>) s;
                    if (button == 1) { is.setValue((Integer) is.getDefaultValue()); listeningKeySetting = null; }
                    else { listeningKeySetting = (listeningKeySetting == is) ? null : is; listeningForBind = false; }
                    return true;
                }
                cy += ROW_H;

            } else if (s.getDefaultValue() instanceof Boolean) {
                if (mouseX >= x + 2 && mouseX < x + WIDTH - 1 && mouseY >= cy && mouseY < cy + ROW_H) {
                    @SuppressWarnings("unchecked") Setting<Boolean> bs = (Setting<Boolean>) s;
                    bs.setValue(!bs.getValue()); return true;
                }
                cy += ROW_H;

            } else if (s.getDefaultValue() instanceof String) {
                if (mouseX >= x + 2 && mouseX < x + WIDTH - 1 && mouseY >= cy && mouseY < cy + ROW_H) {
                    @SuppressWarnings("unchecked") Setting<String> ss = (Setting<String>) s;
                    if (ss.options != null && ss.options.length > 1) {
                        int idx = 0;
                        for (int oi = 0; oi < ss.options.length; oi++)
                            if (ss.options[oi].equals(ss.getValue())) { idx = oi; break; }
                        if (button == 0) idx = (idx + 1) % ss.options.length;
                        else idx = (idx - 1 + ss.options.length) % ss.options.length;
                        ss.setValue(ss.options[idx]);
                    } else {
                        if (editingSetting == ss) {
                            ss.setValue(editBuffer);
                            editingSetting = null; editBuffer = "";
                        } else {
                            editingSetting = ss;
                            editBuffer = ss.getValue();
                            listeningForBind = false; listeningKeySetting = null;
                        }
                    }
                    return true;
                }
                cy += ROW_H;

            } else {
                if (mouseX >= x + 2 && mouseX < x + WIDTH - 1 && mouseY >= cy && mouseY < cy + ROW_H) {
                    adjustSetting(s, button); return true;
                }
                cy += ROW_H;
            }
        }

        // Bind
        if (mouseX >= x + 2 && mouseX < x + WIDTH - 1 && mouseY >= cy && mouseY < cy + ROW_H) {
            if (button == 1) { module.setBind(-1); listeningForBind = false; }
            else { listeningForBind = !listeningForBind; listeningKeySetting = null; }
            return true;
        }
        cy += ROW_H;

        // Select Blocks
        if (hasBlockSelector() && mouseX >= x + PAD && mouseX < x + WIDTH - PAD
            && mouseY >= cy + 2 && mouseY < cy + ROW_H - 2) {
            if (module instanceof com.dclient.module.modules.render.BlockESP)
                net.minecraft.client.Minecraft.getInstance().setScreen(
                    new BlockESPScreen((com.dclient.module.modules.render.BlockESP) module));
            else if (module instanceof com.dclient.module.modules.render.WallHack)
                net.minecraft.client.Minecraft.getInstance().setScreen(
                    new WallHackScreen((com.dclient.module.modules.render.WallHack) module));
            return true;
        }
        if (hasBlockSelector()) cy += ROW_H;

        // Edit HUD
        if (hasHudEditor() && mouseX >= x + PAD && mouseX < x + WIDTH - PAD
            && mouseY >= cy + 2 && mouseY < cy + ROW_H - 2) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                new HudEditorScreen((com.dclient.module.modules.render.HUD) module));
            return true;
        }
        if (hasHudEditor()) cy += ROW_H;

        // Pick Item (AH Sniper)
        if (hasItemPicker() && mouseX >= x + PAD && mouseX < x + WIDTH - PAD
            && mouseY >= cy + 2 && mouseY < cy + ROW_H - 2) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                new AhSniperItemScreen(
                    (com.dclient.module.modules.donut.AhSniper) module,
                    net.minecraft.client.Minecraft.getInstance().screen));
            return true;
        }

        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + visH;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (draggingSetting == null) return false;
        applySliderDrag(draggingSetting, (int) mouseX, null);
        return true;
    }

    public void mouseReleased() { draggingSetting = null; }

    public boolean onKeyPressed(int keyCode) {
        // Text input for String settings
        if (editingSetting != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                editingSetting = null; editBuffer = "";
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                editingSetting.setValue(editBuffer);
                editingSetting = null; editBuffer = "";
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
            }
            return true;
        }
        if (listeningKeySetting != null) {
            listeningKeySetting.setValue(keyCode == GLFW.GLFW_KEY_ESCAPE
                ? (Integer) listeningKeySetting.getDefaultValue() : keyCode);
            listeningKeySetting = null;
            return true;
        }
        if (!listeningForBind) return false;
        module.setBind(keyCode == GLFW.GLFW_KEY_ESCAPE ? -1 : keyCode);
        listeningForBind = false;
        return true;
    }

    public boolean isListening() { return listeningForBind || listeningKeySetting != null || editingSetting != null; }

    /** True only when a String text field is being edited. */
    public boolean isTextEditing() { return editingSetting != null; }

    /** Called from ClickGui charTyped to feed characters into the active text field. */
    public boolean charTyped(char c) {
        if (editingSetting == null) return false;
        editBuffer += c;
        return true;
    }

    // ── internals ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void applySliderDrag(Setting<?> s, int mouseX, Font font) {
        // Reconstruct track bounds matching renderSlider
        String label = truncate(s.name, font, 55);
        int labelW = font != null ? font.width(label) : 40;
        int trackX = x + PAD + 2 + labelW + 4;
        String val = formatVal(s);
        int valW = font != null ? font.width(val) : 20;
        int trackW = WIDTH - PAD - 2 - labelW - 4 - PAD - valW - 4;
        if (trackW <= 0) return;
        float pct = Math.max(0f, Math.min(1f, (float)(mouseX - trackX) / trackW));
        if (s.getDefaultValue() instanceof Float) {
            Setting<Float> fs = (Setting<Float>) s;
            float lo = (Float) fs.min, hi = (Float) fs.max;
            fs.setValue(Math.round((lo + pct * (hi - lo)) * 10) / 10.0f);
        } else if (s.getDefaultValue() instanceof Integer) {
            Setting<Integer> is = (Setting<Integer>) s;
            int lo = (Integer) is.min, hi = (Integer) is.max;
            is.setValue(lo + Math.round(pct * (hi - lo)));
        }
    }

    private float getSliderPct(Setting<?> s) {
        if (s.getDefaultValue() instanceof Float) {
            @SuppressWarnings("unchecked") Setting<Float> fs = (Setting<Float>) s;
            float lo = (Float) fs.min, hi = (Float) fs.max;
            return hi == lo ? 0f : ((Float) fs.getValue() - lo) / (hi - lo);
        } else if (s.getDefaultValue() instanceof Integer) {
            @SuppressWarnings("unchecked") Setting<Integer> is = (Setting<Integer>) s;
            int lo = (Integer) is.min, hi = (Integer) is.max;
            return hi == lo ? 0f : (float)((Integer) is.getValue() - lo) / (hi - lo);
        }
        return 0f;
    }

    @SuppressWarnings("unchecked")
    private void adjustSetting(Setting<?> s, int button) {
        if (s.getDefaultValue() instanceof Float) {
            Setting<Float> fs = (Setting<Float>) s;
            float v = button == 0 ? fs.getValue() + 0.1f : fs.getValue() - 0.1f;
            fs.setValue(Math.max(0f, Math.round(v * 10) / 10.0f));
        } else if (s.getDefaultValue() instanceof Integer) {
            Setting<Integer> is = (Setting<Integer>) s;
            is.setValue(button == 0 ? is.getValue() + 1 : Math.max(0, is.getValue() - 1));
        }
    }

    private String formatVal(Setting<?> s) {
        Object v = s.getValue();
        if (v instanceof Float f)   return String.format("%.1f", f);
        if (v instanceof Boolean b) return b ? "ON" : "OFF";
        if (v instanceof Integer i) return String.valueOf(i);
        return v.toString();
    }

    private String truncate(String text, Font font, int maxPx) {
        if (font == null) return text.length() > 8 ? text.substring(0, 8) : text;
        if (font.width(text) <= maxPx) return text;
        while (text.length() > 1 && font.width(text + "..") > maxPx)
            text = text.substring(0, text.length() - 1);
        return text + "..";
    }

    public static String getKeyName(int key) {
        if (key == -1) return "None";
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT   -> "RShift";
            case GLFW.GLFW_KEY_LEFT_SHIFT    -> "LShift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
            case GLFW.GLFW_KEY_LEFT_CONTROL  -> "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_ALT     -> "RAlt";
            case GLFW.GLFW_KEY_LEFT_ALT      -> "LAlt";
            case GLFW.GLFW_KEY_TAB           -> "Tab";
            case GLFW.GLFW_KEY_SPACE         -> "Space";
            case GLFW.GLFW_KEY_ENTER         -> "Enter";
            case GLFW.GLFW_KEY_BACKSPACE     -> "Bksp";
            case GLFW.GLFW_KEY_DELETE        -> "Del";
            case GLFW.GLFW_KEY_INSERT        -> "Ins";
            case GLFW.GLFW_KEY_HOME          -> "Home";
            case GLFW.GLFW_KEY_END           -> "End";
            case GLFW.GLFW_KEY_PAGE_UP       -> "PgUp";
            case GLFW.GLFW_KEY_PAGE_DOWN     -> "PgDn";
            case GLFW.GLFW_KEY_UP            -> "Up";
            case GLFW.GLFW_KEY_DOWN          -> "Down";
            case GLFW.GLFW_KEY_LEFT          -> "Left";
            case GLFW.GLFW_KEY_RIGHT         -> "Right";
            case GLFW.GLFW_KEY_F1  -> "F1";  case GLFW.GLFW_KEY_F2  -> "F2";
            case GLFW.GLFW_KEY_F3  -> "F3";  case GLFW.GLFW_KEY_F4  -> "F4";
            case GLFW.GLFW_KEY_F5  -> "F5";  case GLFW.GLFW_KEY_F6  -> "F6";
            case GLFW.GLFW_KEY_F7  -> "F7";  case GLFW.GLFW_KEY_F8  -> "F8";
            case GLFW.GLFW_KEY_F9  -> "F9";  case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11"; case GLFW.GLFW_KEY_F12 -> "F12";
            default -> { String n = GLFW.glfwGetKeyName(key, 0); yield n != null ? n.toUpperCase() : "K" + key; }
        };
    }
}
