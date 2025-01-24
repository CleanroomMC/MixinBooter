package zone.rong.mixinbooter.fix.forge;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Iterator;

public class EagerlyLoadEventClassTransformer implements IClassTransformer, Opcodes {

    @Override
    public byte[] transform(String name, String transformedName, byte[] classBytes) {
        if ("$wrapper.net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer".equals(name)) {
            return this.eagerlyLoadEventClass(classBytes);
        }
        return classBytes;
    }

    private byte[] eagerlyLoadEventClass(byte[] classBytes) {
        ClassNode node = new ClassNode();
        ClassReader reader = new ClassReader(classBytes);
        reader.accept(node, 0);

        for (MethodNode method : node.methods) {
            if ("<init>".equals(method.name)) {
                Iterator<AbstractInsnNode> iterator = method.instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode instruction = iterator.next();
                    if (instruction.getOpcode() == RETURN) {
                        InsnList instructions = new InsnList();
                        instructions.add(new TypeInsnNode(NEW, "net/minecraftforge/fml/common/eventhandler/Event"));
                        instructions.add(new InsnNode(DUP));
                        instructions.add(new MethodInsnNode(INVOKESPECIAL, "net/minecraftforge/fml/common/eventhandler/Event", "<init>", "()V", false));
                        method.instructions.insert(instruction.getPrevious(), instructions);
                        break;
                    }
                }
                break;
            }
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

}
