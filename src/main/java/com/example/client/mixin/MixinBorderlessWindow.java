package com.example.client.mixin;

import net.champ.client.gui.BorderlessFullscreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.ScreenManager;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Window.class)
public abstract class MixinBorderlessWindow implements BorderlessFullscreen.Switchable {
   @Shadow
   @Final
   private long handle;
   @Shadow
   @Final
   private ScreenManager screenManager;
   @Shadow
   private boolean fullscreen;
   @Shadow
   private boolean actuallyFullscreen;
   @Shadow
   private int x;
   @Shadow
   private int y;
   @Shadow
   private int width;
   @Shadow
   private int height;
   @Shadow
   private int windowedX;
   @Shadow
   private int windowedY;
   @Shadow
   private int windowedWidth;
   @Shadow
   private int windowedHeight;
   @Unique
   private boolean champ$borderless;

   @Inject(method = "setMode", at = @At("HEAD"), cancellable = true)
   private void champ$setMode(CallbackInfo ci) {
      if (this.fullscreen && BorderlessFullscreen.wanted()) {
         Monitor monitor = this.screenManager.findBestMonitor((Window)(Object)(Object)this);
         if (monitor != null) {
            boolean exclusive = GLFW.glfwGetWindowMonitor(this.handle) != 0L;
            if (!exclusive && !this.champ$borderless) {
               this.windowedX = this.x;
               this.windowedY = this.y;
               this.windowedWidth = this.width;
               this.windowedHeight = this.height;
            }

            VideoMode mode = monitor.getCurrentMode();
            this.x = monitor.getX();
            this.y = monitor.getY();
            this.width = mode.getWidth();
            this.height = mode.getHeight();
            GLFW.glfwSetWindowAttrib(this.handle, 131077, 0);
            GLFW.glfwSetWindowMonitor(this.handle, 0L, this.x, this.y, this.width, this.height, -1);
            if (exclusive) {
               GLFW.glfwSetWindowAttrib(this.handle, 131077, 0);
            }

            this.champ$borderless = true;
            ci.cancel();
         }
      } else {
         if (this.champ$borderless) {
            this.champ$leaveBorderless();
         }
      }
   }

   @Unique
   private void champ$leaveBorderless() {
      this.champ$borderless = false;
      GLFW.glfwSetWindowAttrib(this.handle, 131077, 1);
      this.x = this.windowedX;
      this.y = this.windowedY;
      this.width = this.windowedWidth;
      this.height = this.windowedHeight;
   }

   @Override
   public boolean champ$isBorderless() {
      return this.champ$borderless;
   }

   @Override
   public void champ$reapplyMode() {
      this.actuallyFullscreen = !this.fullscreen;
   }
}
