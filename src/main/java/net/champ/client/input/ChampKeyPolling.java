package net.champ.client.input;

import imgui.glfw.ChampKeys;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.InputConstants;

@Environment(EnvType.CLIENT)
public final class ChampKeyPolling {
   private ChampKeyPolling() {
   }

   public static boolean isKeyDown(Window window, int key) {
      return ChampKeys.isAndroid() ? ChampKeys.getKey(window.handle(), key) == 1 : InputConstants.isKeyDown(window, key);
   }
}
