package com.dclient.module.modules.combat;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

/**
 * When inventory is open, automatically equips totem to offhand.
 * Prefers totems from the main inventory (slots 9-35) over hotbar.
 * Only uses hotbar totems if that's the only option.
 */
public class AutoInvTotem extends Module {
    public final Setting<Integer> delayMs = addSetting("Delay (ms)", 7, 0, 500);
    private long lastEquip = 0;

    public AutoInvTotem() { super("Auto Inv Totem", Category.COMBAT); }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (!(mc.screen instanceof InventoryScreen)) return;
        if (mc.player.getItemInHand(InteractionHand.OFF_HAND).getItem() == Items.TOTEM_OF_UNDYING) return;
        long now = System.currentTimeMillis();
        if (now - lastEquip < delayMs.getValue()) return;

        var inv = mc.player.getInventory();

        // First try main inventory (slots 9-35) — container slots 9-35
        for (int i = 9; i < 36; i++) {
            if (inv.getItem(i).getItem() != Items.TOTEM_OF_UNDYING) continue;
            equip(mc, i, i); // inv slot == container slot for main inv
            return;
        }

        // Count hotbar totems — only use if it's the last totem
        int hotbarCount = 0;
        int hotbarSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).getItem() == Items.TOTEM_OF_UNDYING) {
                hotbarCount++;
                hotbarSlot = i;
            }
        }
        // Only grab from hotbar if it's the only totem left
        if (hotbarCount == 1 && hotbarSlot != -1) {
            equip(mc, hotbarSlot + 36, hotbarSlot); // hotbar container slot = inv slot + 36
        }
    }

    private void equip(Minecraft mc, int containerSlot, int invSlot) {
        lastEquip = System.currentTimeMillis();
        mc.gameMode.handleInventoryMouseClick(
            mc.player.inventoryMenu.containerId, containerSlot, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(
            mc.player.inventoryMenu.containerId, 45, 0, ClickType.PICKUP, mc.player);
        if (!mc.player.inventoryMenu.getCarried().isEmpty()) {
            mc.gameMode.handleInventoryMouseClick(
                mc.player.inventoryMenu.containerId, containerSlot, 0, ClickType.PICKUP, mc.player);
        }
    }
}
