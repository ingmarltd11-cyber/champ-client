package com.example.client.mixin;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ItemCooldowns.class)
public interface AccessorHudItemCooldowns {
   @Accessor("cooldowns")
   Map<Identifier, ?> champ$hudCooldowns();

   @Accessor("tickCount")
   int champ$hudTickCount();
}
