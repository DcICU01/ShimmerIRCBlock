package xyz.dcicu.shimmerfix.mixin;

import dev.greencat.shimmer.module.ModuleManager;
import dev.greencat.shimmer.module.modules.render.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.dcicu.shimmerfix.module.modules.macro.AutoRefill;
import xyz.dcicu.shimmerfix.module.modules.macro.MiningSkill;

@Mixin(value = ModuleManager.class, remap = false)
public class ModuleManagerMixin {

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void onInit(CallbackInfo ci) {
        //获取当前 ModuleManager的modules列表
        ModuleManager self = (ModuleManager) (Object) this;
        //注册 各种实例
        self.modules.add(new MiningSkill());
        self.modules.add(new Hud());
        self.modules.add(new AutoRefill());
    }
}
