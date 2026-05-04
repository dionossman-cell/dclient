package com.dclient.module.modules.visuals;

import com.dclient.module.Category;
import com.dclient.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shows a HUD panel with the current target's name and health bar.
 */
public class TargetHUD extends Module {
    public TargetHUD() { super("Target HUD", Category.VISUALS); }

    public void render(GuiGraphics gfx) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        LivingEntity target = null;
        if (mc.crosshairPickEntity instanceof LivingEntity le && le != mc.player) {
            target = le;
        }
        if (target == null) return;

        int x = 10, y = mc.getWindow().getGuiScaledHeight() - 50;
        int w = 120, h = 30;

        gfx.fill(x, y, x + w, y + h, 0xCC000000);
        gfx.drawString(mc.font, target.getName().getString(), x + 4, y + 4, 0xFFFFFFFF, true);

        float hp = target.getHealth();
        float maxHp = target.getMaxHealth();
        int barW = (int) ((hp / maxHp) * (w - 8));
        int barColor = hp > maxHp * 0.5f ? 0xFF44FF44 : hp > maxHp * 0.25f ? 0xFFFFAA00 : 0xFFFF4444;

        gfx.fill(x + 4, y + 18, x + w - 4, y + 26, 0xFF333333);
        gfx.fill(x + 4, y + 18, x + 4 + barW, y + 26, barColor);
        gfx.drawString(mc.font, String.format("%.1f / %.1f", hp, maxHp), x + 4, y + 18, 0xFFFFFFFF, true);
    }
}
