package net.champ.client.gui;

import java.nio.file.Path;
import java.util.List;
import net.champ.client.config.HudTheme;
import net.champ.client.config.ModuleConfigStore;
import net.champ.client.config.ThemeStore;
import net.champ.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

@Environment(EnvType.CLIENT)
public final class ImGuiScreen extends Screen {
   private final ClickGuiRenderer clickGui = new ClickGuiRenderer();

   public ImGuiScreen() {
      super(Component.literal("CHAMP"));
   }

   protected void init() {
      super.init();
      ImGuiManager.getInstance().activate(this.clickGui::render);
   }

   public void removed() {
      super.removed();
      this.clickGui.handleEscape();
      ImGuiManager.getInstance().deactivate();
      ModuleConfigStore.getInstance().save();
      ThemeStore.get().save();
      HudTheme.get().save();
   }

   public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      if (ThemeStore.get().menuBlur.get()) {
         super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
      }
   }

   public void onFilesDrop(List<Path> paths) {
      this.clickGui.onFilesDropped(paths);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
      ImGuiManager.getInstance().requestCursor(graphics);
   }

   public boolean keyPressed(KeyEvent event) {
      return event.key() == 256 && this.clickGui.handleEscape() ? true : super.keyPressed(event);
   }

   public boolean capturesKeys() {
      return this.clickGui.capturesKeys();
   }

   public boolean isPauseScreen() {
      return false;
   }
}
