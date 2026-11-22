package zone.rong.mixinbooter.service;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;
import zone.rong.mixinbooter.Tags;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * Advances the mixin environment to {@link org.spongepowered.asm.mixin.MixinEnvironment.Phase#INIT INIT} at the
 * one point in the boot where it is both safe and complete to do so, which is inside
 * {@code FMLDeobfTweaker#injectIntoClassLoader}, after {@code DeobfuscationTransformer} and the access transformers
 * have been registered, and before {@code net.minecraftforge.fml.common.Loader} is loaded and initialized.
 */
public final class InitPhaseTrigger implements InvocationHandler {

    private static final String DEOBF_TWEAKER = "net.minecraftforge.fml.common.launcher.FMLDeobfTweaker";
    private static final String FML_LOG = "net.minecraftforge.fml.common.FMLLog";
    // Forge before 1.11.2 logs it through FMLRelaunchLog.fine, which uses FMLRelaunchLog#log's own logger
    private static final String RELAUNCH_LOG = "net.minecraftforge.fml.relauncher.FMLRelaunchLog";
    private static final String MESSAGE = "Validating minecraft";

    private static InitPhaseTrigger installed;

    private final Field field;
    private final Object owner;
    private final Logger delegate;
    private final String target;

    private boolean fired;

    private InitPhaseTrigger(Field field, Object owner, Logger delegate, String target) {
        this.field = field;
        this.owner = owner;
        this.delegate = delegate;
        this.target = target;
    }

    static void install() {
        if (installed != null) {
            return;
        }
        String target = FML_LOG + "#log";
        try {
            ClassLoader loader = Launch.classLoader.loadClass(DEOBF_TWEAKER).getClassLoader();
            Field field;
            Object owner = null;
            try {
                field = Class.forName(FML_LOG, true, loader).getDeclaredField("log");
                Field modifiers = Field.class.getDeclaredField("modifiers");
                modifiers.setAccessible(true);
                modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            } catch (NoSuchFieldException e) {
                target = RELAUNCH_LOG + "#log.myLog";
                Class<?> relaunchLog = Class.forName(RELAUNCH_LOG, true, loader);
                owner = relaunchLog.getField("log").get(null);
                field = relaunchLog.getDeclaredField("myLog");
            }
            field.setAccessible(true);

            Logger delegate = (Logger) field.get(owner);
            if (delegate == null) {
                logger().warn("'{}' is null, the INIT phase will not begin until the DEFAULT transition.", target);
                return;
            }
            InitPhaseTrigger trigger = new InitPhaseTrigger(field, owner, delegate, target);
            set(field, owner, Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[] { Logger.class }, trigger));
            installed = trigger;
            logger().debug("Hooked '{}' as defined by {}, the INIT phase will begin within FMLDeobfTweaker.", target, loader);
        } catch (Throwable t) {
            logger().warn("Unable to hook '{}', the INIT phase will not begin until the DEFAULT transition.", target, t);
        }
    }

    static void uninstall() {
        InitPhaseTrigger trigger = installed;
        if (trigger == null) {
            return;
        }
        installed = null;
        if (!trigger.fired) {
            logger().warn("'{}' was never seen on '{}' ({}), so INIT-phase configs were only staged at the DEFAULT transition",
                    MESSAGE, trigger.target, trigger.delegate.getClass().getName());
        }
        try {
            set(trigger.field, trigger.owner, trigger.delegate);
        } catch (Throwable t) {
            logger().warn("Unable to restore '{}', FML logging remains proxied.", trigger.target, t);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // debug(message) from 1.11.2 on, log(Level.DEBUG, message) before
        if (!this.fired && args != null && (args.length > 0 && MESSAGE.equals(args[0]) || args.length > 1 && MESSAGE.equals(args[1]))) {
            this.fired = true;
            IMixinService service = MixinService.getService();
            if (service instanceof MixinBooterService) {
                logger().debug("'{}' reached, advancing the mixin environment to INIT.", MESSAGE);
                ((MixinBooterService) service).gotoInitPhase();
            }
        }
        try {
            return method.invoke(this.delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static void set(Field field, Object owner, Object value) throws Throwable {
        field.set(owner, value);
        if (field.get(owner) != value) {
            throw new IllegalStateException("Unable to write '" + field.getDeclaringClass().getName() + "#" + field.getName() + "'");
        }
    }

    private static ILogger logger() {
        return MixinService.getService().getLogger(Tags.MOD_NAME);
    }

}
