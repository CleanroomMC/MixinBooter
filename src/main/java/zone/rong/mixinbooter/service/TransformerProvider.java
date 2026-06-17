package zone.rong.mixinbooter.service;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.service.ILegacyClassTransformer;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.service.mojang.LaunchWrapperTransformerHandle;
import zone.rong.mixinbooter.Tags;

import java.util.*;

public class TransformerProvider implements ITransformerProvider {

    /**
     * Known re-entrant transformers that must never process meta class data.
     * Other re-entrants will be detected automatically.
     */
    private final Set<String> excludeTransformers = new HashSet<>(Arrays.asList(
            "net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer",
            "cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer",
            "net.minecraftforge.fml.common.asm.transformers.TerminalTransformer",
            "cpw.mods.fml.common.asm.transformers.TerminalTransformer"
    ));

    private List<ILegacyClassTransformer> delegatedTransformers;

    void refreshDelegatedTransformers() {
        this.delegatedTransformers = null;
    }

    @Override
    public Collection<ITransformer> getTransformers() {
        List<IClassTransformer> transformers = Launch.classLoader.getTransformers();
        List<ITransformer> result = new ArrayList<>(transformers.size());
        for (IClassTransformer transformer : transformers) {
            if (transformer instanceof ITransformer) {
                result.add((ITransformer) transformer);
            } else {
                result.add(new LaunchWrapperTransformerHandle(transformer));
            }
        }
        return result;
    }

    @Override
    public List<ITransformer> getDelegatedTransformers() {
        return Collections.unmodifiableList(this.getDelegatedLegacyTransformers());
    }

    @Override
    public void addTransformerExclusion(String name) {
        this.excludeTransformers.add(name);
        this.delegatedTransformers = null;
    }

    private List<ILegacyClassTransformer> getDelegatedLegacyTransformers() {
        if (this.delegatedTransformers == null) {
            this.buildTransformerDelegationList();
        }

        return this.delegatedTransformers;
    }

    /**
     * Builds the transformer list to apply to loaded mixin bytecode. Since
     * generating this list requires inspecting each transformer by name (to
     * cope with the new wrapper functionality added by FML) we generate the
     * list just once per environment and cache the result.
     */
    private void buildTransformerDelegationList() {
        ILogger logger = MixinService.getService().getLogger(Tags.MOD_NAME + "|TransformerProvider");
        logger.debug("Rebuilding transformer delegation list:");
        this.delegatedTransformers = new ArrayList<>();
        for (ITransformer transformer : this.getTransformers()) {
            if (!(transformer instanceof ILegacyClassTransformer)) {
                continue;
            }

            ILegacyClassTransformer legacyTransformer = (ILegacyClassTransformer)transformer;
            String transformerName = legacyTransformer.getName();
            boolean include = true;
            for (String excludeClass : this.excludeTransformers) {
                if (transformerName.contains(excludeClass)) {
                    include = false;
                    break;
                }
            }
            if (include && !legacyTransformer.isDelegationExcluded()) {
                logger.debug("  Adding:    {}", transformerName);
                this.delegatedTransformers.add(legacyTransformer);
            } else {
                logger.debug("  Excluding: {}", transformerName);
            }
        }
        logger.debug("Transformer delegation list created with {} entries", this.delegatedTransformers.size());
    }

}
