package zone.rong.mixinbooter.service.platform;

import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.launch.platform.IMixinPlatformServiceAgent;
import org.spongepowered.asm.launch.platform.MixinPlatformAgentAbstract;
import org.spongepowered.asm.launch.platform.MixinPlatformManager;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IRemapper;
import org.spongepowered.asm.obfuscation.mapping.remap.CleanroomRemapper;
import org.spongepowered.asm.obfuscation.mapping.remap.Srg2McpRemapper;
import zone.rong.mixinbooter.util.Environment;
import zone.rong.mixinbooter.util.Srg2NotchRemapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MixinBooterPlatformAgent extends MixinPlatformAgentAbstract implements IMixinPlatformServiceAgent {

    @Override
    public AcceptResult accept(MixinPlatformManager manager, IContainerHandle handle) {
        return AcceptResult.ACCEPTED;
    }

    @Override
    public void init() {
        injectRemapper();
    }

    @Override
    public String getSideName() {
        return Environment.side();
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        List<IContainerHandle> containers = new ArrayList<>();
        for (URL url : Launch.classLoader.getURLs()) {
            try {
                URI uri = url.toURI();
                String path = uri.getPath();
                if (path != null && path.endsWith(".jar")) {
                    containers.add(new ContainerHandleURI(uri));
                }
            } catch (URISyntaxException ignored) { }
        }
        return containers;
    }

    private void injectRemapper() {
        IRemapper remapper = Environment.inDev() ?
                new Srg2McpRemapper(MixinEnvironment.getDefaultEnvironment()) :
                new CleanroomRemapper<>(new Srg2NotchRemapper());
        MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
    }

}
