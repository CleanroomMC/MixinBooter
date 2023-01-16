package zone.rong.mixinextras.sugar.impl;

import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import zone.rong.mixinbooter.MixinBooterPlugin;

public class SugarApplicatorExtension implements IExtension {
  @Override
  public boolean checkActive(MixinEnvironment environment) {
    return true;
  }

  @Override
  public void preApply(ITargetClassContext context) {
    for (Pair<IMixinInfo, ClassNode> pair : MixinBooterPlugin.getMixinsFor(context)) {
      SugarInjector.prepareMixin(pair.getLeft(), pair.getRight());
    }
  }

  @Override
  public void postApply(ITargetClassContext context) {
  }

  @Override
  public void export(MixinEnvironment env, String name, boolean force, ClassNode classNode) {
  }
}
