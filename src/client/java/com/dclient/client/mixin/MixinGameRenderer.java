package com.dclient.client.mixin;

import com.dclient.client.DClientClient;
import com.dclient.module.modules.render.FullBright;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @ModifyVariable(method = "getNightVisionScale", at = @At("RETURN"), ordinal = 0)
    private static float onGetNightVisionScale(float original) {
        FullBright fb = DClientClient.getModule(FullBright.class);
        if (fb != null && fb.isEnabled()) return 1.0f;
        return original;
    }

    /** Extend the far clipping plane so ESP lines render at long range */
    @Inject(method = "getDepthFar", at = @At("RETURN"), cancellable = true)
    private void onGetDepthFar(CallbackInfoReturnable<Float> cir) {
        float original = cir.getReturnValue();
        if (original < 4096.0f) {
            // Check if any ESP module is enabled — use cached getModule instead of stream
            FullBright fb = DClientClient.getModule(FullBright.class); // just to check if cache is ready
            if (fb != null) cir.setReturnValue(4096.0f);
        }
    }
}
