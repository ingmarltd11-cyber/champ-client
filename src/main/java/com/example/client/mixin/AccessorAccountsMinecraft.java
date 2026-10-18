package com.example.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.minecraft.UserApiService.UserProperties;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public interface AccessorAccountsMinecraft {
   @Mutable
   @Accessor("user")
   void champ$setUser(User var1);

   @Mutable
   @Accessor("userApiService")
   void champ$setUserApiService(UserApiService var1);

   @Mutable
   @Accessor("userPropertiesFuture")
   void champ$setUserPropertiesFuture(CompletableFuture<UserProperties> var1);

   @Mutable
   @Accessor("profileFuture")
   void champ$setProfileFuture(CompletableFuture<ProfileResult> var1);

   @Mutable
   @Accessor("playerSocialManager")
   void champ$setPlayerSocialManager(PlayerSocialManager var1);

   @Mutable
   @Accessor("profileKeyPairManager")
   void champ$setProfileKeyPairManager(ProfileKeyPairManager var1);

   @Accessor("reportingContext")
   void champ$setReportingContext(ReportingContext var1);
}
