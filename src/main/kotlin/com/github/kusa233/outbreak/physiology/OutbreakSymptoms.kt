package com.github.kusa233.outbreak.physiology

import com.github.kusa233.outbreak.registry.Registration
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.Holder
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import kotlin.math.abs

/** One symptom set: what a mineral imbalance does to a player for the next `ticks` ticks. */
private typealias Symptom = (ServerPlayer, Int) -> Unit

/**
 * Turns [OutbreakData] into things the player can feel.
 *
 * All symptoms are deliberately mild; the point is that an untreated infection is a slow drain
 * rather than an instant death sentence.
 *
 * Everything mineral goes through one table ([MINERALS]) with four thresholds per entry, so an
 * excess and a deficit of the same substance are always described side by side and neither can be
 * added without the other being considered. Body temperature gets its own block at the end.
 */
object OutbreakSymptoms {

    private val MINING_MODIFIER_ID = Registration.id("infection_mining")

    /** Up to 6% slower mining from the infection. */
    private const val MINING_PENALTY_PER_SEVERITY = 0.06

    /** Over-hydration makes the player weak and slows mining further. */
    private const val MINING_PENALTY_OVERHYDRATED = 0.08

    /**
     * The screen effects that go with a fever and with hypothermia. The assets live in
     * `assets/outbreak/post_effect/<name>.json`, and the client only ever applies what the server
     * asks it to through `ServerPlayer.addPostEffect`, so there is nothing to do client-side.
     *
     * The fever has two of them, and they stack: [HEAT_HAZE] from [OutbreakData.FEVER_MILD], and
     * the motion blur on top of it from [OutbreakData.FEVER_SEVERE].
     */
    val HEAT_HAZE: Identifier = Registration.id("heat_haze")
    val HEAT_BLUR: Identifier = Registration.id("heat_blur")
    val COLD_SHIVER: Identifier = Registration.id("cold_shiver")

    /** Every 15 seconds the game rolls for a brief camera shake. */
    const val SHAKE_INTERVAL_TICKS = 300
    const val SHAKE_CHANCE = 0.2f

    /** How long a shake lasts once it triggers, in ticks. */
    const val SHAKE_DURATION_TICKS = 16

    /** An immune storm both doubles the chance and shakes harder. */
    private const val STORM_SHAKE_CHANCE_MULTIPLIER = 2f

    /** No state, however bad, shakes the camera more often than this. */
    private const val MAX_SHAKE_CHANCE = 0.85f

    /** Extra exhaustion per roll while the player is ill. */
    private const val EXHAUSTION_PER_ROLL = 0.03f

    /** How often the storm, mineral and thermal effects are refreshed. */
    private const val EFFECT_INTERVAL_TICKS = 40

    // ------------------------------------------------------------------ environment

    /** Vanilla's idea of a temperate biome; the neutral point of the ambient scale. */
    private const val TEMPERATE_BIOME = 0.8f

    /** Degrees of pull per unit of vanilla biome temperature, before thermoregulation. */
    private const val BIOME_PULL = 2.0f

    /**
     * The thermoneutral zone: this much pull costs the body nothing at all, which is what keeps an
     * ordinary walk through a taiga from being a medical event.
     */
    private const val THERMONEUTRAL_BAND = 2.0f

    /** Soaked to the skin, or out in the rain and snow. */
    private const val WET_PULL = 2.5f

    /** Buried in powder snow. */
    private const val POWDER_SNOW_PULL = 4.0f

    /** On fire, or swimming in lava. */
    private const val FIRE_PULL = 4.0f
    private const val LAVA_PULL = 6.0f

    // ------------------------------------------------------------------ thermal symptoms

    /** Extra food exhaustion while shivering or running a fever. */
    private const val THERMAL_EXHAUSTION = 0.25f

    /** Extra chance of a shiver, per tier of thermal stress. */
    private const val THERMAL_SHAKE_CHANCE = 0.15f

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

        var data = OutbreakPhysiology.tick(before, ambientTemperature(player))
        data = OutbreakInfection.rollBatContact(player, data, runtime)
        player.setAttached(OutbreakAttachments.DATA, data)

        this.applyMiningPenalty(player, data)
        this.applyOngoingEffects(player, data, runtime)
        this.rollShake(player, data, runtime)
        this.applyPostEffects(player, data)
        this.syncClient(player, data, runtime)
    }

    // ------------------------------------------------------------------ environment

    /**
     * The temperature the surroundings are dragging the player towards, on the body's own Celsius
     * scale, before thermoregulation.
     *
     * The world is almost never hot enough to overwhelm a healthy body - a desert is 2.4 degrees
     * above temperate, well inside [THERMONEUTRAL_BAND] - so the heat side of the model is mostly
     * driven by fever. The cold side is where the environment bites: wet, powder snow, fire and
     * lava are the things that get past the body's defences.
     */
    fun ambientTemperature(player: ServerPlayer): Float = environmentTemperature(
        biomeTemperature = player.level().getBiome(player.blockPosition()).value().baseTemperature,
        wet = player.isInWaterOrRain,
        powderSnow = player.isInPowderSnow,
        lava = player.isInLava,
        fire = player.isOnFire,
    )

    /**
     * The pure half of [ambientTemperature], so that the environment can be reasoned about - and
     * tested - without a world.
     */
    fun environmentTemperature(
        biomeTemperature: Float,
        wet: Boolean = false,
        powderSnow: Boolean = false,
        lava: Boolean = false,
        fire: Boolean = false,
    ): Float {
        var pull = (biomeTemperature - TEMPERATE_BIOME) * BIOME_PULL
        if (wet) {
            pull -= WET_PULL
        }
        if (powderSnow) {
            pull -= POWDER_SNOW_PULL
        }
        if (lava) {
            pull += LAVA_PULL
        } else if (fire) {
            pull += FIRE_PULL
        }

        // Shrug off the thermoneutral zone, and only then let the body's defences matter.
        val effective = when {
            pull > THERMONEUTRAL_BAND -> pull - THERMONEUTRAL_BAND
            pull < -THERMONEUTRAL_BAND -> pull + THERMONEUTRAL_BAND
            else -> 0f
        }

        return (OutbreakData.TEMPERATURE_NORMAL + effective)
            .coerceIn(OutbreakData.TEMPERATURE_MIN, OutbreakData.TEMPERATURE_MAX)
    }

    // ------------------------------------------------------------------ symptoms

    private fun applyMiningPenalty(player: ServerPlayer, data: OutbreakData) {
        val instance = player.getAttribute(Attributes.BLOCK_BREAK_SPEED) ?: return

        var penalty = 0.0
        if (data.isSymptomatic) {
            penalty += MINING_PENALTY_PER_SEVERITY * data.severity
        }
        if (data.isOverhydrated) {
            penalty += MINING_PENALTY_OVERHYDRATED
        }
        // Hypokalaemia and hypocalcaemia both cause muscular weakness.
        if (data.electrolytes.potassium < Electrolytes.SAFE_LOW) {
            penalty += 0.06
        }
        if (data.electrolytes.calcium < Electrolytes.SAFE_LOW) {
            penalty += 0.04
        }

        if (penalty <= 0.0) {
            if (instance.hasModifier(MINING_MODIFIER_ID)) {
                instance.removeModifier(MINING_MODIFIER_ID)
            }
            return
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
        val data = player.getAttachedOrElse(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        var multiplier = 1f
        if (data.isSymptomatic) {
            multiplier += 0.5f * data.severity
        }
        if (data.isImmuneStorm) {
            multiplier += 0.5f
        }
        if (data.isOverhydrated) {
            multiplier += 0.2f
        }
        // Hyperchloraemia causes a mild metabolic acidosis with nausea and poor appetite.
        if (data.electrolytes.chloride > Electrolytes.SAFE_HIGH) {
            multiplier += 0.2f
        }
        if (data.electrolytes.magnesium < Electrolytes.SAFE_LOW) {
            multiplier += 0.15f
        }
        // Shivering and a raised metabolic rate both cost calories.
        if (data.hasThermalStress) {
            multiplier += THERMAL_EXHAUSTION
        }
        return multiplier
    }

    /**
     * Everything that has to be refreshed rather than set once: the storm, the water, the six
     * minerals, and the body temperature.
     *
     * It runs on the [EFFECT_INTERVAL_TICKS] cadence and re-applies each effect for three intervals,
     * so an effect that stops being warranted simply lapses instead of having to be removed. Only
     * vanilla effects are used, which is why none of this needs anything rendered for it.
     */
    private fun applyOngoingEffects(player: ServerPlayer, data: OutbreakData, runtime: OutbreakRuntime) {
        if (runtime.shakeCooldown % EFFECT_INTERVAL_TICKS != 0) {
            return
        }
        val ticks = EFFECT_INTERVAL_TICKS * 3

        // --- immune storm: the response itself hurts the host
        if (data.isImmuneStorm) {
            player.addEffect(MobEffectInstance(MobEffects.WEAKNESS, ticks, 0))
            player.causeFoodExhaustion(0.05f)
        }

        // --- water
        if (data.isOverhydrated) {
            // Too much water: weakness, and at the extreme, confusion.
            player.addEffect(MobEffectInstance(MobEffects.WEAKNESS, ticks, 0))
            if (data.water > OutbreakData.WATER_VISIBLY_OVERHYDRATED) {
                player.addEffect(MobEffectInstance(MobEffects.NAUSEA, ticks, 0))
            }
        } else if (data.isDehydrated && data.water < 15f) {
            player.addEffect(MobEffectInstance(MobEffects.HUNGER, ticks, 0))
        }

        // --- the six minerals, each judged on both sides of its safe band
        for (rule in MINERALS) {
            rule.symptomFor(rule.of(data))?.invoke(player, ticks)
        }

        // --- body temperature
        applyThermalEffects(player, data, ticks)
    }

    /**
     * Fever and hypothermia both produce weakness and mining fatigue, with the strength stepping
     * up at tier 2. Hypothermia adds slowness on top; a cold body is a slow body, and the shiver
     * itself is handled by [rollShake].
     *
     * The visual half - the shimmer at the edges of the screen - is a post effect, applied by
     * [applyPostEffects] rather than here.
     */
    private fun applyThermalEffects(player: ServerPlayer, data: OutbreakData, ticks: Int) {
        val tier = data.thermalTier
        if (tier == 0) {
            return
        }
        val amplifier = abs(tier) - 1

        player.addEffect(MobEffectInstance(MobEffects.WEAKNESS, ticks, amplifier))
        player.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, ticks, amplifier))
        if (tier < 0) {
            player.addEffect(MobEffectInstance(MobEffects.SLOWNESS, ticks, amplifier))
        }

        // A fever is a furnace and a shiver is a workout; both burn food.
        player.causeFoodExhaustion(EXHAUSTION_PER_ROLL * abs(tier))
    }

    /**
     * Applies and clears the screen effects.
     *
     * A post effect has to be requested by the server for the client to load it, and the request is
     * a plain id - the client resolves `assets/outbreak/post_effect/<id>.json` itself and logs (and
     * skips) anything it cannot load. This runs every tick so the effect clears the instant the
     * temperature is back inside the comfortable band.
     *
     * The tiers line up with the bands in [OutbreakData.thermalTier]: the edge distortion is a
     * fever thing (38.5 and up), and the motion blur only joins it once the fever is dangerous.
     */
    private fun applyPostEffects(player: ServerPlayer, data: OutbreakData) {
        val tier = data.thermalTier
        syncPostEffect(player, HEAT_HAZE, tier >= 1)
        syncPostEffect(player, HEAT_BLUR, tier >= 2)
        syncPostEffect(player, COLD_SHIVER, tier <= -1)
    }

    /** Removes every screen effect, for `/outbreak cure` and anything else that resets the body. */
    fun clearPostEffects(player: ServerPlayer) {
        syncPostEffect(player, HEAT_HAZE, false)
        syncPostEffect(player, HEAT_BLUR, false)
        syncPostEffect(player, COLD_SHIVER, false)
    }

    private fun syncPostEffect(player: ServerPlayer, id: Identifier, wanted: Boolean) {
        val present = player.getPostEffects().contains(id)
        when {
            // addPostEffect is a no-op when the id is already there, so this never spams packets.
            wanted && !present -> player.addPostEffect(id)
            !wanted && present -> player.removePostEffect(id)
        }
    }

    /**
     * Chance that this tick's shake roll actually shakes the camera, `0..[MAX_SHAKE_CHANCE]`.
     *
     * Split out from [rollShake] so that it can be reasoned about - and tested - without a random
     * number generator, because "why is my view still moving about when nothing is wrong with me?"
     * is a question that deserves an answer rather than a shrug.
     *
     * Every term here has to come from something the player can actually notice. Over-hydration in
     * particular only counts once it is past [OutbreakData.WATER_VISIBLY_OVERHYDRATED], because the
     * thirst bar is ten cells wide and shows anything from 100 to 200 as simply "full" - a player
     * standing there with a full bar and no other symptom was getting a camera tremor out of a
     * state they had no way of seeing.
     */
    fun shakeChance(data: OutbreakData): Float {
        var chance = if (data.isSymptomatic) SHAKE_CHANCE else 0f
        if (data.isImmuneStorm) {
            chance *= STORM_SHAKE_CHANCE_MULTIPLIER
        }
        if (data.water > OutbreakData.WATER_VISIBLY_OVERHYDRATED) {
            chance += 0.1f
        }
        if (data.electrolytes.magnesium < Electrolytes.SAFE_LOW) {
            chance += 0.15f
        }
        if (data.electrolytes.calcium < Electrolytes.DEFICIT) {
            chance += 0.2f
        }
        // Palpitations are a classic thyrotoxic symptom.
        if (data.traceElements.iodine > Electrolytes.EXCESS) {
            chance += 0.1f
        }
        // Teeth chattering, or the rigors of a high fever. Only ever non-zero at or above
        // FEVER_MILD (or at or below COLD_MILD), which is the point of the tier.
        chance += THERMAL_SHAKE_CHANCE * abs(data.thermalTier)
        return chance.coerceAtMost(MAX_SHAKE_CHANCE)
    }

    /**
     * Rolls for the camera shake. Low magnesium and calcium both make the tremor more likely,
     * because both cause real neuromuscular hyperexcitability, and so does a fever or a shiver.
     */
    private fun rollShake(player: ServerPlayer, data: OutbreakData, runtime: OutbreakRuntime) {
        if (runtime.shakeCooldown > 0) {
            runtime.shakeCooldown--
            return
        }
        runtime.shakeCooldown = SHAKE_INTERVAL_TICKS

        val chance = shakeChance(data)
        if (chance <= 0f || player.level().random.nextFloat() >= chance) {
            return
        }

        val amplitude = 0.6f + 0.8f * data.severity +
            (if (data.isImmuneStorm) 0.6f else 0f) +
            (if (data.electrolytes.calcium < Electrolytes.DEFICIT) 0.4f else 0f) +
            (if (data.hasThermalStress) 0.4f else 0f)
        pushShake(player, amplitude)
    }

    /** Bumps the synced shake counter, which is what the client watches. */
    private fun pushShake(player: ServerPlayer, amplitude: Float) {
        val current = player.getAttachedOrElse(OutbreakAttachments.CLIENT, OutbreakClientState.INACTIVE)
        player.setAttached(
            OutbreakAttachments.CLIENT,
            current.copy(shakeSequence = current.shakeSequence + 1, shakeAmplitude = amplitude),
        )
    }

    /**
     * Publishes the bits the client needs.
     *
     * The water level is only resent when its whole number changes, so a draining thirst bar costs
     * roughly one packet every 270 ticks rather than one per tick.
     */
    private fun syncClient(player: ServerPlayer, data: OutbreakData, runtime: OutbreakRuntime) {
        val water = data.water.toInt()
        val current = player.getAttachedOrElse(OutbreakAttachments.CLIENT, OutbreakClientState.INACTIVE)

        if (water == runtime.syncedWater && current.shakeSequence == runtime.syncedShake) {
            return
        }
        runtime.syncedWater = water
        runtime.syncedShake = current.shakeSequence
        player.setAttached(OutbreakAttachments.CLIENT, current.copy(water = water))
    }

    // ------------------------------------------------------------------ the mineral table

    /**
     * One mineral, and what happens when it falls short of, or past, its safe band.
     *
     * [deficit] and [excess] are the extremes, [low] and [high] the mild ends of the same sides, so
     * every entry reads as a two-sided illness rather than as a list of one-way checks.
     */
    private class MineralRule(
        val of: (OutbreakData) -> Float,
        val deficit: Symptom,
        val low: Symptom,
        val high: Symptom,
        val excess: Symptom,
    ) {
        /** The symptom for [value], or null while it is inside the safe band. */
        fun symptomFor(value: Float): Symptom? = when {
            value < Electrolytes.DEFICIT -> this.deficit
            value < Electrolytes.SAFE_LOW -> this.low
            value > Electrolytes.EXCESS -> this.excess
            value > Electrolytes.SAFE_HIGH -> this.high
            else -> null
        }
    }

    /** Builds a [Symptom] out of vanilla effects, with optional damage for the lethal extremes. */
    private fun symptom(vararg effects: Pair<Holder<MobEffect>, Int>, damage: Float = 0f): Symptom =
        { player, ticks ->
            for ((effect, amplifier) in effects) {
                player.addEffect(MobEffectInstance(effect, ticks, amplifier))
            }
            if (damage > 0f) {
                player.hurtServer(player.level(), player.damageSources().magic(), damage)
            }
        }

    /**
     * Every mineral, in the order `/outbreak status` prints them.
     *
     * The thresholds are shared ([Electrolytes.DEFICIT] / [Electrolytes.SAFE_LOW] /
     * [Electrolytes.SAFE_HIGH] / [Electrolytes.EXCESS]) so only the symptoms differ between rows.
     */    private val MINERALS: List<MineralRule> = listOf(
        // Sodium: hyponatraemia is confusion and nausea, hypernatraemia is intense thirst.
        MineralRule(
            of = { it.electrolytes.sodium },
            deficit = symptom(MobEffects.NAUSEA to 0, MobEffects.SLOWNESS to 0),
            low = symptom(MobEffects.WEAKNESS to 0),
            high = symptom(MobEffects.HUNGER to 0),
            excess = symptom(MobEffects.HUNGER to 0, MobEffects.WEAKNESS to 0),
        ),
        // Potassium: both ends upset the heart, which is why the excess one does damage.
        MineralRule(
            of = { it.electrolytes.potassium },
            deficit = symptom(MobEffects.WEAKNESS to 1, MobEffects.MINING_FATIGUE to 0),
            low = symptom(MobEffects.WEAKNESS to 0),
            high = symptom(MobEffects.WEAKNESS to 0),
            excess = symptom(MobEffects.SLOWNESS to 1, damage = 1f),
        ),
        // Magnesium: too little is tremor and cramps, too much is lethargy.
        MineralRule(
            of = { it.electrolytes.magnesium },
            deficit = symptom(MobEffects.WEAKNESS to 0, MobEffects.SLOWNESS to 0),
            low = symptom(MobEffects.WEAKNESS to 0),
            high = symptom(MobEffects.SLOWNESS to 0),
            excess = symptom(MobEffects.SLOWNESS to 0, MobEffects.WEAKNESS to 0),
        ),
        // Chloride follows sodium, but the acid-base side of it shows up as nausea and appetite.
        MineralRule(
            of = { it.electrolytes.chloride },
            deficit = symptom(MobEffects.NAUSEA to 0),
            low = symptom(MobEffects.WEAKNESS to 0),
            high = symptom(MobEffects.HUNGER to 0),
            excess = symptom(MobEffects.HUNGER to 0, MobEffects.NAUSEA to 0),
        ),
        // Calcium: hypocalcaemia is tetany, hypercalcaemia is lethargy.
        MineralRule(
            of = { it.electrolytes.calcium },
            deficit = symptom(MobEffects.SLOWNESS to 0, MobEffects.WEAKNESS to 0),
            low = symptom(MobEffects.WEAKNESS to 0),
            high = symptom(MobEffects.SLOWNESS to 0),
            excess = symptom(MobEffects.SLOWNESS to 1, MobEffects.WEAKNESS to 0),
        ),
        // Iodine: the thyroid. Deficiency is hypothyroidism (slow, weak, cold, hungry); excess is
        // thyrotoxicosis (appetite without weight gain, nausea, muscle wasting).
        MineralRule(
            of = { it.traceElements.iodine },
            deficit = symptom(MobEffects.SLOWNESS to 1, MobEffects.WEAKNESS to 1, MobEffects.MINING_FATIGUE to 0),
            low = symptom(MobEffects.WEAKNESS to 0, MobEffects.SLOWNESS to 0, MobEffects.HUNGER to 0),
            high = symptom(MobEffects.HUNGER to 0, MobEffects.NAUSEA to 0),
            excess = symptom(MobEffects.HUNGER to 0, MobEffects.NAUSEA to 0, MobEffects.WEAKNESS to 0),
        ),
    )
}
