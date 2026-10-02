package com.github.kusa233.outbreak.mixin.client;

import com.github.kusa233.outbreak.client.OutbreakClientBlur;
import com.github.kusa233.outbreak.physiology.OutbreakData;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Closes the player's view in while a mandrake overdose has blurred their sight.
 *
 * <p>Nothing in the game can be told "this player can only see eight blocks" from the server side, so
 * the fog is clamped here, the same way vanilla's blindness effect does it: the environmental fog and
 * the sky/cloud fade are pulled in to the blur distance, while the render distance itself is left
 * alone so the chunks the player cannot see through the fog are still drawn.
 *
 * <p>A fever's screen effects are a separate post effect and stack with this one - this only ever
 * narrows the fog, never widens it, so the player can be feverish and half blind at the same time.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void outbreak$blurSight(
            final Camera camera,
            final int fogMode,
            final DeltaTracker deltaTracker,
            final float renderDistance,
            final ClientLevel level,
            final CallbackInfoReturnable<FogData> cir) {
        if (!OutbreakClientBlur.isBlurred()) {
            return;
        }

        final float distance = OutbreakData.ANTICHOLINERGIC_BLUR_DISTANCE;
        final FogData fog = cir.getReturnValue();
        fog.environmentalStart = 0.0F;
        fog.environmentalEnd = distance;
        fog.skyEnd = distance;
        fog.cloudEnd = distance;
    }
}
