package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ServerboundMovePlayerPacket.class)
public interface AccessorServerboundMovePlayerPacket {
   @Mutable
   @Accessor("y")
   void champ$setY(double var1);

   @Mutable
   @Accessor("onGround")
   void champ$setOnGround(boolean var1);
}
