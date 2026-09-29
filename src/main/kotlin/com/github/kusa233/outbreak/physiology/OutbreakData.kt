package com.github.kusa233.outbreak.physiology

import com.github.kusa233.outbreak.registry.Registration
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * The inflammatory mediators the immune response is broken down into.
 *
 * The single "inflammation" number the system used to carry is now derived from these, and drugs
 * act on individual mediators rather than on inflammation as a whole - which is what makes
 * salicin and dexamethasone behave differently.
 */
data class Mediators(
    val histamine: Float,
    val prostaglandin: Float,
    val leukotriene: Float,
    val cytokine: Float,
    val bradykinin: Float,
) {
    /** Inflammation index, 0..100, as a weighted sum of the mediators. */
    val inflammation: Float
        get() = (
            HISTAMINE_WEIGHT * this.histamine +
                PROSTAGLANDIN_WEIGHT * this.prostaglandin +
                LEUKOTRIENE_WEIGHT * this.leukotriene +
                CYTOKINE_WEIGHT * this.cytokine +
                BRADYKININ_WEIGHT * this.bradykinin
            ).coerceIn(0f, 100f)

    companion object {
        const val MAX = 100f

        const val HISTAMINE_WEIGHT = 0.15f
        const val PROSTAGLANDIN_WEIGHT = 0.20f
        const val LEUKOTRIENE_WEIGHT = 0.15f

        /** Cytokines are the systemic driver, so they dominate the index. */
        const val CYTOKINE_WEIGHT = 0.35f
        const val BRADYKININ_WEIGHT = 0.15f

        val CALM = Mediators(0f, 0f, 0f, 0f, 0f)

        /**
         * The mediator levels a healthy player sits at. These are the fixed points of
         * `OutbreakPhysiology` with no pathogen present, and they give an inflammation of exactly
         * [OutbreakData.BASELINE_INFLAMMATION]; the physiology self test asserts that.
         */
        val RESTING = Mediators(
            histamine = 25f,
            prostaglandin = 30f,
            leukotriene = 30f,
            cytokine = 20f,
            bradykinin = 25f,
        )

        val CODEC: Codec<Mediators> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.fieldOf("histamine").forGetter(Mediators::histamine),
                Codec.FLOAT.fieldOf("prostaglandin").forGetter(Mediators::prostaglandin),
                Codec.FLOAT.fieldOf("leukotriene").forGetter(Mediators::leukotriene),
                Codec.FLOAT.fieldOf("cytokine").forGetter(Mediators::cytokine),
                Codec.FLOAT.fieldOf("bradykinin").forGetter(Mediators::bradykinin),
            ).apply(instance, ::Mediators)
        }
    }
}

/**
 * The concentration scale shared by [Electrolytes] and [TraceElements].
 *
 * Both are tracked on the same 0..200 index with 100 as the healthy value, so they share the band
 * and the deviation helper even though they are different kinds of thing.
 *
 * The scale has four thresholds and every one of them works in both directions, because both a
 * deficit and an excess are illnesses:
 *
 * ```
 *   0        DEFICIT   SAFE_LOW   NORMAL   SAFE_HIGH   EXCESS      200
 *   |  severe  |  mild   |  healthy  |   mild   |  severe  |
 * ```
 *
 * `OutbreakPhysiology` pulls every value towards [NORMAL], so a player who does nothing at all
 * settles somewhere inside this picture rather than drifting to an extreme.
 */
object MineralScale {
    const val MIN = 0f
    const val MAX = 200f

    /** Healthy concentration; the set point every value is regulated towards. */
    const val NORMAL = 100f

    /** Outside this band the player starts showing symptoms. */
    const val SAFE_LOW = 85f
    const val SAFE_HIGH = 115f

    /** A marked deficit or excess, where the symptoms become serious. */
    const val DEFICIT = 70f
    const val EXCESS = 130f

    /** Distance outside the safe band; 0 while inside it. */
    fun deviation(value: Float): Float = when {
        value < SAFE_LOW -> SAFE_LOW - value
        value > SAFE_HIGH -> value - SAFE_HIGH
        else -> 0f
    }

    /** Which side of the safe band [value] is on, as the sign of [deviation]. */
    fun direction(value: Float): Int = when {
        value < SAFE_LOW -> -1
        value > SAFE_HIGH -> 1
        else -> 0
    }
}

/**
 * The five electrolytes that are tracked separately.
 *
 * [MineralScale.NORMAL] is the healthy concentration and the safe band is
 * [MineralScale.SAFE_LOW]..[MineralScale.SAFE_HIGH]; both a deficit and an excess are modelled,
 * because eating salt and drinking too much water push the values in opposite directions.
 */
data class Electrolytes(
    val sodium: Float,
    val potassium: Float,
    val magnesium: Float,
    val chloride: Float,
    val calcium: Float,
) {
    /** Every value, for the aggregate helpers below. */
    private fun all(): List<Float> =
        listOf(this.sodium, this.potassium, this.magnesium, this.chloride, this.calcium)

    /** Lowest value in the set, 0..200; handy for "is anything badly off" checks. */
    val lowest: Float
        get() = this.all().min()

    /** How far the worst offender is outside the safe band, in points. 0 when everything is fine. */
    val worstImbalance: Float
        get() = this.all().maxOf { MineralScale.deviation(it) }

    fun scaled(factor: Float): Electrolytes = Electrolytes(
        sodium = (this.sodium * factor).coerceIn(MineralScale.MIN, MineralScale.MAX),
        potassium = (this.potassium * factor).coerceIn(MineralScale.MIN, MineralScale.MAX),
        magnesium = (this.magnesium * factor).coerceIn(MineralScale.MIN, MineralScale.MAX),
        chloride = (this.chloride * factor).coerceIn(MineralScale.MIN, MineralScale.MAX),
        calcium = (this.calcium * factor).coerceIn(MineralScale.MIN, MineralScale.MAX),
    )

    companion object {
        const val MIN = MineralScale.MIN
        const val MAX = MineralScale.MAX
        const val NORMAL = MineralScale.NORMAL
        const val SAFE_LOW = MineralScale.SAFE_LOW
        const val SAFE_HIGH = MineralScale.SAFE_HIGH
        const val DEFICIT = MineralScale.DEFICIT
        const val EXCESS = MineralScale.EXCESS

        val HEALTHY = Electrolytes(NORMAL, NORMAL, NORMAL, NORMAL, NORMAL)

        fun deviation(value: Float): Float = MineralScale.deviation(value)

        val CODEC: Codec<Electrolytes> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.fieldOf("sodium").forGetter(Electrolytes::sodium),
                Codec.FLOAT.fieldOf("potassium").forGetter(Electrolytes::potassium),
                Codec.FLOAT.fieldOf("magnesium").forGetter(Electrolytes::magnesium),
                Codec.FLOAT.fieldOf("chloride").forGetter(Electrolytes::chloride),
                Codec.FLOAT.fieldOf("calcium").forGetter(Electrolytes::calcium),
            ).apply(instance, ::Electrolytes)
        }
    }
}

/**
 * The trace elements, which is iodine for now.
 *
 * Kept apart from the electrolytes because they behave differently: the body cannot make iodine at
 * all, so the only way in is food - kelp, in this mod. It is still regulated, but the regulation is
 * one-sided: a deficit is corrected by hanging on to what little there is (renal conservation)
 * rather than by manufacturing more, so the diet sets how high the equilibrium sits.
 */
data class TraceElements(
    val iodine: Float,
) {
    val worstImbalance: Float
        get() = MineralScale.deviation(this.iodine)

    /** -1 for a deficit, 1 for an excess, 0 while iodine is inside the safe band. */
    val direction: Int
        get() = MineralScale.direction(this.iodine)

    companion object {
        val HEALTHY = TraceElements(MineralScale.NORMAL)

        val CODEC: Codec<TraceElements> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.fieldOf("iodine").forGetter(TraceElements::iodine),
            ).apply(instance, ::TraceElements)
        }
    }
}

/**
 * Everything Outbreak tracks about a player's body.
 *
 * The class is immutable on purpose: every tick produces a new instance, which is trivial to
 * serialise and to compare.
 */
data class OutbreakData(
    val mediators: Mediators,
    val bacteria: Float,
    val virus: Float,
    /** Total body water, 0..[WATER_MAX]. The normal band is [WATER_LOW]..[WATER_NORMAL]. */
    val water: Float,
    val electrolytes: Electrolytes,
    val traceElements: TraceElements,
    /** Salicin, the willow bark soup drug. */
    val salicin: Float,
    /** Dexamethasone, the injected corticosteroid. */
    val dexamethasone: Float,
    /**
     * Core temperature in degrees Celsius, [TEMPERATURE_MIN]..[TEMPERATURE_MAX], normally
     * [TEMPERATURE_NORMAL].
     */
    val temperature: Float = TEMPERATURE_NORMAL,
    /**
     * Circulating pyrogen, the exogenous fever driver. It behaves like a drug - the command that
     * induces a fever for testing sets it, and it is metabolised away over
     * [PYROGEN_METABOLISM_TICKS] - while the fever of a real infection comes from prostaglandin.
     */
    val pyrogen: Float = 0f,
) {
    val inflammation: Float
        get() = this.mediators.inflammation

    /** Combined pathogen load, 0..200 in theory, 0..100 in practice. */
    val pathogenLoad: Float
        get() = this.bacteria + this.virus

    /** True once the load is high enough to actually make the player feel ill. */
    val isSymptomatic: Boolean
        get() = this.pathogenLoad >= SYMPTOM_THRESHOLD

    val isImmuneStorm: Boolean
        get() = this.inflammation >= IMMUNE_STORM_THRESHOLD

    val isImmunosuppressed: Boolean
        get() = this.inflammation <= IMMUNOSUPPRESSION_THRESHOLD

    /** 0..1 severity used to scale symptoms; saturates at [SEVERE_LOAD]. */
    val severity: Float
        get() = (this.pathogenLoad / SEVERE_LOAD).coerceIn(0f, 1f)

    /** True above [WATER_NORMAL]; causes weakness and slower mining. */
    val isOverhydrated: Boolean
        get() = this.water > WATER_NORMAL

    /** True below [WATER_LOW]; the thirst bar is nearly empty. */
    val isDehydrated: Boolean
        get() = this.water < WATER_LOW

    /** The thirst bar, 0..10 cells. Each 10 points is one cell, and anything above 100 is full. */
    val thirstCells: Int
        get() = (this.water / 10f).toInt().coerceIn(0, THIRST_CELLS)

    /** True when any electrolyte is outside the safe band. */
    val hasElectrolyteImbalance: Boolean
        get() = this.electrolytes.worstImbalance > 0f

    /** True when any trace element is outside the safe band. */
    val hasTraceElementImbalance: Boolean
        get() = this.traceElements.worstImbalance > 0f

    // ------------------------------------------------------------------ temperature

    /** True at or above [FEVER_MILD]; the player is running a fever. */
    val isFebrile: Boolean
        get() = this.temperature >= FEVER_MILD

    /** True at or below [COLD_MILD]; the player is hypothermic. */
    val isHypothermic: Boolean
        get() = this.temperature <= COLD_MILD

    /** True while the core temperature is outside the comfortable band. */
    val hasThermalStress: Boolean
        get() = this.thermalTier != 0

    /**
     * How far outside the comfortable band the core temperature is, as a signed tier:
     *
     * | tier | range | meaning |
     * | --- | --- | --- |
     * | 2 | ≥ 40.0 | super-high fever: everything tier 1 does, plus motion blur |
     * | 1 | 38.5 – 40.0 | fever: flushing and distortion at the screen edges, weakness, mining fatigue |
     * | 0 | 36.0 < T < 38.5 | comfortable |
     * | -1 | 35.0 – 36.0 | mild hypothermia |
     * | -2 | ≤ 35.0 | severe hypothermia |
     */
    val thermalTier: Int
        get() = when {
            this.temperature >= FEVER_SEVERE -> 2
            this.temperature >= FEVER_MILD -> 1
            this.temperature <= COLD_SEVERE -> -2
            this.temperature <= COLD_MILD -> -1
            else -> 0
        }

    fun withMediators(value: Mediators): OutbreakData = this.copy(mediators = value)

    fun withElectrolytes(value: Electrolytes): OutbreakData = this.copy(electrolytes = value)

    fun withTraceElements(value: TraceElements): OutbreakData = this.copy(traceElements = value)

    companion object {
        const val MIN_INFLAMMATION = 0f
        const val MAX_INFLAMMATION = 100f

        /** The band a healthy player sits in. */
        const val SAFE_INFLAMMATION_LOW = 20f
        const val SAFE_INFLAMMATION_HIGH = 30f
        const val BASELINE_INFLAMMATION = 25f

        /** Below this the immune system stops keeping up with the pathogens. */
        const val IMMUNOSUPPRESSION_THRESHOLD = 12f

        /** Above this the immune response itself becomes the problem. */
        const val IMMUNE_STORM_THRESHOLD = 75f

        const val MAX_PATHOGEN = 100f

        /** Load at which symptoms start showing. */
        const val SYMPTOM_THRESHOLD = 8f

        /** Load at which symptoms are at full strength. */
        const val SEVERE_LOAD = 60f

        // ---------------------------------------------------------------- water

        const val WATER_MIN = 0f

        /** Lower edge of the normal band. */
        const val WATER_LOW = 30f

        /** Upper edge of the normal band; above this the player is over-hydrated. */
        const val WATER_NORMAL = 100f

        /** Hard ceiling so drinking cannot run away. */
        const val WATER_MAX = 200f

        /**
         * Where over-hydration becomes something the player can see for themselves.
         *
         * The thirst bar is [THIRST_CELLS] cells and anything from [WATER_NORMAL] upwards draws as
         * a full bar, so the bar alone cannot tell 101 from 200. From this point on the player also
         * gets nausea (`OutbreakSymptoms`), which is the visible marker; effects that are only
         * confusing when they come out of nowhere - the camera tremor - wait for it.
         */
        const val WATER_VISIBLY_OVERHYDRATED = 150f

        /** The thirst bar has ten cells. */
        const val THIRST_CELLS = 10

        /** One drink adds this much water. */
        const val WATER_PER_DRINK = 15f

        /**
         * A full bladder drains to a single cell over one in-game day, so 90 points per 24000
         * ticks. The rate is a hair under that so that after exactly one day the bar still reads
         * one cell rather than falling off the bottom of it through float drift.
         */
        const val WATER_DECAY_PER_TICK = 89.5f / 24_000f

        // ---------------------------------------------------------------- drugs

        /** Drug concentration needed before salicin starts damping prostaglandins. */
        const val SALICIN_EFFECTIVE = 1f

        /** Highest concentration a player can build up. */
        const val SALICIN_CAP = 3f

        /** Salicin is fully metabolised after three in-game days. */
        const val SALICIN_METABOLISM_TICKS = 72_000
        const val SALICIN_DECAY_PER_TICK = SALICIN_CAP / SALICIN_METABOLISM_TICKS

        /** Dexamethasone is far more potent and lasts two in-game days. */
        const val DEXAMETHASONE_EFFECTIVE = 1f
        const val DEXAMETHASONE_CAP = 2f
        const val DEXAMETHASONE_METABOLISM_TICKS = 48_000
        const val DEXAMETHASONE_DECAY_PER_TICK = DEXAMETHASONE_CAP / DEXAMETHASONE_METABOLISM_TICKS

        // ---------------------------------------------------------------- temperature

        /** Core temperature of a healthy player, and the set point thermoregulation defends. */
        const val TEMPERATURE_NORMAL = 37.0f

        /**
         * The comfortable band, just above 36.0 up to just below 38.5.
         *
         * The fever side deliberately starts at **38.5**, not 38.0. A temperature of 38 is what a
         * hot biome, a fire, or a thyroid that runs hot produces on their own, and the screen
         * effects and the shiver it switched on were indistinguishable from a bug: every visible
         * vital sign read normal and the player's view was still swimming.
         */
        const val COLD_MILD = 36.0f
        const val FEVER_MILD = 38.5f

        /** Past these the thermal symptoms get worse; 40 is where a fever turns dangerous. */
        const val COLD_SEVERE = 35.0f
        const val FEVER_SEVERE = 40.0f

        /** Hard clamp for the model; 42 is where proteins start to denature. */
        const val TEMPERATURE_MIN = 30.0f
        const val TEMPERATURE_MAX = 42.0f

        /**
         * The most pyrogen a body can carry. Pyrogen is what the fever test command injects, so
         * the cap is also the largest fever the command can produce above the current one.
         */
        const val PYROGEN_CAP = 6f

        /**
         * An injected pyrogen is cleared over one in-game day.
         *
         * Long enough that the body can walk to the set point (that takes a couple of in-game
         * minutes) and the player can look at what it does to the screen, short enough that a test
         * fever is gone before it can be mistaken for a bug: the previous five day metabolism left
         * a `/outbreak fever` shimmering on the screen for over an hour after the command.
         *
         * `OutbreakPhysiology.induceFever` adds back the distance the body loses to this decay, so
         * the *peak* is still exactly the temperature that was asked for.
         */
        const val PYROGEN_METABOLISM_TICKS = 24_000
        const val PYROGEN_DECAY_PER_TICK = PYROGEN_CAP / PYROGEN_METABOLISM_TICKS

        /** What a healthy player looks like. */
        val HEALTHY = OutbreakData(
            mediators = Mediators.RESTING,
            bacteria = 0f,
            virus = 0f,
            water = 80f,
            electrolytes = Electrolytes.HEALTHY,
            traceElements = TraceElements.HEALTHY,
            salicin = 0f,
            dexamethasone = 0f,
            temperature = TEMPERATURE_NORMAL,
            pyrogen = 0f,
        )

        val CODEC: Codec<OutbreakData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Mediators.CODEC.fieldOf("mediators").forGetter(OutbreakData::mediators),
                Codec.FLOAT.fieldOf("bacteria").forGetter(OutbreakData::bacteria),
                Codec.FLOAT.fieldOf("virus").forGetter(OutbreakData::virus),
                Codec.FLOAT.fieldOf("water").forGetter(OutbreakData::water),
                Electrolytes.CODEC.fieldOf("electrolytes").forGetter(OutbreakData::electrolytes),
                TraceElements.CODEC.fieldOf("trace_elements").forGetter(OutbreakData::traceElements),
                Codec.FLOAT.fieldOf("salicin").forGetter(OutbreakData::salicin),
                Codec.FLOAT.fieldOf("dexamethasone").forGetter(OutbreakData::dexamethasone),
                // Optional with a default so that a world saved before temperature existed loads
                // as a healthy player instead of being thrown away.
                Codec.FLOAT.optionalFieldOf("temperature", TEMPERATURE_NORMAL)
                    .forGetter(OutbreakData::temperature),
                Codec.FLOAT.optionalFieldOf("pyrogen", 0f).forGetter(OutbreakData::pyrogen),
            ).apply(instance, ::OutbreakData)
        }
    }
}

/**
 * The part of the physiology the client needs: a counter that ticks up whenever the server decides
 * the player should shake, how hard, and the current water level so the thirst bar can be drawn.
 *
 * Sending a counter instead of a countdown keeps the traffic to one packet per shake event, and the
 * water level is only resent when its whole number changes.
 */
data class OutbreakClientState(
    val shakeSequence: Int,
    val shakeAmplitude: Float,
    val water: Int,
) {
    companion object {
        val INACTIVE = OutbreakClientState(0, 0f, 0)

        val CODEC: Codec<OutbreakClientState> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("shake_sequence").forGetter(OutbreakClientState::shakeSequence),
                Codec.FLOAT.fieldOf("shake_amplitude").forGetter(OutbreakClientState::shakeAmplitude),
                Codec.INT.fieldOf("water").forGetter(OutbreakClientState::water),
            ).apply(instance, ::OutbreakClientState)
        }

        val STREAM_CODEC: StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, OutbreakClientState> =
            StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                OutbreakClientState::shakeSequence,
                ByteBufCodecs.FLOAT,
                OutbreakClientState::shakeAmplitude,
                ByteBufCodecs.VAR_INT,
                OutbreakClientState::water,
                ::OutbreakClientState,
            )
    }
}

object OutbreakAttachments {

    /**
     * The full physiology. Persistent and server-authoritative, so it is never synced: the client
     * only ever needs [CLIENT].
     */
    val DATA: AttachmentType<OutbreakData> = AttachmentRegistry.create(Registration.id("physiology")) { builder ->
        builder
            .persistent(OutbreakData.CODEC)
            .copyOnDeath()
            .initializer { OutbreakData.HEALTHY }
    }

    /** Synced to every client that can see the player: drives the camera shake and the thirst bar. */
    val CLIENT: AttachmentType<OutbreakClientState> = AttachmentRegistry.create(Registration.id("client_state")) { builder ->
        builder
            .persistent(OutbreakClientState.CODEC)
            .initializer { OutbreakClientState.INACTIVE }
            .syncWith(OutbreakClientState.STREAM_CODEC, AttachmentSyncPredicate.all())
    }

    /**
     * Per-player timers that only matter while the player is online, so they are neither saved nor
     * synced.
     */
    val RUNTIME: AttachmentType<OutbreakRuntime> = AttachmentRegistry.create(Registration.id("runtime"))

    fun initialize() {
        // Registration happens in the property initialisers above.
    }
}

/** Short lived counters; deliberately mutable and never serialised. */
class OutbreakRuntime {
    /** Ticks until the next "will the view shake?" roll. */
    var shakeCooldown: Int = 0

    /** Ticks during which touching a bat cannot roll an infection again. */
    var batCooldown: Int = 0

    /** Last water value that was pushed to the client, so the bar is only resent when it moves. */
    var syncedWater: Int = Int.MIN_VALUE

    /** Last shake counter that was pushed to the client. */
    var syncedShake: Int = Int.MIN_VALUE
}
