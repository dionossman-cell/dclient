package com.dclient.module.modules.combat;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;

public class CrystalOptimizer extends Module {
    public final Setting<Float> minDamage = addSetting("Min Damage", 6.0f);
    public final Setting<Float> maxSelfDamage = addSetting("Max Self Damage", 4.0f);
    public CrystalOptimizer() { super("Crystal Optimizer", Category.COMBAT); }
}
