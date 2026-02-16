package xyz.dcicu.shimmerfix.mixin;

import dev.greencat.shimmer.event.events.TickEvent;
import dev.greencat.shimmer.eventbus.ShimmerSubscribe;
import dev.greencat.shimmer.module.modules.macro.MacroProtector;
import dev.greencat.shimmer.setting.settings.BooleanSetting;
import dev.greencat.shimmer.setting.settings.NumberSetting;
import dev.greencat.shimmer.util.WindowsNotificationUtils;
import dev.greencat.shimmer.util.entity.EntityUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MacroProtector.class, remap = false)
public abstract class MacroProtectorMixin {

    @Unique
    private final NumberSetting protectRange = new NumberSetting(
            "Protect range",
            "At how many Blocks away from the enemy should the Macro stop? (Type 0.0 to disable)",
            7.0, 0.0, 100.0, 1.0
    );

    @Unique
    private final BooleanSetting enableRangeProtect = new BooleanSetting(
            "Range Protect",
            "Allow range-protect when macroing",
            false
    );

    @Unique
    private boolean protectionTriggered = false; // 防止同一玩家持续触发

    @Unique
    private boolean prevEnableState = false;      // 记录上一 tick 的开关状态

    // 在构造后添加设置到父类
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        MacroProtector self = (MacroProtector) (Object) this;
        self.addSettings(enableRangeProtect, protectRange);
    }

    @ShimmerSubscribe
    @Unique
    public void onTick(TickEvent event) {
        MacroProtector self = (MacroProtector) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        boolean currentEnable = enableRangeProtect.isEnabled();

        // 检测开关从关闭变为开启，重置触发标志，使保护能够立即生效
        if (currentEnable && !prevEnableState) {
            protectionTriggered = false;
        }
        prevEnableState = currentEnable;

        // 模块未启用、开关关闭或范围为0时，不执行后续逻辑
        if (!self.isEnabled()) return;
        if (!currentEnable || protectRange.getValue() <= 0) return;

        double range = protectRange.getValue();
        boolean playerNearby = false;

        // 遍历所有实体，寻找附近的其他真实玩家
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player && entity != mc.player) {
                // 排除 NPC（如村民或特定条件的远程玩家）
                if (EntityUtil.isNPC(entity)) continue;

                if (mc.player.distanceTo(entity) <= range) {
                    playerNearby = true;
                    break;
                }
            }
        }

        if (playerNearby) {
            if (!protectionTriggered) {
                // 禁用所有需要关闭的宏模块
                self.disableAllMacro();

                // 系统托盘通知（如果用户开启）
                if (self.systemTray.isEnabled()) {
                    WindowsNotificationUtils.sendNotification(
                            "MacroProtector",
                            "检测到有玩家靠近，已停用Macro",
                            2
                    );
                }
                // 播放音效（如果用户开启）
                if (self.sound.isEnabled()) {
                    mc.level.playSound(
                            mc.player,
                            mc.player.blockPosition(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP,
                            SoundSource.PLAYERS,
                            3.0F, 1.0F
                    );
                }
                protectionTriggered = true;
            }
        } else {
            protectionTriggered = false; // 玩家离开范围，重置标志以便下次触发
        }
    }
}