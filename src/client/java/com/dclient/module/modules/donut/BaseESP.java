package com.dclient.module.modules.donut;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base ESP / Advanced Stash Finder — scans chunks for storage blocks and spawners to detect player bases.
 */
public class BaseESP extends Module {
    public final Setting<Integer> minStorage    = addSetting("Min Storage", 4);
    public final Setting<Integer> minDistance   = addSetting("Min Distance", 0);
    public final Setting<Boolean> critSpawner   = addSetting("Spawner Alert", true);
    public final Setting<Boolean> chatNotify    = addSetting("Chat Notify", true);
    public final Setting<Boolean> soundAlert    = addSetting("Sound Alert", true);
    public final Setting<Boolean> disconnectOnFind = addSetting("Auto Disconnect", false);

    // Block types to count
    public final Setting<Boolean> countChests   = addSetting("Chests", true);
    public final Setting<Boolean> countBarrels  = addSetting("Barrels", true);
    public final Setting<Boolean> countShulkers = addSetting("Shulkers", true);
    public final Setting<Boolean> countFurnaces = addSetting("Furnaces", true);
    public final Setting<Boolean> countHoppers  = addSetting("Hoppers", true);
    public final Setting<Boolean> countSpawners = addSetting("Spawners", true);

    // Webhook
    public final Setting<Boolean> webhookEnabled = addSetting("Webhook", false);
    public final Setting<String> webhookUrl      = addSetting("Webhook URL", "");
    public final Setting<Boolean> selfPing       = addSetting("Ping Me", false);
    public final Setting<String> discordId       = addSetting("Discord ID", "");

    public final Map<ChunkPos, StashChunk> foundChunks = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    public BaseESP() { super("Base ESP", Category.DONUT); }

    @Override
    protected void onEnable() { foundChunks.clear(); }

    @Override
    protected void onDisable() { foundChunks.clear(); }

    public void onChunkLoad(LevelChunk chunk) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ChunkPos cp = chunk.getPos();
        double cx = cp.x * 16, cz = cp.z * 16;
        double dist = Math.sqrt(cx*cx + cz*cz);
        if (dist < minDistance.getValue()) return;

        StashChunk sc = new StashChunk(cp);
        for (var be : chunk.getBlockEntities().values()) {
            if (be instanceof SpawnerBlockEntity && countSpawners.getValue()) sc.spawners++;
            else if ((be instanceof ChestBlockEntity || be instanceof TrappedChestBlockEntity) && countChests.getValue()) sc.chests++;
            else if (be instanceof BarrelBlockEntity && countBarrels.getValue()) sc.barrels++;
            else if (be instanceof ShulkerBoxBlockEntity && countShulkers.getValue()) sc.shulkers++;
            else if (be instanceof EnderChestBlockEntity && countChests.getValue()) sc.enderChests++;
            else if (be instanceof AbstractFurnaceBlockEntity && countFurnaces.getValue()) sc.furnaces++;
            else if (be instanceof HopperBlockEntity && countHoppers.getValue()) sc.hoppers++;
            else if (be instanceof DispenserBlockEntity && countHoppers.getValue()) sc.dispensers++;
        }

        boolean isCrit = critSpawner.getValue() && sc.spawners > 0;
        boolean isStash = isCrit || sc.getTotal() >= minStorage.getValue();
        if (!isStash) return;

        StashChunk prev = foundChunks.put(cp, sc);
        if (prev != null && sc.countsEqual(prev)) return; // no change

        String type = isCrit ? "Spawner Base" : "Stash";
        String reason = isCrit ? "Spawner detected" : sc.getTotal() + " storage blocks";
        String msg = "[BaseESP] Found " + type + " at " + sc.x + ", " + sc.z + " — " + reason;

        mc.execute(() -> {
            if (chatNotify.getValue() && mc.player != null)
                mc.player.displayClientMessage(Component.literal(msg), false);
            if (soundAlert.getValue() && mc.level != null && mc.player != null)
                mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 1.5f, false);
            if (disconnectOnFind.getValue()) mc.disconnect(new net.minecraft.client.gui.screens.DisconnectedScreen(
                new net.minecraft.client.gui.screens.TitleScreen(),
                Component.literal("Base ESP"),
                Component.literal("Base found!")), false);
        });

        if (webhookEnabled.getValue() && !webhookUrl.getValue().isEmpty())
            sendWebhook(sc, isCrit, reason);
    }

    private void sendWebhook(StashChunk sc, boolean isCrit, String reason) {
        String url = webhookUrl.getValue().trim();
        if (url.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            try {
                String ping = selfPing.getValue() && !discordId.getValue().isEmpty()
                    ? "<@" + discordId.getValue() + "> " : "";
                String server = Minecraft.getInstance().getCurrentServer() != null
                    ? Minecraft.getInstance().getCurrentServer().ip : "Unknown";
                StringBuilder items = new StringBuilder();
                if (sc.spawners > 0) items.append("Spawners: ").append(sc.spawners).append("\\n");
                if (sc.chests > 0) items.append("Chests: ").append(sc.chests).append("\\n");
                if (sc.barrels > 0) items.append("Barrels: ").append(sc.barrels).append("\\n");
                if (sc.shulkers > 0) items.append("Shulkers: ").append(sc.shulkers).append("\\n");
                if (sc.enderChests > 0) items.append("Ender Chests: ").append(sc.enderChests).append("\\n");
                if (sc.furnaces > 0) items.append("Furnaces: ").append(sc.furnaces).append("\\n");
                if (sc.hoppers > 0) items.append("Hoppers: ").append(sc.hoppers).append("\\n");
                if (sc.dispensers > 0) items.append("Dispensers: ").append(sc.dispensers).append("\\n");

                String json = String.format(
                    "{\"content\":\"%s\",\"username\":\"Base ESP\",\"embeds\":[{" +
                    "\"title\":\"Base Found!\",\"description\":\"%s found at %d, %d\"," +
                    "\"color\":%d,\"fields\":[" +
                    "{\"name\":\"Reason\",\"value\":\"%s\",\"inline\":false}," +
                    "{\"name\":\"Total\",\"value\":\"%d\",\"inline\":true}," +
                    "{\"name\":\"Blocks\",\"value\":\"%s\",\"inline\":false}," +
                    "{\"name\":\"Server\",\"value\":\"%s\",\"inline\":true}," +
                    "{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}" +
                    "]}]}",
                    escape(ping), isCrit ? "Spawner Base" : "Stash", sc.x, sc.z,
                    isCrit ? 16711680 : 3066993,
                    escape(reason), sc.getTotal(), escape(items.toString()),
                    escape(server), System.currentTimeMillis() / 1000);

                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(30)).build();
                httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {}
        });
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");
    }

    public static class StashChunk {
        public final ChunkPos pos;
        public final int x, z;
        public int chests, barrels, shulkers, enderChests, furnaces, hoppers, dispensers, spawners;

        public StashChunk(ChunkPos pos) {
            this.pos = pos;
            this.x = pos.x * 16 + 8;
            this.z = pos.z * 16 + 8;
        }

        public int getTotal() {
            return chests + barrels + shulkers + enderChests + furnaces + hoppers + dispensers + spawners;
        }

        public boolean countsEqual(StashChunk o) {
            return o != null && chests == o.chests && barrels == o.barrels && shulkers == o.shulkers
                && enderChests == o.enderChests && furnaces == o.furnaces && hoppers == o.hoppers
                && dispensers == o.dispensers && spawners == o.spawners;
        }
    }
}
