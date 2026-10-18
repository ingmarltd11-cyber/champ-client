package net.champ.client.module.impl.render;

import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class FullbrightModule extends ClientModule {
   private final NumberSetting gamma = this.addSetting("gamma", new NumberSetting("Gamma", 8.0F, 1.0F, 16.0F).step(0.5F));

   public FullbrightModule() {
      super("fullbright", "Fullbright", "Brighten dark areas by overriding the client lightmap gamma.", ModuleCategory.RENDER);
   }

   public float gamma() {
      return Math.max(1.0F, this.gamma.get());
   }
}
