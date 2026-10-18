package com.example.client.mixin;

import net.champ.client.module.impl.render.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.state.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(WeatherEffectRenderer.class)
public abstract class MixinNoRenderWeather {
   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void champ$hideWeather(MultiBufferSource bufferSource, Vec3 camera, WeatherRenderState state, CallbackInfo ci) {
      if (NoRenderModule.hideWeather()) {
         ci.cancel();
      }
   }

   @Inject(method = "tickRainParticles", at = @At("HEAD"), cancellable = true)
   private void champ$hideRainSplashes(ClientLevel level, Camera camera, int ticks, ParticleStatus particles, int radius, CallbackInfo ci) {
      if (NoRenderModule.hideWeather()) {
         ci.cancel();
      }
   }
}
