package com.github.kusa233.aliment.mixin.client;

import com.github.kusa233.aliment.client.AlimentClientShake;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The camera shake.
 *
 * <p>Vanilla has no camera-shake hook and Fabric's viewport event no longer exists in this
 * version, so the shake is applied right after the camera has been aligned with the player. Only
 * the camera is moved: the player's actual rotation, aim and movement are untouched.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float yRot();

    @Shadow
    public abstract float xRot();

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void aliment$applyShake(final float partialTicks, final CallbackInfo ci) {
        float yaw = AlimentClientShake.yawOffset();
        float pitch = AlimentClientShake.pitchOffset();
        if (yaw != 0.0F || pitch != 0.0F) {
            this.setRotation(this.yRot() + yaw, this.xRot() + pitch);
        }
    }
}
