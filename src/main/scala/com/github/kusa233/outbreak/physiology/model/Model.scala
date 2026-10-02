package com.github.kusa233.outbreak.physiology.model

import scala.beans.BeanProperty

/**
 * One mineral Outbreak tracks, with the unit and the thresholds a real blood test would report.
 *
 * Sodium, potassium, magnesium, chloride and calcium are serum electrolytes and are tracked in
 * **mmol/L**; iodine is a trace element and is tracked in **umol/L**, a thousandth of a mmol,
 * because its whole physiological range is a fraction of one - in mmol/L it would only ever print
 * as "0.0005".
 *
 * The `safeLow`..`safeHigh` band is the reference range used in clinical practice, and there are two
 * thresholds on each side because a deficit and an excess of the same mineral are both illnesses:
 *
 * {{{
 *   min    severeLow   safeLow   normal   safeHigh   severeHigh    max
 *   |  severe  |  mild   | healthy  |   mild   |  severe   |
 * }}}
 *
 * The named instances live in [[MineralRanges]] rather than in a companion object, because a
 * companion would have to be called `ModelMineral` too and the whole point of the `Model` names is
 * that Kotlin's storage types (`Mineral`, `Mediators`, ...) and the model's own types stay apart.
 */
final case class ModelMineral(
    @BeanProperty name: String,
    @BeanProperty unit: String,
    @BeanProperty normal: Float,
    @BeanProperty safeLow: Float,
    @BeanProperty safeHigh: Float,
    @BeanProperty severeLow: Float,
    @BeanProperty severeHigh: Float,
    @BeanProperty min: Float,
    @BeanProperty max: Float,
) {

  /** Clamped to the hard limits, so a runaway value can never reach nonsense. */
  def clamp(value: Float): Float =
    if (value < this.min) this.min else if (value > this.max) this.max else value

  /** Distance outside the reference range, in this mineral's own units; 0 while inside it. */
  def deviation(value: Float): Float =
    if (value < this.safeLow) this.safeLow - value
    else if (value > this.safeHigh) value - this.safeHigh
    else 0f

  /**
   * How far outside the reference range `value` is, as a fraction of `normal`.
   *
   * Deviations cannot be compared across minerals in their own units - 0.2 is nothing to sodium and
   * a catastrophe for iodine - so anything that has to rank or combine them uses this.
   */
  def relativeDeviation(value: Float): Float = this.deviation(value) / this.normal

  /** -1 for a deficit, 1 for an excess, 0 while `value` is inside the reference range. */
  def direction(value: Float): Int =
    if (value < this.safeLow) -1 else if (value > this.safeHigh) 1 else 0

  /** True past `severeLow` or `severeHigh`. */
  def isSevere(value: Float): Boolean = value < this.severeLow || value > this.severeHigh

  /** The value as `/outbreak status` prints it: iodine needs more decimals than sodium. */
  def display(value: Float): String =
    String.format("%." + (if (this.normal < 1f) 2 else 1) + "f", java.lang.Float.valueOf(value))
}

/** The minerals the model knows, and the reference range of each. */
object MineralRanges {

  /** 135-145 mmol/L, the clinical reference range. */
  val SODIUM: ModelMineral = new ModelMineral("SODIUM", "mmol/L", 140f, 135f, 145f, 125f, 150f, 100f, 190f)

  /** 3.5-5.0 mmol/L. */
  val POTASSIUM: ModelMineral = new ModelMineral("POTASSIUM", "mmol/L", 4.2f, 3.5f, 5f, 3f, 6f, 1.5f, 9f)

  /** 0.70-1.00 mmol/L. */
  val MAGNESIUM: ModelMineral = new ModelMineral("MAGNESIUM", "mmol/L", 0.85f, 0.7f, 1f, 0.5f, 1.5f, 0.2f, 3f)

  /** 96-106 mmol/L. */
  val CHLORIDE: ModelMineral = new ModelMineral("CHLORIDE", "mmol/L", 101f, 96f, 106f, 90f, 115f, 70f, 140f)

  /** 2.10-2.60 mmol/L. */
  val CALCIUM: ModelMineral = new ModelMineral("CALCIUM", "mmol/L", 2.35f, 2.1f, 2.6f, 1.8f, 3f, 1f, 4f)

  /** 0.40-0.80 umol/L of serum iodine. */
  val IODINE: ModelMineral = new ModelMineral("IODINE", "umol/L", 0.5f, 0.4f, 0.8f, 0.2f, 1.2f, 0.05f, 2f)
}

/**
 * The inflammatory mediators the immune response is broken down into.
 *
 * The single "inflammation" number the system used to carry is derived from these, and drugs act on
 * individual mediators rather than on inflammation as a whole - which is what makes salicin and
 * dexamethasone behave differently.
 *
 * The weights and the named sets live in [[MediatorLevels]], for the same reason
 * [[ModelMineral]]'s do.
 */
final case class ModelMediators(
    @BeanProperty histamine: Float,
    @BeanProperty prostaglandin: Float,
    @BeanProperty leukotriene: Float,
    @BeanProperty cytokine: Float,
    @BeanProperty bradykinin: Float,
) {

  /** Inflammation index, 0..100, as a weighted sum of the mediators. */
  def getInflammation: Float = {
    val sum = MediatorLevels.HISTAMINE_WEIGHT * this.histamine +
      MediatorLevels.PROSTAGLANDIN_WEIGHT * this.prostaglandin +
      MediatorLevels.LEUKOTRIENE_WEIGHT * this.leukotriene +
      MediatorLevels.CYTOKINE_WEIGHT * this.cytokine +
      MediatorLevels.BRADYKININ_WEIGHT * this.bradykinin
    if (sum < 0f) 0f else if (sum > MediatorLevels.MAX) MediatorLevels.MAX else sum
  }

  def withHistamine(value: Float): ModelMediators = copy(histamine = value)
  def withProstaglandin(value: Float): ModelMediators = copy(prostaglandin = value)
  def withLeukotriene(value: Float): ModelMediators = copy(leukotriene = value)
  def withCytokine(value: Float): ModelMediators = copy(cytokine = value)
  def withBradykinin(value: Float): ModelMediators = copy(bradykinin = value)
}

/** The weights of the inflammation index, and the mediator sets the model is stated against. */
object MediatorLevels {

  val MAX: Float = 100f

  val HISTAMINE_WEIGHT: Float = 0.15f
  val PROSTAGLANDIN_WEIGHT: Float = 0.20f
  val LEUKOTRIENE_WEIGHT: Float = 0.15f

  /** Cytokines are the systemic driver, so they dominate the index. */
  val CYTOKINE_WEIGHT: Float = 0.35f
  val BRADYKININ_WEIGHT: Float = 0.15f

  val CALM: ModelMediators = new ModelMediators(0f, 0f, 0f, 0f, 0f)

  /**
   * The mediator levels a healthy player sits at: the fixed points of the model with no pathogen
   * present, giving an inflammation of exactly [[ModelConstants.BASELINE_INFLAMMATION]].
   */
  val RESTING: ModelMediators = new ModelMediators(25f, 30f, 30f, 20f, 25f)
}

/** The five electrolytes that are tracked separately, in mmol/L. */
final case class ModelElectrolytes(
    @BeanProperty sodium: Float,
    @BeanProperty potassium: Float,
    @BeanProperty magnesium: Float,
    @BeanProperty chloride: Float,
    @BeanProperty calcium: Float,
) {

  /** The value of `mineral`, for the table-driven parts. */
  def of(mineral: ModelMineral): Float =
    if (mineral == MineralRanges.SODIUM) this.sodium
    else if (mineral == MineralRanges.POTASSIUM) this.potassium
    else if (mineral == MineralRanges.MAGNESIUM) this.magnesium
    else if (mineral == MineralRanges.CHLORIDE) this.chloride
    else if (mineral == MineralRanges.CALCIUM) this.calcium
    else throw new IllegalArgumentException("iodine is a trace element, not an electrolyte")

  /**
   * How far the worst offender is outside its reference range, as a fraction of normal. 0 when
   * everything is inside.
   */
  def getWorstImbalance: Float = {
    var worst = 0f
    var i = 0
    while (i < ElectrolyteDefaults.MINERALS.size()) {
      val mineral = ElectrolyteDefaults.MINERALS.get(i)
      val deviation = mineral.relativeDeviation(this.of(mineral))
      if (deviation > worst) worst = deviation
      i += 1
    }
    worst
  }

  def withSodium(value: Float): ModelElectrolytes = copy(sodium = value)
  def withPotassium(value: Float): ModelElectrolytes = copy(potassium = value)
  def withMagnesium(value: Float): ModelElectrolytes = copy(magnesium = value)
  def withChloride(value: Float): ModelElectrolytes = copy(chloride = value)
  def withCalcium(value: Float): ModelElectrolytes = copy(calcium = value)
}

/** The electrolyte set and the order `/outbreak status` prints it in. */
object ElectrolyteDefaults {

  /** The five electrolytes, in the order `/outbreak status` prints them. */
  val MINERALS: java.util.List[ModelMineral] = java.util.List.of(
    MineralRanges.SODIUM,
    MineralRanges.POTASSIUM,
    MineralRanges.MAGNESIUM,
    MineralRanges.CHLORIDE,
    MineralRanges.CALCIUM,
  )

  val HEALTHY: ModelElectrolytes = new ModelElectrolytes(
    MineralRanges.SODIUM.normal,
    MineralRanges.POTASSIUM.normal,
    MineralRanges.MAGNESIUM.normal,
    MineralRanges.CHLORIDE.normal,
    MineralRanges.CALCIUM.normal,
  )
}

/**
 * The trace elements, which is iodine for now, in umol/L.
 *
 * Kept apart from the electrolytes because the body cannot make iodine at all, so the only way in is
 * food - kelp, in this mod - and the regulation is one-sided: a deficit is corrected by hanging on
 * to what little there is rather than by manufacturing more.
 */
final case class ModelTraceElements(@BeanProperty iodine: Float) {
  def getWorstImbalance: Float = MineralRanges.IODINE.relativeDeviation(this.iodine)

  /** -1 for a deficit, 1 for an excess, 0 while iodine is inside the reference range. */
  def getDirection: Int = MineralRanges.IODINE.direction(this.iodine)

  def withIodine(value: Float): ModelTraceElements = copy(iodine = value)
}

/** The trace element set a healthy player sits at. */
object TraceElementDefaults {
  val HEALTHY: ModelTraceElements = new ModelTraceElements(MineralRanges.IODINE.normal)
}

/**
 * Every scalar the model reads or writes, in one place.
 *
 * `OutbreakModelBridge` re-exports these names to Kotlin, so the numbers exist exactly once and the
 * Kotlin side, the docs and the self test all keep reading them from where they always did.
 */
object ModelConstants {
  val MIN_INFLAMMATION: Float = 0f
  val MAX_INFLAMMATION: Float = 100f
  val SAFE_INFLAMMATION_LOW: Float = 20f
  val SAFE_INFLAMMATION_HIGH: Float = 30f
  val BASELINE_INFLAMMATION: Float = 25f
  val IMMUNOSUPPRESSION_THRESHOLD: Float = 12f
  val IMMUNE_STORM_THRESHOLD: Float = 75f

  val MAX_PATHOGEN: Float = 100f
  val SYMPTOM_THRESHOLD: Float = 8f
  val SEVERE_LOAD: Float = 60f

  val WATER_MIN: Float = 0f
  val WATER_LOW: Float = 30f
  val WATER_NORMAL: Float = 100f
  val WATER_MAX: Float = 200f

  /** What a player starts with and what `/outbreak cure` restores: hydrated, but not full. */
  val WATER_START: Float = 80f

  val WATER_VISIBLY_OVERHYDRATED: Float = 150f

  /** Below this the player is not just thirsty: hunger sets in. */
  val WATER_SEVERELY_DEHYDRATED: Float = 15f
  val THIRST_CELLS: Int = 10
  val WATER_PER_DRINK: Float = 15f
  val WATER_DECAY_PER_TICK: Float = 89.5f / 24000f

  val SALICIN_EFFECTIVE: Float = 1f
  val SALICIN_CAP: Float = 3f
  val SALICIN_METABOLISM_TICKS: Int = 72000
  val SALICIN_DECAY_PER_TICK: Float = SALICIN_CAP / SALICIN_METABOLISM_TICKS

  val DEXAMETHASONE_EFFECTIVE: Float = 1f
  val DEXAMETHASONE_CAP: Float = 2f
  val DEXAMETHASONE_METABOLISM_TICKS: Int = 48000
  val DEXAMETHASONE_DECAY_PER_TICK: Float = DEXAMETHASONE_CAP / DEXAMETHASONE_METABOLISM_TICKS

  val TEMPERATURE_NORMAL: Float = 37f
  val TEMPERATURE_MIN: Float = 30f
  val TEMPERATURE_MAX: Float = 42f
  val COLD_MILD: Float = 36f
  val COLD_SEVERE: Float = 35f
  val FEVER_MILD: Float = 38.5f
  val FEVER_SEVERE: Float = 40f

  val PYROGEN_CAP: Float = 6f
  val PYROGEN_METABOLISM_TICKS: Int = 24000
  val PYROGEN_DECAY_PER_TICK: Float = PYROGEN_CAP / PYROGEN_METABOLISM_TICKS

  // ---------------------------------------------------------------- anticholinergics

  /** The most of either tropane alkaloid a body can carry. */
  val ANTICHOLINERGIC_CAP: Float = 5f

  /**
   * Total alkaloid load at which the body starts running a temperature.
   *
   * Unlike a fever from an infection this is not the immune system: it is the drug shutting down
   * sweating, which is exactly what atropine does to a real patient.
   */
  val ANTICHOLINERGIC_FEVER_THRESHOLD: Float = 1.5f

  /** The two steps above it: 1.5..2.5 runs to 38, 2.5..4 to 39.5, and 4 or more to 41. */
  val ANTICHOLINERGIC_FEVER_STEP: Float = 2.5f
  val ANTICHOLINERGIC_FEVER_MAX: Float = 4f

  /** The ceiling each step drives the core temperature towards. */
  val ANTICHOLINERGIC_FEVER_MILD: Float = 38f
  val ANTICHOLINERGIC_FEVER_SEVERE: Float = 39.5f
  val ANTICHOLINERGIC_FEVER_EXTREME: Float = 41f

  /** Either alkaloid alone this high blurs the vision... */
  val ANTICHOLINERGIC_BLUR_SINGLE: Float = 2.3f

  /** ...or the two of them together this high. */
  val ANTICHOLINERGIC_BLUR_TOTAL: Float = 2.7f

  /** How far a blurred player can see, in blocks. */
  val ANTICHOLINERGIC_BLUR_DISTANCE: Float = 8f

  /** Both alkaloids are cleared over one in-game day. */
  val ANTICHOLINERGIC_METABOLISM_TICKS: Int = 24000
  val ANTICHOLINERGIC_DECAY_PER_TICK: Float = ANTICHOLINERGIC_CAP / ANTICHOLINERGIC_METABOLISM_TICKS

  // ---------------------------------------------------------------- psilocybin and psilocin

  /** The most of either of the mushroom's compounds a body can carry. */
  val PSILOCYBIN_CAP: Float = 10f
  val PSILOCIN_CAP: Float = 10f

  /** One mushroom's worth, which is the unit both of the rates below are written in. */
  val PSILOCIN_DOSE: Float = 1.3f

  /** A dose of psilocybin becomes psilocin over half a game day, one for one. */
  val PSILOCYBIN_METABOLISM_TICKS: Int = 12000
  val PSILOCYBIN_DECAY_PER_TICK: Float = PSILOCIN_DOSE / PSILOCYBIN_METABOLISM_TICKS

  /**
   * Psilocin leaves at a flat 1.3 a game day whatever the level, rather than as a fraction of what
   * is there - so a single mushroom is gone in a day and ten of them take eight.
   */
  val PSILOCIN_METABOLISM_TICKS: Int = 24000
  val PSILOCIN_DECAY_PER_TICK: Float = PSILOCIN_DOSE / PSILOCIN_METABOLISM_TICKS

  /** Where the four stages of the trip start: outlines, colour, a mild warp, a hard one. */
  val PSILOCIN_OUTLINE: Float = 1.2f
  val PSILOCIN_COLOUR: Float = 1.7f
  val PSILOCIN_WARP: Float = 2.5f
  val PSILOCIN_STORM: Float = 5f

  /** Past a hard warp the body runs hot, and at 7 it is as bad as either of them gets. */
  val PSILOCIN_FEVER_STEP: Float = 7f
  val PSILOCIN_FEVER_MILD: Float = 39f
  val PSILOCIN_FEVER_EXTREME: Float = 41f
}

/**
 * The model's whole input and output: everything `Physiology.tick` reads and everything it writes.
 *
 * Kotlin's `OutbreakData` is the storage and serialisation layer (it owns the codecs and the Fabric
 * attachment), and `OutbreakModelBridge` is the only thing that converts between the two. Keeping
 * the model on its own state type is what lets it stay free of Minecraft and of Kotlin: it is a pure
 * function of numbers.
 *
 * There is no companion object, so this is built with `new` and copied with `withX`; the scalars it
 * is stated against are in [[ModelConstants]].
 */
final case class ModelState(
    @BeanProperty mediators: ModelMediators,
    @BeanProperty bacteria: Float,
    @BeanProperty virus: Float,
    @BeanProperty water: Float,
    @BeanProperty electrolytes: ModelElectrolytes,
    @BeanProperty traceElements: ModelTraceElements,
    @BeanProperty salicin: Float,
    @BeanProperty dexamethasone: Float,
    @BeanProperty temperature: Float,
    @BeanProperty pyrogen: Float,
    /** Scopolamine, the deliriant half of a mandrake, 0..[ModelConstants.ANTICHOLINERGIC_CAP]. */
    @BeanProperty scopolamine: Float,
    /** Atropine, the same plant's peripheral poison, on the same cap. */
    @BeanProperty atropine: Float,
    /** Psilocybin, the mushroom's prodrug: no effect of its own, converted into psilocin. 0..10. */
    @BeanProperty psilocybin: Float,
    /** Psilocin, which is what the trip and the fever actually come from. 0..10. */
    @BeanProperty psilocin: Float,
) {
  def withMediators(value: ModelMediators): ModelState = copy(mediators = value)
  def withBacteria(value: Float): ModelState = copy(bacteria = value)
  def withVirus(value: Float): ModelState = copy(virus = value)
  def withWater(value: Float): ModelState = copy(water = value)
  def withElectrolytes(value: ModelElectrolytes): ModelState = copy(electrolytes = value)
  def withTraceElements(value: ModelTraceElements): ModelState = copy(traceElements = value)
  def withSalicin(value: Float): ModelState = copy(salicin = value)
  def withDexamethasone(value: Float): ModelState = copy(dexamethasone = value)
  def withTemperature(value: Float): ModelState = copy(temperature = value)
  def withPyrogen(value: Float): ModelState = copy(pyrogen = value)
  def withScopolamine(value: Float): ModelState = copy(scopolamine = value)
  def withAtropine(value: Float): ModelState = copy(atropine = value)
  def withPsilocybin(value: Float): ModelState = copy(psilocybin = value)
  def withPsilocin(value: Float): ModelState = copy(psilocin = value)
}
