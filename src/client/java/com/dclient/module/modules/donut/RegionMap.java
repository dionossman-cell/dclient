package com.dclient.module.modules.donut;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Map;

/**
 * Region Map — DonutSMP region map showing your location on the server grid.
 * Shows a 9x9 grid of regions with EU/NA/Asia/Oceania color coding.
 */
public class RegionMap extends Module {
    public final Setting<Integer> posX       = addSetting("Position X", 4);
    public final Setting<Integer> posY       = addSetting("Position Y", -1); // -1 = auto center
    public final Setting<Integer> cellSize   = addSetting("Cell Size", 16);
    public final Setting<Boolean> showCoords = addSetting("Show Coords", true);
    public final Setting<Boolean> showGrid   = addSetting("Show Grid", true);
    public final Setting<Boolean> showPlayer = addSetting("Show Player", true);
    public final Setting<Boolean> showLegend = addSetting("Show Legend", true);

    private static final int MAP_SIZE = 9;
    private static final double REGION_SIZE = 50000.0;
    private static final double MAP_OFFSET = 225000.0;

    // Region type colors: EU Central, EU West, NA East, NA West, Asia, Oceania
    private static final int[] TYPE_COLORS = {
        0xCC9FCE63, // EU Central - green
        0xCC00A663, // EU West - dark green
        0xCC4FADED, // NA East - blue
        0xCC2F6EBA, // NA West - dark blue
        0xCCF5C242, // Asia - yellow
        0xCCFC8803  // Oceania - orange
    };
    private static final String[] TYPE_NAMES = {
        "EU Central", "EU West", "NA East", "NA West", "Asia", "Oceania"
    };

    // [regionId, regionType] for each of the 81 cells (row-major)
    private static final int[][] REGION_LAYOUT = {
        {82,5},{100,3},{101,3},{102,3},{103,2},{104,2},{105,2},{106,2},{91,2},
        {83,5},{44,3},{75,3},{42,3},{41,2},{40,2},{39,2},{38,2},{92,2},
        {84,5},{45,3},{14,3},{13,3},{12,2},{11,2},{10,2},{37,2},{93,2},
        {85,5},{46,5},{74,5},{3,3},{2,2},{1,2},{25,2},{36,2},{94,2},
        {86,4},{47,4},{72,4},{71,4},{5,2},{4,2},{24,2},{35,2},{95,2},
        {87,4},{51,1},{17,1},{9,0},{8,0},{7,0},{23,0},{34,0},{96,2},
        {88,4},{54,1},{18,1},{61,0},{62,0},{21,0},{22,0},{33,0},{97,0},
        {89,0},{26,1},{27,0},{28,0},{29,0},{30,0},{59,0},{32,0},{98,0},
        {90,0},{107,1},{108,1},{109,1},{110,1},{111,1},{112,1},{113,1},{99,0}
    };

    public RegionMap() { super("Region Map", Category.DONUT); }

    public void render(GuiGraphics gfx) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int ox = posX.getValue();
        int cs = cellSize.getValue();
        int mapW = MAP_SIZE * cs;
        int mapH = MAP_SIZE * cs;

        // posY = -1 means auto-center vertically
        int screenH = mc.getWindow().getGuiScaledHeight();
        int oy = posY.getValue() == -1
            ? (screenH - mapH) / 2
            : posY.getValue();

        double px = mc.player.getX();
        double pz = mc.player.getZ();

        // Background
        gfx.fill(ox, oy, ox + mapW, oy + mapH, 0xCC191919);

        // Region cells
        for (int i = 0; i < REGION_LAYOUT.length; i++) {
            int row = i / MAP_SIZE;
            int col = i % MAP_SIZE;
            int type = Math.min(REGION_LAYOUT[i][1], TYPE_COLORS.length - 1);
            int cx = ox + col * cs;
            int cy = oy + row * cs;
            gfx.fill(cx + 1, cy + 1, cx + cs - 1, cy + cs - 1, TYPE_COLORS[type]);
        }

        // Grid lines
        if (showGrid.getValue()) {
            for (int i = 0; i <= MAP_SIZE; i++) {
                gfx.fill(ox + i * cs, oy, ox + i * cs + 1, oy + mapH, 0xFF0F0F0F);
                gfx.fill(ox, oy + i * cs, ox + mapW, oy + i * cs + 1, 0xFF0F0F0F);
            }
        }

        // Region numbers — always draw all of them
        for (int i = 0; i < REGION_LAYOUT.length; i++) {
            int row = i / MAP_SIZE;
            int col = i % MAP_SIZE;
            int id = REGION_LAYOUT[i][0];
            int cx = ox + col * cs;
            int cy = oy + row * cs;
            String num = String.valueOf(id);
            int tw = mc.font.width(num);
            // Center in cell, clip to cell bounds
            int tx = cx + Math.max(0, (cs - tw) / 2);
            int ty = cy + Math.max(0, (cs - 8) / 2);
            gfx.drawString(mc.font, num, tx, ty, 0xFFFFFFFF, false);
        }

        // Player indicator
        if (showPlayer.getValue()) {
            int[] grid = worldToGrid(px, pz);
            if (grid[0] >= 0 && grid[0] < MAP_SIZE && grid[1] >= 0 && grid[1] < MAP_SIZE) {
                double[] cell = worldToCellPos(px, pz);
                int ipx = ox + grid[0] * cs + (int)(cell[0] * cs);
                int ipy = oy + grid[1] * cs + (int)(cell[1] * cs);
                float yaw = mc.player.getYRot();
                double angle = Math.toRadians(-yaw - 90.0);
                int arrowSize = 6;
                int tipX = ipx + (int)(Math.cos(angle) * arrowSize);
                int tipY = ipy - (int)(Math.sin(angle) * arrowSize);
                double la = angle + Math.toRadians(135);
                double ra = angle - Math.toRadians(135);
                int lx = ipx + (int)(Math.cos(la) * arrowSize);
                int ly = ipy - (int)(Math.sin(la) * arrowSize);
                int rx = ipx + (int)(Math.cos(ra) * arrowSize);
                int ry = ipy - (int)(Math.sin(ra) * arrowSize);
                drawTriangle(gfx, tipX, tipY, lx, ly, rx, ry, 0xFFFF3232);
            }
        }

        // Coords below map
        if (showCoords.getValue()) {
            int infoY = oy + mapH + 5;
            String coords = "X: " + (int)px + "  Z: " + (int)pz;
            gfx.drawString(mc.font, coords, ox, infoY, 0xFFFFFFFF, false);
            int[] grid = worldToGrid(px, pz);
            if (grid[0] >= 0 && grid[0] < MAP_SIZE && grid[1] >= 0 && grid[1] < MAP_SIZE) {
                int idx = grid[1] * MAP_SIZE + grid[0];
                int regionId = REGION_LAYOUT[idx][0];
                int regionType = REGION_LAYOUT[idx][1];
                String regionStr = "Region " + regionId + " (" + TYPE_NAMES[Math.min(regionType, TYPE_NAMES.length-1)] + ")";
                gfx.drawString(mc.font, regionStr, ox, infoY + 10, 0xFFAAAAAA, false);
            }
        }

        // Legend
        if (showLegend.getValue()) {
            int legendY = oy + mapH + (showCoords.getValue() ? 30 : 5);
            for (int i = 0; i < TYPE_NAMES.length; i++) {
                int ly = legendY + i * 14;
                gfx.fill(ox, ly, ox + 12, ly + 12, TYPE_COLORS[i] | 0xFF000000);
                gfx.drawString(mc.font, TYPE_NAMES[i], ox + 15, ly + 2, 0xFFFFFFFF, false);
            }
        }
    }

    private void drawTriangle(GuiGraphics gfx, int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        int minY = Math.min(y1, Math.min(y2, y3));
        int maxY = Math.max(y1, Math.max(y2, y3));
        for (int sy = minY; sy <= maxY; sy++) {
            int lx = Integer.MAX_VALUE, rx = Integer.MIN_VALUE;
            int[] xs = {edgeX(x1,y1,x2,y2,sy), edgeX(x2,y2,x3,y3,sy), edgeX(x3,y3,x1,y1,sy)};
            for (int x : xs) if (x != Integer.MAX_VALUE) { lx = Math.min(lx,x); rx = Math.max(rx,x); }
            if (lx <= rx && lx != Integer.MAX_VALUE) gfx.fill(lx, sy, rx+1, sy+1, color);
        }
    }

    private int edgeX(int x1, int y1, int x2, int y2, int sy) {
        if (sy < Math.min(y1,y2) || sy > Math.max(y1,y2)) return Integer.MAX_VALUE;
        if (y1 == y2) return (x1+x2)/2;
        return x1 + (x2-x1)*(sy-y1)/(y2-y1);
    }

    private int[] worldToGrid(double wx, double wz) {
        return new int[]{(int)((wx + MAP_OFFSET) / REGION_SIZE), (int)((wz + MAP_OFFSET) / REGION_SIZE)};
    }

    private double[] worldToCellPos(double wx, double wz) {
        double cx = Math.max(0, Math.min(1, ((wx + MAP_OFFSET) % REGION_SIZE) / REGION_SIZE));
        double cz = Math.max(0, Math.min(1, ((wz + MAP_OFFSET) % REGION_SIZE) / REGION_SIZE));
        return new double[]{cx, cz};
    }
}
