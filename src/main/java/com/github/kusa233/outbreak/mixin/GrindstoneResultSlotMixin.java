package com.github.kusa233.outbreak.mixin;

import com.github.kusa233.outbreak.advancement.OutbreakAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Awards Outbreak's grinding achievements when output items are taken from the grindstone result slot.
 */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
public abstract class GrindstoneResultSlotMixin {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void outbreak$onTake(final Player player, final ItemStack stack, final CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            OutbreakAdvancements.onGrind(serverPlayer, stack);
        }
    }

    /** Keeps the reference to the menu class so the target stays documented in code. */
    @SuppressWarnings("unused")
    private static final Class<?> OUTBREAK_TARGET = GrindstoneMenu.class;
}
