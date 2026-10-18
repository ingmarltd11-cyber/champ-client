package net.champ.client.module.impl.combat;

import net.champ.client.chat.ModuleChat;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
public final class FriendGuardModule extends ClientModule {
   private static final long HINT_COOLDOWN_MS = 1500L;
   private static volatile FriendGuardModule active;
   private long lastHintAt;

   public FriendGuardModule() {
      super("friend-guard", "Friend Guard", "Stops you from hitting players on your friends list.", ModuleCategory.MISC);
      this.usesNotifications();
   }

   @Override
   protected void onEnable() {
      this.lastHintAt = 0L;
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
      this.lastHintAt = 0L;
   }

   public static boolean intercepts(Player attacker, Entity target) {
      FriendGuardModule guard = active;
      if (!(guard != null && target instanceof Player friend) || friend == attacker) {
         return false;
      } else if (attacker == Minecraft.getInstance().player && MeleeAttacks.isFriend(friend)) {
         guard.hint(friend);
         return true;
      } else {
         return false;
      }
   }

   public static boolean interceptsStab(LocalPlayer player) {
      FriendGuardModule guard = active;
      if (guard != null && player != null) {
         Entity friend = MeleeAttacks.stabTarget(player, entity -> entity instanceof Player other && MeleeAttacks.isFriend(other));
         if (friend == null) {
            return false;
         }

         guard.hint((Player)friend);
         return true;
      } else {
         return false;
      }
   }

   private void hint(Player friend) {
      if (this.notificationsOn()) {
         long now = System.currentTimeMillis();
         if (now - this.lastHintAt >= 1500L) {
            this.lastHintAt = now;
            ModuleChat.actionBar(this, ModuleChat.body().text("Held back your hit on ").name(friend.getGameProfile().name()));
         }
      }
   }
}
