package com.dclient.client.mixin;

import com.dclient.client.DClientClient;
import com.dclient.module.modules.render.WallHack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = BlockRenderDispatcher.class, priority = 2000)
public class MixinBlockRenderDispatcher {

    @Inject(method = "renderBatched", at = @At("HEAD"), cancellable = true, require = 0)
    private void onRenderBatched(BlockState state, BlockPos pos, BlockAndTintGetter level,
                                  PoseStack pose, VertexConsumer consumer, boolean checkSides,
                                  List<BlockModelPart> parts, CallbackInfo ci) {
        WallHack wh = DClientClient.getModule(WallHack.class);
        if (wh != null && wh.isEnabled() && wh.isTargetBlock(state.getBlock())) {
            ci.cancel();
        }
    }
}
