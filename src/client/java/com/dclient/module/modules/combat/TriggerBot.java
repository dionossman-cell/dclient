package com.dclient.module.modules.combat;

import com.dclient.friends.FriendManager;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.ModuleManager;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class TriggerBot extends Module {
    public final Setting<Boolean> requireFullCharge = addSetting("Full Charge", true);
    public final Setting<Boolean> attackPlayers = addSetting("Players", false);
    public final Setting<Boolean> attackMobs = addSetting("Mobs", true);
    public final Setting<Float> range = addSetting("Range", 3.5f);

    public TriggerBot() { super("TriggerBot", Category.COMBAT); }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        if (requireFullCharge.getValue() && mc.player.getAttackStrengthScale(0f) < 1.0f) return;

        Entity target = mc.crosshairPickEntity;
        if (!(target instanceof LivingEntity living)) return;
        if (!living.isAlive()) return;
        // Range check — only attack within configured range
        if (mc.player.distanceTo(target) > range.getValue()) return;
        if (target instanceof Player p) {
            if (p == mc.player) return;
            if (!attackPlayers.getValue()) return;
            if (FriendManager.isFriend(p)) return;
        } else if (!attackMobs.getValue()) return;

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }
}
