package quarryopt.mixin;

import com.rwtema.extrautils2.dimensions.workhousedim.WorldProviderSpecialDim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import quarryopt.MixinStatus;
import quarryopt.QuarryOptConfig;

@Mixin(value = WorldProviderSpecialDim.class, remap = false)
public abstract class MixinChunkResetRadius {

    private static int quarryopt$radius() {
        if (!MixinStatus.chunkApplied()) {
            return 2;
        }
        if (WorldProviderSpecialDim.ALLOW_SPECIAL_DIMS) {
            return 2;
        }
        return QuarryOptConfig.chunkResetRadius;
    }

    @ModifyConstant(method = "releaseChunk", constant = @Constant(intValue = -2), remap = false,
            require = 2, allow = 2)
    private static int quarryopt$resetLowerBound(int original) {
        return -quarryopt$radius();
    }

    @ModifyConstant(method = "releaseChunk", constant = @Constant(intValue = 2), remap = false,
            require = 2, allow = 2)
    private static int quarryopt$resetUpperBound(int original) {
        return quarryopt$radius();
    }

}
