package com.dclient.module.modules.combat;

import com.dclient.friends.FriendManager;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public class AutoCrystal extends Module {
    public final Setting<Float> placeRange  = addSetting("Place Range",  5.0f, 1.0f, 10.0f);
    public final Setting<Float> attackRange = addSetting("Attack Range", 5.0f, 1.0f, 10.0f);
    public final Setting<Integer> delay     = addSetting("Delay (ms)",   130,  0,    1000);

    private long lastAction = 0;

    public AutoCrystal() { super("Auto Crystal", Category.COMBAT); }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        long now = System.currentTimeMillis();
        if (now - lastAction < delay.getValue()) return;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof EndCrystal)) continue;
            if (e.distanceTo(mc.player) > attackRange.getValue()) continue;
            mc.gameMode.attack(mc.player, e);
            lastAction = now;
            return;
        }

        int crystalSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.END_CRYSTAL) { crystalSlot = i; break; }
        }
        if (crystalSlot == -1) return;

        Entity target = null;
        float bestDist = Float.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Monster) && !(e instanceof Player && e != mc.player)) continue;
            if (e instanceof Player p && FriendManager.isFriend(p)) continue;
            float d = e.distanceTo(mc.player);
            if (d <= placeRange.getValue() + 3 && d < bestDist) { bestDist = d; target = e; }
        }
        if (target == null) return;

        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos candidate = target.blockPosition().offset(x, -1, z);
            var block = mc.level.getBlockState(candidate).getBlock();
            if ((block == Blocks.OBSIDIAN || block == Blocks.BEDROCK)
                && mc.level.getBlockState(candidate.above()).isAir()
                && mc.player.distanceToSqr(Vec3.atCenterOf(candidate)) <= placeRange.getValue() * placeRange.getValue()) {
                mc.player.getInventory().setSelectedSlot(crystalSlot);
                BlockPos place = candidate.above();
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(place), net.minecraft.core.Direction.UP, place, false));
                lastAction = now;
                return;
            }
        }
    }
}
