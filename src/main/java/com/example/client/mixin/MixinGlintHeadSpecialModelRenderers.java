package com.example.client.mixin;

import com.mojang.serialization.MapCodec;
import net.champ.client.gui.GlintHeadRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.special.SpecialModelRenderer.Unbaked;
import net.minecraft.util.ExtraCodecs.LateBoundIdMapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SpecialModelRenderers.class)
public abstract class MixinGlintHeadSpecialModelRenderers {
   @Shadow
   @Final
   private static LateBoundIdMapper<Identifier, MapCodec<? extends Unbaked>> ID_MAPPER;

   @Inject(method = "bootstrap", at = @At("TAIL"))
   private static void champ$registerGlintHead(CallbackInfo ci) {
      ID_MAPPER.put(GlintHeadRenderer.ID, GlintHeadRenderer.Unbaked.MAP_CODEC);
   }
}
