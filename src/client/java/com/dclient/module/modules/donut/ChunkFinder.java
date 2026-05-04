package com.dclient.module.modules.donut;

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
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ChunkFinder — Detects suspicious player-built chunks.
 */
public class ChunkFinder extends Module {

    // ── Detection ────────────────────────────────────────────────────────────
    public final Setting<Boolean> detectRotated       = addSetting("Rotated Deepslate",     true);
    public final Setting<Boolean> detectCobbled       = addSetting("Cobbled Deepslate",     true);
    public final Setting<Boolean> detectEndStone      = addSetting("End Stone",             true);
    public final Setting<Boolean> detectDiorite       = addSetting("Diorite Vein",          true);
    public final Setting<Boolean> detectObsidian      = addSetting("Obsidian Vein",         true);
    public final Setting<Boolean> detectDripstone     = addSetting("Long Dripstone",        true);
    public final Setting<Boolean> detectVines         = addSetting("Long Vines",            true);
    public final Setting<Boolean> detectKelp          = addSetting("Full Kelp",             true);
    // Filter natural deepslate lines to reduce false positives
    public final Setting<Boolean> ignoreNaturalDeepslate = addSetting("Ignore Natural Deepslate", true);
    public final Setting<Boolean> ignoreExposed       = addSetting("Ignore Exposed",        true);
    public final Setting<Boolean> ignoreTrialChambers = addSetting("Ignore Trial Chambers", true);
    // Skip the chunk the player is standing in
    public final Setting<Boolean> ignorePlayerChunk   = addSetting("Ignore Player Chunk",   true);
    // XP orb / item entity detection
    public final Setting<Boolean> detectXP            = addSetting("Check XP Orbs",         true);
    public final Setting<Integer> maxXP               = addSetting("Max XP Orbs",           3);

    // ── Thresholds ───────────────────────────────────────────────────────────
    public final Setting<Integer> rotatedMin    = addSetting("Min Rotated",    3);
    public final Setting<Integer> cobbledMin    = addSetting("Min Cobbled",    4);
    public final Setting<Integer> endStoneMin   = addSetting("Min End Stone",  2);
    public final Setting<Integer> trialMin      = addSetting("Trial Min",      50);
    // Rate-limit alerts
    public final Setting<Integer> maxAlertsPerMin = addSetting("Max Alerts/Min", 5);

    // ── Render ───────────────────────────────────────────────────────────────
    public final Setting<Float>   renderY         = addSetting("Render Y",         64.0f);
    public final Setting<Boolean> highlightBlocks = addSetting("Highlight Blocks", true);
    public final Setting<Boolean> playSound       = addSetting("Play Sound",       true);

    // ── Threading constants ──────────────────────────────────────────────────
    private static final int  THREAD_COUNT        = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final int  MAX_CONCURRENT      = 50;
    private static final long RESCAN_INTERVAL_MS  = 5000L;
    private static final long QUEUE_REBUILD_MS    = 2000L;

    // ── State ────────────────────────────────────────────────────────────────
    private final Set<ChunkPos>                    flagged        = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos>                    notified       = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<ChunkPos, Long> scanned       = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Long> notifyTimes   = new ConcurrentHashMap<>();
    private final Map<BlockPos, BlockType>          blocks         = new ConcurrentHashMap<>();
    private final Queue<Long>                       recentAlerts   = new ConcurrentLinkedQueue<>();
    // BFS scan queue
    private final Queue<ChunkPos>                   scanQueue      = new ConcurrentLinkedQueue<>();
    private final AtomicLong                        activeScans    = new AtomicLong(0);
    // Entity counts per chunk (updated on tick)
    private final Map<ChunkPos, Integer>            chunkXPCounts  = new ConcurrentHashMap<>();

    private ChunkPos lastPlayerChunk = null;
    private long lastQueueRebuild = 0L;
    private long lastCleanup = 0L;
    private volatile boolean scanning = false;
    private ExecutorService pool;

    public ChunkFinder() { super("Chunk Finder", Category.DONUT); }

    @Override
    protected void onEnable() {
        clearAll();
        scanning = true;
        pool = Executors.newFixedThreadPool(THREAD_COUNT, r -> {
            Thread t = new Thread(r, "ChunkFinder-Worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    @Override
    protected void onDisable() {
        scanning = false;
        if (pool != null) { pool.shutdownNow(); pool = null; }
        clearAll();
    }

    private void clearAll() {
        flagged.clear(); notified.clear(); scanned.clear(); notifyTimes.clear();
        blocks.clear(); recentAlerts.clear(); scanQueue.clear();
        chunkXPCounts.clear(); heightCache.clear(); activeScans.set(0);
        lastPlayerChunk = null; lastQueueRebuild = 0L; lastCleanup = 0L;
    }

    public void onChunkLoad(LevelChunk chunk) { /* picked up by tick/render */ }

    // ── Tick: entity counting ─────────────────────────────────────────────────
    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Expire old alert timestamps
        long now = System.currentTimeMillis();
        while (!recentAlerts.isEmpty() && now - recentAlerts.peek() > 60_000L) recentAlerts.poll();

        // Periodic cleanup of distant chunks
        if (now - lastCleanup > 30_000L) { performCleanup(mc); lastCleanup = now; }

        // Count XP orbs per chunk
        chunkXPCounts.clear();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ExperienceOrb)
                chunkXPCounts.merge(new ChunkPos(e.blockPosition()), 1, Integer::sum);
        }
    }

    // ── Render: BFS queue management + draw ──────────────────────────────────
    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !scanning) return;

        int playerChunkX = (int) Math.floor(mc.player.getX() / 16.0);
        int playerChunkZ = (int) Math.floor(mc.player.getZ() / 16.0);
        ChunkPos currentChunk = new ChunkPos(playerChunkX, playerChunkZ);
        long now = System.currentTimeMillis();

        if (lastPlayerChunk == null) {
            lastPlayerChunk = currentChunk;
            buildBFSQueue(mc, currentChunk);
            lastQueueRebuild = now;
        } else if (!currentChunk.equals(lastPlayerChunk)) {
            lastPlayerChunk = currentChunk;
            cleanupDistant(mc, currentChunk);
            scanQueue.clear();
            buildBFSQueue(mc, currentChunk);
            lastQueueRebuild = now;
        } else if (now - lastQueueRebuild >= QUEUE_REBUILD_MS) {
            scanQueue.clear();
            buildBFSQueue(mc, currentChunk);
            lastQueueRebuild = now;
        }

        tryStartScans(mc);
        drawFlagged(pose, buffers, mc);
    }

    // ── BFS queue builder ─────────────────────────────────────────────────────
    private void buildBFSQueue(Minecraft mc, ChunkPos center) {
        int radius = Math.min(mc.options.renderDistance().get(), 32);
        Set<ChunkPos> visited = new HashSet<>();
        Deque<ChunkPos> bfs = new ArrayDeque<>();
        bfs.offer(center);
        visited.add(center);
        int[][] dirs = {{0,1},{1,0},{0,-1},{-1,0}};
        long now = System.currentTimeMillis();
        while (!bfs.isEmpty()) {
            ChunkPos cur = bfs.poll();
            Long scanTime = scanned.get(cur);
            if (scanTime == null || now - scanTime >= RESCAN_INTERVAL_MS)
                scanQueue.offer(cur);
            for (int[] d : dirs) {
                ChunkPos nb = new ChunkPos(cur.x + d[0], cur.z + d[1]);
                if (Math.abs(nb.x - center.x) > radius || Math.abs(nb.z - center.z) > radius) continue;
                if (visited.add(nb)) bfs.offer(nb);
            }
        }
    }

    private void cleanupDistant(Minecraft mc, ChunkPos center) {
        int r = Math.min(mc.options.renderDistance().get(), 32) + 2;
        scanned.keySet().removeIf(p -> Math.abs(p.x - center.x) > r || Math.abs(p.z - center.z) > r);
    }

    // ── Scan dispatch ─────────────────────────────────────────────────────────
    private void tryStartScans(Minecraft mc) {
        if (!scanning || pool == null) return;
        long now = System.currentTimeMillis();
        while (activeScans.get() < MAX_CONCURRENT && !scanQueue.isEmpty()) {
            ChunkPos pos = scanQueue.poll();
            if (pos == null) continue;
            Long last = scanned.get(pos);
            if (last != null && now - last < RESCAN_INTERVAL_MS) continue;
            if (!mc.level.hasChunk(pos.x, pos.z)) continue;
            LevelChunk chunk = mc.level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) continue;
            scanned.put(pos, now);
            notified.remove(pos); // allow re-notify on rescan
            activeScans.incrementAndGet();
            final LevelChunk fc = chunk;
            final ChunkPos fcp = pos;
            pool.submit(() -> {
                try { analyzeChunk(mc, fc, fcp); }
                finally { activeScans.decrementAndGet(); }
            });
        }
    }

    // ── Analysis ──────────────────────────────────────────────────────────────
    private void analyzeChunk(Minecraft mc, LevelChunk chunk, ChunkPos cp) {
        if (!scanning) return;
        boolean inEnd = mc.level.dimension().equals(net.minecraft.world.level.Level.END);
        int minY = mc.level.dimensionType().minY();
        int maxY = minY + mc.level.dimensionType().height() - 1;

        // Cache all settings once
        boolean doIgnoreTrial    = ignoreTrialChambers.getValue();
        boolean doIgnoreExposed  = ignoreExposed.getValue();
        boolean doIgnoreNatural  = ignoreNaturalDeepslate.getValue();
        boolean doRotated        = detectRotated.getValue();
        boolean doCobbled        = detectCobbled.getValue();
        boolean doEndStone       = detectEndStone.getValue();
        boolean doDiorite        = detectDiorite.getValue();
        boolean doObsidian       = detectObsidian.getValue();
        boolean doDripstone      = detectDripstone.getValue();
        boolean doVines          = detectVines.getValue();
        boolean doKelp           = detectKelp.getValue();
        boolean doHighlight      = highlightBlocks.getValue();
        int trialMinV            = trialMin.getValue();
        int rotatedMinV          = rotatedMin.getValue();
        int cobbledMinV          = cobbledMin.getValue();
        int endStoneMinV         = endStoneMin.getValue();

        ChunkAnalysis analysis = new ChunkAnalysis();

        // Reusable visited sets (single background thread per chunk)
        Set<Long> dioriteVisited  = new HashSet<>();
        Set<Long> obsidianVisited = new HashSet<>();
        Set<Long> vineTops        = new HashSet<>();
        Set<Long> kelpBases       = new HashSet<>();
        int kelpPlants = 0, fullKelpPlants = 0;
        BlockPos firstKelp = null;

        LevelChunkSection[] sections = chunk.getSections();
        for (int si = 0; si < sections.length; si++) {
            if (!scanning) return;
            LevelChunkSection sec = sections[si];
            if (sec == null || sec.hasOnlyAir()) continue;
            int baseY = minY + si * 16;
            if (baseY > maxY) continue;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        if (!scanning) return;
                        int wy = baseY + y;
                        if (wy < minY || wy > maxY) continue;
                        BlockState st = sec.getBlockState(x, y, z);
                        if (st.isAir()) continue;
                        var b = st.getBlock();

                        // Trial chamber count
                        if (doIgnoreTrial && isTrialBlock(b)) { analysis.trialCount++; }

                        BlockPos pos = new BlockPos(cp.getMinBlockX() + x, wy, cp.getMinBlockZ() + z);
                        boolean exposed = doIgnoreExposed && isExposed(mc, pos);

                        // Rotated deepslate
                        if (doRotated && isRotatedDeepslate(st) && !exposed) {
                            analysis.rotatedCount++;
                            if (doHighlight) blocks.put(pos, BlockType.ROTATED);
                            if (analysis.susPos == null) analysis.susPos = pos;
                        }

                        // Cobbled deepslate
                        if (doCobbled && b == Blocks.COBBLED_DEEPSLATE && !exposed) {
                            analysis.cobbledCount++;
                            if (doHighlight) blocks.put(pos, BlockType.COBBLED);
                            if (analysis.susPos == null) analysis.susPos = pos;
                        }

                        // End stone
                        if (doEndStone && b == Blocks.END_STONE && !inEnd && !exposed) {
                            analysis.endStoneCount++;
                            if (doHighlight) blocks.put(pos, BlockType.END_STONE);
                            if (analysis.susPos == null) analysis.susPos = pos;
                        }

                        // Filter natural deepslate lines before diorite/other checks
                        if (doIgnoreNatural && (b == Blocks.DEEPSLATE || b == Blocks.COBBLED_DEEPSLATE)
                                && isInLargeDeepslateLine(mc, pos, wy)) continue;

                        // Diorite/granite/andesite vein
                        if (doDiorite && !analysis.hasDioriteVein && isDioriteBlock(st)) {
                            long key = pos.asLong();
                            if (!dioriteVisited.contains(key)) {
                                int up = countRun(mc, pos, Direction.UP, this::isDioriteBlock, 20);
                                int dn = countRun(mc, pos, Direction.DOWN, this::isDioriteBlock, 20);
                                int total = up + 1 + dn;
                                if (total >= 5) {
                                    BlockPos start = pos.relative(Direction.DOWN, dn);
                                    boolean enclosed = true;
                                    for (int i = 0; i < total; i++) {
                                        BlockPos bp = start.relative(Direction.UP, i);
                                        dioriteVisited.add(bp.asLong());
                                        if (!isEnclosedByStone(mc, bp)) enclosed = false;
                                    }
                                    if (enclosed) { analysis.hasDioriteVein = true; if (analysis.susPos == null) analysis.susPos = pos; }
                                }
                            }
                        }

                        // Obsidian vein (y 15-63)
                        if (doObsidian && !analysis.hasObsidianVein && wy >= 15 && wy <= 63 && b == Blocks.OBSIDIAN) {
                            long key = pos.asLong();
                            if (!obsidianVisited.contains(key)) {
                                int up = countRun(mc, pos, Direction.UP, s -> s.getBlock() == Blocks.OBSIDIAN, 20);
                                int dn = countRun(mc, pos, Direction.DOWN, s -> s.getBlock() == Blocks.OBSIDIAN, 20);
                                int total = up + 1 + dn;
                                if (total >= 15) {
                                    BlockPos start = pos.relative(Direction.DOWN, dn);
                                    boolean enclosed = true;
                                    for (int i = 0; i < total; i++) {
                                        BlockPos bp = start.relative(Direction.UP, i);
                                        obsidianVisited.add(bp.asLong());
                                        if (!isEnclosedByNonObsidian(mc, bp)) enclosed = false;
                                    }
                                    if (enclosed) { analysis.hasObsidianVein = true; if (analysis.susPos == null) analysis.susPos = pos; }
                                }
                            }
                        }

                        // Long dripstone
                        if (doDripstone && !analysis.hasLongDripstone && b == Blocks.POINTED_DRIPSTONE
                                && st.hasProperty(BlockStateProperties.VERTICAL_DIRECTION)
                                && st.getValue(BlockStateProperties.VERTICAL_DIRECTION) == Direction.DOWN) {
                            if (mc.level.getBlockState(pos.relative(Direction.UP)).getBlock() != Blocks.POINTED_DRIPSTONE) {
                                int len = 1;
                                BlockPos cur = pos.relative(Direction.DOWN);
                                while (cur.getY() >= minY && len < 63) {
                                    BlockState cs = mc.level.getBlockState(cur);
                                    if (cs.getBlock() == Blocks.POINTED_DRIPSTONE
                                            && cs.hasProperty(BlockStateProperties.VERTICAL_DIRECTION)
                                            && cs.getValue(BlockStateProperties.VERTICAL_DIRECTION) == Direction.DOWN) {
                                        len++; cur = cur.relative(Direction.DOWN);
                                    } else break;
                                }
                                if (len >= 60) { analysis.hasLongDripstone = true; if (analysis.susPos == null) analysis.susPos = pos; }
                            }
                        }

                        // Long vine (y≥40, top detection)
                        if (doVines && !analysis.hasLongVine && wy >= 40 && b == Blocks.VINE) {
                            long key = pos.asLong();
                            if (!vineTops.contains(key)) {
                                BlockState above = mc.level.getBlockState(pos.relative(Direction.UP));
                                if (above.getBlock() != Blocks.VINE) {
                                    vineTops.add(key);
                                    int len = 1;
                                    BlockPos cur = pos.relative(Direction.DOWN);
                                    while (cur.getY() >= Math.max(minY, 40)) {
                                        if (mc.level.getBlockState(cur).getBlock() == Blocks.VINE) { len++; cur = cur.relative(Direction.DOWN); }
                                        else break;
                                    }
                                    if (len >= 37) { analysis.hasLongVine = true; if (analysis.susPos == null) analysis.susPos = pos; }
                                }
                            }
                        }

                        // Full kelp
                        if (doKelp && !analysis.allKelpFull && (b == Blocks.KELP || b == Blocks.KELP_PLANT)) {
                            long key = pos.asLong();
                            if (!kelpBases.contains(key)) {
                                BlockState below = mc.level.getBlockState(pos.relative(Direction.DOWN));
                                if (below.getBlock() != Blocks.KELP && below.getBlock() != Blocks.KELP_PLANT) {
                                    kelpBases.add(key);
                                    if (firstKelp == null) firstKelp = pos;
                                    int len = 1; boolean surface = false;
                                    BlockPos cur = pos.relative(Direction.UP);
                                    while (cur.getY() <= maxY) {
                                        BlockState cs = mc.level.getBlockState(cur);
                                        if (cs.getBlock() == Blocks.KELP || cs.getBlock() == Blocks.KELP_PLANT) { len++; cur = cur.relative(Direction.UP); }
                                        else { if (cs.getFluidState().isEmpty()) surface = true; break; }
                                    }
                                    if (!(len < 6 && surface)) { kelpPlants++; if (surface) fullKelpPlants++; }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (kelpPlants >= 8 && kelpPlants == fullKelpPlants) {
            analysis.allKelpFull = true;
            if (analysis.susPos == null) analysis.susPos = firstKelp;
        }

        evaluateChunk(mc, cp, analysis, trialMinV, rotatedMinV, cobbledMinV, endStoneMinV, doIgnoreTrial);
    }

    // ── Evaluation ────────────────────────────────────────────────────────────
    private void evaluateChunk(Minecraft mc, ChunkPos cp, ChunkAnalysis analysis,
                                int trialMinV, int rotatedMinV, int cobbledMinV, int endStoneMinV,
                                boolean doIgnoreTrial) {
        // Skip player's own chunk
        if (ignorePlayerChunk.getValue() && cp.equals(lastPlayerChunk)) return;

        // Skip if too many XP orbs (indicates active player)
        if (detectXP.getValue() && chunkXPCounts.getOrDefault(cp, 0) > maxXP.getValue()) return;

        // Skip trial chambers
        if (doIgnoreTrial && analysis.trialCount >= trialMinV) {
            flagged.remove(cp); return;
        }

        boolean sus = false;
        StringBuilder reasons = new StringBuilder();
        if (analysis.rotatedCount >= rotatedMinV)   { sus = true; reasons.append("Rotated[").append(analysis.rotatedCount).append("] "); }
        if (analysis.cobbledCount >= cobbledMinV)   { sus = true; reasons.append("Cobbled[").append(analysis.cobbledCount).append("] "); }
        if (analysis.endStoneCount >= endStoneMinV) { sus = true; reasons.append("EndStone[").append(analysis.endStoneCount).append("] "); }
        if (analysis.hasDioriteVein)   { sus = true; reasons.append("DioriteVein "); }
        if (analysis.hasObsidianVein)  { sus = true; reasons.append("ObsidianVein "); }
        if (analysis.hasLongDripstone) { sus = true; reasons.append("LongDripstone "); }
        if (analysis.hasLongVine)      { sus = true; reasons.append("LongVine "); }
        if (analysis.allKelpFull)      { sus = true; reasons.append("FullKelp "); }

        if (sus) {
            if (flagged.add(cp)) notify(mc, cp, analysis.susPos, reasons.toString().trim());
        } else {
            flagged.remove(cp);
        }
    }

    // ── Notification (rate-limiter) ───────────────────────────────────────────
    private void notify(Minecraft mc, ChunkPos cp, BlockPos susPos, String reasons) {
        long now = System.currentTimeMillis();
        if (recentAlerts.size() >= maxAlertsPerMin.getValue()) return;
        Long last = notifyTimes.get(cp);
        if (last != null && now - last < 45_000L) return;
        if (!notified.add(cp)) return;

        notifyTimes.put(cp, now);
        recentAlerts.offer(now);

        int ax = susPos != null ? susPos.getX() : cp.getMinBlockX() + 8;
        int az = susPos != null ? susPos.getZ() : cp.getMinBlockZ() + 8;
        String msg = "§6[Chunk Finder] §f" + reasons + " §7(X:" + ax + " Z:" + az + ")";

        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(msg), false);
                if (playSound.getValue() && mc.level != null)
                    mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.5f, false);
            }
        });
    }

    // ── Periodic cleanup ──────────────────────────────────────────────────────
    private void performCleanup(Minecraft mc) {
        if (mc.player == null) return;
        int vd = Math.min(mc.options.renderDistance().get(), 32);
        int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z;
        flagged.removeIf(p -> Math.abs(p.x - pcx) > vd + 5 || Math.abs(p.z - pcz) > vd + 5);
        scanned.keySet().removeIf(p -> Math.abs(p.x - pcx) > vd + 3 || Math.abs(p.z - pcz) > vd + 3);
        double maxDist = vd * 16 + 80;
        blocks.keySet().removeIf(p -> mc.player.distanceToSqr(p.getX(), p.getY(), p.getZ()) > maxDist * maxDist);
    }

    // Cache chunk surface heights — updated lazily, not every frame
    private final ConcurrentHashMap<ChunkPos, Double> heightCache = new ConcurrentHashMap<>();

    // ── Render ────────────────────────────────────────────────────────────────
    private void drawFlagged(PoseStack pose, MultiBufferSource buffers, Minecraft mc) {
        if (flagged.isEmpty()) return;
        var cam = mc.gameRenderer.getMainCamera().position();
        float ry = renderY.getValue();

        for (ChunkPos cp : flagged) {
            double sx = cp.getMinBlockX(), sz = cp.getMinBlockZ();
            // Use cached height, update lazily every 5s
            double plateY = heightCache.computeIfAbsent(cp, k -> {
                try {
                    LevelChunk chunk = mc.level.getChunkSource().getChunkNow(k.x, k.z);
                    if (chunk != null)
                        return (double) chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 8, 8);
                } catch (Exception ignored) {}
                return (double) ry;
            });
            RenderUtil.drawAABBRaw(pose, buffers,
                sx - cam.x, plateY - cam.y, sz - cam.z,
                sx + 16 - cam.x, plateY + 0.3 - cam.y, sz + 16 - cam.z,
                1.0f, 0.84f, 0.0f, 0.8f);
        }

        if (highlightBlocks.getValue()) {
            double vdSq = mc.options.renderDistance().get() * 16.0;
            vdSq *= vdSq;
            int count = 0;
            for (var entry : blocks.entrySet()) {
                if (count++ > 200) break;
                BlockPos pos = entry.getKey();
                if (mc.player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > vdSq) continue;
                float r, g, b;
                switch (entry.getValue()) {
                    case COBBLED   -> { r = 0.3f; g = 0.3f; b = 0.3f; }
                    case ROTATED   -> { r = 0.5f; g = 0.0f; b = 0.5f; }
                    case END_STONE -> { r = 1.0f; g = 1.0f; b = 0.8f; }
                    default        -> { r = 1.0f; g = 1.0f; b = 1.0f; }
                }
                RenderUtil.drawBlockBox(pose, buffers, pos, r, g, b, 0.8f);
            }
        }
    }

    public Set<ChunkPos> getFlaggedChunks() { return flagged; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isExposed(Minecraft mc, BlockPos pos) {
        if (pos.getY() < -32) return false;
        for (Direction dir : Direction.values()) {
            BlockState ns = mc.level.getBlockState(pos.relative(dir));
            if (ns.isAir() || !ns.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    /** Returns true if this block is part of a long natural deepslate line.
     *  Optimized: only checks within the chunk sections already in memory (no cross-chunk reads). */
    private boolean isInLargeDeepslateLine(Minecraft mc, BlockPos pos, int worldY) {
        // Only apply the expensive check above the natural deepslate layer
        // Below y=-8 deepslate is always natural, skip the check entirely
        if (worldY <= -8) return true;
        // Quick check: if there are 8+ consecutive deepslate in X or Z within the chunk, treat as natural
        final int quickThreshold = 8;
        int xCount = 1, zCount = 1;
        for (int i = 1; i < quickThreshold; i++) {
            if (!isNaturalDeepslate(mc.level.getBlockState(pos.relative(Direction.EAST, i)))) break;
            xCount++;
        }
        if (xCount >= quickThreshold) return true;
        for (int i = 1; i < quickThreshold; i++) {
            if (!isNaturalDeepslate(mc.level.getBlockState(pos.relative(Direction.WEST, i)))) break;
            xCount++;
        }
        if (xCount >= quickThreshold) return true;
        for (int i = 1; i < quickThreshold; i++) {
            if (!isNaturalDeepslate(mc.level.getBlockState(pos.relative(Direction.SOUTH, i)))) break;
            zCount++;
        }
        if (zCount >= quickThreshold) return true;
        for (int i = 1; i < quickThreshold; i++) {
            if (!isNaturalDeepslate(mc.level.getBlockState(pos.relative(Direction.NORTH, i)))) break;
            zCount++;
        }
        return zCount >= quickThreshold;
    }

    private boolean isNaturalDeepslate(BlockState st) {
        var b = st.getBlock();
        return b == Blocks.DEEPSLATE || b == Blocks.COBBLED_DEEPSLATE;
    }

    private boolean isRotatedDeepslate(BlockState st) {
        if (st.getBlock() != Blocks.DEEPSLATE) return false;
        if (!st.hasProperty(BlockStateProperties.AXIS)) return false;
        return st.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y;
    }

    private boolean isDioriteBlock(BlockState st) {
        var b = st.getBlock();
        return b == Blocks.DIORITE || b == Blocks.GRANITE || b == Blocks.ANDESITE;
    }

    private boolean isTrialBlock(net.minecraft.world.level.block.Block b) {
        return b == Blocks.WAXED_COPPER_BLOCK || b == Blocks.WAXED_OXIDIZED_COPPER || b == Blocks.TUFF_BRICKS;
    }

    @FunctionalInterface interface BlockPredicate { boolean test(BlockState st); }

    private int countRun(Minecraft mc, BlockPos from, Direction dir, BlockPredicate pred, int max) {
        int count = 0;
        BlockPos cur = from.relative(dir);
        while (count < max) {
            if (!pred.test(mc.level.getBlockState(cur))) break;
            count++; cur = cur.relative(dir);
        }
        return count;
    }

    private boolean isEnclosedByStone(Minecraft mc, BlockPos pos) {
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST})
            if (!mc.level.getBlockState(pos.relative(d)).is(Blocks.STONE)) return false;
        return true;
    }

    private boolean isEnclosedByNonObsidian(Minecraft mc, BlockPos pos) {
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST})
            if (mc.level.getBlockState(pos.relative(d)).is(Blocks.OBSIDIAN)) return false;
        return true;
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private static class ChunkAnalysis {
        int rotatedCount = 0, cobbledCount = 0, endStoneCount = 0, trialCount = 0;
        boolean hasDioriteVein = false, hasObsidianVein = false;
        boolean hasLongDripstone = false, hasLongVine = false, allKelpFull = false;
        BlockPos susPos = null;
    }

    private enum BlockType { COBBLED, ROTATED, END_STONE }
}
