package com.example.client.mixin;

import net.champ.client.module.impl.render.AtmosphereModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AtmosphericFogEnvironment.class)
public abstract class MixinAtmosphereRainFog {
   @Shadow
   private float field_60589;

   @Inject(method = "updateRainFogState", at = @At("TAIL"))
   private void champ$keepClearInRain(Camera camera, ClientLevel level, DeltaTracker deltaTracker, CallbackInfo ci) {
      AtmosphereModule atmosphere = AtmosphereModule.get();
      if (atmosphere != null && atmosphere.keepsSkyInRain()) {
         this.rainFogMultiplier = 0.0F;
      }
   }
}
