package com.example.client.mixin;

import net.champ.client.module.ModuleRegistry;
import net.champ.client.module.impl.misc.SkinChangeModule;
import net.champ.client.module.impl.render.NametagsModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.Avatar;
import net.minecraft.client.renderer.state.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AvatarRenderer.class)
public abstract class MixinPlayerRenderer {
   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void champ$injectSkin(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
      SkinChangeModule mod = ModuleRegistry.get(SkinChangeModule.class);
      if (mod != null && mod.isEnabled()) {
         state.skin = mod.skinFor(state.id, state.skin);
      }
   }

   @Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
   private void champ$suppressVanillaNameTag(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo ci) {
      if (NametagsModule.hideVanillaNametags()) {
         ci.cancel();
      }
   }
}
