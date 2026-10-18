package com.example.client.mixin;

import net.champ.client.module.impl.misc.AirStuckModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class MixinAirStuckLocalPlayer {
   @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
   private void champ$airStuck(CallbackInfo ci) {
      if (AirStuckModule.active != null) {
         ci.cancel();
      }
   }
}
