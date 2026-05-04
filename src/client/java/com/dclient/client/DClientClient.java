package com.dclient.client;

import com.dclient.client.gui.ClickGui;
import com.dclient.client.gui.ClickGuiNew;
import com.dclient.auth.AuthManager;
import com.dclient.auth.AuthScreen;
import com.dclient.config.ConfigManager;
import com.dclient.module.ModuleManager;
import com.dclient.module.modules.combat.*;
import com.dclient.module.modules.misc.*;
import com.dclient.module.modules.donut.*;
import com.dclient.module.modules.render.*;
import com.dclient.module.modules.visuals.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class DClientClient implements ClientModInitializer {
    public static KeyMapping openGuiKey;
    private static final java.util.Set<Integer> pressedKeys = new java.util.HashSet<>();
    private static int guiKeyPressedTicks = 0;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        com.dclient.friends.FriendManager.load();
        initModuleRefs();

        KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("dclient", "dclient")
        );

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.dclient.opengui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            CATEGORY
        ));

        ConfigManager.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.save());

        // Auth check — show on first tick after main menu loads
        final boolean[] authShown = {false};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (authShown[0]) return;
            if (client.screen == null && client.level == null) return; // not on main menu yet
            if (!(client.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
            authShown[0] = true;
            String savedKey = AuthManager.loadSavedKey();
            if (savedKey != null) {
                AuthManager.validateAsync(savedKey).thenAccept(result -> {
                    if (result == AuthManager.Result.VALID || result == AuthManager.Result.NO_CONNECTION) {
                        // Valid or offline — keep the key, let them in
                        return;
                    }
                    if (result == AuthManager.Result.HWID_MISMATCH) {
                        // HWID mismatch — show auth screen but DON'T delete the key
                        // The HWID may have changed due to VPN/network adapter changes
                        client.execute(() -> client.setScreen(new AuthScreen(null)));
                    } else {
                        // Truly invalid/blocked/expired — clear the key
                        AuthManager.clearKey();
                        client.execute(() -> client.setScreen(new AuthScreen(null)));
                    }
                });
            } else {
                client.setScreen(new AuthScreen(null));
            }
        });

        // Chunk load event for ChunkFinder
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            getModule(ChunkFinder.class).onChunkLoad(chunk);
            getModule(AmethystDebug.class).onChunkLoad(chunk);
            getModule(SpawnerNotifier.class).onChunkLoad(chunk);
            getModule(BaseESP.class).onChunkLoad(chunk);
            // WallHack — rebuild only this chunk's sections
            WallHack wh = getModule(WallHack.class);
            if (wh.isEnabled()) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.levelRenderer != null) {
                    net.minecraft.world.level.ChunkPos cp = chunk.getPos();
                    // Mark just this chunk's sections dirty instead of allChanged()
                    for (int sy = mc.level.getMinSectionY(); sy <= mc.level.getMaxSectionY(); sy++) {
                        mc.levelRenderer.setSectionDirty(cp.x, sy, cp.z);
                    }
                }
            }
        });


        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Check GUI key — use cached _fourEPlus reference
            int guiKeyCode = _fourEPlus.guiKey.getValue();
            boolean guiKeyDown = org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().handle(), guiKeyCode) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (guiKeyDown && !pressedKeys.contains(guiKeyCode) && client.screen == null) {
                client.setScreen(_fourEPlus.guiStyle.getValue().equals("Classic") ? new ClickGui() : new ClickGuiNew());
            }
            if (guiKeyDown) pressedKeys.add(guiKeyCode); else pressedKeys.remove(guiKeyCode);

            // Check module binds
            for (com.dclient.module.Module mod : ModuleManager.getAll()) {
                int bind = mod.getBind();
                if (bind == -1) continue;
                boolean down = org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().handle(), bind) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                if (down && !pressedKeys.contains(bind)) mod.toggle();
                if (down) pressedKeys.add(bind); else pressedKeys.remove(bind);
            }

            if (client.player == null) return;

            // Combat modules — always tick (time-critical)
            if (_autoTotem.isEnabled())     _autoTotem.tick(client.player);
            if (_hoverTotem.isEnabled())    _hoverTotem.tick();
            if (_autoInvTotem.isEnabled())  _autoInvTotem.tick();
            if (_autoCrystal.isEnabled())   _autoCrystal.tick();
            if (_triggerBot.isEnabled())    _triggerBot.tick();
            if (_aimAssist.isEnabled())     _aimAssist.tick();
            if (_antiTrap.isEnabled())      _antiTrap.tick();
            if (_maceSwap.isEnabled())      _maceSwap.tick();
            if (_anchorMacro.isEnabled())   _anchorMacro.tick();
            if (_doubleAnchor.isEnabled())  _doubleAnchor.tick();

            // Misc modules — always tick
            if (_fastPlace.isEnabled())     _fastPlace.tick();
            if (_freecam.isEnabled())       _freecam.tick();
            if (_autoTool.isEnabled())      _autoTool.tick();
            if (_shulkerDropper.isEnabled()) _shulkerDropper.tick();
            if (_spawnerProtect.isEnabled())  _spawnerProtect.tick();
            if (_ahSniper.isEnabled())         _ahSniper.tick();
            if (_fakeStats.isEnabled())     _fakeStats.tick();
            if (_fullBright.isEnabled())    _fullBright.tick();
            _hud.tick();

            // Render ESP — always tick (have internal throttles)
            if (_playerESP.isEnabled())     _playerESP.tick();
            if (_mobESP.isEnabled())        _mobESP.tick();
            if (_storageESP.isEnabled())    _storageESP.tick();
            if (_blockESP.isEnabled())      _blockESP.tick();
            if (_holeESP.isEnabled())       _holeESP.tick();
            if (_freeLook.isEnabled())      _freeLook.tick();
            if (_wallHack.isEnabled())      _wallHack.tick();
            if (_tridentESP.isEnabled())    _tridentESP.tick();
            if (_spawnerNotifier.isEnabled()) _spawnerNotifier.tick();

            // Heavy scanning modules — have their own adaptive throttle based on FPS
            if (_chunkFinder.isEnabled())   _chunkFinder.tick();
            if (_susChunkFinder.isEnabled()) _susChunkFinder.tick();
            if (_growthFinder.isEnabled())  _growthFinder.tick();
            if (_kelpChunkESP.isEnabled())  _kelpChunkESP.tick();
            if (_playerDetect.isEnabled())  _playerDetect.tick();
            if (_amethystDebug.isEnabled()) _amethystDebug.tick();
            if (_susESP.isEnabled())        _susESP.tick();
            if (_lightDebug.isEnabled())    _lightDebug.tick();
        });

        HudRenderCallback.EVENT.register((gfx, tickDelta) -> {
            if (_hud.isEnabled())          _hud.render(gfx);
            if (_targetHUD.isEnabled())    _targetHUD.render(gfx);
            if (_targetBlockHUD.isEnabled()) _targetBlockHUD.render(gfx);
            if (_regionMap.isEnabled())    _regionMap.render(gfx);
            if (_nametags.isEnabled())     _nametags.render(gfx);
            if (_spotifyHUD.isEnabled())   _spotifyHUD.render(gfx);
        });

        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            var pose = context.matrices();
            if (pose == null) return;
            var buffers = context.consumers();
            if (buffers == null) return;
            // All ESP renders every frame — geometry is already cached in lists from tick()
            if (_playerESP.isEnabled())      _playerESP.render(pose, buffers);
            if (_mobESP.isEnabled())         _mobESP.render(pose, buffers);
            if (_storageESP.isEnabled())     _storageESP.render(pose, buffers);
            if (_blockESP.isEnabled())       _blockESP.render(pose, buffers);
            if (_holeESP.isEnabled())        _holeESP.render(pose, buffers);
            if (_spawnerNotifier.isEnabled()) _spawnerNotifier.render(pose, buffers);
            if (_susESP.isEnabled())         _susESP.render(pose, buffers);
            if (_tridentESP.isEnabled())     _tridentESP.render(pose, buffers);
            if (_chunkFinder.isEnabled())    _chunkFinder.render(pose, buffers);
            if (_susChunkFinder.isEnabled()) _susChunkFinder.render(pose, buffers);
            if (_growthFinder.isEnabled())   _growthFinder.render(pose, buffers);
            if (_lightDebug.isEnabled())     _lightDebug.render(pose, buffers);
            if (_amethystDebug.isEnabled())  _amethystDebug.render(pose, buffers);
        });
    }

    private static final java.util.Map<Class<?>, com.dclient.module.Module> moduleCache = new java.util.HashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T getModule(Class<T> clazz) {
        return (T) moduleCache.computeIfAbsent(clazz, c ->
            ModuleManager.getAll().stream()
                .filter(m -> m.getClass() == c)
                .findFirst().orElseThrow());
    }

    // Pre-cached module references for hot tick path
    private static AutoTotem _autoTotem;
    private static HoverTotem _hoverTotem;
    private static AutoInvTotem _autoInvTotem;
    private static AutoCrystal _autoCrystal;
    private static TriggerBot _triggerBot;
    private static AimAssist _aimAssist;
    private static AntiTrap _antiTrap;
    private static MaceSwap _maceSwap;
    private static AnchorMacro _anchorMacro;
    private static DoubleAnchor _doubleAnchor;
    private static FastPlace _fastPlace;
    private static Freecam _freecam;
    private static AutoTool _autoTool;
    private static com.dclient.module.modules.client.FourEPlus _fourEPlus;
    private static ChunkFinder _chunkFinder;
    private static GrowthFinder _growthFinder;
    private static KelpChunkESP _kelpChunkESP;
    private static PlayerDetect _playerDetect;
    private static AmethystDebug _amethystDebug;
    private static SusESP _susESP;
    private static LightDebug _lightDebug;
    private static FakeStats _fakeStats;
    private static ShulkerDropper _shulkerDropper;
    private static SpawnerProtect _spawnerProtect;
    private static AhSniper _ahSniper;
    private static SusChunkFinder _susChunkFinder;
    private static SpawnerNotifier _spawnerNotifier;
    private static FullBright _fullBright;
    private static PlayerESP _playerESP;
    private static MobESP _mobESP;
    private static StorageESP _storageESP;
    private static BlockESP _blockESP;
    private static HoleESP _holeESP;
    private static FreeLook _freeLook;
    private static WallHack _wallHack;
    private static TridentESP _tridentESP;
    private static HUD _hud;
    private static TargetHUD _targetHUD;
    private static TargetBlockHUD _targetBlockHUD;
    private static RegionMap _regionMap;
    private static Nametags _nametags;
    private static SpotifyHUD _spotifyHUD;

    private static void initModuleRefs() {
        _autoTotem     = getModule(AutoTotem.class);
        _hoverTotem    = getModule(HoverTotem.class);
        _autoInvTotem  = getModule(AutoInvTotem.class);
        _autoCrystal   = getModule(AutoCrystal.class);
        _triggerBot    = getModule(TriggerBot.class);
        _aimAssist     = getModule(AimAssist.class);
        _antiTrap      = getModule(AntiTrap.class);
        _maceSwap      = getModule(MaceSwap.class);
        _anchorMacro   = getModule(AnchorMacro.class);
        _doubleAnchor  = getModule(DoubleAnchor.class);
        _fastPlace     = getModule(FastPlace.class);
        _freecam       = getModule(Freecam.class);
        _autoTool      = getModule(AutoTool.class);
        _fourEPlus     = getModule(com.dclient.module.modules.client.FourEPlus.class);
        _chunkFinder   = getModule(ChunkFinder.class);
        _growthFinder  = getModule(GrowthFinder.class);
        _kelpChunkESP  = getModule(KelpChunkESP.class);
        _playerDetect  = getModule(PlayerDetect.class);
        _amethystDebug = getModule(AmethystDebug.class);
        _susESP        = getModule(SusESP.class);
        _lightDebug    = getModule(LightDebug.class);
        _fakeStats     = getModule(FakeStats.class);
        _shulkerDropper = getModule(ShulkerDropper.class);
        _spawnerProtect = getModule(SpawnerProtect.class);
        _ahSniper       = getModule(AhSniper.class);
        _susChunkFinder = getModule(SusChunkFinder.class);
        _spawnerNotifier = getModule(SpawnerNotifier.class);
        _fullBright    = getModule(FullBright.class);
        _playerESP     = getModule(PlayerESP.class);
        _mobESP        = getModule(MobESP.class);
        _storageESP    = getModule(StorageESP.class);
        _blockESP      = getModule(BlockESP.class);
        _holeESP       = getModule(HoleESP.class);
        _freeLook      = getModule(FreeLook.class);
        _wallHack      = getModule(WallHack.class);
        _tridentESP    = getModule(TridentESP.class);
        _hud           = getModule(HUD.class);
        _targetHUD     = getModule(TargetHUD.class);
        _targetBlockHUD = getModule(TargetBlockHUD.class);
        _regionMap     = getModule(RegionMap.class);
        _nametags      = getModule(Nametags.class);
        _spotifyHUD    = getModule(SpotifyHUD.class);
    }}
