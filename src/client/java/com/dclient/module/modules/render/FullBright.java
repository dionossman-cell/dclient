package com.dclient.module.modules.render;

import com.dclient.module.Category;
import com.dclient.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Makes the world fully bright by applying a permanent Night Vision effect client-side.
 * The GameRenderer mixin also boosts the night vision scale to 1.0.
 */
public class FullBright extends Module {
    public FullBright() { super("FullBright", Category.RENDER); }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 99999, 0, false, false));
        }
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }
}
