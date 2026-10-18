package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.champ.client.module.ModuleRegistry;
import net.champ.client.module.impl.misc.SkinChangeModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ItemInHandRenderer.class)
public abstract class MixinSkinChangeItemInHandRenderer {
   @Shadow
   @Final
   private Minecraft field_4050;
   @Shadow
   @Final
   private EntityRenderDispatcher field_4046;

   @WrapOperation(
      method = {"renderPlayerArm", "renderMapHand"},
      at = {
            @At(
               value = "INVOKE",
               target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"
            ),
            @At(
               value = "INVOKE",
               target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"
            )
      }
   )
   private void champ$skinChangeArm(
      AvatarRenderer<?> renderer, PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier texture, boolean sleeve, Operation<Void> original
   ) {
      SkinChangeModule mod = ModuleRegistry.get(SkinChangeModule.class);
      LocalPlayer player = this.minecraft.player;
      if (mod != null && mod.isEnabled() && player != null) {
         PlayerSkin own = player.getSkin();
         PlayerSkin skin = mod.skinFor(player.getId(), own);
         if (skin != own) {
            texture = skin.comp_1626().comp_3627();
            AvatarRenderer<AbstractClientPlayer> byModel = ((AccessorSkinChangeEntityRenderDispatcher)this.entityRenderDispatcher).champ$skinChangePlayerRenderers().get(skin.comp_1629());
            if (byModel != null) {
               renderer = byModel;
            }
         }
      }

      original.call(new Object[]{renderer, poseStack, collector, light, texture, sleeve});
   }
}
