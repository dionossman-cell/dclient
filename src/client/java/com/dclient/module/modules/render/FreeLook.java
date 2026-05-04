package com.dclient.module.modules.render;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Free Look — ported from Meteor Client.
 * Player mode: camera rotates independently, player body stays put.
 * Camera mode: player body rotates, camera follows.
 */
public class FreeLook extends Module {
    // 0 = Player mode, 1 = Camera mode
    public final Setting<String>  lookMode     = addSetting("Look Mode", "Player", new String[]{"Player", "Camera"});
    public final Setting<Boolean> togglePersp  = addSetting("Toggle Perspective", true);
    public final Setting<Boolean> arrowKeys    = addSetting("Arrow Keys", true);
    public final Setting<Float> arrowSpeed     = addSetting("Arrow Speed", 4.0f);

    public float cameraYaw;
    public float cameraPitch;
    private CameraType prevPerspective;

    // Temp fields used by MixinCamera
    public float _origYaw, _origPitch;
    public boolean _needsRestore = false;

    public FreeLook() { super("Free Look", Category.RENDER); }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        cameraYaw = mc.player.getYRot();
        cameraPitch = mc.player.getXRot();
        prevPerspective = mc.options.getCameraType();
        if (togglePersp.getValue() && prevPerspective != CameraType.THIRD_PERSON_BACK)
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (togglePersp.getValue() && prevPerspective != null)
            mc.options.setCameraType(prevPerspective);
    }

    /** True when in Player mode and third person is active */
    public boolean isPlayerMode() {
        Minecraft mc = Minecraft.getInstance();
        return isEnabled()
            && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK
            && lookMode.getValue().equals("Player");
    }

    /** True when in Camera mode */
    public boolean isCameraMode() {
        return isEnabled() && lookMode.getValue().equals("Camera");
    }

    /** Used by MixinCamera — same as isPlayerMode */
    public boolean isActive() { return isPlayerMode(); }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (arrowKeys.getValue()) {
            long window = mc.getWindow().handle();
            int steps = (int)(arrowSpeed.getValue() * 2);
            boolean playerMode = lookMode.getValue().equals("Player");
            for (int i = 0; i < steps; i++) {
                if (playerMode) {
                    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT)  == GLFW.GLFW_PRESS) cameraYaw -= 0.5f;
                    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS) cameraYaw += 0.5f;
                    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP)    == GLFW.GLFW_PRESS) cameraPitch -= 0.5f;
                    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN)  == GLFW.GLFW_PRESS) cameraPitch += 0.5f;
                } else {
                    float yaw = mc.player.getYRot();
                    float pitch = mc.player.getXRot();
                    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT)  == GLFW.GLFW_PRESS) yaw -= 0.5f;
                    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS) yaw += 0.5f;
                    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP)    == GLFW.GLFW_PRESS) pitch -= 0.5f;
                    if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN)  == GLFW.GLFW_PRESS) pitch += 0.5f;
                    mc.player.setYRot(yaw);
                    mc.player.setXRot(Mth.clamp(pitch, -90, 90));
                }
            }
        }

        cameraPitch = Mth.clamp(cameraPitch, -90, 90);
    }
}
