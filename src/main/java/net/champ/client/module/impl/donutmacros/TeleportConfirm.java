package net.champ.client.module.impl.donutmacros;

import java.util.Map;
import net.champ.client.chat.ModuleChat;
import net.champ.client.module.ClientModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_310;

@Environment(EnvType.CLIENT)
final class TeleportConfirm {
   private final ClientModule owner;
   private Object pressed;
   private boolean warned;

   TeleportConfirm(ClientModule owner) {
      this.owner = owner;
   }

   void reset() {
      this.pressed = null;
      this.warned = false;
   }

   boolean tick(class_310 mc) {
      Dialogs.View view = Dialogs.current(mc);
      if (view != null && view.titled("teleport")) {
         if (view.dialog() == this.pressed) {
            return true;
         }

         Dialogs.Button confirm = view.labelled("confirm", "yes", "accept");
         if (confirm == null) {
            return false;
         }

         if (!this.warned) {
            this.warned = true;
            ModuleChat.send(
               this.owner,
               ModuleChat.body()
                  .warn("Your teleport confirm menu is on")
                  .text(". Turn it off in /settings so requests go out faster. Confirming each one for now.")
            );
         }

         this.pressed = view.dialog();
         Dialogs.press(mc, view, confirm, Map.of());
         return true;
      } else {
         return false;
      }
   }
}
