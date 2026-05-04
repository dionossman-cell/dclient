package com.dclient.module.modules.client;

import com.dclient.module.Category;
import com.dclient.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Disables all modules and clears all config when enabled.
 * Emergency panic button.
 */
public class SelfDestruct extends Module {
    public SelfDestruct() { super("Self Destruct", Category.CLIENT); }

    @Override
    protected void onEnable() {
        com.dclient.module.ModuleManager.getAll().forEach(m -> {
            if (m != this && m.isEnabled()) m.setEnabled(false);
        });
        setEnabled(false);
        Minecraft.getInstance().setScreen(null);
    }
}
