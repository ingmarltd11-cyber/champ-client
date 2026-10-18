package net.champ.client.module.impl.render.character;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface CharacterHolder {
   WornCharacter champ$character();

   void champ$setCharacter(WornCharacter var1);

   float champ$seat();

   void champ$setSeat(float var1);
}
