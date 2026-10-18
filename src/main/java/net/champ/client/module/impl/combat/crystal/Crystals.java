package net.champ.client.module.impl.combat.crystal;

import com.example.client.mixin.AccessorLocalPlayer;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.champ.client.input.ChampKeyPolling;
import net.champ.client.input.ModuleKeybinds;
import net.champ.client.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Holder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import com.mojang.blaze3d.platform.InputConstants.Key;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class Crystals {
   public static final int READY = -2;
   public static final int NO_ROUTE = -1;
   private static final int MIN_CONFIRM_TICKS = 4;
   private static final int MAX_CONFIRM_TICKS = 20;
   private static final float RTT_SMOOTHING = 0.25F;
   private static final double RESERVE_RANGE = 12.0;
   private static final Set<Block> USABLE_BLOCKS = Set.of(
      Blocks.RESPAWN_ANCHOR,
      Blocks.NOTE_BLOCK,
      Blocks.LEVER,
      Blocks.REPEATER,
      Blocks.REDSTONE_WIRE,
      Blocks.CAKE,
      Blocks.COMPOSTER,
      Blocks.DRAGON_EGG
   );
   private static final Long2IntOpenHashMap PLACED_AT = new Long2IntOpenHashMap();
   private static float measuredRtt;

   private Crystals() {
   }

   public static boolean isBase(BlockState state) {
      return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
   }

   public static Vec3 explosionAt(BlockPos base) {
      return Vec3.atBottomCenterOf(base.above());
   }

   public static AABB boxOn(BlockPos base) {
      return EntityType.END_CRYSTAL.getDimensions().makeBoundingBox(explosionAt(base));
   }

   public static boolean hasCrystal(Level level, BlockPos base, Entity ignore) {
      BlockPos above = base.above();

      for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, new AABB(above))) {
         if (crystal != ignore && crystal.blockPosition().equals(above)) {
            return true;
         }
      }

      return false;
   }

   public static boolean canHost(Level level, BlockPos base, Entity ignore) {
      BlockPos above = base.above();
      if (!level.isEmptyBlock(above)) {
         return false;
      }

      AABB space = new AABB(
         above.getX(), above.getY(), above.getZ(), above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0
      );

      for (Entity entity : level.getEntities(ignore, space)) {
         if (entity.isAlive()) {
            return false;
         }
      }

      return true;
   }

   public static BlockPos obsidianTarget(ClientLevel level, LocalPlayer player, BlockHitResult hit, BlockState clicked) {
      BlockPos target = placedObsidian(level, player, hit, clicked);
      return target != null && canHost(level, target, null) ? target : null;
   }

   public static BlockPos placedObsidian(ClientLevel level, LocalPlayer player, BlockHitResult hit, BlockState clicked) {
      if (!player.isSecondaryUseActive() && reactsToUse(level, hit.getBlockPos(), clicked)) {
         return null;
      } else {
         BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(Items.OBSIDIAN), hit);
         if (!context.canPlace()) {
            return null;
         } else {
            BlockPos target = context.getClickedPos();
            if (level.isOutsideBuildHeight(target) || !level.getWorldBorder().isWithinBounds(target)) {
               return null;
            } else {
               return !level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), target, CollisionContext.placementContext(player)) ? null : target;
            }
         }
      }
   }

   private static boolean reactsToUse(Level level, BlockPos pos, BlockState state) {
      Block block = state.getBlock();
      if (block instanceof EntityBlock || USABLE_BLOCKS.contains(block)) {
         return true;
      } else {
         return !state.is(BlockTags.DOORS)
               && !state.is(BlockTags.TRAPDOORS)
               && !state.is(BlockTags.FENCE_GATES)
               && !state.is(BlockTags.BUTTONS)
               && !state.is(BlockTags.CAULDRONS)
               && !state.is(BlockTags.CANDLE_CAKES)
               && !state.is(BlockTags.FLOWER_POTS)
            ? state.getMenuProvider(level, pos) != null
            : true;
      }
   }

   public static int route(LocalPlayer player, Item item, BlockState clicked, int preferredSlot) {
      ItemStack main = player.getMainHandItem();
      if (main.is(item)) {
         return -2;
      }

      boolean inOffhand = player.getOffhandItem().is(item);
      if (inOffhand && letsClickThrough(main, clicked)) {
         return -2;
      }

      Inventory inventory = player.getInventory();
      int slot = hotbarSlotOf(inventory, item);
      if (slot != -1) {
         return slot;
      }

      if (!inOffhand) {
         return -1;
      }

      if (Inventory.isHotbarSlot(preferredSlot) && letsClickThrough(inventory.getItem(preferredSlot), clicked)) {
         return preferredSlot;
      }

      for (int var8 = 0; var8 < Inventory.getSelectionSize(); var8++) {
         if (letsClickThrough(inventory.getItem(var8), clicked)) {
            return var8;
         }
      }

      return -1;
   }

   public static boolean letsClickThrough(ItemStack stack, BlockState clicked) {
      if (stack.isEmpty()) {
         return true;
      } else if (!stack.has(DataComponents.CONSUMABLE) && !stack.has(DataComponents.BLOCKS_ATTACKS) && !stack.has(DataComponents.KINETIC_WEAPON)) {
         Equippable equippable = (Equippable)stack.get(DataComponents.EQUIPPABLE);
         return equippable != null && equippable.comp_3213()
            ? false
            : stack.is(ItemTags.SWORDS)
               || stack.is(Items.MACE)
               || stack.is(Items.TOTEM_OF_UNDYING)
               || stack.is(ItemTags.AXES) && isBase(clicked);
      } else {
         return false;
      }
   }

   public static boolean isCombatItem(ItemStack stack) {
      return stack.isEmpty()
         || stack.is(ItemTags.SWORDS)
         || stack.is(ItemTags.AXES)
         || stack.is(Items.MACE)
         || stack.is(Items.TOTEM_OF_UNDYING);
   }

   public static int hotbarSlotOf(Inventory inventory, Item item) {
      for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
         if (inventory.getItem(slot).is(item)) {
            return slot;
         }
      }

      return -1;
   }

   public static boolean strike(Minecraft mc, LocalPlayer player, Entity target) {
      if (player != null && mc.gameMode != null && target != null && !target.isRemoved() && !player.isSpectator()) {
         if (!MeleeAttacks.actedThisTick(player)
            && MeleeAttacks.handsFree(mc, player)
            && player.isSprinting() == ((AccessorLocalPlayer)player).champ$wasSprinting()) {
            mc.gameMode.attack(player, target);
            player.swing(InteractionHand.MAIN_HAND);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean rightClick(Minecraft mc, LocalPlayer player, BlockHitResult hit) {
      if (!MeleeAttacks.actedThisTick(player) && MeleeAttacks.handsFree(mc, player)) {
         InteractionHand hand = letsClickThrough(player.getMainHandItem(), mc.level.getBlockState(hit.getBlockPos()))
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND;
         ItemStack stack = player.getItemInHand(hand);
         if (!stack.isItemEnabled(mc.level.enabledFeatures())) {
            return false;
         }

         int countBefore = stack.getCount();
         if (mc.gameMode.useItemOn(player, hand, hit) instanceof Success success && success.comp_2909() == SwingSource.CLIENT) {
            player.swing(hand);
            if (!stack.isEmpty() && (stack.getCount() != countBefore || player.hasInfiniteMaterials())) {
               mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static int weaponRoute(LocalPlayer player) {
      ItemStack main = player.getMainHandItem();
      if (!swings(player, main) || !enchantsAddDamage(main) && !dealsDamage(player, main)) {
         Inventory inventory = player.getInventory();
         int fallback = -1;

         for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (swings(player, stack)) {
               if (enchantsAddDamage(stack)) {
                  return slot;
               }

               if (fallback == -1 && dealsDamage(player, stack)) {
                  fallback = slot;
               }
            }
         }

         return fallback;
      } else {
         return -2;
      }
   }

   private static boolean swings(LocalPlayer player, ItemStack stack) {
      return !stack.has(DataComponents.PIERCING_WEAPON) && !player.cannotAttackWithItem(stack, 0);
   }

   public static boolean enchantsAddDamage(ItemStack weapon) {
      float bonus = 0.0F;

      for (Entry<Holder<Enchantment>> entry : weapon.getEnchantments().entrySet()) {
         Enchantment enchantment = (Enchantment)((Holder)entry.getKey()).comp_349();

         for (ConditionalEffect<EnchantmentValueEffect> effect : enchantment.getEffects(EnchantmentEffectComponents.DAMAGE)) {
            if (effect.comp_2681().isPresent() || !(effect.comp_2680() instanceof AddValue add)) {
               return false;
            }

            bonus += add.comp_2704().calculate(entry.getIntValue());
         }
      }

      return bonus > 0.0F;
   }

   public static boolean dealsDamage(LivingEntity player, ItemStack mainHand) {
      AttributeInstance damage = new AttributeInstance(Attributes.ATTACK_DAMAGE, changed -> {});
      damage.setBaseValue(player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));

      for (MobEffectInstance effect : player.getActiveEffects()) {
         ((MobEffect)effect.getEffect().comp_349()).createModifiers(effect.getAmplifier(), (attribute, modifier) -> include(damage, attribute, modifier));
      }

      for (EquipmentSlot slot : EquipmentSlot.VALUES) {
         ItemStack stack = slot == EquipmentSlot.MAINHAND ? mainHand : player.getItemBySlot(slot);
         if (!stack.isEmpty() && !stack.isBroken()) {
            stack.forEachModifier(slot, (attribute, modifier) -> include(damage, attribute, modifier));
         }
      }

      return (float)damage.getValue() > 0.0F;
   }

   private static void include(AttributeInstance damage, Holder<Attribute> attribute, AttributeModifier modifier) {
      if (Attributes.ATTACK_DAMAGE.equals(attribute)) {
         damage.removeModifier(modifier.comp_2447());
         damage.addTransientModifier(modifier);
      }
   }

   public static int confirmWindow(Minecraft mc, LocalPlayer player) {
      return Mth.clamp(rttTicks(mc, player) + 2, 4, 20);
   }

   public static int rttTicks(Minecraft mc, LocalPlayer player) {
      return measuredRtt > 0.0F ? Math.round(measuredRtt) : latencyMs(mc, player) / 50 + 1;
   }

   public static void notePlace(LocalPlayer player, BlockPos base) {
      ObjectIterator<it.unimi.dsi.fastutil.longs.Long2IntMap.Entry> iterator = PLACED_AT.long2IntEntrySet().iterator();

      while (iterator.hasNext()) {
         int age = player.tickCount - ((it.unimi.dsi.fastutil.longs.Long2IntMap.Entry)iterator.next()).getIntValue();
         if (age < 0 || age > 20) {
            iterator.remove();
         }
      }

      PLACED_AT.put(base.asLong(), player.tickCount);
   }

   public static void noteSpawn(LocalPlayer player, BlockPos base) {
      long key = base.asLong();
      if (PLACED_AT.containsKey(key)) {
         int ticks = player.tickCount - PLACED_AT.remove(key);
         if (ticks > 0 && ticks <= 20) {
            measuredRtt = measuredRtt > 0.0F ? measuredRtt + (ticks - measuredRtt) * 0.25F : ticks;
         }
      }
   }

   public static boolean placing(LocalPlayer player, BlockPos base, int window) {
      long key = base.asLong();
      return PLACED_AT.containsKey(key) && sent(player, PLACED_AT.get(key), window);
   }

   public static boolean anyPlacing(LocalPlayer player, int window) {
      IntIterator var2 = PLACED_AT.values().iterator();

      while (var2.hasNext()) {
         int tick = (Integer)var2.next();
         if (sent(player, tick, window)) {
            return true;
         }
      }

      return false;
   }

   public static List<AABB> reservedBoxes(LocalPlayer player, int window) {
      List<AABB> boxes = new ArrayList<>();

      for (EndCrystal crystal : player.level().getEntitiesOfClass(EndCrystal.class, player.getBoundingBox().inflate(12.0))) {
         if (crystal.isAlive()) {
            boxes.add(crystal.getBoundingBox());
         }
      }

      ObjectIterator var5 = PLACED_AT.long2IntEntrySet().iterator();

      while (var5.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2IntMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry)var5.next();
         if (sent(player, entry.getIntValue(), window)) {
            boxes.add(boxOn(BlockPos.of(entry.getLongKey())));
         }
      }

      return boxes;
   }

   private static boolean sent(LocalPlayer player, int tick, int window) {
      int age = player.tickCount - tick;
      return age >= 0 && age < window;
   }

   public static void forgetRtt() {
      PLACED_AT.clear();
      measuredRtt = 0.0F;
   }

   private static int latencyMs(Minecraft mc, LocalPlayer player) {
      ClientPacketListener connection = mc.getConnection();
      if (connection == null) {
         return 0;
      }

      PlayerInfo info = connection.getPlayerInfo(player.getUUID());
      return info == null ? 0 : Math.max(info.getLatency(), 0);
   }

   public static boolean isKeyDown(Minecraft mc, int key) {
      if (!ModuleKeybinds.isBindable(key)) {
         return false;
      } else {
         return key >= 1000 ? GLFW.glfwGetMouseButton(mc.getWindow().handle(), key - 1000) == 1 : ChampKeyPolling.isKeyDown(mc.getWindow(), key);
      }
   }

   public static boolean isPhysicallyDown(Minecraft mc, KeyMapping mapping) {
      Key key;
      try {
         key = InputConstants.getKey(mapping.saveString());
      } catch (IllegalArgumentException unknown) {
         return false;
      }

      int value = key.getValue();
      if (value < 0) {
         return false;
      }

      return switch (key.getType()) {
         case field_1672 -> GLFW.glfwGetMouseButton(mc.getWindow().handle(), value) == 1;
         case field_1668 -> ChampKeyPolling.isKeyDown(mc.getWindow(), value);
         default -> false;
      };
   }
}
