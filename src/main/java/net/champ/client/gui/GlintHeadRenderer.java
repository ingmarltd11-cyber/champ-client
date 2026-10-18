package net.champ.client.gui;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.renderer.special.SpecialModelRenderer.Unbaked;
import net.minecraft.client.renderer.special.SpecialModelRenderer.BakingContext;
import net.minecraft.world.level.block.SkullBlock.Types;
import net.minecraft.client.model.object.skull.SkullModelBase.State;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public final class GlintHeadRenderer implements NoDataSpecialModelRenderer {
   public static final Identifier ID = Identifier.fromNamespaceAndPath("champ", "glint_head");
   private final SkullModelBase model;

   private GlintHeadRenderer(SkullModelBase model) {
      this.model = model;
   }

   public static ItemStack stack() {
      ItemStack head = new ItemStack(Items.PLAYER_HEAD);
      head.set(DataComponents.ITEM_MODEL, ID);
      head.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
      return head;
   }

   public void method_65699(ItemDisplayContext context, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
      SkullBlockRenderer.submitSkull(null, 180.0F, 0.0F, pose, collector, light, this.model, PlayerSkinRenderCache.DEFAULT_PLAYER_SKIN_RENDER_TYPE, outline, null);
      if (foil) {
         pose.pushPose();
         pose.translate(0.5F, 0.0F, 0.5F);
         pose.scale(-1.0F, -1.0F, 1.0F);
         State state = new State();
         state.yRot = 180.0F;
         collector.order(1).submitModel(this.model, state, pose, RenderTypes.entityGlint(), light, OverlayTexture.NO_OVERLAY, 0, null);
         pose.popPose();
      }
   }

   public void method_72175(Consumer<Vector3fc> output) {
      PoseStack pose = new PoseStack();
      pose.translate(0.5F, 0.0F, 0.5F);
      pose.scale(-1.0F, -1.0F, 1.0F);
      this.model.root().getExtentsForGui(pose, output);
   }

   @Environment(EnvType.CLIENT)
   public record Unbaked() implements Unbaked {
      public static final MapCodec<GlintHeadRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new GlintHeadRenderer.Unbaked());

      public MapCodec<GlintHeadRenderer.Unbaked> method_65696() {
         return MAP_CODEC;
      }

      public SpecialModelRenderer<?> method_65698(BakingContext context) {
         SkullModelBase model = SkullBlockRenderer.createModel(context.comp_3391(), Types.PLAYER);
         return model == null ? null : new GlintHeadRenderer(model);
      }
   }
}
