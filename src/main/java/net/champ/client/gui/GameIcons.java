package net.champ.client.gui;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;

@Environment(EnvType.CLIENT)
final class GameIcons {
   private static final Map<Identifier, ThemeImages.Texture> TEXTURES = new HashMap<>();
   private static final Set<Identifier> MISSING = new HashSet<>();

   private GameIcons() {
   }

   static ThemeImages.Texture effect(Holder<MobEffect> effect) {
      Identifier id = effect.unwrapKey().<Identifier>map(ResourceKey::identifier).orElse(null);
      return id == null ? null : texture(Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/mob_effect/" + id.getPath() + ".png"));
   }

   static ThemeImages.Texture block(String name) {
      return texture(Identifier.withDefaultNamespace("textures/block/" + name + ".png"));
   }

   private static ThemeImages.Texture texture(Identifier path) {
      ThemeImages.Texture texture = TEXTURES.get(path);
      if (texture == null && !MISSING.contains(path)) {
         try (InputStream in = Minecraft.getInstance().getResourceManager().open(path)) {
            texture = ThemeImages.decode(in.readAllBytes(), path.toString(), true);
         } catch (IOException | RuntimeException e) {
            texture = null;
         }

         if (texture == null) {
            MISSING.add(path);
         } else {
            TEXTURES.put(path, texture);
         }

         return texture;
      } else {
         return texture;
      }
   }
}
