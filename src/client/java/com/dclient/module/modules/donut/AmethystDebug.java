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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AmethystDebug extends Module {
    // Cluster types
    public final Setting<Boolean> smallBuds    = addSetting("Small Buds", true);
    public final Setting<Boolean> mediumBuds   = addSetting("Medium Buds", true);
    public final Setting<Boolean> largeBuds    = addSetting("Large Buds", true);
    public final Setting<Boolean> clusters     = addSetting("Clusters", true);

    // Range
    public final Setting<Integer> minY         = addSetting("Min Y", -64);
    public final Setting<Integer> maxY         = addSetting("Max Y", 128);

    // Render
    public final Setting<Float> r              = addSetting("Red", 0.58f);
    public final Setting<Float> g              = addSetting("Green", 0.0f);
    public final Setting<Float> b              = addSetting("Blue", 0.83f);
    public final Setting<Boolean> tracers      = addSetting("Tracers", false);
    public final Setting<Boolean> chatFeedback = addSetting("Chat Feedback", true);

    private final Set<BlockPos> clusterPositions = ConcurrentHashMap.newKeySet();
    private ExecutorService pool;

    public AmethystDebug() { super("Amethyst Debug", Category.DONUT); }

    @Override
    protected void onEnable() {
        clusterPositions.clear();
        pool = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            r -> { Thread t = new Thread(r, "AmethystDebug-Worker"); t.setDaemon(true); return t; }
        );
        // Initial scan
        pool.submit(this::initialScan);
    }

    @Override
    protected void onDisable() {
        if (pool != null) { pool.shutdownNow(); pool = null; }
        clusterPositions.clear();
    }

    private void initialScan() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int cx = mc.player.chunkPosition().x, cz = mc.player.chunkPosition().z;
        int vd = Math.min(mc.options.renderDistance().get(), 32);
        for (int dx = -vd; dx <= vd; dx++) {
            for (int dz = -vd; dz <= vd; dz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk != null) scanChunk(chunk);
            }
        }
    }

    public void onChunkLoad(LevelChunk chunk) {
        if (!isEnabled() || pool == null) return;
        pool.submit(() -> scanChunk(chunk));
    }

    private void scanChunk(LevelChunk chunk) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        var cp = chunk.getPos();
        int xStart = cp.getMinBlockX(), zStart = cp.getMinBlockZ();
        int yMin = Math.max(mc.level.dimensionType().minY(), minY.getValue());
        int yMax = Math.min(mc.level.dimensionType().minY() + mc.level.dimensionType().height(), maxY.getValue());

        Set<BlockPos> found = ConcurrentHashMap.newKeySet();
        int newCount = 0;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos(); // reuse — no allocation per block

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    mpos.set(x, y, z);
                    BlockState state = chunk.getBlockState(mpos);
                    if (isAmethyst(state, y)) found.add(mpos.immutable());
                }
            }
        }

        // Remove old, add new
        clusterPositions.removeIf(pos -> new net.minecraft.world.level.ChunkPos(pos).equals(cp) && !found.contains(pos));
        for (BlockPos pos : found) {
            if (clusterPositions.add(pos)) newCount++;
        }

        if (chatFeedback.getValue() && newCount > 0) {
            final int count = newCount;
            mc.execute(() -> {
                if (mc.player != null)
                    mc.player.displayClientMessage(
                        Component.literal("[AmethystDebug] Chunk " + cp.x + "," + cp.z + ": " + count + " new clusters"), false);
            });
        }
    }

    private boolean isAmethyst(BlockState state, int y) {
        if (y < minY.getValue() || y > maxY.getValue()) return false;
        var block = state.getBlock();
        if (smallBuds.getValue()  && block == Blocks.SMALL_AMETHYST_BUD)  return true;
        if (mediumBuds.getValue() && block == Blocks.MEDIUM_AMETHYST_BUD) return true;
        if (largeBuds.getValue()  && block == Blocks.LARGE_AMETHYST_BUD)  return true;
        if (clusters.getValue()   && block == Blocks.AMETHYST_CLUSTER)    return true;
        return false;
    }

    public void tick() {
        // Periodic rescan every 5 seconds
        if (!isEnabled()) return;
    }

    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled() || clusterPositions.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float rv = r.getValue(), gv = g.getValue(), bv = b.getValue();
        boolean doTracers = tracers.getValue();
        for (BlockPos pos : clusterPositions) {
            RenderUtil.drawBlockBox(pose, buffers, pos, rv, gv, bv, 0.8f);
            if (doTracers)
                RenderUtil.drawTracer(pose, buffers, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rv, gv, bv, 1.0f);
        }
    }
}
