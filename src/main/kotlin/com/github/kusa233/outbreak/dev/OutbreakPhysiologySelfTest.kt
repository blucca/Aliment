package com.github.kusa233.outbreak.dev

import com.github.kusa233.outbreak.Outbreak
import com.github.kusa233.outbreak.physiology.Electrolytes
import com.github.kusa233.outbreak.physiology.Mediators
import com.github.kusa233.outbreak.physiology.MineralScale
import com.github.kusa233.outbreak.physiology.OutbreakAttachments
import com.github.kusa233.outbreak.physiology.OutbreakClientState
import com.github.kusa233.outbreak.physiology.OutbreakData
import com.github.kusa233.outbreak.physiology.OutbreakIngestion
import com.github.kusa233.outbreak.physiology.OutbreakPhysiology
import com.github.kusa233.outbreak.physiology.OutbreakRuntime
import com.github.kusa233.outbreak.physiology.OutbreakSymptoms
import com.github.kusa233.outbreak.physiology.TraceElements
import com.github.kusa233.outbreak.registry.OutbreakBlocks
import com.github.kusa233.outbreak.registry.OutbreakItems
import com.github.kusa233.outbreak.world.OutbreakGrinding
import com.google.gson.JsonParser
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.FakePlayer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.inventory.GrindstoneMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.apache.logging.log4j.LogManager
import kotlin.math.abs

/**
 * DEVELOPMENT ONLY. Second self test entrypoint, covering the physiology model.
 *
 * The model half needs no world at all: the simulation is pure data, so it runs in a few
 * milliseconds on the first server tick. The second half uses a `FakePlayer` so the mixins are
 * exercised end to end. Enable it from `fabric.mod.json` the same way as [OutbreakSelfTest].
 */
class OutbreakPhysiologySelfTest : ModInitializer {

    private val logger = LogManager.getLogger("OutbreakPhysiologySelfTest")

    private var ticks = 0
    private var done = false
    private var passed = 0
    private var failed = 0

    override fun onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            ticks++
            if (done || ticks < 40) {
                return@register
            }
            done = true
            try {
                runAll()
                translationChecks()
                mixinChecks(server.overworld())
                symptomChecks(server.overworld())
                grinding(server.overworld())
            } catch (t: Throwable) {
                logger.error("PHYSIOLOGY SELFTEST exception", t)
                failed++
            }
            logger.info("PHYSIOLOGY SELFTEST DONE passed={} failed={}", passed, failed)
        }
    }

    // ================================================================== model

    private fun runAll() {
        homeostasis()
        mildInfectionResolves()
        untreatedInfectionRunsAway()
        salicinControlsInfection()
        dexamethasoneControlsInfection()
        overdoseLetsInfectionRun()
        drugMetabolism()
        immuneCompetenceCurve()
        mediatorBreakdown()
        thirstDrainsInOneDay()
        overhydration()
        drinkingDilutesElectrolytes()
        dehydrationStopsWhenDrinking()
        iodineSteadyState()
        temperatureHomeostasis()
        environmentThermoregulation()
        feverFollowsProstaglandin()
        pyrogenInducesFever()
        thyroidMovesTheSetPoint()
    }

    private fun homeostasis() {
        var data = OutbreakData.HEALTHY
        var min = data.inflammation
        var max = data.inflammation
        repeat(72_000) {
            data = OutbreakPhysiology.tick(data)
            min = minOf(min, data.inflammation)
            max = maxOf(max, data.inflammation)
        }
        logger.info(
            "PHYS homeostasis over 3 days: inflammation {}..{} lowest electrolyte {} water {} temperature {}",
            min, max, data.electrolytes.lowest, data.water, data.temperature,
        )
        check("homeostasis keeps inflammation inside 20..30", min >= 20f && max <= 30f)
        // Iodine is deliberately excluded: it has its own, lower, steady state (see iodineSteadyState).
        val e = data.electrolytes
        val steady = listOf(e.sodium, e.potassium, e.magnesium, e.chloride, e.calcium)
        check("homeostasis keeps the five electrolytes near normal", steady.all { abs(it - 100f) < 5f })
        check("homeostasis keeps the core temperature at 37", abs(data.temperature - OutbreakData.TEMPERATURE_NORMAL) < 0.01f)
    }

    private fun mildInfectionResolves() {
        var data = OutbreakPhysiology.seed(OutbreakData.HEALTHY, bacteria = 6f)
        var peakInflammation = 0f
        repeat(24_000) {
            data = OutbreakPhysiology.tick(data)
            peakInflammation = maxOf(peakInflammation, data.inflammation)
        }
        logger.info("PHYS mild infection: peak inflammation {} final load {}", peakInflammation, data.bacteria)
        check("a single 6 point infection is cleared", data.bacteria < 0.5f)
        check("a mild infection does not cause a storm", peakInflammation < OutbreakData.IMMUNE_STORM_THRESHOLD)
    }

    private fun untreatedInfectionRunsAway() {
        var data = OutbreakPhysiology.seed(OutbreakData.HEALTHY, bacteria = 40f)
        var peak = 0f
        repeat(24_000) {
            data = OutbreakPhysiology.tick(data)
            peak = maxOf(peak, data.inflammation)
        }
        logger.info("PHYS untreated infection: final load {} peak inflammation {}", data.bacteria, peak)
        check("an untreated 40 point infection takes hold", data.bacteria > 50f)
        check("an untreated infection ends in an immune storm", peak >= OutbreakData.IMMUNE_STORM_THRESHOLD)
    }

    private fun salicinControlsInfection() {
        var data = OutbreakPhysiology.seed(OutbreakData.HEALTHY, bacteria = 40f)
        data = OutbreakPhysiology.dose(data, 1.1f)
        var peak = 0f
        repeat(24_000) {
            data = OutbreakPhysiology.tick(data)
            peak = maxOf(peak, data.inflammation)
        }
        logger.info("PHYS salicin treated: final load {} peak inflammation {}", data.bacteria, peak)
        check("salicin clears the same infection", data.bacteria < 0.5f)
        check("salicin keeps inflammation out of the storm", peak < OutbreakData.IMMUNE_STORM_THRESHOLD)
    }

    private fun dexamethasoneControlsInfection() {
        var data = OutbreakPhysiology.seed(OutbreakData.HEALTHY, bacteria = 40f)
        data = OutbreakPhysiology.inject(data, 1.2f)
        var peak = 0f
        repeat(24_000) {
            data = OutbreakPhysiology.tick(data)
            peak = maxOf(peak, data.inflammation)
        }
        logger.info("PHYS dexamethasone treated: final load {} peak inflammation {}", data.bacteria, peak)
        check("dexamethasone clears the same infection", data.bacteria < 0.5f)
        check("dexamethasone keeps inflammation out of the storm", peak < OutbreakData.IMMUNE_STORM_THRESHOLD)

        // Dexamethasone is the drug that actually shuts cytokines down; salicin is not.
        var med = OutbreakData.HEALTHY.copy(bacteria = 60f)
        val salicinOnly = OutbreakPhysiology.tick(OutbreakPhysiology.dose(med, 1.1f))
        val dexOnly = OutbreakPhysiology.tick(OutbreakPhysiology.inject(med, 1.2f))
        logger.info(
            "PHYS mediators after one tick: untreated cytokine {} salicin {} dexamethasone {}",
            OutbreakPhysiology.tick(med).mediators.cytokine,
            salicinOnly.mediators.cytokine,
            dexOnly.mediators.cytokine,
        )
        check("dexamethasone suppresses cytokines harder than salicin", dexOnly.mediators.cytokine < salicinOnly.mediators.cytokine)
    }

    private fun overdoseLetsInfectionRun() {
        var data = OutbreakPhysiology.seed(OutbreakData.HEALTHY, bacteria = 40f)
        repeat(24_000) {
            data = OutbreakPhysiology.tick(OutbreakPhysiology.dose(data, 0.5f))
        }
        logger.info("PHYS overdose: final load {} inflammation {}", data.bacteria, data.inflammation)
        check("an overdose suppresses inflammation", data.inflammation < OutbreakData.IMMUNOSUPPRESSION_THRESHOLD + 10f)
        check("an overdose lets the infection run away", data.bacteria > 50f)
    }

    private fun drugMetabolism() {
        var salicin = OutbreakData.HEALTHY.copy(salicin = OutbreakData.SALICIN_CAP)
        repeat(OutbreakData.SALICIN_METABOLISM_TICKS) { salicin = OutbreakPhysiology.tick(salicin) }
        check("salicin is fully metabolised after three game days", salicin.salicin <= 0f)

        var half = OutbreakData.HEALTHY.copy(salicin = OutbreakData.SALICIN_CAP)
        repeat(OutbreakData.SALICIN_METABOLISM_TICKS / 2) { half = OutbreakPhysiology.tick(half) }
        check(
            "salicin decays linearly",
            kotlin.math.abs(half.salicin - OutbreakData.SALICIN_CAP / 2f) < 0.01f,
        )

        var dex = OutbreakData.HEALTHY.copy(dexamethasone = OutbreakData.DEXAMETHASONE_CAP)
        repeat(OutbreakData.DEXAMETHASONE_METABOLISM_TICKS) { dex = OutbreakPhysiology.tick(dex) }
        check("dexamethasone is metabolised after two game days", dex.dexamethasone <= 0f)
    }

    private fun immuneCompetenceCurve() {
        val atBaseline = OutbreakPhysiology.immuneCompetence(OutbreakData.BASELINE_INFLAMMATION)
        val atZero = OutbreakPhysiology.immuneCompetence(0f)
        val atStorm = OutbreakPhysiology.immuneCompetence(OutbreakData.IMMUNE_STORM_THRESHOLD)
        logger.info("PHYS competence: 0 -> {} baseline -> {} storm -> {}", atZero, atBaseline, atStorm)
        check("competence peaks at the baseline", atBaseline > 0.99f)
        check("competence collapses at zero inflammation", atZero <= 0f)
        check("competence collapses during a storm", atStorm < 0.1f)
    }

    /** The inflammation index must really be a weighted sum of the five mediators. */
    private fun mediatorBreakdown() {
        val onlyCytokine = Mediators(0f, 0f, 0f, 100f, 0f).inflammation
        val onlyProstaglandin = Mediators(0f, 100f, 0f, 0f, 0f).inflammation
        val onlyHistamine = Mediators(100f, 0f, 0f, 0f, 0f).inflammation
        logger.info(
            "PHYS single mediator inflammation: cytokine {} prostaglandin {} histamine {}",
            onlyCytokine, onlyProstaglandin, onlyHistamine,
        )
        check("cytokines dominate the inflammation index", onlyCytokine > onlyProstaglandin && onlyCytokine > onlyHistamine)
        check("a calm mediator set gives the baseline", kotlin.math.abs(Mediators.CALM.inflammation) < 0.01f)
        check("the healthy baseline sits in the safe band", OutbreakData.HEALTHY.inflammation in 20f..30f)

        // The resting mediator levels must be the fixed point of the model, not a guess.
        val relaxed = OutbreakPhysiology.tick(OutbreakData.HEALTHY)
        logger.info(
            "PHYS resting mediators after one tick: {} vs declared {} (inflammation {} vs {})",
            relaxed.mediators, Mediators.RESTING, relaxed.inflammation, Mediators.RESTING.inflammation,
        )
        check("the resting mediator set is the model's fixed point", kotlin.math.abs(relaxed.inflammation - Mediators.RESTING.inflammation) < 0.6f)
    }

    // ================================================================== water

    private fun thirstDrainsInOneDay() {
        // Start from a healthy body so no electrolyte imbalance speeds the water loss up.
        var data = OutbreakData.HEALTHY.copy(water = OutbreakData.WATER_NORMAL)
        repeat(24_000) { data = OutbreakPhysiology.tick(data) }
        logger.info("PHYS thirst after one game day: water {} cells {}", data.water, data.thirstCells)
        check("a full bladder drains to one cell in one game day", data.thirstCells == 1)
    }

    private fun overhydration() {
        var data = OutbreakData.HEALTHY.copy(water = 150f)
        repeat(2_400) { data = OutbreakPhysiology.tick(data) }
        check("water above 100 is flagged as over-hydration", data.isOverhydrated)
        check("the thirst bar still shows ten cells when over-hydrated", data.thirstCells == OutbreakData.THIRST_CELLS)

        // Water above the normal band must drain faster than the base rate.
        val start = OutbreakData.HEALTHY.copy(water = 160f)
        val after = OutbreakPhysiology.tick(start)
        val baseAfter = OutbreakPhysiology.tick(OutbreakData.HEALTHY.copy(water = 60f))
        val excessLoss = 160f - after.water
        val baseLoss = 60f - baseAfter.water
        logger.info("PHYS water loss per tick: over-hydrated {} normal {}", excessLoss, baseLoss)
        check("over-hydration accelerates water loss", excessLoss > baseLoss * 1.5f)
    }

    private fun drinkingDilutesElectrolytes() {
        var data = OutbreakData.HEALTHY
        // Drink constantly: keep topping the bladder up above the normal band.
        repeat(48_000) {
            data = OutbreakPhysiology.drink(OutbreakPhysiology.tick(data), 40f)
        }
        logger.info(
            "PHYS two days of heavy drinking: sodium {} potassium {} magnesium {} chloride {} calcium {} water {}",
            data.electrolytes.sodium, data.electrolytes.potassium, data.electrolytes.magnesium,
            data.electrolytes.chloride, data.electrolytes.calcium, data.water,
        )
        check("heavy drinking dilutes sodium", data.electrolytes.sodium < Electrolytes.SAFE_LOW)
        check("heavy drinking dilutes chloride", data.electrolytes.chloride < Electrolytes.SAFE_LOW)
        check("magnesium is flushed more slowly than sodium", data.electrolytes.magnesium > data.electrolytes.sodium)
    }

    private fun dehydrationStopsWhenDrinking() {
        var data = OutbreakData.HEALTHY.copy(water = 20f)
        check("low water is flagged as dehydration", data.isDehydrated)
        data = OutbreakPhysiology.drink(data)
        check("one drink adds 15 water", kotlin.math.abs(data.water - 35f) < 0.01f)
    }

    /**
     * Iodine is regulated like everything else now, but around a lower set point, because the body
     * cannot make it: the equilibrium is `NORMAL - drain / homeostasis` = 88. That is inside the
     * safe band, so ignoring kelp is survivable, and three points from the bottom of it, so a fever
     * or a drinking binge will push it under.
     */
    private fun iodineSteadyState() {
        var data = OutbreakData.HEALTHY
        repeat(4 * 24_000) { data = OutbreakPhysiology.tick(data) }
        logger.info(
            "PHYS iodine after 4 days with no kelp: {} (safe band {}..{})",
            data.traceElements.iodine, MineralScale.SAFE_LOW, MineralScale.SAFE_HIGH,
        )
        check("iodine settles at a steady state instead of draining away", abs(data.traceElements.iodine - 88f) < 1.5f)
        check("that steady state is inside the safe band", !data.hasTraceElementImbalance)
        check("the five electrolytes are unaffected", abs(data.electrolytes.sodium - 100f) < 5f)
        check("iodine is not part of the electrolyte set", !data.hasElectrolyteImbalance)

        val oneKelp = OutbreakPhysiology.iodine(data, 25f)
        logger.info("PHYS iodine after one kelp: {}", oneKelp.traceElements.iodine)
        check("one kelp is enough and not too much", !oneKelp.hasTraceElementImbalance)

        val tooMuch = OutbreakPhysiology.iodine(data, 70f)
        logger.info("PHYS iodine after three helpings of kelp: {}", tooMuch.traceElements.iodine)
        check("too much kelp pushes iodine into excess", tooMuch.traceElements.direction > 0)
        check("an iodine excess is reported as an imbalance", tooMuch.hasTraceElementImbalance)

        var recovered = tooMuch
        repeat(4 * 24_000) { recovered = OutbreakPhysiology.tick(recovered) }
        check("an iodine excess is regulated away again", !recovered.hasTraceElementImbalance)

        // A sustained fever sweats iodine out, which is what pushes a neglectful player under.
        // The temperature is pinned every tick because a fever that is not fed by an infection or a
        // pyrogen would otherwise break on its own within a minute.
        var feverish = OutbreakData.HEALTHY.copy(temperature = 41f)
        repeat(3 * 24_000) { feverish = OutbreakPhysiology.tick(feverish.copy(temperature = 41f)) }
        logger.info("PHYS iodine after three days of fever: {}", feverish.traceElements.iodine)
        check("a long fever drains iodine below the safe band", feverish.hasTraceElementImbalance)
    }

    // ================================================================== temperature

    /** Nothing at all should move a healthy body off 37 degrees. */
    private fun temperatureHomeostasis() {
        var data = OutbreakData.HEALTHY
        var min = data.temperature
        var max = data.temperature
        repeat(24_000) {
            data = OutbreakPhysiology.tick(data)
            min = minOf(min, data.temperature)
            max = maxOf(max, data.temperature)
        }
        logger.info("PHYS core temperature over one game day: {}..{} tier {}", min, max, data.thermalTier)
        check("a healthy player holds 37.0 exactly", abs(min - 37f) < 0.001f && abs(max - 37f) < 0.001f)
        check("37.0 is the comfortable tier", data.thermalTier == 0 && !data.hasThermalStress)
    }

    /**
     * The environment: a temperate biome, and even a desert, are shrugged off; being soaked in the
     * snow is not. Powder snow has to be able to produce real hypothermia, and walking back out of
     * it has to fix it.
     */
    private fun environmentThermoregulation() {
        val temperate = OutbreakSymptoms.environmentTemperature(0.8f)
        val snowy = OutbreakSymptoms.environmentTemperature(-0.5f)
        val snowyWet = OutbreakSymptoms.environmentTemperature(-0.5f, wet = true)
        val powder = OutbreakSymptoms.environmentTemperature(-0.5f, wet = true, powderSnow = true)
        val desert = OutbreakSymptoms.environmentTemperature(2.0f)
        val lava = OutbreakSymptoms.environmentTemperature(0.8f, lava = true)
        logger.info(
            "PHYS ambient pull: temperate {} snowy {} snowy+wet {} powder snow {} desert {} lava {}",
            temperate, snowy, snowyWet, powder, desert, lava,
        )
        check("a temperate biome is thermoneutral", temperate == OutbreakData.TEMPERATURE_NORMAL)
        check("a snowy biome drags the body colder", snowy < OutbreakData.TEMPERATURE_NORMAL)
        check("but not out of the comfortable band on its own", snowy >= OutbreakData.COLD_MILD)
        check("desert heat does not even reach a mild fever", desert < OutbreakData.FEVER_MILD)
        check("being soaked through in the snow is not shrugged off", snowyWet < OutbreakData.COLD_MILD)
        check("powder snow is colder still", powder < snowyWet)
        check("lava is hot enough to matter", lava > OutbreakData.FEVER_MILD)

        var chilly = OutbreakData.HEALTHY
        repeat(12_000) { chilly = OutbreakPhysiology.tick(chilly, snowy) }
        logger.info("PHYS after 12000 ticks in a snowy biome: {}", chilly.temperature)
        check("a snowy biome alone produces no thermal symptoms", !chilly.hasThermalStress)

        var hot = OutbreakData.HEALTHY
        repeat(12_000) { hot = OutbreakPhysiology.tick(hot, desert) }
        check("a desert still gives a healthy player no fever", !hot.isFebrile)

        var cold = OutbreakData.HEALTHY
        repeat(12_000) { cold = OutbreakPhysiology.tick(cold, powder) }
        logger.info("PHYS after 12000 ticks in powder snow: {} tier {}", cold.temperature, cold.thermalTier)
        check("powder snow produces hypothermia", cold.isHypothermic)
        check("and it is the severe tier", cold.thermalTier <= -2)

        repeat(12_000) { cold = OutbreakPhysiology.tick(cold) }
        logger.info("PHYS ten minutes after getting out of it: {}", cold.temperature)
        check("getting out of the cold restores the temperature", !cold.hasThermalStress)
    }

    /**
     * An infection produces a fever through prostaglandin (PGE2 is the fever mediator), which is
     * also why salicin - a COX inhibitor - brings the fever down.
     */
    private fun feverFollowsProstaglandin() {
        var data = OutbreakPhysiology.seed(OutbreakData.HEALTHY, bacteria = 60f)
        var peak = data.temperature
        repeat(12_000) {
            data = OutbreakPhysiology.tick(data)
            peak = maxOf(peak, data.temperature)
        }
        logger.info(
            "PHYS fever from an untreated infection: peak {} final {} prostaglandin {}",
            peak, data.temperature, data.mediators.prostaglandin,
        )
        check("an untreated infection produces a fever", peak >= OutbreakData.FEVER_MILD)
        check("a cytokine storm runs a high fever", peak >= OutbreakData.FEVER_SEVERE)

        // Salicin is an antipyretic: damp the prostaglandins and the fever goes with them.
        var treated = OutbreakPhysiology.seed(OutbreakData.HEALTHY, bacteria = 60f)
        var treatedPeak = treated.temperature
        repeat(12_000) {
            treated = OutbreakPhysiology.tick(OutbreakPhysiology.dose(treated, 0.3f))
            treatedPeak = maxOf(treatedPeak, treated.temperature)
        }
        logger.info("PHYS fever with salicin on board: peak {}", treatedPeak)
        check("salicin suppresses the fever", treatedPeak < peak)

        // And a fever costs water and salt.
        val sweating = OutbreakData.HEALTHY.copy(temperature = 40f)
        val sober = OutbreakData.HEALTHY.copy(temperature = 37f)
        val sweatLoss = sober.water - OutbreakPhysiology.tick(sober).water
        val feverLoss = sweating.water - OutbreakPhysiology.tick(sweating).water
        logger.info("PHYS water loss per tick: at 37 {} at 40 {}", sweatLoss, feverLoss)
        check("a fever sweats water away faster", feverLoss > sweatLoss * 2f)
    }

    /** `/outbreak fever` works by injecting a pyrogen, which is stored and cleared like a drug. */
    private fun pyrogenInducesFever() {
        val fever = OutbreakPhysiology.induceFever(OutbreakData.HEALTHY, 39.5f)
        logger.info(
            "PHYS pyrogen injected for a 39.5 C peak: {} (initial set point {})",
            fever.pyrogen, OutbreakPhysiology.targetTemperature(fever),
        )
        check("the fever command injects pyrogen", fever.pyrogen > 0f)
        check(
            "the set point starts a little above the peak, to pay for the chase",
            fever.pyrogen > 2.5f,
        )

        // The pyrogen holds until the fever has developed, so it takes longer to peak than the
        // temperature alone would suggest; the run is long enough to cover the hold and the whole
        // metabolism on top of it.
        var walked = fever
        var peak = walked.temperature
        var feverish = 0
        repeat(3 * OutbreakData.PYROGEN_METABOLISM_TICKS) {
            walked = OutbreakPhysiology.tick(walked)
            peak = maxOf(peak, walked.temperature)
            if (walked.thermalTier >= 1) feverish++
        }
        logger.info(
            "PHYS induced fever: peak {} (asked for 39.5), pyrogen now {}, {} ticks in the fever band",
            peak, walked.pyrogen, feverish,
        )
        check("the fever peaks exactly where it was asked to", abs(peak - 39.5f) < 0.05f)
        check("pyrogen is cleared within a game day of the fever developing", walked.pyrogen == 0f)
        check("the fever breaks once the pyrogen is gone", !walked.hasThermalStress)
        check(
            "the screen effect lasts minutes, not hours",
            feverish in 5_000..20_000,
        )

        // A super-high fever, the one that adds the motion blur.
        val superHigh = OutbreakPhysiology.induceFever(OutbreakData.HEALTHY, 40.5f)
        var hot = superHigh
        var hotPeak = hot.temperature
        repeat(3 * OutbreakData.PYROGEN_METABOLISM_TICKS) {
            hot = OutbreakPhysiology.tick(hot)
            hotPeak = maxOf(hotPeak, hot.temperature)
        }
        logger.info("PHYS super-high fever: peak {} (asked for 40.5)", hotPeak)
        check("the super-high fever lands on its target too", abs(hotPeak - 40.5f) < 0.05f)
        check("and it reaches the tier that adds the blur", hotPeak >= OutbreakData.FEVER_SEVERE)

        // The same command, with a target below normal, is how hypothermia is tested.
        val chill = OutbreakPhysiology.induceFever(OutbreakData.HEALTHY, 34f)
        check("a target below normal gives a negative pyrogen", chill.pyrogen < 0f)
        var cold = chill
        var low = cold.temperature
        repeat(3 * OutbreakData.PYROGEN_METABOLISM_TICKS) {
            cold = OutbreakPhysiology.tick(cold)
            low = minOf(low, cold.temperature)
        }
        logger.info("PHYS induced hypothermia: lowest {}, pyrogen now {}", low, cold.pyrogen)
        check("the command can produce hypothermia too", low <= OutbreakData.COLD_SEVERE)
        check("and it lands on its target as well", abs(low - 34f) < 0.05f)
        check("the body warms back up afterwards", !cold.hasThermalStress)
    }

    /**
     * The thyroid sets the metabolic rate, so iodine moves the temperature set point: a deficiency
     * means the player cannot stay warm, an excess means they run hot. It is a small shift - under a
     * degree - because it is a set point change, not a fever.
     */
    private fun thyroidMovesTheSetPoint() {
        var hypothyroid = OutbreakData.HEALTHY.copy(traceElements = TraceElements(60f))
        var hyperthyroid = OutbreakData.HEALTHY.copy(traceElements = TraceElements(150f))
        repeat(12_000) {
            hypothyroid = OutbreakPhysiology.tick(hypothyroid)
            hyperthyroid = OutbreakPhysiology.tick(hyperthyroid)
        }
        logger.info(
            "PHYS resting temperature with iodine 60: {} / with iodine 150: {}",
            hypothyroid.temperature, hyperthyroid.temperature,
        )
        check("hypothyroidism lowers the resting temperature", hypothyroid.temperature < 37f)
        check("thyrotoxicosis raises it", hyperthyroid.temperature > 37f)
        check("neither is a fever on its own", !hyperthyroid.isFebrile && !hypothyroid.isHypothermic)
    }

    // ================================================================== symptoms

    /**
     * The symptom half: run the real server tick against a `FakePlayer` and look at what lands on
     * it. Every mineral is checked on both sides of its safe band, because the whole point of the
     * table in `OutbreakSymptoms` is that a deficit and an excess are both illnesses.
     */
    private fun symptomChecks(level: ServerLevel) {
        val player = FakePlayer.get(level)
        mineralSymptoms(player)
        thermalSymptoms(player)
        shakeOnlyWhenSomethingIsVisible(player)
        postEffects(player)
        seaWaterIsNotFoul(level, player)
    }

    private fun mineralSymptoms(player: ServerPlayer) {
        applyWith(player, minerals())
        check("a body with everything in band produces no symptoms at all", player.activeEffects.isEmpty())

        // --- sodium: hyponatraemia is confusion, hypernatraemia is thirst
        expect(player, "low sodium causes nausea", minerals(sodium = 60f), MobEffects.NAUSEA)
        expect(player, "low sodium slows the player down", minerals(sodium = 60f), MobEffects.SLOWNESS)
        expect(player, "slightly low sodium is only weakness", minerals(sodium = 80f), MobEffects.WEAKNESS)
        expect(player, "high sodium causes thirst", minerals(sodium = 140f), MobEffects.HUNGER)

        // --- potassium: both ends upset the heart
        expect(player, "low potassium causes weakness", minerals(potassium = 60f), MobEffects.WEAKNESS)
        expect(player, "low potassium causes mining fatigue", minerals(potassium = 60f), MobEffects.MINING_FATIGUE)
        expect(player, "high potassium causes cardiac slowing", minerals(potassium = 150f), MobEffects.SLOWNESS)

        // --- magnesium: tremor and cramps, then lethargy
        expect(player, "low magnesium causes tremor", minerals(magnesium = 60f), MobEffects.SLOWNESS)
        expect(player, "high magnesium causes lethargy", minerals(magnesium = 140f), MobEffects.SLOWNESS)

        // --- chloride: the acid-base side
        expect(player, "low chloride causes nausea", minerals(chloride = 60f), MobEffects.NAUSEA)
        expect(player, "high chloride causes nausea", minerals(chloride = 150f), MobEffects.NAUSEA)

        // --- calcium: tetany against lethargy
        expect(player, "low calcium causes tetany", minerals(calcium = 60f), MobEffects.SLOWNESS)
        expect(player, "high calcium causes lethargy", minerals(calcium = 150f), MobEffects.SLOWNESS)

        // --- iodine: the thyroid
        expect(player, "low iodine is subclinical hypothyroidism", minerals(iodine = 80f), MobEffects.WEAKNESS)
        expect(player, "very low iodine causes mining fatigue", minerals(iodine = 60f), MobEffects.MINING_FATIGUE)
        expect(player, "high iodine causes thyrotoxicosis", minerals(iodine = 150f), MobEffects.NAUSEA)
        expect(player, "and an appetite without weight gain", minerals(iodine = 150f), MobEffects.HUNGER)

        // The exhaustion multiplier picks up the minerals that genuinely raise metabolic cost.
        player.setAttached(OutbreakAttachments.DATA, minerals(magnesium = 80f))
        check("low magnesium raises the metabolic cost", OutbreakSymptoms.exhaustionMultiplier(player) > 1f)
        player.setAttached(OutbreakAttachments.DATA, minerals())
        check("a body with everything in band pays nothing extra", OutbreakSymptoms.exhaustionMultiplier(player) == 1f)
    }

    private fun thermalSymptoms(player: ServerPlayer) {
        // --- the tiers themselves. 38.5 is deliberate: 38.0 is reachable without an infection.
        check("37.5 is comfortable", OutbreakData.HEALTHY.copy(temperature = 37.5f).thermalTier == 0)
        check("38.0 is still comfortable", OutbreakData.HEALTHY.copy(temperature = 38f).thermalTier == 0)
        check("38.5 is where the fever starts", OutbreakData.HEALTHY.copy(temperature = 38.5f).thermalTier == 1)
        check("39.9 is still the first fever tier", OutbreakData.HEALTHY.copy(temperature = 39.9f).thermalTier == 1)
        check("40.0 is the super-high tier", OutbreakData.HEALTHY.copy(temperature = 40f).thermalTier == 2)
        check("36.0 is the first cold tier", OutbreakData.HEALTHY.copy(temperature = 36f).thermalTier == -1)
        check("35.0 is the second cold tier", OutbreakData.HEALTHY.copy(temperature = 35f).thermalTier == -2)

        // --- a fever: distortion on screen, weakness and mining fatigue in the body
        val fever = OutbreakData.HEALTHY.copy(temperature = 39f)
        expect(player, "a fever causes weakness", fever, MobEffects.WEAKNESS)
        expect(player, "a fever causes mining fatigue", fever, MobEffects.MINING_FATIGUE)
        applyWith(player, fever)
        check("a fever does not cause slowness", !player.hasEffect(MobEffects.SLOWNESS))
        check("a fever burns food faster", OutbreakSymptoms.exhaustionMultiplier(player) > 1f)

        // --- just below the fever band there is nothing at all
        applyWith(player, OutbreakData.HEALTHY.copy(temperature = 38.4f))
        check("a warm but not feverish body gets no effects", player.activeEffects.isEmpty())

        // --- hypothermia: the same, plus slowness
        val cold = OutbreakData.HEALTHY.copy(temperature = 35.5f)
        expect(player, "hypothermia causes weakness", cold, MobEffects.WEAKNESS)
        expect(player, "hypothermia causes mining fatigue", cold, MobEffects.MINING_FATIGUE)
        expect(player, "hypothermia also causes slowness", cold, MobEffects.SLOWNESS)

        // --- the stronger tier really is stronger
        applyWith(player, OutbreakData.HEALTHY.copy(temperature = 40.5f))
        val superHigh = player.getEffect(MobEffects.WEAKNESS)?.amplifier ?: -1
        applyWith(player, fever)
        val mildFever = player.getEffect(MobEffects.WEAKNESS)?.amplifier ?: -1
        logger.info("PHYS weakness amplifier: 40.5 C -> {} 39.0 C -> {}", superHigh, mildFever)
        check("a super-high fever is worse than a mild one", superHigh > mildFever)
    }

    /**
     * The regression test for "my vitals are normal but my view is still moving about".
     *
     * The camera tremor is the only thing in the mod that touches the view, so it now has to be
     * earned: every state that can produce one also puts an effect icon on the screen, which means
     * the player can always tell why. The two cases that used to break that rule were a
     * temperature of 38 (reachable from a hot biome and a thyroid that runs hot, with no illness
     * anywhere) and over-hydration that the ten cell thirst bar cannot distinguish from "full".
     */
    private fun shakeOnlyWhenSomethingIsVisible(player: ServerPlayer) {
        val healthy = OutbreakData.HEALTHY
        logger.info(
            "PHYS shake chance: healthy {} at 38.0 {} at 38.5 {} at 40 {}",
            OutbreakSymptoms.shakeChance(healthy),
            OutbreakSymptoms.shakeChance(healthy.copy(temperature = 38f)),
            OutbreakSymptoms.shakeChance(healthy.copy(temperature = 38.5f)),
            OutbreakSymptoms.shakeChance(healthy.copy(temperature = 40f)),
        )
        check("a healthy body never shakes the camera", OutbreakSymptoms.shakeChance(healthy) == 0f)
        check("a warm body never shakes the camera", OutbreakSymptoms.shakeChance(healthy.copy(temperature = 38f)) == 0f)
        check("the fever tier starts shaking it", OutbreakSymptoms.shakeChance(healthy.copy(temperature = 38.5f)) > 0f)
        check(
            "a super-high fever shakes it harder",
            OutbreakSymptoms.shakeChance(healthy.copy(temperature = 40f)) >
                OutbreakSymptoms.shakeChance(healthy.copy(temperature = 38.5f)),
        )

        // The thirst bar is ten cells wide, so 101 and 149 both just read as "full".
        check(
            "over-hydration behind a full thirst bar does not shake the camera",
            OutbreakSymptoms.shakeChance(healthy.copy(water = 149f)) == 0f,
        )
        check(
            "but over-hydration the player can see (nausea) does",
            OutbreakSymptoms.shakeChance(healthy.copy(water = 151f)) > 0f,
        )

        // A hot biome and a thyroid that runs hot, together, still land short of the fever band.
        var warm = OutbreakData.HEALTHY.copy(traceElements = TraceElements(155f))
        val desert = OutbreakSymptoms.environmentTemperature(2.0f)
        repeat(12_000) { warm = OutbreakPhysiology.tick(warm, desert) }
        logger.info("PHYS hottest a healthy body gets: {} (tier {})", warm.temperature, warm.thermalTier)
        check("a hot biome plus a hot thyroid is not a fever", warm.thermalTier == 0)
        check("and it asks for no screen effect", OutbreakSymptoms.shakeChance(warm) == 0f)

        applyWith(player, warm)
        check("so it puts nothing on the screen", player.postEffects.isEmpty())
        // The only effects left are the iodine excess this case is built out of, which has its own
        // icons (hunger, nausea, weakness). Mining fatigue is what the fever tier would have added.
        check("and no symptom the temperature is responsible for", !player.hasEffect(MobEffects.MINING_FATIGUE))
    }

    /**
     * The screen effects. The server only publishes ids; the client loads
     * `assets/outbreak/post_effect/<id>.json` itself, so this checks which ids are asked for, that
     * the right ones stack, and - most importantly - that they are taken away again.
     */
    private fun postEffects(player: ServerPlayer) {
        applyWith(player, OutbreakData.HEALTHY.copy(temperature = 38f))
        check("a warm body at 38.0 asks for nothing", player.postEffects.isEmpty())

        applyWith(player, OutbreakData.HEALTHY.copy(temperature = 39f))
        check("a fever asks for the heat haze", player.postEffects.contains(OutbreakSymptoms.HEAT_HAZE))
        check("a fever this mild does not ask for the motion blur", !player.postEffects.contains(OutbreakSymptoms.HEAT_BLUR))
        check("a fever does not ask for the cold shiver", !player.postEffects.contains(OutbreakSymptoms.COLD_SHIVER))

        applyWith(player, OutbreakData.HEALTHY.copy(temperature = 40.5f))
        check("a super-high fever keeps the heat haze", player.postEffects.contains(OutbreakSymptoms.HEAT_HAZE))
        check("and adds the motion blur on top", player.postEffects.contains(OutbreakSymptoms.HEAT_BLUR))
        check("exactly the two heat effects", player.postEffects.size == 2)

        applyWith(player, OutbreakData.HEALTHY.copy(temperature = 34.5f))
        check("hypothermia asks for the cold shiver", player.postEffects.contains(OutbreakSymptoms.COLD_SHIVER))
        check("hypothermia asks for neither heat effect", player.postEffects.size == 1)

        applyWith(player, OutbreakData.HEALTHY)
        check("a comfortable body asks for none of them", player.postEffects.isEmpty())

        // An id must not be re-sent while it is already applied.
        applyWith(player, OutbreakData.HEALTHY.copy(temperature = 40.5f))
        val once = player.postEffects.size
        applyWith(player, OutbreakData.HEALTHY.copy(temperature = 40.5f))
        check("each effect is only requested once", once == 2 && player.postEffects.size == 2)

        // Falling back out of the top tier has to take the blur away and leave the haze.
        applyWith(player, OutbreakData.HEALTHY.copy(temperature = 39f))
        check("dropping below 40 clears the blur only", player.postEffects == listOf(OutbreakSymptoms.HEAT_HAZE))

        OutbreakSymptoms.clearPostEffects(player)
        check("cure takes every effect off the screen", player.postEffects.isEmpty())
    }

    /**
     * Sea water is not dirty water. It must produce no immediate effect whatsoever - its damage is
     * the sodium it carries, and that arrives later through the model.
     */
    private fun seaWaterIsNotFoul(level: ServerLevel, player: ServerPlayer) {
        check("swamp water is foul", OutbreakIngestion.isFoulWater(OutbreakItems.SWAMP_WATER_BOTTLE))
        check("sea water is not", !OutbreakIngestion.isFoulWater(OutbreakItems.SEA_WATER_BOTTLE))
        check("salted sea water is not either", !OutbreakIngestion.isFoulWater(OutbreakItems.SALT_SEA_WATER))
        check("salted swamp water still is", OutbreakIngestion.isFoulWater(OutbreakItems.SALT_SWAMP_WATER))

        var infections = 0
        var nausea = 0
        var poison = 0
        val trials = 600
        for (i in 0 until trials) {
            player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
            player.removeAllEffects()
            ItemStack(OutbreakItems.SEA_WATER_BOTTLE, 1).finishUsingItem(level, player)
            if (player.getAttachedOrCreate(OutbreakAttachments.DATA).bacteria > 0f) infections++
            if (player.hasEffect(MobEffects.NAUSEA)) nausea++
            if (player.hasEffect(MobEffects.POISON)) poison++
        }
        logger.info(
            "PHYS sea water over {} drinks: infection {} nausea {} poison {}",
            trials, infections, nausea, poison,
        )
        check("sea water never infects", infections == 0)
        check("sea water never causes nausea", nausea == 0)
        check("sea water never poisons", poison == 0)

        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        ItemStack(OutbreakItems.SEA_WATER_BOTTLE, 1).finishUsingItem(level, player)
        val after = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        logger.info(
            "PHYS one bottle of sea water: water {} sodium {} magnesium {}",
            after.water, after.electrolytes.sodium, after.electrolytes.magnesium,
        )
        check("sea water still hydrates", abs(after.water - 95f) < 0.01f)
        check("sea water is a sodium load in its own right", after.electrolytes.sodium >= 118f)
        check("and it carries the magnesium the sea has", after.electrolytes.magnesium > 100f)
    }

    /** Runs one real server tick for [player] against [data], from a clean slate. */
    private fun applyWith(player: ServerPlayer, data: OutbreakData) {
        player.removeAllEffects()
        player.setAttached(OutbreakAttachments.DATA, data)
        // The effect pass runs on a cadence; a fresh runtime sits on it.
        player.setAttached(OutbreakAttachments.RUNTIME, OutbreakRuntime())
        OutbreakSymptoms.tick(player)
    }

    /** Applies [data] and asserts that [effect] landed on the player. */
    private fun expect(player: ServerPlayer, label: String, data: OutbreakData, effect: Holder<MobEffect>) {
        applyWith(player, data)
        check(label, player.hasEffect(effect))
    }

    /** Builds healthy data with specific mineral values, everything else at normal. */
    private fun minerals(
        sodium: Float = Electrolytes.NORMAL,
        potassium: Float = Electrolytes.NORMAL,
        magnesium: Float = Electrolytes.NORMAL,
        chloride: Float = Electrolytes.NORMAL,
        calcium: Float = Electrolytes.NORMAL,
        iodine: Float = MineralScale.NORMAL,
    ): OutbreakData = OutbreakData.HEALTHY.copy(
        electrolytes = Electrolytes(sodium, potassium, magnesium, chloride, calcium),
        traceElements = TraceElements(iodine),
    )

    /** The grindstone table, and the mixin entry points that read it. */
    private fun grinding(level: ServerLevel) {
        val bark = ItemStack(OutbreakItems.WILLOW_BARK, 1)
        val ore = ItemStack(OutbreakBlocks.ROCK_SALT_ORE.asItem(), 1)
        val salt = ItemStack(OutbreakItems.CRUDE_SALT, 1)
        val bread = ItemStack(Items.BREAD, 1)
        val empty = ItemStack.EMPTY

        check("the grindstone accepts one willow bark", OutbreakGrinding.mayPlace(bark))
        check("the grindstone accepts one rock salt ore", OutbreakGrinding.mayPlace(ore))
        check("the grindstone accepts one crude salt", OutbreakGrinding.mayPlace(salt))
        check("the grindstone still rejects unrelated items", !OutbreakGrinding.mayPlace(bread))
        check("the grindstone refuses a stack, which onTake would swallow", !OutbreakGrinding.mayPlace(ItemStack(OutbreakItems.WILLOW_BARK, 2)))

        check("one bark grinds into two pieces", OutbreakGrinding.resultFor(bark, empty).`is`(OutbreakItems.WILLOW_BARK_PIECES) && OutbreakGrinding.resultFor(bark, empty).count == 2)
        check("one rock salt ore grinds into nine crude salt", OutbreakGrinding.resultFor(ore, empty).`is`(OutbreakItems.CRUDE_SALT) && OutbreakGrinding.resultFor(ore, empty).count == 9)
        check("one crude salt grinds into one crude salt powder", OutbreakGrinding.resultFor(salt, empty).`is`(OutbreakItems.CRUDE_SALT_POWDER))
        check("a plain item still produces nothing", OutbreakGrinding.resultFor(bread, empty).isEmpty)
        check("two items in the slots produce nothing", OutbreakGrinding.resultFor(bark, salt).isEmpty)
        check("a stack produces nothing", OutbreakGrinding.resultFor(ItemStack(OutbreakItems.WILLOW_BARK, 5), empty).isEmpty)

        // The strongest check: drive a real GrindstoneMenu, which only works if both mixins
        // (the anonymous input slots and computeResult) actually applied.
        val player = FakePlayer.get(level)
        val menu = GrindstoneMenu(0, player.inventory)
        check("a real grindstone slot accepts willow bark", menu.getSlot(0).mayPlace(bark))
        check("a real grindstone slot rejects bread", !menu.getSlot(0).mayPlace(bread))
        menu.getSlot(0).set(bark)
        val produced = menu.getSlot(2).item
        logger.info("PHYS real grindstone result: {} x{}", produced.item, produced.count)
        check(
            "a real grindstone slot produces two bark pieces",
            produced.`is`(OutbreakItems.WILLOW_BARK_PIECES) && produced.count == 2,
        )
    }

    // ================================================================== translations

    /**
     * Every item and entity the mod registers has to have a name in every language file.
     *
     * This is not cosmetic. `item.outbreak.willow_sign` shipped without a translation, so the sign
     * showed up in the inventory as its raw translation key: the standing sign was registered
     * without `useBlockDescriptionPrefix()` while its hanging twin had it, and the name lives under
     * `block.outbreak.willow_sign` (which is also where vanilla keeps its own signs).
     *
     * The language files are read straight off the classpath, which is exactly what the client
     * loads, so this fails the moment a key is renamed or an item is added without one.
     */
    private fun translationChecks() {
        val languages = listOf("en_us", "zh_cn")
        val names = languages.associateWith(::languageKeys)

        val missingItems = mutableListOf<String>()
        val missingEntities = mutableListOf<String>()
        for ((language, keys) in names) {
            missingItems += untranslated(BuiltInRegistries.ITEM, keys) { it.descriptionId }.map { "$it ($language)" }
            missingEntities += untranslated(BuiltInRegistries.ENTITY_TYPE, keys) { it.descriptionId }
                .map { "$it ($language)" }
        }
        logger.info(
            "PHYS translations: {} languages, missing item names {} missing entity names {}",
            languages, missingItems, missingEntities,
        )

        check("every outbreak item is named in en_us and zh_cn", missingItems.isEmpty())
        check("every outbreak entity is named in en_us and zh_cn", missingEntities.isEmpty())
        check(
            "the standing sign takes its name from the block, like vanilla's does",
            OutbreakItems.WILLOW_SIGN.descriptionId == "block.${Outbreak.MOD_ID}.willow_sign",
        )
    }

    /** The keys of one shipped language file. */
    private fun languageKeys(language: String): Set<String> {
        val path = "/assets/${Outbreak.MOD_ID}/lang/$language.json"
        val text = javaClass.getResourceAsStream(path)?.use { it.reader().readText() }
            ?: error("$path is not on the classpath")
        return JsonParser.parseString(text).asJsonObject.keySet()
    }

    /** Names of every entry of [registry] in this mod's namespace that [keys] does not define. */
    private fun <T : Any> untranslated(registry: Registry<T>, keys: Set<String>, name: (T) -> String): List<String> =
        registry.keySet()
            .filter { it.namespace == Outbreak.MOD_ID }
            .mapNotNull { registry.getValue(it) }
            .map(name)
            .filterNot { it in keys }

    // ================================================================== mixins

    private fun mixinChecks(level: ServerLevel) {
        val player = FakePlayer.get(level)

        val meatInfections = countInfections(level, player, Items.BEEF, 400)
        logger.info("PHYS raw meat infections: {}/400", meatInfections)
        check("eating raw meat infects about 30% of the time", meatInfections in 90..150)

        val fleshInfections = countInfections(level, player, Items.ROTTEN_FLESH, 400)
        logger.info("PHYS rotten flesh infections: {}/400", fleshInfections)
        check("rotten flesh is a risky food too", fleshInfections in 90..150)

        val potatoInfections = countInfections(level, player, Items.POISONOUS_POTATO, 400)
        check("poisonous potatoes are a risky food too", potatoInfections in 90..150)

        val breadInfections = countInfections(level, player, Items.BREAD, 400)
        check("safe food never infects", breadInfections == 0)

        // Every drink listed in the spec must add 40 water.
        for (item in listOf(
            Items.POTION,
            Items.MUSHROOM_STEW,
            OutbreakItems.RAW_WILLOW_BARK_SOUP_BOWL,
            OutbreakItems.WILLOW_BARK_SOUP_BOWL,
            OutbreakItems.CRUDE_SALT_WATER,
            OutbreakItems.SALT_WATER,
            OutbreakItems.CRUDE_SALT_MUSHROOM_STEW,
            OutbreakItems.SALT_MUSHROOM_STEW,
            OutbreakItems.CRUDE_SALT_WILLOW_BARK_SOUP,
            OutbreakItems.SALT_WILLOW_BARK_SOUP,
            OutbreakItems.CRUDE_SALT_RAW_WILLOW_BARK_SOUP,
            OutbreakItems.SALT_RAW_WILLOW_BARK_SOUP,
        )) {
            player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
            ItemStack(item, 1).finishUsingItem(level, player)
            val after = player.getAttachedOrCreate(OutbreakAttachments.DATA)
            check("${item.descriptionId} adds water", kotlin.math.abs(after.water - 95f) < 0.01f)
        }

        // The biome waters and their salted versions all hydrate and salt the player.
        for (item in listOf(
            OutbreakItems.SWAMP_WATER_BOTTLE,
            OutbreakItems.SEA_WATER_BOTTLE,
            OutbreakItems.CRUDE_SALT_SWAMP_WATER,
            OutbreakItems.SALT_SWAMP_WATER,
            OutbreakItems.CRUDE_SALT_SEA_WATER,
            OutbreakItems.SALT_SEA_WATER,
        )) {
            player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
            ItemStack(item, 1).finishUsingItem(level, player)
            val after = player.getAttachedOrCreate(OutbreakAttachments.DATA)
            check("${item.descriptionId} adds water", kotlin.math.abs(after.water - 95f) < 0.01f)
        }

        // The salted biome waters add sodium; the crude ones carry the extra minerals too.
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        ItemStack(OutbreakItems.SALT_SEA_WATER, 1).finishUsingItem(level, player)
        val saltedSea = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        ItemStack(OutbreakItems.CRUDE_SALT_SEA_WATER, 1).finishUsingItem(level, player)
        val crudeSea = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        logger.info(
            "PHYS sodium from sea water: refined {} crude {}; magnesium {} / {}",
            saltedSea.electrolytes.sodium, crudeSea.electrolytes.sodium,
            saltedSea.electrolytes.magnesium, crudeSea.electrolytes.magnesium,
        )
        check("salted sea water raises sodium", saltedSea.electrolytes.sodium > Electrolytes.NORMAL)
        check("crude salted sea water carries the extra minerals", crudeSea.electrolytes.magnesium > saltedSea.electrolytes.magnesium)

        // Foul water must be able to inflict all four effects. Run enough trials that a 5% roll
        // is expected to fire.
        var infections = 0
        var nausea = 0
        var poison = 0
        val trials = 600
        for (i in 0 until trials) {
            player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
            player.removeAllEffects()
            ItemStack(OutbreakItems.SWAMP_WATER_BOTTLE, 1).finishUsingItem(level, player)
            if (player.getAttachedOrCreate(OutbreakAttachments.DATA).bacteria > 0f) infections++
            if (player.hasEffect(MobEffects.NAUSEA)) nausea++
            if (player.hasEffect(MobEffects.POISON)) poison++
        }
        logger.info(
            "PHYS swamp water over {} drinks: infection {} nausea {} poison {}",
            trials, infections, nausea, poison,
        )
        check("swamp water infects about 30% of the time", infections in 140..220)
        check("swamp water causes nausea about 35% of the time", nausea in 170..250)
        check("swamp water poisons about 5% of the time", poison in 10..60)

        // Kelp is the only iodine source.
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY.copy(
            traceElements = TraceElements.HEALTHY.copy(iodine = 40f),
        ))
        ItemStack(Items.KELP, 1).finishUsingItem(level, player)
        val afterKelp = player.getAttachedOrCreate(OutbreakAttachments.DATA).traceElements.iodine
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY.copy(
            traceElements = TraceElements.HEALTHY.copy(iodine = 40f),
        ))
        ItemStack(Items.DRIED_KELP, 1).finishUsingItem(level, player)
        val afterDried = player.getAttachedOrCreate(OutbreakAttachments.DATA).traceElements.iodine
        logger.info("PHYS iodine from kelp {} / dried kelp {}", afterKelp, afterDried)
        check("kelp adds 25 iodine", kotlin.math.abs(afterKelp - 65f) < 0.01f)
        check("dried kelp adds 35 iodine", kotlin.math.abs(afterDried - 75f) < 0.01f)

        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        ItemStack(Items.BREAD, 1).finishUsingItem(level, player)
        check("bread adds no iodine", player.getAttachedOrCreate(OutbreakAttachments.DATA).traceElements.iodine == TraceElements.HEALTHY.iodine)

        // Salicin from soup, dexamethasone and salt from the salted variants.
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        ItemStack(OutbreakItems.WILLOW_BARK_SOUP_BOWL, 1).finishUsingItem(level, player)
        check("cooked soup raises salicin to the effective level", player.getAttachedOrCreate(OutbreakAttachments.DATA).salicin >= OutbreakData.SALICIN_EFFECTIVE)

        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        ItemStack(OutbreakItems.SALT_WATER, 1).finishUsingItem(level, player)
        val refined = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        ItemStack(OutbreakItems.CRUDE_SALT_WATER, 1).finishUsingItem(level, player)
        val crude = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        logger.info(
            "PHYS sodium from salt water: refined {} crude {}; magnesium refined {} crude {}",
            refined.electrolytes.sodium, crude.electrolytes.sodium,
            refined.electrolytes.magnesium, crude.electrolytes.magnesium,
        )
        check("refined salt water raises sodium", refined.electrolytes.sodium > Electrolytes.NORMAL)
        check("crude salt water carries the extra minerals", crude.electrolytes.magnesium > refined.electrolytes.magnesium)

        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        OutbreakIngestion.injectDexamethasone(player)
        check("an injection raises dexamethasone", player.getAttachedOrCreate(OutbreakAttachments.DATA).dexamethasone > 0f)

        // PlayerMixin scales food exhaustion.
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        val healthyMultiplier = OutbreakSymptoms.exhaustionMultiplier(player)
        player.setAttached(OutbreakAttachments.DATA, withInflammation(60f).copy(bacteria = 55f))
        val illMultiplier = OutbreakSymptoms.exhaustionMultiplier(player)
        logger.info("PHYS exhaustion multiplier: healthy {} ill {}", healthyMultiplier, illMultiplier)
        check("a healthy player has no exhaustion penalty", healthyMultiplier == 1f)
        check("an ill player burns food faster", illMultiplier > 1.25f)

        // The mining penalty rides on BLOCK_BREAK_SPEED, which vanilla multiplies unconditionally.
        OutbreakSymptoms.tick(player)
        val illMining = player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED)
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        OutbreakSymptoms.tick(player)
        val healthyMining = player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED)
        logger.info("PHYS mining speed: ill {} healthy {}", illMining, healthyMining)
        check("an infection slows mining down", illMining < 1.0)

        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY.copy(water = 150f))
        OutbreakSymptoms.tick(player)
        val overhydratedMining = player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED)
        logger.info("PHYS mining speed over-hydrated {}", overhydratedMining)
        check("over-hydration slows mining down", overhydratedMining < 1.0)

        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        OutbreakSymptoms.tick(player)
        check("mining speed returns to normal once healthy", player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED) == 1.0)

        // The client state carries the thirst bar.
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY.copy(water = 55f))
        OutbreakSymptoms.tick(player)
        val synced = player.getAttachedOrElse(OutbreakAttachments.CLIENT, OutbreakClientState.INACTIVE)
        logger.info("PHYS synced water {}", synced.water)
        check("the client is told the water level", synced.water in 50..60)
    }

    private fun countInfections(level: ServerLevel, player: ServerPlayer, item: Item, trials: Int): Int {
        var infections = 0
        repeat(trials) {
            player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
            ItemStack(item, 64).finishUsingItem(level, player)
            if (player.getAttachedOrCreate(OutbreakAttachments.DATA).bacteria > 0f) {
                infections++
            }
        }
        return infections
    }

    /** Builds data whose inflammation index is [inflammation], by scaling the cytokine load. */
    private fun withInflammation(inflammation: Float): OutbreakData {
        val base = OutbreakData.HEALTHY
        val extra = (inflammation - base.inflammation) / Mediators.CYTOKINE_WEIGHT
        return base.copy(
            mediators = base.mediators.copy(cytokine = (base.mediators.cytokine + extra).coerceIn(0f, Mediators.MAX)),
        )
    }

    private fun check(name: String, ok: Boolean) {
        if (ok) {
            passed++
            logger.info("PHYSIOLOGY SELFTEST PASS: {}", name)
        } else {
            failed++
            logger.error("PHYSIOLOGY SELFTEST FAIL: {}", name)
        }
    }
}
