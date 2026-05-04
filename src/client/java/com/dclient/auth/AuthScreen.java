package com.dclient.auth;

import com.dclient.client.gui.ThemeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;

public class AuthScreen extends Screen {

    private static final int BOX_W = 300;
    private static final int BOX_H = 160;

    // States
    private enum State { INPUT, CHECKING, VALID, INVALID }
    private State state = State.INPUT;

    private String keyInput = "";
    private String statusMsg = "";
    private int statusColor = 0xFFAAAAAA;

    // The screen to open after successful auth
    private final Screen nextScreen;

    public AuthScreen(Screen nextScreen) {
        super(Component.literal("DClient Auth"));
        this.nextScreen = nextScreen;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (state == State.CHECKING) return true; // block input while checking

        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) return true; // can't escape auth
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit(); return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !keyInput.isEmpty()) {
            keyInput = keyInput.substring(0, keyInput.length() - 1);
            state = State.INPUT; statusMsg = "";
            return true;
        }
        // Ctrl+V paste
        if (key == GLFW.GLFW_KEY_V && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            String clipboard = GLFW.glfwGetClipboardString(net.minecraft.client.Minecraft.getInstance().getWindow().handle());
            if (clipboard != null) {
                keyInput = clipboard.trim().toUpperCase().replaceAll("[^A-Z0-9\\-]", "");
                state = State.INPUT; statusMsg = "";
            }
            return true;
        }
        char c = keyToChar(key, event.modifiers());
        if (c != 0 && keyInput.length() < 32) {
            keyInput += c;
            state = State.INPUT; statusMsg = "";
        }
        return true;
    }

    private char keyToChar(int key, int mods) {
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
            return shift ? (char)('A' + key - GLFW.GLFW_KEY_A) : (char)('a' + key - GLFW.GLFW_KEY_A);
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return (char)('0' + key - GLFW.GLFW_KEY_0);
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) return '-';
        return 0;
    }

    private void submit() {
        if (keyInput.trim().isEmpty()) {
            statusMsg = "Enter a key first."; statusColor = 0xFFFF4444; return;
        }
        state = State.CHECKING;
        statusMsg = "Connecting to server..."; statusColor = 0xFFAAAAAA;

        AuthManager.validateAsync(keyInput.trim().toUpperCase()).thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                switch (result) {
                    case VALID -> {
                        AuthManager.saveKey(keyInput.trim().toUpperCase());
                        state = State.VALID;
                        statusMsg = "Valid! Loading...";
                        statusColor = 0xFF44FF88;
                        // Short delay then open the game
                        CompletableFuture.runAsync(() -> {
                            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                            Minecraft.getInstance().execute(() ->
                                Minecraft.getInstance().setScreen(nextScreen));
                        });
                    }
                    case BLOCKED -> {
                        state = State.INVALID;
                        statusMsg = "Key is blocked.";
                        statusColor = 0xFFFF4444;
                    }
                    case EXPIRED -> {
                        state = State.INVALID;
                        statusMsg = "Key has expired.";
                        statusColor = 0xFFFFAA33;
                    }
                    case HWID_MISMATCH -> {
                        state = State.INVALID;
                        statusMsg = "Key bound to another machine.";
                        statusColor = 0xFFFFAA33;
                    }
                    case NO_CONNECTION -> {
                        // Only let them in if they already have a saved key (returning user)
                        // New users with no saved key must wait for server to be online
                        String saved = AuthManager.loadSavedKey();
                        if (saved != null && !saved.isEmpty()) {
                            state = State.VALID;
                            statusMsg = "Server offline — using cached key.";
                            statusColor = 0xFFFFAA33;
                            AuthManager.saveKey(keyInput.trim().toUpperCase());
                            CompletableFuture.runAsync(() -> {
                                try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                                Minecraft.getInstance().execute(() ->
                                    Minecraft.getInstance().setScreen(nextScreen));
                            });
                        } else {
                            state = State.INVALID;
                            statusMsg = "Server offline. Try again later.";
                            statusColor = 0xFFFF8844;
                        }
                    }
                    default -> {
                        state = State.INVALID;
                        statusMsg = "Invalid key.";
                        statusColor = 0xFFFF4444;
                    }
                }
            });
        });
    }

    private boolean prevLeft = false;

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long window = Minecraft.getInstance().getWindow().handle();
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (leftDown && !prevLeft && state != State.CHECKING && state != State.VALID) {
            int bx = (width  - BOX_W) / 2;
            int by = (height - BOX_H) / 2;
            int btnX = bx + 20, btnY = by + 84, btnW = BOX_W - 40, btnH = 18;
            if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH)
                submit();
        }
        prevLeft = leftDown;
        int accent = ThemeUtil.accent();
        int bx = (width  - BOX_W) / 2;
        int by = (height - BOX_H) / 2;

        // Backdrop
        gfx.fill(0, 0, width, height, 0xFF050505);

        // Box shadow
        gfx.fill(bx + 4, by + 4, bx + BOX_W + 4, by + BOX_H + 4, 0x55000000);

        // Box background
        gfx.fill(bx, by, bx + BOX_W, by + BOX_H, 0xFF0A0A0A);

        // Accent top bar
        gfx.fill(bx, by, bx + BOX_W, by + 3, accent);

        // Border
        gfx.fill(bx, by + 3,          bx + BOX_W, by + 4,          0xFF1E1E1E);
        gfx.fill(bx, by + BOX_H - 1,  bx + BOX_W, by + BOX_H,      0xFF1E1E1E);
        gfx.fill(bx, by,               bx + 1,     by + BOX_H,      0xFF1E1E1E);
        gfx.fill(bx + BOX_W - 1, by,   bx + BOX_W, by + BOX_H,     0xFF1E1E1E);

        // Title
        String title = "DClient";
        gfx.drawString(font, title, bx + (BOX_W - font.width(title)) / 2, by + 14, accent, false);

        // Subtitle
        String sub = "Enter your license key to continue";
        gfx.drawString(font, sub, bx + (BOX_W - font.width(sub)) / 2, by + 30, 0xFF555555, false);

        // Key input box
        int inX = bx + 20, inY = by + 52, inW = BOX_W - 40, inH = 20;
        gfx.fill(inX, inY, inX + inW, inY + inH, 0xFF0D0D0D);
        // Accent bottom line
        gfx.fill(inX, inY + inH - 1, inX + inW, inY + inH,
            state == State.INVALID ? 0xFFFF4444 : accent);

        String display = keyInput.isEmpty() ? "DC-XXXX-XXXX-XXXX-XXXX" :
            (keyInput + (state == State.INPUT ? "|" : ""));
        int displayColor = keyInput.isEmpty() ? 0xFF333333 : 0xFFDDDDDD;
        gfx.drawString(font, display, inX + 6, inY + (inH - 8) / 2, displayColor, false);

        // Submit button
        int btnX = bx + 20, btnY = by + 84, btnW = BOX_W - 40, btnH = 18;
        boolean btnHov = mouseX >= btnX && mouseX < btnX + btnW
                      && mouseY >= btnY && mouseY < btnY + btnH
                      && state != State.CHECKING;
        boolean btnDisabled = state == State.CHECKING || state == State.VALID;
        int btnBg = btnDisabled ? 0xFF111111 : (btnHov ? ThemeUtil.accentDark() : 0xFF111111);
        gfx.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
        if (!btnDisabled) gfx.fill(btnX, btnY, btnX + btnW, btnY + 1, accent);
        String btnLabel = state == State.CHECKING ? "Checking..." : "Activate";
        gfx.drawCenteredString(font, btnLabel,
            bx + BOX_W / 2, btnY + (btnH - 8) / 2,
            btnDisabled ? 0xFF333333 : accent);

        // Status message
        if (!statusMsg.isEmpty()) {
            gfx.drawCenteredString(font, statusMsg,
                bx + BOX_W / 2, by + 116, statusColor);
        }

        // Version / footer
        String footer = "dclient.xyz";
        gfx.drawString(font, footer, bx + BOX_W - font.width(footer) - 8,
            by + BOX_H - 14, 0xFF222222, false);
    }

    @Override public boolean isPauseScreen() { return true; }
}
