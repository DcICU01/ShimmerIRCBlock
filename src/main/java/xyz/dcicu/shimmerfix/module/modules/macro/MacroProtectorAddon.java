package xyz.dcicu.shimmerfix.module.modules.macro;

import baritone.api.event.events.TickEvent;
import dev.greencat.shimmer.eventbus.ShimmerSubscribe;
import dev.greencat.shimmer.module.modules.macro.MacroProtector;
import dev.greencat.shimmer.setting.settings.NumberSetting;
import dev.greencat.shimmer.util.WindowsNotificationUtils;
import dev.greencat.shimmer.util.entity.EntityUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;


public class MacroProtectorAddon extends MacroProtector {
    public final NumberSetting protectRange = new NumberSetting("Protect range", "At how many Blocks away from the enemy should the Macro stop?", (double) 7.0F, (double) 1.0F, (double) 100.0F, (double) 1.0F);
    private boolean protectionTriggered = false;

    public MacroProtectorAddon() {
        super();
        this.addSettings(protectRange);
    }

    @ShimmerSubscribe
    public void onTick(TickEvent event) {

    }

    private void checkNearbyPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || this.isEnabled()) {return;}

        double range = protectRange.getValue();
        boolean playerNearby = false;

        //遍历所有实体 (使用Shimmer中常见的遍历方式)
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player && entity != mc.player) {
                //利用 EntityUtil 排除 NPC
                if (EntityUtil.isNPC(entity)) {
                    continue;
                }
                double distance = mc.player.distanceTo(entity);
                if (distance <= range) {
                    playerNearby = true;
                    break;
                }
            }
        }
        if (playerNearby) {
            if (!protectionTriggered) {
                //直接调用父类的 public方法禁用所有宏
                this.disableAllMacro();
                //根据用户设置发出System通知
                if (this.systemTray.isEnabled()) {
                    WindowsNotificationUtils.sendNotification("MacroProtector", "检测到有玩家靠近，已停用Macro", 2);
                }
                if (this.sound.isEnabled()) {
                    mc.level.playSound(mc.player, mc.player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 3.0F, 1.0F);
                }
                protectionTriggered = true;
            }
        } else {protectionTriggered = false;}

    }
}
