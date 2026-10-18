package net.champ.client.module.impl.render;

import com.example.client.mixin.AccessorHandPlayerModel;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.ModeSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemDisplayContext;

@Environment(EnvType.CLIENT)
public final class HandModelModule extends ClientModule {
   public static volatile HandModelModule active;
   private static final String ALL_ITEMS = "All Items";
   private static final String WEAPONS_TOOLS = "Weapons & Tools";
   private final ModeSetting items = this.addSetting("items", new ModeSetting("Items", "All Items", "All Items", "Weapons & Tools"));
   private final BooleanSetting mainHand = this.addSetting("main-hand", new BooleanSetting("Main Hand", true));
   private final BooleanSetting offHand = this.addSetting("off-hand", new BooleanSetting("Off Hand", true));

   public HandModelModule() {
      super("3d-hand", "3D Hand", "Shows your own arm holding the item in first person: swords, tools, items and blocks.", ModuleCategory.VISUALS);
   }

   @Override
   protected void onEnable() {
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
   }

   static boolean wants(boolean mainHand) {
      HandModelModule module = active;
      return module != null && (mainHand ? module.mainHand.get() : module.offHand.get());
   }

   static boolean covers(ItemStack stack, boolean mainHand) {
      HandModelModule module = active;
      return wants(mainHand) && module != null && (module.items.is("All Items") || weaponOrTool(stack));
   }

   private static boolean weaponOrTool(ItemStack stack) {
      return stack.is(ItemTags.SWORDS)
         || stack.is(ItemTags.AXES)
         || stack.is(ItemTags.PICKAXES)
         || stack.is(ItemTags.SHOVELS)
         || stack.is(ItemTags.HOES)
         || stack.is(ItemTags.SPEARS)
         || stack.is(Items.MACE)
         || stack.is(Items.FISHING_ROD)
         || stack.is(Items.SHEARS)
         || stack.is(Items.FLINT_AND_STEEL)
         || stack.is(Items.BRUSH)
         || stack.is(Items.CARROT_ON_A_STICK)
         || stack.is(Items.WARPED_FUNGUS_ON_A_STICK);
   }

   public static void shoulder(PoseStack pose, boolean right, AvatarRenderer<?> renderer) {
      FirstPersonArm.shoulder(pose, right, ((AccessorHandPlayerModel)renderer.getModel()).champ$handSlim());
   }

   public static void renderArm(PoseStack pose, LivingEntity holder, ItemStack stack, ItemDisplayContext context, SubmitNodeCollector collector, int light, boolean mainHand) {
      if (covers(stack, mainHand)) {
         FirstPersonArm.Arm arm = FirstPersonArm.arm();
         if (arm != null) {
            FirstPersonArm.hold(pose, arm, stack, context, holder, collector, light, 0.0F, 0.0F, 0.0F);
         }
      }
   }
}
