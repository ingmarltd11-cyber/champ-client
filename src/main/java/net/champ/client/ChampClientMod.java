package net.champ.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ChampClientMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Champ Client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Champ Client] Fase 0 skelet geladen. V5 addon-port volgt.");
    }
}
