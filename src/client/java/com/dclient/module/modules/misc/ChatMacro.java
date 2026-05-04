package com.dclient.module.modules.misc;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;

public class ChatMacro extends Module {
    public final Setting<String> message = addSetting("Message", "Hello from DClient!");

    public ChatMacro() { super("Chat Macro", Category.MISC); }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendChat(message.getValue());
        setEnabled(false);
    }
}
