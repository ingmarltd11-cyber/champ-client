package com.example.client.mixin;

import net.champ.client.module.impl.render.character.CharacterHolder;
import net.champ.client.module.impl.render.character.WornCharacter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin(AvatarRenderState.class)
public abstract class MixinCharacterAvatarRenderState implements CharacterHolder {
   @Unique
   private WornCharacter champ$character;
   @Unique
   private float champ$seat;

   @Override
   public WornCharacter champ$character() {
      return this.champ$character;
   }

   @Override
   public void champ$setCharacter(WornCharacter character) {
      this.champ$character = character;
   }

   @Override
   public float champ$seat() {
      return this.champ$seat;
   }

   @Override
   public void champ$setSeat(float knee) {
      this.champ$seat = knee;
   }
}
