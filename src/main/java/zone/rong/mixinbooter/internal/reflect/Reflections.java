package zone.rong.mixinbooter.internal.reflect;

import net.minecraftforge.fml.relauncher.CoreModManager;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import zone.rong.mixinbooter.internal.MixinBooterPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Reflections {
    private static RuntimeException reflectionErrors;
    private static Method classInfo$getMixins;
    private static Field coreModManager$loadPlugins;
    private static Field coreModManager$coreModInstance;
    private static Field annotationInvocationHandler$memberValues;

    static {
        try {
            classInfo$getMixins = ClassInfo.class.getDeclaredMethod("getMixins");
            classInfo$getMixins.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            addSuppressedExceptions("Not able to reflect ClassInfo#getMixins", e);
            MixinBooterPlugin.LOGGER.error("Not able to reflect ClassInfo#getMixins");
        }

        try {
            coreModManager$loadPlugins = CoreModManager.class.getDeclaredField("loadPlugins");
            coreModManager$loadPlugins.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            addSuppressedExceptions("Not able to reflect coreModManager#loadPlugins", e);
            MixinBooterPlugin.LOGGER.error("Not able to reflect coreModManager#loadPlugins");
        }

        try {
            coreModManager$coreModInstance = CoreModManager.class.getDeclaredClasses()[0].getDeclaredField("coreModInstance");
            coreModManager$coreModInstance.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            addSuppressedExceptions("Not able to reflect FMLPluginWrapper#coreModInstance", e);
            MixinBooterPlugin.LOGGER.error("Not able to reflect FMLPluginWrapper#coreModInstance");
        }

        try {
            //this is used since im not able to get method.getAnnotation(MixinMessage.class) to work
            annotationInvocationHandler$memberValues = Class.forName("sun.reflect.annotation.AnnotationInvocationHandler").getDeclaredField("memberValues");
            annotationInvocationHandler$memberValues.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            addSuppressedExceptions("Not able to reflect annotationInvocationHandler#memberValues", e);
            MixinBooterPlugin.LOGGER.error("Not able to reflect annotationInvocationHandler#memberValues");
        }

        if (reflectionErrors != null) {
            throw reflectionErrors;
        }
    }

    private static void addSuppressedExceptions(String message, Throwable throwable) {
        if (reflectionErrors == null) {
            reflectionErrors = new RuntimeException("(MixinBooter) errors while reflecting:");
        }
        reflectionErrors.addSuppressed(new RuntimeException(message, throwable));
    }

    public static Object reflectClassInfo$getMixins(Object obj) throws InvocationTargetException {
        try {
            return classInfo$getMixins.invoke(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
    public static Object reflectCoreModManager$loadPlugins(Object obj) {
        try {
            return coreModManager$loadPlugins.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
    public static Object reflectCoreModManager$coreModInstance(Object obj) {
        try {
            return coreModManager$coreModInstance.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
    public static Object reflectAnnotationInvocationHandler$memberValues(Object obj) {
        try {
            return annotationInvocationHandler$memberValues.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
