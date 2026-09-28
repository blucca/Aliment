package com.github.kusa233.outbreak.mixin;

import com.github.kusa233.outbreak.physiology.OutbreakInfection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the exact moment a food or drink is swallowed.
 *
 * Vanilla has no event for this and the item stack is gone by the time a server tick could notice,
 * so this is the one place a mixin is genuinely required. The handler only forwards to
 * {@link OutbreakInfection}, which keeps the injected code trivial.
 */
@Mixin(Item.class)
public abstract class ItemMixin {

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void outbreak$onFinishUsingItem(
            final ItemStack stack,
            final Level level,
            final LivingEntity user,
            final CallbackInfoReturnable<ItemStack> cir) {
        if (user instanceof ServerPlayer player) {
            OutbreakInfection.onItemConsumed(player, stack);
        }
    }
}
