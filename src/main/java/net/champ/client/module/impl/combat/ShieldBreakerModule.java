package net.champ.client.module.impl.combat;

import net.champ.client.event.EventHandler;
import net.champ.client.event.EventPriority;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.CombatMacroLock;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.impl.combat.melee.MeleeAttacks;
import net.champ.client.module.impl.combat.melee.SlotReturn;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.ModeSetting;
import net.champ.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;

@Environment(EnvType.CLIENT)
public final class ShieldBreakerModule extends ClientModule {
   private static final String MODE_PASSIVE = "Passive";
   private static final String MODE_AUTO = "Auto";
   private static final int REARM_TICKS = 10;
   private static final int MAX_LEAD_TICKS = 4;
   private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Passive", "Passive", "Auto"));
   private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", true));
   private final SlotReturn slots = new SlotReturn(this);
   private int ticks;
   private int rearmTick;

   public ShieldBreakerModule() {
      super("shield-breaker", "Shield Breaker", "Swaps to an axe for any hit into a raised shield. Auto also makes the hit.", ModuleCategory.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.ticks = 0;
      this.rearmTick = 0;
   }

   @Override
   protected void onDisable() {
      this.slots.drop(Minecraft.getInstance(), this.switchBack.get());
   }

   public void beforeAttack(LocalPlayer player, Entity target) {
      if (target instanceof Player other && !MeleeAttacks.disablesShields(player.getMainHandItem())) {
         if (!MeleeAttacks.shieldBroken(other) && blocksSoon(player, other)) {
            int slot = bestAxe(player);
            if (slot >= 0 && this.slots.take(player, slot)) {
               MeleeAttacks.noteShieldBroken(other);
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onTick(TickEvent event) {
      this.ticks++;
      this.slots.tick(Minecraft.getInstance(), this.switchBack.get());
   }

   @EventHandler
   public void onAutoTick(TickEvent event) {
      if (this.mode.is("Auto")) {
         this.tryHit(Minecraft.getInstance());
      }
   }

   private void tryHit(Minecraft mc) {
      if (this.ticks >= this.rearmTick && MeleeAttacks.canAct(mc)) {
         if (!CombatMacroLock.heldByOther(this) && !AutoInvTotemModule.wantsHotbar()) {
            LocalPlayer player = mc.player;
            if (!MeleeAttacks.actedThisTick(player) && !ServerRotation.pending() && !MeleeAttacks.clickPending(mc)) {
               EntityHitResult hit = MeleeAttacks.crosshairHit(mc);
               if (hit != null && hit.getEntity() instanceof Player target && MeleeAttacks.isEnemy(player, target)) {
                  if (!MeleeAttacks.shieldBroken(target) && blocksSoon(player, target)) {
                     if (MeleeAttacks.disablesShields(player.getMainHandItem()) || bestAxe(player) >= 0) {
                        if (MeleeAttacks.attack(mc, hit)) {
                           this.rearmTick = this.ticks + 10;
                           MeleeAttacks.claimTick(mc);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean blocksSoon(LocalPlayer player, Player target) {
      if (!target.isUsingItem()) {
         return false;
      }

      ItemStack shield = target.getUseItem();
      BlocksAttacks blocks = (BlocksAttacks)shield.get(DataComponents.BLOCKS_ATTACKS);
      return blocks != null && target.getTicksUsingItem() + leadTicks(player) >= blocks.blockDelayTicks() ? MeleeAttacks.shieldBlocks(target, player, shield) : false;
   }

   private static int leadTicks(LocalPlayer player) {
      PlayerInfo info = player.connection.getPlayerInfo(player.getUUID());
      return info == null ? 0 : Mth.clamp(Math.round(info.getLatency() / 50.0F), 0, 4);
   }

   static int bestAxe(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      int best = -1;
      double bestDamage = Double.NEGATIVE_INFINITY;

      for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (MeleeAttacks.disablesShields(stack) && !stack.nextDamageWillBreak()) {
            double damage = MeleeAttacks.attackDamage(stack);
            if (damage > bestDamage) {
               best = slot;
               bestDamage = damage;
            }
         }
      }

      return best;
   }
}
