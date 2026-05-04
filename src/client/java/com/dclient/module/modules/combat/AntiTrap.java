package com.dclient.module.modules.combat;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

/**
 * Anti Trap — removes armor stands and chest minecarts near the player.
 * Ported from Glazed AntiTrap.
 */
public class AntiTrap extends Module {
    public final Setting<Boolean> notifications  = addSetting("Notifications",   true);
    public final Setting<Boolean> armorStands    = addSetting("Armor Stands",    true);
    public final Setting<Boolean> chestMinecarts = addSetting("Chest Minecarts", true);

    private int tickTimer = 0;

    public AntiTrap() { super("Anti Trap", Category.COMBAT); }

    @Override
    protected void onEnable() {
        // Remove existing trap entities immediately on enable
        removeTrapEntities(true);
    }

    public void tick() {
        if (!isEnabled()) return;
        // Check every second (20 ticks)
        if (++tickTimer < 20) return;
        tickTimer = 0;
        removeTrapEntities(false);
    }

    private void removeTrapEntities(boolean notify) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        int count = 0;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity == null) continue;
            var type = entity.getType();
            if ((armorStands.getValue()    && type == EntityType.ARMOR_STAND)
             || (chestMinecarts.getValue() && type == EntityType.CHEST_MINECART)) {
                entity.discard();
                count++;
            }
        }

        if (notify && count > 0 && notifications.getValue()) {
            mc.player.displayClientMessage(
                Component.literal("[AntiTrap] Removed " + count + " trap entities"), false);
        }
    }
}
