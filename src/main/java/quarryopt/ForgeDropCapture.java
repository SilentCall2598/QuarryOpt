package quarryopt;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

public final class ForgeDropCapture {

    private static ThreadLocal<Boolean> captureFlag;
    private static ThreadLocal<NonNullList<ItemStack>> capturedDrops;
    private static boolean available = false;

    private static final ThreadLocal<Boolean> ownedByUs = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static ThreadLocal<LinkedList<ItemStack>> xuCapture;

    private static ThreadLocal<Integer> xpCapture;

    private static int leaks = 0;

    private static final int LEAK_LIMIT = 5;

    private ForgeDropCapture() {
    }

    @SuppressWarnings("unchecked")
    public static void init() {
        try {
            Field flag = Block.class.getDeclaredField("captureDrops");
            flag.setAccessible(true);
            captureFlag = (ThreadLocal<Boolean>) flag.get(null);

            Field drops = Block.class.getDeclaredField("capturedDrops");
            drops.setAccessible(true);
            capturedDrops = (ThreadLocal<NonNullList<ItemStack>>) drops.get(null);

            available = captureFlag != null && capturedDrops != null;
        } catch (Throwable t) {
            available = false;
            QuarryOpt.LOGGER.warn("Could not access Forge's drop capture fields, "
                    + "the drop-capture optimization will stay off and drops will behave exactly as before", t);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static void rememberXuCapture(ThreadLocal<LinkedList<ItemStack>> capturing) {
        xuCapture = capturing;
    }

    public static void rememberXpCapture(ThreadLocal<Integer> capturing) {
        xpCapture = capturing;
    }

    public static void begin() {

        if (!available || !QuarryOptConfig.dropCapture || !MixinStatus.captureApplied()) {
            return;
        }
        if (leaks >= LEAK_LIMIT) {
            return;
        }
        if (Boolean.TRUE.equals(captureFlag.get())) {
            return;
        }
        capturedDrops.get().clear();
        captureFlag.set(Boolean.TRUE);
        ownedByUs.set(Boolean.TRUE);
    }

    public static void endOfTickCheck() {
        if (!available || !Boolean.TRUE.equals(ownedByUs.get())) {
            return;
        }

        ownedByUs.set(Boolean.FALSE);
        captureFlag.set(Boolean.FALSE);

        NonNullList<ItemStack> stranded = capturedDrops.get();
        int lost = stranded.size();
        stranded.clear();

        int xuLost = 0;
        ThreadLocal<LinkedList<ItemStack>> xu = xuCapture;
        if (xu != null) {
            LinkedList<ItemStack> xuList = xu.get();
            if (xuList != null) {
                xuLost = xuList.size();
                xu.set(null);
            }
        }

        int xpLost = 0;
        ThreadLocal<Integer> xp = xpCapture;
        if (xp != null) {
            Integer held = xp.get();
            if (held != null) {
                xpLost = held;
                xp.set(null);
            }
        }

        leaks++;
        if (leaks <= 3) {
            QuarryOpt.LOGGER.error("Item capture was still active at end of server tick. Extra "
                    + "Utilities 2 started a capture and never stopped it. One known failure path "
                    + "is XPCaptureHandler.startCapturing throwing outside XU2's try block, but "
                    + "any escape between start and stop does this. Released: Forge capture ({} "
                    + "stack(s) discarded), XU2 item capture ({} stack(s) discarded), XU2 XP "
                    + "capture ({} xp discarded - zero means XP capture was not stranded). Left "
                    + "alone, stranded capture keeps swallowing item drops on this thread, and XP "
                    + "orbs too when XP capture was part of it, until restart.",
                    lost, xuLost, xpLost);
        }
        if (leaks == LEAK_LIMIT) {
            QuarryOpt.LOGGER.error("That has now happened {} times, which means something is "
                    + "repeatedly putting Extra Utilities 2's capture system into an invalid "
                    + "state. Quarry Opt will stop the Forge capture for the rest of this "
                    + "session and will no longer clean up after it."
                    + "please restart and investigate.", LEAK_LIMIT);
        }
    }

    public static void endInto(List<ItemStack> target) {
        if (!available || !Boolean.TRUE.equals(ownedByUs.get())) {
            return;
        }
        ownedByUs.set(Boolean.FALSE);
        captureFlag.set(Boolean.FALSE);

        NonNullList<ItemStack> collected = capturedDrops.get();
        if (!collected.isEmpty()) {
            target.addAll(collected);
            collected.clear();
        }
    }

}
