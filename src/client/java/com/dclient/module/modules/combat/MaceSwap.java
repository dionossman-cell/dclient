package com.dclient.module.modules.combat;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Breach Swap — ported from Glazed BreachSwap.
 * Swaps to the hotbar slot with the highest Breach-enchanted mace on attack,
 * then swaps back after a configurable delay.
 */
public class MaceSwap extends Module {
    public final Setting<Boolean> autoSwap  = addSetting("Auto Find", true);
    public final Setting<Integer> slot      = addSetting("Slot",      1);   // 1-9, used if auto=false
    public final Setting<Boolean> swapBack  = addSetting("Swap Back", true);
    public final Setting<Integer> delay     = addSetting("Delay",     8);   // ticks

    private int prevSlot  = -1;
    private int countdown = 0;
    // Track last attack to detect new attacks
    private int lastAttackTick = -1;

    public MaceSwap() { super("Breach Swap", Category.COMBAT); }

    @Override
    protected void onDisable() {
        prevSlot = -1;
        countdown = 0;
    }

    /** Called from MixinLocalPlayer when the player attacks */
    public void onAttack() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var inv = mc.player.getInventory();
        if (swapBack.getValue()) prevSlot = inv.getSelectedSlot();

        int targetSlot;
        if (autoSwap.getValue()) {
            targetSlot = findBreachMace(mc);
            if (targetSlot == -1) return;
        } else {
            targetSlot = Math.max(0, Math.min(8, slot.getValue() - 1));
        }

        inv.setSelectedSlot(targetSlot);

        if (swapBack.getValue() && prevSlot != -1) {
            countdown = delay.getValue();
        }
    }

    public void tick() {
        if (!isEnabled()) return;
        if (countdown > 0) {
            countdown--;
            if (countdown == 0 && prevSlot != -1) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.getInventory().setSelectedSlot(prevSlot);
                prevSlot = -1;
            }
        }
    }

    private int findBreachMace(Minecraft mc) {
        var inv = mc.player.getInventory();
        int bestSlot  = -1;
        int bestLevel = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            int level = getBreachLevel(stack);
            if (level > bestLevel) {
                bestLevel = level;
                bestSlot  = i;
            }
        }
        // Also accept any mace if no breach found
        if (bestSlot == -1) {
            for (int i = 0; i < 9; i++) {
                if (inv.getItem(i).is(Items.MACE)) return i;
            }
        }
        return bestSlot;
    }

    private int getBreachLevel(ItemStack stack) {
        try {
            ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
            if (enchants == null || enchants.isEmpty()) return 0;
            for (var holder : enchants.keySet()) {
                // Get the resource key string via unwrapKey or direct registry key
                var key = holder.unwrapKey();
                if (key.isPresent() && key.get().identifier().getPath().contains("breach")) {
                    return enchants.getLevel(holder);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
