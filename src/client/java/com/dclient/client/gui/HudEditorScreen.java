package com.dclient.client.gui;

import com.dclient.client.DClientClient;
import com.dclient.module.modules.donut.RegionMap;
import com.dclient.module.modules.misc.SpotifyHUD;
import com.dclient.module.modules.render.HUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game HUD editor — drag each HUD element to reposition it.
 * Shows HUD, active modules list, Spotify HUD, and Region Map live.
 */
public class HudEditorScreen extends Screen {
    private final HUD hud;
    private final SpotifyHUD spotify;
    private final RegionMap regionMap;

    private final List<HudElement> elements = new ArrayList<>();
    private HudElement dragging = null;
    private int dragOffX, dragOffY;
    private boolean prevLeft = false;

    public HudEditorScreen(HUD hud) {
        super(Component.literal("HUD Editor"));
        this.hud = hud;
        this.spotify   = DClientClient.getModule(SpotifyHUD.class);
        this.regionMap = DClientClient.getModule(RegionMap.class);
    }

    @Override
    protected void init() {
        elements.clear();
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // HUD info block
        elements.add(new HudElement("HUD Info", hud.posX.getValue(), hud.posY.getValue(), 130, 90, "hud"));

        // Active modules list (top-right)
        elements.add(new HudElement("Active Modules", sw - 130, 4, 126, 14, "modules"));

        // Spotify HUD
        int spX = spotify.posX.getValue(), spY = spotify.posY.getValue();
        elements.add(new HudElement("Spotify", spX, spY, 240, 64, "spotify"));

        // Region Map
        int cs = regionMap.cellSize.getValue();
        int mapSize = 9 * cs;
        int rmX = regionMap.posX.getValue();
        int rmY = regionMap.posY.getValue() == -1 ? (sh - mapSize) / 2 : regionMap.posY.getValue();
        elements.add(new HudElement("Region Map", rmX, rmY, mapSize, mapSize, "regionmap"));
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long window = Minecraft.getInstance().getWindow().handle();
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (leftDown && !prevLeft) {
            for (HudElement el : elements) {
                if (mouseX >= el.x && mouseX < el.x + el.w && mouseY >= el.y && mouseY < el.y + el.h) {
                    dragging = el; dragOffX = mouseX - el.x; dragOffY = mouseY - el.y; break;
                }
            }
        }
        if (!leftDown) {
            if (dragging != null) applyPositions();
            dragging = null;
        }
        if (dragging != null) {
            dragging.x = Math.max(0, Math.min(width - dragging.w, mouseX - dragOffX));
            dragging.y = Math.max(0, Math.min(height - dragging.h, mouseY - dragOffY));
            // Live-update positions while dragging
            applyPositions();
        }
        prevLeft = leftDown;

        // Dim overlay
        gfx.fill(0, 0, width, height, 0x88000000);

        // Render live HUD elements
        hud.render(gfx);
        spotify.render(gfx);
        regionMap.render(gfx);

        // Draw drag handles
        for (HudElement el : elements) {
            boolean hov = mouseX >= el.x && mouseX < el.x + el.w && mouseY >= el.y && mouseY < el.y + el.h;
            boolean drag = dragging == el;
            int col = drag ? 0xFFFFAA00 : hov ? 0xFFFFFFFF : 0xFF888888;
            // Border rect
            gfx.fill(el.x,          el.y,          el.x + el.w, el.y + 1,      col);
            gfx.fill(el.x,          el.y + el.h - 1, el.x + el.w, el.y + el.h, col);
            gfx.fill(el.x,          el.y,          el.x + 1,    el.y + el.h,   col);
            gfx.fill(el.x + el.w - 1, el.y,        el.x + el.w, el.y + el.h,   col);
            // Label tab at top
            gfx.fill(el.x, el.y - 10, el.x + font.width(el.name) + 6, el.y, 0xCC000000);
            gfx.drawString(font, el.name, el.x + 3, el.y - 9, col, false);
        }

        gfx.drawCenteredString(font, "Drag elements | ESC to save & close", width / 2, height - 12, 0xFFAAAAAA);
    }

    private void applyPositions() {
        for (HudElement el : elements) {
            switch (el.id) {
                case "hud"       -> { hud.posX.setValue(el.x); hud.posY.setValue(el.y); }
                case "spotify"   -> { spotify.posX.setValue(el.x); spotify.posY.setValue(el.y); }
                case "regionmap" -> { regionMap.posX.setValue(el.x); regionMap.posY.setValue(el.y); }
                // "modules" — top-right anchor, no separate setting yet
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { applyPositions(); onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override public boolean isPauseScreen() { return false; }

    private static class HudElement {
        String name, id;
        int x, y, w, h;
        HudElement(String name, int x, int y, int w, int h, String id) {
            this.name = name; this.x = x; this.y = y; this.w = w; this.h = h; this.id = id;
        }
    }
}
