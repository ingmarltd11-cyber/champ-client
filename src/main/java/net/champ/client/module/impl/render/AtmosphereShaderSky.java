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
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class AtmosphereShaderSky {
   static final String OFF = "Off";
   static final String SPACE = "Space";
   static final String NORTHERN_LIGHTS = "Northern Lights";
   static final String RINGED_PLANET = "Ringed Planet";
   static final String BLACK_HOLE = "Black Hole";
   private static final List<String> THEMES = List.of("Space", "Northern Lights", "Ringed Planet", "Black Hole");
   private static final Logger LOGGER = LoggerFactory.getLogger("Champ Client");
   private static final RenderPipeline PIPELINE = skyPipeline(-1);
   private static final RenderPipeline[] MOBILE_PIPELINES = new RenderPipeline[]{skyPipeline(0), skyPipeline(1), skyPipeline(2), skyPipeline(3)};
   private static final RenderPipeline COMPOSITE = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.fromNamespaceAndPath("champ", "pipeline/sky_theme_composite"))
      .withVertexShader("core/screenquad")
      .withFragmentShader(Identifier.fromNamespaceAndPath("champ", "core/sky_theme_composite"))
      .withSampler("InSampler")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthWrite(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
      .build();
   private static final int UNIFORM_SIZE = 112;
   private static final float CUBE = 10.0F;
   private static final int CUBE_INDICES = 36;
   private static final float MAX_STEP = 0.1F;
   private static final float TIME_WRAP = 4096.0F;
   private GpuBuffer cube;
   private GpuBuffer uniforms;
   private ByteBuffer uniformData;
   private TextureTarget mobileTarget;
   private boolean mobileTargetFailed;
   private boolean invalidLogged;
   private float time;
   private long lastNanos;

   private static RenderPipeline skyPipeline(int mobileMode) {
      boolean offscreen = mobileMode >= 0;
      Builder builder = RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_PROJECTION_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("champ", offscreen ? "pipeline/sky_theme_mobile_" + mobileMode : "pipeline/sky_theme"))
         .withVertexShader(Identifier.fromNamespaceAndPath("champ", "core/sky_theme"))
         .withFragmentShader(Identifier.fromNamespaceAndPath("champ", "core/sky_theme"))
         .withUniform("ChampSky", UniformType.UNIFORM_BUFFER)
         .withDepthWrite(false)
         .withCull(false)
         .withVertexFormat(DefaultVertexFormat.POSITION, Mode.QUADS);
      if (offscreen) {
         builder.withShaderDefine("CHAMP_SKY_MODE", mobileMode);
      }

      return (offscreen ? builder.withoutBlend().withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST) : builder.withBlend(BlendFunction.TRANSLUCENT)).build();
   }

   boolean render(
      String theme,
      float visibility,
      float brightness,
      float speed,
      float stars,
      float effect,
      float band,
      int low,
      int high,
      int bandColor,
      int zenith,
      int horizon
   ) {
      int mode = THEMES.indexOf(theme);
      long now = System.nanoTime();
      float dt = this.lastNanos == 0L ? 0.0F : Math.min((float)(now - this.lastNanos) / 1.0E9F, 0.1F);
      this.lastNanos = now;
      this.time = (this.time + dt * speed) % 4096.0F;
      if (mode >= 0 && !(visibility <= 0.002F)) {
         GpuDevice device = RenderSystem.getDevice();
         boolean mobile = AtmosphereQuality.mobile();
         if (mobile && this.mobileTargetFailed) {
            return false;
         }

         RenderPipeline pipeline = mobile ? MOBILE_PIPELINES[mode] : PIPELINE;
         if (device.precompilePipeline(pipeline).isValid() && (!mobile || device.precompilePipeline(COMPOSITE).isValid())) {
            RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
            if (target.width > 0 && target.height > 0) {
               int width = mobile ? AtmosphereQuality.skyWidth(target.width, target.height) : target.width;
               int height = mobile ? AtmosphereQuality.skyHeight(target.width, target.height) : target.height;
               if (this.cube == null) {
                  this.cube = buildCube(device);
               }

               if (this.uniforms == null) {
                  this.uniforms = device.createBuffer(() -> "Champ Client sky theme settings", 136, 112L);
                  this.uniformData = BufferUtils.createByteBuffer(112);
               }

               this.putVec(0, mode, visibility, this.time, brightness);
               this.putVec(16, stars, effect, band, (float)width / target.width);
               this.putColor(32, low);
               this.putColor(48, high);
               this.putColor(64, bandColor);
               this.putColor(80, zenith);
               this.putColor(96, horizon);
               CommandEncoder encoder = device.createCommandEncoder();
               encoder.writeToBuffer(this.uniforms.slice(), this.uniformData);
               GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                  .writeTransform(RenderSystem.getModelViewStack(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
               AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(Mode.QUADS);
               if (!mobile) {
                  RenderPass pass = encoder.createRenderPass(
                     () -> "Champ Client sky theme", target.getColorTextureView(), OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty()
                  );

                  try {
                     this.drawSky(pass, pipeline, transforms, indices);
                  } catch (Throwable var32) {
                     if (pass != null) {
                        try {
                           pass.close();
                        } catch (Throwable var29) {
                           var32.addSuppressed(var29);
                        }
                     }

                     throw var32;
                  }

                  if (pass != null) {
                     pass.close();
                  }

                  return true;
               } else {
                  try {
                     if (this.mobileTarget == null) {
                        this.mobileTarget = new TextureTarget("Champ Client mobile sky", width, height, false);
                     } else if (this.mobileTarget.width != width || this.mobileTarget.height != height) {
                        this.mobileTarget.resize(width, height);
                     }

                     RenderPass pass = encoder.createRenderPass(() -> "Champ Client mobile sky", this.mobileTarget.getColorTextureView(), OptionalInt.of(0));

                     try {
                        this.drawSky(pass, pipeline, transforms, indices);
                     } catch (Throwable var34) {
                        if (pass != null) {
                           try {
                              pass.close();
                           } catch (Throwable var31) {
                              var34.addSuppressed(var31);
                           }
                        }

                        throw var34;
                     }

                     if (pass != null) {
                        pass.close();
                     }

                     pass = encoder.createRenderPass(() -> "Champ Client mobile sky composite", target.getColorTextureView(), OptionalInt.empty());

                     try {
                        pass.setPipeline(COMPOSITE);
                        pass.bindTexture("InSampler", this.mobileTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                        pass.draw(0, 3);
                     } catch (Throwable var33) {
                        if (pass != null) {
                           try {
                              pass.close();
                           } catch (Throwable var30) {
                              var33.addSuppressed(var30);
                           }
                        }

                        throw var33;
                     }

                     if (pass != null) {
                        pass.close();
                     }

                     return true;
                  } catch (RuntimeException failure) {
                     this.mobileTargetFailed = true;
                     this.releaseMobileTarget();
                     LOGGER.warn("[Champ Client] Mobile sky target unavailable; using the gradient sky", failure);
                     return false;
                  }
               }
            } else {
               return false;
            }
         } else {
            if (!this.invalidLogged) {
               this.invalidLogged = true;
               LOGGER.warn("[Champ Client] Atmosphere sky shader {} did not compile; using the gradient sky", pipeline.getFragmentShader());
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private void drawSky(RenderPass pass, RenderPipeline pipeline, GpuBufferSlice transforms, AutoStorageIndexBuffer indices) {
      pass.setPipeline(pipeline);
      RenderSystem.bindDefaultUniforms(pass);
      pass.setUniform("DynamicTransforms", transforms);
      pass.setUniform("ChampSky", this.uniforms.slice());
      pass.setVertexBuffer(0, this.cube);
      pass.setIndexBuffer(indices.method_68274(36), indices.method_31924());
      pass.drawIndexed(0, 0, 36, 1);
   }

   void close() {
      this.releaseMobileTarget();
      if (this.cube != null) {
         this.cube.close();
      }

      if (this.uniforms != null) {
         this.uniforms.close();
      }

      this.cube = null;
      this.uniforms = null;
      this.uniformData = null;
      this.lastNanos = 0L;
      this.mobileTargetFailed = false;
      this.invalidLogged = false;
   }

   private void releaseMobileTarget() {
      if (this.mobileTarget != null) {
         this.mobileTarget.destroyBuffers();
         this.mobileTarget = null;
      }
   }

   private void putVec(int offset, float x, float y, float z, float w) {
      this.uniformData.putFloat(offset, x);
      this.uniformData.putFloat(offset + 4, y);
      this.uniformData.putFloat(offset + 8, z);
      this.uniformData.putFloat(offset + 12, w);
   }

   private void putColor(int offset, int rgb) {
      this.putVec(offset, (rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, 1.0F);
   }

   private static GpuBuffer buildCube(GpuDevice device) {
      ByteBufferBuilder bytes = new ByteBufferBuilder(24 * DefaultVertexFormat.POSITION.getVertexSize());

      GpuBuffer var5;
      try {
         BufferBuilder builder = new BufferBuilder(bytes, Mode.QUADS, DefaultVertexFormat.POSITION);
         float s = 10.0F;
         face(builder, s, -s, -s, s, s, -s, s, s, s, s, -s, s);
         face(builder, -s, -s, s, -s, s, s, -s, s, -s, -s, -s, -s);
         face(builder, -s, s, -s, -s, s, s, s, s, s, s, s, -s);
         face(builder, -s, -s, s, -s, -s, -s, s, -s, -s, s, -s, s);
         face(builder, s, -s, s, s, s, s, -s, s, s, -s, -s, s);
         face(builder, -s, -s, -s, -s, s, -s, s, s, -s, s, -s, -s);
         MeshData mesh = builder.buildOrThrow();

         try {
            var5 = device.createBuffer(() -> "Champ Client sky theme cube", 32, mesh.vertexBuffer());
         } catch (Throwable var9) {
            if (mesh != null) {
               try {
                  mesh.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }
            }

            throw var9;
         }

         if (mesh != null) {
            mesh.close();
         }
      } catch (Throwable var10) {
         try {
            bytes.close();
         } catch (Throwable var7) {
            var10.addSuppressed(var7);
         }

         throw var10;
      }

      bytes.close();
      return var5;
   }

   private static void face(
      BufferBuilder builder, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz
   ) {
      builder.addVertex(ax, ay, az);
      builder.addVertex(bx, by, bz);
      builder.addVertex(cx, cy, cz);
      builder.addVertex(dx, dy, dz);
   }
}
