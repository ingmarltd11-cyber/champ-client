package net.champ.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ClientBrand {
   public static final String NAME = "Champ Client";
   public static final String WINDOW_TITLE = "Champ Client Client";
   public static final String LOGGER_NAME = "Champ Client";

   private ClientBrand() {
   }
}
