package net.champ.client.account;

import com.example.client.mixin.AccessorAccountsMinecraft;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.minecraft.UserApiService.UserProperties;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_310;
import net.minecraft.class_320;
import net.minecraft.class_5520;
import net.minecraft.class_7569;
import net.minecraft.class_7574;
import net.minecraft.class_7853;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class GameSession {
   private static final Logger LOGGER = LoggerFactory.getLogger("Champ Client");
   private YggdrasilAuthenticationService mojangAuth;

   GameSession() {
   }

   GameSession.Prepared prepare(AccountType type, Credentials credentials) {
      class_310 mc = class_310.method_1551();
      boolean offline = type == AccountType.CRACKED;
      class_320 user = new class_320(credentials.name(), credentials.uuid(), credentials.accessToken(), Optional.empty(), Optional.empty());
      UserApiService api = offline ? UserApiService.OFFLINE : this.mojangAuth(mc).createUserApiService(credentials.accessToken());

      UserProperties properties;
      try {
         properties = api.fetchProperties();
      } catch (AuthenticationException e) {
         LOGGER.warn("[Champ Client] Couldn't fetch user properties for {}", credentials.name(), e);
         properties = UserApiService.OFFLINE_PROPERTIES;
      }

      ProfileResult profile = offline ? null : mc.method_73361().comp_837().fetchProfile(credentials.uuid(), true);
      return new GameSession.Prepared(user, api, properties, profile, offline);
   }

   void apply(GameSession.Prepared prepared) {
      class_310 mc = class_310.method_1551();
      AccessorAccountsMinecraft game = (AccessorAccountsMinecraft)mc;
      game.champ$setUser(prepared.user());
      game.champ$setUserApiService(prepared.api());
      game.champ$setUserPropertiesFuture(CompletableFuture.completedFuture(prepared.properties()));
      game.champ$setProfileFuture(CompletableFuture.completedFuture(prepared.profile()));
      game.champ$setPlayerSocialManager(new class_5520(mc, prepared.api()));
      game.champ$setProfileKeyPairManager(
         prepared.offline() ? class_7853.field_40800 : class_7853.method_46532(prepared.api(), prepared.user(), mc.field_1697.toPath())
      );
      game.champ$setReportingContext(class_7574.method_44599(class_7569.method_44586(), prepared.api()));
   }

   private YggdrasilAuthenticationService mojangAuth(class_310 mc) {
      if (this.mojangAuth == null) {
         this.mojangAuth = new YggdrasilAuthenticationService(mc.method_1487());
      }

      return this.mojangAuth;
   }

   @Environment(EnvType.CLIENT)
   record Prepared(class_320 user, UserApiService api, UserProperties properties, ProfileResult profile, boolean offline) {
   }
}
