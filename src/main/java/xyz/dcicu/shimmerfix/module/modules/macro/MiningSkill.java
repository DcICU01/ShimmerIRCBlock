package xyz.dcicu.shimmerfix.module.modules.macro;

import dev.greencat.shimmer.event.events.TickEvent;
import dev.greencat.shimmer.eventbus.ShimmerSubscribe;
import dev.greencat.shimmer.module.Module;
import dev.greencat.shimmer.module.modules.render.Hud;
import dev.greencat.shimmer.setting.settings.BooleanSetting;
import dev.greencat.shimmer.setting.settings.StringSetting;
import dev.greencat.shimmer.util.WindowsNotificationUtils;
import dev.greencat.shimmer.util.player.PlayerUtil;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class MiningSkill extends Module {

    private long lastMessageTime = 0;
    private boolean onSkill = false;
    private boolean onCoolDown = false;
    private  boolean skillAvailable = false;

    private final BooleanSetting systemNotification = new BooleanSetting("SystemNotification",
            "Whether to enable SystemNotification",
            true);

    private final StringSetting triggerMessage = new StringSetting(
            "Trigger Message",
            "The exact message that triggers the auto skill",
            "Mining Speed Boost is now available!"
    );

    private final StringSetting cooldownMessage = new StringSetting(
            "Colldown Message",
            "The exact message that shows the begging of CoolDown",
            "Your Mining Speed Boost has expired!"
    );

    private final StringSetting isTriggeredMessage = new StringSetting(
            "Is Triggered Message",
            "Reflects that whether mining skill is triggered",
            "You used your Mining Speed Boost Pickaxe Ability!"
    );

    public MiningSkill() {
        super("MiningSkill",
                "Auto use mining skill on mining islands",
                -1,
                Category.MACRO);
        this.addSettings(systemNotification, triggerMessage, isTriggeredMessage, cooldownMessage);
        this.needDisable = false;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
        triggerSkill();
    }

    @Override
    public void onDisable() {
        onSkill = false;
        skillAvailable = false;
        onCoolDown = false;
        super.onDisable();
        //聊天栏监听占用资源不大，无需unregister
    }

    //监听Tick事件，用于在各个时期发送提醒
    @ShimmerSubscribe
    public void onTick(TickEvent event) {
        if (isEnabled()) {
            long now = System.currentTimeMillis();
            // 每 10 秒发送一次消息
            if (now - lastMessageTime >= 10000) {
                Component message = null;
                if (onSkill) {
                    message = Component.literal("You are using your Mining Skill!");
                } else if (onCoolDown) {
                    message = Component.literal("Your Mining Skill is cooling down!");
                } else if (skillAvailable) {
                    message = Component.literal("Your Mining Skill is available");
                }
                if (message != null) {
                    Hud.onMessage(message);
                    lastMessageTime = now;
                }
            }
        }
    }

    private void onGameMessage(Component message, boolean overlay) {
        String stripped = StringUtil.stripColor(message.getString());
        if (triggerMessage.getString().equalsIgnoreCase(stripped)) {
            skillAvailable = true;
            onSkill = false;
            onCoolDown = false;
            triggerSkill();
        }
        else if (isTriggeredMessage.getString().equalsIgnoreCase(stripped)) {
            skillAvailable = false;
            onSkill = true;
            onCoolDown = false;
        }
        else if (cooldownMessage.getString().equalsIgnoreCase(stripped)) {
            skillAvailable = false;
            onSkill = false;
            onCoolDown = true;
        }
    }

    private void triggerSkill() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty()) return;

        String itemName = mainHand.getHoverName().getString().toLowerCase();
        if (itemName.contains("drill") && skillAvailable) {
            PlayerUtil.useItem();
            skillAvailable = false;
            if (systemNotification.isEnabled()) {
                WindowsNotificationUtils.sendNotification("Mining Skill",
                        "激活挖掘技能",
                        2);
            }
        }
    }
}