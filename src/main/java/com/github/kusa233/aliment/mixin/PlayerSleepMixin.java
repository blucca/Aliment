package com.github.kusa233.aliment.mixin;

import com.github.kusa233.aliment.physiology.AlimentSymptoms;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.attribute.BedRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refuses to let a heavily stimulated body fall asleep.
 *
 * <p>Ephedrine is a sympathomimetic: past a certain load the nervous system will not settle, so a
 * player whose ephedrine is above
 * {@link com.github.kusa233.aliment.physiology.AlimentData#EPHEDRINE_SLEEP_BLOCK_THRESHOLD} cannot
 * enter a bed.
 *
 * <p>Vanilla already has exactly the right seam for this. {@code startSleepInBed} answers with an
 * {@code Either} - a {@link Player.BedSleepingProblem} on the left, {@link Unit} on the right - and
 * {@code AbstractBedBlock} shows {@code problem.message()} to the player through
 * {@code sendOverlayMessage}. Returning a problem of our own therefore needs no HUD work: the
 * refusal and its explanation arrive the same way "the bed is occupied" does.
 *
 * <p>Injecting at the head of {@code Player.startSleepInBed} also covers {@code ServerPlayer}, whose
 * override performs its own checks and then delegates to this method with {@code invokespecial}.
 */
@Mixin(Player.class)
public abstract class PlayerSleepMixin {

    /** The line the player sees when a stimulant keeps them awake. */
    private static final String TOO_STIMULATED_KEY = "block.aliment.bed.too_stimulated";

    @Inject(method = "startSleepInBed", at = @At("HEAD"), cancellable = true)
    private void aliment$refuseSleepWhileStimulated(
            final AbstractBedBlock bed,
            final BlockState state,
            final BedRule rule,
            final BlockPos pos,
            final CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
        final Player self = (Player) (Object) this;
        if (AlimentSymptoms.isTooStimulatedToSleep(self)) {
            cir.setReturnValue(
                    Either.left(new Player.BedSleepingProblem(Component.translatable(TOO_STIMULATED_KEY))));
        }
    }
}
