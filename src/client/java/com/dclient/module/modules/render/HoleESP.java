package com.dclient.module.modules.render;

import com.dclient.client.render.RenderUtil;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.*;

public class HoleESP extends Module {
    // Detection
    public final Setting<Integer> range          = addSetting("Range", 64, 16, 256);
    public final Setting<Integer> minHoleDepth   = addSetting("Min Depth", 4);
    public final Setting<Integer> minTunnelLen   = addSetting("Min Tunnel Length", 3);
    public final Setting<Integer> minTunnelH     = addSetting("Min Tunnel Height", 2);
    public final Setting<Integer> maxTunnelH     = addSetting("Max Tunnel Height", 3);
    public final Setting<Integer> minStairLen    = addSetting("Min Stair Steps", 3);
    public final Setting<Boolean> detectHoles    = addSetting("Show Holes", true);
    public final Setting<Boolean> detect3x1      = addSetting("Show 3x1 Holes", true);
    public final Setting<Boolean> detectTunnels  = addSetting("Show Tunnels", true);
    public final Setting<Boolean> detectStairs   = addSetting("Show Staircases", true);
    public final Setting<Boolean> airOnly        = addSetting("Air Only", false);

    // Colors
    public final Setting<Float> holeR   = addSetting("Hole Red",    0.0f);
    public final Setting<Float> holeG   = addSetting("Hole Green",  1.0f);
    public final Setting<Float> holeB   = addSetting("Hole Blue",   0.0f);
    public final Setting<Float> hole3R  = addSetting("3x1 Red",     1.0f);
    public final Setting<Float> hole3G  = addSetting("3x1 Green",   0.65f);
    public final Setting<Float> hole3B  = addSetting("3x1 Blue",    0.0f);
    public final Setting<Float> tunR    = addSetting("Tunnel Red",  0.0f);
    public final Setting<Float> tunG    = addSetting("Tunnel Green",0.0f);
    public final Setting<Float> tunB    = addSetting("Tunnel Blue", 1.0f);
    public final Setting<Float> stairR  = addSetting("Stair Red",   1.0f);
    public final Setting<Float> stairG  = addSetting("Stair Green", 0.0f);
    public final Setting<Float> stairB  = addSetting("Stair Blue",  1.0f);

    private static final Direction[] DIRS = {Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH};

    private final Set<AABB> holes     = ConcurrentHashMap.newKeySet();
    private final Set<AABB> holes3x1  = ConcurrentHashMap.newKeySet();
    private final Set<AABB> tunnels   = ConcurrentHashMap.newKeySet();
    private final Set<AABB> stairs    = ConcurrentHashMap.newKeySet();

    private ExecutorService pool;
    private int timer = 0;

    public HoleESP() { super("Hole ESP", Category.RENDER); }

    @Override
    protected void onEnable() {
        holes.clear(); holes3x1.clear(); tunnels.clear(); stairs.clear();
        pool = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "HoleESP"); t.setDaemon(true); return t; });
    }

    @Override
    protected void onDisable() {
        if (pool != null) { pool.shutdownNow(); pool = null; }
        holes.clear(); holes3x1.clear(); tunnels.clear(); stairs.clear();
        lastHoleCenter = null;
    }

    private final java.util.Queue<LevelChunk> scanQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private ChunkPos lastHoleCenter = null;

    public void tick() {
        if (!isEnabled()) return;
        if (++timer < 40) return;
        timer = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int cx = mc.player.chunkPosition().x, cz = mc.player.chunkPosition().z;
        int cr = (range.getValue() >> 4) + 1;

        // Skip full rescan if player hasn't moved chunks
        ChunkPos center = new ChunkPos(cx, cz);
        if (center.equals(lastHoleCenter) && !holes.isEmpty()) return;
        lastHoleCenter = center;

        holes.clear(); holes3x1.clear(); tunnels.clear(); stairs.clear();
        scanQueue.clear();

        // Queue chunks instead of submitting all at once
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk != null) scanQueue.add(chunk);
            }
        }

        // Submit up to 4 chunks per tick to avoid thread pool flooding
        int submitted = 0;
        while (!scanQueue.isEmpty() && submitted < 4) {
            final LevelChunk fc = scanQueue.poll();
            if (pool != null) { pool.submit(() -> scanChunk(fc)); submitted++; }
        }
        // Process remaining on next ticks
        if (!scanQueue.isEmpty() && pool != null) {
            pool.submit(() -> {
                LevelChunk c;
                while ((c = scanQueue.poll()) != null) scanChunk(c);
            });
        }
    }

    private void scanChunk(LevelChunk chunk) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Cache everything before the triple-nested loop
        cachedLevel = mc.level;
        cachedAirOnly = airOnly.getValue();
        boolean doHoles = detectHoles.getValue();
        boolean do3x1 = detect3x1.getValue();
        boolean doTunnels = detectTunnels.getValue();
        boolean doStairs = detectStairs.getValue();
        int minDepth = minHoleDepth.getValue();
        int minTLen = minTunnelLen.getValue();
        int minTH = minTunnelH.getValue(), maxTH = maxTunnelH.getValue();
        int minSLen = minStairLen.getValue();

        int minYv = mc.level.dimensionType().minY();
        int maxYv = minYv + mc.level.dimensionType().height();
        int startX = chunk.getPos().getMinBlockX(), startZ = chunk.getPos().getMinBlockZ();
        int r = range.getValue();
        double px = mc.player.getX(), pz = mc.player.getZ();

        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                if (Math.abs(x - px) > r || Math.abs(z - pz) > r) continue;
                BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
                for (int y = minYv; y < maxYv; y++) {
                    mpos.set(x, y, z);
                    if (!isPassable(mpos)) continue;
                    BlockPos immutable = mpos.immutable();
                    if (doHoles) checkHole(immutable, minDepth);
                    if (do3x1) check3x1Hole(immutable, minDepth);
                    if (doTunnels) checkTunnel(immutable, minTLen, minTH, maxTH);
                    if (doStairs) checkStaircase(immutable, minSLen);
                }
            }
        }
        cachedLevel = null;
    }

    // Cached per-scan-chunk to avoid Minecraft.getInstance() per isPassable call
    private net.minecraft.client.multiplayer.ClientLevel cachedLevel = null;
    private boolean cachedAirOnly = false;

    private void checkHole(BlockPos pos, int minDepth) {
        if (!isValidHole(pos)) return;
        BlockPos.MutableBlockPos cur = pos.mutable();
        while (isValidHole(cur)) cur.move(Direction.UP);
        int depth = cur.getY() - pos.getY();
        if (depth < minDepth) return;
        AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+1, cur.getY(), pos.getZ()+1);
        holes.add(box);
    }

    private boolean isValidHole(BlockPos pos) {
        return isPassable(pos) && !isPassable(pos.north()) && !isPassable(pos.south())
            && !isPassable(pos.east()) && !isPassable(pos.west());
    }

    private void check3x1Hole(BlockPos pos, int minDepth) {
        if (isPassable(pos) && isPassable(pos.east()) && isPassable(pos.east(2))
            && !isPassable(pos.west()) && !isPassable(pos.east(3))
            && !isPassable(pos.north()) && !isPassable(pos.south())) {
            BlockPos.MutableBlockPos cur = pos.mutable();
            while (isPassable(cur) && isPassable(cur.east()) && isPassable(cur.east(2))) cur.move(Direction.UP);
            int depth = cur.getY() - pos.getY();
            if (depth >= minDepth) {
                holes3x1.add(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+3, cur.getY(), pos.getZ()+1));
            }
        }
        if (isPassable(pos) && isPassable(pos.south()) && isPassable(pos.south(2))
            && !isPassable(pos.north()) && !isPassable(pos.south(3))
            && !isPassable(pos.east()) && !isPassable(pos.west())) {
            BlockPos.MutableBlockPos cur = pos.mutable();
            while (isPassable(cur) && isPassable(cur.south()) && isPassable(cur.south(2))) cur.move(Direction.UP);
            int depth = cur.getY() - pos.getY();
            if (depth >= minDepth) {
                holes3x1.add(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+1, cur.getY(), pos.getZ()+3));
            }
        }
    }

    private void checkTunnel(BlockPos pos, int minTLen, int minTH, int maxTH) {
        for (Direction dir : DIRS) {
            BlockPos.MutableBlockPos cur = pos.mutable();
            int steps = 0, maxH = 0;
            BlockPos start = pos, end = pos;
            while (isTunnelSection(cur, dir, minTH, maxTH)) {
                int h = getTunnelHeight(cur, maxTH);
                if (h > maxH) maxH = h;
                end = cur.immutable();
                cur.move(dir);
                steps++;
            }
            if (steps >= minTLen && maxH >= minTH && maxH <= maxTH) {
                tunnels.add(new AABB(
                    Math.min(start.getX(), end.getX()), start.getY(), Math.min(start.getZ(), end.getZ()),
                    Math.max(start.getX(), end.getX())+1, start.getY()+maxH, Math.max(start.getZ(), end.getZ())+1));
            }
        }
    }

    private boolean isTunnelSection(BlockPos pos, Direction dir, int minTH, int maxTH) {
        int h = getTunnelHeight(pos, maxTH);
        if (h < minTH || h > maxTH) return false;
        if (isPassable(pos.below()) || isPassable(pos.above(h))) return false;
        Direction[] perp = dir.getAxis() == Direction.Axis.X
            ? new Direction[]{Direction.NORTH, Direction.SOUTH}
            : new Direction[]{Direction.EAST, Direction.WEST};
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        for (Direction p : perp)
            for (int i = 0; i < h; i++) {
                check.set(pos.getX() + p.getStepX(), pos.getY() + i, pos.getZ() + p.getStepZ());
                if (isPassable(check)) return false;
            }
        return true;
    }

    private int getTunnelHeight(BlockPos pos, int maxTH) {
        int h = 0;
        while (isPassable(pos.above(h)) && h < maxTH) h++;
        return h;
    }

    private final List<AABB> staircaseBuf = new ArrayList<>();

    private void checkStaircase(BlockPos pos, int minSLen) {
        for (Direction dir : DIRS) {
            BlockPos.MutableBlockPos cur = pos.mutable();
            int steps = 0;
            staircaseBuf.clear();
            while (isStaircaseSection(cur, dir)) {
                int h = getStairHeight(cur);
                staircaseBuf.add(new AABB(cur.getX(), cur.getY(), cur.getZ(), cur.getX()+1, cur.getY()+h, cur.getZ()+1));
                cur.move(dir); cur.move(Direction.UP);
                steps++;
            }
            if (steps >= minSLen) stairs.addAll(staircaseBuf);
        }
    }

    private boolean isStaircaseSection(BlockPos pos, Direction dir) {
        int h = getStairHeight(pos);
        if (h < 2 || h > 5) return false;
        if (isPassable(pos.below()) || isPassable(pos.above(h))) return false;
        Direction[] perp = dir.getAxis() == Direction.Axis.X
            ? new Direction[]{Direction.NORTH, Direction.SOUTH}
            : new Direction[]{Direction.EAST, Direction.WEST};
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        for (Direction p : perp)
            for (int i = 0; i < h; i++) {
                check.set(pos.getX() + p.getStepX(), pos.getY() + i, pos.getZ() + p.getStepZ());
                if (isPassable(check)) return false;
            }
        return true;
    }

    private int getStairHeight(BlockPos pos) {
        int h = 0;
        while (isPassable(pos.above(h)) && h < 5) h++;
        return h;
    }

    // Uses cachedLevel and cachedAirOnly — no Minecraft.getInstance() per call
    private boolean isPassable(BlockPos pos) {
        if (cachedLevel == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return false;
            BlockState state = mc.level.getBlockState(pos);
            return state.isAir() || (!airOnly.getValue() && !state.isSolid());
        }
        BlockState state = cachedLevel.getBlockState(pos);
        return state.isAir() || (!cachedAirOnly && !state.isSolid());
    }

    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled()) return;
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        double cx = cam.x, cy = cam.y, cz = cam.z;
        // Cache all getValue() and detect flags once — not per box
        boolean dh = detectHoles.getValue(), d3 = detect3x1.getValue();
        boolean dt = detectTunnels.getValue(), ds = detectStairs.getValue();
        float hr = holeR.getValue(), hg = holeG.getValue(), hb = holeB.getValue();
        float h3r = hole3R.getValue(), h3g = hole3G.getValue(), h3b = hole3B.getValue();
        float tr = tunR.getValue(), tg = tunG.getValue(), tb = tunB.getValue();
        float sr = stairR.getValue(), sg = stairG.getValue(), sb = stairB.getValue();
        if (dh) for (AABB box : holes)
            RenderUtil.drawAABBRaw(pose, buffers, box.minX-cx, box.minY-cy, box.minZ-cz, box.maxX-cx, box.maxY-cy, box.maxZ-cz, hr, hg, hb, 0.8f);
        if (d3) for (AABB box : holes3x1)
            RenderUtil.drawAABBRaw(pose, buffers, box.minX-cx, box.minY-cy, box.minZ-cz, box.maxX-cx, box.maxY-cy, box.maxZ-cz, h3r, h3g, h3b, 0.8f);
        if (dt) for (AABB box : tunnels)
            RenderUtil.drawAABBRaw(pose, buffers, box.minX-cx, box.minY-cy, box.minZ-cz, box.maxX-cx, box.maxY-cy, box.maxZ-cz, tr, tg, tb, 0.6f);
        if (ds) for (AABB box : stairs)
            RenderUtil.drawAABBRaw(pose, buffers, box.minX-cx, box.minY-cy, box.minZ-cz, box.maxX-cx, box.maxY-cy, box.maxZ-cz, sr, sg, sb, 0.6f);
    }
}
