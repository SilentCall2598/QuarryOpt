package quarryopt.mixin;

import com.rwtema.extrautils2.eventhandlers.XPCaptureHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quarryopt.ForgeDropCapture;

@Mixin(value = XPCaptureHandler.class, remap = false)
public abstract class MixinXPCaptureHandler {

    @Shadow
    static ThreadLocal<Integer> capturing;

    @Inject(method = "startCapturing", at = @At("HEAD"), remap = false, require = 0, allow = 1)
    private static void quarryopt$rememberXpCapture(CallbackInfo ci) {
        ForgeDropCapture.rememberXpCapture(capturing);
    }

}
