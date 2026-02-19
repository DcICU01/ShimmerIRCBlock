package xyz.dcicu.shimmerfix.mixin;

import dev.greencat.shimmer.module.ModuleManager;
import dev.greencat.shimmer.module.modules.render.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.dcicu.shimmerfix.module.modules.macro.AutoBeachBall;
import xyz.dcicu.shimmerfix.module.modules.macro.AutoRefill;
import xyz.dcicu.shimmerfix.module.modules.macro.MiningSkill;
import xyz.dcicu.shimmerfix.module.modules.macro.NewBallBot;

@Mixin(value = ModuleManager.class, remap = false)
public class ModuleManagerMixin {

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void onInit(CallbackInfo ci) {
        ModuleManager self = (ModuleManager) (Object) this;
        // 移除原版的 BallBot 和 FullyBallBot
        self.modules.removeIf(module ->
                module.getName().equals("BallBot") || module.getName().equals("FullyBallBot")
        );
        // 添加我们自己的模块
        self.modules.add(new MiningSkill());
        self.modules.add(new AutoRefill());
        self.modules.add(new NewBallBot());
        self.modules.add(new AutoBeachBall());
        self.modules.add(new Hud());
    }
}