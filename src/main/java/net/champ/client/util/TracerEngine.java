package net.champ.client.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public final class TracerEngine {
   private static final float WIDTH = 1.5F;
   private static boolean active;
   private static Vec3 cameraPos = Vec3.ZERO;
   private static Vec3 origin = Vec3.ZERO;
   private static FoglessLines lines;
   private static final Matrix4f viewProjection = new Matrix4f();
   private static final Matrix4f mobileInverse = new Matrix4f();
   private static final Vector4f mobileCentre = new Vector4f();
   private static final Vector4f mobileEye = new Vector4f();
   private static final Vector3f mobileForward = new Vector3f();

   private TracerEngine() {
   }

   public static void beginFrame(Matrix4fc viewRotation, Matrix4fc projection) {
      active = false;
      lines = null;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         if (mc.screen == null || mc.screen instanceof ChatScreen) {
            Camera camera = mc.gameRenderer.getMainCamera();
            if (camera != null && camera.isInitialized()) {
               viewProjection.set(projection).mul(viewRotation);
               boolean mobile = MobilePerformance.active();
               Matrix4f inverse = viewProjection.invert(mobile ? mobileInverse : new Matrix4f());
               Vector4f centre = inverse.transform(mobile ? mobileCentre.set(0.0F, 0.0F, 0.0F, 1.0F) : new Vector4f(0.0F, 0.0F, 0.0F, 1.0F));
               Vector4f eye = inverse.transform(mobile ? mobileEye.set(0.0F, 0.0F, -1.0F, 0.0F) : new Vector4f(0.0F, 0.0F, -1.0F, 0.0F));
               cameraPos = camera.position();
               origin = cameraPos.add(centre.x / centre.w, centre.y / centre.w, centre.z / centre.w);
               Vec3 eyePos = cameraPos.add(eye.x / eye.w, eye.y / eye.w, eye.z / eye.w);
               Vector3fc forward = (mobile ? mobileForward : new Vector3f())
                  .set(
                     (float)(origin.x - eyePos.x),
                     (float)(origin.y - eyePos.y),
                     (float)(origin.z - eyePos.z)
                  )
                  .normalize();
               double pixel = 2.0 / (projection.m11() * Math.max(1, mc.getWindow().getHeight()));
               lines = FoglessLines.forView(eyePos, forward, pixel);
               active = true;
            }
         }
      }
   }

   public static void draw(Vec3 target, int argb) {
      if (active) {
         lines.line(origin, target, argb, 1.5F);
      }
   }

   public static void draw(double x, double y, double z, int argb) {
      if (active) {
         if (MobilePerformance.active()) {
            lines.line(origin.x, origin.y, origin.z, x, y, z, argb, 1.5F);
         } else {
            lines.line(origin, new Vec3(x, y, z), argb, 1.5F);
         }
      }
   }

   public static void stem(Vec3 feet, double height, int argb) {
      if (active) {
         if (MobilePerformance.active()) {
            lines.line(feet.x, feet.y, feet.z, feet.x, feet.y + height, feet.z, argb, 1.5F);
         } else {
            lines.line(feet, feet.add(0.0, height, 0.0), argb, 1.5F);
         }
      }
   }

   public static Vector4f clip(Vec3 world) {
      return !active
         ? null
         : viewProjection.transform(
            new Vector4f(
               (float)(world.x - cameraPos.x),
               (float)(world.y - cameraPos.y),
               (float)(world.z - cameraPos.z),
               1.0F
            )
         );
   }

   public static Vector4f clip(Vec3 world, Vector4f output) {
      return !active
         ? null
         : viewProjection.transform(
            output.set(
               (float)(world.x - cameraPos.x),
               (float)(world.y - cameraPos.y),
               (float)(world.z - cameraPos.z),
               1.0F
            )
         );
   }

   public static boolean isInFront(Camera camera, Vec3 target) {
      if (MobilePerformance.active()) {
         Vec3 position = camera.position();
         Vector3fc fwd = camera.forwardVector();
         return (target.x - position.x) * fwd.x()
               + (target.y - position.y) * fwd.y()
               + (target.z - position.z) * fwd.z()
            > 0.05;
      } else {
         Vec3 delta = target.subtract(camera.position());
         Vector3fc fwd = camera.forwardVector();
         return delta.x * fwd.x() + delta.y * fwd.y() + delta.z * fwd.z() > 0.05;
      }
   }
}
