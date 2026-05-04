package com.dclient.module.modules.visuals;

import com.dclient.friends.FriendManager;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders nametags above players with health, ping, and held item info.
 * Uses GameRenderer.projectPointToScreen() for accurate 3D->2D projection.
 */
public class Nametags extends Module {
    public final Setting<Boolean> showHealth = addSetting("Health", true);
    public final Setting<Boolean> showPing   = addSetting("Ping", true);
    public final Setting<Boolean> showItem   = addSetting("Held Item", true);
    public final Setting<Integer> range      = addSetting("Range", 500, 10, 1000);

    public Nametags() { super("Nametags", Category.VISUALS); }

    public void render(GuiGraphics gfx) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Cache these once — not per-player
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int maxRange = range.getValue();
        var conn = mc.getConnection(); // cache connection lookup

        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            if (p.distanceTo(mc.player) > maxRange) continue;

            Vec3 worldPos = new Vec3(p.getX(), p.getY() + p.getBbHeight() + 0.3, p.getZ());
            Vec3 projected = mc.gameRenderer.projectPointToScreen(worldPos);
            if (projected.z >= 1.0) continue;

            int sx = (int)((projected.x * 0.5 + 0.5) * sw);
            int sy = (int)((1.0 - (projected.y * 0.5 + 0.5)) * sh);
            if (sx < -200 || sx > sw + 200 || sy < -50 || sy > sh + 50) continue;

            renderNametag(gfx, mc, conn, p, sx, sy);
        }
    }

    private void renderNametag(GuiGraphics gfx, Minecraft mc, net.minecraft.client.multiplayer.ClientPacketListener conn, Player p, int cx, int cy) {
        String name = p.getName().getString();
        float hp    = p.getHealth();
        float maxHp = p.getMaxHealth();

        // Ping — use pre-cached connection
        int ping = 0;
        if (conn != null) {
            var info = conn.getPlayerInfo(p.getUUID());
            if (info != null) ping = info.getLatency();
        }

        // Held item
        String itemStr = null;
        if (showItem.getValue()) {
            var held = p.getMainHandItem();
            if (!held.isEmpty()) itemStr = held.getHoverName().getString();
        }

        // Build display string
        boolean isFriend = FriendManager.isFriend(p);
        String friendTag = isFriend ? " §a[F]" : "";
        String healthPart = showHealth.getValue() ? String.format(" §c%.1f❤", hp) : "";
        String pingPart   = showPing.getValue()   ? String.format(" §7[%dms]", ping) : "";
        String line1      = name + friendTag + healthPart + pingPart;

        int pad   = 3;
        int lineH = 10;
        int lines = 1 + (itemStr != null ? 1 : 0);

        int line1W = mc.font.width(line1);
        int totalW = line1W;
        if (itemStr != null) totalW = Math.max(totalW, mc.font.width(itemStr));

        int boxW = totalW + pad * 2;
        int boxH = lines * lineH + pad * 2;

        int bx = cx - boxW / 2;
        int by = cy - boxH;

        // Background
        gfx.fill(bx, by, bx + boxW, by + boxH, 0xAA000000);

        // Health bar below the box
        if (showHealth.getValue()) {
            int barY    = by + boxH;
            int filledW = (int)(boxW * (hp / maxHp));
            int barColor = hp > maxHp * 0.6f ? 0xFF44FF44 : hp > maxHp * 0.3f ? 0xFFFFAA00 : 0xFFFF4444;
            gfx.fill(bx, barY, bx + boxW, barY + 2, 0xFF333333);
            gfx.fill(bx, barY, bx + filledW, barY + 2, barColor);
        }

        // Text
        gfx.drawString(mc.font, line1, bx + pad, by + pad, 0xFFFFFFFF, true);
        if (itemStr != null) {
            gfx.drawString(mc.font, itemStr, bx + pad, by + pad + lineH, 0xFFFFDD88, true);
        }
    }
}
