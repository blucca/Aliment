package com.github.kusa233.outbreak.mixin;

import com.github.kusa233.outbreak.physiology.OutbreakSymptoms;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Scales every source of food exhaustion (walking, sprinting, mining, jumping) while the player
 * is ill, which is what "hunger drains a little faster" means in practice.
 *
 * <p>Scaling the argument here is much better than adding exhaustion from a tick handler: it also
 * covers activity the player does between ticks.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float outbreak$scaleExhaustion(final float amount) {
        return amount * OutbreakSymptoms.exhaustionMultiplier((Player) (Object) this);
    }
}
