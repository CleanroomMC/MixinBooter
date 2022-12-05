package zone.rong.mixinbooter;

import zone.rong.mixinbooter.annotations.MixinMessage;

import java.util.List;


/**
 * Implement this interface alongside {@link IEarlyMixinLoader} or {@link ILateMixinLoader}.
 * If it's a {@link IEarlyMixinLoader} it must be implemented with a coremod, if it's a {@link ILateMixinLoader} any call will surface al long a core mod is present
 * <p>
 * This interface needs to be implemented for the {@link MixinMessage} annotation to work
 */
public interface IMixinLogGenerator {

    /**
     * @return mixin configurations to be queued and sent to Mixin library.
     */
    List<String> getMixinConfigs();

    /**
     * Runs when a crash is generated.
     *
     * @param mixinConfig mixin config name, queried via {@link IMixinLogGenerator#getMixinConfigs()}.
     * @return true if a custom message for that mixinConfig should be included in the log.
     */
    default boolean shouldMixinReportJsonMessage(String mixinConfig) {
        return false;
    }

    /**
     * @param mixinConfig mixin config name, query if {@link IMixinLogGenerator#shouldMixinReportJsonMessage(String mixinConfig)} returns true.
     * @return The custom message for that mixinConfig
     */
    default String onMixinMessage(String mixinConfig) {
        return "No custom message was implemented for " + mixinConfig;
    }

}
