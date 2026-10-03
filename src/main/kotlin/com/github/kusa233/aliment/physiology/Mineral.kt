package com.github.kusa233.aliment.physiology

/**
 * One mineral the mod tracks, in the unit and with the thresholds a real blood test would report.
 *
 * Sodium, potassium, magnesium, chloride and calcium are serum electrolytes and are tracked in
 * **mmol/L**; iodine is a trace element and is tracked in **umol/L**, a thousandth of a mmol, because
 * its whole physiological range is a fraction of one.
 *
 * The `safeLow`..`safeHigh` band is the reference range used in clinical practice, and there are two
 * thresholds on each side because a deficit and an excess of the same mineral are both illnesses:
 *
 * ```
 *   min    severeLow   safeLow   normal   safeHigh   severeHigh    max
 *   |  severe  |  mild   | healthy  |   mild   |  severe   |
 * ```
 *
 * **Not one of these numbers is written here.** They live in the Scala model, and this enum reads
 * them through [AlimentModelBridge] so that `/aliment status`, the symptom table and the self test
 * can name a mineral without ever naming a Scala type - which is what keeps K2 from dragging
 * `scala.Product` in and misreporting it in the IDE.
 */
enum class Mineral {
    SODIUM,
    POTASSIUM,
    MAGNESIUM,
    CHLORIDE,
    CALCIUM,
    IODINE,
    VITAMIN_C;

    /** The model's own reference range for this mineral. */
    private val spec: AlimentModelBridge.MineralSpec = AlimentModelBridge.spec(this)

    /** The unit a value is measured in: mmol/L for the electrolytes, umol/L for iodine. */
    val unit: String get() = spec.unit

    /** The healthy concentration, and the set point the model regulates towards. */
    val normal: Float get() = spec.normal

    /** Inside this band the mineral causes no symptoms at all. */
    val safeLow: Float get() = spec.safeLow
    val safeHigh: Float get() = spec.safeHigh

    /** Past these the symptoms become serious, and sometimes lethal. */
    val severeLow: Float get() = spec.severeLow
    val severeHigh: Float get() = spec.severeHigh

    /** Hard limits, so a runaway value can never reach nonsense. */
    val min: Float get() = spec.min
    val max: Float get() = spec.max

    /** Clamped to the hard limits. */
    fun clamp(value: Float): Float = spec.clamp(value)

    /** Distance outside the reference range, in this mineral's own units; 0 while inside it. */
    fun deviation(value: Float): Float = spec.deviation(value)

    /** The same distance as a fraction of normal, so minerals can be ranked against each other. */
    fun relativeDeviation(value: Float): Float = spec.relativeDeviation(value)

    /** -1 for a deficit, 1 for an excess, 0 while the value is inside the reference range. */
    fun direction(value: Float): Int = spec.direction(value)

    /** True past [severeLow] or [severeHigh]. */
    fun isSevere(value: Float): Boolean = spec.isSevere(value)

    /** The value as `/aliment status` prints it: iodine needs more decimals than sodium. */
    fun display(value: Float): String = spec.display(value)
}
