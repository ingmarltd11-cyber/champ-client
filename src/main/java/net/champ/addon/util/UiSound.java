package net.champ.addon.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Zacht klikgeluidje voor de GUI (hoge pitch, laag volume). */
public final class UiSound {
    private UiSound() {}

    public static void hover() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.7F, 0.25F));
        } catch (Exception ignored) {}
    }
}
