package com.dclient.module.modules.donut;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player Detect — ported from Meteor Client Notifier.
 * Notifies about: totem pops, visual range enter/leave, pearl landings, join/leave.
 */
public class PlayerDetect extends Module {
    // Totem pops
    public final Setting<Boolean> totemPops        = addSetting("Totem Pops", true);
    public final Setting<Boolean> ignoreOwnTotem   = addSetting("Ignore Self", false);
    public final Setting<Integer> totemDistance    = addSetting("Distance", 50);

    // Visual range
    public final Setting<Boolean> visualRange      = addSetting("Visual Range", true);
    public final Setting<Boolean> visualSound      = addSetting("Sound", true);

    // Pearl tracking
    public final Setting<Boolean> pearlTrack       = addSetting("Pearl Track", true);

    // Join/Leave
    public final Setting<Boolean> joinLeave        = addSetting("Join/Leave", true);

    // State
    private final Map<UUID, Integer> totemPops2    = new ConcurrentHashMap<>();
    private final Map<Integer, Vec3> pearlStart    = new ConcurrentHashMap<>();
    private final Set<UUID> knownPlayers           = ConcurrentHashMap.newKeySet();
    private final Set<Integer> trackedPearls       = ConcurrentHashMap.newKeySet();
    // Reusable sets — allocated once, cleared each tick instead of new HashSet<>() every tick
    private final Set<UUID> currentPlayers         = new java.util.HashSet<>();
    private final Set<UUID> knownSnapshot          = new java.util.HashSet<>();

    public PlayerDetect() { super("Player Detect", Category.DONUT); }

    @Override
    protected void onEnable() {
        totemPops2.clear();
        pearlStart.clear();
        knownPlayers.clear();
        // Populate known players
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.players().forEach(p -> knownPlayers.add(p.getUUID()));
        }
    }

    @Override
    protected void onDisable() {
        totemPops2.clear();
        pearlStart.clear();
        knownPlayers.clear();
    }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Visual range — check for new/gone players
        if (visualRange.getValue()) {
            currentPlayers.clear(); // reuse — no allocation
            mc.level.players().forEach(p -> {
                if (p == mc.player) return;
                currentPlayers.add(p.getUUID());
                if (!knownPlayers.contains(p.getUUID())) {
                    notify("[PlayerDetect] " + p.getName().getString() + " entered visual range at "
                        + p.blockPosition().getX() + ", " + p.blockPosition().getY() + ", " + p.blockPosition().getZ());
                    if (visualSound.getValue()) playSound(mc);
                }
            });
            // Check for players who left — snapshot knownPlayers to avoid ConcurrentModification
            knownSnapshot.clear();
            knownSnapshot.addAll(knownPlayers);
            for (UUID id : knownSnapshot) {
                if (!currentPlayers.contains(id)) {
                    String name = getPlayerName(mc, id);
                    notify("[PlayerDetect] " + name + " left visual range");
                    if (visualSound.getValue()) playSound(mc);
                }
            }
            knownPlayers.clear();
            knownPlayers.addAll(currentPlayers);
        }

        // Pearl tracking
        if (pearlTrack.getValue()) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof ThrownEnderpearl)) continue;
                if (!trackedPearls.contains(e.getId())) {
                    trackedPearls.add(e.getId());
                    pearlStart.put(e.getId(), e.position());
                }
            }
            // Check for pearls that landed (no longer in world)
            trackedPearls.removeIf(id -> {
                Entity e = mc.level.getEntity(id);
                if (e == null && pearlStart.containsKey(id)) {
                    pearlStart.remove(id);
                    return true;
                }
                return false;
            });
        }

        // Totem pop detection — check player health
        if (totemPops.getValue()) {
            for (Player p : mc.level.players()) {
                if (p == mc.player && ignoreOwnTotem.getValue()) continue;
                if (p.deathTime > 0 || p.getHealth() <= 0) {
                    Integer pops = totemPops2.remove(p.getUUID());
                    if (pops != null && pops > 0) {
                        notify("[PlayerDetect] " + p.getName().getString() + " died after " + pops + " totem pop" + (pops > 1 ? "s" : ""));
                    }
                }
            }
        }

        // Join/Leave via tab list
        if (joinLeave.getValue()) {
            ClientPacketListener conn = mc.getConnection();
            if (conn != null) {
                Set<UUID> tabList = new HashSet<>();
                for (PlayerInfo info : conn.getListedOnlinePlayers()) {
                    tabList.add(info.getProfile().id());
                }
                // New joins
                for (PlayerInfo info : conn.getListedOnlinePlayers()) {
                    UUID id = info.getProfile().id();
                    if (mc.player != null && id.equals(mc.player.getUUID())) continue;
                }
            }
        }
    }

    /** Called from mixin when a totem is used */
    public void onTotemPop(Player player) {
        if (!isEnabled() || !totemPops.getValue()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (player == mc.player && ignoreOwnTotem.getValue()) return;
        if (mc.player.distanceTo(player) > totemDistance.getValue()) return;

        int pops = totemPops2.merge(player.getUUID(), 1, Integer::sum);
        notify("[PlayerDetect] " + player.getName().getString() + " popped a totem! (" + pops + " total)");
        playSound(mc);
    }

    private void notify(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null)
                mc.player.displayClientMessage(Component.literal(msg), false);
        });
    }

    private void playSound(Minecraft mc) {
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.5f, false);
        }
    }

    private String getPlayerName(Minecraft mc, UUID id) {
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) return info.getProfile().name();
        }
        return id.toString().substring(0, 8);
    }
}
