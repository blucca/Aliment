package com.github.kusa233.aliment.mixin;

import com.github.kusa233.aliment.world.AlimentGrinding;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Widens the two grindstone input slots.
 *
 * <p>Vanilla's {@code mayPlace} only accepts damageable items or items with enchantments, which is
 * why aliment's items were being rejected by the grindstone. Both slots are anonymous classes
 * ({@code GrindstoneMenu$2} and {@code GrindstoneMenu$3}), so they are named by string rather than
 * by a compile-time reference.
 */
@Mixin(targets = {
        "net.minecraft.world.inventory.GrindstoneMenu$2",
        "net.minecraft.world.inventory.GrindstoneMenu$3"
})
public abstract class GrindstoneInputSlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void aliment$mayPlace(final ItemStack stack, final CallbackInfoReturnable<Boolean> cir) {
        if (AlimentGrinding.mayPlace(stack)) {
            cir.setReturnValue(true);
        }
    }

    /** Keeps the reference to the menu class so the target stays documented in code. */
    @SuppressWarnings("unused")
    private static final Class<?> ALIMENT_TARGET = GrindstoneMenu.class;
}
