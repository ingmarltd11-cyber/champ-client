package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(targets = "net/minecraft/world/item/ItemCooldowns$CooldownInstance")
public interface AccessorHudCooldownInstance {
   @Accessor("comp_3083")
   int champ$hudStartTime();

   @Accessor("comp_3084")
   int champ$hudEndTime();
}
