package com.dclient.module.modules.donut;

import com.dclient.client.render.RenderUtil;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kelp ESP — flags chunks with suspicious kelp patterns.
 *   - Detects kelp columns >= 8 tall
 *   - Flags chunk if >= 10 such columns AND >= 60% of tops are at Y=62
 */
public class KelpChunkESP extends Module {
    public final Setting<Boolean> chatFeedback = addSetting("Chat Feedback", true);
    public final Setting<Float>   r            = addSetting("Red",   0.0f);
    public final Setting<Float>   g            = addSetting("Green", 1.0f);
    public final Setting<Float>   b            = addSetting("Blue",  0.0f);
    public final Setting<Float>   alpha        = addSetting("Alpha", 0.7f);

    private final Set<ChunkPos> flaggedChunks = ConcurrentHashMap.newKeySet();
    private ExecutorService pool;

    public KelpChunkESP() { super("Kelp ESP", Category.DONUT); }

    @Override
    protected void onEnable() {
        flaggedChunks.clear();
        pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "KelpESP-Scanner");
            t.setDaemon(true);
            return t;
        });
        // Scan already-loaded chunks
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int cx = mc.player.chunkPosition().x, cz = mc.player.chunkPosition().z;
        int vd = Math.min(mc.options.renderDistance().get(), 32);
        for (int dx = -vd; dx <= vd; dx++) {
            for (int dz = -vd; dz <= vd; dz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk != null) scheduleChunkScan(chunk);
            }
        }
    }

    @Override
    protected void onDisable() {
        if (pool != null) { pool.shutdownNow(); pool = null; }
        flaggedChunks.clear();
    }

    /** Called from DClientClient chunk load event */
    public void onChunkLoad(LevelChunk chunk) {
        if (!isEnabled()) return;
        scheduleChunkScan(chunk);
    }

    private void scheduleChunkScan(LevelChunk chunk) {
        if (pool == null) return;
        pool.submit(() -> scanChunk(chunk));
    }

    private void scanChunk(LevelChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int xStart = cpos.getMinBlockX();
        int zStart = cpos.getMinBlockZ();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int yMin = mc.level.dimensionType().minY();
        int yMax = yMin + mc.level.dimensionType().height();

        int kelpColumns  = 0;
        int kelpTopsAt62 = 0;

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                int bottom = -1;
                int top    = -1;
                BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
                for (int y = yMin; y < yMax; y++) {
                    mpos.set(x, y, z);
                    var block = chunk.getBlockState(mpos).getBlock();
                    if (block == Blocks.KELP_PLANT || block == Blocks.KELP) {
                        if (bottom < 0) bottom = y;
                        top = y;
                    }
                }
                if (bottom >= 0 && (top - bottom + 1) >= 8) {
                    kelpColumns++;
                    if (top == 62) kelpTopsAt62++;
                }
            }
        }

        boolean flagged = kelpColumns >= 10 && ((double) kelpTopsAt62 / kelpColumns) >= 0.6;

        if (flagged) {
            if (flaggedChunks.add(cpos) && chatFeedback.getValue()) {
                final int cols = kelpColumns, tops = kelpTopsAt62;
                mc.execute(() -> {
                    if (mc.player != null)
                        mc.player.displayClientMessage(Component.literal(
                            "[KelpESP] Chunk " + cpos.x + ", " + cpos.z +
                            " flagged: " + tops + "/" + cols + " kelp tops at Y=62"), false);
                });
            }
        } else {
            flaggedChunks.remove(cpos);
        }
    }

    public void tick() {
        // Scanning is event-driven via onChunkLoad; nothing needed here
    }

    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled() || flaggedChunks.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var cam = mc.gameRenderer.getMainCamera().position();
        float rv = r.getValue(), gv = g.getValue(), bv = b.getValue(), av = alpha.getValue();

        for (ChunkPos cp : flaggedChunks) {
            double sx = cp.getMinBlockX(), sz = cp.getMinBlockZ();
            RenderUtil.drawAABBRaw(pose, buffers,
                sx - cam.x, 63 - cam.y, sz - cam.z,
                sx + 16 - cam.x, 63.1 - cam.y, sz + 16 - cam.z,
                rv, gv, bv, av);
        }
    }
}
