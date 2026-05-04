package com.dclient.module.modules.render;

import com.dclient.client.render.RenderUtil;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class StorageESP extends Module {
    public final Setting<Integer> range = addSetting("Range", 128, 10, 3000);
    public final Setting<String>  style = addSetting("Style", "Box", new String[]{"Box", "Flat", "Corner"});
    public final Setting<Boolean> tracers = addSetting("Tracers", false);

    // Per-type colors (R, G, B as single 0-255 int sliders for each channel)
    public final Setting<Float> chestR = addSetting("Chest Red",   1.0f, 0.0f, 1.0f);
    public final Setting<Float> chestG = addSetting("Chest Green", 0.8f, 0.0f, 1.0f);
    public final Setting<Float> chestB = addSetting("Chest Blue",  0.0f, 0.0f, 1.0f);

    public final Setting<Float> shulkerR = addSetting("Shulker Red",   0.6f, 0.0f, 1.0f);
    public final Setting<Float> shulkerG = addSetting("Shulker Green", 0.0f, 0.0f, 1.0f);
    public final Setting<Float> shulkerB = addSetting("Shulker Blue",  1.0f, 0.0f, 1.0f);

    public final Setting<Float> barrelR = addSetting("Barrel Red",   0.6f, 0.0f, 1.0f);
    public final Setting<Float> barrelG = addSetting("Barrel Green", 0.4f, 0.0f, 1.0f);
    public final Setting<Float> barrelB = addSetting("Barrel Blue",  0.2f, 0.0f, 1.0f);

    public final Setting<Float> enderChestR = addSetting("Ender Chest Red",   0.3f, 0.0f, 1.0f);
    public final Setting<Float> enderChestG = addSetting("Ender Chest Green", 0.0f, 0.0f, 1.0f);
    public final Setting<Float> enderChestB = addSetting("Ender Chest Blue",  0.8f, 0.0f, 1.0f);

    public final Setting<Float> spawnerR = addSetting("Spawner Red",   1.0f, 0.0f, 1.0f);
    public final Setting<Float> spawnerG = addSetting("Spawner Green", 0.0f, 0.0f, 1.0f);
    public final Setting<Float> spawnerB = addSetting("Spawner Blue",  0.0f, 0.0f, 1.0f);

    // Store pos + type
    private final List<BlockPos> chests = new ArrayList<>();
    private final List<BlockPos> shulkers = new ArrayList<>();
    private final List<BlockPos> barrels = new ArrayList<>();
    private final List<BlockPos> enderChests = new ArrayList<>();
    private final List<BlockPos> spawners = new ArrayList<>();
    private int timer = 0;

    public StorageESP() { super("Storage ESP", Category.RENDER); }

    private net.minecraft.world.level.ChunkPos lastStorageCenter = null;

    public void tick() {
        if (!isEnabled()) return;
        if (++timer < 40) return;
        timer = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Skip rescan if player hasn't moved chunks
        net.minecraft.world.level.ChunkPos pc = mc.player.chunkPosition();
        if (pc.equals(lastStorageCenter) && !chests.isEmpty()) return;
        lastStorageCenter = pc;

        chests.clear(); shulkers.clear(); barrels.clear(); enderChests.clear(); spawners.clear();
        int cx = pc.x, cz = pc.z;
        int rngVal = range.getValue(); // cache once — was called inside every block entity lambda
        int cr = (rngVal >> 4) + 1;
        double rngSq = (double) rngVal * rngVal;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        for (int dx = -cr; dx <= cr; dx++) for (int dz = -cr; dz <= cr; dz++) {
            LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx + dx, cz + dz);
            if (chunk == null) continue;
            chunk.getBlockEntities().forEach((pos, be) -> {
                double pdx = pos.getX() - px;
                double pdy = pos.getY() - py;
                double pdz = pos.getZ() - pz;
                if (pdx*pdx + pdy*pdy + pdz*pdz > rngSq) return;
                var block = mc.level.getBlockState(pos).getBlock();
                if (block instanceof ChestBlock || block instanceof TrappedChestBlock) chests.add(pos);
                else if (block instanceof ShulkerBoxBlock) shulkers.add(pos);
                else if (block == Blocks.BARREL) barrels.add(pos);
                else if (block == Blocks.ENDER_CHEST) enderChests.add(pos);
                else if (block == Blocks.SPAWNER) spawners.add(pos);
            });
        }
    }

    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled()) return;
        // Cache all getValue() calls once — not per-list or per-block
        String s = style.getValue();
        boolean tr = tracers.getValue();
        renderList(pose, buffers, chests,      s, tr, chestR.getValue(), chestG.getValue(), chestB.getValue());
        renderList(pose, buffers, shulkers,    s, tr, shulkerR.getValue(), shulkerG.getValue(), shulkerB.getValue());
        renderList(pose, buffers, barrels,     s, tr, barrelR.getValue(), barrelG.getValue(), barrelB.getValue());
        renderList(pose, buffers, enderChests, s, tr, enderChestR.getValue(), enderChestG.getValue(), enderChestB.getValue());
        renderList(pose, buffers, spawners,    s, tr, spawnerR.getValue(), spawnerG.getValue(), spawnerB.getValue());
    }

    private void renderList(PoseStack pose, MultiBufferSource buffers, List<BlockPos> list, String style, boolean tracers, float r, float g, float b) {
        for (BlockPos pos : list) {
            if ("Flat".equals(style) || "Corner".equals(style)) {
                // Need AABB for these — but reuse a single allocation pattern via drawAABBRaw for Flat
                if ("Flat".equals(style)) {
                    var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().position();
                    RenderUtil.drawAABBRaw(pose, buffers,
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                        pos.getX() + 1 - cam.x, pos.getY() - cam.y, pos.getZ() + 1 - cam.z,
                        r, g, b, 1.0f);
                } else {
                    RenderUtil.drawCornerBox(pose, buffers, new net.minecraft.world.phys.AABB(pos), r, g, b, 1.0f);
                }
            } else {
                RenderUtil.drawBlockBox(pose, buffers, pos, r, g, b, 1.0f);
            }
            if (tracers) {
                RenderUtil.drawTracer(pose, buffers, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, r, g, b, 1.0f);
            }
        }
    }
}
