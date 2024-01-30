package zone.rong.mixinbooter;

import java.util.Set;

/**
 * Hijackers are used to stop certain mixin configurations from ever being applied.
 * Usage is similar to {@link IEarlyMixinLoader}, implement it in your coremod class.
 * Requested by: @Desoroxxx
 *
 * @since 9.0
 */
public interface IMixinConfigHijacker {

    Set<String> getHijackedMixinConfigs();

}
