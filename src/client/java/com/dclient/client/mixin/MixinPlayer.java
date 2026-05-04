package com.dclient.client.mixin;

import com.dclient.client.DClientClient;
import com.dclient.module.modules.misc.NameProtect;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class MixinPlayer {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void onGetName(CallbackInfoReturnable<Component> cir) {
        Player self = (Player)(Object)this;
        if (self != Minecraft.getInstance().player) return;
        NameProtect np = DClientClient.getModule(NameProtect.class);
        if (np == null || !np.isEnabled()) return;
        String fake = np.fakeName.getValue();
        if (!fake.isEmpty()) cir.setReturnValue(Component.literal(fake));
    }
}
