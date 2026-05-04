package com.dclient.module.modules.render;

import com.dclient.client.gui.ThemeUtil;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.ModuleManager;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.item.Items;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class HUD extends Module {
    public final Setting<Boolean> showClientTag = addSetting("Client Tag", true);
    public final Setting<Boolean> showPing      = addSetting("Ping", true);
    public final Setting<Boolean> showTotems    = addSetting("Totems", true);
    public final Setting<Boolean> showTime      = addSetting("Time", true);
    public final Setting<Boolean> showCoords    = addSetting("Coords", true);
    public final Setting<Boolean> showFps       = addSetting("FPS", false);
    public final Setting<Boolean> showTps       = addSetting("TPS", false);
    public final Setting<Boolean> showModules   = addSetting("Active Modules", true);
    public final Setting<Integer> posX          = addSetting("HUD X", 4);
    public final Setting<Integer> posY          = addSetting("HUD Y", 4);

    // Color modes: Rainbow, RedBlue, RedGreen, GreenBlue, Sunset, Candy, Steady
    private static final String[] COLOR_MODES = {"Rainbow", "RedBlue", "RedGreen", "GreenBlue", "Sunset", "Candy", "Steady"};
    public final Setting<String>  colorMode    = addSetting("Color Mode", "Rainbow", COLOR_MODES);
    public final Setting<Float>   colorSpeed   = addSetting("Color Speed", 1.0f, 0.1f, 5.0f);

    // Animated colors
    private long startTime = System.currentTimeMillis();

    /** Returns the primary animated color based on current mode. offset shifts the phase (ms). */
    private int getColor(long offset) {
        float speed = colorSpeed.getValue();
        long elapsed = (long)((System.currentTimeMillis() - startTime + offset) * speed);
        String mode = colorMode.getValue();
        return switch (mode) {
            case "RedBlue"   -> lerpCycle(elapsed, 3000, 0xFFFF2222, 0xFF2222FF);
            case "RedGreen"  -> lerpCycle(elapsed, 3000, 0xFFFF2222, 0xFF22FF44);
            case "GreenBlue" -> lerpCycle(elapsed, 3000, 0xFF22FF44, 0xFF2244FF);
            case "Sunset"    -> lerpCycle(elapsed, 4000, 0xFFFF4400, 0xFFFF00AA);
            case "Candy"     -> lerpCycle(elapsed, 2000, 0xFFFF44CC, 0xFF44CCFF);
            case "Steady"    -> {
                // Use theme accent color via cached ThemeUtil
                yield ThemeUtil.accent();
            }
            default -> hsvToRgb((elapsed % 3000) / 3000f, 1f, 1f); // Rainbow
        };
    }

    /** Smooth ping-pong lerp between two ARGB colors over periodMs. */
    private static int lerpCycle(long elapsed, long periodMs, int colorA, int colorB) {
        float t = (elapsed % periodMs) / (float) periodMs;
        // ping-pong: 0->1->0
        float p = t < 0.5f ? t * 2f : (1f - (t - 0.5f) * 2f);
        int ar = (colorA >> 16) & 0xFF, ag = (colorA >> 8) & 0xFF, ab = colorA & 0xFF;
        int br = (colorB >> 16) & 0xFF, bg = (colorB >> 8) & 0xFF, bb = colorB & 0xFF;
        int r = (int)(ar + (br - ar) * p);
        int g = (int)(ag + (bg - ag) * p);
        int b = (int)(ab + (bb - ab) * p);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private int getLabelColor()              { return getColor(0); }
    private int getValueColor()              { return getColor(300); }
    private int getModuleColor(int index)    { return getColor(index * 180L); }

    private static int hsvToRgb(float h, float s, float v) {
        int i = (int)(h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | ((int)(r*255) << 16) | ((int)(g*255) << 8) | (int)(b*255);
    }

    public HUD() { super("HUD", Category.RENDER); }

    // Cached values — updated every 20 ticks, not every frame
    private int cachedPing = 0;
    private int cachedTotems = 0;
    private int cacheTimer = 0;
    private List<Module> cachedModules = new java.util.ArrayList<>();
    // Cached colors — updated every tick, not every frame
    private int cachedLabelColor = 0xFFFF4444;
    private int cachedValueColor = 0xFFFF4444;
    private int colorTick = 0;

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // Update colors every 2 ticks (~100ms) — smooth enough for animation
        if (++colorTick >= 2) {
            colorTick = 0;
            cachedLabelColor = getLabelColor();
            cachedValueColor = getValueColor();
        }
        if (++cacheTimer < 20) return;
        cacheTimer = 0;
        cachedPing = getPing(mc);
        cachedTotems = countTotems(mc);
        cachedModules = new java.util.ArrayList<>();
        for (Module m : ModuleManager.getAll()) {
            if (m.isEnabled() && !(m instanceof HUD)) cachedModules.add(m);
        }
    }

    public void render(GuiGraphics gfx) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Use pre-cached colors from tick() — no getColor()/getByName() per frame
        int labelCol = cachedLabelColor;
        int valueCol = cachedValueColor;
        int x = posX.getValue(), y = posY.getValue();

        if (showClientTag.getValue()) {
            drawLine(gfx, mc, x, y, "dclient", "", labelCol, valueCol);
            y += 10;
        }
        if (showPing.getValue()) {
            drawLine(gfx, mc, x, y, "Ping: ", cachedPing + "ms", labelCol, valueCol);
            y += 10;
        }
        if (showTotems.getValue()) {
            drawLine(gfx, mc, x, y, "Totems: ", String.valueOf(cachedTotems), labelCol, valueCol);
            y += 10;
        }
        if (showTime.getValue()) {
            drawLine(gfx, mc, x, y, "Time: ", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), labelCol, valueCol);
            y += 10;
        }
        if (showCoords.getValue()) {
            var pos = mc.player.blockPosition();
            drawLine(gfx, mc, x, y, "XYZ: ", pos.getX() + " / " + pos.getY() + " / " + pos.getZ(), labelCol, valueCol);
            y += 10;
        }
        if (showFps.getValue()) {
            drawLine(gfx, mc, x, y, "FPS: ", String.valueOf(mc.getFps()), labelCol, valueCol);
            y += 10;
        }
        if (showTps.getValue() && mc.getSingleplayerServer() != null) {
            float mspt = mc.getSingleplayerServer().getAverageTickTimeNanos() / 1_000_000f;
            float tps = Math.min(20.0f, 1000.0f / Math.max(mspt, 1));
            drawLine(gfx, mc, x, y, "TPS: ", String.format("%.1f", tps), labelCol, valueCol);
            y += 10;
        }

        if (showModules.getValue()) {
            int rx = mc.getWindow().getGuiScaledWidth() - 4;
            int ry = 4;
            for (int i = 0; i < cachedModules.size(); i++) {
                Module mod = cachedModules.get(i);
                int tw = mc.font.width(mod.name);
                int col = (i % 2 == 0) ? labelCol : valueCol; // use cached colors
                gfx.drawString(mc.font, mod.name, rx - tw + 1, ry + 1, 0x55000000, false);
                gfx.drawString(mc.font, mod.name, rx - tw, ry, col, false);
                gfx.fill(rx - 1, ry - 1, rx, ry + 9, col);
                ry += 10;
            }
        }
    }

    private void drawLine(GuiGraphics gfx, Minecraft mc, int x, int y, String label, String value, int labelCol, int valueCol) {
        gfx.fill(x, y - 1, x + 1, y + 9, labelCol);
        int tx = x + 4;
        gfx.drawString(mc.font, label, tx + 1, y + 1, 0x55000000, false);
        gfx.drawString(mc.font, label, tx, y, labelCol, false);
        if (!value.isEmpty()) {
            int lw = mc.font.width(label);
            gfx.drawString(mc.font, value, tx + lw + 1, y + 1, 0x55000000, false);
            gfx.drawString(mc.font, value, tx + lw, y, valueCol, false);
        }
    }

    private int getPing(Minecraft mc) {
        if (mc.getConnection() == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info != null ? info.getLatency() : 0;
    }

    private int countTotems(Minecraft mc) {
        int count = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() == Items.TOTEM_OF_UNDYING) count++;
        }
        return count;
    }
}
