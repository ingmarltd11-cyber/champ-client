package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public interface AccessorCombatMinecraft {
   @Accessor("rightClickDelay")
   int champ$rightClickDelay();

   @Accessor("rightClickDelay")
   void champ$setRightClickDelay(int var1);
}
