package zone.rong.mixinextras;

import org.spongepowered.asm.util.logging.MessageRouter;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@SupportedAnnotationTypes({})
public class MixinExtrasAnnotationProcessor extends AbstractProcessor {

    public static final String VERSION = "0.1.1-rc.4";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        MessageRouter.setMessager(processingEnv.getMessager());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

}
