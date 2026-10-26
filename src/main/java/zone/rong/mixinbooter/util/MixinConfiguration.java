package zone.rong.mixinbooter.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds a mixin configuration when the annotated field of a Forge {@code @Config} class is {@code true}.
 * It is the same as {@link ConditionalMixin#option(Class, String)} followed by {@link ConditionalMixin#requires(String)}
 * for each dependency. Needs Minecraft 1.11.2 or newer.
 * <p>
 * Only jars that depend on MixinBooter through {@code @Mod(dependencies = ...)} or {@code mcmod.info} are searched
 * past their {@code @Mod} class.
 * <p>
 * For example, in a {@code @Config(modid = "mymod")} class:
 * <pre>{@code
 * @MixinConfiguration(name = "mixins.mymod.jei.json", depends = @Dependency(modid = "jei", version = "[4.16,)"))
 * public static boolean jeiIntegration = true;
 * }</pre>
 *
 * @since 12.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MixinConfiguration {

    /**
     * @return the mixin configuration to add
     */
    String name();

    Dependency[] depends() default { };

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ })
    @interface Dependency {

        String modid();

        /**
         * @return a range in the same format as {@code @Mod(dependencies = ...)} such as {@code [1.2,2.0)}, or empty for any version
         */
        String version() default "";

        /**
         * @return whether a missing mod crashes the game, otherwise the configuration is skipped with a warning
         */
        boolean crash() default true;

        /**
         * @return why the mod is needed, shown in the log
         */
        String reason() default "";

    }

}
