package com.dclient.module.modules.misc;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;

public class FastPlace extends Module {
    public final Setting<Integer> delay = addSetting("Delay", 0);

    public FastPlace() { super("Fast Place", Category.MISC); }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.rightClickDelay = delay.getValue();
    }
}
