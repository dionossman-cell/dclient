package com.dclient.client.mixin;

import com.dclient.module.ModuleManager;
import com.dclient.module.modules.donut.PlayerDetect;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class MixinClientLevel {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void onTickNonPassenger(Entity entity, CallbackInfo ci) {}
}
