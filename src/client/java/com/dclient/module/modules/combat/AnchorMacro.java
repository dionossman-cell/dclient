package com.dclient.module.modules.combat;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Anchor Macro — ported from Glazed AnchorMacro.
 * Hold right-click on a Respawn Anchor:
 *   1. Switches to glowstone and charges the anchor
 *   2. Switches to totem slot and explodes the anchor
 * All with configurable delays between steps.
 */
public class AnchorMacro extends Module {
    public final Setting<Integer> switchDelay    = addSetting("Switch Delay",    0);
    public final Setting<Integer> glowstoneDelay = addSetting("Glowstone Delay", 0);
    public final Setting<Integer> explodeDelay   = addSetting("Explode Delay",   0);
    public final Setting<Integer> totemSlot      = addSetting("Totem Slot",      1); // 1-9
    public final Setting<Integer> actionDelayMs  = addSetting("Action Delay (ms)", 5, 0, 500);

    private int switchCounter    = 0;
    private int glowstoneCounter = 0;
    private int explodeCounter   = 0;
    private boolean placedGlowstone = false;
    private boolean explodedAnchor  = false;
    private long lastAction = 0;

    public AnchorMacro() { super("Anchor Macro", Category.COMBAT); }

    @Override
    protected void onEnable()  { reset(); }
    @Override
    protected void onDisable() { reset(); }

    private void reset() {
        switchCounter = glowstoneCounter = explodeCounter = 0;
        placedGlowstone = explodedAnchor = false;
        lastAction = 0;
    }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.screen != null) return;
        // Enforce ms delay between actions
        if (System.currentTimeMillis() - lastAction < actionDelayMs.getValue()) return;
        lastAction = System.currentTimeMillis();

        boolean rmb = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (!rmb) {
            // Reset state when right-click released
            placedGlowstone = false;
            explodedAnchor  = false;
            switchCounter = glowstoneCounter = explodeCounter = 0;
            return;
        }

        // Must be looking at a respawn anchor
        if (!(mc.hitResult instanceof BlockHitResult bhr)) return;
        BlockPos anchorPos = bhr.getBlockPos();
        var block = mc.level.getBlockState(anchorPos).getBlock();
        if (block != Blocks.RESPAWN_ANCHOR) return;

        // Suppress vanilla right-click so we control the interaction
        mc.options.keyUse.setDown(false);

        int charges = getAnchorCharges(mc, anchorPos);

        if (charges == 0 && !placedGlowstone) {
            // Step 1: charge the anchor with glowstone
            placeGlowstone(mc, bhr);
        } else if (charges > 0 && !explodedAnchor) {
            // Step 2: explode the anchor
            explodeAnchor(mc, bhr);
        }
    }

    private void placeGlowstone(Minecraft mc, BlockHitResult bhr) {
        var inv = mc.player.getInventory();

        // Switch to glowstone if not already holding it
        if (!mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
            if (switchCounter < switchDelay.getValue()) { switchCounter++; return; }
            switchCounter = 0;
            int slot = findHotbarSlot(mc, Items.GLOWSTONE);
            if (slot == -1) return; // no glowstone
            inv.setSelectedSlot(slot);
            return;
        }

        // Wait glowstone delay then interact
        if (glowstoneCounter < glowstoneDelay.getValue()) { glowstoneCounter++; return; }
        glowstoneCounter = 0;

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
        placedGlowstone = true;
    }

    private void explodeAnchor(Minecraft mc, BlockHitResult bhr) {
        int targetSlot = Math.max(0, Math.min(8, totemSlot.getValue() - 1));
        var inv = mc.player.getInventory();

        // Switch to totem slot
        if (inv.getSelectedSlot() != targetSlot) {
            if (switchCounter < switchDelay.getValue()) { switchCounter++; return; }
            switchCounter = 0;
            inv.setSelectedSlot(targetSlot);
            return;
        }

        // Wait explode delay then interact
        if (explodeCounter < explodeDelay.getValue()) { explodeCounter++; return; }
        explodeCounter = 0;

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
        explodedAnchor = true;
    }

    private int getAnchorCharges(Minecraft mc, BlockPos pos) {
        var state = mc.level.getBlockState(pos);
        if (state.getBlock() != Blocks.RESPAWN_ANCHOR) return 0;
        // CHARGES property: 0 = uncharged, 1-4 = charged
        try {
            return state.getValue(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE);
        } catch (Exception e) {
            return 0;
        }
    }

    private int findHotbarSlot(Minecraft mc, net.minecraft.world.item.Item item) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(item)) return i;
        }
        return -1;
    }
}
