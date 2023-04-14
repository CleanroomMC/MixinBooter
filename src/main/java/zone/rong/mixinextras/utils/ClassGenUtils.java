package zone.rong.mixinextras.utils;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

public class ClassGenUtils {
    private static final Definer DEFINER;

    static {
        Definer theDefiner;
        try {
            Method method = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
            method.setAccessible(true);
            theDefiner = (name, bytes, scope) -> {
                try {
                    scope.unreflect(method).invokeExact(scope.lookupClass().getClassLoader(), name.replace('/', '.'), bytes, 0, bytes.length);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (NoSuchMethodException nsme) {
            RuntimeException re = new RuntimeException("Could not resolve class definer! Please inform to Rongmario.");
            re.addSuppressed(nsme);
            throw re;
        }
        DEFINER = theDefiner;
    }

    public static void defineClass(ClassNode node, MethodHandles.Lookup scope) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        DEFINER.define(node.name.replace('/', '.'), writer.toByteArray(), scope);
    }

    @FunctionalInterface
    private interface Definer {
        void define(String name, byte[] bytes, MethodHandles.Lookup scope);
    }
}
