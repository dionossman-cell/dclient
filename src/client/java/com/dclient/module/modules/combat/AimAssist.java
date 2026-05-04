package com.dclient.module.modules.combat;

import com.dclient.friends.FriendManager;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class AimAssist extends Module {
    // General
    public final Setting<Float>   range           = addSetting("Range",        5.0f,   1.0f,   20.0f);
    public final Setting<Float>   fov             = addSetting("FOV",          360.0f, 10.0f,  360.0f);
    public final Setting<Boolean> players         = addSetting("Players",      true);
    public final Setting<Boolean> mobs            = addSetting("Mobs",         false);
    public final Setting<Boolean> ignoreWalls     = addSetting("Ignore Walls", false);
    public final Setting<Boolean> aimHead         = addSetting("Aim Head",     false);

    // Speed
    public final Setting<Boolean> instant         = addSetting("Instant",      false);
    public final Setting<Float>   speed           = addSetting("Speed",        5.0f,   0.5f,   20.0f);

    // Smoothing
    public final Setting<Boolean> randomize       = addSetting("Smooth",       true);
    public final Setting<Float>   noise           = addSetting("Smoothness",   0.5f,   0.0f,   5.0f);
    public final Setting<Float>   maxDelta        = addSetting("Turn Limit",   2.5f,   0.1f,   10.0f);

    private final Random rng = new Random();
    private Entity target = null;
    private int tickCounter = 0;

    public AimAssist() { super("Aim Assist", Category.COMBAT); }

    @Override
    protected void onDisable() { target = null; }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Only rescan for a new target every 3 ticks to save CPU
        if (++tickCounter >= 3) {
            tickCounter = 0;
            float rangeSq = range.getValue() * range.getValue(); // cache once
            boolean checkWalls = !ignoreWalls.getValue();
            boolean wantPlayers = players.getValue();
            boolean wantMobs = mobs.getValue();
            Entity best = null;
            float bestHealth = Float.MAX_VALUE;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player) continue;
                if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
                if (e instanceof Player && !wantPlayers) continue;
                if (!(e instanceof Player) && !wantMobs) continue;
                if (e instanceof Player p && FriendManager.isFriend(p)) continue;
                if (e.distanceToSqr(mc.player) > rangeSq) continue;
                if (checkWalls && !mc.player.hasLineOfSight(e)) continue;
                if (!isInFov(e)) continue;
                float health = le.getHealth();
                if (health < bestHealth) { bestHealth = health; best = e; }
            }
            target = best;
        }

        // Validate current target is still alive and in range
        if (target != null) {
            float rangeSq = range.getValue() * range.getValue();
            if (!(target instanceof LivingEntity le) || !le.isAlive()
                    || target.distanceToSqr(mc.player) > rangeSq) {
                target = null;
            }
        }

        if (target == null) return;

        // Get target position
        double targetY = aimHead.getValue()
            ? target.getBoundingBox().maxY
            : target.getBoundingBox().getCenter().y;

        aim(mc, target.getX(), targetY, target.getZ());
    }

    private void aim(Minecraft mc, double tx, double ty, double tz) {
        double dx = tx - mc.player.getX();
        double dz = tz - mc.player.getZ();
        double dy = ty - (mc.player.getY() + mc.player.getEyeHeight());

        // Target yaw
        double targetYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        if (randomize.getValue()) targetYaw += (rng.nextFloat() - 0.5f) * noise.getValue() * 2.0;

        // Target pitch
        double horiz = Math.sqrt(dx * dx + dz * dz);
        double targetPitch = -Math.toDegrees(Math.atan2(dy, horiz));
        if (randomize.getValue()) targetPitch += (rng.nextFloat() - 0.5f) * noise.getValue() * 2.0;

        if (instant.getValue()) {
            mc.player.setYRot((float) targetYaw);
            mc.player.setXRot((float) Mth.clamp(targetPitch, -90, 90));
        } else {
            float spd = speed.getValue();
            float maxD = maxDelta.getValue();

            // Yaw
            double yawDiff = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
            double yawStep = Math.copySign(Math.min(Math.abs(yawDiff), spd), yawDiff);
            if (Math.abs(yawStep) > maxD) yawStep = Math.copySign(maxD, yawStep);
            mc.player.setYRot(mc.player.getYRot() + (float) yawStep);

            // Pitch
            double pitchDiff = Mth.wrapDegrees(targetPitch - mc.player.getXRot());
            double pitchStep = Math.copySign(Math.min(Math.abs(pitchDiff), spd), pitchDiff);
            if (Math.abs(pitchStep) > maxD) pitchStep = Math.copySign(maxD, pitchStep);
            mc.player.setXRot((float) Mth.clamp(mc.player.getXRot() + pitchStep, -90, 90));
        }
    }

    private boolean isInFov(Entity entity) {
        float fovVal = fov.getValue();
        if (fovVal >= 360f) return true;
        Minecraft mc = Minecraft.getInstance();
        Vec3 dir = entity.position().subtract(mc.player.getEyePosition()).normalize();
        double yaw   = Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0;
        double pitch = -Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1, 1)));
        double yawDiff   = Mth.wrapDegrees(yaw   - mc.player.getYRot());
        double pitchDiff = Mth.wrapDegrees(pitch  - mc.player.getXRot());
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff) <= fovVal / 2.0;
    }
}
