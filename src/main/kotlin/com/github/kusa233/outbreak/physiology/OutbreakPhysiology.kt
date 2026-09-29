package com.github.kusa233.outbreak.physiology

import kotlin.math.abs
import kotlin.math.exp

/**
 * The per-tick model behind [OutbreakData].
 *
 * Every number below is expressed per tick. The design goals, in the words of the feature spec:
 *
 * * a healthy player drifts back to an inflammation of roughly 20..30;
 * * too little inflammation means the infection runs away, too much means an immune storm that
 *   hurts the host without clearing the pathogen;
 * * salicin and dexamethasone control the infection by damping the mediator response, while an
 *   overdose pushes inflammation below the safe band and therefore makes the infection worse;
 * * a full bladder drains to one cell over one in-game day;
 * * drinking far too much water dilutes and flushes the electrolytes;
 * * every mineral is regulated towards [MineralScale.NORMAL], so a player who ignores kelp and
 *   salt settles at a steady state rather than drifting off to an extreme;
 * * core temperature is driven by prostaglandin (PGE2 is the fever mediator), by the environment
 *   the player is standing in, and by the thyroid.
 */
object OutbreakPhysiology {

    // ------------------------------------------------------------------ mediators

    /**
     * Resting mediator levels. Together with the feedback from cytokine into the prostaglandin and
     * leukotriene pathways they sum, through the weights in [Mediators], to a baseline
     * inflammation of exactly 25 - the middle of the safe band. `OutbreakData.HEALTHY` has to
     * start from these values or a fresh player would begin life immunosuppressed.
     */
    private const val HISTAMINE_BASE = 25f
    private const val PROSTAGLANDIN_BASE = 20f
    private const val LEUKOTRIENE_BASE = 20f
    private const val CYTOKINE_BASE = 20f
    private const val BRADYKININ_BASE = 25f

    /** Pathogen-driven response, at a full load, before any drug damping. */
    private const val CYTOKINE_RESPONSE = 100f
    private const val HISTAMINE_RESPONSE = 45f
    private const val BRADYKININ_RESPONSE = 60f
    private const val PROSTAGLANDIN_RESPONSE = 25f
    private const val LEUKOTRIENE_RESPONSE = 20f

    /** How much of the prostaglandin drive comes from induced COX-2 rather than the pathogen. */
    private const val PROSTAGLANDIN_FROM_CYTOKINE = 0.5f
    private const val LEUKOTRIENE_FROM_CYTOKINE = 0.5f

    /** How quickly a mediator moves towards its target. 1/0.002 = 500 ticks, about 25 seconds. */
    private const val MEDIATOR_APPROACH = 0.002f

    /** Drugs cut the whole pathogen-driven response by this fraction at full effect. */
    private const val DRUG_RESPONSE_DAMPING = 0.88f

    /** Extra, mediator-specific effects on top of the general damping. */
    private const val DEX_CYTOKINE_EXTRA = 0.4f
    private const val SALICIN_PROSTAGLANDIN_EXTRA = 0.5f
    private const val DEX_LEUKOTRIENE_EXTRA = 0.4f

    /**
     * A dose above the effective concentration scales the *resting* mediator levels down, which is
     * what turns an overdose into immunosuppression. At the drug cap this drives the resting
     * inflammation from 25 down to single digits, which is low enough for the competence curve to
     * collapse and the infection to run.
     */
    private const val OVERDOSE_BASELINE_DROP = 1.8f

    /** Drugs can overshoot their effective concentration by this factor. */
    private const val DRUG_MAX_SUPPRESSION = 1.5f

    // ------------------------------------------------------------------ pathogens

    /**
     * Logistic growth rate once a pathogen is present. Tuned so that an untreated infection plays
     * out over in-game minutes, not seconds.
     */
    private const val GROWTH_RATE = 0.0004f

    /**
     * Clearance rate at full immune competence. At competence 1 this beats [GROWTH_RATE] at every
     * load, so a healthy player always wins; the infection only takes hold once inflammation has
     * been pushed out of the safe band.
     */
    private const val CLEARANCE_RATE = 0.0007f

    private const val COMPETENCE_SIGMA = 15f

    // ------------------------------------------------------------------ water

    /** Extra water loss per point above the normal band, on top of the base loss. */
    private const val OVERHYDRATION_DIURESIS = 0.01f

    /** How fast excess water flushes each electrolyte out, per point above the normal band. */
    private const val WATER_FLUSH_RATE = 0.00004f

    /** Sweat: water lost per tick per degree of core temperature above normal. */
    private const val SWEAT_WATER_PER_DEGREE = 0.0015f

    /** Sweat also carries salt away, which is how a long fever ends in hyponatraemia. */
    private const val SWEAT_MINERAL_PER_DEGREE = 0.0002f

    /** The body slowly restores electrolytes on its own. 1/0.00005 = 20000 ticks, under a day. */
    private const val ELECTROLYTE_HOMEOSTASIS = 0.00005f

    /** Relative urinary loss per electrolyte; sodium and chloride go first. */
    private const val EXCRETION_SODIUM = 1.0f
    private const val EXCRETION_CHLORIDE = 1.0f
    private const val EXCRETION_POTASSIUM = 0.7f
    private const val EXCRETION_MAGNESIUM = 0.4f
    private const val EXCRETION_CALCIUM = 0.4f
    private const val EXCRETION_IODINE = 0.3f

    // ------------------------------------------------------------------ iodine

    /**
     * Iodine is the one mineral the diet has to supply, so it is regulated from the other end: a
     * deficit is met by hanging on to what is left rather than by making more.
     *
     * The two terms below are a proportional controller plus a constant leak, which gives a fixed
     * point of `NORMAL - IODINE_DRAIN / IODINE_HOMEOSTASIS`, i.e. **88** - inside the safe band,
     * but only three points from the bottom of it. Nobody who eats kelp now and then will ever see
     * a symptom, and nobody who never does will spiral: they just sit at 88. One kelp takes that to
     * 113, still in band; dried kelp overshoots. A fever or a drinking binge is what pushes it under.
     *
     * The time constant is `1 / IODINE_HOMEOSTASIS` = 20000 ticks, the same as the electrolytes.
     */
    private const val IODINE_HOMEOSTASIS = 0.00005f
    private const val IODINE_DRAIN = 0.0006f

    // ------------------------------------------------------------------ temperature

    /**
     * Fever per point of prostaglandin above the resting level. PGE2 in the hypothalamus is what
     * actually raises the set point, which is why salicin - a COX inhibitor - is an antipyretic
     * here and why a cytokine storm produces a high fever.
     */
    private const val FEVER_PER_PROSTAGLANDIN = 0.05f

    /** No infection can push the set point further than this on its own. */
    private const val FEVER_MAX = 4.0f

    /** 1/0.0004 = 2500 ticks, a little over two in-game minutes, to close most of the gap. */
    private const val TEMPERATURE_APPROACH = 0.0004f

    /**
     * How close to its set point the body has to get before an injected pyrogen starts to clear.
     *
     * An injected pyrogen *is* the set point, so if it cleared from the moment it was injected the
     * set point would fall away underneath a body that was still climbing towards it, and the fever
     * would peak far short of the temperature that was asked for - asking for 39.5 used to peak at
     * 38.8. Holding it until the fever has actually developed makes the peak exact, and costs
     * nothing but a few minutes before the load starts to clear.
     */
    private const val PYROGEN_SETTLED = 0.2f

    /**
     * How much of the environment's pull the body cannot compensate for. Thermoregulation is good
     * but not perfect: a player in freezing water eventually loses the fight.
     */
    private const val AMBIENT_COUPLING = 0.6f

    /**
     * The thyroid sets the metabolic rate, so iodine moves the set point: too little and the
     * player cannot stay warm (cold intolerance), too much and they run hot.
     */
    private const val THYROID_SHIFT_PER_POINT = 0.02f
    private const val THYROID_SHIFT_MAX = 0.8f

    /**
     * How well the current inflammation level actually fights pathogens, in `0..1`.
     *
     * A bell curve: the immune system works best inside the safe band. Below the immunosuppression
     * threshold it gives up entirely, and during a storm it is too dysregulated to be useful, so
     * both extremes let the infection grow.
     */
    fun immuneCompetence(inflammation: Float): Float {
        val delta = inflammation - OutbreakData.BASELINE_INFLAMMATION
        val bell = exp(-(delta * delta) / (2f * COMPETENCE_SIGMA * COMPETENCE_SIGMA))
        val lowEnd = (inflammation / OutbreakData.IMMUNOSUPPRESSION_THRESHOLD).coerceIn(0f, 1f)
        return bell * lowEnd
    }

    /** Drug concentration as a multiple of its effective concentration, capped. */
    fun suppression(concentration: Float, effective: Float): Float =
        (concentration / effective).coerceIn(0f, DRUG_MAX_SUPPRESSION)

    /**
     * Advances a player's physiology by a single tick.
     *
     * [ambient] is the temperature the environment is trying to drag the body towards, on the same
     * Celsius scale as the body itself; [OutbreakData.TEMPERATURE_NORMAL] means "indoors, no wind,
     * nothing to fight". `OutbreakSymptoms` computes it from the biome and the weather, and it
     * defaults to neutral so the model can be driven from a test without a world.
     *
     * Deliberately free of any Minecraft objects beyond the data itself: the whole model can be
     * driven from a test or a debug command without a player.
     */
    fun tick(data: OutbreakData, ambient: Float = OutbreakData.TEMPERATURE_NORMAL): OutbreakData {
        // 1. Drugs and injected pyrogen are metabolised first so the rest of the tick sees the
        //    current concentrations. A negative pyrogen is an antipyretic offset and clears the
        //    same way a positive one does.
        val salicin = (data.salicin - OutbreakData.SALICIN_DECAY_PER_TICK).coerceAtLeast(0f)
        val dexamethasone = (data.dexamethasone - OutbreakData.DEXAMETHASONE_DECAY_PER_TICK).coerceAtLeast(0f)
        val pyrogen = stepPyrogen(data, ambient)

        // 2. Pathogens grow logistically and are cleared in proportion to immune competence.
        val competence = immuneCompetence(data.inflammation)
        val bacteria = stepPathogen(data.bacteria, competence)
        val virus = stepPathogen(data.virus, competence)

        val next = data.copy(
            salicin = salicin,
            dexamethasone = dexamethasone,
            pyrogen = pyrogen,
            bacteria = bacteria,
            virus = virus,
        )

        // 3. The immune response, mediator by mediator.
        val mediators = stepMediators(next)
        val inflamed = next.copy(mediators = mediators)

        // 4. Temperature follows the mediators and the environment. Everything the body *does* about
        //    a temperature - sweating water and salt away - is driven by the temperature it started
        //    the tick at, so that each step of the model reads the previous state and the new
        //    temperature is written once, at the end.
        val temperature = stepTemperature(inflamed, ambient)

        val water = stepWater(inflamed)
        val hydrated = inflamed.copy(water = water)

        return hydrated.copy(
            temperature = temperature,
            electrolytes = stepElectrolytes(hydrated),
            traceElements = stepTraceElements(hydrated),
        )
    }

    // ------------------------------------------------------------------ mediators

    private fun stepMediators(data: OutbreakData): Mediators {
        val stimulus = (data.pathogenLoad / OutbreakData.MAX_PATHOGEN).coerceIn(0f, 1f)

        val salicin = suppression(data.salicin, OutbreakData.SALICIN_EFFECTIVE)
        val dex = suppression(data.dexamethasone, OutbreakData.DEXAMETHASONE_EFFECTIVE)
        val salFight = salicin.coerceAtMost(1f)
        val dexFight = dex.coerceAtMost(1f)

        // A combination of drugs damps the response more than either alone.
        val fight = maxOf(salFight, dexFight)
        val damping = (1f - DRUG_RESPONSE_DAMPING * fight).coerceAtLeast(0.05f)

        // Anything above the effective concentration also suppresses the resting level.
        val overdose = (salicin - 1f).coerceAtLeast(0f) + (dex - 1f).coerceAtLeast(0f)
        val baselineScale = (1f - overdose * OVERDOSE_BASELINE_DROP).coerceAtLeast(0f)

        val cytokine = approach(
            data.mediators.cytokine,
            CYTOKINE_BASE * baselineScale + CYTOKINE_RESPONSE * stimulus * damping * (1f - DEX_CYTOKINE_EXTRA * dexFight),
        )
        val histamine = approach(
            data.mediators.histamine,
            HISTAMINE_BASE * baselineScale + HISTAMINE_RESPONSE * stimulus * damping,
        )
        val bradykinin = approach(
            data.mediators.bradykinin,
            BRADYKININ_BASE * baselineScale + BRADYKININ_RESPONSE * stimulus * damping,
        )
        val prostaglandin = approach(
            data.mediators.prostaglandin,
            PROSTAGLANDIN_BASE * baselineScale +
                (cytokine * PROSTAGLANDIN_FROM_CYTOKINE + PROSTAGLANDIN_RESPONSE * stimulus) *
                damping * (1f - SALICIN_PROSTAGLANDIN_EXTRA * salFight),
        )
        val leukotriene = approach(
            data.mediators.leukotriene,
            LEUKOTRIENE_BASE * baselineScale +
                (cytokine * LEUKOTRIENE_FROM_CYTOKINE + LEUKOTRIENE_RESPONSE * stimulus) *
                damping * (1f - DEX_LEUKOTRIENE_EXTRA * dexFight),
        )

        return Mediators(histamine, prostaglandin, leukotriene, cytokine, bradykinin)
    }

    private fun approach(current: Float, target: Float): Float =
        (current + (target - current) * MEDIATOR_APPROACH).coerceIn(0f, Mediators.MAX)

    // ------------------------------------------------------------------ temperature

    /**
     * The core temperature the body is currently aiming for.
     *
     * Four things move it, and they are all separate on purpose:
     *
     * * **prostaglandin** - PGE2 in the hypothalamus is the fever mediator, so this is the only
     *   term an infection creates, and the one salicin (a COX inhibitor) takes away;
     * * **pyrogen** - an injected fever, the test command's lever, stored like a drug;
     * * **the thyroid** - iodine moves the set point, which is why hypothyroidism means always
     *   feeling cold and thyrotoxicosis means always feeling hot;
     * * **the environment** - the biome, rain and what the player is standing in, of which only
     *   [AMBIENT_COUPLING] gets past thermoregulation.
     */
    fun targetTemperature(data: OutbreakData, ambient: Float = OutbreakData.TEMPERATURE_NORMAL): Float {
        val prostaglandin = (data.mediators.prostaglandin - Mediators.RESTING.prostaglandin).coerceAtLeast(0f)
        val fever = (FEVER_PER_PROSTAGLANDIN * prostaglandin).coerceAtMost(FEVER_MAX)
        val environmental = (ambient - OutbreakData.TEMPERATURE_NORMAL) * AMBIENT_COUPLING
        val target = OutbreakData.TEMPERATURE_NORMAL +
            fever + data.pyrogen + thyroidShift(data.traceElements.iodine) + environmental
        return target.coerceIn(OutbreakData.TEMPERATURE_MIN, OutbreakData.TEMPERATURE_MAX)
    }

    private fun stepTemperature(data: OutbreakData, ambient: Float): Float {
        val target = targetTemperature(data, ambient)
        return (data.temperature + (target - data.temperature) * TEMPERATURE_APPROACH)
            .coerceIn(OutbreakData.TEMPERATURE_MIN, OutbreakData.TEMPERATURE_MAX)
    }

    /** How much the thyroid moves the set point, in degrees. 0 while iodine is in the safe band. */
    private fun thyroidShift(iodine: Float): Float {
        val delta = when {
            iodine < MineralScale.SAFE_LOW -> iodine - MineralScale.SAFE_LOW
            iodine > MineralScale.SAFE_HIGH -> iodine - MineralScale.SAFE_HIGH
            else -> 0f
        }
        return (delta * THYROID_SHIFT_PER_POINT).coerceIn(-THYROID_SHIFT_MAX, THYROID_SHIFT_MAX)
    }

    /** Degrees of core temperature above normal; the drive behind sweating. 0 when not hot. */
    private fun heatStress(data: OutbreakData): Float =
        (data.temperature - OutbreakData.TEMPERATURE_NORMAL).coerceAtLeast(0f)

    /**
     * Clears an injected pyrogen, one step per tick - but only once the fever it drives has
     * actually developed. See [PYROGEN_SETTLED] for why it waits.
     *
     * An infection never goes through here: its fever comes from prostaglandin, so `pyrogen` is
     * zero and this returns immediately.
     */
    private fun stepPyrogen(data: OutbreakData, ambient: Float): Float {
        val pyrogen = data.pyrogen
        if (pyrogen == 0f) {
            return 0f
        }
        if (abs(data.temperature - targetTemperature(data, ambient)) >= PYROGEN_SETTLED) {
            return pyrogen
        }
        return if (pyrogen > 0f) {
            (pyrogen - OutbreakData.PYROGEN_DECAY_PER_TICK).coerceAtLeast(0f)
        } else {
            (pyrogen + OutbreakData.PYROGEN_DECAY_PER_TICK).coerceAtMost(0f)
        }
    }

    // ------------------------------------------------------------------ pathogens

    private fun stepPathogen(load: Float, competence: Float): Float {
        if (load <= 0f) {
            return 0f
        }
        val growth = GROWTH_RATE * load * (1f - load / OutbreakData.MAX_PATHOGEN)
        val clearance = CLEARANCE_RATE * competence * load
        return (load + growth - clearance).coerceIn(0f, OutbreakData.MAX_PATHOGEN)
    }

    // ------------------------------------------------------------------ water

    private fun stepWater(data: OutbreakData): Float {
        val excess = (data.water - OutbreakData.WATER_NORMAL).coerceAtLeast(0f)

        var loss = OutbreakData.WATER_DECAY_PER_TICK
        // The kidneys dump a water load: up to twice the base rate at the ceiling.
        loss *= 1f + excess * OVERHYDRATION_DIURESIS

        // Hypernatraemia and hypercalcaemia both cause thirst and increased urine output.
        if (data.electrolytes.sodium > Electrolytes.SAFE_HIGH) {
            loss *= 1.3f
        }
        if (data.electrolytes.calcium > Electrolytes.SAFE_HIGH) {
            loss *= 1.2f
        }

        // A fever is a furnace: every degree above normal is paid for in sweat.
        loss += heatStress(data) * SWEAT_WATER_PER_DEGREE

        return (data.water - loss).coerceIn(OutbreakData.WATER_MIN, OutbreakData.WATER_MAX)
    }

    // ------------------------------------------------------------------ electrolytes

    private fun stepElectrolytes(data: OutbreakData): Electrolytes {
        val excess = (data.water - OutbreakData.WATER_NORMAL).coerceAtLeast(0f)
        val flush = excess * WATER_FLUSH_RATE
        val sweat = heatStress(data) * SWEAT_MINERAL_PER_DEGREE
        val current = data.electrolytes

        fun step(value: Float, excretion: Float): Float {
            val loss = (flush + sweat) * excretion
            val homeostasis = (Electrolytes.NORMAL - value) * ELECTROLYTE_HOMEOSTASIS
            return (value + homeostasis - loss).coerceIn(Electrolytes.MIN, Electrolytes.MAX)
        }

        return Electrolytes(
            sodium = step(current.sodium, EXCRETION_SODIUM),
            potassium = step(current.potassium, EXCRETION_POTASSIUM),
            magnesium = step(current.magnesium, EXCRETION_MAGNESIUM),
            chloride = step(current.chloride, EXCRETION_CHLORIDE),
            calcium = step(current.calcium, EXCRETION_CALCIUM),
        )
    }

    // ------------------------------------------------------------------ trace elements

    /**
     * Iodine is pulled towards normal like everything else, but with a leak on top
     * ([IODINE_DRAIN]) so the equilibrium sits a few points low. Kelp is what lifts it back.
     */
    private fun stepTraceElements(data: OutbreakData): TraceElements {
        val excess = (data.water - OutbreakData.WATER_NORMAL).coerceAtLeast(0f)
        val flush = excess * WATER_FLUSH_RATE
        val sweat = heatStress(data) * SWEAT_MINERAL_PER_DEGREE
        val iodine = data.traceElements.iodine

        val homeostasis = (MineralScale.NORMAL - iodine) * IODINE_HOMEOSTASIS
        val loss = IODINE_DRAIN + (flush + sweat) * EXCRETION_IODINE

        return TraceElements(
            iodine = (iodine + homeostasis - loss).coerceIn(MineralScale.MIN, MineralScale.MAX),
        )
    }

    // ------------------------------------------------------------------ external inputs

    /** Adds a pathogen seed, used by the infection sources. */
    fun seed(data: OutbreakData, bacteria: Float = 0f, virus: Float = 0f): OutbreakData = data.copy(
        bacteria = (data.bacteria + bacteria).coerceIn(0f, OutbreakData.MAX_PATHOGEN),
        virus = (data.virus + virus).coerceIn(0f, OutbreakData.MAX_PATHOGEN),
    )

    /** Adds salicin, capped at [OutbreakData.SALICIN_CAP]. */
    fun dose(data: OutbreakData, amount: Float): OutbreakData =
        data.copy(salicin = (data.salicin + amount).coerceIn(0f, OutbreakData.SALICIN_CAP))

    /** Adds dexamethasone, capped at [OutbreakData.DEXAMETHASONE_CAP]. */
    fun inject(data: OutbreakData, amount: Float): OutbreakData =
        data.copy(dexamethasone = (data.dexamethasone + amount).coerceIn(0f, OutbreakData.DEXAMETHASONE_CAP))

    /** Adds water from a drink. */
    fun drink(data: OutbreakData, amount: Float = OutbreakData.WATER_PER_DRINK): OutbreakData =
        data.copy(water = (data.water + amount).coerceIn(OutbreakData.WATER_MIN, OutbreakData.WATER_MAX))

    /**
     * Adds salt. [crude] salt carries the other minerals that rock salt contains, refined salt is
     * almost pure sodium chloride.
     */
    fun salt(data: OutbreakData, sodium: Float, chloride: Float, magnesium: Float = 0f, calcium: Float = 0f): OutbreakData {
        val e = data.electrolytes
        return data.copy(
            electrolytes = e.copy(
                sodium = (e.sodium + sodium).coerceIn(Electrolytes.MIN, Electrolytes.MAX),
                magnesium = (e.magnesium + magnesium).coerceIn(Electrolytes.MIN, Electrolytes.MAX),
                chloride = (e.chloride + chloride).coerceIn(Electrolytes.MIN, Electrolytes.MAX),
                calcium = (e.calcium + calcium).coerceIn(Electrolytes.MIN, Electrolytes.MAX),
            ),
        )
    }

    /** Adds iodine, which the body only gets from food - kelp, in this mod. */
    fun iodine(data: OutbreakData, amount: Float): OutbreakData {
        val t = data.traceElements
        return data.copy(
            traceElements = t.copy(iodine = (t.iodine + amount).coerceIn(MineralScale.MIN, MineralScale.MAX)),
        )
    }

    /**
     * Raises or lowers the fever so that the body *peaks* at [degrees] Celsius.
     *
     * This works out how much pyrogen is needed given whatever the infection is already doing, so
     * the answer is the requested temperature whether or not the player is ill. Passing something
     * below [OutbreakData.TEMPERATURE_NORMAL] gives a negative offset, which is how the test
     * command produces hypothermia as well as a fever.
     *
     * The extra [PYROGEN_SETTLED] pays for the same tolerance that [stepPyrogen] waits for, so the
     * peak lands on the requested temperature exactly rather than a fraction under it. The request
     * is clamped to what a pyrogen can actually do ([OutbreakData.PYROGEN_CAP] degrees either way
     * of the patient's own set point).
     *
     * The result is a *target*, not an instant change: the core temperature walks towards it over
     * a couple of in-game minutes.
     */
    fun induceFever(
        data: OutbreakData,
        degrees: Float,
        ambient: Float = OutbreakData.TEMPERATURE_NORMAL,
    ): OutbreakData {
        val withoutPyrogen = targetTemperature(data.copy(pyrogen = 0f), ambient)
        val naive = degrees - withoutPyrogen
        val needed = (if (naive >= 0f) naive + PYROGEN_SETTLED else naive - PYROGEN_SETTLED)
            .coerceIn(-OutbreakData.PYROGEN_CAP, OutbreakData.PYROGEN_CAP)
        return data.copy(pyrogen = needed)
    }
}
