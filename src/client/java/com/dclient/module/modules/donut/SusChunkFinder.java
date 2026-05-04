package com.dclient.module.modules.donut;

import com.dclient.client.render.RenderUtil;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sus Chunk Finder — detects non-natural blocks below Y=15.
 * Only active when the player is at Y 16-17 (standing on bedrock layer).
 */
public class SusChunkFinder extends Module {

    private final Set<ChunkPos>        flagged       = ConcurrentHashMap.newKeySet();
    private final Map<ChunkPos, Long>  scanned       = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Long>  lastCheckTime = new ConcurrentHashMap<>();

    public SusChunkFinder() { super("Sus Chunk Finder", Category.DONUT); }

    @Override
    protected void onEnable()  { clearAll(); }

    @Override
    protected void onDisable() { clearAll(); }

    private void clearAll() {
        flagged.clear();
        scanned.clear();
        lastCheckTime.clear();
    }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        double playerY = mc.player.getY();

        // Only scan when player is at bedrock level (y 16-17)
        if (playerY < 16.0 || playerY >= 17.0) {
            clearAll();
            return;
        }

        long now = System.currentTimeMillis();
        int vd = mc.options.renderDistance().get();
        ChunkPos playerChunk = mc.player.chunkPosition();

        for (int dx = -vd; dx <= vd; dx++) {
            for (int dz = -vd; dz <= vd; dz++) {
                ChunkPos cp = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cp.x, cp.z);
                if (chunk == null) continue;

                if (!scanned.containsKey(cp)) {
                    // First scan
                    if (shouldFlag(mc, chunk)) flagged.add(cp);
                    scanned.put(cp, now);
                    lastCheckTime.put(cp, now);
                } else {
                    // Re-check every 1 second
                    Long last = lastCheckTime.get(cp);
                    if (last == null || now - last >= 1000L) {
                        if (shouldFlag(mc, chunk)) flagged.add(cp);
                        else flagged.remove(cp);
                        lastCheckTime.put(cp, now);
                    }
                }
            }
        }

        // Cleanup out-of-range chunks
        int max = vd + 1;
        scanned.keySet().removeIf(p -> Math.abs(p.x - playerChunk.x) > max || Math.abs(p.z - playerChunk.z) > max);
        lastCheckTime.keySet().removeIf(p -> !scanned.containsKey(p));
        flagged.removeIf(p -> !scanned.containsKey(p));
    }

    /**
     * Returns true if the chunk contains any non-natural block below Y=15.
     * Natural = deepslate, bedrock, air variants.
     */
    private boolean shouldFlag(Minecraft mc, LevelChunk chunk) {
        int bottomY = mc.level.dimensionType().minY();
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 15; y >= bottomY; y--) {
                    mutable.set(startX + x, y, startZ + z);
                    var block = chunk.getBlockState(mutable).getBlock();
                    if (block != Blocks.DEEPSLATE
                            && block != Blocks.BEDROCK
                            && block != Blocks.AIR
                            && block != Blocks.CAVE_AIR) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled() || flagged.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var cam = mc.gameRenderer.getMainCamera().position();

        for (ChunkPos cp : flagged) {
            double sx = cp.getMinBlockX(), sz = cp.getMinBlockZ();
            // Render flat plate at Y=40 (visible from bedrock level)
            RenderUtil.drawAABBRaw(pose, buffers,
                sx - cam.x,      40.0 - cam.y,        sz - cam.z,
                sx + 16 - cam.x, 40.2 - cam.y, sz + 16 - cam.z,
                1.0f, 0.0f, 0.0f, 0.6f);
        }
    }
}
