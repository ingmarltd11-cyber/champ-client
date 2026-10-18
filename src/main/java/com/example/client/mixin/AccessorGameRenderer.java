package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public interface AccessorGameRenderer {
   @Invoker("getFov")
   float champ$getFov(Camera var1, float var2, boolean var3);

   @Accessor("fogRenderer")
   FogRenderer champ$getFogRenderer();

   @Invoker("shouldRenderBlockOutline")
   boolean champ$shouldRenderBlockOutline();
}
