package zone.rong.mixinbooter.service;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.IClassBytecodeProvider;

import java.io.IOException;

public class BytecodeProvider implements IClassBytecodeProvider {

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, true, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException, IOException {
        byte[] bytes = Launch.classLoader.getClassBytes(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        if (runTransformers) {
            bytes = applyTransformers(name, bytes);
        }
        ClassNode classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, readerFlags);
        return classNode;
    }

    private byte[] applyTransformers(String name, byte[] bytes) {
        for (IClassTransformer transformer : Launch.classLoader.getTransformers()) {
            byte[] transformed = transformer.transform(name, name, bytes);
            if (transformed != null) {
                bytes = transformed;
            }
        }
        return bytes;
    }

}
