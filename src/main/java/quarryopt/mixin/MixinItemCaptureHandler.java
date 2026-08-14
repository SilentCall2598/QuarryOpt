package quarryopt.mixin;

import com.rwtema.extrautils2.eventhandlers.ItemCaptureHandler;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quarryopt.ForgeDropCapture;

import java.util.LinkedList;

@Mixin(value = ItemCaptureHandler.class, remap = false)
public abstract class MixinItemCaptureHandler {

    @Shadow
    static ThreadLocal<LinkedList<ItemStack>> capturing;

    @Inject(method = "startCapturing", at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private static void quarryopt$armForgeCapture(CallbackInfo ci) {

        ForgeDropCapture.rememberXuCapture(capturing);
        ForgeDropCapture.begin();
    }

    @Inject(method = "stopCapturing", at = @At("HEAD"), remap = false, require = 1, allow = 1)
    private static void quarryopt$drainForgeCapture(CallbackInfoReturnable<LinkedList<ItemStack>> cir) {
        LinkedList<ItemStack> list = capturing.get();
        if (list != null) {
            ForgeDropCapture.endInto(list);
        }
    }

}
