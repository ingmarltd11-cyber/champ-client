package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.champ.client.util.TranslationGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;

@Environment(EnvType.CLIENT)
@Mixin(AnvilScreen.class)
public abstract class MixinTranslationGuardAnvilScreen {
   @WrapMethod(method = "slotChanged")
   private void champ$guardSlot(AbstractContainerMenu menu, int slot, ItemStack stack, Operation<Void> original) {
      TranslationGuard.run(() -> original.call(new Object[]{menu, slot, stack}));
   }

   @WrapMethod(method = "onNameChanged")
   private void champ$guardName(String name, Operation<Void> original) {
      TranslationGuard.run(() -> original.call(new Object[]{name}));
   }
}
