package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ServerboundMoveVehiclePacket.class)
public interface AccessorBoatFlyServerboundMoveVehiclePacket {
   @Mutable
   @Accessor("comp_3350")
   void champ$setPosition(Vec3 var1);
}
