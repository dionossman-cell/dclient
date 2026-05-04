package com.dclient.module.modules.render;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerNotifier extends Module {
    // Detection
    public final Setting<Boolean> detectSpawners    = addSetting("Spawners", true);
    public final Setting<Boolean> detectChests      = addSetting("Chests", false);
    public final Setting<Boolean> detectObsidian    = addSetting("Obsidian", false);
    public final Setting<Boolean> detectBedrock     = addSetting("Bedrock", false);
    public final Setting<Boolean> detectAncientDebris = addSetting("Ancient Debris", false);
    public final Setting<Boolean> detectDiamondOre  = addSetting("Diamond Ore", false);

    // Notifications
    public final Setting<Boolean> chatNotify        = addSetting("Chat Notify", true);
    public final Setting<Boolean> disconnectOnFind  = addSetting("Auto Disconnect", false);

    // Webhook
    public final Setting<Boolean> webhookEnabled    = addSetting("Webhook", false);
    public final Setting<String> webhookUrl         = addSetting("Webhook URL", "");
    public final Setting<Boolean> selfPing          = addSetting("Ping Me", false);
    public final Setting<String> discordId          = addSetting("Discord ID", "");

    // Render
    public final Setting<Boolean> showEsp           = addSetting("ESP", true);
    public final Setting<Boolean> showTracers       = addSetting("Tracers", true);
    public final Setting<Float> r                   = addSetting("Red",   1.0f, 0.0f, 1.0f);
    public final Setting<Float> g                   = addSetting("Green", 0.0f, 0.0f, 1.0f);
    public final Setting<Float> b                   = addSetting("Blue",  1.0f, 0.0f, 1.0f);
    public final Setting<Float> renderDist          = addSetting("Range", 3000.0f, 10.0f, 3000.0f);

    private final Set<ChunkPos> processedChunks = ConcurrentHashMap.newKeySet();
    private final Map<BlockPos, Block> foundBlocks = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();
    private int totalFound = 0;

    public SpawnerNotifier() { super("BlockNotifier", Category.RENDER); }

    @Override
    protected void onEnable() {
        processedChunks.clear();
        foundBlocks.clear();
        totalFound = 0;
    }

    @Override
    protected void onDisable() {
        processedChunks.clear();
        foundBlocks.clear();
    }

    public void onChunkLoad(LevelChunk chunk) {
        if (!isEnabled()) return;
        ChunkPos cp = chunk.getPos();
        if (processedChunks.contains(cp)) return;
        processedChunks.add(cp);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Set<Block> targets = getTargetBlocks();
        if (targets.isEmpty()) return;

        Map<Block, List<BlockPos>> found = new HashMap<>();
        int minY = mc.level.dimensionType().minY();
        int maxY = minY + mc.level.dimensionType().height();

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    mpos.set(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z);
                    Block block = chunk.getBlockState(mpos).getBlock();
                    if (targets.contains(block)) {
                        BlockPos immutable = mpos.immutable();
                        found.computeIfAbsent(block, k -> new ArrayList<>()).add(immutable);
                        foundBlocks.put(immutable, block);
                        totalFound++;
                    }
                }
            }
        }

        if (!found.isEmpty()) {
            handleFound(cp, found);
        }
    }

    private Set<Block> getTargetBlocks() {
        // Build fresh each call — called only on chunk load, not per tick
        Set<Block> t = new java.util.HashSet<>(8);
        if (detectSpawners.getValue())     t.add(Blocks.SPAWNER);
        if (detectChests.getValue())       t.add(Blocks.CHEST);
        if (detectObsidian.getValue())     t.add(Blocks.OBSIDIAN);
        if (detectBedrock.getValue())      t.add(Blocks.BEDROCK);
        if (detectAncientDebris.getValue()) t.add(Blocks.ANCIENT_DEBRIS);
        if (detectDiamondOre.getValue())   { t.add(Blocks.DIAMOND_ORE); t.add(Blocks.DEEPSLATE_DIAMOND_ORE); }
        return t;
    }

    private void handleFound(ChunkPos cp, Map<Block, List<BlockPos>> found) {
        Minecraft mc = Minecraft.getInstance();
        int cx = cp.x * 16 + 8, cz = cp.z * 16 + 8;

        StringBuilder msg = new StringBuilder("[BlockNotifier] Found at " + cx + ", " + cz + ": ");
        found.forEach((block, positions) ->
            msg.append(block.getName().getString()).append(" x").append(positions.size()).append(" "));

        mc.execute(() -> {
            if (chatNotify.getValue() && mc.player != null)
                mc.player.displayClientMessage(Component.literal(msg.toString()), false);

            // Play sound alert
            if (mc.level != null && mc.player != null) {
                mc.level.playLocalSound(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    1.0f, 1.5f, false);
            }

            if (disconnectOnFind.getValue() && mc.player != null) {
                mc.player.displayClientMessage(Component.literal("[BlockNotifier] Disconnecting!"), false);
                mc.disconnect(new net.minecraft.client.gui.screens.DisconnectedScreen(
                    new net.minecraft.client.gui.screens.TitleScreen(),
                    Component.literal("BlockNotifier"),
                    Component.literal("Target block found!")), false);
            }
        });

        if (webhookEnabled.getValue() && !webhookUrl.getValue().trim().isEmpty()) {
            sendWebhook(cp, found);
        }
    }

    private void sendWebhook(ChunkPos cp, Map<Block, List<BlockPos>> found) {
        String url = webhookUrl.getValue().trim();
        if (url.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                int cx = cp.x * 16 + 8, cz = cp.z * 16 + 8;
                String ping = selfPing.getValue() && !discordId.getValue().isEmpty()
                    ? "<@" + discordId.getValue() + ">" : "";
                StringBuilder items = new StringBuilder();
                found.forEach((block, positions) ->
                    items.append("• ").append(block.getName().getString())
                         .append(": x").append(positions.size()).append("\\n"));

                String json = String.format(
                    "{\"content\":\"%s\",\"username\":\"BlockNotifier\"," +
                    "\"embeds\":[{\"title\":\"Block Notifier Alert\"," +
                    "\"description\":\"Blocks found at %d, %d\"," +
                    "\"color\":16753920," +
                    "\"fields\":[{\"name\":\"Blocks\",\"value\":\"%s\",\"inline\":false}," +
                    "{\"name\":\"Coords\",\"value\":\"%d, %d\",\"inline\":true}," +
                    "{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}]}]}",
                    ping, cx, cz, items.toString(), cx, cz, System.currentTimeMillis() / 1000);

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30)).build();
                httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {}
        });
    }

    private int tickTimer = 0;

    public void tick() {
        if (!isEnabled()) return;
        if (++tickTimer < 40) return; // scan every 2 seconds
        tickTimer = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int cx = mc.player.chunkPosition().x, cz = mc.player.chunkPosition().z;
        int vd = Math.min(mc.options.renderDistance().get(), 32);
        for (int dx = -vd; dx <= vd; dx++) {
            for (int dz = -vd; dz <= vd; dz++) {
                ChunkPos cp = new ChunkPos(cx + dx, cz + dz);
                if (!processedChunks.contains(cp)) {
                    LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cp.x, cp.z);
                    if (chunk != null) onChunkLoad(chunk);
                }
            }
        }
    }

    public void render(PoseStack pose, MultiBufferSource buffers) {
        if (!isEnabled() || foundBlocks.isEmpty()) return;
        if (!showEsp.getValue() && !showTracers.getValue()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float rv = r.getValue(), gv = g.getValue(), bv = b.getValue();
        float maxDist = renderDist.getValue();
        boolean doEsp = showEsp.getValue(), doTracers = showTracers.getValue();
        double maxDistSq = maxDist > 0 ? (double) maxDist * maxDist : Double.MAX_VALUE;

        for (BlockPos pos : foundBlocks.keySet()) {
            if (mc.player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > maxDistSq) continue;
            if (doEsp) RenderUtil.drawBlockBox(pose, buffers, pos, rv, gv, bv, 0.8f);
            if (doTracers)
                RenderUtil.drawTracer(pose, buffers, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rv, gv, bv, 1.0f);
        }
    }
}
