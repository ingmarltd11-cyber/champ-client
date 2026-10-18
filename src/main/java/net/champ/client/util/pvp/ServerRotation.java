package net.champ.client.util.pvp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
public final class ServerRotation {
   private static Rotation requested;
   private static Rotation committed;
   private static Rotation server;
   private static boolean silent;
   private static LocalPlayer owner;
   private static float ownYaw;
   private static float ownPitch;
   private static boolean swapped;
   private static Rotation walked;

   private ServerRotation() {
   }

   public static void request(Rotation rotation) {
      if (requested == null && committed == null) {
         requested = rotation;
      }
   }

   public static boolean pending() {
      return requested != null || committed != null;
   }

   public static Rotation sent() {
      return server;
   }

   public static boolean canRotate(LocalPlayer player) {
      return !player.isFallFlying() && !player.isAutoSpinAttack() && !player.isPassenger() && !player.isSwimming() && player.connection.hasClientLoaded();
   }

   public static Rotation next(LocalPlayer player, Rotation wanted) {
      Rotation base = server != null ? server : new Rotation(player.getYRot(), player.getXRot());
      double k = RotationUtils.mouseScale(Minecraft.getInstance(), player);
      float step = (float)k * 0.15F;
      long yawCounts = Math.round(Mth.wrapDegrees(wanted.yaw() - base.yaw()) / step);
      long pitchCounts = Math.round((wanted.pitch() - base.pitch()) / step);
      float yaw = base.yaw() + (float)(yawCounts * k) * 0.15F;
      float pitch = Mth.clamp(base.pitch() + (float)(pitchCounts * k) * 0.15F, -90.0F, 90.0F);
      return new Rotation(yaw, pitch);
   }

   public static void startTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != owner) {
         owner = player;
         server = null;
      }

      requested = null;
      committed = null;
      if (player != null && server != null && !(Math.abs(player.getYRot() - server.yaw()) <= 180.0F)) {
         float shift = server.yaw() + Mth.wrapDegrees(player.getYRot() - server.yaw()) - player.getYRot();
         player.setYRot(player.getYRot() + shift);
         player.yRotO += shift;
         player.yBob += shift;
         player.yBobO += shift;
         player.yHeadRot += shift;
         player.yHeadRotO += shift;
         player.yBodyRot += shift;
         player.yBodyRotO += shift;
      }
   }

   public static void noteSent(Packet<?> packet) {
      if (packet instanceof ServerboundMovePlayerPacket move && move.hasRotation() && Minecraft.getInstance().isSameThread()) {
         server = new Rotation(move.getYRot(0.0F), move.getXRot(0.0F));
         silent = swapped;
         owner = Minecraft.getInstance().player;
      }
   }

   public static Rotation commit(LocalPlayer player) {
      if (committed == null) {
         committed = requested != null && canRotate(player) ? next(player, requested) : new Rotation(player.getYRot(), player.getXRot());
      }

      return committed;
   }

   public static boolean isSwapped() {
      return swapped;
   }

   public static void applyForMovement(LocalPlayer player) {
      if (!swapped && canRotate(player)) {
         Rotation target = committed != null ? committed : (requested != null ? next(player, requested) : null);
         if (target != null && (target.yaw() != player.getYRot() || target.pitch() != player.getXRot())) {
            ownYaw = player.getYRot();
            ownPitch = player.getXRot();
            player.setYRot(target.yaw());
            player.setXRot(target.pitch());
            walked = target;
            swapped = true;
         }
      }
   }

   public static Input correctInput(Input keys) {
      if (walked == null) {
         return keys;
      }

      int forward = impulse(keys.comp_3159(), keys.comp_3160());
      int strafe = impulse(keys.comp_3161(), keys.comp_3162());
      if (forward == 0 && strafe == 0) {
         return keys;
      }

      double[] wanted = heading(ownYaw, strafe, forward);
      int bestForward = forward;
      int bestStrafe = strafe;
      double bestDot = Double.NEGATIVE_INFINITY;

      for (int f = -1; f <= 1; f++) {
         for (int s = -1; s <= 1; s++) {
            if (f != 0 || s != 0) {
               double[] way = heading(walked.yaw(), s, f);
               double dot = way[0] * wanted[0] + way[1] * wanted[1];
               if (dot > bestDot) {
                  bestDot = dot;
                  bestForward = f;
                  bestStrafe = s;
               }
            }
         }
      }

      return bestForward == forward && bestStrafe == strafe
         ? keys
         : new Input(bestForward > 0, bestForward < 0, bestStrafe > 0, bestStrafe < 0, keys.comp_3163(), keys.comp_3164(), keys.comp_3165());
   }

   public static boolean misses(LocalPlayer player, AABB box, double range) {
      if (server != null && pending() && canRotate(player)) {
         Vec3 eye = player.getEyePosition();
         if (box.contains(eye)) {
            return false;
         }

         Rotation look = committed != null ? committed : next(player, requested);
         return !hits(eye, box, range, look.yaw(), look.pitch())
            && !hits(eye, box, range, server.yaw(), look.pitch())
            && !hits(eye, box, range, server.yaw(), server.pitch());
      } else {
         return false;
      }
   }

   public static boolean missesBlock(LocalPlayer player, AABB box, double range) {
      if (server == null) {
         return false;
      }

      if (!silent) {
         if (!pending() || !canRotate(player)) {
            return false;
         }

         Rotation look = committed != null ? committed : next(player, requested);
         if (look.yaw() == player.getYRot() && look.pitch() == player.getXRot()) {
            return false;
         }
      }

      Vec3 eye = player.getEyePosition();
      return !box.contains(eye) && !hits(eye, box, range, server.yaw(), server.pitch());
   }

   public static void afterSend(LocalPlayer player) {
      if (swapped) {
         restore(player);
      }
   }

   public static void finishTick(LocalPlayer player) {
      if (swapped) {
         restore(player);
      }
   }

   private static int impulse(boolean positive, boolean negative) {
      return positive == negative ? 0 : (positive ? 1 : -1);
   }

   private static double[] heading(float yaw, int strafe, int forward) {
      double sin = Mth.sin(yaw * (float) (Math.PI / 180.0));
      double cos = Mth.cos(yaw * (float) (Math.PI / 180.0));
      double x = strafe * cos - forward * sin;
      double z = forward * cos + strafe * sin;
      double length = Math.sqrt(x * x + z * z);
      return new double[]{x / length, z / length};
   }

   private static boolean hits(Vec3 eye, AABB box, double range, float yaw, float pitch) {
      Vec3 direction = Vec3.directionFromRotation(pitch, yaw);
      return box.clip(eye, eye.add(direction.scale(range))).isPresent();
   }

   private static void restore(LocalPlayer player) {
      player.yBob = player.yBob + (ownYaw - walked.yaw()) * 0.5F;
      player.xBob = player.xBob + (ownPitch - walked.pitch()) * 0.5F;
      walked = null;
      player.setYRot(ownYaw);
      player.setXRot(ownPitch);
      swapped = false;
   }
}
