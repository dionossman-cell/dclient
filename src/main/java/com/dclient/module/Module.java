package com.dclient.module;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String name;
    public final Category category;
    private boolean enabled = false;
    protected final List<Setting<?>> settings = new ArrayList<>();

    // -1 = no bind, otherwise GLFW key code
    private int bind = -1;

    public Module(String name, Category category) {
        this.name = name;
        this.category = category;
    }

    public boolean isEnabled() { return enabled; }

    public void toggle() {
        enabled = !enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) toggle();
    }

    public int getBind() { return bind; }
    public void setBind(int key) { this.bind = key; }

    public List<Setting<?>> getSettings() { return settings; }

    protected <T> Setting<T> addSetting(String name, T defaultValue) {
        Setting<T> s = new Setting<>(name, defaultValue);
        settings.add(s);
        return s;
    }

    protected <T> Setting<T> addSetting(String name, T defaultValue, T min, T max) {
        Setting<T> s = new Setting<>(name, defaultValue, min, max);
        settings.add(s);
        return s;
    }

    protected Setting<String> addSetting(String name, String defaultValue, String[] options) {
        Setting<String> s = new Setting<>(name, defaultValue, options);
        settings.add(s);
        return s;
    }

    protected void onEnable() {}
    protected void onDisable() {}
}
