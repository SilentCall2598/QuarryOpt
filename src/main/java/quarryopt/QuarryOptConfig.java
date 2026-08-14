package quarryopt;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class QuarryOptConfig {

    public static boolean filterCache = true;

    public static boolean filterFullCache = true;
    public static boolean dropCapture = true;

    public static int chunkResetRadius = 1;

    private QuarryOptConfig() {
    }

    static void load(File file) {
        Configuration config = new Configuration(file);
        try {
            config.load();
            filterCache = config.getBoolean(
                    "filterCache", Configuration.CATEGORY_GENERAL, true,
                    "Cache deserialized Extra Utilities 2 filter contents instead of rebuilding them from NBT on every check.");
            dropCapture = config.getBoolean(
                    "dropCapture", Configuration.CATEGORY_GENERAL, true,
                    "Skip building throwaway EntityItems for drops that Extra Utilities 2 is about to capture anyway.");
            filterFullCache = config.getBoolean(
                    "filterFullCache", Configuration.CATEGORY_GENERAL, true,
                    "Cache a whole filter at once (all 16 slots plus its flags) rather than slot by slot. "
                            + "Set false to fall back to the simpler per-slot cache. Needs filterCache on.");
            chunkResetRadius = config.getInt(
                    "chunkResetRadius", Configuration.CATEGORY_GENERAL, 1, 1, 2,
                    "Half-width of the chunk area wiped when a Quantum Quarry finishes a chunk. "
                            + "1 = 3x3 (9 chunks, everything that can actually have been modified). "
                            + "2 = 5x5 (25 chunks, Extra Utilities 2's original behaviour). "
                            + "Automatically forced to 2 while the Extra Utilities 2 option "
                            + "'Quantum Quarry: Enable Nether/End biome generation' is on, because those "
                            + "jobs write blocks across the full 5x5.");
        } catch (Exception e) {
            QuarryOpt.LOGGER.error("Failed to read config, all optimizations stay at their default settings", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

}
