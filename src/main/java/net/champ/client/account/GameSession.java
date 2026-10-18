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
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class GameSession {
   private static final Logger LOGGER = LoggerFactory.getLogger("Champ Client");
   private YggdrasilAuthenticationService mojangAuth;

   GameSession() {
   }

   GameSession.Prepared prepare(AccountType type, Credentials credentials) {
      Minecraft mc = Minecraft.getInstance();
      boolean offline = type == AccountType.CRACKED;
      User user = new User(credentials.name(), credentials.uuid(), credentials.accessToken(), Optional.empty(), Optional.empty());
      UserApiService api = offline ? UserApiService.OFFLINE : this.mojangAuth(mc).createUserApiService(credentials.accessToken());

      UserProperties properties;
      try {
         properties = api.fetchProperties();
      } catch (AuthenticationException e) {
         LOGGER.warn("[Champ Client] Couldn't fetch user properties for {}", credentials.name(), e);
         properties = UserApiService.OFFLINE_PROPERTIES;
      }

      ProfileResult profile = offline ? null : mc.services().sessionService().fetchProfile(credentials.uuid(), true);
      return new GameSession.Prepared(user, api, properties, profile, offline);
   }

   void apply(GameSession.Prepared prepared) {
      Minecraft mc = Minecraft.getInstance();
      AccessorAccountsMinecraft game = (AccessorAccountsMinecraft)mc;
      game.champ$setUser(prepared.user());
      game.champ$setUserApiService(prepared.api());
      game.champ$setUserPropertiesFuture(CompletableFuture.completedFuture(prepared.properties()));
      game.champ$setProfileFuture(CompletableFuture.completedFuture(prepared.profile()));
      game.champ$setPlayerSocialManager(new PlayerSocialManager(mc, prepared.api()));
      game.champ$setProfileKeyPairManager(
         prepared.offline() ? ProfileKeyPairManager.EMPTY_KEY_MANAGER : ProfileKeyPairManager.create(prepared.api(), prepared.user(), mc.gameDirectory.toPath())
      );
      game.champ$setReportingContext(ReportingContext.create(ReportEnvironment.local(), prepared.api()));
   }

   private YggdrasilAuthenticationService mojangAuth(Minecraft mc) {
      if (this.mojangAuth == null) {
         this.mojangAuth = new YggdrasilAuthenticationService(mc.getProxy());
      }

      return this.mojangAuth;
   }

   @Environment(EnvType.CLIENT)
   record Prepared(User user, UserApiService api, UserProperties properties, ProfileResult profile, boolean offline) {
   }
}
