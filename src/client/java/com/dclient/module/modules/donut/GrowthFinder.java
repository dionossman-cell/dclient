package com.dclient.module.modules.donut;

import com.dclient.client.render.RenderUtil;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GrowthFinder extends Module {
    public final Setting<Boolean> renderVines     = addSetting("Vines",       true);
    public final Setting<Boolean> renderDripstone = addSetting("Dripstone",   true);
    public final Setting<Boolean> renderBerries   = addSetting("Berries",     true);
    public final Setting<Boolean> renderGray      = addSetting("Gray Chunks", true);
    public final Setting<Float>   alpha           = addSetting("Alpha",       0.6f);
    public final Setting<Float>   renderY         = addSetting("Render Y",    64.0f);

    private static final double PLATE_H = 0.3;
    private static final float[] COL_GRAY    = {0.53f, 0.53f, 0.53f};
    private static final float[] COL_SOURCE  = {0.80f, 0.25f, 1.00f};
    private static final float[] COL_EXTREME = {1.00f, 0.78f, 0.25f};

    // [maxVineLen, dripCount, berryCount, type] type: 0=gray,1=source,2=extreme
    private final Map<ChunkPos, int[]> results = new ConcurrentHashMap<>();
    private final Set<ChunkPos> scanned = new HashSet<>();
    private final List<ChunkPos> queue  = new ArrayList<>();
    private int cursor = 0;
    private ChunkPos lastCenter = null;

    public GrowthFinder() { super("Growth Finder", Category.DONUT); }

    @Override protected void onEnable()  {
        results.clear(); scanned.clear(); queue.clear(); cursor = 0; lastCenter = null; lastScanMs = 0;
        scanPool = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "GrowthFinder-Scanner");
            t.setDaemon(true);
            return t;
        });
    }
    @Override protected void onDisable() {
        if (scanPool != null) { scanPool.shutdownNow(); scanPool = null; }
        results.clear(); scanned.clear(); queue.clear(); cursor = 0; lastCenter = null;
    }

    private java.util.concurrent.ExecutorService scanPool;

    private long lastScanMs = 0;

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // Adaptive throttle: slow down when FPS is low
        int fps = mc.getFps();
        long minInterval = fps < 40 ? 500 : fps < 60 ? 250 : 150;
        long now = System.currentTimeMillis();
        if (now - lastScanMs < minInterval) return;
        lastScanMs = now;

        ChunkPos pc = mc.player.chunkPosition();
        int vd = Math.min(mc.options.renderDistance().get(), 32);

        if (lastCenter == null || pc.x != lastCenter.x || pc.z != lastCenter.z) {
            queue.clear();
            for (int dx = -vd; dx <= vd; dx++)
                for (int dz = -vd; dz <= vd; dz++)
                    queue.add(new ChunkPos(pc.x + dx, pc.z + dz));
            cursor = 0;
            lastCenter = pc;
            scanned.removeIf(p -> Math.abs(p.x - pc.x) > vd + 2 || Math.abs(p.z - pc.z) > vd + 2);
            results.keySet().removeIf(p -> Math.abs(p.x - pc.x) > vd + 2 || Math.abs(p.z - pc.z) > vd + 2);
        }

        int done = 0;
        // Process 1 chunk per tick (was 2) — reduces CPU load
        while (cursor < queue.size() && done < 1) {
            ChunkPos cp = queue.get(cursor++);
            if (scanned.contains(cp)) continue;
            LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cp.x, cp.z);
            if (chunk != null) {
                scanned.add(cp);
                // Run on background thread
                if (scanPool != null) {
                    final LevelChunk fc = chunk;
                    final ChunkPos fcp = cp;
                    scanPool.submit(() -> analyzeChunk(mc, fc, fcp));
                }
                done++;
            }
        }
    }

    private void analyzeChunk(Minecraft mc, LevelChunk chunk, ChunkPos cp) {
        int minY = mc.level.dimensionType().minY();
        int height = mc.level.dimensionType().height();
        int maxY = minY + height;

        int dripCount = 0, berryCount = 0;
        // Track vine columns: for each (x,z) column, count consecutive vine blocks
        int maxVineLen = 0;

        LevelChunkSection[] sections = chunk.getSections();

        // Count dripstone and berries from sections (fast)
        boolean doDrip = renderDripstone.getValue();
        boolean doBerry = renderBerries.getValue();
        boolean doVines = renderVines.getValue();
        for (LevelChunkSection sec : sections) {
            if (sec == null || sec.hasOnlyAir()) continue;
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                    for (int y = 0; y < 16; y++) {
                        BlockState st = sec.getBlockState(x, y, z);
                        Block b = st.getBlock();
                        if (doDrip && b instanceof PointedDripstoneBlock) dripCount++;
                        if (doBerry && b instanceof SweetBerryBushBlock) {
                            try { if (st.getValue(SweetBerryBushBlock.AGE) == 3) berryCount++; } catch (Exception ignored) {}
                        }
                    }
        }

        // Count vine columns across full height per (x,z) column
        if (doVines) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int colLen = 0, bestLen = 0;
                    for (int worldY = maxY - 1; worldY >= minY; worldY--) {
                        int si = (worldY - minY) / 16;
                        int ly = (worldY - minY) % 16;
                        if (si < 0 || si >= sections.length || sections[si] == null) { colLen = 0; continue; }
                        Block b = sections[si].getBlockState(x, ly, z).getBlock();
                        if (isVine(b)) { colLen++; if (colLen > bestLen) bestLen = colLen; }
                        else colLen = 0;
                    }
                    if (bestLen > maxVineLen) maxVineLen = bestLen;
                }
            }
        }

        boolean hasGrowth = (doVines     && maxVineLen >= 6)
                         || (doDrip      && dripCount  >= 6)
                         || (doBerry     && berryCount >= 4);

        if (hasGrowth) {
            if (!results.containsKey(cp)) {
                results.put(cp, new int[]{maxVineLen, dripCount, berryCount});
            }
        } else {
            results.remove(cp);
        }
    }

    private boolean isVine(Block b) {
        return b instanceof VineBlock
            || b == Blocks.CAVE_VINES || b == Blocks.CAVE_VINES_PLANT
            || b == Blocks.WEEPING_VINES || b == Blocks.WEEPING_VINES_PLANT
            || b == Blocks.TWISTING_VINES || b == Blocks.TWISTING_VINES_PLANT;
    }

    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled() || results.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var cam = mc.gameRenderer.getMainCamera().position();
        float a = alpha.getValue();
        // Follow player Y so it's visible above and below ground
        double ry = mc.player.getY() < 63 ? mc.player.getY() : 64.0;
        // Only render chunks within 512 blocks — cap draw calls
        double maxDistSq = 512.0 * 512.0;
        double px = mc.player.getX(), pz = mc.player.getZ();

        for (var entry : results.entrySet()) {
            ChunkPos cp = entry.getKey();
            int[] data = entry.getValue();
            int vineLen = data[0];

            // Distance cull
            double cx = cp.getMinBlockX() + 8, cz = cp.getMinBlockZ() + 8;
            double dx = cx - px, dz = cz - pz;
            if (dx*dx + dz*dz > maxDistSq) continue;

            float[] col;
            if (vineLen >= 100)     col = COL_EXTREME;
            else if (vineLen >= 25) col = COL_SOURCE;
            else                    col = COL_GRAY;

            if (col == COL_GRAY && !renderGray.getValue()) continue;

            double sx = cp.getMinBlockX(), sz = cp.getMinBlockZ();
            RenderUtil.drawAABBRaw(pose, buffers,
                sx - cam.x, ry - 0.15 - cam.y, sz - cam.z,
                sx + 16 - cam.x, ry + 0.15 - cam.y, sz + 16 - cam.z,
                col[0], col[1], col[2], a);
        }
    }
}
