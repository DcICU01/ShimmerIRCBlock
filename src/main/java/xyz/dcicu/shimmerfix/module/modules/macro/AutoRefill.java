package xyz.dcicu.shimmerfix.module.modules.macro;

import dev.greencat.shimmer.module.Module;
import dev.greencat.shimmer.module.modules.render.Hud;
import dev.greencat.shimmer.setting.settings.BooleanSetting;
import dev.greencat.shimmer.setting.settings.NumberSetting;
import dev.greencat.shimmer.eventbus.ShimmerSubscribe;
import dev.greencat.shimmer.event.events.TickEvent;
import dev.greencat.shimmer.util.player.PlayerUtil;
import dev.greencat.shimmer.Shimmer;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;

import java.util.List;
import java.util.ArrayList;

public class AutoRefill extends Module {

    private enum Stage {
        IDLE, RCABI, ABI, FINDDRILL, PICKDRILL, PICK0FUEL, PICKFUEL, FUSE,
        PICKDRILL2, PUTFUEL_DRILL, PICKFUEL2, PUTFUEL_FUEL, CLOSE
    }

    private Stage currentStage = Stage.IDLE;
    private final NumberSetting minFuel = new NumberSetting("Min Fuel",
            "The minimum fuel to refill the drill",
            0,
            0,
            100000,
            1);
    private final BooleanSetting forceRefill = new BooleanSetting("Force Refill",
            "Trigger a refill process immediately (auto-resets)",
            false);

    private long lastActionTime = 0;
    private long stageStartTime = 0;
    private int fuelSlot = -1;      // 燃料原始槽位
    private int drillSlot = -1;     // 钻头原始槽位
    private final List<Module> disabledModules = new ArrayList<>();

    public AutoRefill() {
        super("AutoRefill",
                "Auto refill your drill fuel",
                -1,
                Category.MACRO);
        addSettings(minFuel, forceRefill);
        this.needDisable = false;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
        resetToIdle();
    }

    @Override
    public void onDisable() {
        currentStage = Stage.IDLE;
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.closeContainer();
        }
        super.onDisable();
    }

    @ShimmerSubscribe
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 处理强制加油按钮
        if (forceRefill.isEnabled()) {
            forceRefill.setEnabled(false);
            resetToIdle();
            startRefillProcess();
        }

        // 检查状态超时（10秒无进展则重置）
        if (currentStage != Stage.IDLE && System.currentTimeMillis() - stageStartTime > 10000) {
            Hud.onMessage(Component.literal("§c[AutoRefill] Process timed out, resetting."));
            resetToIdle();
            return;
        }

        // 点击间隔 500ms
        if (System.currentTimeMillis() - lastActionTime < 500) return;

        Screen currentScreen = mc.screen;
        if (currentScreen instanceof AbstractContainerScreen<?> containerScreen) {
            handleGui(containerScreen);
        }

        if (currentStage == Stage.IDLE) {
            checkFuelAndStart(mc.player.getMainHandItem());
        }
    }

    private void handleGui(AbstractContainerScreen<?> screen) {
        String title = screen.getTitle().getString();
        AbstractContainerMenu menu = screen.getMenu();

        // 如果当前不是空闲状态，但打开的屏幕既不是Abiphone也不是Drill Anvil，说明流程被打断，重置
        if (currentStage != Stage.IDLE && !title.startsWith("Abiphone") && !title.equals("Drill Anvil")) {
            Hud.onMessage(Component.literal("§c[AutoRefill] Unexpected screen opened, resetting."));
            resetToIdle();
            return;
        }

        // Abiphone 界面处理
        if (currentStage == Stage.ABI && StringUtil.stripColor(title).toLowerCase().startsWith("abiphone")) {
            for (Slot slot : menu.slots) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    String name = StringUtil.stripColor(stack.getHoverName().getString());
                    if (name.toLowerCase().contains("jotraeline greatforge")) {
                        clickSlot(slot.index);
                        currentStage = Stage.FINDDRILL;
                        stageStartTime = System.currentTimeMillis();
                        lastActionTime = System.currentTimeMillis();
                        break;
                    }
                }
            }
        }
        // Drill Anvil 界面处理
        else if (title.equals("Drill Anvil")) {
            switch (currentStage) {
                case FINDDRILL:
                    // 扫描背包（54-89）找到钻头原始槽位
                    drillSlot = -1;
                    for (int i = 54; i < 90; i++) {
                        ItemStack stack = menu.getSlot(i).getItem();
                        if (!stack.isEmpty() && isDrill(stack)) {
                            drillSlot = i;
                            break;
                        }
                    }
                    if (drillSlot == -1) {
                        Hud.onMessage(Component.literal("§c[AutoRefill] No drill found in inventory!"));
                        setEnabled(false);
                        return;
                    }
                    clickSlot(drillSlot);
                    currentStage = Stage.PICKDRILL;
                    stageStartTime = System.currentTimeMillis();
                    lastActionTime = System.currentTimeMillis();
                    break;

                case PICKDRILL:
                    clickSlot(29); // 将钻头放入槽29
                    currentStage = Stage.PICK0FUEL;
                    stageStartTime = System.currentTimeMillis();
                    lastActionTime = System.currentTimeMillis();
                    break;

                case PICK0FUEL:
                    fuelSlot = -1;
                    for (int i = 54; i < 90; i++) {
                        ItemStack stack = menu.getSlot(i).getItem();
                        if (!stack.isEmpty() && isFuel(stack)) {
                            fuelSlot = i;
                            break;
                        }
                    }
                    if (fuelSlot == -1) {
                        Hud.onMessage(Component.literal("§c[AutoRefill] No fuel found in inventory!"));
                        setEnabled(false);
                        return;
                    }
                    clickSlot(fuelSlot); // 拿起燃料
                    currentStage = Stage.PICKFUEL;
                    stageStartTime = System.currentTimeMillis();
                    lastActionTime = System.currentTimeMillis();
                    break;

                case PICKFUEL:
                    clickSlot(33); // 将燃料放入槽33
                    currentStage = Stage.FUSE;
                    stageStartTime = System.currentTimeMillis();
                    lastActionTime = System.currentTimeMillis();
                    break;

                case FUSE:
                    clickSlot(22); // 点击融合按钮
                    currentStage = Stage.PICKDRILL2;
                    stageStartTime = System.currentTimeMillis();
                    lastActionTime = System.currentTimeMillis();
                    break;

                case PICKDRILL2:
                    clickSlot(13); // 融合后钻头出现在槽13，点击拿起钻头
                    currentStage = Stage.PUTFUEL_DRILL;
                    stageStartTime = System.currentTimeMillis();
                    lastActionTime = System.currentTimeMillis();
                    break;

                case PUTFUEL_DRILL:
                    if (drillSlot != -1) {
                        clickSlot(drillSlot); // 放回钻头原始槽位
                    }
                    currentStage = Stage.PICKFUEL2;
                    stageStartTime = System.currentTimeMillis();
                    lastActionTime = System.currentTimeMillis();
                    break;

                case PICKFUEL2:
                    clickSlot(33); // 点击33取出剩余燃料
                    currentStage = Stage.PUTFUEL_FUEL;
                    stageStartTime = System.currentTimeMillis();
                    lastActionTime = System.currentTimeMillis();
                    break;

                case PUTFUEL_FUEL:
                    if (fuelSlot != -1) {
                        clickSlot(fuelSlot); // 放回燃料原始槽位
                    }
                    currentStage = Stage.CLOSE;
                    stageStartTime = System.currentTimeMillis();
                    lastActionTime = System.currentTimeMillis();
                    break;

                case CLOSE:
                    if (mc.player != null) {
                        mc.player.closeContainer();
                    }
                    currentStage = Stage.IDLE;
                    stageStartTime = 0;
                    reEnableModules();
                    lastActionTime = System.currentTimeMillis();
                    break;
            }
        }
    }

    private void onGameMessage(Component message, Boolean overlay) {
        if (!isEnabled()) return;
        String stripped = StringUtil.stripColor(message.getString());
        if (stripped.contains("is empty! Refuel it by talking to a Drill Mechanic!")) {
            startRefillProcess();
        }
    }

    private void checkFuelAndStart(ItemStack held) {
        if (held.isEmpty()) return;

        if (!isDrill(held)) return;

        ItemLore lore = held.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                String raw = StringUtil.stripColor(line.getString());
                if (raw.startsWith("Fuel: ")) {
                    try {
                        String[] parts = raw.split(" ")[1].split("/");
                        String fuelStr = parts[0].replaceAll("[^0-9,]", "").replace(",", "");
                        int current = Integer.parseInt(fuelStr.trim());
                        if (current <= minFuel.getValue()) {
                            startRefillProcess();
                        }
                    } catch (Exception ignored) {}
                    break;
                }
            }
        }
    }

    private void startRefillProcess() {
        if (currentStage != Stage.IDLE) return;
        Hud.onMessage(Component.literal("§a[AutoRefill] Starting refill process..."));
        disableOtherMacros();
        if (!useAbiPhone()) {
            Hud.onMessage(Component.literal("§c[AutoRefill] No Abiphone in hotbar!"));
            setEnabled(false);
            return;
        }
        currentStage = Stage.ABI;
        stageStartTime = System.currentTimeMillis();
    }

    private boolean useAbiPhone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && isAbiPhone(stack)) {
                int prevSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(i);
                PlayerUtil.useItem();
                mc.player.getInventory().setSelectedSlot(prevSlot);
                return true;
            }
        }
        return false;
    }

    // 判断是否为钻头（基于物品名称）
    private boolean isDrill(ItemStack stack) {
        String name = StringUtil.stripColor(stack.getHoverName().getString()).toLowerCase();
        return name.contains("drill");
    }

    // 判断是否为燃料（基于物品名称）
    private boolean isFuel(ItemStack stack) {
        String name = StringUtil.stripColor(stack.getHoverName().getString()).toLowerCase();
        return name.contains("dicker") || name.contains("volta") || name.contains("oil") || name.contains("barrel") || name.contains("fuel");
    }

    // 判断是否为 Abiphone（基于物品名称）
    private boolean isAbiPhone(ItemStack stack) {
        String name = StringUtil.stripColor(stack.getHoverName().getString()).toLowerCase();
        return name.contains("abiphone");
    }

    private void clickSlot(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, 0, ClickType.PICKUP, mc.player);
        }
    }

    private void disableOtherMacros() {
        disabledModules.clear();
        for (Module mod : Shimmer.getInstance().getModuleManager().getModulesByCategory(Category.MACRO)) {
            if (mod != this && mod.isEnabled() && !mod.needDisable) {
                mod.setEnabled(false);
                disabledModules.add(mod);
            }
        }
    }

    private void reEnableModules() {
        for (Module mod : disabledModules) {
            mod.setEnabled(true);
        }
        disabledModules.clear();
    }

    private void resetToIdle() {
        currentStage = Stage.IDLE;
        fuelSlot = -1;
        drillSlot = -1;
        stageStartTime = 0;
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.closeContainer();
        }
    }
}