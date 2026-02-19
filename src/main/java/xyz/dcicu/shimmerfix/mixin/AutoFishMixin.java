package xyz.dcicu.shimmerfix.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.greencat.shimmer.module.modules.macro.AutoFish;
import dev.greencat.shimmer.setting.settings.NumberSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutoFish.class, remap = false)
public abstract class AutoFishMixin {

    @Unique
    private final NumberSetting autoRethrowDelay = new NumberSetting(
            "AutoRethrow Delay",
            "The time (seconds) before AutoRethrow the foshing rod",
            8.0,
            3.0,
            100.0,
            1.0
    );

    @Unique
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private  void onInit(CallbackInfo ci) {
        AutoFish self = ((AutoFish) (Object) this);
        self.addSettings(autoRethrowDelay);
    }

    //源代码this.enableAutoRethrow.isEnabled() && System.currentTimeMillis() - lastThrow >= (long)(isThrowed ? 20000 : 3000
    @Unique
    @ModifyExpressionValue(
            method = "onTick",
    at = @At(value = "CONSTANT", args = "intValue=20000"),
    remap = false)
    private int modifyThrownDelay(int original) {
        //注入点只在常量 20000 时触发
        return (int) (autoRethrowDelay.getValue() * 1000); //秒转毫秒
    }

    //源代码var10000[1] = class_2561.method_43470("After " + (isThrowed ? 20 : 3) + "s");
    @Unique
    @ModifyExpressionValue(
            method = "onTick",
            at = @At(value = "CONSTANT", args = "intValue=20"),
            remap = false
    )
    private int modifyMessageSeconds(int original) {
        return (int) (autoRethrowDelay.getValue());
    }


}
