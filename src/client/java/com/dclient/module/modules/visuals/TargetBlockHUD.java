package com.dclient.module.modules.visuals;

import com.dclient.module.Category;
import com.dclient.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Shows info about the block you're looking at (name, hardness, light level).
 */
public class TargetBlockHUD extends Module {
    public TargetBlockHUD() { super("Target Block HUD", Category.VISUALS); }

    public void render(GuiGraphics gfx) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) return;

        var state = mc.level.getBlockState(blockHit.getBlockPos());
        String blockName = state.getBlock().getName().getString();
        float hardness = state.getDestroySpeed(mc.level, blockHit.getBlockPos());
        int light = mc.level.getLightEmission(blockHit.getBlockPos());

        int x = 10, y = mc.getWindow().getGuiScaledHeight() - 90;
        gfx.fill(x, y, x + 140, y + 36, 0xCC000000);
        gfx.drawString(mc.font, blockName, x + 4, y + 4, 0xFFFFFFFF, true);
        gfx.drawString(mc.font, String.format("Hardness: %.1f  Light: %d", hardness, light), x + 4, y + 16, 0xFFAAAAAA, true);
    }
}
