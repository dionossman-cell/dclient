package com.dclient.module.modules.donut;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Shulker Dropper — opens /shop, buys shulkers, and drops them.
 *
 * Logic:
 *  1. If no container is open → send /shop command, wait 20 ticks
 *  2. If a 3-row container is open:
 *     a. Slot 11 has 1x End Stone → click it (buy shulker), wait 20 ticks
 *     b. Slot 17 has a Shulker Box → click it (confirm purchase), wait 20 ticks
 *     c. Slot 13 has a Shulker Box → quick-move slot 23, then drop item
 */
public class ShulkerDropper extends Module {
    public final Setting<Integer> delay = addSetting("Delay", 1, 0, 20);

    private int delayCounter = 0;

    public ShulkerDropper() { super("Shulker Dropper", Category.DONUT); }

    @Override
    protected void onEnable()  { delayCounter = 0; }
    @Override
    protected void onDisable() { delayCounter = 0; }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        if (delayCounter > 0) { delayCounter--; return; }

        AbstractContainerMenu menu = mc.player.containerMenu;

        // No shop open — send /shop command
        if (menu == mc.player.inventoryMenu) {
            mc.player.connection.sendCommand("shop");
            delayCounter = 20;
            return;
        }

        // Must be a 3-row container (27 slots + 36 player slots = 63 total)
        int containerSlots = menu.slots.size() - 36; // subtract player inv slots
        if (containerSlots != 27) return; // not a 3-row chest

        ItemStack slot11 = menu.getSlot(11).getItem();
        ItemStack slot13 = menu.getSlot(13).getItem();
        ItemStack slot17 = menu.getSlot(17).getItem();

        // Step a: slot 11 has exactly 1 End Stone → click to buy
        if (slot11.is(Items.END_STONE) && slot11.getCount() == 1) {
            mc.gameMode.handleInventoryMouseClick(
                menu.containerId, 11, 0, ClickType.PICKUP, mc.player);
            delayCounter = 20;
            return;
        }

        // Step b: slot 17 has a shulker box → click to confirm
        if (isShulker(slot17)) {
            mc.gameMode.handleInventoryMouseClick(
                menu.containerId, 17, 0, ClickType.PICKUP, mc.player);
            delayCounter = 20;
            return;
        }

        // Step c: slot 13 has a shulker box → quick-move slot 23, then drop
        if (isShulker(slot13)) {
            mc.gameMode.handleInventoryMouseClick(
                menu.containerId, 23, 0, ClickType.QUICK_MOVE, mc.player);
            delayCounter = delay.getValue();
            // Drop the item
            mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.DROP_ITEM,
                BlockPos.ZERO, Direction.DOWN));
        }
    }

    private boolean isShulker(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        // Check all 16 shulker box colors + the uncolored one
        return item == Items.SHULKER_BOX
            || item == Items.WHITE_SHULKER_BOX
            || item == Items.ORANGE_SHULKER_BOX
            || item == Items.MAGENTA_SHULKER_BOX
            || item == Items.LIGHT_BLUE_SHULKER_BOX
            || item == Items.YELLOW_SHULKER_BOX
            || item == Items.LIME_SHULKER_BOX
            || item == Items.PINK_SHULKER_BOX
            || item == Items.GRAY_SHULKER_BOX
            || item == Items.LIGHT_GRAY_SHULKER_BOX
            || item == Items.CYAN_SHULKER_BOX
            || item == Items.PURPLE_SHULKER_BOX
            || item == Items.BLUE_SHULKER_BOX
            || item == Items.BROWN_SHULKER_BOX
            || item == Items.GREEN_SHULKER_BOX
            || item == Items.RED_SHULKER_BOX
            || item == Items.BLACK_SHULKER_BOX;
    }
}
