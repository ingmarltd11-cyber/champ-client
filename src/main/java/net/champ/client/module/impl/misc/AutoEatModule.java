package net.champ.client.module.impl.misc;

import java.util.Set;
import net.champ.client.event.EventHandler;
import net.champ.client.event.EventPriority;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.CombatMacroLock;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.impl.combat.AutoInvTotemModule;
import net.champ.client.module.impl.combat.crystal.Crystals;
import net.champ.client.module.impl.combat.melee.MeleeAttacks;
import net.champ.client.module.impl.combat.melee.SlotReturn;
import net.champ.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;

@Environment(EnvType.CLIENT)
public final class AutoEatModule extends ClientModule {
   private static final Set<Item> NEVER_EAT = Set.of(
      Items.ROTTEN_FLESH,
      Items.SPIDER_EYE,
      Items.POISONOUS_POTATO,
      Items.PUFFERFISH,
      Items.CHICKEN,
      Items.CHORUS_FRUIT,
      Items.SUSPICIOUS_STEW,
      Items.ENCHANTED_GOLDEN_APPLE
   );
   private static final int NOT_FOUND = -1;
   private static final int OFFHAND_SLOT = -2;
   private static final float HEALING_BONUS = 100.0F;
   private static AutoEatModule instance;
   private final NumberSetting hunger = this.addSetting("hunger", new NumberSetting("Hunger", 16.0F, 0.0F, 20.0F).step(1.0F));
   private final NumberSetting health = this.addSetting("health", new NumberSetting("Health", 10.0F, 0.0F, 20.0F).step(1.0F));
   private final SlotReturn slots = new SlotReturn(this);
   private boolean eating;

   public AutoEatModule() {
      super("auto-eat", "Auto Eat", "Eats hotbar or offhand food once hunger or health drops too low.", ModuleCategory.MISC);
      instance = this;
   }

   @Override
   protected void onDisable() {
      Minecraft mc = Minecraft.getInstance();
      this.stop(mc);
      this.slots.drop(mc, true);
   }

   public static boolean isEating() {
      AutoEatModule module = instance;
      return module != null && module.isEnabled() && module.eating;
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      this.eat(mc);
      if (!this.eating) {
         this.slots.tick(mc, true);
      }
   }

   private void eat(Minecraft mc) {
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null && mc.gameMode != null && mc.screen == null && !AutoInvTotemModule.wantsHotbar()) {
         if (player.isUsingItem() && player.getUseItem().has(DataComponents.FOOD)) {
            if (this.eating) {
               mc.options.keyUse.setDown(true);
            }
         } else if (!this.shouldEat(player)) {
            this.stop(mc);
         } else {
            int slot = this.findBestSlot(player);
            if (slot == -1 || MeleeAttacks.actedThisTick(player) || !MeleeAttacks.handsFree(mc, player)) {
               this.stop(mc);
            } else if (CombatMacroLock.heldByOther(this)) {
               this.eating = true;
            } else {
               if (slot == -2) {
                  mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
                  MeleeAttacks.claimTick(mc);
               } else if (!this.slots.take(player, slot)) {
                  this.stop(mc);
                  return;
               }

               this.eating = true;
               MeleeAttacks.takeAttackClicks(mc);
               mc.options.keyUse.setDown(true);
            }
         }
      } else {
         this.stop(mc);
      }
   }

   private boolean hungry(LocalPlayer player) {
      return player.getFoodData().getFoodLevel() <= this.hunger.getInt();
   }

   private boolean shouldEat(LocalPlayer player) {
      boolean hurt = this.health.getInt() > 0 && player.getHealth() <= this.health.getInt();
      return this.hungry(player) || hurt;
   }

   private int findBestSlot(LocalPlayer player) {
      boolean healing = !this.hungry(player);
      Inventory inventory = player.getInventory();
      int best = -1;
      float bestScore = -1.0F;

      for (int slot = 0; slot < 9; slot++) {
         FoodProperties food = foodOf(player, inventory.getItem(slot));
         if (food != null) {
            float score = score(food, healing);
            if (score > bestScore) {
               bestScore = score;
               best = slot;
            }
         }
      }

      FoodProperties offhandFood = foodOf(player, player.getOffhandItem());
      if (offhandFood != null && score(offhandFood, healing) > bestScore) {
         best = -2;
      }

      return best;
   }

   private static float score(FoodProperties food, boolean healing) {
      float score = food.comp_2491() + food.comp_2492();
      return healing && food.comp_2493() ? score + 100.0F : score;
   }

   private static FoodProperties foodOf(LocalPlayer player, ItemStack stack) {
      if (!stack.isEmpty() && !NEVER_EAT.contains(stack.getItem()) && !player.getCooldowns().isOnCooldown(stack)) {
         FoodProperties food = (FoodProperties)stack.get(DataComponents.FOOD);
         return food != null && player.canEat(food.comp_2493()) ? food : null;
      } else {
         return null;
      }
   }

   private void stop(Minecraft mc) {
      if (this.eating) {
         this.eating = false;
         mc.options.keyUse.setDown(Crystals.isPhysicallyDown(mc, mc.options.keyUse));
      }
   }
}
