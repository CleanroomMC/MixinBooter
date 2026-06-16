package zone.rong.mixinbooter.service;

import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.service.IClassProvider;

import java.net.URL;

public class ClassProvider implements IClassProvider {

    @Override
    public URL[] getClassPath() {
        return Launch.classLoader.getURLs();
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return Launch.classLoader.findClass(name);
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, Launch.classLoader);
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, Launch.class.getClassLoader());
    }

}
