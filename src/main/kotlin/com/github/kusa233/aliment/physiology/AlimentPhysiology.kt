package com.github.kusa233.aliment.physiology

/**
 * The bridge between the mod and its numerical model, in the shape the mod already used.
 *
 * **All of the arithmetic lives in Scala**, in `src/main/scala/.../physiology/model/Physiology.scala`,
 * along with every constant it is built from. Kotlin cannot name those types - K2 drags
 * `scala.Product` in when it does, which is what the IDE kept reporting - so every call goes through
 * [AlimentModelBridge], a Java file that references the model by JVM descriptor and is therefore
 * invisible to that problem.
 *
 * This object deliberately adds no numbers of its own. It exists to keep the call sites that were
 * written against the model's old Kotlin shape working unchanged: the bridge speaks Java, so it has
 * no default arguments, and this is where `tick(data)`, `drink(data)` and `seed(data, bacteria = 4f)`
 * get them back.
 */
object AlimentPhysiology {

    // ------------------------------------------------------------------ the model

    /** Advances a player's physiology by a single tick, in neutral surroundings. */
    fun tick(data: AlimentData): AlimentData = AlimentModelBridge.tick(data)

    /**
     * Advances a player's physiology by a single tick.
     *
     * [ambient] is the temperature the environment is dragging the body towards, on the same Celsius
     * scale as the body itself; [AlimentData.TEMPERATURE_NORMAL] means "indoors, no wind, nothing to
     * fight".
     */
    fun tick(data: AlimentData, ambient: Float): AlimentData = AlimentModelBridge.tick(data, ambient)

    /** How well an inflammation level fights pathogens, in `0..1`. */
    fun immuneCompetence(inflammation: Float): Float = AlimentModelBridge.immuneCompetence(inflammation)

    /** Drug concentration as a multiple of its effective concentration, capped. */
    fun suppression(concentration: Float, effective: Float): Float =
        AlimentModelBridge.suppression(concentration, effective)

    /** The core temperature the body is currently aiming for. */
    fun targetTemperature(data: AlimentData, ambient: Float = AlimentData.TEMPERATURE_NORMAL): Float =
        AlimentModelBridge.targetTemperature(data, ambient)

    /**
     * Raises or lowers the fever so that the body *peaks* at [degrees] Celsius.
     *
     * Works out how much pyrogen is needed given whatever the infection is already doing, so the
     * answer is the requested temperature whether or not the player is ill.
     */
    fun induceFever(
        data: AlimentData,
        degrees: Float,
        ambient: Float = AlimentData.TEMPERATURE_NORMAL,
    ): AlimentData = AlimentModelBridge.induceFever(data, degrees, ambient)

    // ------------------------------------------------------------------ external inputs

    /** Adds a pathogen seed, used by the infection sources. */
    fun seed(data: AlimentData, bacteria: Float = 0f, virus: Float = 0f): AlimentData =
        AlimentModelBridge.seed(data, bacteria, virus)

    /** Adds salicin, capped at [AlimentData.SALICIN_CAP]. */
    fun dose(data: AlimentData, amount: Float): AlimentData = AlimentModelBridge.dose(data, amount)

    /** Adds dexamethasone, capped at [AlimentData.DEXAMETHASONE_CAP]. */
    fun inject(data: AlimentData, amount: Float): AlimentData = AlimentModelBridge.inject(data, amount)

    /** Adds water from a drink. */
    fun drink(data: AlimentData, amount: Float = AlimentData.WATER_PER_DRINK): AlimentData =
        AlimentModelBridge.drink(data, amount)

    /**
     * Adds salt, in mmol/L of serum sodium and chloride. Crude salt carries the other minerals that
     * rock salt contains; refined salt is almost pure sodium chloride.
     */
    fun salt(
        data: AlimentData,
        sodium: Float,
        chloride: Float,
        magnesium: Float = 0f,
        calcium: Float = 0f,
    ): AlimentData = AlimentModelBridge.salt(data, sodium, chloride, magnesium, calcium)

    /** Adds iodine, in umol/L, which the body only gets from food - kelp, in this mod. */
    fun iodine(data: AlimentData, amount: Float): AlimentData = AlimentModelBridge.iodine(data, amount)

    /** Adds vitamin C, in umol/L, from plant foods (fruits, carrots, pumpkins, etc.). */
    fun vitaminC(data: AlimentData, amount: Float): AlimentData = AlimentModelBridge.vitaminC(data, amount)

    /**
     * Adds the two tropane alkaloids a mandrake carries, capped at
     * [AlimentData.ANTICHOLINERGIC_CAP] each. Past 1.5 of the two together the body runs a
     * temperature; past 2.3 of either, or 2.7 of the two, the player's sight blurs.
     */
    fun anticholinergic(data: AlimentData, scopolamine: Float, atropine: Float): AlimentData =
        AlimentModelBridge.anticholinergic(data, scopolamine, atropine)

    /**
     * Adds what one raw gymnopilus carries: a dose of psilocybin, which does nothing on its own and
     * becomes psilocin over the next half a game day, and a dose of psilocin, which is the trip.
     */
    fun mushroom(data: AlimentData, psilocybin: Float, psilocin: Float): AlimentData =
        AlimentModelBridge.mushroom(data, psilocybin, psilocin)

    /**
     * Adds ephedrine, the stimulant alkaloid, capped at [AlimentData.EPHEDRINE_CAP].
     */
    fun addEphedrine(data: AlimentData, amount: Float): AlimentData =
        AlimentModelBridge.addEphedrine(data, amount)

    /**
     * Adds berberine, capped at [AlimentData.BERBERINE_CAP].
     */
    fun addBerberine(data: AlimentData, amount: Float): AlimentData =
        AlimentModelBridge.addBerberine(data, amount)

    /**
     * Adds glycyrrhizin, capped at [AlimentData.GLYCYRRHIZIN_CAP].
     */
    fun addGlycyrrhizin(data: AlimentData, amount: Float): AlimentData =
        AlimentModelBridge.addGlycyrrhizin(data, amount)

    /**
     * Adds ethanol, capped at [AlimentData.ETHANOL_CAP].
     */
    fun addEthanol(data: AlimentData, amount: Float): AlimentData =
        AlimentModelBridge.addEthanol(data, amount)
}
