package com.dclient.module.modules.combat;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

/**
 * Hover Totem — ported from Glazed HoverTotem.
 * When inventory is open and you hover over a totem:
 *   - Equips it to offhand via F-key swap (slot 40)
 *   - Optionally also places one in a hotbar slot
 */
public class HoverTotem extends Module {
    public final Setting<Integer> tickDelay       = addSetting("Delay",        0);
    public final Setting<Boolean> hotbarTotem     = addSetting("Hotbar Totem", true);
    public final Setting<Integer> hotbarSlot      = addSetting("Hotbar Slot",  1); // 1-9

    private int remainingDelay = 0;

    public HoverTotem() { super("Hover Totem", Category.COMBAT); }

    @Override
    protected void onEnable() { remainingDelay = 0; }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        // Only when inventory screen is open
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            remainingDelay = 0;
            return;
        }

        // Get hovered slot via reflection
        Slot hovered = getHoveredSlot(screen);
        if (hovered == null || !hovered.hasItem()) return;
        if (!hovered.getItem().is(Items.TOTEM_OF_UNDYING)) return;
        // Only inventory slots (not crafting output etc.)
        if (hovered.index > 35) return;

        // Tick delay
        if (remainingDelay > 0) { remainingDelay--; return; }

        int syncId   = screen.getMenu().containerId;
        int slotIdx  = hovered.index;

        // Equip to offhand if not already holding one
        if (!mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            // Button 40 = F key = swap with offhand
            mc.gameMode.handleInventoryMouseClick(syncId, slotIdx, 40, ClickType.SWAP, mc.player);
            remainingDelay = tickDelay.getValue();
            return;
        }

        // Equip to hotbar slot if enabled and slot is empty / not a totem
        if (hotbarTotem.getValue()) {
            int hbIdx = Math.max(0, Math.min(8, hotbarSlot.getValue() - 1));
            if (!mc.player.getInventory().getItem(hbIdx).is(Items.TOTEM_OF_UNDYING)) {
                // Button = hotbar index (0-8) = swap with that hotbar slot
                mc.gameMode.handleInventoryMouseClick(syncId, slotIdx, hbIdx, ClickType.SWAP, mc.player);
                remainingDelay = tickDelay.getValue();
            }
        }
    }

    private Slot getHoveredSlot(AbstractContainerScreen<?> screen) {
        // Try known field names across mappings
        String[] names = {"hoveredSlot", "field_7528", "f_96807_", "lastClickSlot"};
        for (String name : names) {
            try {
                var field = AbstractContainerScreen.class.getDeclaredField(name);
                field.setAccessible(true);
                return (Slot) field.get(screen);
            } catch (Exception ignored) {}
        }
        // Walk superclass fields
        try {
            for (var field : AbstractContainerScreen.class.getDeclaredFields()) {
                if (field.getType() == Slot.class) {
                    field.setAccessible(true);
                    return (Slot) field.get(screen);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
