package zone.rong.mixinbooter.service;

import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.service.IClassTracker;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for reflecting into {@link LaunchClassLoader}. We <b>do not write</b> anything of the
 * classloader fields except inserting entries into the invalid classes set (to prevent mixin "classes"
 * from being classloaded). Everything else is read-only validation.
 */
final class LaunchClassLoaderUtil implements IClassTracker {

    private static final String CACHED_CLASSES_FIELD = "cachedClasses";
    private static final String INVALID_CLASSES_FIELD = "invalidClasses";
    private static final String CLASS_LOADER_EXCEPTIONS_FIELD = "classLoaderExceptions";
    private static final String TRANSFORMER_EXCEPTIONS_FIELD = "transformerExceptions";

    private final LaunchClassLoader classLoader;

    private final Map<String, Class<?>> cachedClasses;
    private final Set<String> invalidClasses;
    private final Set<String> classLoaderExceptions;
    private final Set<String> transformerExceptions;

    LaunchClassLoaderUtil(LaunchClassLoader classLoader) {
        this.classLoader = classLoader;
        this.cachedClasses = getField(classLoader, CACHED_CLASSES_FIELD);
        this.invalidClasses = getField(classLoader, INVALID_CLASSES_FIELD);
        this.classLoaderExceptions = getField(classLoader, CLASS_LOADER_EXCEPTIONS_FIELD);
        this.transformerExceptions = getField(classLoader, TRANSFORMER_EXCEPTIONS_FIELD);
    }

    @Override
    public boolean isClassLoaded(String name) {
        return this.cachedClasses != null && this.cachedClasses.containsKey(name);
    }

    @Override
    public String getClassRestrictions(String className) {
        String restrictions = "";
        if (this.isClassClassLoaderExcluded(className, null)) {
            restrictions = "PACKAGE_CLASSLOADER_EXCLUSION";
        }
        if (this.isClassTransformerExcluded(className, null)) {
            restrictions = (!restrictions.isEmpty() ? restrictions + "," : "") + "PACKAGE_TRANSFORMER_EXCLUSION";
        }
        return restrictions;
    }

    /**
     * Append a class name directly into the {@link #invalidClasses} set, preventing the loader from classloading
     * the class. Used by the mixin processor to prevent classloading of mixin classes.
     */
    @Override
    public void registerInvalidClass(String name) {
        if (this.invalidClasses != null) {
            this.invalidClasses.add(name);
        }
    }

    /**
     * Get whether the specified name or transformedName exist in either of the exclusion lists.
     */
    boolean isClassExcluded(String name, String transformedName) {
        return this.isClassClassLoaderExcluded(name, transformedName) || this.isClassTransformerExcluded(name, transformedName);
    }

    private boolean isClassClassLoaderExcluded(String name, String transformedName) {
        for (final String exception : this.getClassLoaderExceptions()) {
            if ((transformedName != null && transformedName.startsWith(exception)) || name.startsWith(exception)) {
                return true;
            }
        }
        return false;
    }

    private boolean isClassTransformerExcluded(String name, String transformedName) {
        for (final String exception : this.getTransformerExceptions()) {
            if ((transformedName != null && transformedName.startsWith(exception)) || name.startsWith(exception)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> getClassLoaderExceptions() {
        return this.classLoaderExceptions != null ? this.classLoaderExceptions : Collections.<String>emptySet();
    }

    private Set<String> getTransformerExceptions() {
        return this.transformerExceptions != null ? this.transformerExceptions : Collections.<String>emptySet();
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(LaunchClassLoader classLoader, String fieldName) {
        try {
            Field field = LaunchClassLoader.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(classLoader);
        } catch (Exception ex) {
            return null;
        }
    }

}
