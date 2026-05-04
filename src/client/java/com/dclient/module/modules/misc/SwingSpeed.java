package com.dclient.module.modules.misc;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;

/**
 * SwingSpeed — controls how fast your hand swings when mining, hitting, or placing.
 * Speed 1 = very slow (20 ticks), Speed 20 = instant (1 tick). Default vanilla = ~6.
 */
public class SwingSpeed extends Module {
    public final Setting<Float> speed = addSetting("Speed", 6.0f, 1.0f, 20.0f);

    public SwingSpeed() { super("SwingSpeed", Category.MISC); }
}
