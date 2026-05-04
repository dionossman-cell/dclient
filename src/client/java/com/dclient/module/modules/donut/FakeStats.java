package com.dclient.module.modules.donut;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake Scoreboard — displays a custom sidebar scoreboard with fake stats.
 */
public class FakeStats extends Module {
    public final Setting<String> title    = addSetting("Title", "dclient");
    public final Setting<String> money    = addSetting("Money", "67");
    public final Setting<String> shards   = addSetting("Shards", "67");
    public final Setting<String> kills    = addSetting("Kills", "67");
    public final Setting<String> deaths   = addSetting("Deaths", "67");
    public final Setting<String> playtime = addSetting("Playtime", "6h 7m");
    public final Setting<String> team     = addSetting("Team", "dclient");
    public final Setting<String> footer   = addSetting("Footer", "play.server.net");

    private static final String OBJ_NAME = "dclient_fake";
    private Objective customObjective;
    private Objective originalObjective;
    private final List<String> teamNames = new ArrayList<>();

    private long keyallStart = 0;
    private long lastMsUpdate = 0;
    private int displayMs = 50;
    private int msDir = 1;
    private long lastUpdate = 0;

    public FakeStats() { super("Fake Stats", Category.DONUT); }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Scoreboard sb = mc.level.getScoreboard();
        originalObjective = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        keyallStart = System.currentTimeMillis();
        lastMsUpdate = System.currentTimeMillis();
        displayMs = 50 + (int)(Math.random() * 50);
        msDir = Math.random() < 0.5 ? 1 : -1;
        updateScoreboard();
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Scoreboard sb = mc.level.getScoreboard();
        cleanupTeams(sb);
        if (customObjective != null) {
            try { sb.removeObjective(customObjective); } catch (Exception ignored) {}
            customObjective = null;
        }
        try {
            sb.setDisplayObjective(DisplaySlot.SIDEBAR, originalObjective);
        } catch (Exception ignored) {
            sb.setDisplayObjective(DisplaySlot.SIDEBAR, null);
        }
        originalObjective = null;
    }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastUpdate >= 1000) {
            updateScoreboard();
            lastUpdate = now;
        }
    }

    private void cleanupTeams(Scoreboard sb) {
        for (String name : teamNames) {
            PlayerTeam t = sb.getPlayerTeam(name);
            if (t != null) try { sb.removePlayerTeam(t); } catch (Exception ignored) {}
        }
        teamNames.clear();
    }

    private void updateScoreboard() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Scoreboard sb = mc.level.getScoreboard();
        cleanupTeams(sb);

        // Always remove by name first — handles orphaned objectives after world reload
        if (customObjective != null) {
            try { sb.removeObjective(customObjective); } catch (Exception ignored) {}
            customObjective = null;
        }
        // Also remove any leftover objective with our name
        try {
            Objective existing = sb.getObjective(OBJ_NAME);
            if (existing != null) sb.removeObjective(existing);
        } catch (Exception ignored) {}

        String titleStr = title.getValue();
        if (titleStr == null || titleStr.isEmpty()) titleStr = " ";

        try {
            customObjective = sb.addObjective(OBJ_NAME, ObjectiveCriteria.DUMMY,
                gradientTitle(titleStr), ObjectiveCriteria.RenderType.INTEGER, false, null);
        } catch (Exception e) {
            // Objective name collision — try a unique fallback name
            try {
                String fallback = OBJ_NAME + "_" + (System.currentTimeMillis() % 1000);
                customObjective = sb.addObjective(fallback, ObjectiveCriteria.DUMMY,
                    gradientTitle(titleStr), ObjectiveCriteria.RenderType.INTEGER, false, null);
            } catch (Exception ignored) { return; }
        }
        sb.setDisplayObjective(DisplaySlot.SIDEBAR, customObjective);

        List<MutableComponent> entries = buildEntries();
        for (int i = 0; i < entries.size(); i++) {
            String tName = "dc_t_" + i;
            teamNames.add(tName);
            try {
                PlayerTeam existing2 = sb.getPlayerTeam(tName);
                if (existing2 != null) sb.removePlayerTeam(existing2);
                PlayerTeam t = sb.addPlayerTeam(tName);
                t.setPlayerPrefix(entries.get(i));
                String holder = "\u00A7" + Integer.toHexString(i);
                ScoreHolder scoreHolder = ScoreHolder.forNameOnly(holder);
                sb.resetSinglePlayerScore(scoreHolder, customObjective);
                ScoreAccess score = sb.getOrCreatePlayerScore(scoreHolder, customObjective);
                score.set(entries.size() - i);
                sb.addPlayerToTeam(holder, t);
            } catch (Exception ignored) {}
        }
    }

    private String val(Setting<String> s) {
        String v = s.getValue();
        return (v == null || v.isEmpty()) ? " " : v;
    }

    private List<MutableComponent> buildEntries() {
        return List.of(
            text(" "),
            colored("$ ", 0x00FF00).append(colored("Money: ", 0xFFFFFF)).append(colored(val(money), 0x00FF00)),
            colored("\u2605 ", 0xA503FC).append(colored("Shards: ", 0xFFFFFF)).append(colored(val(shards), 0xA503FC)),
            colored("\u2694 ", 0xFF0000).append(colored("Kills: ", 0xFFFFFF)).append(colored(val(kills), 0xFF0000)),
            colored("\u2620 ", 0xFC7703).append(colored("Deaths: ", 0xFFFFFF)).append(colored(val(deaths), 0xFC7703)),
            colored("\u23F1 ", 0xFFE600).append(colored("Playtime: ", 0xFFFFFF)).append(colored(val(playtime), 0xFFE600)),
            colored("\u26CF ", 0x00A2FF).append(colored("Team: ", 0xFFFFFF)).append(colored(val(team), 0x00A2FF)),
            text(" "),
            buildFooter()
        );
    }

    private MutableComponent buildFooter() {
        long now = System.currentTimeMillis();
        if (now - lastMsUpdate > 2000 + (long)(Math.random() * 2000)) {
            int change = 1 + (int)(Math.random() * 5);
            displayMs += msDir * change;
            if (displayMs < 20) { displayMs = 20; msDir = 1; }
            else if (displayMs > 150) { displayMs = 150; msDir = -1; }
            if (Math.random() < 0.1) msDir *= -1;
            lastMsUpdate = now;
        }
        // Just show the server name + animated ping — no arrows
        return colored(val(footer), 0xA0A0A0)
            .append(colored(" (", 0xA0A0A0))
            .append(colored(displayMs + "ms", 0x00A2FF))
            .append(colored(")", 0xA0A0A0));
    }

    private MutableComponent gradientTitle(String text) {
        if (text == null || text.isEmpty()) text = " ";
        int startColor = 0x007CF9, endColor = 0x00C6F9;
        int sr = (startColor>>16)&0xFF, sg = (startColor>>8)&0xFF, sb2 = startColor&0xFF;
        int er = (endColor>>16)&0xFF, eg = (endColor>>8)&0xFF, eb = endColor&0xFF;
        MutableComponent result = Component.empty();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            float t = len <= 1 ? 0f : (float)i / (len - 1);
            int r = Math.round(sr + (er-sr)*t);
            int g = Math.round(sg + (eg-sg)*t);
            int b = Math.round(sb2 + (eb-sb2)*t);
            result.append(Component.literal(String.valueOf(text.charAt(i)))
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb((r<<16)|(g<<8)|b))));
        }
        return result;
    }

    private MutableComponent colored(String s, int rgb) {
        return Component.literal(s).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    private MutableComponent text(String s) { return Component.literal(s); }
}
