package zone.rong.mixinbooter.fix;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

public class SuppressingClassReader extends ClassReader {

    public SuppressingClassReader() {
        super(new byte[] { 0, 0, 0, 0, 0, 0, 0, 52, 0, 1 });
    }

    @Override
    public void accept(ClassVisitor classVisitor, int flags) {
        // NO-OP
    }

}
