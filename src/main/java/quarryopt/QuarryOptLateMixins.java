package quarryopt;

import org.apache.logging.log4j.LogManager;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Collections;
import java.util.List;

public class QuarryOptLateMixins implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        LogManager.getLogger("QuarryOpt").info("QuarryOpt registering mixins (Extra Utilities 2 only)");
        return Collections.singletonList("mixins.quarryopt.json");
    }

}
