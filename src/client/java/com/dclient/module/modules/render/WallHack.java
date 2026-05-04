package com.dclient.module.modules.render;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * WallHack — cancels rendering of selected blocks so you can see through them.
 * Block names use the same formatted keys as BlockESP (e.g. "Stone", "Cobbled Deepslate").
 */
public class WallHack extends Module {
    public final Setting<Integer> opacity = addSetting("Opacity", 0, 0, 255);
    public final Setting<Boolean> players = addSetting("Players", true);
    public final Setting<Boolean> mobs    = addSetting("Mobs", true);

    // Block names selected via the block selector screen — same format as BlockESP keys
    public final Set<String> targetBlockNames = new LinkedHashSet<>();

    // Fast O(1) lookup set — rebuilt when targetBlockNames changes
    private final Set<Block> targetBlocks = new HashSet<>();
    private boolean dirty = true;

    public WallHack() {
        super("Wall Hack", Category.RENDER);
        // Defaults — must match BlockESP.getAllBlocks() formatted keys exactly
        targetBlockNames.add("Stone");
        targetBlockNames.add("Deepslate");
        targetBlockNames.add("Cobblestone");
        targetBlockNames.add("Dirt");
        targetBlockNames.add("Netherrack");
        targetBlockNames.add("Obsidian");
    }

    private ChunkPos lastChunk = null;

    @Override
    protected void onEnable() {
        rebuildTargetSet();
        lastChunk = null;
        reload();
    }

    @Override
    protected void onDisable() {
        targetBlocks.clear();
        lastChunk = null;
        reload();
    }

    public void tick() {
        // No per-tick reload needed — mixin handles it at compile time
    }

    /** Rebuilds the fast Block lookup from targetBlockNames using BlockESP's registry. */
    public void rebuildTargetSet() {
        targetBlocks.clear();
        var allBlocks = BlockESP.getAllBlocks();
        for (String name : targetBlockNames) {
            Block b = allBlocks.get(name);
            if (b != null) targetBlocks.add(b);
        }
        dirty = false;
    }

    /** Called from WallHackScreen after the user changes the selection. */
    public void onBlockSelectionChanged() {
        dirty = true;
        if (isEnabled()) {
            rebuildTargetSet();
            reload();
        }
    }

    public boolean isTargetBlock(Block block) {
        if (!isEnabled()) return false;
        if (dirty) rebuildTargetSet();
        return targetBlocks.contains(block);
    }

    public float getAlpha() {
        return opacity.getValue() / 255.0f;
    }

    private void reload() {
        var mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) mc.levelRenderer.allChanged();
    }
}
