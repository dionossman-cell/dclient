package com.dclient.module.modules.client;

import com.dclient.friends.FriendListScreen;
import com.dclient.module.Category;
import com.dclient.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Friends — opens the friend list manager.
 * Friended players are ignored by combat and ESP modules.
 * Toggle this module to open the friend list screen.
 */
public class Friends extends Module {
    public Friends() { super("Friends", Category.CLIENT); }

    @Override
    protected void onEnable() {
        // Opening the screen disables the module toggle state
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new FriendListScreen(null));
            setEnabled(false);
        });
    }
}
