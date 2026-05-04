package com.dclient.client.mixin;

import com.dclient.client.DClientClient;
import com.dclient.module.modules.render.FreeLook;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), cancellable = true)
    private void onHandleAccumulatedMovement(CallbackInfo ci) {
        FreeLook fl = DClientClient.getModule(FreeLook.class);
        if (fl == null || !fl.isEnabled() || !fl.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        double sens = mc.options.sensitivity().get() * 0.6 + 0.2;
        double scaledSens = sens * sens * sens * 8.0;
        fl.cameraYaw   += (float)(accumulatedDX * scaledSens * 0.15);
        fl.cameraPitch += (float)(accumulatedDY * scaledSens * 0.15);
        accumulatedDX = 0;
        accumulatedDY = 0;
        ci.cancel();
    }
}
