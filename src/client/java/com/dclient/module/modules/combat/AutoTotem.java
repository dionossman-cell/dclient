package com.dclient.module.modules.combat;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

public class AutoTotem extends Module {
    public final Setting<Integer> delay   = addSetting("Delay (ticks)", 0);
    public final Setting<Integer> delayMs = addSetting("Delay (ms)",    7, 0, 500);
    private int ticksSince = 0;
    private long lastEquip = 0;

    public AutoTotem() { super("Auto Totem", Category.COMBAT); }

    public void tick(LocalPlayer player) {
        if (!isEnabled()) return;
        if (player.getItemInHand(InteractionHand.OFF_HAND).getItem() == Items.TOTEM_OF_UNDYING) {
            ticksSince = 0; return;
        }
        if (ticksSince < delay.getValue()) { ticksSince++; return; }
        long now = System.currentTimeMillis();
        if (now - lastEquip < delayMs.getValue()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return;

        var inv = player.getInventory();
        int totemSlot = -1;

        // Search inventory slots 0-35 (0-8 = hotbar, 9-35 = main)
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).getItem() == Items.TOTEM_OF_UNDYING) {
                totemSlot = i;
                break;
            }
        }
        if (totemSlot == -1) return;

        // Convert to container slot index
        // Hotbar: inv slot 0-8 -> container slot 36-44
        // Main inv: inv slot 9-35 -> container slot 9-35
        // Offhand: container slot 45
        int containerSlot = totemSlot < 9 ? totemSlot + 36 : totemSlot;

        // Pick up totem
        mc.gameMode.handleInventoryMouseClick(
            player.inventoryMenu.containerId, containerSlot, 0, ClickType.PICKUP, player);
        // Place in offhand (slot 45)
        mc.gameMode.handleInventoryMouseClick(
            player.inventoryMenu.containerId, 45, 0, ClickType.PICKUP, player);
        // If cursor still has something, put it back
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            mc.gameMode.handleInventoryMouseClick(
                player.inventoryMenu.containerId, containerSlot, 0, ClickType.PICKUP, player);
        }

        ticksSince = 0;
        lastEquip = System.currentTimeMillis();
    }
}
