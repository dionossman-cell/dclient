package com.dclient.module.modules.visuals;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;

/**
 * Hand View — ported from Meteor HandView.
 * Adjusts position, rotation and scale of held items in first person.
 * Applied via MixinItemInHandRenderer.
 */
public class HandView extends Module {
    // Main hand
    public final Setting<Float> mainX     = addSetting("Main X",      0.0f, -2.0f, 2.0f);
    public final Setting<Float> mainY     = addSetting("Main Y",      0.0f, -2.0f, 2.0f);
    public final Setting<Float> mainZ     = addSetting("Main Z",      0.0f, -2.0f, 2.0f);
    public final Setting<Float> mainRotX  = addSetting("Main Rot X",  0.0f, -180.0f, 180.0f);
    public final Setting<Float> mainRotY  = addSetting("Main Rot Y",  0.0f, -180.0f, 180.0f);
    public final Setting<Float> mainRotZ  = addSetting("Main Rot Z",  0.0f, -180.0f, 180.0f);
    public final Setting<Float> mainScale = addSetting("Main Scale",  1.0f, 0.1f, 3.0f);

    // Off hand
    public final Setting<Float> offX      = addSetting("Off X",       0.0f, -2.0f, 2.0f);
    public final Setting<Float> offY      = addSetting("Off Y",       0.0f, -2.0f, 2.0f);
    public final Setting<Float> offZ      = addSetting("Off Z",       0.0f, -2.0f, 2.0f);
    public final Setting<Float> offRotX   = addSetting("Off Rot X",   0.0f, -180.0f, 180.0f);
    public final Setting<Float> offRotY   = addSetting("Off Rot Y",   0.0f, -180.0f, 180.0f);
    public final Setting<Float> offRotZ   = addSetting("Off Rot Z",   0.0f, -180.0f, 180.0f);
    public final Setting<Float> offScale  = addSetting("Off Scale",   1.0f, 0.1f, 3.0f);

    public HandView() { super("Hand View", Category.VISUALS); }
}
