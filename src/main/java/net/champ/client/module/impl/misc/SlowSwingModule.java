package net.champ.client.module.impl.misc;

import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SlowSwingModule extends ClientModule {
   private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 0.25F, 0.1F, 2.0F).step(0.05F));

   public SlowSwingModule() {
      super("slow-swing", "Slow Swing", "Control hand swing animation speed.", ModuleCategory.MISC);
   }

   public int getModifiedSwingDuration(int vanilla) {
      float s = this.speed.get();
      if (s <= 0.0F) {
         s = 0.1F;
      }

      int modified = Math.round(vanilla / s);
      return Math.max(1, modified);
   }
}
