package zone.rong.mixinbooter.fix;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class SuppressingClassReader extends ClassReader {

    public SuppressingClassReader() {
        super(new byte[] { 0, 0, 0, 0, 0, 0, 0, 52, 0, 1 });
    }

    @Override
    public void accept(ClassVisitor classVisitor, int flags) {
        classVisitor.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                "zone/rong/mixinbooter/fix/DummyClass",
                null,
                "java/lang/Object",
                new String[0]
        );
        classVisitor.visitEnd();
    }

}
