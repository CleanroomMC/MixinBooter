package zone.rong.mixinbooter.internal.stacktrace;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import zone.rong.mixinbooter.IMixinLogGenerator;
import zone.rong.mixinbooter.IMixinStack;
import zone.rong.mixinbooter.annotations.MixinMessage;
import zone.rong.mixinbooter.internal.MixinBooterPlugin;
import zone.rong.mixinbooter.internal.reflect.Reflections;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.stream.Collectors;

public class MixinStack implements IMixinStack {
    private String errorMessage;
    private String stackMessage;

    //map kay class found in mixin cache, map value source mixins
    private Map<String, Set<IMixinInfo>> mixinStack;
    //source mixins
    private List<IMixinInfo> flatMixins;

    private static final Set<IMixinLogGenerator> iLateMixinsLogRegisteredCallbacks = new HashSet<>();

    public static void registerILateMixinsLogCallback(IMixinLogGenerator callback) {
        iLateMixinsLogRegisteredCallbacks.add(callback);
    }

    public MixinStack(StackTraceElement[] stacktrace) {
        if (stacktrace.length == 0) {
            errorMessage = "\n(MixinBooter) Failed to find Mixin Metadata because the provided Stacktrace is empty\n";
            return;
        }

        mixinStack = new HashMap<>();
        Set<String> classes = new ObjectOpenHashSet<>();
        for (StackTraceElement stackTraceElement : stacktrace) {
            classes.add(stackTraceElement.getClassName());
        }
        try {
            for (String className : classes) {
                ClassInfo classInfo = ClassInfo.fromCache(className);
                if (classInfo != null) {
                    @SuppressWarnings("unchecked")
                    Set<IMixinInfo> mixinInfos = (Set<IMixinInfo>) Reflections.reflectClassInfo$getMixins(classInfo);
                    if (!mixinInfos.isEmpty()) {
                        mixinStack.putIfAbsent(className, mixinInfos);
                    }
                }
            }

            if (mixinStack.size() == 0) {
                errorMessage = "\n(MixinBooter) No Mixin Metadata is found in the Stacktrace.\n";
            }

        } catch (Throwable t) {
            errorMessage = "\n(MixinBooter) Failed to find Mixin Metadata in Stacktrace: " + t + "\n";
            MixinBooterPlugin.LOGGER.error("Failed to find Mixin Metadata in Stacktrace: " + t);
        }
    }

    private void indentBuilder(StringBuilder tabBuilder, int indent) {
        for (int i = 0; i < indent; i++) {
            tabBuilder.append("\t");
        }
    }

    //Formats the target class found in the stacktrace
    private void formatStackTraceTargetClass(StringBuilder mixinMetadataBuilder, int indent, String mixinClass) {
        mixinMetadataBuilder.append("\n");
        indentBuilder(mixinMetadataBuilder, indent);
        mixinMetadataBuilder.append(mixinClass);
        mixinMetadataBuilder.append(":");
    }

    //Formats the source mixin class found in the stacktrace
    private void formatStackTraceMixinsClass(StringBuilder mixinMetadataBuilder, int indent, IMixinInfo mixinInfo) {
        mixinMetadataBuilder.append("\n");
        indentBuilder(mixinMetadataBuilder, indent);
        mixinMetadataBuilder.append(mixinInfo.getClassName());
        mixinMetadataBuilder.append(" (");
        mixinMetadataBuilder.append(mixinInfo.getConfig().getName());
        mixinMetadataBuilder.append(")");
    }

    //If a custom message for the specified mixin json should be processed
    private boolean shouldFormatJsonMessages(IMixinLogGenerator callback, String mixinJson) {
        return callback.shouldMixinReportJsonMessage(mixinJson);
    }

    //Processing the custom messages
    private void formatJsonMessages(StringBuilder mixinMessageBuilder, int indent, String mixinJson, IMixinLogGenerator callback) {
        mixinMessageBuilder.append("\n");
        indentBuilder(mixinMessageBuilder, indent);
        mixinMessageBuilder.append(callback.onMixinMessage(mixinJson));
    }

    //Formats @MixinMessage class annotations
    private void formatCustomClassAnnotation(StringBuilder mixinMessageBuilder, int indent, IMixinInfo mixinInfo) {
        try {
            Class<?> clazz = Class.forName(mixinInfo.getClassName(), false, ClassLoader.getSystemClassLoader());
            for (Annotation annotation : clazz.getDeclaredAnnotations()) {
                if (annotation.annotationType().getName().equals(MixinMessage.class.getName())) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Map<String, Object> annotationMap = (Map) Reflections.reflectAnnotationInvocationHandler$memberValues(Proxy.getInvocationHandler(annotation));
                    for (Map.Entry<String, Object> mapEntry : annotationMap.entrySet()) {
                        if (mapEntry.getKey().equals("value")) {
                            mixinMessageBuilder.append("\n");
                            indentBuilder(mixinMessageBuilder, indent);
                            mixinMessageBuilder.append("Class Annotation Message: ");
                            mixinMessageBuilder.append(mapEntry.getValue());
                        }
                    }
                }
            }
        } catch (Throwable e) {
            mixinMessageBuilder.append("\n");
            indentBuilder(mixinMessageBuilder, indent);
            mixinMessageBuilder.append("Unexpected error while generating Class Annotation Message");
            MixinBooterPlugin.LOGGER.error("Unexpected error while generating custom mixin messages: " + e);
        }
    }

    //Formats @MixinMessage method annotations
    private void formatCustomMethodAnnotation(StringBuilder mixinMessageBuilder, int indent, IMixinInfo mixinInfo) {
        try {
            Class<?> clazz = Class.forName(mixinInfo.getClassName(), false, ClassLoader.getSystemClassLoader());
            for (Method method : clazz.getMethods()) {
                for (Annotation annotation : method.getDeclaredAnnotations()) {
                    if (annotation.annotationType().getName().equals(MixinMessage.class.getName())) {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        Map<String, Object> annotationMap = (Map) Reflections.reflectAnnotationInvocationHandler$memberValues(Proxy.getInvocationHandler(annotation));
                        for (Map.Entry<String, Object> mapEntry : annotationMap.entrySet()) {
                            if (mapEntry.getKey().equals("value")) {
                                mixinMessageBuilder.append("\n");
                                indentBuilder(mixinMessageBuilder, indent);
                                mixinMessageBuilder.append("Method Annotation Message: ");
                                mixinMessageBuilder.append(mapEntry.getValue());
                                mixinMessageBuilder.append(" (");
                                mixinMessageBuilder.append("Method: ");
                                mixinMessageBuilder.append(method.getName());
                                mixinMessageBuilder.append(")");
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            mixinMessageBuilder.append("\n");
            indentBuilder(mixinMessageBuilder, indent);
            mixinMessageBuilder.append("Unexpected error while generating Method Annotation Message");
            MixinBooterPlugin.LOGGER.error("Unexpected error while generating custom mixin messages: " + e);
        }
    }

    //Searches classes implementing IMixinLogGenerator
    private Set<IMixinLogGenerator> findCallbacks(List<IMixinInfo> mixinInfoCollection) {
        Set<IMixinLogGenerator> iMixinLogGeneratorCallback = new HashSet<>();

        //Searching for IEarlyMixinLoader callbacks
        for (Object coreModWrapper : (List) Reflections.reflectCoreModManager$loadPlugins(this)) {
            Object coreMod = Reflections.reflectCoreModManager$coreModInstance(coreModWrapper);

            if (coreMod instanceof IMixinLogGenerator) {
                IMixinLogGenerator coreModLogger = (IMixinLogGenerator) coreMod;

                for (IMixinInfo mixins : mixinInfoCollection) {
                    for (String mixinJson : coreModLogger.getMixinConfigs()) {
                        if (mixins.getConfig().getName().equals(mixinJson)) {
                            iMixinLogGeneratorCallback.add(coreModLogger);
                        }
                    }
                }
            }
        }

        //Searching for ILateMixinLoader callbacks
        for (IMixinLogGenerator logGenerator : iLateMixinsLogRegisteredCallbacks) {
            for (IMixinInfo mixins : mixinInfoCollection) {
                for (String mixinJson : logGenerator.getMixinConfigs()) {
                    if (mixins.getConfig().getName().equals(mixinJson)) {
                        iMixinLogGeneratorCallback.add(logGenerator);
                    }
                }
            }
        }

        return iMixinLogGeneratorCallback;
    }

    private List<IMixinInfo> getFlatMixins() {
        if (flatMixins == null) {
            flatMixins = mixinStack.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        }
        return flatMixins;
    }

    private String formatStackMessage() {
        if (stackMessage != null) {
            return stackMessage;
        }

        StringBuilder mixinLogBuilder = new StringBuilder();
        Set<IMixinLogGenerator> callbacks = findCallbacks(getFlatMixins());

        if (callbacks.isEmpty()) {
            //Formatting basic log
            for (Map.Entry<String, Set<IMixinInfo>> stack : mixinStack.entrySet()) {
                formatStackTraceTargetClass(mixinLogBuilder, 1, stack.getKey());

                for (IMixinInfo mixin : stack.getValue()) {
                    formatStackTraceMixinsClass(mixinLogBuilder, 2, mixin);
                }
            }
        } else {
            //Formatting basic log and annotations
            for (Map.Entry<String, Set<IMixinInfo>> stack : mixinStack.entrySet()) {
                formatStackTraceTargetClass(mixinLogBuilder, 1, stack.getKey());

                for (IMixinInfo mixin : stack.getValue()) {
                    formatCustomClassAnnotation(mixinLogBuilder, 2, mixin);
                    formatStackTraceMixinsClass(mixinLogBuilder, 2, mixin);
                    formatCustomMethodAnnotation(mixinLogBuilder, 3, mixin);
                }
                mixinLogBuilder.append("\n");
            }

            //Formatting for custom mixin json messages
            for (Map.Entry<String, Set<IMixinInfo>> stack : mixinStack.entrySet()) {
                for (IMixinInfo mixin : stack.getValue()) {
                    for (IMixinLogGenerator callback : callbacks) {
                        if (shouldFormatJsonMessages(callback, mixin.getConfig().getName())) {
                            formatJsonMessages(mixinLogBuilder, 1, mixin.getConfig().getName(), callback);
                        }
                    }
                }
            }
        }

        if (mixinLogBuilder.length() != 0) {
            mixinLogBuilder.insert(0, "\n(MixinBooter) Mixins in Stacktrace:");
        }

        return stackMessage = mixinLogBuilder.toString();
    }

    @Override
    public boolean isStackMessagePresent() {
        return isMixinStackPresent() && formatStackMessage() != null;
    }

    @Override
    public String getStackMessage() {
        if (isStackMessagePresent()) {
            return stackMessage;
        }
        return errorMessage;
    }

    @Override
    public boolean isMixinStackPresent() {
        return mixinStack != null;
    }

    @Override
    public boolean isMixinPartOfStackTrace(String mixinJson) {
        if (isMixinStackPresent()) {
            return getFlatMixins().stream().
                    anyMatch(iMixinInfo -> iMixinInfo.getConfig().getName().equals(mixinJson));
        }

        return false;
    }

}
