package quarryopt;

public final class MixinStatus {

    private static final String PROP_PREFIX = "quarryopt.mixin.applied.";

    public static volatile boolean filterMixinApplied;
    public static volatile boolean captureMixinApplied;
    public static volatile boolean chunkMixinApplied;

    private static volatile boolean filterConfirmed;
    private static volatile boolean captureConfirmed;
    private static volatile boolean chunkConfirmed;

    private MixinStatus() {
    }

    public static void markApplied(String key) {
        if ("filter".equals(key)) {
            filterMixinApplied = true;
            filterConfirmed = true;
        } else if ("capture".equals(key)) {
            captureMixinApplied = true;
            captureConfirmed = true;
        } else if ("chunk".equals(key)) {
            chunkMixinApplied = true;
            chunkConfirmed = true;
        }
        try {
            System.setProperty(PROP_PREFIX + key, "true");
        } catch (Throwable ignored) {

        }
    }

    public static boolean filterApplied() {
        if (filterConfirmed) {
            return true;
        }
        if (filterMixinApplied || propertyTrue("filter")) {
            filterConfirmed = true;
            return true;
        }
        return false;
    }

    public static boolean captureApplied() {
        if (captureConfirmed) {
            return true;
        }
        if (captureMixinApplied || propertyTrue("capture")) {
            captureConfirmed = true;
            return true;
        }
        return false;
    }

    public static boolean chunkApplied() {
        if (chunkConfirmed) {
            return true;
        }
        if (chunkMixinApplied || propertyTrue("chunk")) {
            chunkConfirmed = true;
            return true;
        }
        return false;
    }

    private static boolean propertyTrue(String key) {
        try {
            return "true".equals(System.getProperty(PROP_PREFIX + key));
        } catch (Throwable ignored) {
            return false;
        }
    }

}
