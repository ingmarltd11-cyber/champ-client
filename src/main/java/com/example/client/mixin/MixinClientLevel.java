package com.example.client.mixin;

import net.champ.client.event.impl.BlockUpdateEvent;
import net.champ.client.event.impl.WorldEntityAddEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {
   @Inject(method = "addEntity", at = @At("HEAD"))
   private void champ$onEntityAdd(Entity entity, CallbackInfo ci) {
      WorldEntityAddEvent.dispatch(entity);
   }

   @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
   private void champ$onBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
      BlockUpdateEvent.dispatch(pos, oldState, newState);
   }
}
