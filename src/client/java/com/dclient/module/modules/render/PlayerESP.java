package com.dclient.module.modules.render;

import com.dclient.client.render.RenderUtil;
import com.dclient.friends.FriendManager;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;
import java.util.List;

public class PlayerESP extends Module {
    public final Setting<Float> r = addSetting("Red",   1.0f, 0.0f, 1.0f);
    public final Setting<Float> g = addSetting("Green", 0.0f, 0.0f, 1.0f);
    public final Setting<Float> b = addSetting("Blue",  0.0f, 0.0f, 1.0f);
    public final Setting<Float> alpha = addSetting("Alpha", 1.0f, 0.0f, 1.0f);
    public final Setting<String>  style = addSetting("Style", "Box", new String[]{"Box", "Flat", "Corner", "Skeleton"});
    public final Setting<Boolean> tracers = addSetting("Tracers", false);
    public final Setting<Integer> range = addSetting("Range", 3000, 10, 3000);
    public final List<Player> targets = new ArrayList<>();

    private int tickCounter = 0;

    public PlayerESP() { super("Player ESP", Category.RENDER); }

    public void tick() {
        if (!isEnabled()) return;
        // Refresh player list every 5 ticks — no need to rebuild every tick
        if (++tickCounter < 10) return;
        tickCounter = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int rangeSq = range.getValue() * range.getValue();
        targets.clear();
        for (Player p : mc.level.players()) {
            if (p != mc.player && p.distanceToSqr(mc.player) <= rangeSq) targets.add(p);
        }
    }

    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled()) return;
        // Cache all getValue() calls once before the loop
        float rv = r.getValue(), gv = g.getValue(), bv = b.getValue(), av = alpha.getValue();
        String s = style.getValue();
        boolean tr = tracers.getValue();
        for (Player p : targets) {
            float fr = rv, fg = gv, fb = bv;
            if (FriendManager.isFriend(p)) { fr = 0.0f; fg = 1.0f; fb = 0.3f; }
            var box = p.getBoundingBox();
            if ("Flat".equals(s))          RenderUtil.draw2DBox(pose, buffers, box, fr, fg, fb, av);
            else if ("Corner".equals(s))   RenderUtil.drawCornerBox(pose, buffers, box, fr, fg, fb, av);
            else if ("Skeleton".equals(s)) RenderUtil.drawSkeleton(pose, buffers, p, fr, fg, fb, av);
            else                           RenderUtil.drawEntityBox(pose, buffers, p, fr, fg, fb, av);
            if (tr) RenderUtil.drawTracer(pose, buffers,
                (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5,
                fr, fg, fb, av);
        }
    }
}
