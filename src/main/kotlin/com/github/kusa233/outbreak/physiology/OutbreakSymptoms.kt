package com.github.kusa233.outbreak.physiology

import com.github.kusa233.outbreak.registry.Registration
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player

/**
 * Turns [OutbreakData] into things the player can feel.
 *
 * All symptoms are deliberately mild; the point is that an untreated infection is a slow drain
 * rather than an instant death sentence.
 */
object OutbreakSymptoms {

    private val MINING_MODIFIER_ID = Registration.id("infection_mining")

    /** Up to 6% slower mining from the infection, plus 5% if electrolytes are low. */
    private const val MINING_PENALTY_PER_SEVERITY = 0.06
    private const val MINING_PENALTY_LOW_ELECTROLYTES = 0.05

    /** Every 15 seconds the game rolls for a brief camera shake. */
    const val SHAKE_INTERVAL_TICKS = 300
    const val SHAKE_CHANCE = 0.2f

    /** How long a shake lasts once it triggers, in ticks. */
    const val SHAKE_DURATION_TICKS = 16

    /** An immune storm both doubles the chance and shakes harder. */
    private const val STORM_SHAKE_CHANCE_MULTIPLIER = 2f

    /** Extra exhaustion per roll while the player is ill. */
    private const val EXHAUSTION_PER_ROLL = 0.03f

    /** How often the storm and electrolyte effects are refreshed. */
    private const val EFFECT_INTERVAL_TICKS = 40

    /**
     * Runs the whole system for every online player, once per server tick.
     *
     * The physiology only advances while the player is online, which keeps the model predictable.
     */
    fun initialize() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            for (player in server.playerList.players) {
                this.tick(player)
            }
        }
    }

    /**
     * Runs one tick of the whole system for [player].
     */
    fun tick(player: ServerPlayer) {
        // RUNTIME has no default initializer, so the supplier form is required here.
        val runtime = player.getAttachedOrCreate(OutbreakAttachments.RUNTIME) { OutbreakRuntime() }
        val before = player.getAttachedOrCreate(OutbreakAttachments.DATA)

        var data = OutbreakPhysiology.tick(before)
        data = OutbreakInfection.rollBatContact(player, data, runtime)
        player.setAttached(OutbreakAttachments.DATA, data)

        this.applyMiningPenalty(player, data)
        this.applyOngoingEffects(player, data, runtime)
        this.rollShake(player, data, runtime)
        this.applyHungerDrain(player, data, runtime)
    }

    // ------------------------------------------------------------------ symptoms

    private fun applyMiningPenalty(player: ServerPlayer, data: OutbreakData) {
        val instance = player.getAttribute(Attributes.BLOCK_BREAK_SPEED) ?: return
        if (!data.isSymptomatic) {
            if (instance.hasModifier(MINING_MODIFIER_ID)) {
                instance.removeModifier(MINING_MODIFIER_ID)
            }
            return
        }

        var penalty = MINING_PENALTY_PER_SEVERITY * data.severity
        if (data.electrolytes < OutbreakData.LOW_ELECTROLYTES) {
            penalty += MINING_PENALTY_LOW_ELECTROLYTES
        }
        instance.addOrUpdateTransientModifier(
            AttributeModifier(MINING_MODIFIER_ID, -penalty, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
        )
    }

    /**
     * The multiplier applied to every source of food exhaustion while the player is unwell, so
     * that hunger drains a little faster. Used by `PlayerMixin`.
     */
    @JvmStatic
    fun exhaustionMultiplier(player: Player): Float {
        val data = player.getAttachedOrThrow<OutbreakData>(OutbreakAttachments.DATA)
        if (!data.isSymptomatic && !data.isImmuneStorm) {
            return 1f
        }
        var multiplier = 1f + 0.5f * data.severity
        if (data.isImmuneStorm) {
            multiplier += 0.5f
        }
        if (data.electrolytes < OutbreakData.LOW_ELECTROLYTES) {
            multiplier += 0.25f
        }
        return multiplier
    }

    private fun applyHungerDrain(player: ServerPlayer, data: OutbreakData, runtime: OutbreakRuntime) {
        if (!data.isSymptomatic || runtime.shakeCooldown % EFFECT_INTERVAL_TICKS != 0) {
            return
        }
        player.causeFoodExhaustion(EXHAUSTION_PER_ROLL * data.severity)
    }

    private fun applyOngoingEffects(player: ServerPlayer, data: OutbreakData, runtime: OutbreakRuntime) {
        if (runtime.shakeCooldown % EFFECT_INTERVAL_TICKS != 0) {
            return
        }
        if (data.isImmuneStorm) {
            // The storm hurts the host: weakness and a slow extra drain.
            player.addEffect(MobEffectInstance(MobEffects.WEAKNESS, EFFECT_INTERVAL_TICKS * 3, 0))
            player.causeFoodExhaustion(0.05f)
        }
        if (data.electrolytes < OutbreakData.LOW_ELECTROLYTES && data.isSymptomatic) {
            player.addEffect(MobEffectInstance(MobEffects.HUNGER, EFFECT_INTERVAL_TICKS * 3, 0))
        }
    }

    private fun rollShake(player: ServerPlayer, data: OutbreakData, runtime: OutbreakRuntime) {
        if (runtime.shakeCooldown > 0) {
            runtime.shakeCooldown--
            return
        }
        runtime.shakeCooldown = SHAKE_INTERVAL_TICKS

        if (!data.isSymptomatic) {
            return
        }
        var chance = SHAKE_CHANCE
        if (data.isImmuneStorm) {
            chance *= STORM_SHAKE_CHANCE_MULTIPLIER
        }
        if (data.electrolytes < OutbreakData.LOW_ELECTROLYTES) {
            chance *= 1.5f
        }
        if (player.level().random.nextFloat() >= chance) {
            return
        }

        val amplitude = 0.6f + 0.8f * data.severity + (if (data.isImmuneStorm) 0.6f else 0f)
        val current = player.getAttachedOrElse(OutbreakAttachments.SHAKE, OutbreakShakeState.INACTIVE)
        player.setAttached(OutbreakAttachments.SHAKE, OutbreakShakeState(current.sequence + 1, amplitude))
    }
}
