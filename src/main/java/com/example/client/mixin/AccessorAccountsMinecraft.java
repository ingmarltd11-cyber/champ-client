package com.example.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.minecraft.UserApiService.UserProperties;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_310;
import net.minecraft.class_320;
import net.minecraft.class_5520;
import net.minecraft.class_7574;
import net.minecraft.class_7853;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(class_310.class)
public interface AccessorAccountsMinecraft {
   @Mutable
   @Accessor("field_1726")
   void champ$setUser(class_320 var1);

   @Mutable
   @Accessor("field_26902")
   void champ$setUserApiService(UserApiService var1);

   @Mutable
   @Accessor("field_47680")
   void champ$setUserPropertiesFuture(CompletableFuture<UserProperties> var1);

   @Mutable
   @Accessor("field_45899")
   void champ$setProfileFuture(CompletableFuture<ProfileResult> var1);

   @Mutable
   @Accessor("field_26842")
   void champ$setPlayerSocialManager(class_5520 var1);

   @Mutable
   @Accessor("field_39068")
   void champ$setProfileKeyPairManager(class_7853 var1);

   @Accessor("field_39492")
   void champ$setReportingContext(class_7574 var1);
}
