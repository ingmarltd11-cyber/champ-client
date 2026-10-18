package com.example.client.mixin;

import net.champ.client.gui.WindowIcon;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.server.packs.PackResources;
import com.mojang.blaze3d.platform.IconSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Window.class)
public abstract class MixinWindow {
   @Shadow
   public abstract long handle();

   @Inject(method = "setIcon", at = @At("TAIL"))
   private void champ$setIcon(PackResources resources, IconSet iconSet, CallbackInfo ci) {
      WindowIcon.apply(this.handle());
   }
}
