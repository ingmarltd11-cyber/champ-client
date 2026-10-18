package net.champ.client.module.impl.client;

import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.ModuleRegistry;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.ColorSetting;
import net.champ.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
public final class RadarModule extends ClientModule {
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 48.0F, 8.0F, 256.0F).step(1.0F));
   private final NumberSetting size = this.addSetting("size", new NumberSetting("Size", 150.0F, 80.0F, 320.0F).step(1.0F));
   private final BooleanSetting rotate = this.addSetting("rotate", new BooleanSetting("Rotate", true));
   private final ColorSetting dotColor = this.addSetting("dot-color", new ColorSetting("Player Color", -47814));

   public RadarModule() {
      super("radar", "Radar", "A round radar of the players around you.", ModuleCategory.CLIENT);
   }

   public static RadarModule get() {
      return ModuleRegistry.get(RadarModule.class);
   }

   public static boolean shouldShowHud() {
      RadarModule module = get();
      return module != null && module.isEnabled();
   }

   public float range() {
      return this.range.get();
   }

   public float size() {
      return this.size.get();
   }

   public boolean rotate() {
      return this.rotate.get();
   }

   public int dotColor() {
      return this.dotColor.argb();
   }

   public boolean shows(Player player) {
      return !(player instanceof LocalPlayer) && !player.isRemoved() && !player.isDeadOrDying() && !player.isSpectator();
   }
}
