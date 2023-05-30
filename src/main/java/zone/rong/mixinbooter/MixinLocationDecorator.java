package zone.rong.mixinbooter;

import net.minecraftforge.fml.common.ModAPIManager;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.Config;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

public class MixinLocationDecorator {

    public static final String MIXIN_LOCATION_DECORATOR = "mixinLocation";

    private static final List<IMixinConfig> queuedMixinConfigs = new ArrayList<>();

    static void prepareDecoration() {
        // Preliminary results, may change when decorate is called
        for (Config config : Mixins.getConfigs()) {
            queuedMixinConfigs.add(config.getConfig());
        }
    }

    public static void decorate() {
        for (Config config : Mixins.getConfigs()) {
            queuedMixinConfigs.add(config.getConfig());
        }
        try {
            Field modApiManager$dataTable = ModAPIManager.class.getDeclaredField("dataTable");
            modApiManager$dataTable.setAccessible(true);
            ASMDataTable table = (ASMDataTable) modApiManager$dataTable.get(ModAPIManager.INSTANCE);
            for (IMixinConfig config : queuedMixinConfigs) {
                String pkg = config.getMixinPackage();
                pkg = pkg.charAt(pkg.length() - 1) == '.' ? pkg.substring(0, pkg.length() - 1) : pkg;
                config.decorate(MIXIN_LOCATION_DECORATOR, table.getCandidatesFor(pkg)
                        .stream()
                        .map(ModCandidate::getClassPathRoot)
                        .map(File::getName)
                        .findFirst()
                        .orElse("unknown"));
            }
            queuedMixinConfigs.clear();
        } catch (ReflectiveOperationException e) {
            MixinBooterPlugin.LOGGER.fatal("Not able to reflect ModAPIManager#dataTable", e);
        }
    }

    public static String getDecoratedMixinLocation(IMixinInfo mixinInfo) {
        return getDecoratedMixinLocation(mixinInfo.getConfig(), "unknown");
    }

    public static String getDecoratedMixinLocation(IMixinConfig config) {
        return getDecoratedMixinLocation(config, "unknown");
    }

    public static String getDecoratedMixinLocation(IMixinInfo mixinInfo, String defaultValue) {
        return getDecoratedMixinLocation(mixinInfo.getConfig(), defaultValue);
    }

    public static String getDecoratedMixinLocation(IMixinConfig config, String defaultValue) {
        String location = config.getDecoration(MIXIN_LOCATION_DECORATOR);
        return location == null || location.isEmpty() ? defaultValue : location;
    }

    private MixinLocationDecorator() { }

}
