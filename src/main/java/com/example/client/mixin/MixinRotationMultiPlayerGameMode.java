package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.Supplier;
import net.champ.client.util.pvp.Rotation;
import net.champ.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinRotationMultiPlayerGameMode {
   @Shadow
   @Final
   private Minecraft field_3712;

   @WrapMethod(method = "useItem")
   private InteractionResult champ$useItemRotation(Player player, InteractionHand hand, Operation<InteractionResult> original) {
      return this.champ$asSent(player, () -> (InteractionResult)original.call(new Object[]{player, hand}));
   }

   @WrapMethod(method = "releaseUsingItem")
   private void champ$releaseRotation(Player player, Operation<Void> original) {
      this.champ$asSent(player, () -> (Void)original.call(new Object[]{player}));
   }

   private <T> T champ$asSent(Player player, Supplier<T> call) {
      LocalPlayer self = this.minecraft.player;
      if (player == self && !ServerRotation.isSwapped()) {
         Rotation rotation = ServerRotation.commit(self);
         float yaw = self.getYRot();
         float pitch = self.getXRot();
         if (rotation.yaw() == yaw && rotation.pitch() == pitch) {
            return call.get();
         }

         self.setYRot(rotation.yaw());
         self.setXRot(rotation.pitch());

         try {
            return call.get();
         } finally {
            self.setYRot(yaw);
            self.setXRot(pitch);
         }
      } else {
         return call.get();
      }
   }

   @Inject(method = "attack", at = @At("HEAD"), cancellable = true, order = 950)
   private void champ$attackRotationGate(Player player, Entity target, CallbackInfo ci) {
      LocalPlayer self = this.minecraft.player;
      if (player == self) {
         AABB box = target.getBoundingBox();
         double reach = self.entityInteractionRange();
         AttackRange range = (AttackRange)self.getMainHandItem().get(DataComponents.ATTACK_RANGE);
         if (range != null) {
            box = box.inflate(range.comp_5262());
            reach = range.effectiveMaxRange(self);
         }

         if (ServerRotation.misses(self, box, reach)) {
            player.resetAttackStrengthTicker();
            ci.cancel();
         }
      }
   }

   @Inject(method = "interact", at = @At("HEAD"), cancellable = true, order = 950)
   private void champ$interactRotationGate(Player player, Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
      LocalPlayer self = this.minecraft.player;
      if (player == self && ServerRotation.misses(self, target.getBoundingBox(), self.entityInteractionRange())) {
         cir.setReturnValue(InteractionResult.PASS);
      }
   }

   @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true, order = 950)
   private void champ$interactAtRotationGate(Player player, Entity target, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
      LocalPlayer self = this.minecraft.player;
      if (player == self && ServerRotation.misses(self, target.getBoundingBox(), self.entityInteractionRange())) {
         cir.setReturnValue(InteractionResult.PASS);
      }
   }

   @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, order = 950)
   private void champ$useItemOnRotationGate(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
      if (player == this.minecraft.player && ServerRotation.missesBlock(player, new AABB(hit.getBlockPos()), player.blockInteractionRange())) {
         cir.setReturnValue(InteractionResult.FAIL);
      }
   }

   @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true, order = 950)
   private void champ$startDestroyRotationGate(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
      LocalPlayer self = this.minecraft.player;
      if (self != null && ServerRotation.missesBlock(self, new AABB(pos), self.blockInteractionRange())) {
         cir.setReturnValue(false);
      }
   }
}
