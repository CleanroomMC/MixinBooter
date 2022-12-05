package zone.rong.mixinbooter.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Apply this annotation to a class or method, its {@link MixinMessage#value()} will be printed if in a stack trace if its detected
 * <p>
 * For this annotation to work you must implement the {@link zone.rong.mixinbooter.IMixinLogGenerator} interface
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface MixinMessage {
    String value();
}
