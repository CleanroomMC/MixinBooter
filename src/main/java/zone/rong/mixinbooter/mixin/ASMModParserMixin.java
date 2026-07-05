package zone.rong.mixinbooter.mixin;

import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zone.rong.mixinbooter.fix.SuppressingClassReader;

import java.io.IOException;
import java.io.InputStream;

@Mixin(value = ASMModParser.class, remap = false)
public class ASMModParserMixin {

    @Shadow
    private Type asmType;

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "org/objectweb/asm/ClassReader"))
    private ClassReader suppressClassReaderWhenReadingNewClasses(InputStream is) throws IOException {
        try {
            return new ClassReader(is);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() == null) {
                this.asmType = Type.VOID_TYPE;
                return new SuppressingClassReader();
            }
            throw e;
        }
    }

}
