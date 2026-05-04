package com.dclient.module.modules.visuals;

import com.dclient.client.render.RenderUtil;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights entities holding tridents and thrown tridents in the world.
 */
public class TridentESP extends Module {
    public final Setting<Float>   r        = addSetting("Red",     0.2f, 0.0f, 1.0f);
    public final Setting<Float>   g        = addSetting("Green",   1.0f, 0.0f, 1.0f);
    public final Setting<Float>   b        = addSetting("Blue",    0.4f, 0.0f, 1.0f);
    public final Setting<Float>   alpha    = addSetting("Alpha",   0.9f, 0.0f, 1.0f);
    public final Setting<Integer> mode     = addSetting("Mode",    0);
    public final Setting<Boolean> tracers  = addSetting("Tracers", true);
    public final Setting<Boolean> thrown   = addSetting("Thrown",  true);
    public final Setting<Integer> range    = addSetting("Range",   3000, 10, 3000);

    private final List<LivingEntity> holders = new ArrayList<>();
    private final List<ThrownTrident> tridents = new ArrayList<>();

    public TridentESP() { super("Trident ESP", Category.VISUALS); }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        holders.clear();
        tridents.clear();

        int r = range.getValue();
        boolean wantThrown = thrown.getValue();
        for (var e : mc.level.entitiesForRendering()) {
            if (mc.player.distanceTo(e) > r) continue;
            if (e instanceof LivingEntity le && le != mc.player) {
                if (le.getMainHandItem().is(Items.TRIDENT)
                    || le.getOffhandItem().is(Items.TRIDENT)) {
                    holders.add(le);
                }
            }
            if (wantThrown && e instanceof ThrownTrident tt) {
                tridents.add(tt);
            }
        }
    }

    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled()) return;
        // Cache all getValue() once before loops
        float rv = r.getValue(), gv = g.getValue(), bv = b.getValue(), av = alpha.getValue();
        int m = mode.getValue();
        boolean tr = tracers.getValue();

        for (LivingEntity e : holders) {
            var box = e.getBoundingBox();
            if (m == 1) RenderUtil.draw2DBox(pose, buffers, box, rv, gv, bv, av);
            else if (m == 2) RenderUtil.drawCornerBox(pose, buffers, box, rv, gv, bv, av);
            else RenderUtil.drawEntityBox(pose, buffers, e, rv, gv, bv, av);
            if (tr) RenderUtil.drawTracer(pose, buffers,
                (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5,
                rv, gv, bv, av);
        }

        for (ThrownTrident tt : tridents) {
            RenderUtil.drawEntityBox(pose, buffers, tt, rv, gv, bv, av);
            if (tr) RenderUtil.drawTracer(pose, buffers, tt.getX(), tt.getY(), tt.getZ(), rv, gv, bv, av);
        }
    }
}
