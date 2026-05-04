package com.dclient.module.modules.misc;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SpotifyHUD extends Module {
    public final Setting<Integer> posX = addSetting("Pos X", 10);
    public final Setting<Integer> posY = addSetting("Pos Y", 10);

    private volatile String trackName  = null;
    private volatile String artistName = null;
    private volatile boolean isPlaying = false;
    private volatile long trackStartMs = 0;
    private String lastRawTitle = "";
    private String lastArtworkQuery = "";
    private ScheduledExecutorService scheduler;

    // Album art texture
    private Identifier artTexture = null;
    private DynamicTexture artDynTex = null;
    private volatile NativeImage pendingNative = null;

    // Dominant color for accent
    private volatile int artColor1 = 0xFF2A2A2A;
    private volatile int artColor2 = 0xFF1A1A1A;

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8)).build();

    private static final int C_BG       = 0xCC1A1A1A;
    private static final int C_TITLE    = 0xFFFFFFFF;
    private static final int C_ARTIST   = 0xFFAAAAAA;
    private static final int C_BAR_BG   = 0xFF444444;
    private static final int C_BAR_FG   = 0xFF1DB954;
    private static final int C_TIME     = 0xFF1DB954;
    private static final int C_TIME_END = 0xFF888888;

    public SpotifyHUD() { super("Spotify HUD", Category.MISC); }

    @Override
    protected void onEnable() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SpotifyHUD-Watcher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::pollSpotifyTitle, 0, 2, TimeUnit.SECONDS);
    }

    @Override
    protected void onDisable() {
        if (scheduler != null) { scheduler.shutdownNow(); scheduler = null; }
        trackName = null; artistName = null; isPlaying = false;
        artColor1 = 0xFF2A2A2A; artColor2 = 0xFF1A1A1A;
        freeTexture();
    }

    private void freeTexture() {
        if (artDynTex != null) { try { artDynTex.close(); } catch (Exception ignored) {} artDynTex = null; }
        if (artTexture != null) {
            try { Minecraft.getInstance().getTextureManager().release(artTexture); } catch (Exception ignored) {}
            artTexture = null;
        }
        if (pendingNative != null) { try { pendingNative.close(); } catch (Exception ignored) {} pendingNative = null; }
    }

    private void pollSpotifyTitle() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "Get-Process -Name Spotify -ErrorAction SilentlyContinue | " +
                "Where-Object {$_.MainWindowTitle -ne ''} | " +
                "Select-Object -ExpandProperty MainWindowTitle -First 1"
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String title = new BufferedReader(new InputStreamReader(proc.getInputStream())).readLine();
            proc.waitFor(3, TimeUnit.SECONDS);
            proc.destroy();

            if (title == null || title.isBlank() || title.trim().equals("Spotify")) {
                isPlaying = false;
                return;
            }
            title = title.trim();
            if (title.contains(" - ")) {
                int sep = title.indexOf(" - ");
                String newArtist = title.substring(0, sep).trim();
                String newTrack  = title.substring(sep + 3).trim();
                if (!title.equals(lastRawTitle)) {
                    lastRawTitle = title;
                    trackStartMs = System.currentTimeMillis();
                    fetchAlbumArt(newArtist, newTrack);
                }
                artistName = newArtist;
                trackName  = newTrack;
                isPlaying  = true;
            } else {
                isPlaying = false;
            }
        } catch (Exception ignored) {}
    }

    private void fetchAlbumArt(String artist, String track) {
        try {
            String artworkQuery = artist + "|" + track;
            if (artworkQuery.equals(lastArtworkQuery)) return;
            lastArtworkQuery = artworkQuery;

            String query = URLEncoder.encode(artist + " " + track, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://itunes.apple.com/search?term=" + query + "&media=music&limit=1"))
                .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return;

            var json = JsonParser.parseString(resp.body()).getAsJsonObject();
            var results = json.getAsJsonArray("results");
            if (results == null || results.isEmpty()) return;

            String artUrl = results.get(0).getAsJsonObject().get("artworkUrl100").getAsString();

            HttpRequest imgReq = HttpRequest.newBuilder()
                .uri(URI.create(artUrl)).timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<InputStream> imgResp = http.send(imgReq, HttpResponse.BodyHandlers.ofInputStream());
            if (imgResp.statusCode() != 200) return;

            BufferedImage img = ImageIO.read(imgResp.body());
            if (img == null) return;

            // Scale to 48x48
            BufferedImage scaled = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(img, 0, 0, 48, 48, null);
            g2.dispose();

            // Build NativeImage — NativeImage RGBA stores bytes as R,G,B,A in memory
            // setPixel takes ABGR packed int (alpha in high byte, red in low)
            NativeImage ni = new NativeImage(NativeImage.Format.RGBA, 48, 48, false);
            for (int py = 0; py < 48; py++) {
                for (int px = 0; px < 48; px++) {
                    int argb = scaled.getRGB(px, py);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >>  8) & 0xFF;
                    int b =  argb        & 0xFF;
                    ni.setPixel(px, py, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
            pendingNative = ni;

            // Extract dominant color for accent
            long rSum = 0, gSum = 0, bSum = 0; int count = 0;
            for (int py = 0; py < 48; py += 2) {
                for (int px = 0; px < 48; px += 2) {
                    int argb = scaled.getRGB(px, py);
                    int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bv = argb & 0xFF;
                    int br = (r + g + bv) / 3;
                    if (br > 30 && br < 220) { rSum += r; gSum += g; bSum += bv; count++; }
                }
            }
            if (count > 0) {
                int r = (int)(rSum/count), g = (int)(gSum/count), b = (int)(bSum/count);
                artColor1 = 0xFF000000 | (r << 16) | (g << 8) | b;
                artColor2 = 0xFF000000 | ((r/3) << 16) | ((g/3) << 8) | (b/3);
            }
        } catch (Exception ignored) {}
    }

    public void render(GuiGraphics gfx) {
        if (!isEnabled() || !isPlaying || trackName == null) return;
        Minecraft mc = Minecraft.getInstance();

        // Upload pending NativeImage on render thread
        if (pendingNative != null) {
            NativeImage ni = pendingNative;
            pendingNative = null;
            try {
                freeTexture();
                artTexture = Identifier.fromNamespaceAndPath("dclient", "spotify_art");
                artDynTex = new DynamicTexture(() -> "spotify_art", ni);
                mc.getTextureManager().register(artTexture, artDynTex);
            } catch (Exception e) { freeTexture(); }
        }

        int ox = posX.getValue(), oy = posY.getValue();
        int albumSize = 48, pad = 8, textW = 160;
        int totalW = albumSize + pad * 3 + textW;
        int totalH = albumSize + pad * 2;
        int barH = 3;
        int textAreaX = ox + albumSize + pad * 2;

        gfx.fill(ox, oy, ox + totalW, oy + totalH, C_BG);

        // Album art
        int artX = ox + pad, artY = oy + pad;
        if (artTexture != null && artDynTex != null) {
            // Use GUI_TEXTURED pipeline — correct for rendering arbitrary textures in GUI
            gfx.blit(RenderPipelines.GUI_TEXTURED, artTexture,
                artX, artY, 0f, 0f, albumSize, albumSize, albumSize, albumSize);
        } else {
            gfx.fill(artX, artY, artX + albumSize, artY + albumSize, artColor2);
            gfx.fill(artX + 1, artY + 1, artX + albumSize - 1, artY + albumSize - 1, artColor1);
            gfx.drawCenteredString(mc.font, "\u266B",
                artX + albumSize / 2, artY + albumSize / 2 - 4, 0xDDFFFFFF);
        }

        // Accent line using album color
        gfx.fill(ox, oy, ox + 2, oy + totalH, artColor1 | 0xFF000000);

        // Track name
        String displayTrack = trackName.length() > 22 ? trackName.substring(0, 20) + "..." : trackName;
        gfx.drawString(mc.font, displayTrack, textAreaX, oy + pad + 4, C_TITLE, true);

        // Artist
        String displayArtist = "by " + artistName;
        if (displayArtist.length() > 24) displayArtist = displayArtist.substring(0, 22) + "...";
        gfx.drawString(mc.font, displayArtist, textAreaX, oy + pad + 16, C_ARTIST, false);

        // Progress bar
        int barY = oy + pad + albumSize - barH - 14;
        int barX = textAreaX;
        int barW = totalW - albumSize - pad * 3;
        long elapsed = System.currentTimeMillis() - trackStartMs;
        float pct = Math.min(1f, (float)(elapsed % 240_000) / 240_000f);
        int filled = (int)(barW * pct);
        gfx.fill(barX, barY, barX + barW, barY + barH, C_BAR_BG);
        if (filled > 0) gfx.fill(barX, barY, barX + filled, barY + barH, C_BAR_FG);

        int timeY = barY + barH + 3;
        gfx.drawString(mc.font, formatMs(elapsed), barX, timeY, C_TIME, false);
        gfx.drawString(mc.font, "--:--", barX + barW - mc.font.width("--:--"), timeY, C_TIME_END, false);
    }

    private String formatMs(long ms) {
        long secs = ms / 1000;
        return (secs / 60) + ":" + String.format("%02d", secs % 60);
    }
}