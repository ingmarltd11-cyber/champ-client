package net.champ.client.gui;

import com.sun.jna.Platform;
import net.champ.client.module.impl.misc.NoInvLeakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class BorderlessFullscreen {
   private BorderlessFullscreen() {
   }

   public static boolean wanted() {
      return Platform.isWindows() && NoInvLeakModule.wantsBorderless();
   }

   public static void tick() {
      Window window = Minecraft.getInstance().getWindow();
      if (window.isFullscreen()) {
         BorderlessFullscreen.Switchable switchable = (BorderlessFullscreen.Switchable)(Object)window;
         if (wanted() != switchable.champ$isBorderless()) {
            switchable.champ$reapplyMode();
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public interface Switchable {
      boolean champ$isBorderless();

      void champ$reapplyMode();
   }
}
