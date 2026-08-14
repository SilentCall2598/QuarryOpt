package quarryopt.mixin;

import com.google.common.collect.MapMaker;
import com.rwtema.extrautils2.compatibility.StackHelper;
import com.rwtema.extrautils2.items.ItemFilterItems;
import com.rwtema.extrautils2.utils.helpers.CollectionHelper;
import com.rwtema.extrautils2.utils.helpers.NBTHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quarryopt.FilterCache;
import quarryopt.MixinStatus;
import quarryopt.QuarryOptConfig;

import java.util.Map;

@Mixin(value = ItemFilterItems.class, remap = false)
public abstract class MixinFilterGhostStackCache {

    private static final Map<NBTTagCompound, ItemStack> quarryopt$CHILD_CACHE =
            new MapMaker().weakKeys().concurrencyLevel(2).makeMap();

    @Inject(method = "getGhostStack", at = @At("HEAD"), cancellable = true, remap = false,
            require = 1, allow = 1)
    private static void quarryopt$cachedGhostStack(ItemStack filter, int i, CallbackInfoReturnable<ItemStack> cir) {
        if (!QuarryOptConfig.filterCache) {
            return;
        }

        NBTTagCompound nbt = filter.getTagCompound();
        if (nbt == null) {
            return;
        }

        if (QuarryOptConfig.filterFullCache && MixinStatus.filterApplied()) {
            cir.setReturnValue(FilterCache.get(nbt).ghosts[i]);
            return;
        }

        NBTBase raw = nbt.getTag(CollectionHelper.STRING_DIGITS[i]);
        if (!(raw instanceof NBTTagCompound)) {
            return;
        }

        NBTTagCompound tag = (NBTTagCompound) raw;

        ItemStack cached = quarryopt$CHILD_CACHE.get(tag);
        if (cached == null) {
            cached = StackHelper.loadFromNBT(tag);
            quarryopt$CHILD_CACHE.put(tag, cached);
        }

        cir.setReturnValue(cached);
    }

    @Redirect(
            method = "getFlag",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NBTTagCompound;getInteger(Ljava/lang/String;)I", remap = true),
            remap = false, require = 0, allow = 1)
    private static int quarryopt$cachedFlags(NBTTagCompound nbt, String key) {
        if (QuarryOptConfig.filterCache && QuarryOptConfig.filterFullCache
                && MixinStatus.filterApplied()) {
            return FilterCache.get(nbt).flags;
        }
        return nbt.getInteger(key);
    }

    @Inject(method = "putGhostStack", at = @At("HEAD"), remap = false, require = 1, allow = 1)
    private static void quarryopt$invalidateOnPut(ItemStack filter, int i, ItemStack result, CallbackInfo ci) {
        FilterCache.invalidate(filter.getTagCompound());
    }

    @Redirect(
            method = "setFlag",
            at = @At(value = "INVOKE", target = "Lcom/rwtema/extrautils2/utils/helpers/NBTHelper;getOrInitTagCompound(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/nbt/NBTTagCompound;"),
            remap = false, require = 1, allow = 1)
    private static NBTTagCompound quarryopt$invalidateOnFlag(ItemStack filter) {
        NBTTagCompound nbt = NBTHelper.getOrInitTagCompound(filter);
        FilterCache.invalidate(nbt);
        return nbt;
    }

}
