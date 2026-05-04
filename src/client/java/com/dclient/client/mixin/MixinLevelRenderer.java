package com.dclient.client.mixin;

import com.dclient.client.DClientClient;
import com.dclient.module.modules.render.WallHack;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Shadow private com.mojang.blaze3d.pipeline.RenderTarget entityOutlineTarget;

    @Inject(method = "shouldShowEntityOutlines", at = @At("RETURN"), cancellable = true)
    private void onShouldShowEntityOutlines(CallbackInfoReturnable<Boolean> cir) {
        WallHack wh = DClientClient.getModule(WallHack.class);
        if (wh != null && wh.isEnabled() && entityOutlineTarget != null) {
            cir.setReturnValue(true);
        }
    }
}
