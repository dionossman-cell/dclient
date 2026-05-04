package com.dclient.client.mixin;

import com.dclient.client.DClientClient;
import com.dclient.module.modules.visuals.HandView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class MixinItemInHandRenderer {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void onRenderHandsWithItems(float partialTick, PoseStack pose,
                                         SubmitNodeCollector collector,
                                         LocalPlayer player, int light,
                                         CallbackInfo ci) {
        HandView hv = DClientClient.getModule(HandView.class);
        if (hv == null || !hv.isEnabled()) return;

        // Apply main hand transforms
        applyTransforms(pose,
            hv.mainX.getValue(), hv.mainY.getValue(), hv.mainZ.getValue(),
            hv.mainRotX.getValue(), hv.mainRotY.getValue(), hv.mainRotZ.getValue(),
            hv.mainScale.getValue());
    }

    private void applyTransforms(PoseStack pose,
                                  float tx, float ty, float tz,
                                  float rx, float ry, float rz,
                                  float scale) {
        pose.translate(tx, ty, tz);
        if (rx != 0) pose.mulPose(new Quaternionf().rotationX(rx * Mth.DEG_TO_RAD));
        if (ry != 0) pose.mulPose(new Quaternionf().rotationY(ry * Mth.DEG_TO_RAD));
        if (rz != 0) pose.mulPose(new Quaternionf().rotationZ(rz * Mth.DEG_TO_RAD));
        if (scale != 1.0f) pose.scale(scale, scale, scale);
    }
}
