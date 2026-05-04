package com.dclient.module.modules.client;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class DiscordPresence extends Module {
    public final Setting<String> statusText = addSetting("Status", "Playing DClient");

    public DiscordPresence() { super("Discord Presence", Category.CLIENT); }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("[DClient] Discord Presence: " + statusText.getValue()), false);
    }
}
