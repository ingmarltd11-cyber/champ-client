package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_368;
import net.minecraft.class_368.class_369;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(targets = "net/minecraft/class_374$class_375")
public interface AccessorHudToastInstance {
   @Accessor("field_2241")
   class_368 champ$hudToast();

   @Accessor("field_52787")
   int champ$hudFirstSlot();

   @Accessor("field_2244")
   class_369 champ$hudVisibility();

   @Accessor("field_52789")
   float champ$hudVisiblePortion();
}
