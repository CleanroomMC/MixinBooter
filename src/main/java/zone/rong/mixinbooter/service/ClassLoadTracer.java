package zone.rong.mixinbooter.service;

import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.logging.Level;
import zone.rong.mixinbooter.util.MixinBooterLogFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Debug transformer that records the stack which triggered the load of one or more watched classes.
 * Writing each captured stack to {@code logs/mixinbooter.log}. This helps diagnose
 * "mixin target was loaded too early" issues. As once a class is defined, its mixins can no longer apply,
 * and by the time the mixin subsystem notices, the causal stack is gone.
 * <p>
 * Watched class names are supplied through the {@code mixinbooter.watchedClasses} system property
 * (comma-separated), populated from the {@code debug.watchedClasses} option in {@code config/mixinbooter.cfg}.
 * The single token {@code *} enables "watch all" mode, tracing the load of every class (very verbose, each class
 * traced once). The transformer is inert (a single property read per class) until the property is set.</p>
 */
public final class ClassLoadTracer implements IClassTransformer {

    public static final String WATCH_PROPERTY = "mixinbooter.watchedClasses";
    public static final String WATCH_ALL = "*";

    private static volatile Set<String> watched;

    private final Set<String> traced = Collections.synchronizedSet(new HashSet<>());

    private static Set<String> watched() {
        Set<String> current = watched;
        if (current != null) {
            return current;
        }
        String property = System.getProperty(WATCH_PROPERTY);
        if (property == null || property.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> parsed = new HashSet<>();
        for (String name : property.split(",")) {
            name = name.trim();
            if (!name.isEmpty()) {
                parsed.add(name);
            }
        }
        watched = parsed;
        return parsed;
    }

    /**
     * Trims the captured stack to the interesting portion.
     * Drops the first 5 frames (transformer + classloader find/loading)
     * Stopping at the LaunchWrapper/Minecraft entrypoint.
     */
    private static StackTraceElement[] trim(StackTraceElement[] elements) {
        int start = Math.min(5, elements.length);
        int end = elements.length;
        for (int i = start; i < elements.length; i++) {
            if (isEntryPoint(elements[i])) {
                end = i;
                break;
            }
        }
        List<StackTraceElement> kept = new ArrayList<>();
        for (int i = start; i < end; i++) {
            if (!isInternalCall(elements[i])) {
                kept.add(elements[i]);
            }
        }
        return kept.toArray(new StackTraceElement[0]);
    }

    private static boolean isEntryPoint(StackTraceElement element) {
        String className = element.getClassName();
        String methodName = element.getMethodName();
        return ("net.minecraft.launchwrapper.Launch".equals(className) && "launch".equals(methodName))
                || ("net.minecraft.client.main.Main".equals(className) && "main".equals(methodName));
    }

    private static boolean isInternalCall(StackTraceElement element) {
        String className = element.getClassName();
        String methodName = element.getMethodName();
        if ("java.lang.ClassLoader".equals(className) || "java.security.SecureClassLoader".equals(className)
                || "sun.reflect.NativeMethodAccessorImpl".equals(className)
                || "sun.reflect.DelegatingMethodAccessorImpl".equals(className)) {
            return true;
        }
        return "net.minecraft.launchwrapper.LaunchClassLoader".equals(className) &&
                ("findClass".equals(methodName) || "loadClass".equals(methodName));
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        Set<String> watchedClasses = watched();
        if (!watchedClasses.isEmpty()) {
            String target;
            if (watchedClasses.contains(WATCH_ALL)) {
                target = transformedName != null ? transformedName : name;
            } else {
                target = watchedClasses.contains(transformedName) ? transformedName : (watchedClasses.contains(name) ? name : null);
            }
            if (target != null && this.traced.add(target)) {
                Throwable trace = new Throwable(target);
                trace.setStackTrace(trim(trace.getStackTrace()));
                MixinBooterLogFile.get().write(Level.DEBUG, "ClassLoadTracer", "'" + target + "' is being loaded, load stack:", trace);
            }
        }
        return basicClass;
    }

}
