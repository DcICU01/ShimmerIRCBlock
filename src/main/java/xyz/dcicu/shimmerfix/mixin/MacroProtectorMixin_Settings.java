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
import xyz.dcicu.shimmerfix.access.MacroProtectorAccessor;

@Mixin(value = MacroProtector.class, remap = false)
public abstract class MacroProtectorMixin_Settings {

    @Unique
    private final NumberSetting protectRange = new NumberSetting(
            "Protect range",
            "At how many Blocks away from the enemy should the Macro stop? (Type 0.0 to disable)",
            7.0, 0.0, 100.0, 1.0
    );

    @Unique
    private final BooleanSetting ifDisable = new BooleanSetting(
            "Auto disable",
            "Whether to disable this macro when someone comes",
            true
    );

    @Unique
    private final BooleanSetting enableRangeProtect = new BooleanSetting(
            "Range Protect",
            "Allow range-protect when macroing",
            false
    );

    @Unique
    private final BooleanSetting autoRestore = new BooleanSetting(
            "Auto Restore",
            "When other players are out of range, automatically restore the macros",
            false
    );

    @Unique
    private boolean protectionTriggered = false;

    @Unique
    private boolean prevEnableState = false;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        MacroProtector self = (MacroProtector) (Object) this;
        self.addSettings(enableRangeProtect, protectRange, ifDisable, autoRestore);
    }

    @ShimmerSubscribe
    @Unique
    public void onTick(TickEvent event) {
        MacroProtector self = (MacroProtector) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        boolean currentEnable = enableRangeProtect.isEnabled();

        // 检测开关从关闭变为开启，重置触发标志
        if (currentEnable && !prevEnableState) {
            protectionTriggered = false;
        }
        prevEnableState = currentEnable;

        if (!self.isEnabled() || !currentEnable || protectRange.getValue() <= 0) return;

        double range = protectRange.getValue();
        boolean playerNearby = false;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player && entity != mc.player) {
                if (EntityUtil.isNPC(entity)) continue;
                if (mc.player.distanceTo(entity) <= range) {
                    playerNearby = true;
                    break;
                }
            }
        }

        if (playerNearby) {
            if (!protectionTriggered && ifDisable.isEnabled()) {
                // 玩家进入范围且未触发保护 → 触发禁用
                self.disableAllMacro();

                if (self.systemTray.isEnabled()) {
                    WindowsNotificationUtils.sendNotification(
                            "MacroProtector",
                            "检测到有玩家靠近，已停用Macro",
                            2
                    );
                }
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
            else if (!protectionTriggered && !ifDisable.isEnabled()) {
                // 玩家进入范围且未触发保护 仅通知模式


                if (self.systemTray.isEnabled()) {
                    WindowsNotificationUtils.sendNotification(
                            "MacroProtector",
                            "请注意， 检测到有玩家靠近",
                            2
                    );
                }
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
            // 玩家离开范围
            if (protectionTriggered && autoRestore.isEnabled() && ifDisable.isEnabled()) {
                // 自动恢复被禁用的宏
                MacroProtectorAccessor accessor = (MacroProtectorAccessor) self;
                accessor.restoreDisabledMacros();

                if (self.systemTray.isEnabled()) {
                    WindowsNotificationUtils.sendNotification(
                            "MacroProtector",
                            "玩家已远离，自动恢复宏",
                            2
                    );
                }
                if (self.sound.isEnabled()) {
                    mc.level.playSound(
                            mc.player,
                            mc.player.blockPosition(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP,
                            SoundSource.PLAYERS,
                            3.0F, 1.0F
                    );
                }
            }
            // 无论是否自动恢复，都将触发标志重置（表示当前未触发保护状态）
            protectionTriggered = false;
        }
    }
}