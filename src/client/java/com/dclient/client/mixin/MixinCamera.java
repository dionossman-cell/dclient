package com.dclient.client.mixin;

import com.dclient.client.DClientClient;
import com.dclient.module.modules.render.FreeLook;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private float yRot;
    @Shadow private float xRot;

    @Inject(method = "setup", at = @At("HEAD"))
    private void onSetupHead(Level level, Entity entity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
        FreeLook fl = DClientClient.getModule(FreeLook.class);
        if (fl == null || !fl.isEnabled() || !fl.isActive()) return;
        float origYaw = entity.getYRot();
        float origPitch = entity.getXRot();
        entity.setYRot(fl.cameraYaw);
        entity.setXRot(fl.cameraPitch);
        fl._origYaw = origYaw;
        fl._origPitch = origPitch;
        fl._needsRestore = true;
    }

    @Inject(method = "setup", at = @At("RETURN"))
    private void onSetupReturn(Level level, Entity entity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
        FreeLook fl = DClientClient.getModule(FreeLook.class);
        if (fl == null || !fl._needsRestore) return;
        entity.setYRot(fl._origYaw);
        entity.setXRot(fl._origPitch);
        fl._needsRestore = false;
    }
}
