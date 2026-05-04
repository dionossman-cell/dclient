package com.dclient.config;

import com.dclient.module.Module;
import com.dclient.module.ModuleManager;
import com.dclient.module.Setting;
import com.dclient.module.modules.render.BlockESP;
import com.dclient.module.modules.render.WallHack;
import com.google.gson.*;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.*;

public class ConfigManager {
    private static final Path CONFIG_FILE = Minecraft.getInstance()
        .gameDirectory.toPath().resolve("dclient-config.json");

    public static void save() {
        JsonObject root = new JsonObject();
        for (Module mod : ModuleManager.getAll()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", mod.isEnabled());
            obj.addProperty("bind", mod.getBind());

            JsonObject settingsObj = new JsonObject();
            for (Setting<?> s : mod.getSettings()) {
                settingsObj.addProperty(s.name, s.toString());
            }
            obj.add("settings", settingsObj);

            // Save BlockESP enabled blocks
            if (mod instanceof BlockESP blockEsp) {
                JsonArray arr = new JsonArray();
                for (String b : blockEsp.enabledBlocks) arr.add(b);
                obj.add("enabledBlocks", arr);
            }

            // Save WallHack target blocks
            if (mod instanceof WallHack wallHack) {
                JsonArray arr = new JsonArray();
                for (String b : wallHack.targetBlockNames) arr.add(b);
                obj.add("targetBlocks", arr);
            }

            root.add(mod.name, obj);
        }
        try (Writer w = Files.newBufferedWriter(CONFIG_FILE)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static void load() {
        if (!Files.exists(CONFIG_FILE)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_FILE)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (Module mod : ModuleManager.getAll()) {
                if (!root.has(mod.name)) continue;
                JsonObject obj = root.getAsJsonObject(mod.name);

                boolean enabled = obj.has("enabled") && obj.get("enabled").getAsBoolean();
                if (enabled != mod.isEnabled()) mod.toggle();

                if (obj.has("bind")) mod.setBind(obj.get("bind").getAsInt());

                // Load BlockESP enabled blocks
                if (mod instanceof BlockESP blockEsp && obj.has("enabledBlocks")) {
                    blockEsp.enabledBlocks.clear();
                    for (JsonElement e : obj.getAsJsonArray("enabledBlocks")) {
                        blockEsp.enabledBlocks.add(e.getAsString());
                    }
                }

                // Load WallHack target blocks
                if (mod instanceof WallHack wallHack && obj.has("targetBlocks")) {
                    wallHack.targetBlockNames.clear();
                    for (JsonElement e : obj.getAsJsonArray("targetBlocks")) {
                        wallHack.targetBlockNames.add(e.getAsString());
                    }
                }

                if (!obj.has("settings")) continue;
                JsonObject settingsObj = obj.getAsJsonObject("settings");
                for (Setting<?> s : mod.getSettings()) {
                    if (!settingsObj.has(s.name)) continue;
                    String val = settingsObj.get(s.name).getAsString();
                    try {
                        Object def = s.getDefaultValue();
                        if (def instanceof Boolean)
                            ((Setting<Boolean>) s).setValue(Boolean.parseBoolean(val));
                        else if (def instanceof Float)
                            ((Setting<Float>) s).setValue(Float.parseFloat(val));
                        else if (def instanceof Integer)
                            ((Setting<Integer>) s).setValue(Integer.parseInt(val));
                        else if (def instanceof String)
                            ((Setting<String>) s).setValue(val);
                    } catch (Exception ignored) {}
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
