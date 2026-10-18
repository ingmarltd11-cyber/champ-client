package net.champ.client.util;

import com.example.client.mixin.AccessorGameRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.client.Camera;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public final class FoglessLines {
   private static final double NEAR = 0.05;
   private final Vec3 eye;
   private final Vector3fc forward;
   private final double pixelPerDepth;
   private GizmoStyle lastFill;
   private int lastArgb;

   private FoglessLines(Vec3 eye, Vector3fc forward, double pixelPerDepth) {
      this.eye = eye;
      this.forward = forward;
      this.pixelPerDepth = pixelPerDepth;
   }

   public static FoglessLines forFrame(float partialTick) {
      Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
      return new FoglessLines(camera.position(), camera.forwardVector(), pixelScale(partialTick));
   }

   public static double pixelScale(float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      Camera camera = mc.gameRenderer.getMainCamera();
      float fov = ((AccessorGameRenderer)mc.gameRenderer).champ$getFov(camera, partialTick, true);
      return 2.0 * Math.tan(Math.toRadians(fov) * 0.5) / Math.max(1, mc.getWindow().getHeight());
   }

   public static FoglessLines forView(Vec3 eye, Vector3fc forward, double pixelPerDepth) {
      return new FoglessLines(eye, forward, pixelPerDepth);
   }

   public void line(Vec3 from, Vec3 to, int argb, float width) {
      if (MobilePerformance.active()) {
         this.scalarLine(from.x, from.y, from.z, to.x, to.y, to.z, argb, width);
      } else {
         double depthFrom = this.depth(from);
         double depthTo = this.depth(to);
         if (!(depthFrom < 0.05) || !(depthTo < 0.05)) {
            if (depthFrom < 0.05) {
               from = from.lerp(to, (0.05 - depthFrom) / (depthTo - depthFrom));
               depthFrom = 0.05;
            } else if (depthTo < 0.05) {
               to = to.lerp(from, (0.05 - depthTo) / (depthFrom - depthTo));
               depthTo = 0.05;
            }

            Vec3 side = from.subtract(this.eye).cross(to.subtract(from));
            double length = side.length();
            if (!(length < 1.0E-9)) {
               double scale = width * 0.5 * this.pixelPerDepth / length;
               Vec3 atFrom = side.scale(scale * depthFrom);
               Vec3 atTo = side.scale(scale * depthTo);
               Gizmos.rect(
                     from.add(atFrom), to.add(atTo), to.subtract(atTo), from.subtract(atFrom), GizmoStyle.fill(argb)
                  )
                  .setAlwaysOnTop();
            }
         }
      }
   }

   public void line(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, int argb, float width) {
      if (MobilePerformance.active()) {
         this.scalarLine(fromX, fromY, fromZ, toX, toY, toZ, argb, width);
      } else {
         this.line(new Vec3(fromX, fromY, fromZ), new Vec3(toX, toY, toZ), argb, width);
      }
   }

   private void scalarLine(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, int argb, float width) {
      double depthFrom = this.depth(fromX, fromY, fromZ);
      double depthTo = this.depth(toX, toY, toZ);
      if (!(depthFrom < 0.05) || !(depthTo < 0.05)) {
         if (depthFrom < 0.05) {
            double amount = (0.05 - depthFrom) / (depthTo - depthFrom);
            fromX = Mth.lerp(amount, fromX, toX);
            fromY = Mth.lerp(amount, fromY, toY);
            fromZ = Mth.lerp(amount, fromZ, toZ);
            depthFrom = 0.05;
         } else if (depthTo < 0.05) {
            double amount = (0.05 - depthTo) / (depthFrom - depthTo);
            toX = Mth.lerp(amount, toX, fromX);
            toY = Mth.lerp(amount, toY, fromY);
            toZ = Mth.lerp(amount, toZ, fromZ);
            depthTo = 0.05;
         }

         double ax = fromX - this.eye.x;
         double ay = fromY - this.eye.y;
         double az = fromZ - this.eye.z;
         double bx = toX - fromX;
         double by = toY - fromY;
         double bz = toZ - fromZ;
         double sx = ay * bz - az * by;
         double sy = az * bx - ax * bz;
         double sz = ax * by - ay * bx;
         double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
         if (!(length < 1.0E-9)) {
            double scale = width * 0.5 * this.pixelPerDepth / length;
            double startScale = scale * depthFrom;
            double endScale = scale * depthTo;
            double fx = sx * startScale;
            double fy = sy * startScale;
            double fz = sz * startScale;
            double tx = sx * endScale;
            double ty = sy * endScale;
            double tz = sz * endScale;
            if (this.lastFill == null || this.lastArgb != argb) {
               this.lastFill = GizmoStyle.fill(argb);
               this.lastArgb = argb;
            }

            Gizmos.rect(
                  new Vec3(fromX + fx, fromY + fy, fromZ + fz),
                  new Vec3(toX + tx, toY + ty, toZ + tz),
                  new Vec3(toX - tx, toY - ty, toZ - tz),
                  new Vec3(fromX - fx, fromY - fy, fromZ - fz),
                  this.lastFill
               )
               .setAlwaysOnTop();
         }
      }
   }

   public void box(AABB box, int argb, float width) {
      double x0 = box.minX;
      double y0 = box.minY;
      double z0 = box.minZ;
      double x1 = box.maxX;
      double y1 = box.maxY;
      double z1 = box.maxZ;
      if (MobilePerformance.active()) {
         for (int level = 0; level < 2; level++) {
            double y = level == 0 ? y0 : y1;
            this.scalarLine(x0, y, z0, x1, y, z0, argb, width);
            this.scalarLine(x1, y, z0, x1, y, z1, argb, width);
            this.scalarLine(x1, y, z1, x0, y, z1, argb, width);
            this.scalarLine(x0, y, z1, x0, y, z0, argb, width);
         }

         this.scalarLine(x0, y0, z0, x0, y1, z0, argb, width);
         this.scalarLine(x1, y0, z0, x1, y1, z0, argb, width);
         this.scalarLine(x1, y0, z1, x1, y1, z1, argb, width);
         this.scalarLine(x0, y0, z1, x0, y1, z1, argb, width);
      } else {
         for (double y : new double[]{y0, y1}) {
            this.line(new Vec3(x0, y, z0), new Vec3(x1, y, z0), argb, width);
            this.line(new Vec3(x1, y, z0), new Vec3(x1, y, z1), argb, width);
            this.line(new Vec3(x1, y, z1), new Vec3(x0, y, z1), argb, width);
            this.line(new Vec3(x0, y, z1), new Vec3(x0, y, z0), argb, width);
         }

         this.line(new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), argb, width);
         this.line(new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), argb, width);
         this.line(new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), argb, width);
         this.line(new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), argb, width);
      }
   }

   private double depth(Vec3 point) {
      return (point.x - this.eye.x) * this.forward.x()
         + (point.y - this.eye.y) * this.forward.y()
         + (point.z - this.eye.z) * this.forward.z();
   }

   private double depth(double x, double y, double z) {
      return (x - this.eye.x) * this.forward.x() + (y - this.eye.y) * this.forward.y() + (z - this.eye.z) * this.forward.z();
   }
}
