package com.example.client.mixin;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(LayerRenderState.class)
public interface AccessorHandItemLayer {
   @Accessor("transform")
   ItemTransform champ$handTransform();

   @Accessor("extents")
   Supplier<Vector3fc[]> champ$handExtents();
}
