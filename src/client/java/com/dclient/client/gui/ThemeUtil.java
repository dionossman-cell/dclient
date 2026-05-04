package com.dclient.client.gui;

import com.dclient.module.ModuleManager;
import com.dclient.module.modules.client.Theme;

/**
 * Central helper for reading the active accent color from the Theme module.
 * Caches the Theme module reference to avoid linear search every call.
 */
public final class ThemeUtil {
    private ThemeUtil() {}

    // Cached reference — set once after modules are initialized
    private static Theme cachedTheme = null;
    private static int cachedAccent = 0xFFFF4444;
    private static long lastCacheMs = 0;

    private static Theme getTheme() {
        // Refresh cache every 500ms in case theme changes
        long now = System.currentTimeMillis();
        if (cachedTheme == null || now - lastCacheMs > 500) {
            cachedTheme = (Theme) ModuleManager.getByName("Theme");
            lastCacheMs = now;
        }
        return cachedTheme;
    }

    /** Returns the accent color as a packed ARGB int (fully opaque). */
    public static int accent() {
        Theme theme = getTheme();
        if (theme == null) return 0xFFFF4444;
        int r = (int)(theme.accentR.getValue() * 255f) & 0xFF;
        int g = (int)(theme.accentG.getValue() * 255f) & 0xFF;
        int b = (int)(theme.accentB.getValue() * 255f) & 0xFF;
        int color = 0xFF000000 | (r << 16) | (g << 8) | b;
        cachedAccent = color;
        return color;
    }

    /** Returns the accent color with custom alpha (0-255). */
    public static int accent(int alpha) {
        int c = accent();
        return (c & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /** Darkened version of accent for backgrounds. */
    public static int accentDark() {
        int c = accent();
        int r = ((c >> 16) & 0xFF) / 3;
        int g = ((c >> 8)  & 0xFF) / 3;
        int b = ( c        & 0xFF) / 3;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
