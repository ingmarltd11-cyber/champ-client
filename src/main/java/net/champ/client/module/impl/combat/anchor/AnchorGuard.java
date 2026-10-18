package net.champ.client.module.impl.combat.anchor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.champ.client.module.impl.combat.crystal.Crystals;
import net.champ.client.module.impl.combat.melee.MeleeAttacks;
import net.champ.client.util.pvp.Rotation;
import net.champ.client.util.pvp.RotationUtils;
import net.champ.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.ClipContext.Block;

@Environment(EnvType.CLIENT)
public final class AnchorGuard {
   public static final float BLAST_POWER = 5.0F;
   private static final double MIN_SHADE = 0.15;
   private static final double FACE_INSET = 0.3;
   private static final double AIM_INSET = 0.1;

   private AnchorGuard() {
   }

   public static AnchorGuard.Spot find(ClientLevel level, LocalPlayer player, BlockPos anchor, Vec3 sightline) {
      Vec3 blast = Vec3.atCenterOf(anchor);
      AABB body = player.getBoundingBox();
      List<Vec3> points = bodyPoints(body);
      List<Vec3> open = new ArrayList<>(points.size());

      for (Vec3 point : points) {
         BlockHitResult hit = level.clip(new ClipContext(point, blast, Block.COLLIDER, Fluid.NONE, player));
         if (hit.getType() == Type.MISS || hit.getBlockPos().equals(anchor)) {
            open.add(point);
         }
      }

      BlockPos feet = player.blockPosition();
      if (open.size() < Math.ceil(points.size() * 0.15)) {
         return new AnchorGuard.Spot(feet, null, null, null);
      }

      int needed = (int)Math.ceil(open.size() * 0.15);
      Vec3 eye = player.getEyePosition();
      Set<BlockPos> around = new LinkedHashSet<>();

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) {
               around.add(feet.offset(dx, 0, dz));
            }
         }
      }

      for (Direction side : Plane.HORIZONTAL) {
         around.add(anchor.relative(side));
      }

      List<AnchorGuard.Cell> cells = new ArrayList<>(around.size());

      for (BlockPos pos : around) {
         AABB box = new AABB(pos);
         if (!pos.equals(anchor) && !box.intersects(body) && !box.clip(eye, sightline).isPresent()) {
            int shade = shade(box, blast, open);
            if (shade >= needed) {
               cells.add(new AnchorGuard.Cell(pos, shade, box.getCenter().distanceToSqr(blast)));
            }
         }
      }

      cells.sort(Comparator.comparingInt(AnchorGuard.Cell::shade).reversed().thenComparingDouble(AnchorGuard.Cell::distance));

      for (AnchorGuard.Cell cell : cells) {
         BlockState state = level.getBlockState(cell.pos());
         if (state.canBeReplaced()) {
            AnchorGuard.Spot spot = state.getShape(level, cell.pos()).isEmpty()
               ? supportFor(level, player, eye, cell.pos())
               : direct(level, player, eye, cell.pos(), state);
            if (spot != null) {
               return spot;
            }
         }
      }

      return null;
   }

   public static BlockHitResult hitAlong(ClientLevel level, LocalPlayer player, Rotation look, AnchorGuard.Spot spot) {
      BlockHitResult hit = trace(level, player, look);
      if (hit == null || !hit.getBlockPos().equals(spot.support())) {
         return null;
      }

      if (spot.face() != null && hit.getDirection() != spot.face()) {
         return null;
      }

      BlockPos placed = Crystals.placedObsidian(level, player, hit, level.getBlockState(spot.support()));
      return spot.pos().equals(placed) ? hit : null;
   }

   public static BlockHitResult anchorHit(ClientLevel level, LocalPlayer player, BlockPos anchor) {
      Rotation sent = ServerRotation.sent();
      BlockHitResult hit = sent == null ? null : trace(level, player, sent);
      return hit != null && hit.getBlockPos().equals(anchor) ? hit : null;
   }

   public static boolean isGuardBlock(ItemStack stack) {
      if (stack.getItem() instanceof BlockItem item) {
         Block block = item.getBlock();
         return !(block instanceof EntityBlock) && !(block instanceof FallingBlock) && block != Blocks.RESPAWN_ANCHOR && block != Blocks.TNT
            ? block.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
            : false;
      } else {
         return false;
      }
   }

   public static int detonatorSlot(Inventory inventory) {
      int totem = Crystals.hotbarSlotOf(inventory, Items.TOTEM_OF_UNDYING);
      if (totem != -1) {
         return totem;
      }

      for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
         if (Crystals.isCombatItem(inventory.getItem(slot))) {
            return slot;
         }
      }

      return -1;
   }

   public static int detonatorRoute(Inventory inventory, ItemStack main) {
      if (Crystals.isCombatItem(main)) {
         return -2;
      }

      int slot = detonatorSlot(inventory);
      return slot == -1 ? -1 : slot;
   }

   public static Vec3 anchorAim(ClientLevel level, LocalPlayer player, BlockPos anchor) {
      Vec3 eye = player.getEyePosition();
      AABB box = new AABB(anchor).deflate(0.1);
      Vec3 point = new Vec3(
         Mth.clamp(eye.x, box.minX, box.maxX),
         Mth.clamp(eye.y, box.minY, box.maxY),
         Mth.clamp(eye.z, box.minZ, box.maxZ)
      );
      BlockHitResult hit = trace(level, player, ServerRotation.next(player, RotationUtils.fromTo(eye, point)));
      return hit != null && hit.getBlockPos().equals(anchor) ? point : null;
   }

   public static void click(Minecraft mc, LocalPlayer player, BlockHitResult hit) {
      ItemStack held = player.getMainHandItem();
      int countBefore = held.getCount();
      if (mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit) instanceof Success success && success.comp_2909() == SwingSource.CLIENT) {
         player.swing(InteractionHand.MAIN_HAND);
         if (!held.isEmpty() && (held.getCount() != countBefore || player.hasInfiniteMaterials())) {
            mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);
         }
      }

      MeleeAttacks.claimTick(mc);
   }

   private static AnchorGuard.Spot direct(ClientLevel level, LocalPlayer player, Vec3 eye, BlockPos pos, BlockState state) {
      AnchorGuard.Spot spot = new AnchorGuard.Spot(pos, pos, null, state.getShape(level, pos).bounds().move(pos).getCenter());
      Rotation look = ServerRotation.next(player, RotationUtils.fromTo(eye, spot.aim()));
      return hitAlong(level, player, look, spot) != null ? spot : null;
   }

   public static AnchorGuard.Spot supportFor(ClientLevel level, LocalPlayer player, Vec3 eye, BlockPos cell) {
      AnchorGuard.Spot best = null;
      double bestDistance = Double.MAX_VALUE;

      for (Direction side : Direction.values()) {
         BlockPos support = cell.relative(side);
         BlockState state = level.getBlockState(support);
         if (!state.canBeReplaced()) {
            VoxelShape shape = state.getShape(level, support);
            if (!shape.isEmpty()) {
               Direction face = side.getOpposite();
               AnchorGuard.Spot spot = null;

               for (Vec3 aim : faceAims(shape.bounds().move(support), face)) {
                  AnchorGuard.Spot candidate = new AnchorGuard.Spot(cell, support, face, aim);
                  Rotation look = ServerRotation.next(player, RotationUtils.fromTo(eye, aim));
                  if (hitAlong(level, player, look, candidate) != null) {
                     spot = candidate;
                     break;
                  }
               }

               if (spot != null) {
                  double distance = side == Direction.DOWN ? -1.0 : eye.distanceToSqr(spot.aim());
                  if (distance < bestDistance) {
                     best = spot;
                     bestDistance = distance;
                  }
               }
            }
         }
      }

      return best;
   }

   private static List<Vec3> faceAims(AABB box, Direction face) {
      Axis axis = face.getAxis();
      double plane = face.getAxisDirection() == AxisDirection.POSITIVE ? box.max(axis) : box.min(axis);
      Vec3 center = box.getCenter().with(axis, plane);
      List<Vec3> aims = new ArrayList<>(5);
      aims.add(center);

      for (Direction toward : Direction.values()) {
         Axis along = toward.getAxis();
         if (along != axis) {
            aims.add(center.relative(toward, (box.max(along) - box.min(along)) * 0.3));
         }
      }

      return aims;
   }

   public static BlockHitResult trace(ClientLevel level, LocalPlayer player, Rotation look) {
      Vec3 eye = player.getEyePosition();
      Vec3 reach = Vec3.directionFromRotation(look.pitch(), look.yaw()).scale(player.blockInteractionRange());
      BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(reach), Block.OUTLINE, Fluid.NONE, player));
      return hit.getType() == Type.BLOCK ? hit : null;
   }

   private static int shade(AABB box, Vec3 blast, List<Vec3> points) {
      int blocked = 0;

      for (Vec3 point : points) {
         if (box.clip(point, blast).isPresent()) {
            blocked++;
         }
      }

      return blocked;
   }

   private static List<Vec3> bodyPoints(AABB body) {
      double stepX = 1.0 / ((body.maxX - body.minX) * 2.0 + 1.0);
      double stepY = 1.0 / ((body.maxY - body.minY) * 2.0 + 1.0);
      double stepZ = 1.0 / ((body.maxZ - body.minZ) * 2.0 + 1.0);
      double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
      double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
      List<Vec3> points = new ArrayList<>();

      for (double fx = 0.0; fx <= 1.0; fx += stepX) {
         for (double fy = 0.0; fy <= 1.0; fy += stepY) {
            for (double fz = 0.0; fz <= 1.0; fz += stepZ) {
               points.add(
                  new Vec3(
                     Mth.lerp(fx, body.minX, body.maxX) + offsetX,
                     Mth.lerp(fy, body.minY, body.maxY),
                     Mth.lerp(fz, body.minZ, body.maxZ) + offsetZ
                  )
               );
            }
         }
      }

      return points;
   }

   @Environment(EnvType.CLIENT)
   private record Cell(BlockPos pos, int shade, double distance) {
   }

   @Environment(EnvType.CLIENT)
   public record Spot(BlockPos pos, BlockPos support, Direction face, Vec3 aim) {
      public boolean standing() {
         return this.support == null;
      }
   }
}
