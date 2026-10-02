package com.github.kusa233.outbreak.physiology;

import com.github.kusa233.outbreak.physiology.model.ElectrolyteDefaults;
import com.github.kusa233.outbreak.physiology.model.MediatorLevels;
import com.github.kusa233.outbreak.physiology.model.MineralRanges;
import com.github.kusa233.outbreak.physiology.model.ModelConstants;
import com.github.kusa233.outbreak.physiology.model.ModelElectrolytes;
import com.github.kusa233.outbreak.physiology.model.ModelMediators;
import com.github.kusa233.outbreak.physiology.model.ModelMineral;
import com.github.kusa233.outbreak.physiology.model.ModelState;
import com.github.kusa233.outbreak.physiology.model.ModelTraceElements;
import com.github.kusa233.outbreak.physiology.model.Physiology;
import com.github.kusa233.outbreak.physiology.model.TraceElementDefaults;

import java.util.Map;

/**
 * The seam between the mod and its numerical model.
 *
 * Every number and every calculation lives in Scala, in
 * {@code src/main/scala/.../physiology/model}. Kotlin cannot talk to it directly: K2 resolves a Scala
 * class by also loading its supertypes, so a Kotlin file that merely *names* a model type drags in
 * {@code scala.Product} and IntelliJ reports `Cannot access 'scala.Product' which is a supertype of
 * 'Mineral'` however the classpath is wired. Java references its dependencies by JVM descriptor and
 * has no such problem, so this class is the only thing in the project that is allowed to mention a
 * Scala class, and Kotlin only ever calls through it.
 *
 * The build order is what makes that possible: Scala compiles first, Kotlin second, Java last, so a
 * Java file can see both sides. Nothing here decides anything - it converts, it forwards, and it
 * re-exports the model's numbers under the names Kotlin already uses, so the model stays their single
 * source.
 *
 * <h2>Rules for this file</h2>
 *
 * <ul>
 *   <li>Nothing Scala-typed may appear in a public method, field or parameter. Kotlin resolves those
 *       eagerly, and a Scala type there puts the whole problem back. Scala types are confined to
 *       private fields and method bodies, which the Kotlin compiler never resolves.</li>
 *   <li>No arithmetic and no thresholds: every value below is read from the model or handed to it.
 *       If a number is needed on both sides it is added to the model and re-exported here.</li>
 *   <li>The model's objects are ordinary objects rather than companions, so their members are the
 *       static forwarders Scala generates for them: `ModelConstants.WATER_MAX()`, not
 *       `ModelConstants.WATER_MAX`. That is also why the names never collide with Kotlin's own
 *       storage types and every model type can be imported.</li>
 * </ul>
 */
public final class OutbreakModelBridge {

    private OutbreakModelBridge() {
    }

    // ================================================================== the numbers
    //
    // Re-exported from the model, which owns every one of them. They keep their old names and their
    // old home so that nothing else in the mod, or in the docs, had to move with them.

    public static final float MIN_INFLAMMATION = ModelConstants.MIN_INFLAMMATION();
    public static final float MAX_INFLAMMATION = ModelConstants.MAX_INFLAMMATION();

    /** The band a healthy player sits in. */
    public static final float SAFE_INFLAMMATION_LOW = ModelConstants.SAFE_INFLAMMATION_LOW();
    public static final float SAFE_INFLAMMATION_HIGH = ModelConstants.SAFE_INFLAMMATION_HIGH();

    /** The model's fixed point with no pathogen present, and the middle of the safe band. */
    public static final float BASELINE_INFLAMMATION = ModelConstants.BASELINE_INFLAMMATION();

    /** Below this the immune system is suppressed and the infection runs away. */
    public static final float IMMUNOSUPPRESSION_THRESHOLD = ModelConstants.IMMUNOSUPPRESSION_THRESHOLD();

    /** Above this the response itself is the disease. */
    public static final float IMMUNE_STORM_THRESHOLD = ModelConstants.IMMUNE_STORM_THRESHOLD();

    public static final float MAX_PATHOGEN = ModelConstants.MAX_PATHOGEN();

    /** Load at which the player starts showing symptoms. */
    public static final float SYMPTOM_THRESHOLD = ModelConstants.SYMPTOM_THRESHOLD();

    /** Load at which the symptoms are at full strength and the infection starts doing damage. */
    public static final float SEVERE_LOAD = ModelConstants.SEVERE_LOAD();

    // ---------------------------------------------------------------- mediators

    public static final float MEDIATORS_MAX = MediatorLevels.MAX();
    public static final float MEDIATORS_HISTAMINE_WEIGHT = MediatorLevels.HISTAMINE_WEIGHT();
    public static final float MEDIATORS_PROSTAGLANDIN_WEIGHT = MediatorLevels.PROSTAGLANDIN_WEIGHT();
    public static final float MEDIATORS_LEUKOTRIENE_WEIGHT = MediatorLevels.LEUKOTRIENE_WEIGHT();
    public static final float MEDIATORS_CYTOKINE_WEIGHT = MediatorLevels.CYTOKINE_WEIGHT();
    public static final float MEDIATORS_BRADYKININ_WEIGHT = MediatorLevels.BRADYKININ_WEIGHT();

    // ---------------------------------------------------------------- water

    public static final float WATER_MIN = ModelConstants.WATER_MIN();

    /** Below this the player is dehydrated. */
    public static final float WATER_LOW = ModelConstants.WATER_LOW();

    /** The top of the normal band. Above it the player is over-hydrated. */
    public static final float WATER_NORMAL = ModelConstants.WATER_NORMAL();

    /** Hard ceiling so drinking cannot run away. */
    public static final float WATER_MAX = ModelConstants.WATER_MAX();

    /** What a player starts with, and what `/outbreak cure` restores. */
    public static final float WATER_START = ModelConstants.WATER_START();

    /** Where over-hydration becomes visible: the thirst bar is already full below this. */
    public static final float WATER_VISIBLY_OVERHYDRATED = ModelConstants.WATER_VISIBLY_OVERHYDRATED();

    /** Below this the player is not just thirsty: hunger sets in. */
    public static final float WATER_SEVERELY_DEHYDRATED = ModelConstants.WATER_SEVERELY_DEHYDRATED();

    public static final int THIRST_CELLS = ModelConstants.THIRST_CELLS();

    /** Water added by any drinkable. */
    public static final float WATER_PER_DRINK = ModelConstants.WATER_PER_DRINK();

    /** A full bladder drains to one cell over roughly one in-game day. */
    public static final float WATER_DECAY_PER_TICK = ModelConstants.WATER_DECAY_PER_TICK();

    // ---------------------------------------------------------------- drugs

    public static final float SALICIN_EFFECTIVE = ModelConstants.SALICIN_EFFECTIVE();
    public static final float SALICIN_CAP = ModelConstants.SALICIN_CAP();
    public static final int SALICIN_METABOLISM_TICKS = ModelConstants.SALICIN_METABOLISM_TICKS();
    public static final float SALICIN_DECAY_PER_TICK = ModelConstants.SALICIN_DECAY_PER_TICK();

    public static final float DEXAMETHASONE_EFFECTIVE = ModelConstants.DEXAMETHASONE_EFFECTIVE();
    public static final float DEXAMETHASONE_CAP = ModelConstants.DEXAMETHASONE_CAP();
    public static final int DEXAMETHASONE_METABOLISM_TICKS = ModelConstants.DEXAMETHASONE_METABOLISM_TICKS();
    public static final float DEXAMETHASONE_DECAY_PER_TICK = ModelConstants.DEXAMETHASONE_DECAY_PER_TICK();

    // ---------------------------------------------------------------- temperature

    /** Core temperature of a healthy player, and the set point thermoregulation defends. */
    public static final float TEMPERATURE_NORMAL = ModelConstants.TEMPERATURE_NORMAL();

    /**
     * The comfortable band, just above 36.0 up to just below 38.5. The fever side deliberately
     * starts at 38.5 rather than 38.0: 38 is what a hot biome, a fire or a thyroid that runs hot
     * produces on its own, and the screen effects it switched on looked exactly like a bug.
     */
    public static final float COLD_MILD = ModelConstants.COLD_MILD();
    public static final float FEVER_MILD = ModelConstants.FEVER_MILD();

    /** Past these the thermal symptoms get worse; 40 is where a fever turns dangerous. */
    public static final float COLD_SEVERE = ModelConstants.COLD_SEVERE();
    public static final float FEVER_SEVERE = ModelConstants.FEVER_SEVERE();

    /** Hard clamp for the model; 42 is where proteins start to denature. */
    public static final float TEMPERATURE_MIN = ModelConstants.TEMPERATURE_MIN();
    public static final float TEMPERATURE_MAX = ModelConstants.TEMPERATURE_MAX();

    /** The most pyrogen a body can carry, i.e. the largest fever the test command can induce. */
    public static final float PYROGEN_CAP = ModelConstants.PYROGEN_CAP();
    public static final int PYROGEN_METABOLISM_TICKS = ModelConstants.PYROGEN_METABOLISM_TICKS();
    public static final float PYROGEN_DECAY_PER_TICK = ModelConstants.PYROGEN_DECAY_PER_TICK();

    // ---------------------------------------------------------------- mandrake alkaloids

    /** The most of either tropane alkaloid a body can carry. */
    public static final float ANTICHOLINERGIC_CAP = ModelConstants.ANTICHOLINERGIC_CAP();

    /** How far a player whose sight the alkaloids have blurred can see, in blocks. */
    public static final float ANTICHOLINERGIC_BLUR_DISTANCE = ModelConstants.ANTICHOLINERGIC_BLUR_DISTANCE();

    /** Either alkaloid is cleared over one in-game day. */
    public static final int ANTICHOLINERGIC_METABOLISM_TICKS = ModelConstants.ANTICHOLINERGIC_METABOLISM_TICKS();
    public static final float ANTICHOLINERGIC_DECAY_PER_TICK = ModelConstants.ANTICHOLINERGIC_DECAY_PER_TICK();

    // ================================================================== the reference ranges

    /**
     * One tracked mineral and the thresholds a real blood test would report.
     *
     * The bands, the unit, the clamp and the formatting all come from the model; this only carries
     * them across. Kotlin hangs its own {@code Mineral} enum on one of these per case, which is what
     * lets `/outbreak status` and the symptom table read the reference range without knowing that
     * Scala exists.
     */
    public static final class MineralSpec {

        private final ModelMineral mineral;

        private MineralSpec(ModelMineral mineral) {
            this.mineral = mineral;
        }

        /** The unit the value is measured in: mmol/L for the electrolytes, umol/L for iodine. */
        public String getUnit() {
            return this.mineral.getUnit();
        }

        /** The healthy concentration, and the set point the model regulates towards. */
        public float getNormal() {
            return this.mineral.getNormal();
        }

        /** Inside this band the mineral causes no symptoms at all. */
        public float getSafeLow() {
            return this.mineral.getSafeLow();
        }

        public float getSafeHigh() {
            return this.mineral.getSafeHigh();
        }

        /** Past these the symptoms become serious, and sometimes lethal. */
        public float getSevereLow() {
            return this.mineral.getSevereLow();
        }

        public float getSevereHigh() {
            return this.mineral.getSevereHigh();
        }

        /** Hard limits, so a runaway value can never reach nonsense. */
        public float getMin() {
            return this.mineral.getMin();
        }

        public float getMax() {
            return this.mineral.getMax();
        }

        public float clamp(float value) {
            return this.mineral.clamp(value);
        }

        /** Distance outside the reference range, in this mineral's own units; 0 while inside it. */
        public float deviation(float value) {
            return this.mineral.deviation(value);
        }

        /** The same distance as a fraction of normal, so minerals can be ranked against each other. */
        public float relativeDeviation(float value) {
            return this.mineral.relativeDeviation(value);
        }

        /** -1 for a deficit, 1 for an excess, 0 while the value is inside the reference range. */
        public int direction(float value) {
            return this.mineral.direction(value);
        }

        /** True past {@code severeLow} or {@code severeHigh}. */
        public boolean isSevere(float value) {
            return this.mineral.isSevere(value);
        }

        /** The value as `/outbreak status` prints it: iodine needs more decimals than sodium. */
        public String display(float value) {
            return this.mineral.display(value);
        }
    }

    /**
     * The model's minerals, by name.
     *
     * Keyed by name rather than by ordinal so that reordering Kotlin's enum cannot silently pair a
     * mineral with another one's reference range.
     */
    private static final Map<String, MineralSpec> SPECS = Map.of(
            "SODIUM", new MineralSpec(MineralRanges.SODIUM()),
            "POTASSIUM", new MineralSpec(MineralRanges.POTASSIUM()),
            "MAGNESIUM", new MineralSpec(MineralRanges.MAGNESIUM()),
            "CHLORIDE", new MineralSpec(MineralRanges.CHLORIDE()),
            "CALCIUM", new MineralSpec(MineralRanges.CALCIUM()),
            "IODINE", new MineralSpec(MineralRanges.IODINE()));

    /** The reference range of the mineral Kotlin calls {@code mineral}. */
    public static MineralSpec spec(Mineral mineral) {
        MineralSpec spec = SPECS.get(mineral.name());
        if (spec == null) {
            throw new IllegalArgumentException("the model tracks no mineral called " + mineral.name());
        }
        return spec;
    }

    // ================================================================== the fixed points

    /** The mediator levels a healthy player sits at, and the model's fixed point. */
    public static Mediators restingMediators() {
        return fromModel(MediatorLevels.RESTING());
    }

    /** A body with no inflammatory response at all, i.e. one that is immunosuppressed. */
    public static Mediators calmMediators() {
        return fromModel(MediatorLevels.CALM());
    }

    /** Every electrolyte at its normal concentration. */
    public static Electrolytes healthyElectrolytes() {
        return fromModel(ElectrolyteDefaults.HEALTHY());
    }

    /** Iodine at its normal concentration. */
    public static TraceElements healthyTraceElements() {
        return fromModel(TraceElementDefaults.HEALTHY());
    }

    /** What a healthy player looks like, and what a new attachment is initialised with. */
    public static OutbreakData healthy() {
        return new OutbreakData(
                restingMediators(),
                0f,
                0f,
                WATER_START,
                healthyElectrolytes(),
                healthyTraceElements(),
                0f,
                0f,
                TEMPERATURE_NORMAL,
                0f,
                0f,
                0f);
    }

    // ================================================================== derived values

    /** Inflammation index, 0..100, as the model's weighted sum of the mediators. */
    public static float inflammation(
            float histamine, float prostaglandin, float leukotriene, float cytokine, float bradykinin) {
        return new ModelMediators(histamine, prostaglandin, leukotriene, cytokine, bradykinin)
                .getInflammation();
    }

    /** How far the worst electrolyte is outside its reference range, as a fraction of normal. */
    public static float worstImbalance(
            float sodium, float potassium, float magnesium, float chloride, float calcium) {
        return new ModelElectrolytes(sodium, potassium, magnesium, chloride, calcium).getWorstImbalance();
    }

    /** Combined pathogen load. */
    public static float pathogenLoad(float bacteria, float virus) {
        return Physiology.pathogenLoad(bacteria, virus);
    }

    public static boolean isSymptomatic(float load) {
        return Physiology.isSymptomatic(load);
    }

    public static boolean isImmuneStorm(float inflammation) {
        return Physiology.isImmuneStorm(inflammation);
    }

    public static boolean isImmunosuppressed(float inflammation) {
        return Physiology.isImmunosuppressed(inflammation);
    }

    public static boolean isSevereInfection(float load) {
        return Physiology.isSevereInfection(load);
    }

    public static float severity(float load) {
        return Physiology.severity(load);
    }

    public static boolean isOverhydrated(float water) {
        return Physiology.isOverhydrated(water);
    }

    public static boolean isDehydrated(float water) {
        return Physiology.isDehydrated(water);
    }

    public static int thirstCells(float water) {
        return Physiology.thirstCells(water);
    }

    public static boolean isFebrile(float temperature) {
        return Physiology.isFebrile(temperature);
    }

    public static boolean isHypothermic(float temperature) {
        return Physiology.isHypothermic(temperature);
    }

    public static boolean hasThermalStress(float temperature) {
        return Physiology.hasThermalStress(temperature);
    }

    public static int thermalTier(float temperature) {
        return Physiology.thermalTier(temperature);
    }

    /** The two mandrake alkaloids, as one number. */
    public static float anticholinergicLoad(OutbreakData data) {
        return Physiology.anticholinergicLoad(toModel(data));
    }

    /** The temperature the alkaloids are driving the body towards, as an offset from normal. */
    public static float anticholinergicFever(OutbreakData data) {
        return Physiology.anticholinergicFever(toModel(data));
    }

    /** True when the alkaloids have blurred the player's sight. */
    public static boolean isVisionBlurred(OutbreakData data) {
        return Physiology.isVisionBlurred(toModel(data));
    }

    // ================================================================== symptom magnitudes

    /** Magic damage a severe infection does in one two-second pass, and 0 while it is not severe. */
    public static float sepsisDamage(float load) {
        return Physiology.sepsisDamage(load);
    }

    /** Chance that one shake roll actually shakes the camera. */
    public static float shakeChance(OutbreakData data) {
        return Physiology.shakeChance(toModel(data));
    }

    /** Extra food exhaustion while the body is unwell; a multiplier, so 1 is "nothing wrong". */
    public static float exhaustionMultiplier(OutbreakData data) {
        return Physiology.exhaustionMultiplier(toModel(data));
    }

    /** How much slower this body mines, as a positive fraction; 0 when nothing is wrong. */
    public static float miningPenalty(OutbreakData data) {
        return Physiology.miningPenalty(toModel(data));
    }

    /** The temperature the surroundings are dragging the body towards, before thermoregulation. */
    public static float environmentTemperature(
            float biomeTemperature, boolean wet, boolean powderSnow, boolean lava, boolean fire) {
        return Physiology.environmentTemperature(biomeTemperature, wet, powderSnow, lava, fire);
    }

    /** Extra food exhaustion from a shiver or a fever, per tier of thermal stress. */
    public static float thermalExhaustion(int tier) {
        return Physiology.thermalExhaustion(tier);
    }

    /** Extra food exhaustion from an immune storm, which burns energy fighting itself. */
    public static float stormExhaustion() {
        return Physiology.stormExhaustion();
    }

    // ================================================================== the model

    /** Advances a player's physiology by a single tick, in neutral surroundings. */
    public static OutbreakData tick(OutbreakData data) {
        return fromModel(Physiology.tick(toModel(data)));
    }

    /** Advances a player's physiology by a single tick, towards {@code ambient} degrees. */
    public static OutbreakData tick(OutbreakData data, float ambient) {
        return fromModel(Physiology.tick(toModel(data), ambient));
    }

    /** How well an inflammation level fights pathogens, in 0..1. */
    public static float immuneCompetence(float inflammation) {
        return Physiology.immuneCompetence(inflammation);
    }

    /** Drug concentration as a multiple of its effective concentration, capped. */
    public static float suppression(float concentration, float effective) {
        return Physiology.suppression(concentration, effective);
    }

    /** The core temperature the body is currently aiming for. */
    public static float targetTemperature(OutbreakData data, float ambient) {
        return Physiology.targetTemperature(toModel(data), ambient);
    }

    /**
     * Raises or lowers the fever so that the body *peaks* at {@code degrees} Celsius, whatever the
     * infection is already doing.
     */
    public static OutbreakData induceFever(OutbreakData data, float degrees, float ambient) {
        return fromModel(Physiology.induceFever(toModel(data), degrees, ambient));
    }

    /** Adds a pathogen seed, used by the infection sources. */
    public static OutbreakData seed(OutbreakData data, float bacteria, float virus) {
        return fromModel(Physiology.seed(toModel(data), bacteria, virus));
    }

    /** Adds salicin, capped. */
    public static OutbreakData dose(OutbreakData data, float amount) {
        return fromModel(Physiology.dose(toModel(data), amount));
    }

    /** Adds dexamethasone, capped. */
    public static OutbreakData inject(OutbreakData data, float amount) {
        return fromModel(Physiology.inject(toModel(data), amount));
    }

    /** Adds water from a drink. */
    public static OutbreakData drink(OutbreakData data, float amount) {
        return fromModel(Physiology.drink(toModel(data), amount));
    }

    /** Adds salt, in mmol/L of serum sodium and chloride. */
    public static OutbreakData salt(
            OutbreakData data, float sodium, float chloride, float magnesium, float calcium) {
        return fromModel(Physiology.salt(toModel(data), sodium, chloride, magnesium, calcium));
    }

    /** Adds iodine, in umol/L, which the body only gets from food - kelp, in this mod. */
    public static OutbreakData iodine(OutbreakData data, float amount) {
        return fromModel(Physiology.iodine(toModel(data), amount));
    }

    /** Adds the two tropane alkaloids a mandrake carries, capped. */
    public static OutbreakData anticholinergic(OutbreakData data, float scopolamine, float atropine) {
        return fromModel(Physiology.anticholinergic(toModel(data), scopolamine, atropine));
    }

    // ================================================================== the conversion
    //
    // The only place a Kotlin `OutbreakData` becomes the model's `ModelState` or back. Everything
    // above funnels through these four pairs, so there is exactly one definition of what the two
    // representations mean.

    private static ModelState toModel(OutbreakData data) {
        return new ModelState(
                toModel(data.getMediators()),
                data.getBacteria(),
                data.getVirus(),
                data.getWater(),
                toModel(data.getElectrolytes()),
                toModel(data.getTraceElements()),
                data.getSalicin(),
                data.getDexamethasone(),
                data.getTemperature(),
                data.getPyrogen(),
                data.getScopolamine(),
                data.getAtropine());
    }

    private static OutbreakData fromModel(ModelState state) {
        return new OutbreakData(
                fromModel(state.getMediators()),
                state.getBacteria(),
                state.getVirus(),
                state.getWater(),
                fromModel(state.getElectrolytes()),
                fromModel(state.getTraceElements()),
                state.getSalicin(),
                state.getDexamethasone(),
                state.getTemperature(),
                state.getPyrogen(),
                state.getScopolamine(),
                state.getAtropine());
    }

    private static ModelMediators toModel(Mediators mediators) {
        return new ModelMediators(
                mediators.getHistamine(),
                mediators.getProstaglandin(),
                mediators.getLeukotriene(),
                mediators.getCytokine(),
                mediators.getBradykinin());
    }

    private static Mediators fromModel(ModelMediators mediators) {
        return new Mediators(
                mediators.getHistamine(),
                mediators.getProstaglandin(),
                mediators.getLeukotriene(),
                mediators.getCytokine(),
                mediators.getBradykinin());
    }

    private static ModelElectrolytes toModel(Electrolytes electrolytes) {
        return new ModelElectrolytes(
                electrolytes.getSodium(),
                electrolytes.getPotassium(),
                electrolytes.getMagnesium(),
                electrolytes.getChloride(),
                electrolytes.getCalcium());
    }

    private static Electrolytes fromModel(ModelElectrolytes electrolytes) {
        return new Electrolytes(
                electrolytes.getSodium(),
                electrolytes.getPotassium(),
                electrolytes.getMagnesium(),
                electrolytes.getChloride(),
                electrolytes.getCalcium());
    }

    private static ModelTraceElements toModel(TraceElements traceElements) {
        return new ModelTraceElements(traceElements.getIodine());
    }

    private static TraceElements fromModel(ModelTraceElements traceElements) {
        return new TraceElements(traceElements.getIodine());
    }
}
