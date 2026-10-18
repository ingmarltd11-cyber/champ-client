package com.example.client.mixin;

import net.champ.client.util.playerbypass2.SimulationSignals;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class MixinPlayerBypass2ClientPacketListener {
   private static final String ON_MAIN_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

   @Inject(
      method = "handleLogin",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2Login(ClientboundLoginPacket packet, CallbackInfo ci) {
      SimulationSignals.onLogin(packet.simulationDistance(), packet.chunkRadius(), packet.commonPlayerSpawnInfo().dimension());
   }

   @Inject(
      method = "handleRespawn",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2Respawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
      SimulationSignals.onRespawn(packet.commonPlayerSpawnInfo().dimension());
   }

   @Inject(
      method = "handleMovePlayer",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2Teleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      SimulationSignals.onTeleport();
   }

   @Inject(
      method = "handleSetSimulationDistance",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2Simulation(ClientboundSetSimulationDistancePacket packet, CallbackInfo ci) {
      SimulationSignals.onSimulationDistance(packet.simulationDistance());
   }

   @Inject(
      method = "handleSetChunkCacheRadius",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2View(ClientboundSetChunkCacheRadiusPacket packet, CallbackInfo ci) {
      SimulationSignals.onViewDistance(packet.getRadius());
   }

   @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
   private void champ$pb2Chunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
      SimulationSignals.onChunk(packet.getX(), packet.getZ());
   }

   @Inject(
      method = "handleMoveEntity",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2Move(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
      SimulationSignals.onMove(packet);
   }

   @Inject(
      method = "handleEntityPositionSync",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2PositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
      SimulationSignals.onPositionSync(packet);
   }

   @Inject(
      method = "handleRotateMob",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2Head(ClientboundRotateHeadPacket packet, CallbackInfo ci) {
      SimulationSignals.onHeadRotation(packet);
   }

   @Inject(
      method = "handleSetEntityData",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2EntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
      SimulationSignals.onEntityData(packet);
   }

   @Inject(
      method = "handleEntityEvent",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2EntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
      SimulationSignals.onEntityEvent(packet);
   }

   @Inject(
      method = "handleAddEntity",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2AddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
      SimulationSignals.onAddEntity(packet);
   }

   @Inject(
      method = "handleRemoveEntities",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2RemoveEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
      SimulationSignals.onRemoveEntities(packet.getEntityIds());
   }

   @Inject(
      method = "handleBlockUpdate",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2Block(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
      SimulationSignals.onBlockUpdate(packet);
   }

   @Inject(
      method = "handleChunkBlocksUpdate",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2Section(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
      SimulationSignals.onSectionUpdate(packet);
   }

   @Inject(
      method = "handleTakeItemEntity",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2TakeItem(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
      SimulationSignals.onTakeItem(packet);
   }

   @Inject(
      method = "handleDamageEvent",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2Damage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
      SimulationSignals.onDamage(packet);
   }

   @Inject(
      method = "handleSetEquipment",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void champ$pb2Equipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
      SimulationSignals.onEquipment(packet);
   }
}
