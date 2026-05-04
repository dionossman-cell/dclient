package com.dclient.module.modules.client;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;

public class Theme extends Module {
    public final Setting<Float> accentR = addSetting("Accent Red",   1.0f, 0.0f, 1.0f);
    public final Setting<Float> accentG = addSetting("Accent Green", 0.27f, 0.0f, 1.0f);
    public final Setting<Float> accentB = addSetting("Accent Blue",  0.27f, 0.0f, 1.0f);

    public Theme() { super("Theme", Category.CLIENT); }
}
