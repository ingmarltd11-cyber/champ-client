package net.champ.client.module.impl.render;

import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import java.lang.ref.WeakReference;
import net.champ.client.config.FriendStore;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.ModuleRegistry;
import net.champ.client.module.ModuleRevision;
import net.champ.client.module.impl.misc.FreecamModule;
import net.champ.client.module.impl.render.character.CharacterModule;
import net.champ.client.util.MobilePerformance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
abstract class HeadCosmeticModule extends ClientModule {
   static volatile HeadCosmeticModule worn;
   static final String COLOR_THEME = "Theme";
   static final String COLOR_MANUAL = "Manual";
   private static final long EPOCH = System.nanoTime();
   private static final long LOOK_REFRESH_NANOS = 4000000L;
   private static final long FRIEND_REFRESH_NANOS = 1000000000L;
   private static final float GLINT_CYCLE_SECONDS = 3.6F;
   private static final float GLINT_STRENGTH = 0.6F;
   private PharaohMesh.Look look;
   private long lookAt;
   private boolean mobileLook;
   private long lookRevision;
   private int lookAccent;
   private final Int2BooleanOpenHashMap friendCache = new Int2BooleanOpenHashMap();
   private WeakReference<ClientLevel> friendLevel = new WeakReference<>(null);
   private long friendsCheckedAt;

   HeadCosmeticModule(String id, String name, String description) {
      super(id, name, description, ModuleCategory.VISUALS);
   }

   abstract PharaohMesh headMesh(boolean var1);

   abstract PharaohMesh bodyMesh(boolean var1);

   abstract float scale();

   abstract int[] palette();

   abstract boolean glows();

   abstract boolean glints();

   abstract boolean onFriends();

   @Override
   protected void onEnable() {
      CharacterModule character = ModuleRegistry.get(CharacterModule.class);
      if (character != null) {
         character.setEnabled(false);
      }

      this.reset();
      worn = this;
   }

   @Override
   protected void onDisable() {
      if (worn == this) {
         worn = null;
      }

      this.reset();
   }

   void reset() {
      this.look = null;
      this.friendCache.clear();
      this.friendLevel = new WeakReference<>(null);
   }

   final boolean wears(AvatarRenderState state) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer self = mc.player;
      if (self != null && mc.level != null) {
         return state.id == self.getId()
            ? !mc.options.getCameraType().isFirstPerson() || mc.screen != null || FreecamModule.active != null
            : this.onFriends() && this.isFriend(mc, state.id);
      } else {
         return false;
      }
   }

   private boolean isFriend(Minecraft mc, int id) {
      long now = System.nanoTime();
      if (this.friendLevel.get() != mc.level) {
         this.friendLevel = new WeakReference<>(mc.level);
         this.friendCache.clear();
      }

      if (now - this.friendsCheckedAt > 1000000000L) {
         this.friendCache.clear();
         this.friendsCheckedAt = now;
      }

      if (this.friendCache.containsKey(id)) {
         return this.friendCache.get(id);
      }

      boolean friend = mc.level.getEntity(id) instanceof Player player && FriendStore.getInstance().isFriend(player.getGameProfile().name());
      this.friendCache.put(id, friend);
      return friend;
   }

   final PharaohMesh.Look look() {
      long now = System.nanoTime();
      PharaohMesh.Look current = this.look;
      boolean mobile = MobilePerformance.active();
      long revision = mobile ? ModuleRevision.current() : 0L;
      int accent = mobile ? CosmeticAccent.argb() : 0;
      long refresh = mobile ? 33333333L : 4000000L;
      if (current == null
         || this.mobileLook != mobile
         || now - this.lookAt >= refresh
         || mobile && (this.lookRevision != revision || this.lookAccent != accent)) {
         float seconds = (float)(now - EPOCH) / 1.0E9F;
         float phase = seconds % 3.6F / 3.6F;
         float sweep = (phase * 2.0F - 1.0F) * (float) Math.PI * 1.25F;
         float envelope = Mth.sin(phase * (float) Math.PI);
         float gain = this.glints() ? 0.6F * envelope * envelope : 0.0F;
         float pulse = 0.9F + 0.1F * Mth.sin(seconds * 2.2F);
         current = new PharaohMesh.Look(this.palette(), this.glows(), Mth.cos(sweep), Mth.sin(sweep), gain, pulse);
         this.look = current;
         this.lookAt = now;
         this.mobileLook = mobile;
         this.lookRevision = revision;
         this.lookAccent = accent;
         return current;
      } else {
         return current;
      }
   }

   static int metal(int accent) {
      return shade(accent, 0.55F, 0.96F);
   }

   static int gem(int accent) {
      return shade(accent, 1.15F, 1.0F);
   }

   static int shade(int argb, float saturation, float value) {
      int r = argb >> 16 & 0xFF;
      int g = argb >> 8 & 0xFF;
      int b = argb & 0xFF;
      int max = Math.max(r, Math.max(g, b));
      int chroma = max - Math.min(r, Math.min(g, b));
      float hue = 0.0F;
      if (chroma > 0) {
         if (max == r) {
            hue = (float)(g - b) / chroma;
         } else if (max == g) {
            hue = 2.0F + (float)(b - r) / chroma;
         } else {
            hue = 4.0F + (float)(r - g) / chroma;
         }

         hue /= 6.0F;
         if (hue < 0.0F) {
            hue++;
         }
      }

      float s = max == 0 ? 0.0F : (float)chroma / max;
      return Mth.hsvToArgb(hue, Math.min(1.0F, s * saturation), Math.min(1.0F, value), 255);
   }
}
