package zone.rong.mixinbooter.fb;

import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.service.MixinService;
import zone.rong.mixinbooter.Tags;
import zone.rong.mixinbooter.service.ModDiscoverer;
import zone.rong.mixinbooter.util.ConditionalMixin;
import zone.rong.mixinbooter.util.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// Unlike FB, which was always active, nothing here takes effect unless a mod depends on it
public final class FBMod {

    public static final String ID = "fermiumbooter";
    public static final String API_VERSION = "1.6.3";

    // FB only exists for 1.12.2
    private static final boolean AVAILABLE = "1.12.2".equals(Environment.minecraftVersion());

    private static final String CONFIG_CLASS = "Lfermiumbooter/annotations/MixinConfig";
    private static final String CONFIG_DESC = CONFIG_CLASS + ";";
    private static final String TOGGLE_DESC = CONFIG_CLASS + "$MixinToggle;";
    private static final String COMPAT_DESC = CONFIG_CLASS + "$CompatHandling;";
    private static final String COMPAT_CONTAINER_DESC = CONFIG_CLASS + "$CompatHandlingContainer;";
    private static final String CONFIG_NAME_DESC = "Lnet/minecraftforge/common/config/Config$Name;";
    private static final byte[] CONFIG_BYTES = CONFIG_DESC.getBytes(StandardCharsets.UTF_8);

    private static final Set<File> dependents = new LinkedHashSet<>();
    private static final List<File> toggleJars = new ArrayList<>();
    private static final Map<String, List<Supplier<Boolean>>> earlyQueue = new LinkedHashMap<>();
    private static final Map<String, List<Supplier<Boolean>>> lateQueue = new LinkedHashMap<>();
    private static final List<Toggle> toggles = new ArrayList<>();
    private static final List<String> classloadEarly = new ArrayList<>();
    private static final List<String> removals = new ArrayList<>();
    // Mods may toggle a configuration and enqueue it too, either one adds it
    private static final Set<String> toggled = new HashSet<>();

    private static boolean apiUsed, decided, earlyApplied, lateApplied;

    private FBMod() { }

    public static boolean isEnabled() {
        return AVAILABLE && (!dependents.isEmpty() || apiUsed);
    }

    public static void enqueue(boolean late, String configuration, Supplier<Boolean> supplier) {
        if (configuration == null || configuration.trim().isEmpty() || supplier == null) {
            logger().error("Ignoring mixin configuration [{}] as it or its supplier is missing.", configuration);
            return;
        }
        markApiUsed();
        if (decided && !isEnabled()) {
            logger().warn("Ignoring mixin configuration [{}] as no mod depends on {}.", configuration, ID);
            return;
        }
        if (late ? lateApplied : earlyApplied) {
            apply(configuration, Collections.singletonList(supplier));
        } else {
            (late ? lateQueue : earlyQueue).computeIfAbsent(configuration, k -> new ArrayList<>()).add(supplier);
        }
    }

    public static void remove(String configuration) {
        if (configuration == null || configuration.trim().isEmpty()) {
            return;
        }
        markApiUsed();
        if (!decided) {
            removals.add(configuration);
        } else if (isEnabled()) {
            logger().info("Blacklisting {} as it was requested to be removed.", configuration);
            Config.blacklist(configuration);
        }
    }

    // FB added every mod jar to the classloader so early mixins could target mod classes.
    // CleanMix only resolves targets once they are transformed, so that is not needed here.
    public static void applyEarly() {
        boolean superseded = AVAILABLE && ModDiscoverer.isModPresent(ID);
        if (superseded) {
            logger().error("{} is superseded by {} and has to be removed.", ModDiscoverer.getModSources(ID), Tags.MOD_NAME);
        }
        if (AVAILABLE) {
            scan();
        }
        decided = true;
        if (isEnabled() || superseded) {
            FMLInjectionData.containers.add(FBModContainer.class.getName());
        }
        if (!isEnabled()) {
            return;
        }
        logger().info("Enabling {} support, depended on by {}{}.", ID, dependents.stream()
                        .map(File::getName)
                        .collect(Collectors.toList()), apiUsed ? " and coremods calling its API" : "");
        for (String configuration : removals) {
            logger().info("Blacklisting {} as it was requested to be removed.", configuration);
            Config.blacklist(configuration);
        }
        for (File jar : toggleJars) {
            ModDiscoverer.addToClassLoader(jar);
        }
        for (String className : classloadEarly) {
            try {
                Class.forName(className, true, Launch.classLoader);
            } catch (Throwable t) {
                logger().error("Failed to load {} early.", className, t);
            }
        }
        for (Toggle toggle : toggles) {
            toggle.register();
        }
        drain(earlyQueue);
        earlyApplied = true;
    }

    public static void applyLate() {
        if (isEnabled()) {
            drain(lateQueue);
            lateApplied = true;
        }
    }

    // FB reads every class of every jar for @MixinConfig
    // Here only jars depending on it are read, and coremods, as coremods using its API do not always declare the dependency
    private static void scan() {
        for (Map.Entry<File, Boolean> entry : ModDiscoverer.coremodsAndDependents(ID).entrySet()) {
            File source = entry.getKey();
            List<Toggle> sourceToggles = new ArrayList<>();
            List<String> sourceClassloadEarly = new ArrayList<>();
            try (JarFile jar = new JarFile(source)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry classEntry = entries.nextElement();
                    if (classEntry.isDirectory() || !classEntry.getName().endsWith(".class")) {
                        continue;
                    }
                    byte[] bytes;
                    try (InputStream in = jar.getInputStream(classEntry)) {
                        bytes = ByteStreams.toByteArray(in);
                    }
                    if (Bytes.indexOf(bytes, CONFIG_BYTES) != -1) {
                        ClassScanner scanner = new ClassScanner();
                        new ClassReader(bytes).accept(scanner, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        scanner.record(sourceToggles, sourceClassloadEarly);
                    }
                }
            } catch (IOException e) {
                logger().error("Failed to read {} for its mixin toggles.", source.getName(), e);
                continue;
            }
            if (entry.getValue() || !sourceToggles.isEmpty() || !sourceClassloadEarly.isEmpty()) {
                dependents.add(source);
                if (!sourceToggles.isEmpty() || !sourceClassloadEarly.isEmpty()) {
                    toggleJars.add(source);
                }
                toggles.addAll(sourceToggles);
                classloadEarly.addAll(sourceClassloadEarly);
            }
        }
    }

    private static void drain(Map<String, List<Supplier<Boolean>>> queue) {
        for (Map.Entry<String, List<Supplier<Boolean>>> entry : queue.entrySet()) {
            apply(entry.getKey(), entry.getValue());
        }
        queue.clear();
    }

    private static void apply(String configuration, List<Supplier<Boolean>> suppliers) {
        if (toggled.contains(configuration)) {
            return;
        }
        boolean enabled = false;
        for (Supplier<Boolean> supplier : suppliers) {
            try {
                enabled |= Boolean.TRUE.equals(supplier.get());
            } catch (Throwable t) {
                logger().error("Failed to evaluate whether [{}] should be added.", configuration, t);
            }
        }
        ConditionalMixin.of(enabled, configuration).register();
    }

    // Calls made before applyEarly decides come from coremods which do not always declare the dependency
    private static void markApiUsed() {
        if (!decided) {
            apiUsed = true;
        }
    }

    private static ILogger logger() {
        return MixinService.getService().getLogger(Tags.MOD_NAME + "|FBAPI");
    }

    private static final class Toggle {

        private final String config, option;
        private final Map<String, Object> toggle;
        private final List<Map<String, Object>> compat;

        private Toggle(String config, String option, Map<String, Object> toggle, List<Map<String, Object>> compat) {
            this.config = config;
            this.option = option;
            this.toggle = toggle;
            this.compat = compat;
        }

        // lateMixin is added with the rest, as CleanMix resolves mixin targets when they load
        private void register() {
            List<String> configurations = new ArrayList<>();
            for (String key : new String[] { "mixinJson", "earlyMixin", "lateMixin" }) {
                String configuration = string(this.toggle, key);
                if (configuration != null) {
                    configurations.add(configuration);
                }
            }
            if (configurations.isEmpty()) {
                return;
            }
            ConditionalMixin conditional = ConditionalMixin.of(configurations.toArray(new String[0]))
                    .option(this.config, this.option, Boolean.TRUE.equals(this.toggle.get("defaultValue")));
            for (Map<String, Object> mod : this.compat) {
                String modId = string(mod, "modid");
                if (modId == null) {
                    continue;
                }
                boolean desired = !Boolean.FALSE.equals(mod.get("desired"));
                boolean disables = !Boolean.FALSE.equals(mod.get("disableMixin"));
                if (desired) {
                    conditional = disables ? conditional.requires(modId) : conditional.recommends(modId);
                } else {
                    conditional = disables ? conditional.conflicts(modId) : conditional.discourages(modId);
                }
                String name = string(mod, "modName");
                String range = string(mod, "targetVersionRange");
                String reason = string(mod, "reason");
                if (name != null) {
                    conditional.named(name);
                }
                if (range != null) {
                    try {
                        conditional.version(range);
                    } catch (IllegalArgumentException ignored) {
                        // FB ignores invalid ranges instead of counting them as a mismatch
                    }
                }
                if (reason != null) {
                    conditional.because(reason);
                }
            }
            if (conditional.register()) {
                toggled.addAll(configurations);
            }
        }

        private static String string(Map<String, Object> values, String key) {
            Object value = values.get(key);
            return value instanceof String && !((String) value).isEmpty() ? (String) value : null;
        }

    }

    /**
     * Reads a {@code @MixinConfig} class's toggles. Annotation elements are always compile-time constants,
     * so values are read straight from the bytecode without loading the class.
     */
    private static final class ClassScanner extends ClassVisitor {

        private final List<ToggleField> fields = new ArrayList<>();

        private String className;
        private AnnotationValues mixinConfig;

        private ClassScanner() {
            super(Opcodes.ASM5);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name.replace('/', '.');
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return CONFIG_DESC.equals(descriptor) ? this.mixinConfig = new AnnotationValues(null) : null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            // Class annotations are visited before fields
            if (this.mixinConfig == null) {
                return null;
            }
            ToggleField field = new ToggleField(name);
            this.fields.add(field);
            return field;
        }

        private void record(List<Toggle> toggles, List<String> classloadEarly) {
            String config = this.mixinConfig == null ? null : this.mixinConfig.string("name");
            if (config == null) {
                return;
            }
            if (Boolean.TRUE.equals(this.mixinConfig.values.get("classloadEarly"))) {
                classloadEarly.add(this.className);
            }
            for (ToggleField field : this.fields) {
                if (field.toggle != null) {
                    String option = field.configName == null ? null : field.configName.string("value");
                    toggles.add(new Toggle(config, option == null ? field.name : option, field.toggle.values, field.compat));
                }
            }
        }

    }

    private static final class ToggleField extends FieldVisitor {

        private final String name;
        private final List<Map<String, Object>> compat = new ArrayList<>();

        private AnnotationValues configName, toggle;

        private ToggleField(String name) {
            super(Opcodes.ASM5);
            this.name = name;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            switch (descriptor) {
                case CONFIG_NAME_DESC:
                    return this.configName = new AnnotationValues(null);
                case TOGGLE_DESC:
                    return this.toggle = new AnnotationValues(null);
                case COMPAT_DESC:
                    return AnnotationValues.compat(this.compat);
                case COMPAT_CONTAINER_DESC:
                    return new AnnotationValues(this.compat);
                default:
                    return null;
            }
        }

    }

    // When given a compat list
    // Repeated @CompatHandling annotations nested in their container's array are collected into it
    private static final class AnnotationValues extends AnnotationVisitor {

        private final Map<String, Object> values = new HashMap<>();
        private final List<Map<String, Object>> compat;

        private AnnotationValues(List<Map<String, Object>> compat) {
            super(Opcodes.ASM5);
            this.compat = compat;
        }

        private static AnnotationValues compat(List<Map<String, Object>> compat) {
            AnnotationValues values = new AnnotationValues(compat);
            compat.add(values.values);
            return values;
        }

        @Override
        public void visit(String name, Object value) {
            this.values.put(name, value);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            return this.compat != null && COMPAT_DESC.equals(descriptor) ? compat(this.compat) : null;
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            return this.compat == null ? null : new AnnotationValues(this.compat);
        }

        private String string(String key) {
            Object value = this.values.get(key);
            return value instanceof String && !((String) value).isEmpty() ? (String) value : null;
        }

    }

}
