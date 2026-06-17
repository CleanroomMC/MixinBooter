package zone.rong.mixinbooter.service;

import com.google.common.io.ByteStreams;
import net.minecraft.launchwrapper.IClassNameTransformer;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.ILegacyClassTransformer;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.transformers.MixinClassReader;
import org.spongepowered.asm.util.ReEntranceLock;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

public class BytecodeProvider implements IClassBytecodeProvider {

    private final TransformerProvider transformerProvider;
    private final ReEntranceLock lock;
    private final LaunchClassLoaderUtil classLoaderUtil;

    private IClassNameTransformer nameTransformer;

    BytecodeProvider(TransformerProvider transformerProvider, ReEntranceLock lock, LaunchClassLoaderUtil classLoaderUtil) {
        this.transformerProvider = transformerProvider;
        this.lock = lock;
        this.classLoaderUtil = classLoaderUtil;
    }

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, true, ClassReader.EXPAND_FRAMES);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, ClassReader.EXPAND_FRAMES);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException, IOException {
        String transformedName = name.replace('/', '.');
        String originalName = unmapClassName(transformedName);
        byte[] bytes = getClassBytes(originalName, transformedName);
        if (runTransformers) {
            bytes = applyTransformers(originalName, transformedName, bytes);
        }
        if (bytes == null) {
            throw new ClassNotFoundException(transformedName);
        }
        ClassNode classNode = new ClassNode();
        new MixinClassReader(bytes, name).accept(classNode, readerFlags);
        return classNode;
    }

    private byte[] getClassBytes(String name, String transformedName) throws IOException {
        byte[] classBytes = Launch.classLoader.getClassBytes(name);
        if (classBytes != null) {
            return classBytes;
        }
        ClassLoader launchLoader = Launch.class.getClassLoader();
        URLClassLoader appClassLoader = launchLoader instanceof URLClassLoader
                ? (URLClassLoader) launchLoader
                : new URLClassLoader(new URL[]{}, launchLoader);
        try (InputStream classStream = appClassLoader.getResourceAsStream(transformedName.replace('.', '/').concat(".class"))) {
            return classStream != null ? ByteStreams.toByteArray(classStream) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private byte[] applyTransformers(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (this.classLoaderUtil.isClassExcluded(name, transformedName)) {
            return basicClass;
        }
        for (ITransformer transformer : transformerProvider.getDelegatedTransformers()) {
            if (!(transformer instanceof ILegacyClassTransformer)) {
                continue;
            }
            ILegacyClassTransformer legacyTransformer = (ILegacyClassTransformer) transformer;
            this.lock.clear();
            basicClass = legacyTransformer.transformClassBytes(name, transformedName, basicClass);
            if (this.lock.isSet()) {
                this.transformerProvider.addTransformerExclusion(legacyTransformer.getName());
                this.lock.clear();
            }
        }
        return basicClass;
    }

    private String unmapClassName(String className) {
        if (this.nameTransformer == null) {
            for (IClassTransformer transformer : Launch.classLoader.getTransformers()) {
                if (transformer instanceof IClassNameTransformer) {
                    this.nameTransformer = (IClassNameTransformer) transformer;
                }
            }
        }
        return this.nameTransformer != null ? this.nameTransformer.unmapClassName(className) : className;
    }

}
