package com.dclient.module.modules.render;

import com.dclient.client.render.RenderUtil;
import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.*;

public class BlockESP extends Module {
    public final Setting<Integer> range  = addSetting("Range", 64, 8, 256);
    public final Setting<Integer> mode   = addSetting("Mode", 0);
    public final Setting<Boolean> tracers = addSetting("Tracers", false);

    public static LinkedHashMap<String, Block> ALL_BLOCKS = null;

    public final Set<String> enabledBlocks = new LinkedHashSet<>();

    // Double-buffered block list — scan fills pending, tick swaps to active
    private final List<BlockPos> activeBlocks = new ArrayList<>();
    private final Map<BlockPos, float[]> activeBlockColors = new HashMap<>();
    private volatile List<BlockPos> pendingBlocks = null;
    private volatile Map<BlockPos, float[]> pendingBlockColors = null;

    private int timer = 0;
    private ExecutorService pool;

    public BlockESP() { super("Block ESP", Category.RENDER); }

    @Override
    protected void onEnable() {
        pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BlockESP-Scanner");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    protected void onDisable() {
        if (pool != null) { pool.shutdownNow(); pool = null; }
        activeBlocks.clear();
        activeBlockColors.clear();
    }

    public static LinkedHashMap<String, Block> getAllBlocks() {
        if (ALL_BLOCKS == null) {
            ALL_BLOCKS = new LinkedHashMap<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                var id = BuiltInRegistries.BLOCK.getKey(block);
                if (id == null) continue;
                String name = id.getPath().replace("_", " ");
                String[] words = name.split(" ");
                StringBuilder sb = new StringBuilder();
                for (String w : words)
                    if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
                ALL_BLOCKS.put(sb.toString().trim(), block);
            }
        }
        return ALL_BLOCKS;
    }

    public static float[] getBlockColor(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("diamond")) return new float[]{0.0f, 0.8f, 1.0f};
        if (lower.contains("ancient debris")) return new float[]{0.8f, 0.3f, 0.0f};
        if (lower.contains("emerald")) return new float[]{0.0f, 1.0f, 0.3f};
        if (lower.contains("gold")) return new float[]{1.0f, 0.9f, 0.0f};
        if (lower.contains("iron")) return new float[]{0.8f, 0.6f, 0.4f};
        if (lower.contains("redstone")) return new float[]{1.0f, 0.0f, 0.0f};
        if (lower.contains("lapis")) return new float[]{0.0f, 0.2f, 1.0f};
        if (lower.contains("coal")) return new float[]{0.3f, 0.3f, 0.3f};
        if (lower.contains("copper")) return new float[]{0.8f, 0.4f, 0.2f};
        if (lower.contains("spawner")) return new float[]{0.5f, 0.0f, 0.5f};
        if (lower.contains("chest")) return new float[]{1.0f, 0.8f, 0.0f};
        if (lower.contains("obsidian")) return new float[]{0.2f, 0.0f, 0.3f};
        if (lower.contains("bedrock")) return new float[]{0.2f, 0.2f, 0.2f};
        if (lower.contains("netherite")) return new float[]{0.4f, 0.3f, 0.3f};
        if (lower.contains("amethyst")) return new float[]{0.6f, 0.3f, 0.9f};
        return new float[]{1.0f, 1.0f, 1.0f};
    }

    public void tick() {
        if (!isEnabled() || enabledBlocks.isEmpty()) return;

        // Swap pending results to active (non-blocking)
        if (pendingBlocks != null) {
            activeBlocks.clear();
            activeBlocks.addAll(pendingBlocks);
            activeBlockColors.clear();
            activeBlockColors.putAll(pendingBlockColors);
            pendingBlocks = null;
            pendingBlockColors = null;
        }

        if (++timer < 20) return; // scan every second
        timer = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || pool == null) return;

        // Build enabled block map snapshot
        Map<Block, String> enabledMap = new HashMap<>();
        for (String name : enabledBlocks) {
            Block b = getAllBlocks().get(name);
            if (b != null) enabledMap.put(b, name);
        }
        if (enabledMap.isEmpty()) return;

        int r = range.getValue();
        BlockPos center = mc.player.blockPosition();

        // Run scan on background thread
        pool.submit(() -> {
            List<BlockPos> found = new ArrayList<>();
            Map<BlockPos, float[]> colors = new HashMap<>();
            BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
            var level = mc.level; // cache level reference before loop — not per block
            if (level == null) return;
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
                mpos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                Block block = level.getBlockState(mpos).getBlock();
                String name = enabledMap.get(block);
                if (name != null) {
                    BlockPos immutable = mpos.immutable();
                    found.add(immutable);
                    colors.put(immutable, getBlockColor(name)); // compute color once at scan time
                }
            }
            pendingBlocks = found;
            pendingBlockColors = colors;
        });
    }

    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled() || activeBlocks.isEmpty()) return;
        int m = mode.getValue();
        boolean tr = tracers.getValue();
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        for (BlockPos pos : activeBlocks) {
            float[] col = activeBlockColors.get(pos);
            if (col == null) continue;
            double bx = pos.getX(), by = pos.getY(), bz = pos.getZ();
            if (m == 1) {
                // Flat: draw 2D box without allocating AABB
                RenderUtil.drawAABBRaw(pose, buffers,
                    bx - cam.x, by - cam.y, bz - cam.z,
                    bx + 1 - cam.x, by - cam.y, bz + 1 - cam.z,
                    col[0], col[1], col[2], 1.0f);
            } else if (m == 2) {
                RenderUtil.drawCornerBox(pose, buffers, new net.minecraft.world.phys.AABB(pos), col[0], col[1], col[2], 1.0f);
            } else {
                RenderUtil.drawBlockBox(pose, buffers, pos, col[0], col[1], col[2], 1.0f);
            }
            if (tr) {
                RenderUtil.drawTracer(pose, buffers, bx + 0.5, by + 0.5, bz + 0.5, col[0], col[1], col[2], 1.0f);
            }
        }
    }
}
