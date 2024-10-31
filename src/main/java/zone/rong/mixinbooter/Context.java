package zone.rong.mixinbooter;

import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import org.apache.commons.lang3.SystemUtils;

import java.util.Collection;

/**
 * This class contains loading context for callers
 *
 * @since 10.0
 */
public final class Context {

    public enum ModLoader {

        FORGE,
        CLEANROOM;

        // Something more robust in the future
        private static final ModLoader CURRENT = SystemUtils.IS_JAVA_1_8 ? FORGE : CLEANROOM;

    }

    private final String mixinConfig;
    private final Collection<String> presentMods;

    public Context(String mixinConfig, Collection<String> presentMods) {
        this.mixinConfig = mixinConfig;
        this.presentMods = presentMods;
    }

    /**
     * @return the current mod loader
     */
    public ModLoader modLoader() {
        return ModLoader.CURRENT;
    }

    /**
     * @return if the current environment is in-dev
     */
    public boolean inDev() {
        return FMLLaunchHandler.isDeobfuscatedEnvironment();
    }

    /**
     * @return name of the mixin config that is currently being processed
     */
    public String mixinConfig() {
        return mixinConfig;
    }

    /**
     * <p>For early contexts, the list of mods are gathered from culling the classloader
     * for any jars that has the mcmod.info file. The mod IDs are obtained from the mcmod.info file.
     * This means mostly, if not only coremods are queryable here,
     * make sure to test a normal mod's existence in your mixin plugin or in the mixin itself.</p>
     *
     * <p>For late contexts, it comes from {@link net.minecraftforge.fml.common.Loader#getActiveModList}
     * akin to {@link net.minecraftforge.fml.common.Loader#isModLoaded(String)}</p>
     * @param modId to check against the list of present mods in the context
     * @return whether the mod is present
     */
    public boolean isModPresent(String modId) {
        return presentMods.contains(modId);
    }

}
