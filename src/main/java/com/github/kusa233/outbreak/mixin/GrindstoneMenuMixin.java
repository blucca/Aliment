package com.github.kusa233.outbreak.mixin;

import com.github.kusa233.outbreak.world.OutbreakGrinding;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Teaches the grindstone what its new inputs produce.
 *
 * <p>Vanilla's {@code computeResult} only handles durability merging and enchantment stripping, so
 * without this the slot would accept an ore and then produce nothing. Injecting at the head and
 * cancelling keeps vanilla's own behaviour completely untouched for everything else.
 */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {

    @Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
    private void outbreak$computeResult(
            final ItemStack input,
            final ItemStack additional,
            final CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = OutbreakGrinding.resultFor(input, additional);
        if (!result.isEmpty()) {
            cir.setReturnValue(result);
        }
    }
}
