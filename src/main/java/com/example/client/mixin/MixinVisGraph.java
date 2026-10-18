package com.example.client.mixin;

import net.champ.client.module.impl.misc.FreecamModule;
import net.champ.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.chunk.VisGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(VisGraph.class)
public abstract class MixinVisGraph {
   @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
   private void champ$noOcclusion(BlockPos pos, CallbackInfo ci) {
      if (XrayModule.active != null || FreecamModule.active != null) {
         ci.cancel();
      }
   }
}
