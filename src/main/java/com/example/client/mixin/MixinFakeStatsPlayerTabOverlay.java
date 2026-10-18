package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.champ.client.module.impl.misc.FakeStats;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Environment(EnvType.CLIENT)
@Mixin(PlayerTabOverlay.class)
public abstract class MixinFakeStatsPlayerTabOverlay {
   @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
   private Component champ$fakeStatsTabName(Component original, @Local(argsOnly = true) PlayerInfo info) {
      return FakeStats.tabName(info.getProfile().id(), original);
   }

   @WrapOperation(
      method = "render",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/scores/Scoreboard;getPlayerScoreInfo(Lnet/minecraft/world/scores/ScoreHolder;Lnet/minecraft/world/scores/Objective;)Lnet/minecraft/world/scores/ReadOnlyScoreInfo;"
      )
   )
   private ReadOnlyScoreInfo champ$fakeStatsTabScore(Scoreboard board, ScoreHolder holder, Objective objective, Operation<ReadOnlyScoreInfo> original) {
      return FakeStats.tabScore(holder, (ReadOnlyScoreInfo)original.call(new Object[]{board, holder, objective}));
   }

   @ModifyArg(
      method = "render",
      at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"),
      index = 0
   )
   private FormattedText champ$fakeStatsTabHeader(FormattedText header) {
      return FakeStats.tabText(header);
   }

   @ModifyArg(
      method = "render",
      at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"),
      index = 0
   )
   private FormattedText champ$fakeStatsTabFooter(FormattedText footer) {
      return FakeStats.tabText(footer);
   }
}
