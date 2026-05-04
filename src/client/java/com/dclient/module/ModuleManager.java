package com.dclient.module;

import com.dclient.module.modules.combat.*;
import com.dclient.module.modules.misc.*;
import com.dclient.module.modules.donut.*;
import com.dclient.module.modules.render.*;
import com.dclient.module.modules.visuals.*;
import com.dclient.module.modules.client.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();
    // O(1) name lookup — replaces O(n) stream search
    private static final Map<String, Module> byName = new HashMap<>();
    // O(1) category lookup
    private static final Map<Category, List<Module>> byCategory = new HashMap<>();

    public static void init() {
        // Combat
        register(new AutoTotem());
        register(new HoverTotem());
        register(new AutoInvTotem());
        register(new AnchorMacro());
        register(new AutoCrystal());
        register(new DoubleAnchor());
        register(new CrystalOptimizer());
        register(new TriggerBot());
        register(new AimAssist());
        register(new AntiTrap());
        register(new MaceSwap());

        // Misc
        register(new FastPlace());
        register(new SwingSpeed());
        register(new Freecam());
        register(new AutoTool());
        register(new ChatMacro());
        register(new SpotifyHUD());
        register(new NameProtect());

        // Donut
        register(new ChunkFinder());
        register(new BaseESP());
        register(new GrowthFinder());
        register(new KelpChunkESP());
        register(new PlayerDetect());
        register(new LightDebug());
        register(new FakeStats());
        register(new RegionMap());
        register(new AmethystDebug());
        register(new ShulkerDropper());
        register(new SpawnerProtect());
        register(new AhSniper());
        register(new SusChunkFinder());

        // Render
        register(new SusESP());
        register(new SpawnerNotifier());
        register(new HUD());
        register(new FullBright());
        register(new PlayerESP());
        register(new MobESP());
        register(new StorageESP());
        register(new BlockESP());
        register(new HoleESP());
        register(new FreeLook());
        register(new WallHack());

        // Visuals
        register(new Nametags());
        register(new TridentESP());
        register(new TargetHUD());
        register(new TargetBlockHUD());
        register(new HandView());

        // Client
        register(new FourEPlus());
        register(new Theme());
        register(new DiscordPresence());
        register(new SelfDestruct());
        register(new Friends());
    }

    private static void register(Module m) {
        modules.add(m);
        byName.put(m.name.toLowerCase(), m);
        byCategory.computeIfAbsent(m.category, k -> new ArrayList<>()).add(m);
    }

    public static List<Module> getAll() {
        return modules;
    }

    public static List<Module> getByCategory(Category category) {
        return byCategory.getOrDefault(category, List.of());
    }

    public static Module getByName(String name) {
        return byName.get(name.toLowerCase());
    }
}
