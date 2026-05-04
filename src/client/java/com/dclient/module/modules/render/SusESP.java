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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sus ESP — detects suspicious entities and blocks.
 * Detects: bee hives (full), wandering traders, llamas, pillagers,
 * skeleton horses, rotated deepslate, and kelp chunks.
 */
public class SusESP extends Module {
    public final Setting<Boolean> beeHives        = addSetting("Bee Hives",        true);
    public final Setting<Boolean> wanderingESP    = addSetting("Traders",           true);
    public final Setting<Boolean> llamaESP        = addSetting("Trader Llamas",     true);
    public final Setting<Boolean> pillagerESP     = addSetting("Pillagers",         true);
    public final Setting<Boolean> skeletonHorse   = addSetting("Skeleton Horses",   true);
    public final Setting<Boolean> rotatedDeep     = addSetting("Rotated Deepslate", true);
    public final Setting<Boolean> kelpChunks      = addSetting("Kelp Chunks",       true);
    public final Setting<Boolean> tracers         = addSetting("Tracers",           true);

    // Detected state
    private final Set<BlockPos>  beeHivePos    = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos>  rotatedPos    = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos>  kelpPos       = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos>  kelpBlockPos  = ConcurrentHashMap.newKeySet(); // individual kelp bases
    private final Set<UUID>      traders       = ConcurrentHashMap.newKeySet();
    private final Set<UUID>      llamas        = ConcurrentHashMap.newKeySet();
    private final Set<UUID>      pillagers     = ConcurrentHashMap.newKeySet();
    private final Set<UUID>      skelHorses    = ConcurrentHashMap.newKeySet();

    // Notification tracking
    private final Set<BlockPos>  notifiedHives = ConcurrentHashMap.newKeySet();
    private final Set<UUID>      notifiedEnt   = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos>  notifiedKelp  = ConcurrentHashMap.newKeySet();

    // Chunk scan state
    private final Set<ChunkPos>  scanned       = new HashSet<>();
    private final List<ChunkPos> scanQueue     = new ArrayList<>();
    private int scanCursor = 0;
    private ChunkPos lastScanCenter = null;

    private int tickTimer = 0;
    // Reusable sets for scanEntities — allocated once, cleared each call
    private final Set<UUID> scanTraders   = new HashSet<>();
    private final Set<UUID> scanLlamas    = new HashSet<>();
    private final Set<UUID> scanPillagers = new HashSet<>();
    private final Set<UUID> scanSkel      = new HashSet<>();
    // Reusable set for scanChunk kelp processing — allocated once, cleared each call (background thread only)
    private final Set<BlockPos> kelpProcessedBases = new HashSet<>();

    private java.util.concurrent.ExecutorService scanPool;

    public SusESP() { super("Sus ESP", Category.RENDER); }

    @Override
    protected void onEnable() {
        clearAll();
        scanPool = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SusESP-Scanner");
            t.setDaemon(true);
            return t;
        });
    }
    @Override
    protected void onDisable() {
        if (scanPool != null) { scanPool.shutdownNow(); scanPool = null; }
        clearAll();
    }

    private void clearAll() {
        beeHivePos.clear(); rotatedPos.clear(); kelpPos.clear(); kelpBlockPos.clear();
        traders.clear(); llamas.clear(); pillagers.clear(); skelHorses.clear();
        notifiedHives.clear(); notifiedEnt.clear(); notifiedKelp.clear();
        scanned.clear(); scanQueue.clear(); scanCursor = 0; lastScanCenter = null;
        lastScanMs = 0;
    }

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
        tickTimer++;

        // Entity scan every 10 ticks
        if (tickTimer % 10 == 0) scanEntities(mc);

        // Bee hive scan every 20 ticks
        if (tickTimer % 20 == 0 && beeHives.getValue()) scanBeeHives(mc);

        // Chunk scan (rotated deepslate + kelp) — 2 chunks per tick
        ChunkPos pc = mc.player.chunkPosition();
        int vd = Math.min(mc.options.renderDistance().get(), 32);
        if (lastScanCenter == null || pc.x != lastScanCenter.x || pc.z != lastScanCenter.z) {
            scanQueue.clear();
            for (int dx = -vd; dx <= vd; dx++)
                for (int dz = -vd; dz <= vd; dz++)
                    scanQueue.add(new ChunkPos(pc.x + dx, pc.z + dz));
            scanCursor = 0;
            lastScanCenter = pc;
            scanned.removeIf(p -> Math.abs(p.x - pc.x) > vd + 2 || Math.abs(p.z - pc.z) > vd + 2);
            rotatedPos.removeIf(p -> {
                ChunkPos cp = new ChunkPos(p);
                return Math.abs(cp.x - pc.x) > vd + 2 || Math.abs(cp.z - pc.z) > vd + 2;
            });
            kelpPos.removeIf(p -> Math.abs(p.x - pc.x) > vd + 2 || Math.abs(p.z - pc.z) > vd + 2);
        }

        int done = 0;
        while (scanCursor < scanQueue.size() && done < 1) {
            ChunkPos cp = scanQueue.get(scanCursor++);
            if (scanned.contains(cp)) continue;
            LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cp.x, cp.z);
            if (chunk != null) {
                scanned.add(cp);
                if (scanPool != null) {
                    final LevelChunk fc = chunk;
                    final ChunkPos fcp = cp;
                    scanPool.submit(() -> scanChunk(mc, fc, fcp));
                }
                done++;
            }
        }
    }

    private void scanChunk(Minecraft mc, LevelChunk chunk, ChunkPos cp) {
        int minY = mc.level.dimensionType().minY();
        LevelChunkSection[] sections = chunk.getSections();
        // Cache getValue() calls once — not per block
        boolean doRotated = rotatedDeep.getValue();
        boolean doKelp = kelpChunks.getValue();

        // Rotated deepslate
        if (doRotated) {
            for (int si = 0; si < sections.length; si++) {
                LevelChunkSection sec = sections[si];
                if (sec == null || sec.hasOnlyAir()) continue;
                int baseY = minY + si * 16;
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++)
                        for (int y = 0; y < 16; y++) {
                            BlockState st = sec.getBlockState(x, y, z);
                            if (isRotatedDeepslate(st)) {
                                BlockPos pos = new BlockPos(cp.getMinBlockX() + x, baseY + y, cp.getMinBlockZ() + z);
                                rotatedPos.add(pos);
                            }
                        }
            }
        }

        // Kelp chunks — check for ≥7 kelp plants reaching surface
        if (doKelp) {
            int kelpPlants = 0, fullKelp = 0;
            kelpProcessedBases.clear();
            int maxY = minY + mc.level.dimensionType().height();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        BlockPos pos = new BlockPos(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z);
                        var block = chunk.getBlockState(pos).getBlock();
                        if (block != Blocks.KELP && block != Blocks.KELP_PLANT) continue;
                        // Find base
                        BlockPos base = pos;
                        while (true) {
                            var below = chunk.getBlockState(base.below()).getBlock();
                            if (below == Blocks.KELP || below == Blocks.KELP_PLANT) base = base.below();
                            else break;
                        }
                        if (kelpProcessedBases.contains(base)) continue;
                        kelpProcessedBases.add(base);
                        // Find top
                        BlockPos top = base;
                        while (true) {
                            var above = chunk.getBlockState(top.above()).getBlock();
                            if (above == Blocks.KELP || above == Blocks.KELP_PLANT) top = top.above();
                            else break;
                        }
                        kelpPlants++;
                        if (chunk.getBlockState(top.above()).isAir()) fullKelp++;
                        y = top.getY();
                    }
                }
            }

            if (kelpPlants >= 7 && (kelpPlants - fullKelp) <= 1) {
                if (kelpPos.add(cp) && !notifiedKelp.contains(cp)) {
                    // Store individual kelp bases for block highlighting
                    kelpBlockPos.addAll(kelpProcessedBases);
                    notifiedKelp.add(cp);
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.displayClientMessage(Component.literal(
                                "[SusESP] Kelp chunk [" + cp.x + ", " + cp.z + "]"), false);
                            playPing(mc);
                        }
                    });
                }
            } else {
                kelpPos.remove(cp);
                kelpBlockPos.removeIf(p -> new ChunkPos(p).equals(cp));
            }
        }
    }

    private void scanBeeHives(Minecraft mc) {
        Set<BlockPos> current = new HashSet<>();
        int cx = mc.player.chunkPosition().x, cz = mc.player.chunkPosition().z;
        int vd = Math.min(mc.options.renderDistance().get(), 32);
        for (int dx = -vd; dx <= vd; dx++) {
            for (int dz = -vd; dz <= vd; dz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                chunk.getBlockEntities().forEach((pos, be) -> {
                    BlockState st = mc.level.getBlockState(pos);
                    if (st.getBlock() == Blocks.BEE_NEST || st.getBlock() == Blocks.BEEHIVE) {
                        try {
                            if (st.getValue(BeehiveBlock.HONEY_LEVEL) == 5) {
                                current.add(pos);
                                if (!notifiedHives.contains(pos)) {
                                    notifiedHives.add(pos);
                                    mc.execute(() -> {
                                        if (mc.player != null) {
                                            mc.player.displayClientMessage(Component.literal(
                                                "[SusESP] Full beehive at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
                                            playPing(mc);
                                        }
                                    });
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        }
        beeHivePos.clear(); beeHivePos.addAll(current);
    }

    private void scanEntities(Minecraft mc) {
        scanTraders.clear(); scanLlamas.clear(); scanPillagers.clear(); scanSkel.clear();
        Set<UUID> curTraders = scanTraders, curLlamas = scanLlamas,
                  curPillagers = scanPillagers, curSkel = scanSkel;        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof WanderingTrader) curTraders.add(e.getUUID());
            else if (e instanceof TraderLlama) curLlamas.add(e.getUUID());
            else if (e instanceof Pillager)    curPillagers.add(e.getUUID());
            else if (e instanceof SkeletonHorse) curSkel.add(e.getUUID());
        }
        notifyNew(curTraders, traders, notifiedEnt, mc, "Wandering Trader");
        notifyNew(curLlamas,  llamas,  notifiedEnt, mc, "Trader Llama");
        notifyNew(curPillagers, pillagers, notifiedEnt, mc, "Pillager");
        notifyNew(curSkel, skelHorses, notifiedEnt, mc, "Skeleton Horse");
        traders.retainAll(curTraders); traders.addAll(curTraders);
        llamas.retainAll(curLlamas);   llamas.addAll(curLlamas);
        pillagers.retainAll(curPillagers); pillagers.addAll(curPillagers);
        skelHorses.retainAll(curSkel); skelHorses.addAll(curSkel);
    }

    private void notifyNew(Set<UUID> current, Set<UUID> known, Set<UUID> notified, Minecraft mc, String name) {
        for (UUID id : current) {
            if (!notified.contains(id)) {
                notified.add(id);
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal("[SusESP] Found " + name + "!"), false);
                        playPing(mc);
                    }
                });
            }
        }
    }

    private void playPing(Minecraft mc) {
        if (mc.level != null && mc.player != null)
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.5f, false);
    }

    private boolean isRotatedDeepslate(BlockState st) {
        if (st.isAir()) return false;
        var b = st.getBlock();
        if (b != Blocks.DEEPSLATE && b != Blocks.DEEPSLATE_BRICKS && b != Blocks.DEEPSLATE_TILES) return false;
        if (st.hasProperty(BlockStateProperties.AXIS))
            return st.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y;
        return false;
    }

    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Bee hives — yellow
        for (BlockPos pos : beeHivePos) {
            RenderUtil.drawBlockBox(pose, buffers, pos, 1.0f, 1.0f, 0.0f, 0.8f);
            if (tracers.getValue()) RenderUtil.drawTracer(pose, buffers, pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, 1f,1f,0f,1f);
        }

        // Rotated deepslate — pink
        boolean tr = tracers.getValue(); // cache once for all loops below
        for (BlockPos pos : rotatedPos) {
            RenderUtil.drawBlockBox(pose, buffers, pos, 1.0f, 0.0f, 1.0f, 0.5f);
            if (tr) RenderUtil.drawTracer(pose, buffers, pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, 1f,0f,1f,0.8f);
        }

        // Kelp blocks — blue box on each kelp base
        for (BlockPos pos : kelpBlockPos) {
            RenderUtil.drawBlockBox(pose, buffers, pos, 0.0f, 0.4f, 1.0f, 0.7f);
            if (tr) RenderUtil.drawTracer(pose, buffers, pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, 0f,0.4f,1f,1f);
        }

        // Entities — cache all getValue() before loop
        boolean wESP = wanderingESP.getValue(), lESP = llamaESP.getValue();
        boolean pESP = pillagerESP.getValue(), sESP = skeletonHorse.getValue();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == null) continue;
            float er, eg, eb;
            if      (wESP && e instanceof WanderingTrader)  { er = 0f;   eg = 0.6f; eb = 1f;   }
            else if (lESP && e instanceof TraderLlama)      { er = 0f;   eg = 0.6f; eb = 1f;   }
            else if (pESP && e instanceof Pillager)         { er = 1f;   eg = 0f;   eb = 0f;   }
            else if (sESP && e instanceof SkeletonHorse)    { er = 0.8f; eg = 0.8f; eb = 0.8f; }
            else continue;
            RenderUtil.drawEntityBox(pose, buffers, e, er, eg, eb, 0.7f);
            if (tr) {
                var box = e.getBoundingBox();
                RenderUtil.drawTracer(pose, buffers,
                    (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5,
                    er, eg, eb, 1f);
            }
        }
    }
}
