package zone.rong.mixinbooter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ITypeDiscoverer;
import net.minecraftforge.fml.common.discovery.JarDiscoverer;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;

@Mixin(value = JarDiscoverer.class, remap = false)
public class JarDiscovererMixin {

    @WrapOperation(method = "discover", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/common/discovery/JarDiscoverer;findClassesASM(Lnet/minecraftforge/fml/common/discovery/ModCandidate;Lnet/minecraftforge/fml/common/discovery/ASMDataTable;Ljava/util/jar/JarFile;Ljava/util/List;Lnet/minecraftforge/fml/common/MetadataCollection;)V"))
    private void onFindingClasses(JarDiscoverer instance, ModCandidate candidate, ASMDataTable table, JarFile jar, List<ModContainer> found, MetadataCollection mc, Operation<Void> original, @Share(value = "count") LocalIntRef count) throws IOException {
        for (ZipEntry ze : Collections.list(jar.entries())) {
            if (ze.getName() == null || ze.getName().startsWith("__MACOSX")) {
                continue;
            }
            Matcher match = ITypeDiscoverer.classFile.matcher(ze.getName());
            if (match.matches()) {
                ASMModParser modParser;
                try {
                    try (InputStream inputStream = jar.getInputStream(ze)) {
                        modParser = new ASMModParser(inputStream);
                    }
                    candidate.addClassEntry(ze.getName());
                } catch (LoaderException e) {
                    if (e.getCause() instanceof IllegalArgumentException && e.getCause().getMessage() == null) {
                        count.set(count.get() + 1);
                        FMLLog.log.debug("{} of {} is compiled by a more recent Java version, skipping.", ze.getName(), candidate.getModContainer().getName());
                        continue;
                    }
                    FMLLog.log.error("There was a problem reading the entry {} in the jar {} - probably a corrupt zip", ze.getName(), candidate.getModContainer().getPath(), e);
                    jar.close();
                    throw e;
                }
                modParser.validate();
                modParser.sendToTable(table, candidate);
                ModContainer container = ModContainerFactory.instance().build(modParser, candidate.getModContainer(), candidate);
                if (container != null) {
                    table.addContainer(container);
                    found.add(container);
                    container.bindMetadata(mc);
                    container.setClassVersion(modParser.getClassVersion());
                }
            }
        }
    }

    @Inject(method = "discover", at = @At("RETURN"))
    private void onReturn(ModCandidate candidate, ASMDataTable table, CallbackInfoReturnable<List<ModContainer>> cir, @Share(value = "count") LocalIntRef count) {
        if (count.get() > 0) {
            FMLLog.log.warn("{} has {} classes that were skipped from annotation scanning, as they were likely to be compiled by more recent Java versions.", candidate.getModContainer().getName(), count.get());
        }
    }

}
