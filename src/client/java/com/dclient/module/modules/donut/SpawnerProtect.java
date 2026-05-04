package com.dclient.module.modules.donut;

import com.dclient.module.Category;
import com.dclient.module.Module;
import com.dclient.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class SpawnerProtect extends Module {

    public final Setting<Float> activationRange = addSetting("Activation Range", 20.0f, 3.0f, 50.0f);
    public final Setting<Float> criticalRange   = addSetting("Critical Range",    4.0f, 0.0f, 20.0f);

    private enum State {
        IDLE, SCANNING_AREA, ROTATING, MINING, COLLECTING,
        FINDING_CHEST, PLACING_CHEST, OPEN_CHEST, STORING, DISCONNECTING
    }

    private final Set<BlockPos> knownSpawners = new HashSet<>();
    private final Queue<BlockPos> spawnerQueue = new LinkedList<>();
    private State state = State.IDLE;
    private BlockPos targetSpawner = null;
    private BlockPos targetChest   = null;
    private int ticksInState = 0;
    private int spawnerCountBefore = 0;
    private int oldSlot = -1;
    private String detectedPlayerName = "Unknown";

    public SpawnerProtect() { super("Spawner Protect", Category.DONUT); }

    @Override
    protected void onEnable() {
        state = State.IDLE;
        knownSpawners.clear();
        spawnerQueue.clear();
        scanInitialChunks();
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.options.keyShift.setDown(false);
            if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        }
    }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticksInState++;
        // Only check critical range when actively working (not idle)
        if (state != State.IDLE && state != State.DISCONNECTING) {
            checkCriticalRange(mc);
        }
        switch (state) {
            case IDLE          -> doIdle(mc);
            case SCANNING_AREA -> doScanningArea(mc);
            case ROTATING      -> doRotating(mc);
            case MINING        -> doMining(mc);
            case COLLECTING    -> doCollecting(mc);
            case FINDING_CHEST -> doFindingChest(mc);
            case PLACING_CHEST -> doPlacingChest(mc);
            case OPEN_CHEST    -> doOpenChest(mc);
            case STORING       -> doStoring(mc);
            case DISCONNECTING -> doDisconnecting(mc);
        }
    }

    private void scanInitialChunks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        int r = activationRange.getValue().intValue() + 20;
        BlockPos center = mc.player.blockPosition();
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos p = center.offset(x, y, z);
                    if (mc.level.getBlockState(p).is(Blocks.SPAWNER))
                        knownSpawners.add(p);
                }
    }

    private void checkCriticalRange(Minecraft mc) {
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (player.distanceTo(mc.player) <= criticalRange.getValue()) {
                detectedPlayerName = player.getName().getString();
                chat(mc, "§c[SpawnerProtect] Critical range! Disconnecting (" + detectedPlayerName + ")");
                setState(State.DISCONNECTING);
                return;
            }
        }
    }

    private void doIdle(Minecraft mc) {
        mc.options.keyShift.setDown(false);
        if (ticksInState % 5 != 0) return;
        for (BlockPos spawner : knownSpawners) {
            if (mc.player.distanceToSqr(Vec3.atCenterOf(spawner)) > 40000) continue;
            for (Player player : mc.level.players()) {
                if (player == mc.player) continue;
                if (player.position().distanceTo(Vec3.atCenterOf(spawner)) <= activationRange.getValue()) {
                    detectedPlayerName = player.getName().getString();
                    chat(mc, "§e[SpawnerProtect] Player detected: " + detectedPlayerName + "  securing spawners");
                    setState(State.SCANNING_AREA);
                    return;
                }
            }
        }
    }

    private void doScanningArea(Minecraft mc) {
        spawnerQueue.clear();
        BlockPos center = mc.player.blockPosition();
        for (int x = -5; x <= 5; x++)
            for (int y = -5; y <= 5; y++)
                for (int z = -5; z <= 5; z++) {
                    BlockPos p = center.offset(x, y, z);
                    if (mc.level.getBlockState(p).is(Blocks.SPAWNER)
                            && mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(p)) <= 5.0)
                        spawnerQueue.add(p);
                }
        if (spawnerQueue.isEmpty()) { setState(State.IDLE); return; }
        if (findSilkTouchSlot(mc) == -1) {
            chat(mc, "§c[SpawnerProtect] No Silk Touch pickaxe  disabling");
            setEnabled(false);
            return;
        }
        mc.options.keyShift.setDown(true);
        nextSpawner(mc);
    }

    private void nextSpawner(Minecraft mc) {
        if (isInventoryFull(mc) || spawnerQueue.isEmpty()) { setState(State.FINDING_CHEST); return; }
        targetSpawner = spawnerQueue.poll();
        setState(State.ROTATING);
    }

    private void doRotating(Minecraft mc) {
        mc.options.keyShift.setDown(true);
        float[] r = getRotations(mc, Vec3.atCenterOf(targetSpawner));
        mc.player.setYRot(r[0]); mc.player.setXRot(r[1]);
        if (ticksInState > 2) setState(State.MINING);
    }

    private void doMining(Minecraft mc) {
        mc.options.keyShift.setDown(true);
        if (!mc.level.getBlockState(targetSpawner).is(Blocks.SPAWNER)) {
            spawnerCountBefore = countSpawners(mc);
            setState(State.COLLECTING);
            return;
        }
        float[] r = getRotations(mc, Vec3.atCenterOf(targetSpawner));
        mc.player.setYRot(r[0]); mc.player.setXRot(r[1]);
        if (ticksInState == 1) {
            oldSlot = mc.player.getInventory().getSelectedSlot();
            int silk = findSilkTouchSlot(mc);
            if (silk >= 0 && silk < 9) {
                mc.player.getInventory().setSelectedSlot(silk);
            } else if (silk >= 9) {
                mc.gameMode.handleInventoryMouseClick(
                    mc.player.inventoryMenu.containerId, silk,
                    mc.player.getInventory().getSelectedSlot(), ClickType.SWAP, mc.player);
            }
        }
        mc.gameMode.continueDestroyBlock(targetSpawner, Direction.UP);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (ticksInState > 200) nextSpawner(mc);
    }

    private void doCollecting(Minecraft mc) {
        mc.options.keyShift.setDown(true);
        if (ticksInState == 1 && oldSlot >= 0 && oldSlot < 9)
            mc.player.getInventory().setSelectedSlot(oldSlot);
        if (countSpawners(mc) > spawnerCountBefore) { nextSpawner(mc); return; }
        if (ticksInState > 40) nextSpawner(mc);
    }

    private void doFindingChest(Minecraft mc) {
        mc.options.keyShift.setDown(false);
        // Wait a tick before searching so sneak state clears
        if (ticksInState < 3) return;
        targetChest = findBlockPos(mc, Blocks.ENDER_CHEST, 5);
        if (targetChest != null) { setState(State.OPEN_CHEST); return; }
        if (hasItem(mc, Items.ENDER_CHEST)) { setState(State.PLACING_CHEST); return; }
        if (ticksInState > 10) {
            chat(mc, "§c[SpawnerProtect] No ender chest within 5 blocks  disconnecting");
            setState(State.DISCONNECTING);
        }
    }

    private void doPlacingChest(Minecraft mc) {
        mc.options.keyShift.setDown(false);
        if (ticksInState < 3) return;
        int slot = findItemSlot(mc, Items.ENDER_CHEST);
        if (slot >= 0 && slot < 9) mc.player.getInventory().setSelectedSlot(slot);
        BlockPos place = getEmptyAdjacentPos(mc);
        if (place != null && ticksInState > 5) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(place), Direction.UP, place, false));
            mc.player.swing(InteractionHand.MAIN_HAND);
            targetChest = place;
            setState(State.OPEN_CHEST);
        }
        if (ticksInState > 40) {
            chat(mc, "§c[SpawnerProtect] Could not place ender chest  disconnecting");
            setState(State.DISCONNECTING);
        }
    }

    private void doOpenChest(Minecraft mc) {
        mc.options.keyShift.setDown(false);
        AbstractContainerMenu menu = mc.player.containerMenu;
        // Ender chest = 27 slots + 36 player slots = 63 total
        if (menu.slots.size() == 63) { setState(State.STORING); return; }

        // Switch to empty hand on first tick
        if (ticksInState == 1) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).isEmpty()) {
                    mc.player.getInventory().setSelectedSlot(i);
                    break;
                }
            }
        }

        // Rotate toward chest and send interaction every 3 ticks
        if (ticksInState % 3 == 0) {
            float[] r = getRotations(mc, Vec3.atCenterOf(targetChest));
            mc.player.setYRot(r[0]);
            mc.player.setXRot(r[1]);
            // Calculate closest face
            Vec3 pp = mc.player.position();
            Vec3 bc = Vec3.atCenterOf(targetChest);
            double ddx = pp.x - bc.x, ddy = pp.y - bc.y, ddz = pp.z - bc.z;
            double aax = Math.abs(ddx), aay = Math.abs(ddy), aaz = Math.abs(ddz);
            Direction face = aax >= aay && aax >= aaz
                ? (ddx > 0 ? Direction.EAST : Direction.WEST)
                : (aay >= aax && aay >= aaz
                    ? (ddy > 0 ? Direction.UP : Direction.DOWN)
                    : (ddz > 0 ? Direction.SOUTH : Direction.NORTH));
            Vec3 hitVec = Vec3.atCenterOf(targetChest).add(
                face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hitVec, face, targetChest, false));
        }

        if (ticksInState > 120) {
            chat(mc, "§c[SpawnerProtect] Could not open ender chest  place it within 4 blocks");
            setState(State.DISCONNECTING);
        }
    }

    private void doStoring(Minecraft mc) {
        mc.options.keyShift.setDown(false);
        if (ticksInState % 2 != 0) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu.slots.size() != 63) {
            if (ticksInState > 20) setState(State.FINDING_CHEST);
            return;
        }
        // Slots 27-62 are the player inventory inside the ender chest GUI
        boolean found = false;
        for (int i = 27; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).getItem().is(Items.SPAWNER)) {
                mc.gameMode.handleInventoryMouseClick(
                    menu.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
                found = true;
                break;
            }
        }
        if (!found) {
            mc.player.closeContainer();
            chat(mc, "§a[SpawnerProtect] All spawners stored. Disconnecting.");
            setState(State.DISCONNECTING);
        }
        if (ticksInState > 200) {
            mc.player.closeContainer();
            chat(mc, "§c[SpawnerProtect] Store timeout. Disconnecting.");
            setState(State.DISCONNECTING);
        }
    }

    private void doDisconnecting(Minecraft mc) {
        mc.options.keyShift.setDown(false);
        if (ticksInState >= 20) {
            mc.disconnect(new net.minecraft.client.gui.screens.DisconnectedScreen(
                new net.minecraft.client.gui.screens.TitleScreen(),
                Component.literal("Spawner Protect"),
                Component.literal("Secured and disconnected.")), false);
            setEnabled(false);
        }
    }

    private void setState(State s) { state = s; ticksInState = 0; }

    private void chat(Minecraft mc, String msg) {
        mc.execute(() -> {
            if (mc.player != null)
                mc.player.displayClientMessage(Component.literal(msg), false);
        });
    }

    private boolean isInventoryFull(Minecraft mc) {
        for (int i = 0; i < 36; i++)
            if (mc.player.getInventory().getItem(i).isEmpty()) return false;
        return true;
    }

    private int findSilkTouchSlot(Minecraft mc) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (!s.is(Items.DIAMOND_PICKAXE) && !s.is(Items.NETHERITE_PICKAXE) && !s.is(Items.IRON_PICKAXE)) continue;
            if (hasSilkTouch(s)) return i;
        }
        return -1;
    }

    private boolean hasSilkTouch(ItemStack stack) {
        try {
            ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
            if (enchants == null || enchants.isEmpty()) return false;
            for (var holder : enchants.keySet()) {
                var key = holder.unwrapKey();
                if (key.isPresent() && key.get().identifier().getPath().contains("silk_touch")) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private int findItemSlot(Minecraft mc, Item item) {
        for (int i = 0; i < 36; i++)
            if (mc.player.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private boolean hasItem(Minecraft mc, Item item) { return findItemSlot(mc, item) != -1; }

    private int countSpawners(Minecraft mc) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.is(Items.SPAWNER)) count += s.getCount();
        }
        return count;
    }

    private BlockPos findBlockPos(Minecraft mc, Block block, int range) {
        BlockPos center = mc.player.blockPosition();
        for (int x = -range; x <= range; x++)
            for (int y = -range; y <= range; y++)
                for (int z = -range; z <= range; z++) {
                    BlockPos p = center.offset(x, y, z);
                    if (mc.level.getBlockState(p).is(block)) return p;
                }
        return null;
    }

    private BlockPos getEmptyAdjacentPos(Minecraft mc) {
        BlockPos center = mc.player.blockPosition();
        for (int x = -2; x <= 2; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -2; z <= 2; z++) {
                    if (x == 0 && z == 0) continue;
                    BlockPos p = center.offset(x, y, z);
                    if (mc.level.getBlockState(p).isAir() && !mc.level.getBlockState(p.below()).isAir()) return p;
                }
        return null;
    }

    private float[] getRotations(Minecraft mc, Vec3 target) {
        Vec3 diff = target.subtract(mc.player.getEyePosition());
        double xz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw   = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, xz));
        return new float[]{yaw, pitch};
    }
}
