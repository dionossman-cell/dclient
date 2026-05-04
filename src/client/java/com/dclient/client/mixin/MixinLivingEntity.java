package com.dclient.client.mixin;

import com.dclient.client.DClientClient;
import com.dclient.module.modules.donut.PlayerDetect;
import com.dclient.module.modules.misc.SwingSpeed;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void onHandleEntityEvent(byte status, CallbackInfo ci) {
        if (status != 35) return;
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof Player player)) return;
        PlayerDetect pd = DClientClient.getModule(PlayerDetect.class);
        if (pd != null) pd.onTotemPop(player);
    }

    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
    private void onGetCurrentSwingDuration(CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self != Minecraft.getInstance().player) return;
        SwingSpeed ss = DClientClient.getModule(SwingSpeed.class);
        if (ss == null || !ss.isEnabled()) return;
        int duration = Math.max(1, 21 - Math.round(ss.speed.getValue()));
        cir.setReturnValue(duration);
    }
}
