package com.dclient.module.modules.misc;

import com.dclient.module.Category;
import com.dclient.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Automatically switches to the best tool for the block you're looking at.
 */
public class AutoTool extends Module {
    public AutoTool() { super("Auto Tool", Category.MISC); }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!isEnabled()) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) return;

        BlockState state = mc.level.getBlockState(blockHit.getBlockPos());
        var inv = mc.player.getInventory();

        float bestSpeed = -1f;
        int bestSlot = inv.getSelectedSlot();

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        mc.player.getInventory().setSelectedSlot(bestSlot);
    }
}
