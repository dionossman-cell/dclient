package com.dclient.module.modules.donut;

import com.dclient.client.render.EspRenderType;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Light Debug — shows light sources between Y=-64 and Y=-35.
 * Renders filled quads on exposed faces only (no interior faces).
 */
public class LightDebug extends Module {

    // Settings
    public final Setting<Float> red   = addSetting("Red",   1.0f, 0.0f, 1.0f);
    public final Setting<Float> green = addSetting("Green", 1.0f, 0.0f, 1.0f);
    public final Setting<Float> blue  = addSetting("Blue",  0.0f, 0.0f, 1.0f);
    public final Setting<Float> alpha = addSetting("Alpha", 0.4f, 0.0f, 1.0f);

    // Constants
    private static final int  CHUNK_RADIUS      = 10;
    private static final int  MIN_Y             = -64;
    private static final int  MAX_Y             = -35;
    private static final int  MIN_LIGHT_LEVEL   = 5;
    private static final long SCAN_COOLDOWN     = 1000L;
    private static final long OPTIMIZE_COOLDOWN = 1750L;

    // Blocks identified as natural/weak light that can be skipped
    private static final Set<BlockPos> blocksToSkip = ConcurrentHashMap.newKeySet();
    private static long lastOptimizeTime = 0L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LightDebug-Scanner");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    private volatile Map<Integer, List<BlockPos>> groupedLights = new HashMap<>();
    private volatile Set<BlockPos> allLightPositions = new HashSet<>();
    private long lastScanTime = 0L;

    public LightDebug() { super("Light Debug", Category.DONUT); }

    @Override
    protected void onEnable() {
        blocksToSkip.clear();
        groupedLights = new HashMap<>();
        allLightPositions = new HashSet<>();
        lastScanTime = 0L;
        if (scanning.compareAndSet(false, true))
            executor.submit(this::updateLights);
    }

    @Override
    protected void onDisable() {
        groupedLights = new HashMap<>();
        allLightPositions = new HashSet<>();
    }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        if (now - lastScanTime > SCAN_COOLDOWN) {
            if (scanning.compareAndSet(false, true)) {
                lastScanTime = now;
                executor.submit(this::updateLights);
            }
        }
    }

    private void updateLights() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;

            long now = System.currentTimeMillis();
            if (now - lastOptimizeTime >= OPTIMIZE_COOLDOWN) {
                optimizeWeakLights(mc);
                lastOptimizeTime = now;
            }

            Map<Integer, List<BlockPos>> newGrouped = new ConcurrentHashMap<>();
            Set<BlockPos> newAll = ConcurrentHashMap.newKeySet();

            ChunkPos pc = mc.player.chunkPosition();
            for (int cx = pc.x - CHUNK_RADIUS; cx <= pc.x + CHUNK_RADIUS; cx++) {
                for (int cz = pc.z - CHUNK_RADIUS; cz <= pc.z + CHUNK_RADIUS; cz++) {
                    if (!mc.level.hasChunk(cx, cz)) continue;
                    LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    int startX = cx * 16, startZ = cz * 16;
                    BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = MIN_Y; y <= MAX_Y; y++) {
                                mpos.set(startX + x, y, startZ + z);
                                if (blocksToSkip.contains(mpos)) continue;
                                int blockLight = mc.level.getBrightness(LightLayer.BLOCK, mpos);
                                if (blockLight < MIN_LIGHT_LEVEL) continue;
                                int skyLight = mc.level.getBrightness(LightLayer.SKY, mpos);
                                if (blockLight <= skyLight) continue;
                                BlockPos immutable = mpos.immutable();
                                newAll.add(immutable);
                                newGrouped.computeIfAbsent(blockLight, k -> Collections.synchronizedList(new ArrayList<>())).add(immutable);
                            }
                        }
                    }
                }
            }
            groupedLights = newGrouped;
            allLightPositions = newAll;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            scanning.set(false);
        }
    }

    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled()) return;
        Map<Integer, List<BlockPos>> currentGrouped = groupedLights;
        Set<BlockPos> currentAll = allLightPositions;
        if (currentGrouped.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var cam = mc.gameRenderer.getMainCamera().position();

        float baseR = red.getValue(), baseG = green.getValue(), baseB = blue.getValue(), baseA = alpha.getValue();
        Matrix4f mat = pose.last().pose();
        VertexConsumer buf = buffers.getBuffer(EspRenderType.getEspQuads());

        for (Map.Entry<Integer, List<BlockPos>> entry : currentGrouped.entrySet()) {
            int lightLevel = entry.getKey();
            float intensity = lightLevel / 15.0f;
            float r = baseR * intensity;
            float g = baseG * intensity;
            float b = baseB * intensity;
            float a = baseA * (0.3f + 0.7f * intensity);

            for (BlockPos pos : entry.getValue()) {
                // Only render blocks with at least one exposed face
                if (lightLevel < 15 && !hasExposedFace(pos, currentAll)) continue;

                float x = (float)(pos.getX() - cam.x);
                float y = (float)(pos.getY() - cam.y);
                float z = (float)(pos.getZ() - cam.z);
                renderExposedFaces(buf, mat, x, y, z, pos, currentAll, r, g, b, a);
            }
        }
    }

    private boolean hasExposedFace(BlockPos pos, Set<BlockPos> all) {
        for (Direction dir : Direction.values())
            if (!all.contains(pos.relative(dir))) return true;
        return false;
    }

    private void renderExposedFaces(VertexConsumer buf, Matrix4f mat,
                                     float x, float y, float z,
                                     BlockPos pos, Set<BlockPos> all,
                                     float r, float g, float b, float a) {
        int ri = (int)(r*255), gi = (int)(g*255), bi = (int)(b*255), ai = (int)(a*255);
        // Bottom face
        if (!all.contains(pos.below())) {
            buf.addVertex(mat, x,     y,     z    ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x,     y,     z+1f ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x+1f,  y,     z+1f ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x+1f,  y,     z    ).setColor(ri,gi,bi,ai);
        }
        // Top face
        if (!all.contains(pos.above())) {
            buf.addVertex(mat, x,     y+1f,  z    ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x+1f,  y+1f,  z    ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x+1f,  y+1f,  z+1f ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x,     y+1f,  z+1f ).setColor(ri,gi,bi,ai);
        }
        // North face
        if (!all.contains(pos.north())) {
            buf.addVertex(mat, x,     y,     z    ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x+1f,  y,     z    ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x+1f,  y+1f,  z    ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x,     y+1f,  z    ).setColor(ri,gi,bi,ai);
        }
        // South face
        if (!all.contains(pos.south())) {
            buf.addVertex(mat, x,     y,     z+1f ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x,     y+1f,  z+1f ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x+1f,  y+1f,  z+1f ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x+1f,  y,     z+1f ).setColor(ri,gi,bi,ai);
        }
        // West face
        if (!all.contains(pos.west())) {
            buf.addVertex(mat, x,     y,     z    ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x,     y+1f,  z    ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x,     y+1f,  z+1f ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x,     y,     z+1f ).setColor(ri,gi,bi,ai);
        }
        // East face
        if (!all.contains(pos.east())) {
            buf.addVertex(mat, x+1f,  y,     z    ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x+1f,  y,     z+1f ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x+1f,  y+1f,  z+1f ).setColor(ri,gi,bi,ai);
            buf.addVertex(mat, x+1f,  y+1f,  z    ).setColor(ri,gi,bi,ai);
        }
    }

    // ── Weak light optimization ───────────────────────────────────────────────

    private void optimizeWeakLights(Minecraft mc) {
        if (mc.player == null) return;
        clearOldCache(mc);
        ChunkPos pc = mc.player.chunkPosition();
        Set<BlockPos> weakSources = new HashSet<>();
        for (int cx = pc.x - CHUNK_RADIUS; cx <= pc.x + CHUNK_RADIUS; cx++) {
            for (int cz = pc.z - CHUNK_RADIUS; cz <= pc.z + CHUNK_RADIUS; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                int startX = cx * 16, startZ = cz * 16;
                BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = MIN_Y; y <= MAX_Y; y++) {
                            mpos.set(startX + x, y, startZ + z);
                            int bl = mc.level.getBrightness(LightLayer.BLOCK, mpos);
                            int sl = mc.level.getBrightness(LightLayer.SKY, mpos);
                            if ((bl == 6 || bl == 7) && bl > sl) weakSources.add(mpos.immutable());
                        }
                    }
                }
            }
        }
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos weak : weakSources) {
            if (visited.contains(weak)) continue;
            int level = mc.level.getBrightness(LightLayer.BLOCK, weak);
            if (!hasHigherNeighbor(mc, weak, level + 1))
                propagateDeletion(mc, weak, visited, level);
        }
    }

    private boolean hasHigherNeighbor(Minecraft mc, BlockPos center, int required) {
        for (int x = center.getX()-1; x <= center.getX()+1; x++) {
            for (int y = Math.max(MIN_Y, center.getY()-1); y <= Math.min(MAX_Y, center.getY()+1); y++) {
                for (int z = center.getZ()-1; z <= center.getZ()+1; z++) {
                    if (x == center.getX() && y == center.getY() && z == center.getZ()) continue;
                    BlockPos p = new BlockPos(x, y, z);
                    if (!isInScanArea(mc, p)) continue;
                    int bl = mc.level.getBrightness(LightLayer.BLOCK, p);
                    int sl = mc.level.getBrightness(LightLayer.SKY, p);
                    if (bl >= required && bl > sl) return true;
                }
            }
        }
        return false;
    }

    private void propagateDeletion(Minecraft mc, BlockPos start, Set<BlockPos> visited, int maxLevel) {
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start); visited.add(start);
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            blocksToSkip.add(cur);
            for (Direction dir : Direction.values()) {
                BlockPos nb = cur.relative(dir);
                if (!isInScanArea(mc, nb) || visited.contains(nb) || blocksToSkip.contains(nb)) continue;
                int bl = mc.level.getBrightness(LightLayer.BLOCK, nb);
                int sl = mc.level.getBrightness(LightLayer.SKY, nb);
                if (bl < MIN_LIGHT_LEVEL || bl <= sl || bl > maxLevel) continue;
                visited.add(nb); queue.add(nb);
            }
        }
    }

    private void clearOldCache(Minecraft mc) {
        if (mc.player == null) { blocksToSkip.clear(); return; }
        ChunkPos pc = mc.player.chunkPosition();
        int limit = CHUNK_RADIUS + 1;
        blocksToSkip.removeIf(p -> {
            ChunkPos cp = new ChunkPos(p);
            return Math.abs(cp.x - pc.x) > limit || Math.abs(cp.z - pc.z) > limit;
        });
    }

    private boolean isInScanArea(Minecraft mc, BlockPos pos) {
        if (mc.player == null) return false;
        ChunkPos pc = mc.player.chunkPosition();
        int minX = (pc.x - CHUNK_RADIUS) * 16, maxX = (pc.x + CHUNK_RADIUS + 1) * 16;
        int minZ = (pc.z - CHUNK_RADIUS) * 16, maxZ = (pc.z + CHUNK_RADIUS + 1) * 16;
        return pos.getX() >= minX && pos.getX() < maxX
            && pos.getZ() >= minZ && pos.getZ() < maxZ
            && pos.getY() >= MIN_Y && pos.getY() <= MAX_Y;
    }
}
