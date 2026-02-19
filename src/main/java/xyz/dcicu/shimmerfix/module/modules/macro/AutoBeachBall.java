package xyz.dcicu.shimmerfix.module.modules.macro;

import dev.greencat.shimmer.Shimmer;
import dev.greencat.shimmer.event.events.TickEvent;
import dev.greencat.shimmer.eventbus.ShimmerSubscribe;
import dev.greencat.shimmer.module.Module;
import dev.greencat.shimmer.module.modules.render.Hud;
import dev.greencat.shimmer.setting.settings.BooleanSetting;
import dev.greencat.shimmer.util.player.PlayerUtil;
import dev.greencat.shimmer.util.player.WalkerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public class AutoBeachBall extends Module {

    private static final String BEACH_BALL_NAME = "bouncy beach ball";

    private BlockPos startPos = null;
    private boolean returning = false;
    private long returnStartTime = 0;
    private static final long DELAY_MS = 4000;

    // 补球状态机
    private enum RefillState {
        IDLE,
        OPENING,           // 等待背包打开
        CLICK_FIRST,       // 第一次点击（目标槽位）
        WAIT_AFTER_FIRST,  // 第一次点击后延时
        CLICK_SECOND,      // 第二次点击（原主手槽位）
        WAIT_AFTER_SECOND, // 第二次点击后延时
        CLOSE              // 关闭背包
    }
    private RefillState refillState = RefillState.IDLE;
    private int targetSlot = -1;                // 找到的沙滩球槽位
    private int originalSlot = -1;              // 原主手槽位
    private long refillStartTime = 0;            // 用于超时
    private long lastActionTime = 0;              // 上次动作时间戳

    // 等待 GUI 关闭后的延迟
    private boolean waitingForGuiClose = false;
    private long guiCloseStartTime = 0;
    private static final long GUI_CLOSE_DELAY = 500; // 0.5秒

    private final BooleanSetting autoRefill = new BooleanSetting("Auto Refill", "自动从背包中补充沙滩球", true);

    private final Runnable autoDisableListener = this::onNewBallBotDisabled;

    public AutoBeachBall() {
        super("AutoBeachBall", "Auto play Bouncy Beach Ball", -1, Category.MACRO);
        addSettings(autoRefill);
        this.needDisable = false;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            setEnabled(false);
            return;
        }

        startPos = mc.player.blockPosition();
        returning = false;
        refillState = RefillState.IDLE;
        waitingForGuiClose = false;

        NewBallBot.registerAutoDisableListener(autoDisableListener);

        if (hasBeachBallInHand()) {
            throwBallAndStart();
        } else {
            if (autoRefill.isEnabled()) {
                startRefillProcess();
            } else {
                Hud.onMessage(Component.literal("§c[AutoBeachBall] 手中无球且自动补充关闭，流程结束"));
                setEnabled(false);
            }
        }
    }

    @Override
    public void onDisable() {
        NewBallBot.unregisterAutoDisableListener(autoDisableListener);
        Module newBallBot = Shimmer.getInstance().getModuleManager().getModule("NewBallBot");
        if (newBallBot != null && newBallBot.isEnabled()) {
            newBallBot.setEnabled(false);
        }
        WalkerUtils.cancel();
        startPos = null;
        returning = false;
        refillState = RefillState.IDLE;
        waitingForGuiClose = false;
        super.onDisable();
    }

    @ShimmerSubscribe
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 处理补球状态机（优先级最高）
        if (refillState != RefillState.IDLE) {
            handleRefill(mc);
            return;
        }

        // 等待 GUI 关闭状态
        if (waitingForGuiClose) {
            // 只关心背包屏幕是否关闭，如果背包屏幕仍开着，重置计时器
            if (mc.screen instanceof InventoryScreen) {
                guiCloseStartTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - guiCloseStartTime >= GUI_CLOSE_DELAY) {
                waitingForGuiClose = false;
                // 延迟结束，抛球并启动 NewBallBot
                if (hasBeachBallInHand()) {
                    performThrowAndRestart();
                } else {
                    Hud.onMessage(Component.literal("§c[AutoBeachBall] 补球后手中仍无球，流程结束"));
                    setEnabled(false);
                }
            }
            return;
        }

        // 回程处理
        if (returning) {
            Module newBallBot = Shimmer.getInstance().getModuleManager().getModule("NewBallBot");
            if (newBallBot != null && newBallBot.isEnabled()) {
                newBallBot.setEnabled(false);
            }

            if (!WalkerUtils.isActive()) {
                long elapsed = System.currentTimeMillis() - returnStartTime;
                if (elapsed >= DELAY_MS) {
                    if (hasBeachBallInHand()) {
                        performThrowAndRestart();
                    } else {
                        if (autoRefill.isEnabled()) {
                            startRefillProcess();
                        } else {
                            Hud.onMessage(Component.literal("§c[AutoBeachBall] 无球可用，流程结束"));
                            setEnabled(false);
                        }
                    }
                }
            }
        }
    }

    /**
     * 启动补球流程：打开背包，准备点击
     */
    private void startRefillProcess() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 先尝试找到沙滩球槽位
        Inventory inv = mc.player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!stack.isEmpty() && isBeachBallItem(stack)) {
                targetSlot = slot;
                originalSlot = inv.getSelectedSlot();
                break;
            }
        }
        if (targetSlot == -1) {
            Hud.onMessage(Component.literal("§c[AutoBeachBall] 背包中无沙滩球，流程结束"));
            setEnabled(false);
            return;
        }

        // 打开背包
        mc.setScreen(new InventoryScreen(mc.player));
        refillState = RefillState.OPENING;
        refillStartTime = System.currentTimeMillis();
    }

    /**
     * 处理补球状态机
     */
    private void handleRefill(Minecraft mc) {
        long now = System.currentTimeMillis();
        switch (refillState) {
            case OPENING:
                if (mc.screen instanceof InventoryScreen) {
                    refillState = RefillState.CLICK_FIRST;
                } else if (now - refillStartTime > 1000) {
                    Hud.onMessage(Component.literal("§c[AutoBeachBall] 打开背包超时，流程结束"));
                    setEnabled(false);
                    refillState = RefillState.IDLE;
                }
                break;

            case CLICK_FIRST:
                // 执行第一次点击（点击目标槽位）
                clickSlot(targetSlot);
                lastActionTime = now;
                refillState = RefillState.WAIT_AFTER_FIRST;
                break;

            case WAIT_AFTER_FIRST:
                if (now - lastActionTime >= 300) {
                    refillState = RefillState.CLICK_SECOND;
                }
                break;

            case CLICK_SECOND:
                // 执行第二次点击（点击原主手槽位）
                clickSlot(originalSlot);
                lastActionTime = now;
                refillState = RefillState.WAIT_AFTER_SECOND;
                break;

            case WAIT_AFTER_SECOND:
                if (now - lastActionTime >= 300) {
                    refillState = RefillState.CLOSE;
                }
                break;

            case CLOSE:
                // 关闭背包
                mc.player.closeContainer();
                waitingForGuiClose = true;
                guiCloseStartTime = now;
                refillState = RefillState.IDLE;
                break;

            default:
                refillState = RefillState.IDLE;
                break;
        }
    }

    private boolean isBeachBallItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = StringUtil.stripColor(stack.getHoverName().getString()).toLowerCase();
        return name.contains("beach ball") || name.contains("bouncy beach ball");
    }

    private void clickSlot(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, 0, ClickType.PICKUP, mc.player);
        }
    }

    private void performThrowAndRestart() {
        if (!hasBeachBallInHand()) return;
        PlayerUtil.useItem();
        Module newBallBot = Shimmer.getInstance().getModuleManager().getModule("NewBallBot");
        if (newBallBot != null && !newBallBot.isEnabled()) {
            newBallBot.setEnabled(true);
            Hud.onMessage(Component.literal("§a[AutoBeachBall] 已抛球，启动 NewBallBot"));
        } else {
            Hud.onMessage(Component.literal("§c[AutoBeachBall] 无法启动 NewBallBot"));
            setEnabled(false);
        }
        returning = false;
    }

    private void throwBallAndStart() {
        PlayerUtil.useItem();
        Module newBallBot = Shimmer.getInstance().getModuleManager().getModule("NewBallBot");
        if (newBallBot != null && !newBallBot.isEnabled()) {
            newBallBot.setEnabled(true);
            Hud.onMessage(Component.literal("§a[AutoBeachBall] 已抛球，启动 NewBallBot"));
        } else {
            Hud.onMessage(Component.literal("§c[AutoBeachBall] 无法启动 NewBallBot"));
            setEnabled(false);
        }
    }

    private boolean hasBeachBallInHand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return isBeachBallItem(mc.player.getMainHandItem());
    }

    private void onNewBallBotDisabled() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || startPos == null) return;
        Hud.onMessage(Component.literal("§e[AutoBeachBall] NewBallBot 已自动禁用，开始返回起点"));
        returning = true;
        returnStartTime = System.currentTimeMillis();
        WalkerUtils.walkTo(startPos);
    }
}