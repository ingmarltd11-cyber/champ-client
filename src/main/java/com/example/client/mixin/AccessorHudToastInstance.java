package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.Toast.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(targets = "net/minecraft/client/gui/components/toasts/ToastManager$ToastInstance")
public interface AccessorHudToastInstance {
   @Accessor("toast")
   Toast champ$hudToast();

   @Accessor("firstSlotIndex")
   int champ$hudFirstSlot();

   @Accessor("visibility")
   Visibility champ$hudVisibility();

   @Accessor("visiblePortion")
   float champ$hudVisiblePortion();
}
