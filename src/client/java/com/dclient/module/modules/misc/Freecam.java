package com.dclient.module.modules.misc;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Spectator-style free camera.
 * The player entity is moved client-side only — no packets are sent for movement.
 * Player stays at original position server-side.
 */
public class Freecam extends Module {
    public final Setting<Float> speed = addSetting("Speed", 0.3f);
    public final Setting<Float> sprintMultiplier = addSetting("Sprint Multiplier", 3.0f);

    private Vec3 savedPos;
    private float savedYaw, savedPitch;

    // Public so mixin can read it
    public static boolean active = false;

    public Freecam() { super("Freecam", Category.MISC); }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        savedPos = mc.player.position();
        savedYaw = mc.player.getYRot();
        savedPitch = mc.player.getXRot();
        active = true;
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.noPhysics = true;
        mc.player.fallDistance = 0;
    }

    @Override
    protected void onDisable() {
        active = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.noPhysics = false;
        mc.player.fallDistance = 0;
        mc.player.setDeltaMovement(Vec3.ZERO);
        if (savedPos != null) {
            mc.player.setPos(savedPos.x, savedPos.y, savedPos.z);
            mc.player.setYRot(savedYaw);
            mc.player.setXRot(savedPitch);
        }
    }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.fallDistance = 0;
        mc.player.noPhysics = true;

        float s = speed.getValue();
        if (mc.options.keySprint.isDown()) s *= sprintMultiplier.getValue();

        Vec3 look = mc.player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 move = Vec3.ZERO;

        if (mc.options.keyUp.isDown())    move = move.add(look.scale(s));
        if (mc.options.keyDown.isDown())  move = move.add(look.scale(-s));
        if (mc.options.keyLeft.isDown())  move = move.add(right.scale(-s));
        if (mc.options.keyRight.isDown()) move = move.add(right.scale(s));
        if (mc.options.keyJump.isDown())  move = move.add(0, s, 0);
        if (mc.options.keyShift.isDown()) move = move.add(0, -s, 0);

        if (!move.equals(Vec3.ZERO)) {
            mc.player.setPos(
                mc.player.getX() + move.x,
                mc.player.getY() + move.y,
                mc.player.getZ() + move.z
            );
        }
    }
}
