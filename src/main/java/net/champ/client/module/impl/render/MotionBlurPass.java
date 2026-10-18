package net.champ.client.module.impl.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.TextureTarget;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class MotionBlurPass {
   private static final Logger LOGGER = LoggerFactory.getLogger("Champ Client");
   private static final RenderPipeline PIPELINE = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.fromNamespaceAndPath("champ", "pipeline/motion_blur"))
      .withVertexShader("core/screenquad")
      .withFragmentShader(Identifier.fromNamespaceAndPath("champ", "core/motion_blur"))
      .withSampler("InSampler")
      .withUniform("MotionBlurConfig", UniformType.UNIFORM_BUFFER)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withColorWrite(true, false)
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
      .build();
   private static final Supplier<String> PASS_LABEL = () -> "Champ Client motion blur";
   private static final Supplier<String> CONFIG_LABEL = () -> "Champ Client motion blur config";
   private static final int CONFIG_SIZE = 16;
   private static final long STALE_NANOS = 1000000000L;
   private static final float MIN_RETENTION = 0.003921569F;
   private static TextureTarget history;
   private static GpuBuffer config;
   private static GpuBufferSlice configSlice;
   private static ByteBuffer configData;
   private static boolean primed;
   private static long lastFrameNanos;
   private static int levelKey;
   private static int seed;
   private static boolean invalidShaderLogged;

   private MotionBlurPass() {
   }

   static void apply(boolean worldRendered, float retention60) {
      Minecraft mc = Minecraft.getInstance();
      RenderTarget main = mc.getMainRenderTarget();
      GpuTexture mainColor = main.getColorTexture();
      if (worldRendered
         && mc.level != null
         && mc.isGameLoadFinished()
         && !mc.gameRenderer.isPanoramicMode()
         && mainColor != null
         && main.width > 0
         && main.height > 0) {
         long now = System.nanoTime();
         long gap = now - lastFrameNanos;
         lastFrameNanos = now;
         int level = System.identityHashCode(mc.level);
         if (level != levelKey) {
            levelKey = level;
            primed = false;
         }

         if (history == null) {
            allocate(main.width, main.height);
            primed = false;
         } else if (history.width != main.width || history.height != main.height) {
            history.resize(main.width, main.height);
            primed = false;
         }

         GpuDevice device = RenderSystem.getDevice();
         CommandEncoder encoder = device.createCommandEncoder();
         if (primed && gap > 0L && gap < 1000000000L) {
            float retention = (float)Math.pow(retention60, gap * 6.0E-8);
            if (retention >= 0.003921569F && shaderReady(device)) {
               blend(encoder, main, retention);
            }
         }

         encoder.copyTextureToTexture(mainColor, history.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
         primed = true;
      } else {
         primed = false;
      }
   }

   private static void blend(CommandEncoder encoder, RenderTarget main, float retention) {
      seed = seed + 1 & 63;
      configData.putFloat(0, retention);
      configData.putFloat(4, seed * 7.0F);
      encoder.writeToBuffer(configSlice, configData);
      RenderPass pass = encoder.createRenderPass(PASS_LABEL, main.getColorTextureView(), OptionalInt.empty());

      try {
         pass.setPipeline(PIPELINE);
         pass.setUniform("MotionBlurConfig", configSlice);
         pass.bindTexture("InSampler", history.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
         pass.draw(0, 3);
      } catch (Throwable var7) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (pass != null) {
         pass.close();
      }
   }

   private static boolean shaderReady(GpuDevice device) {
      if (device.precompilePipeline(PIPELINE).isValid()) {
         invalidShaderLogged = false;
         return true;
      }

      if (!invalidShaderLogged) {
         invalidShaderLogged = true;
         LOGGER.warn("Motion Blur shader {} did not compile; frames are passed through unblurred", PIPELINE.getFragmentShader());
      }

      return false;
   }

   private static void allocate(int width, int height) {
      history = new TextureTarget("Champ Client motion blur history", width, height, false);
      config = RenderSystem.getDevice().createBuffer(CONFIG_LABEL, 136, 16L);
      configSlice = config.slice();
      configData = BufferUtils.createByteBuffer(16);
   }

   static void release() {
      if (history != null) {
         history.destroyBuffers();
         history = null;
      }

      if (config != null) {
         config.close();
         config = null;
      }

      configSlice = null;
      configData = null;
      primed = false;
      lastFrameNanos = 0L;
      levelKey = 0;
   }
}
