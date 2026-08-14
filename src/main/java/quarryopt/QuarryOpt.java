package quarryopt;

import com.rwtema.extrautils2.dimensions.workhousedim.WorldProviderSpecialDim;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        dependencies = "required-after:extrautils2",
        acceptableRemoteVersions = "*"
)
public class QuarryOpt {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        QuarryOptConfig.load(event.getSuggestedConfigurationFile());
        ForgeDropCapture.init();
        MinecraftForge.EVENT_BUS.register(new QuarryOpt());
        LOGGER.info("{} v{} loaded (filterCache={}, filterFullCache={}, dropCapture={}, chunkResetRadius={}, forgeCaptureAvailable={})",
                Tags.MOD_NAME, Tags.VERSION,
                QuarryOptConfig.filterCache, QuarryOptConfig.filterFullCache,
                QuarryOptConfig.dropCapture, QuarryOptConfig.chunkResetRadius,
                ForgeDropCapture.isAvailable());

        if (WorldProviderSpecialDim.ALLOW_SPECIAL_DIMS) {
            LOGGER.info("Extra Utilities 2 Nether/End biome generation is enabled, so the chunk "
                    + "reset area stays at 5x5, those jobs write blocks across the whole 5x5 area.");
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        LOGGER.info("Mixin status at server start (false may just mean the target class has not "
                        + "been loaded yet): filter={}, capture={}, chunk={}",
                MixinStatus.filterApplied(), MixinStatus.captureApplied(), MixinStatus.chunkApplied());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ForgeDropCapture.endOfTickCheck();
        }
    }

}
