package com.github.kusa233.outbreak.physiology

import kotlin.math.exp

/**
 * The per-tick model behind [OutbreakData].
 *
 * Every number below is expressed per tick. The design goals, in the words of the feature spec:
 *
 * * a healthy player drifts back to an inflammation of roughly 20..30;
 * * too little inflammation means the infection runs away, too much means an immune storm that
 *   hurts the host without clearing the pathogen;
 * * salicin at its effective concentration neutralises the pathogen response, which pulls
 *   inflammation back into the safe band, while an overdose pushes it *below* the band and
 *   therefore makes the infection worse.
 */
object OutbreakPhysiology {

    // ------------------------------------------------------------------ inflammation response

    /** Extra inflammation a fully developed infection provokes, on top of the baseline. */
    private const val RESPONSE_GAIN = 80f

    /**
     * How quickly inflammation moves towards its target: `1/0.002 = 500` ticks, about 25 seconds.
     *
     * This has to be comfortably faster than the pathogen dynamics below, otherwise a load decays
     * before the immune response has time to overshoot into a storm and no infection could ever
     * take hold.
     */
    private const val INFLAMMATION_APPROACH = 0.002f

    /** How far one "unit" of salicin overdose pushes inflammation below the baseline. */
    private const val OVERDOSE_DROP = 40f

    /**
     * Salicin fully suppresses the pathogen response at [OutbreakData.SALICIN_EFFECTIVE], and can
     * overshoot up to this factor. Everything above 1 is an overdose.
     */
    private const val SALICIN_MAX_SUPPRESSION = 1.5f

    // ------------------------------------------------------------------ immune competence

    /** Standard deviation of the competence bell curve around [OutbreakData.BASELINE_INFLAMMATION]. */
    private const val COMPETENCE_SIGMA = 15f

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

    // ------------------------------------------------------------------ electrolytes

    /** Electrolytes lost per tick at maximum inflammation. */
    private const val ELECTROLYTE_DRAIN = 0.0009f

    /** Electrolytes regained per tick while the player is not ill. */
    private const val ELECTROLYTE_RECOVERY = 0.004f

    /**
     * How well the current inflammation level actually fights pathogens, in `0..1`.
     *
     * A bell curve: the immune system works best inside the safe band. Below the immunosuppression
     * threshold it gives up entirely, and during a storm it is too dysregulated to be useful, so
     * both extremes let the infection grow.
     */
    fun immuneCompetence(inflammation: Float): Float {
        val bell = exp(-((inflammation - OutbreakData.BASELINE_INFLAMMATION) * (inflammation - OutbreakData.BASELINE_INFLAMMATION)) / (2f * COMPETENCE_SIGMA * COMPETENCE_SIGMA))
        val lowEnd = (inflammation / OutbreakData.IMMUNOSUPPRESSION_THRESHOLD).coerceIn(0f, 1f)
        return bell * lowEnd
    }

    /**
     * Advances a player's physiology by a single tick.
     *
     * Deliberately free of any Minecraft objects beyond the data itself: the whole model can be
     * driven from a test or a debug command without a player.
     */
    fun tick(data: OutbreakData): OutbreakData {
        // 1. The drug is metabolised first so the rest of the tick sees the current concentration.
        val salicin = (data.salicin - OutbreakData.SALICIN_DECAY_PER_TICK).coerceAtLeast(0f)

        // 2. Pathogens grow logistically and are cleared in proportion to immune competence.
        val competence = immuneCompetence(data.inflammation)
        val bacteria = stepPathogen(data.bacteria, competence)
        val virus = stepPathogen(data.virus, competence)

        // 3. Inflammation chases the response provoked by the pathogen load, damped by salicin.
        val suppression = (salicin / OutbreakData.SALICIN_EFFECTIVE).coerceIn(0f, SALICIN_MAX_SUPPRESSION)
        val response = (bacteria + virus).coerceAtMost(OutbreakData.MAX_PATHOGEN) / OutbreakData.MAX_PATHOGEN * RESPONSE_GAIN
        val damped = response * (1f - suppression.coerceAtMost(1f))
        val overdose = (suppression - 1f).coerceAtLeast(0f)
        val target = OutbreakData.BASELINE_INFLAMMATION + damped - overdose * OVERDOSE_DROP
        val inflammation = (data.inflammation + (target - data.inflammation) * INFLAMMATION_APPROACH)
            .coerceIn(OutbreakData.MIN_INFLAMMATION, OutbreakData.MAX_INFLAMMATION)

        // 4. Fever burns electrolytes; a healthy player gets them back.
        val symptomatic = (bacteria + virus) >= OutbreakData.SYMPTOM_THRESHOLD
        val drain = inflammation / OutbreakData.MAX_INFLAMMATION * ELECTROLYTE_DRAIN * (if (symptomatic) 1.5f else 1f)
        val recovery = if (!symptomatic && inflammation < 40f) ELECTROLYTE_RECOVERY else 0f
        val electrolytes = (data.electrolytes + recovery - drain)
            .coerceIn(OutbreakData.MIN_ELECTROLYTES, OutbreakData.MAX_ELECTROLYTES)

        return OutbreakData(
            inflammation = inflammation,
            electrolytes = electrolytes,
            bacteria = bacteria,
            virus = virus,
            salicin = salicin,
        )
    }

    private fun stepPathogen(load: Float, competence: Float): Float {
        if (load <= 0f) {
            return 0f
        }
        val growth = GROWTH_RATE * load * (1f - load / OutbreakData.MAX_PATHOGEN)
        val clearance = CLEARANCE_RATE * competence * load
        return (load + growth - clearance).coerceIn(0f, OutbreakData.MAX_PATHOGEN)
    }

    /** Adds a pathogen seed, used by the infection sources. */
    fun seed(data: OutbreakData, bacteria: Float = 0f, virus: Float = 0f): OutbreakData = data.copy(
        bacteria = (data.bacteria + bacteria).coerceIn(0f, OutbreakData.MAX_PATHOGEN),
        virus = (data.virus + virus).coerceIn(0f, OutbreakData.MAX_PATHOGEN),
    )

    /** Adds salicin, capped at [OutbreakData.SALICIN_CAP]. */
    fun dose(data: OutbreakData, amount: Float): OutbreakData =
        data.copy(salicin = (data.salicin + amount).coerceIn(0f, OutbreakData.SALICIN_CAP))
}
