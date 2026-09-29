package com.github.kusa233.outbreak.mixin.client;

import com.github.kusa233.outbreak.client.OutbreakThirstHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the thirst bar.
 *
 * <p>It hangs off the end of vanilla's player-health pass, so it inherits the same left-hand
 * alignment as the health and armour rows and lands directly above them.
 */
@Mixin(Hud.class)
public abstract class HudMixin {

    @Inject(method = "extractPlayerHealth", at = @At("RETURN"))
    private void outbreak$thirstBar(final GuiGraphicsExtractor graphics, final CallbackInfo ci) {
        OutbreakThirstHud.render(graphics);
    }
}
