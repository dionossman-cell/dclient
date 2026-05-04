package com.dclient.module.modules.client;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import org.lwjgl.glfw.GLFW;

public class FourEPlus extends Module {
    public final Setting<Boolean> optimizeTicks = addSetting("Optimize Ticks", true);
    // GUI keybind — stored as GLFW key code, default Right Shift
    public final Setting<Integer> guiKey = addSetting("GUI Key", GLFW.GLFW_KEY_RIGHT_SHIFT);
    // "New" = centered window, "Classic" = columns
    public final Setting<String> guiStyle = addSetting("GUI Style", "New", new String[]{"New", "Classic"});

    public FourEPlus() { super("dclient+", Category.CLIENT); }
}
