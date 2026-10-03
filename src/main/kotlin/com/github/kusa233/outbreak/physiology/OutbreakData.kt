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
 * The single "inflammation" number the system used to carry is now derived from these, and drugs act
 * on individual mediators rather than on inflammation as a whole - which is what makes salicin and
 * dexamethasone behave differently.
 *
 * The weights and the index itself are the model's, in Scala; this is only the shape Kotlin stores
 * and serialises.
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
        get() = OutbreakModelBridge.inflammation(
            this.histamine, this.prostaglandin, this.leukotriene, this.cytokine, this.bradykinin,
        )

    fun withHistamine(value: Float): Mediators = this.copy(histamine = value)

    fun withProstaglandin(value: Float): Mediators = this.copy(prostaglandin = value)

    fun withLeukotriene(value: Float): Mediators = this.copy(leukotriene = value)

    fun withCytokine(value: Float): Mediators = this.copy(cytokine = value)

    fun withBradykinin(value: Float): Mediators = this.copy(bradykinin = value)

    companion object {

        @JvmField val MAX: Float = OutbreakModelBridge.MEDIATORS_MAX

        @JvmField val HISTAMINE_WEIGHT: Float = OutbreakModelBridge.MEDIATORS_HISTAMINE_WEIGHT
        @JvmField val PROSTAGLANDIN_WEIGHT: Float = OutbreakModelBridge.MEDIATORS_PROSTAGLANDIN_WEIGHT
        @JvmField val LEUKOTRIENE_WEIGHT: Float = OutbreakModelBridge.MEDIATORS_LEUKOTRIENE_WEIGHT

        /** Cytokines are the systemic driver, so they dominate the index. */
        @JvmField val CYTOKINE_WEIGHT: Float = OutbreakModelBridge.MEDIATORS_CYTOKINE_WEIGHT
        @JvmField val BRADYKININ_WEIGHT: Float = OutbreakModelBridge.MEDIATORS_BRADYKININ_WEIGHT

        /** No response at all: the fixed point a completely immunosuppressed body sits at. */
        @JvmField val CALM: Mediators = OutbreakModelBridge.calmMediators()

        /**
         * The mediator levels a healthy player sits at. These are the fixed points of the model with
         * no pathogen present, and they give an inflammation of exactly
         * [OutbreakData.BASELINE_INFLAMMATION]; the physiology self test asserts that.
         */
        @JvmField val RESTING: Mediators = OutbreakModelBridge.restingMediators()

        val CODEC: Codec<Mediators> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.fieldOf("histamine").forGetter { it.histamine },
                Codec.FLOAT.fieldOf("prostaglandin").forGetter { it.prostaglandin },
                Codec.FLOAT.fieldOf("leukotriene").forGetter { it.leukotriene },
                Codec.FLOAT.fieldOf("cytokine").forGetter { it.cytokine },
                Codec.FLOAT.fieldOf("bradykinin").forGetter { it.bradykinin },
            ).apply(instance, ::Mediators)
        }
    }
}

/**
 * The five electrolytes that are tracked separately, in mmol/L.
 *
 * [Mineral.normal] is the healthy concentration and the safe band is [Mineral.safeLow]..
 * [Mineral.safeHigh]; both a deficit and an excess are modelled, because eating salt and drinking too
 * much water push the values in opposite directions.
 */
data class Electrolytes(
    val sodium: Float,
    val potassium: Float,
    val magnesium: Float,
    val chloride: Float,
    val calcium: Float,
) {

    /** The value of [mineral], for the table-driven parts. */
    fun of(mineral: Mineral): Float = when (mineral) {
        Mineral.SODIUM -> this.sodium
        Mineral.POTASSIUM -> this.potassium
        Mineral.MAGNESIUM -> this.magnesium
        Mineral.CHLORIDE -> this.chloride
        Mineral.CALCIUM -> this.calcium
        Mineral.IODINE -> throw IllegalArgumentException("iodine is a trace element, not an electrolyte")
    }

    /** How far the worst offender is outside its reference range, as a fraction of normal. */
    val worstImbalance: Float
        get() = OutbreakModelBridge.worstImbalance(
            this.sodium, this.potassium, this.magnesium, this.chloride, this.calcium,
        )

    fun withSodium(value: Float): Electrolytes = this.copy(sodium = value)

    fun withPotassium(value: Float): Electrolytes = this.copy(potassium = value)

    fun withMagnesium(value: Float): Electrolytes = this.copy(magnesium = value)

    fun withChloride(value: Float): Electrolytes = this.copy(chloride = value)

    fun withCalcium(value: Float): Electrolytes = this.copy(calcium = value)

    companion object {

        /** The five electrolytes, in the order `/outbreak status` prints them. */
        @JvmField val MINERALS: List<Mineral> =
            listOf(Mineral.SODIUM, Mineral.POTASSIUM, Mineral.MAGNESIUM, Mineral.CHLORIDE, Mineral.CALCIUM)

        /** Every electrolyte at its normal concentration. */
        @JvmField val HEALTHY: Electrolytes = OutbreakModelBridge.healthyElectrolytes()

        val CODEC: Codec<Electrolytes> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.fieldOf("sodium").forGetter { it.sodium },
                Codec.FLOAT.fieldOf("potassium").forGetter { it.potassium },
                Codec.FLOAT.fieldOf("magnesium").forGetter { it.magnesium },
                Codec.FLOAT.fieldOf("chloride").forGetter { it.chloride },
                Codec.FLOAT.fieldOf("calcium").forGetter { it.calcium },
            ).apply(instance, ::Electrolytes)
        }
    }
}

/**
 * The trace elements, which is iodine for now, in umol/L.
 *
 * Kept apart from the electrolytes because they behave differently: the body cannot make iodine at
 * all, so the only way in is food - kelp, in this mod. It is still regulated, but the regulation is
 * one-sided: a deficit is corrected by hanging on to what little there is (renal conservation)
 * rather than by manufacturing more, so the diet sets how high the equilibrium sits.
 */
data class TraceElements(
    val iodine: Float,
) {

    /** How far outside its reference range iodine is, as a fraction of normal. */
    val worstImbalance: Float get() = Mineral.IODINE.relativeDeviation(this.iodine)

    /** -1 for a deficit, 1 for an excess, 0 while iodine is inside the reference range. */
    val direction: Int get() = Mineral.IODINE.direction(this.iodine)

    fun withIodine(value: Float): TraceElements = this.copy(iodine = value)

    companion object {

        @JvmField val HEALTHY: TraceElements = OutbreakModelBridge.healthyTraceElements()

        val CODEC: Codec<TraceElements> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.fieldOf("iodine").forGetter { it.iodine },
            ).apply(instance, ::TraceElements)
        }
    }
}

/**
 * A player's body, as far as this mod is concerned.
 *
 * This class is the **storage and serialisation** layer: it is what the Fabric attachment holds, what
 * the codecs read, and what the rest of the mod reads its thresholds from. Every number and every
 * steady state lives in Scala, in `src/main/scala/.../physiology/model`, and reaches this file
 * through [OutbreakModelBridge] - the one place that knows both representations.
 *
 * The derived values below ([inflammation], [thermalTier], [isSevereInfection] and friends) are
 * delegations to that model rather than a second copy of its rules, so a threshold is defined in
 * exactly one place.
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
    /** Core temperature in degrees Celsius, normally [TEMPERATURE_NORMAL]. */
    val temperature: Float = TEMPERATURE_NORMAL,
    /** Circulating pyrogen, the exogenous fever driver, cleared over a game day. */
    val pyrogen: Float = 0f,
    /**
     * Scopolamine and atropine: the two tropane alkaloids a mandrake carries, 0..[ANTICHOLINERGIC_CAP]
     * each. Past 1.5 of the two together the body runs a temperature, and past 2.3 of either (or 2.7
     * of the two) the player's sight blurs down to [ANTICHOLINERGIC_BLUR_DISTANCE] blocks.
     */
    val scopolamine: Float = 0f,
    val atropine: Float = 0f,
    /**
     * What a raw gymnopilus carries, 0..[PSILOCYBIN_CAP] and 0..[PSILOCIN_CAP].
     *
     * Psilocybin has no effect of its own: it converts into psilocin one for one over half a game
     * day, and psilocin is what the trip and the fever come from. Psilocin itself is cleared at a
     * flat [PSILOCIN_DOSE] a game day, so a big dose lasts proportionally longer.
     */
    val psilocybin: Float = 0f,
    val psilocin: Float = 0f,
    /** Ephedrine, the stimulant alkaloid, 0..[EPHEDRINE_CAP]. */
    val ephedrine: Float = 0f,
    /** Whether the active immune response has been triggered (once pathogen load > 20). */
    val immuneActive: Boolean = false,
) {

    val inflammation: Float
        get() = this.mediators.inflammation

    /** Combined pathogen load, 0..200 in theory, 0..100 in practice. */
    val pathogenLoad: Float
        get() = OutbreakModelBridge.pathogenLoad(this.bacteria, this.virus)

    /** True once the load is high enough to actually make the player feel ill. */
    val isSymptomatic: Boolean
        get() = OutbreakModelBridge.isSymptomatic(this.pathogenLoad)

    val isImmuneStorm: Boolean
        get() = OutbreakModelBridge.isImmuneStorm(this.inflammation)

    val isImmunosuppressed: Boolean
        get() = OutbreakModelBridge.isImmunosuppressed(this.inflammation)

    /** 0..1 severity used to scale symptoms; saturates at [SEVERE_LOAD]. */
    val severity: Float
        get() = OutbreakModelBridge.severity(this.pathogenLoad)

    /**
     * True once the infection is severe enough to hurt the host directly - sepsis, in the sense the
     * model uses. Reached through the infection and never through the immune system on its own: a low
     * inflammation does no damage itself, it only lets an infection climb until *this* is true.
     */
    val isSevereInfection: Boolean
        get() = OutbreakModelBridge.isSevereInfection(this.pathogenLoad)

    /** True above [WATER_NORMAL]; causes weakness and slower mining. */
    val isOverhydrated: Boolean
        get() = OutbreakModelBridge.isOverhydrated(this.water)

    /** True below [WATER_LOW]; the thirst bar is nearly empty. */
    val isDehydrated: Boolean
        get() = OutbreakModelBridge.isDehydrated(this.water)

    /** The thirst bar, 0..10 cells. Each 10 points is one cell, and anything above 100 is full. */
    val thirstCells: Int
        get() = OutbreakModelBridge.thirstCells(this.water)

    /** True when any electrolyte is outside its reference range. */
    val hasElectrolyteImbalance: Boolean
        get() = this.electrolytes.worstImbalance > 0f

    /** True when any trace element is outside its reference range. */
    val hasTraceElementImbalance: Boolean
        get() = this.traceElements.worstImbalance > 0f

    // ------------------------------------------------------------------ temperature

    /** True at or above [FEVER_MILD]; the player is running a fever. */
    val isFebrile: Boolean
        get() = OutbreakModelBridge.isFebrile(this.temperature)

    /** True at or below [COLD_MILD]; the player is hypothermic. */
    val isHypothermic: Boolean
        get() = OutbreakModelBridge.isHypothermic(this.temperature)

    /** True while the core temperature is outside the comfortable band. */
    val hasThermalStress: Boolean
        get() = OutbreakModelBridge.hasThermalStress(this.temperature)

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
        get() = OutbreakModelBridge.thermalTier(this.temperature)

    // ------------------------------------------------------------------ mandrake alkaloids

    /** Scopolamine plus atropine, which is what the fever and the blur are both judged on. */
    val anticholinergicLoad: Float
        get() = OutbreakModelBridge.anticholinergicLoad(this)

    /**
     * True when the alkaloids have blurred the player's sight: either one past 2.3, or the two
     * together past 2.7. Independent of any fever, so the two can be on the screen at once.
     */
    val isVisionBlurred: Boolean
        get() = OutbreakModelBridge.isVisionBlurred(this)

    /**
     * How far into the trip the player is, as the stage the client has a screen effect for:
     *
     * | tier | psilocin | what the screen does |
     * | --- | --- | --- |
     * | 0 | ≤ 1.2 | nothing |
     * | 1 | > 1.2 | coloured outlines along every block edge |
     * | 2 | > 1.7 | the blocks themselves start taking random bright colours |
     * | 3 | > 2.5 | and the whole screen starts to bend |
     * | 4 | > 5 | it bends hard, and the body starts to run hot |
     */
    val psilocinTier: Int
        get() = OutbreakModelBridge.psilocinTier(this)

    /** True when ephedrine exceeds the threshold for Haste I. */
    val hasHasteFromEphedrine: Boolean
        get() = OutbreakModelBridge.hasHasteFromEphedrine(this)

    fun withMediators(value: Mediators): OutbreakData = this.copy(mediators = value)

    fun withElectrolytes(value: Electrolytes): OutbreakData = this.copy(electrolytes = value)

    fun withTraceElements(value: TraceElements): OutbreakData = this.copy(traceElements = value)

    fun withWater(value: Float): OutbreakData = this.copy(water = value)

    fun withBacteria(value: Float): OutbreakData = this.copy(bacteria = value)

    fun withVirus(value: Float): OutbreakData = this.copy(virus = value)

    fun withSalicin(value: Float): OutbreakData = this.copy(salicin = value)

    fun withDexamethasone(value: Float): OutbreakData = this.copy(dexamethasone = value)

    fun withTemperature(value: Float): OutbreakData = this.copy(temperature = value)

    fun withPyrogen(value: Float): OutbreakData = this.copy(pyrogen = value)

    fun withScopolamine(value: Float): OutbreakData = this.copy(scopolamine = value)

    fun withAtropine(value: Float): OutbreakData = this.copy(atropine = value)

    fun withPsilocybin(value: Float): OutbreakData = this.copy(psilocybin = value)

    fun withPsilocin(value: Float): OutbreakData = this.copy(psilocin = value)

    fun withEphedrine(value: Float): OutbreakData = this.copy(ephedrine = value)

    fun withImmuneActive(value: Boolean): OutbreakData = this.copy(immuneActive = value)

    companion object {

        // ---------------------------------------------------------------- the numbers
        //
        // Re-exported from the Scala model through the bridge, which owns every one of them. They keep
        // their old names and their old home so that nothing else in the mod, or in the docs, had to
        // move with them.

        @JvmField val MIN_INFLAMMATION: Float = OutbreakModelBridge.MIN_INFLAMMATION
        @JvmField val MAX_INFLAMMATION: Float = OutbreakModelBridge.MAX_INFLAMMATION

        /** The band a healthy player sits in. */
        @JvmField val SAFE_INFLAMMATION_LOW: Float = OutbreakModelBridge.SAFE_INFLAMMATION_LOW
        @JvmField val SAFE_INFLAMMATION_HIGH: Float = OutbreakModelBridge.SAFE_INFLAMMATION_HIGH

        /** The model's fixed point with no pathogen present, and the middle of the safe band. */
        @JvmField val BASELINE_INFLAMMATION: Float = OutbreakModelBridge.BASELINE_INFLAMMATION

        /** Below this the immune system is suppressed and the infection runs away. */
        @JvmField val IMMUNOSUPPRESSION_THRESHOLD: Float = OutbreakModelBridge.IMMUNOSUPPRESSION_THRESHOLD

        /** Above this the response itself is the disease. */
        @JvmField val IMMUNE_STORM_THRESHOLD: Float = OutbreakModelBridge.IMMUNE_STORM_THRESHOLD

        @JvmField val MAX_PATHOGEN: Float = OutbreakModelBridge.MAX_PATHOGEN

        /** Load at which the player starts showing symptoms. */
        @JvmField val SYMPTOM_THRESHOLD: Float = OutbreakModelBridge.SYMPTOM_THRESHOLD

        /** Pathogen load threshold above which immune response activates and starts suppression. */
        @JvmField val IMMUNITY_ACTIVATION_LOAD: Float = OutbreakModelBridge.IMMUNITY_ACTIVATION_LOAD

        /** Pathogen load threshold above which immune system enters stress and inflammation escalates. */
        @JvmField val IMMUNE_STRESS_LOAD: Float = OutbreakModelBridge.IMMUNE_STRESS_LOAD

        /** Load at which the symptoms are at full strength and the infection starts doing damage. */
        @JvmField val SEVERE_LOAD: Float = OutbreakModelBridge.SEVERE_LOAD

        /** Maximum core temperature under normal immune fever response before stress stage. */
        @JvmField val FEVER_NORMAL_IMMUNE_MAX: Float = OutbreakModelBridge.FEVER_NORMAL_IMMUNE_MAX

        // ---------------------------------------------------------------- water

        @JvmField val WATER_MIN: Float = OutbreakModelBridge.WATER_MIN

        /** Below this the player is dehydrated. */
        @JvmField val WATER_LOW: Float = OutbreakModelBridge.WATER_LOW

        /** The top of the normal band. Above it the player is over-hydrated. */
        @JvmField val WATER_NORMAL: Float = OutbreakModelBridge.WATER_NORMAL

        /** Hard ceiling so drinking cannot run away. */
        @JvmField val WATER_MAX: Float = OutbreakModelBridge.WATER_MAX

        /** What a player starts with, and what `/outbreak cure` restores. */
        @JvmField val WATER_START: Float = OutbreakModelBridge.WATER_START

        /**
         * Where over-hydration becomes something the player can see for themselves: the thirst bar is
         * [THIRST_CELLS] cells and anything from [WATER_NORMAL] upwards draws as a full bar, so the
         * bar alone cannot tell 101 from 200. From here on the player also gets nausea, which is the
         * visible marker; effects that are only confusing when they come out of nowhere - the camera
         * tremor - wait for it.
         */
        @JvmField val WATER_VISIBLY_OVERHYDRATED: Float = OutbreakModelBridge.WATER_VISIBLY_OVERHYDRATED

        /** Below this the player is not just thirsty: hunger sets in. */
        @JvmField val WATER_SEVERELY_DEHYDRATED: Float = OutbreakModelBridge.WATER_SEVERELY_DEHYDRATED

        @JvmField val THIRST_CELLS: Int = OutbreakModelBridge.THIRST_CELLS

        /** Water added by any drinkable. */
        @JvmField val WATER_PER_DRINK: Float = OutbreakModelBridge.WATER_PER_DRINK

        /** A full bladder drains to one cell over roughly one in-game day. */
        @JvmField val WATER_DECAY_PER_TICK: Float = OutbreakModelBridge.WATER_DECAY_PER_TICK

        // ---------------------------------------------------------------- drugs

        @JvmField val SALICIN_EFFECTIVE: Float = OutbreakModelBridge.SALICIN_EFFECTIVE
        @JvmField val SALICIN_CAP: Float = OutbreakModelBridge.SALICIN_CAP
        @JvmField val SALICIN_METABOLISM_TICKS: Int = OutbreakModelBridge.SALICIN_METABOLISM_TICKS
        @JvmField val SALICIN_DECAY_PER_TICK: Float = OutbreakModelBridge.SALICIN_DECAY_PER_TICK

        @JvmField val DEXAMETHASONE_EFFECTIVE: Float = OutbreakModelBridge.DEXAMETHASONE_EFFECTIVE
        @JvmField val DEXAMETHASONE_CAP: Float = OutbreakModelBridge.DEXAMETHASONE_CAP
        @JvmField val DEXAMETHASONE_METABOLISM_TICKS: Int = OutbreakModelBridge.DEXAMETHASONE_METABOLISM_TICKS
        @JvmField val DEXAMETHASONE_DECAY_PER_TICK: Float = OutbreakModelBridge.DEXAMETHASONE_DECAY_PER_TICK

        // ---------------------------------------------------------------- temperature

        /** Core temperature of a healthy player, and the set point thermoregulation defends. */
        @JvmField val TEMPERATURE_NORMAL: Float = OutbreakModelBridge.TEMPERATURE_NORMAL

        /**
         * The comfortable band, just above 36.0 up to just below 38.5. The fever side deliberately
         * starts at 38.5 rather than 38.0: 38 is what a hot biome, a fire or a thyroid that runs hot
         * produces on its own, and the screen effects it switched on looked exactly like a bug.
         */
        @JvmField val COLD_MILD: Float = OutbreakModelBridge.COLD_MILD
        @JvmField val FEVER_MILD: Float = OutbreakModelBridge.FEVER_MILD

        /** Past these the thermal symptoms get worse; 40 is where a fever turns dangerous. */
        @JvmField val COLD_SEVERE: Float = OutbreakModelBridge.COLD_SEVERE
        @JvmField val FEVER_SEVERE: Float = OutbreakModelBridge.FEVER_SEVERE

        /** Hard clamp for the model; 42 is where proteins start to denature. */
        @JvmField val TEMPERATURE_MIN: Float = OutbreakModelBridge.TEMPERATURE_MIN
        @JvmField val TEMPERATURE_MAX: Float = OutbreakModelBridge.TEMPERATURE_MAX

        /** The most pyrogen a body can carry, i.e. the largest fever the test command can induce. */
        @JvmField val PYROGEN_CAP: Float = OutbreakModelBridge.PYROGEN_CAP
        @JvmField val PYROGEN_METABOLISM_TICKS: Int = OutbreakModelBridge.PYROGEN_METABOLISM_TICKS
        @JvmField val PYROGEN_DECAY_PER_TICK: Float = OutbreakModelBridge.PYROGEN_DECAY_PER_TICK

        // ---------------------------------------------------------------- mandrake alkaloids

        /** The most of either tropane alkaloid a body can carry. */
        @JvmField val ANTICHOLINERGIC_CAP: Float = OutbreakModelBridge.ANTICHOLINERGIC_CAP

        /** How far a blurred player can see, in blocks. */
        @JvmField val ANTICHOLINERGIC_BLUR_DISTANCE: Float = OutbreakModelBridge.ANTICHOLINERGIC_BLUR_DISTANCE

        /** Either alkaloid is cleared over one in-game day. */
        @JvmField val ANTICHOLINERGIC_METABOLISM_TICKS: Int = OutbreakModelBridge.ANTICHOLINERGIC_METABOLISM_TICKS

        // ---------------------------------------------------------------- gymnopilus compounds

        /** The most of either of the mushroom's compounds a body can carry. */
        @JvmField val PSILOCYBIN_CAP: Float = OutbreakModelBridge.PSILOCYBIN_CAP
        @JvmField val PSILOCIN_CAP: Float = OutbreakModelBridge.PSILOCIN_CAP

        /** What one raw mushroom carries, which is the unit both metabolism rates are written in. */
        @JvmField val PSILOCIN_DOSE: Float = OutbreakModelBridge.PSILOCIN_DOSE

        /** Psilocybin becomes psilocin over half a game day; psilocin leaves over a whole one. */
        @JvmField val PSILOCYBIN_METABOLISM_TICKS: Int = OutbreakModelBridge.PSILOCYBIN_METABOLISM_TICKS
        @JvmField val PSILOCIN_METABOLISM_TICKS: Int = OutbreakModelBridge.PSILOCIN_METABOLISM_TICKS

        // ---------------------------------------------------------------- ephedrine

        /** The most ephedrine a body can carry, 0..5. */
        @JvmField val EPHEDRINE_CAP: Float = OutbreakModelBridge.EPHEDRINE_CAP
        @JvmField val EPHEDRINE_HASTE_THRESHOLD: Float = OutbreakModelBridge.EPHEDRINE_HASTE_THRESHOLD
        @JvmField val EPHEDRINE_METABOLISM_TICKS: Int = OutbreakModelBridge.EPHEDRINE_METABOLISM_TICKS
        @JvmField val EPHEDRINE_DECAY_PER_TICK: Float = OutbreakModelBridge.EPHEDRINE_DECAY_PER_TICK

        /** What a healthy player looks like. */
        @JvmField val HEALTHY: OutbreakData = OutbreakModelBridge.healthy()

        val CODEC: Codec<OutbreakData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Mediators.CODEC.fieldOf("mediators").forGetter { it.mediators },
                Codec.FLOAT.fieldOf("bacteria").forGetter { it.bacteria },
                Codec.FLOAT.fieldOf("virus").forGetter { it.virus },
                Codec.FLOAT.fieldOf("water").forGetter { it.water },
                Electrolytes.CODEC.fieldOf("electrolytes").forGetter { it.electrolytes },
                TraceElements.CODEC.fieldOf("trace_elements").forGetter { it.traceElements },
                Codec.FLOAT.fieldOf("salicin").forGetter { it.salicin },
                Codec.FLOAT.fieldOf("dexamethasone").forGetter { it.dexamethasone },
                // Optional with a default so that a world saved before temperature existed loads as a
                // healthy player instead of being thrown away.
                Codec.FLOAT.optionalFieldOf("temperature", TEMPERATURE_NORMAL).forGetter { it.temperature },
                Codec.FLOAT.optionalFieldOf("pyrogen", 0f).forGetter { it.pyrogen },
                Codec.FLOAT.optionalFieldOf("scopolamine", 0f).forGetter { it.scopolamine },
                Codec.FLOAT.optionalFieldOf("atropine", 0f).forGetter { it.atropine },
                Codec.FLOAT.optionalFieldOf("psilocybin", 0f).forGetter { it.psilocybin },
                Codec.FLOAT.optionalFieldOf("psilocin", 0f).forGetter { it.psilocin },
                Codec.FLOAT.optionalFieldOf("ephedrine", 0f).forGetter { it.ephedrine },
                Codec.BOOL.optionalFieldOf("immune_active", false).forGetter { it.immuneActive },
            ).apply(instance, ::OutbreakData)
        }
    }
}

/**
 * The bits of the physiology the client needs.
 *
 * Synced rather than simulated: the client only draws the thirst bar and the camera shake, so it is
 * told the water level and the shake counter instead of running the model.
 */
data class OutbreakClientState(
    /** Bumped every time the server wants the camera to shake. */
    val shakeSequence: Int,
    val shakeAmplitude: Float,
    /** Whole water points, for the thirst bar. */
    val water: Int,
    /**
     * True while the mandrake alkaloids have blurred the player's sight. The client turns it into
     * fog out at [OutbreakData.ANTICHOLINERGIC_BLUR_DISTANCE] blocks, which is the one part of the
     * blur the server cannot draw for the player.
     */
    val blurred: Boolean = false,
) {
    companion object {
        val INACTIVE = OutbreakClientState(0, 0f, 0)

        val CODEC: Codec<OutbreakClientState> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("shake_sequence").forGetter { it.shakeSequence },
                Codec.FLOAT.fieldOf("shake_amplitude").forGetter { it.shakeAmplitude },
                Codec.INT.fieldOf("water").forGetter { it.water },
                Codec.BOOL.optionalFieldOf("blurred", false).forGetter { it.blurred },
            ).apply(instance, ::OutbreakClientState)
        }

        val STREAM_CODEC: StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, OutbreakClientState> =
            StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OutbreakClientState::shakeSequence,
                ByteBufCodecs.FLOAT, OutbreakClientState::shakeAmplitude,
                ByteBufCodecs.VAR_INT, OutbreakClientState::water,
                ByteBufCodecs.BOOL, OutbreakClientState::blurred,
                ::OutbreakClientState,
            )
    }
}

/**
 * The attachments every player carries.
 *
 * * `physiology` is the whole body, persisted but deliberately **not** carried across death;
 * * `client_state` is the small subset the client needs, synced to everyone;
 * * `runtime` is per-session counters that are deliberately neither saved nor synced.
 */
object OutbreakAttachments {

    /**
     * The whole body.
     *
     * There is no `copyOnDeath()` on purpose: a respawned player is a new player, with a healthy body
     * and no infection, so death is the one cure that always works. Without that line Fabric starts
     * the new player from [OutbreakData.HEALTHY], which is also what a fresh player gets.
     */
    val DATA: AttachmentType<OutbreakData> = AttachmentRegistry.create(Registration.id("physiology")) { builder ->
        builder
            .initializer { OutbreakData.HEALTHY }
            .persistent(OutbreakData.CODEC)
    }

    /** The bits the client needs, pushed to everyone who can see the player. */
    val CLIENT: AttachmentType<OutbreakClientState> =
        AttachmentRegistry.create(Registration.id("client_state")) { builder ->
            builder
                .initializer { OutbreakClientState.INACTIVE }
                .persistent(OutbreakClientState.CODEC)
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

    /** Ticks until the next "did something next to me pass on a virus?" roll. */
    var contactCooldown: Int = 0

    /** Ticks until the next "am I suppressed enough to catch something random?" roll. */
    var immunosuppressionCooldown: Int = 0

    /** Last water value that was pushed to the client, so the bar is only resent when it moves. */
    var syncedWater: Int = Int.MIN_VALUE

    /** Last shake counter that was pushed to the client. */
    var syncedShake: Int = Int.MIN_VALUE
}
