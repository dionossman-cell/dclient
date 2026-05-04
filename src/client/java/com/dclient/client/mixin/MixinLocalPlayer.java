package com.dclient.client.mixin;

import com.dclient.client.DClientClient;
import com.dclient.module.modules.combat.AutoTotem;
import com.dclient.module.modules.misc.Freecam;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onAiStep(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer)(Object)this;
        AutoTotem autoTotem = DClientClient.getModule(AutoTotem.class);
        if (autoTotem != null && autoTotem.isEnabled()) autoTotem.tick(player);
    }

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void onSendPosition(CallbackInfo ci) {
        if (Freecam.active) ci.cancel();
    }
}
