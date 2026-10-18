package net.champ.client.gui;

import net.champ.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

@Environment(EnvType.CLIENT)
public final class HudEditorScreen extends Screen {
   public HudEditorScreen() {
      super(Component.literal("CHAMP HUD Layout"));
   }

   public void method_25432() {
      super.removed();
      HudLayoutStore.getInstance().save();
   }

   public void method_25419() {
      HudLayoutStore.getInstance().save();
      Minecraft.getInstance().setScreen(null);
   }

   public void method_25420(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
   }

   public void method_25394(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
      ImGuiManager.getInstance().requestCursor(graphics);
   }

   public boolean method_25421() {
      return false;
   }
}
