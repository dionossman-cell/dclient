package com.dclient.module.modules.render;

import com.dclient.client.render.RenderUtil;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import java.util.ArrayList;
import java.util.List;

public class MobESP extends Module {
    public final Setting<Float> r = addSetting("Red",   1.0f, 0.0f, 1.0f);
    public final Setting<Float> g = addSetting("Green", 0.5f, 0.0f, 1.0f);
    public final Setting<Float> b = addSetting("Blue",  0.0f, 0.0f, 1.0f);
    public final Setting<Float> alpha = addSetting("Alpha", 1.0f, 0.0f, 1.0f);
    public final Setting<Boolean> hostileOnly = addSetting("Hostile Only", false);
    public final Setting<String>  style = addSetting("Style", "Box", new String[]{"Box", "Flat", "Corner"});
    public final Setting<Boolean> tracers = addSetting("Tracers", false);
    public final Setting<Integer> range = addSetting("Range", 3000, 10, 3000);
    public final List<Mob> mobs = new ArrayList<>();

    private int tickCounter = 0;

    public MobESP() { super("Mob ESP", Category.RENDER); }

    public void tick() {
        if (!isEnabled()) return;
        // Refresh mob list every 5 ticks — no need to rebuild every tick
        if (++tickCounter < 10) return;
        tickCounter = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int rangeSq = range.getValue() * range.getValue();
        mobs.clear();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Mob m)) continue;
            if (hostileOnly.getValue() && !(m instanceof Monster)) continue;
            if (m.distanceToSqr(mc.player) > rangeSq) continue;
            mobs.add(m);
        }
    }

    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled()) return;
        // Cache all getValue() calls once before the loop
        String s = style.getValue();
        float rv = r.getValue(), gv = g.getValue(), bv = b.getValue(), av = alpha.getValue();
        boolean tr = tracers.getValue();
        for (Mob mob : mobs) {
            var box = mob.getBoundingBox();
            if ("Flat".equals(s))        RenderUtil.draw2DBox(pose, buffers, box, rv, gv, bv, av);
            else if ("Corner".equals(s)) RenderUtil.drawCornerBox(pose, buffers, box, rv, gv, bv, av);
            else                         RenderUtil.drawEntityBox(pose, buffers, mob, rv, gv, bv, av);
            if (tr) RenderUtil.drawTracer(pose, buffers,
                (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5,
                rv, gv, bv, av);
        }
    }
}
