package com.dclient.module.modules.combat;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Double Anchor Macro — ported from Glazed DoubleAnchorMacro.
 * Press the activate key while looking at a block to automatically:
 *   1. Place anchor 1 → charge it
 *   2. Place anchor 2 → charge it
 *   3. Switch to totem slot → explode both
 */
public class DoubleAnchor extends Module {
    public final Setting<Integer> activateKey  = addSetting("Activate Key",   GLFW.GLFW_KEY_V);
    public final Setting<Integer> switchDelay  = addSetting("Switch Delay",   2);  // ticks between steps
    public final Setting<Integer> totemSlot    = addSetting("Totem Slot",     1);  // 1-9
    public final Setting<Integer> stepDelayMs  = addSetting("Step Delay (ms)", 10, 0, 500);

    private int  step         = 0;
    private int  delayCounter = 0;
    private boolean isAnchoring = false;
    private boolean wasPressed  = false;
    private long lastStep = 0;

    public DoubleAnchor() { super("Double Anchor", Category.COMBAT); }

    @Override
    protected void onDisable() { reset(); }

    private void reset() {
        step = 0; delayCounter = 0; isAnchoring = false; wasPressed = false; lastStep = 0;
    }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.screen != null) return;

        // Keybind detection
        boolean pressed = GLFW.glfwGetKey(mc.getWindow().handle(), activateKey.getValue()) == GLFW.GLFW_PRESS;
        if (!isAnchoring && pressed && !wasPressed) {
            if (hasRequiredItems(mc)) {
                isAnchoring = true;
                step = 0;
                delayCounter = 0;
            }
        }
        wasPressed = pressed;

        if (!isAnchoring) return;

        // Must be looking at a solid block
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr)) { isAnchoring = false; reset(); return; }
        var blockState = mc.level.getBlockState(bhr.getBlockPos());
        if (blockState.isAir()) { isAnchoring = false; reset(); return; }

        // Wait for delay between steps (both tick delay and ms delay)
        if (delayCounter < switchDelay.getValue()) { delayCounter++; return; }
        if (System.currentTimeMillis() - lastStep < stepDelayMs.getValue()) return;
        delayCounter = 0;
        lastStep = System.currentTimeMillis();

        var inv = mc.player.getInventory();

        switch (step) {
            case 0 -> swapTo(mc, Items.RESPAWN_ANCHOR);
            case 1 -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
            case 2 -> swapTo(mc, Items.GLOWSTONE);
            case 3 -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
            case 4 -> swapTo(mc, Items.RESPAWN_ANCHOR);
            case 5 -> {
                // Place second anchor and interact twice (place + charge attempt)
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
            }
            case 6 -> swapTo(mc, Items.GLOWSTONE);
            case 7 -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
            case 8 -> inv.setSelectedSlot(Math.max(0, Math.min(8, totemSlot.getValue() - 1)));
            case 9 -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
            case 10 -> { isAnchoring = false; reset(); return; }
        }
        step++;
    }

    private void swapTo(Minecraft mc, net.minecraft.world.item.Item item) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(item)) { inv.setSelectedSlot(i); return; }
        }
    }

    private boolean hasRequiredItems(Minecraft mc) {
        boolean hasAnchor = false, hasGlow = false;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(Items.RESPAWN_ANCHOR)) hasAnchor = true;
            if (inv.getItem(i).is(Items.GLOWSTONE))      hasGlow   = true;
        }
        return hasAnchor && hasGlow;
    }
}
