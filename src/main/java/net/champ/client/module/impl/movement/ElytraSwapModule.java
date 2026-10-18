package net.champ.client.module.impl.movement;

import com.example.client.mixin.AccessorElytraFlyMultiPlayerGameMode;
import com.example.client.mixin.AccessorLocalPlayer;
import net.champ.client.chat.ModuleChat;
import net.champ.client.event.EventPriority;
import net.champ.client.event.Stage;
import net.champ.client.event.impl.KeyInputEvent;
import net.champ.client.event.impl.MouseInputEvent;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.input.ModuleKeybinds;
import net.champ.client.module.ClientModule;
import net.champ.client.module.CombatMacroLock;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.impl.combat.AutoInvTotemModule;
import net.champ.client.module.impl.movement.elytrafly.ElytraFlyInventory;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.KeySetting;
import net.champ.client.module.setting.NumberSetting;
import net.champ.client.util.pvp.InputControl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;

@Environment(EnvType.CLIENT)
public final class ElytraSwapModule extends ClientModule {
   private static final int[] SOURCE_INDICES = sourceIndices();
   private static final int MAX_WAIT_TICKS = 20;
   private static final int CHEST_SLOT_ID = 6;
   private static final Component SWAP_FAILED = ModuleChat.body().danger("Swap failed").text(", the item didn't move into the chest slot").build();
   private final KeySetting key = this.addSetting("key", new KeySetting("Key", -1));
   private final BooleanSetting swapBackOnLanding = this.addSetting("swap-back-on-landing", new BooleanSetting("Swap Back on Landing", true));
   private final NumberSetting landingDelay = this.addSetting("landing-delay", new NumberSetting("Landing Delay", 4.0F, 0.0F, 20.0F).step(1.0F));
   private boolean pending;
   private int pendingTicks;
   private boolean armed;
   private boolean glided;
   private int groundTicks;
   private int settleTicks;
   private int storedIndex = -1;
   private Item storedItem;

   public ElytraSwapModule() {
      super("elytra-swap", "Elytra Swap", "Swaps between your chestplate and elytra with one key.", ModuleCategory.MOVEMENT);
      this.usesNotifications();
      this.landingDelay.visibleWhen(this.swapBackOnLanding::get);
   }

   @Override
   protected void onEnable() {
      this.reset();
      this.on(KeyInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onKey);
      this.on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onMouse);
      this.on(TickEvent.TYPE, Stage.PRE, EventPriority.LOWEST, this::onTick);
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   private void reset() {
      this.pending = false;
      this.pendingTicks = 0;
      this.disarm();
   }

   private void disarm() {
      this.armed = false;
      this.glided = false;
      this.groundTicks = 0;
      this.settleTicks = 0;
      this.storedIndex = -1;
      this.storedItem = null;
   }

   private void onKey(KeyInputEvent event) {
      if (event.action == 1) {
         this.onPress(event.key);
      }
   }

   private void onMouse(MouseInputEvent event) {
      if (event.action == 1) {
         this.onPress(1000 + event.button);
      }
   }

   private void onPress(int pressed) {
      int bound = this.key.value();
      if (ModuleKeybinds.isBindable(bound) && pressed == bound) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.screen == null && mc.player != null) {
            this.pending = true;
            this.pendingTicks = 0;
         }
      }
   }

   private void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || mc.level == null || mc.gameMode == null) {
         this.reset();
      } else if (!this.pending) {
         if (this.armed) {
            this.followFlight(mc, player);
         }
      } else {
         String busy = this.busyReason(player);
         if (busy == null) {
            if (mc.screen != null || this.swap(mc, player)) {
               this.pending = false;
               return;
            }

            busy = "you kept moving";
         }

         if (++this.pendingTicks > 20) {
            this.pending = false;
            ModuleChat.send(this, ModuleChat.body().warn("Swap skipped").text(", " + busy));
         }
      }
   }

   private String busyReason(LocalPlayer player) {
      if (CombatMacroLock.heldByOther(this)) {
         return "another macro kept the inventory busy";
      } else if (AutoInvTotemModule.busy()) {
         return "the totem refill kept the inventory busy";
      } else if (player.isUsingItem()) {
         return "you kept using an item";
      } else {
         return ((AccessorLocalPlayer)player).champ$lastSentInput().shift() ? "you kept sneaking" : null;
      }
   }

   private void followFlight(Minecraft mc, LocalPlayer player) {
      if (!this.swapBackOnLanding.get() || !player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER)) {
         this.disarm();
      } else if (player.isFallFlying()) {
         this.glided = true;
         this.groundTicks = 0;
      } else if (this.glided) {
         if (!player.onGround() && !player.isInWater()) {
            this.groundTicks = 0;
         } else if (this.groundTicks++ >= this.landingDelay.getInt()) {
            if (mc.screen == null && inventoryReady(player) && this.busyReason(player) == null) {
               if (!isBound(player, player.getItemBySlot(EquipmentSlot.CHEST))) {
                  int chestplate = this.chestplateToEquip(player);
                  if (chestplate == -1) {
                     ModuleChat.send(this, ModuleChat.body().danger("Can't swap back").text(", no ").name("chestplate").text(" left in your inventory"));
                  } else if (!AutoInvTotemModule.clickSafe(player)) {
                     if (++this.settleTicks <= 20) {
                        InputControl.idle();
                        return;
                     }

                     ModuleChat.send(this, ModuleChat.body().warn("Swap back skipped").text(", you kept moving"));
                  } else if (!equip(mc, player, chestplate)) {
                     ModuleChat.send(this, SWAP_FAILED);
                  }
               }

               this.disarm();
            }
         }
      }
   }

   private boolean swap(Minecraft mc, LocalPlayer player) {
      if (player.isSpectator()) {
         return true;
      }

      if (!inventoryReady(player)) {
         ModuleChat.send(this, ModuleChat.body().warn("Swap skipped").text(", close the open container first"));
         return true;
      }

      ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
      if (isBound(player, worn)) {
         ModuleChat.send(this, ModuleChat.body().danger("Can't swap").text(", your chest piece has ").name("Curse of Binding"));
         return true;
      }

      if (worn.has(DataComponents.GLIDER)) {
         int chestplate = this.chestplateToEquip(player);
         if (chestplate == -1) {
            ModuleChat.send(this, ModuleChat.body().danger("Can't swap").text(", no ").name("chestplate").text(" in your inventory"));
         } else {
            if (!AutoInvTotemModule.clickSafe(player)) {
               InputControl.idle();
               return false;
            }

            if (!equip(mc, player, chestplate)) {
               ModuleChat.send(this, SWAP_FAILED);
            }
         }

         this.disarm();
         return true;
      } else {
         int elytra = findElytra(player);
         if (elytra == -1) {
            ModuleChat.send(this, ModuleChat.body().danger("Can't swap").text(", no usable ").name("elytra").text(" in your inventory"));
            return true;
         }

         if (!AutoInvTotemModule.clickSafe(player)) {
            InputControl.idle();
            return false;
         }

         Item takenOff = isChestplate(worn) ? worn.getItem() : null;
         if (!equip(mc, player, elytra)) {
            ModuleChat.send(this, SWAP_FAILED);
            return true;
         }

         this.disarm();
         if (takenOff != null) {
            this.armed = this.swapBackOnLanding.get();
            this.storedIndex = elytra;
            this.storedItem = takenOff;
         }

         return true;
      }
   }

   private int chestplateToEquip(LocalPlayer player) {
      if (this.storedIndex >= 0 && this.storedItem != null) {
         ItemStack stored = player.getInventory().getItem(this.storedIndex);
         if (stored.is(this.storedItem) && !isBound(player, stored)) {
            return this.storedIndex;
         }
      }

      return findBestChestplate(player);
   }

   private static boolean equip(Minecraft mc, LocalPlayer player, int index) {
      Item item = player.getInventory().getItem(index).getItem();
      ((AccessorElytraFlyMultiPlayerGameMode)mc.gameMode).champ$elytraFlySyncSelected();
      if (!Inventory.isHotbarSlot(index) && index != 40) {
         ElytraFlyInventory.moveToChest(index);
      } else {
         mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, 6, index, ClickType.SWAP, player);
      }

      player.connection.send(new ServerboundContainerClosePacket(0));
      return player.getItemBySlot(EquipmentSlot.CHEST).is(item);
   }

   private static boolean inventoryReady(LocalPlayer player) {
      return player.containerMenu == player.inventoryMenu && player.containerMenu.getCarried().isEmpty();
   }

   private static boolean isBound(LocalPlayer player, ItemStack stack) {
      return !player.isCreative() && EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
   }

   private static int findElytra(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      int best = -1;
      int bestLeft = -1;

      for (int index : SOURCE_INDICES) {
         ItemStack stack = inventory.getItem(index);
         if (LivingEntity.canGlideUsing(stack, EquipmentSlot.CHEST) && !isBound(player, stack)) {
            int left = durabilityLeft(stack);
            if (left > bestLeft) {
               best = index;
               bestLeft = left;
            }
         }
      }

      return best;
   }

   private static int findBestChestplate(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      int best = -1;
      double bestScore = -1.0;
      int bestLeft = -1;

      for (int index : SOURCE_INDICES) {
         ItemStack stack = inventory.getItem(index);
         if (isChestplate(stack) && !isBound(player, stack)) {
            double score = armorScore(stack);
            int left = durabilityLeft(stack);
            if (score > bestScore || score == bestScore && left > bestLeft) {
               best = index;
               bestScore = score;
               bestLeft = left;
            }
         }
      }

      return best;
   }

   private static boolean isChestplate(ItemStack stack) {
      if (!stack.isEmpty() && !stack.has(DataComponents.GLIDER)) {
         Equippable equippable = (Equippable)stack.get(DataComponents.EQUIPPABLE);
         return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
      } else {
         return false;
      }
   }

   private static double armorScore(ItemStack stack) {
      ItemAttributeModifiers modifiers = (ItemAttributeModifiers)stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
      return modifiers.compute(Attributes.ARMOR, 0.0, EquipmentSlot.CHEST)
         + modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, EquipmentSlot.CHEST);
   }

   private static int durabilityLeft(ItemStack stack) {
      return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
   }

   private static int[] sourceIndices() {
      int[] indices = new int[37];
      int i = 0;

      while (i < 36) {
         indices[i] = i++;
      }

      indices[36] = 40;
      return indices;
   }
}
