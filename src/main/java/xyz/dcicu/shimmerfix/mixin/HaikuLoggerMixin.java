package xyz.dcicu.shimmerfix.mixin;

import dev.greencat.shimmer.util.HaikuLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HaikuLogger.class)
public class HaikuLoggerMixin {
    @Inject(method = "info", at = @At("HEAD"), cancellable = true)
    private static void onInfo(String message, CallbackInfo ci) {
        if (message.contains("[IRC]")) {
            ci.cancel(); // 阻止任何包含 [IRC] 的日志输出
        }
    }
}
