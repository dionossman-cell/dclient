package com.dclient.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AuthManager {

    // ── Change this to your deployed server URL ──────────────────────────────
    public static final String SERVER_URL = "https://dclient-server.onrender.com";
    // ─────────────────────────────────────────────────────────────────────────

    private static final Path KEY_FILE = java.nio.file.Paths.get(
        System.getProperty("user.home"), ".dclient", "license.key"
    );

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .version(HttpClient.Version.HTTP_1_1)
        .build();

    // Cache successful validation — never re-hit the server mid-session
    private static volatile Result cachedResult = null;
    private static volatile long   lastValidateMs = 0;
    // Re-validate at most once every 10 minutes even if called again
    private static final long REVALIDATE_INTERVAL_MS = 10 * 60 * 1000L;

    public enum Result { VALID, INVALID, BLOCKED, EXPIRED, HWID_MISMATCH, NO_CONNECTION }

    /** Reads the saved key from disk, or null if none. */
    public static String loadSavedKey() {
        try {
            if (Files.exists(KEY_FILE))
                return Files.readString(KEY_FILE).trim();
        } catch (IOException ignored) {}
        return null;
    }

    /** Saves the key to disk. */
    public static void saveKey(String key) {
        try {
            java.nio.file.Files.createDirectories(KEY_FILE.getParent());
            Files.writeString(KEY_FILE, key.trim());
        } catch (IOException ignored) {}
    }

    /** Deletes the saved key (so auth screen shows again next launch). */
    public static void clearKey() {
        try { Files.deleteIfExists(KEY_FILE); } catch (IOException ignored) {}
    }

    /** Validates key + HWID against the server. Blocking call — run off main thread. */
    public static Result validate(String key) {
        // Return cached result if still fresh — avoids hammering the server
        long now = System.currentTimeMillis();
        if (cachedResult == Result.VALID && now - lastValidateMs < REVALIDATE_INTERVAL_MS) {
            return Result.VALID;
        }

        // Only retry once on cold-start (Render free tier wakes slowly)
        // Use longer timeout on first attempt to survive the wake-up delay
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String hwid = HwidUtil.get();
                JsonObject body = new JsonObject();
                body.addProperty("key",  key.trim().toUpperCase());
                body.addProperty("hwid", hwid);

                int timeoutSec = attempt == 0 ? 25 : 12;
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SERVER_URL + "/auth"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .build();

                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

                // Rate limited — back off and treat as no connection
                if (resp.statusCode() == 429) {
                    return Result.NO_CONNECTION;
                }

                JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();

                if (json.get("valid").getAsBoolean()) {
                    cachedResult = Result.VALID;
                    lastValidateMs = System.currentTimeMillis();
                    return Result.VALID;
                }

                String reason = json.has("reason") ? json.get("reason").getAsString() : "";
                if (reason.contains("blocked"))         return Result.BLOCKED;
                if (reason.contains("expired"))         return Result.EXPIRED;
                if (reason.contains("another machine")) return Result.HWID_MISMATCH;
                return Result.INVALID;

            } catch (Exception e) {
                if (attempt == 0) {
                    // Wait 4s before retry — gives Render time to wake up
                    try { Thread.sleep(4000); } catch (InterruptedException ignored) {}
                }
            }
        }
        return Result.NO_CONNECTION;
    }

    /** Async version — calls callback on the result. */
    public static CompletableFuture<Result> validateAsync(String key) {
        return CompletableFuture.supplyAsync(() -> validate(key));
    }
}
