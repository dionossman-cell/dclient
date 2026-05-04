package com.dclient.client.mixin;

import com.dclient.client.DClientClient;
import com.dclient.module.modules.render.WallHack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class MixinEntity {
    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        WallHack wh = DClientClient.getModule(WallHack.class);
        if (wh == null || !wh.isEnabled()) return;
        Entity self = (Entity)(Object)this;
        if (self instanceof Player && wh.players.getValue()) { cir.setReturnValue(true); return; }
        if (self instanceof Mob && wh.mobs.getValue()) cir.setReturnValue(true);
    }
}
