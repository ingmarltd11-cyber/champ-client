package com.example.client.mixin;

import net.champ.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(BlockStateBase.class)
public abstract class MixinBlockStateBase {
   @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
   private void champ$xrayNoAmbientOcclusion(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
      if (XrayModule.active != null) {
         cir.setReturnValue(1.0F);
      }
   }
}
