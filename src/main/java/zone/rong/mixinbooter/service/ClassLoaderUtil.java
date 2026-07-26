package zone.rong.mixinbooter.service;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.service.IClassTracker;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

final class ClassLoaderUtil implements IClassTracker {

    private static final String CACHED_CLASSES_FIELD = "cachedClasses";
    private static final String INVALID_CLASSES_FIELD = "invalidClasses";
    private static final String CLASS_LOADER_EXCEPTIONS_FIELD = "classLoaderExceptions";
    private static final String TRANSFORMER_EXCEPTIONS_FIELD = "transformerExceptions";

    private final Map<String, Class<?>> cachedClasses;
    private final Set<String> invalidClasses, classLoaderExceptions, transformerExceptions;

    ClassLoaderUtil() {
        this.cachedClasses = getField(CACHED_CLASSES_FIELD);
        this.invalidClasses = getField(INVALID_CLASSES_FIELD);
        this.classLoaderExceptions = getField(CLASS_LOADER_EXCEPTIONS_FIELD);
        this.transformerExceptions = getField(TRANSFORMER_EXCEPTIONS_FIELD);
    }

    @Override
    public boolean isClassLoaded(String name) {
        return this.cachedClasses.containsKey(name);
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

    @Override
    public void registerInvalidClass(String name) {
        this.invalidClasses.add(name);
    }

    /** Whether the name or transformedName appears in either exclusion list. */
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
    private static <T> T getField(String fieldName) {
        try {
            return (T) LaunchClassLoader.class.getDeclaredField(fieldName).get(Launch.classLoader);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to reflect into LaunchClassLoader", e);
        }
    }

}
