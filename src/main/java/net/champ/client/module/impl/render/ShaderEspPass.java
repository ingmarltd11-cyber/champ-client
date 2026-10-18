package net.champ.client.module.impl.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.champ.client.util.MobilePerformance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OutlineBufferSource;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.TickRateManager;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class ShaderEspPass {
   private static final Logger LOGGER = LoggerFactory.getLogger("Champ Client");
   private static final RenderPipeline PIPELINE = pipeline(false);
   private static final RenderPipeline MOBILE_PIPELINE = pipeline(true);
   private static final Supplier<String> PASS_LABEL = () -> "Champ Client glow esp";
   private static final Supplier<String> CONFIG_LABEL = () -> "Champ Client glow esp config";
   private static final int CONFIG_SIZE = 32;
   private static final CameraRenderState CAMERA = new CameraRenderState();
   private static final PoseStack POSE = new PoseStack();
   private static final Map<Entity, EntityRenderState> KEPT = new IdentityHashMap<>();
   private static ShaderEspPass.Rig rig;
   private static PerspectiveProjectionMatrixBuffer projectionBuffer;
   private static TextureTarget mask;
   private static AuraBlur blur;
   private static GpuBuffer config;
   private static GpuBufferSlice configSlice;
   private static ByteBuffer configData;
   private static long keptFrame = Long.MIN_VALUE;
   private static boolean invalidShaderLogged;

   private static RenderPipeline pipeline(boolean mobile) {
      Builder builder = RenderPipeline.builder(new Snippet[0])
         .withLocation(Identifier.fromNamespaceAndPath("champ", mobile ? "pipeline/glow_esp_mobile" : "pipeline/glow_esp"))
         .withVertexShader("core/screenquad")
         .withFragmentShader(Identifier.fromNamespaceAndPath("champ", "core/glow_esp"))
         .withSampler("SilhouetteSampler")
         .withSampler("HaloSampler")
         .withUniform("GlowEspConfig", UniformType.UNIFORM_BUFFER)
         .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
         .withColorWrite(true, false)
         .withDepthWrite(false)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES);
      if (mobile) {
         builder.withShaderDefine("CHAMP_MOBILE_OUTLINE");
      }

      return builder.build();
   }

   private static RenderPipeline selectedPipeline(GpuDevice device) {
      return MobilePerformance.active() && device.precompilePipeline(MOBILE_PIPELINE).isValid() ? MOBILE_PIPELINE : PIPELINE;
   }

   private ShaderEspPass() {
   }

   static void keep(Entity entity, EntityRenderState state, long frame) {
      if (keptFrame != frame) {
         KEPT.clear();
         keptFrame = frame;
      }

      KEPT.put(entity, state);
   }

   static void dropKept() {
      KEPT.clear();
      keptFrame = Long.MIN_VALUE;
   }

   static boolean paint(Camera camera, Matrix4f viewRotation, Matrix4f projection, Entity[] entities, int[] tints, int count, long frame) {
      Minecraft mc = Minecraft.getInstance();
      RenderTarget main = mc.getMainRenderTarget();
      if (count > 0
         && mc.level != null
         && mc.isGameLoadFinished()
         && !mc.gameRenderer.isPanoramicMode()
         && main.getColorTexture() != null
         && main.width > 0
         && main.height > 0) {
         GpuDevice device = RenderSystem.getDevice();
         if (!shaderReady(device)) {
            return false;
         }

         if (mask == null) {
            allocate(main.width, main.height);
         } else if (mask.width != main.width || mask.height != main.height) {
            mask.resize(main.width, main.height);
         }

         if (rig == null) {
            rig = new ShaderEspPass.Rig(mc);
         }

         if (projectionBuffer == null) {
            projectionBuffer = new PerspectiveProjectionMatrixBuffer("champ glow esp");
         }

         device.createCommandEncoder().clearColorTexture(mask.getColorTexture(), 0);
         submit(mc, camera, entities, tints, count, frame);
         flush(viewRotation, projection);
         return true;
      } else {
         return false;
      }
   }

   private static void submit(Minecraft mc, Camera camera, Entity[] entities, int[] tints, int count, long frame) {
      EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
      DeltaTracker delta = mc.getDeltaTracker();
      TickRateManager ticks = mc.level.tickRateManager();
      Vec3 eye = camera.position();
      Entity viewer = camera.entity();
      boolean reuse = keptFrame == frame;
      CAMERA.initialized = camera.isInitialized();
      CAMERA.pos = eye;
      CAMERA.blockPos = camera.blockPosition();
      CAMERA.entityPos = viewer == null ? eye : viewer.getPosition(delta.getGameTimeDeltaPartialTick(true));
      CAMERA.orientation.set(camera.rotation());

      for (int i = 0; i < count; i++) {
         Entity entity = entities[i];
         if (entity != null && !entity.isRemoved()) {
            EntityRenderState state = reuse ? KEPT.get(entity) : null;
            if (state == null) {
               state = dispatcher.extractEntity(entity, delta.getGameTimeDeltaPartialTick(!ticks.isEntityFrozen(entity)));
            }

            silhouetteOnly(state);
            state.outlineColor = tints[i] | 0xFF000000;
            dispatcher.submit(
               state, CAMERA, state.x - eye.x, state.y - eye.y, state.z - eye.z, POSE, rig.storage
            );
         }
      }
   }

   private static void silhouetteOnly(EntityRenderState state) {
      state.nameTag = null;
      state.leashStates = null;
      state.displayFireAnimation = false;
      state.shadowPieces.clear();
      if (state instanceof AvatarRenderState avatar) {
         avatar.scoreText = null;
      }
   }

   private static void flush(Matrix4f viewRotation, Matrix4f projection) {
      GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
      GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
      Matrix4fStack modelView = RenderSystem.getModelViewStack();
      RenderSystem.backupProjectionMatrix();
      RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
      modelView.pushMatrix();
      modelView.mul(viewRotation);
      RenderSystem.outputColorTextureOverride = mask.getColorTextureView();
      RenderSystem.outputDepthTextureOverride = null;

      try {
         rig.features.renderAllFeatures();
         rig.outlines.endOutlineBatch();
      } finally {
         RenderSystem.outputColorTextureOverride = previousColor;
         RenderSystem.outputDepthTextureOverride = previousDepth;
         modelView.popMatrix();
         RenderSystem.restoreProjectionMatrix();
         rig.storage.clear();
      }

      rig.storage.endFrame();
   }

   static void compose(float strokePx, float aura, float reachPx, float fill, float phase, int sheenArgb, float sheen) {
      RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
      GpuTextureView halo = blur.spread(encoder, mask.getColorTextureView(), mask.width, mask.height, Math.max(reachPx, strokePx + 2.0F));
      configData.putFloat(0, strokePx);
      configData.putFloat(4, aura);
      configData.putFloat(8, fill);
      configData.putFloat(12, phase);
      configData.putFloat(16, (sheenArgb >> 16 & 0xFF) / 255.0F);
      configData.putFloat(20, (sheenArgb >> 8 & 0xFF) / 255.0F);
      configData.putFloat(24, (sheenArgb & 0xFF) / 255.0F);
      configData.putFloat(28, sheen);
      encoder.writeToBuffer(configSlice, configData);
      RenderPass pass = encoder.createRenderPass(PASS_LABEL, main.getColorTextureView(), OptionalInt.empty());

      try {
         pass.setPipeline(selectedPipeline(RenderSystem.getDevice()));
         pass.setUniform("GlowEspConfig", configSlice);
         pass.bindTexture("SilhouetteSampler", mask.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
         pass.bindTexture("HaloSampler", halo, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
         pass.draw(0, 3);
      } catch (Throwable var14) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var13) {
               var14.addSuppressed(var13);
            }
         }

         throw var14;
      }

      if (pass != null) {
         pass.close();
      }
   }

   private static boolean shaderReady(GpuDevice device) {
      if (device.precompilePipeline(selectedPipeline(device)).isValid() && AuraBlur.compiled(device, false)) {
         invalidShaderLogged = false;
         return true;
      }

      if (!invalidShaderLogged) {
         invalidShaderLogged = true;
         LOGGER.warn("Glow ESP shaders did not compile; entities are left without outlines");
      }

      return false;
   }

   private static void allocate(int width, int height) {
      mask = new TextureTarget("Champ Client glow esp mask", width, height, false);
      blur = new AuraBlur("Champ Client glow esp aura", false);
      config = RenderSystem.getDevice().createBuffer(CONFIG_LABEL, 136, 32L);
      configSlice = config.slice();
      configData = BufferUtils.createByteBuffer(32);
   }

   static void release() {
      dropKept();
      if (rig != null) {
         rig.close();
         rig = null;
      }

      if (mask != null) {
         mask.destroyBuffers();
         mask = null;
      }

      if (blur != null) {
         blur.release();
         blur = null;
      }

      if (config != null) {
         config.close();
         config = null;
      }

      if (projectionBuffer != null) {
         projectionBuffer.close();
         projectionBuffer = null;
      }

      configSlice = null;
      configData = null;
   }

   @Environment(EnvType.CLIENT)
   private static final class Drain extends BufferSource {
      private final Map<RenderType, VertexConsumer> sinks = new IdentityHashMap<>();

      Drain() {
         super(new ByteBufferBuilder(64), new LinkedHashMap());
      }

      void close() {
         this.sharedBuffer.close();
      }

      public VertexConsumer method_73477(RenderType renderType) {
         return this.sinks.computeIfAbsent(renderType, type -> new ShaderEspPass.Nowhere());
      }

      public void method_37104() {
      }

      public void method_22993() {
      }

      public void method_22994(RenderType renderType) {
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Nowhere implements VertexConsumer {
      public VertexConsumer method_22912(float x, float y, float z) {
         return this;
      }

      public void method_23919(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
      }

      public void method_22919(Pose pose, BakedQuad quad, float red, float green, float blue, float alpha, int light, int overlay) {
      }

      public void method_22920(Pose pose, BakedQuad quad, float[] brightness, float red, float green, float blue, float alpha, int[] lights, int overlay) {
      }

      public VertexConsumer method_1336(int red, int green, int blue, int alpha) {
         return this;
      }

      public VertexConsumer method_39415(int argb) {
         return this;
      }

      public VertexConsumer method_22913(float u, float v) {
         return this;
      }

      public VertexConsumer method_60796(int u, int v) {
         return this;
      }

      public VertexConsumer method_22921(int u, int v) {
         return this;
      }

      public VertexConsumer method_22914(float x, float y, float z) {
         return this;
      }

      public VertexConsumer method_75298(float width) {
         return this;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Rig {
      final SubmitNodeStorage storage = new SubmitNodeStorage();
      final ShaderEspPass.Drain drain = new ShaderEspPass.Drain();
      final OutlineBufferSource outlines;
      final FeatureRenderDispatcher features;

      Rig(Minecraft mc) {
         this.outlines = mc.renderBuffers().outlineBufferSource();
         this.features = new FeatureRenderDispatcher(this.storage, mc.getBlockRenderer(), this.drain, mc.getAtlasManager(), this.outlines, this.drain, mc.font);
      }

      void close() {
         this.features.close();
         this.drain.close();
      }
   }
}
