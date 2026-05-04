package com.dclient.friends;

import com.google.gson.*;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Manages the friends list — persisted to config/dclient_friends.json.
 * Lookup is by lowercase username for case-insensitive matching.
 */
public class FriendManager {

    private static final Set<String> friends = new LinkedHashSet<>();
    private static final Path FILE = Paths.get("config", "dclient_friends.json");

    // ── Public API ────────────────────────────────────────────────────────────

    public static boolean isFriend(String username) {
        return friends.contains(username.toLowerCase());
    }

    public static boolean isFriend(net.minecraft.world.entity.player.Player player) {
        return isFriend(player.getName().getString());
    }

    public static void add(String username) {
        friends.add(username.toLowerCase());
        save();
    }

    public static void remove(String username) {
        friends.remove(username.toLowerCase());
        save();
    }

    public static boolean toggle(String username) {
        if (isFriend(username)) { remove(username); return false; }
        else { add(username); return true; }
    }

    /** Returns a copy of the friends list with original casing preserved from file. */
    public static List<String> getAll() {
        return new ArrayList<>(friends);
    }

    public static int size() { return friends.size(); }

    // ── Persistence ───────────────────────────────────────────────────────────

    public static void load() {
        friends.clear();
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE)) {
            JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
            for (JsonElement el : arr) friends.add(el.getAsString().toLowerCase());
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonArray arr = new JsonArray();
            for (String f : friends) arr.add(f);
            try (Writer w = Files.newBufferedWriter(FILE)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(arr, w);
            }
        } catch (Exception ignored) {}
    }
}
