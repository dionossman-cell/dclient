package com.dclient.module.modules.misc;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;

/**
 * Name Protect — replaces your username client-side with a fake name.
 * Ported from Meteor NameProtect.
 */
public class NameProtect extends Module {
    public final Setting<String>  fakeName    = addSetting("Fake Name",    "dclient-user");
    public final Setting<Boolean> nameProtect = addSetting("Name Protect", true);

    private String realName = "";

    public NameProtect() { super("Name Protect", Category.MISC); }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) realName = mc.player.getGameProfile().name();
        else realName = mc.getUser().getName();
    }

    /** Replace real name with fake name in any string (chat, nametags, etc.) */
    public String replace(String text) {
        if (text == null || !isEnabled() || !nameProtect.getValue()) return text;
        if (realName.isEmpty()) return text;
        return text.replace(realName, fakeName.getValue());
    }

    /** Get the display name to use for the local player */
    public String getDisplayName(String original) {
        if (!isEnabled() || !nameProtect.getValue()) return original;
        String fake = fakeName.getValue();
        return fake.isEmpty() ? original : fake;
    }
}
