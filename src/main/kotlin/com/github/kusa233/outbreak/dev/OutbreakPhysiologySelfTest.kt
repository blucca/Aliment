package com.github.kusa233.outbreak.dev

import com.github.kusa233.outbreak.Outbreak
import com.github.kusa233.outbreak.physiology.Electrolytes
import com.github.kusa233.outbreak.physiology.Mediators
import com.github.kusa233.outbreak.physiology.Mineral
import com.github.kusa233.outbreak.physiology.OutbreakAttachments
import com.github.kusa233.outbreak.physiology.OutbreakClientState
import com.github.kusa233.outbreak.physiology.OutbreakData
import com.github.kusa233.outbreak.physiology.OutbreakInfection
import com.github.kusa233.outbreak.physiology.OutbreakIngestion
import com.github.kusa233.outbreak.physiology.OutbreakPhysiology
import com.github.kusa233.outbreak.physiology.OutbreakRuntime
import com.github.kusa233.outbreak.physiology.OutbreakSymptoms
import com.github.kusa233.outbreak.physiology.TraceElements
import com.github.kusa233.outbreak.registry.OutbreakBlocks
import com.github.kusa233.outbreak.registry.OutbreakItems
import com.github.kusa233.outbreak.world.OutbreakGrinding
import com.github.kusa233.outbreak.world.OutbreakLoot
import com.google.gson.JsonParser
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.FakePlayer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.inventory.GrindstoneMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
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
                chestLoot(server.overworld())
                creativeIsFrozen(server.overworld())
                // Last, because it makes the server hand out a second player object.
                deathResetsTheBody(server.overworld())
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
        iodineDepletion()
        anticholinergics()
        psilocybinAndPsilocin()
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
        repeat(72_000) { tick ->
            data = OutbreakPhysiology.tick(data)
            // The one thing a player has to keep doing: iodine has no homeostat, so a day's worth of
            // kelp goes in every day. Everything else in this test is "do nothing at all".
            if (tick % 24_000 == 0) {
                data = OutbreakPhysiology.iodine(data, 0.15f)
            }
            min = minOf(min, data.inflammation)
            max = maxOf(max, data.inflammation)
        }
        logger.info(
            "PHYS homeostasis over 3 days (one day's kelp a day): inflammation {}..{} electrolytes {} water {} temperature {}",
            min, max, data.electrolytes, data.water, data.temperature,
        )
        check("homeostasis keeps inflammation inside 20..30", min >= 20f && max <= 30f)
        val e = data.electrolytes
        check(
            "homeostasis keeps every electrolyte inside a percent of normal",
            Electrolytes.MINERALS.all { abs(e.of(it) - it.normal) < 0.01f * it.normal },
        )
        check("homeostasis keeps the core temperature at 37", abs(data.temperature - OutbreakData.TEMPERATURE_NORMAL) < 0.01f)
        check("and the thyroid is happy", !data.hasTraceElementImbalance)
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
        check("heavy drinking dilutes sodium", data.electrolytes.sodium < Mineral.SODIUM.safeLow)
        check("heavy drinking dilutes chloride", data.electrolytes.chloride < Mineral.CHLORIDE.safeLow)
        check(
            "the dilution is severe but it is a number a person could have",
            data.electrolytes.sodium > 110f,
        )
        check(
            "magnesium is flushed more slowly than sodium",
            Mineral.MAGNESIUM.relativeDeviation(data.electrolytes.magnesium) <
                Mineral.SODIUM.relativeDeviation(data.electrolytes.sodium),
        )
    }

    private fun dehydrationStopsWhenDrinking() {
        var data = OutbreakData.HEALTHY.copy(water = 20f)
        check("low water is flagged as dehydration", data.isDehydrated)
        data = OutbreakPhysiology.drink(data)
        check("one drink adds 15 water", kotlin.math.abs(data.water - 35f) < 0.01f)
    }

    /**
     * Iodine is the one mineral the body cannot make and does not conserve: what is not eaten is
     * lost. A full store - normal 0.50 down to the hard floor 0.05 - is drained in exactly three
     * in-game days, and then it stays on the floor until kelp puts something back.
     */
    private fun iodineDepletion() {
        val iodine = Mineral.IODINE
        val floor = iodine.min
        val halfway = (iodine.normal + floor) / 2f

        var data = OutbreakData.HEALTHY
        repeat(36_000) { data = OutbreakPhysiology.tick(data) }
        logger.info("PHYS iodine after a day and a half with no kelp: {}", data.traceElements.iodine)
        check("half the store is gone after half of the three days", abs(data.traceElements.iodine - halfway) < 0.01f)

        repeat(36_000) { data = OutbreakPhysiology.tick(data) }
        logger.info("PHYS iodine after three days with no kelp: {} (floor {})", data.traceElements.iodine, floor)
        check("three game days empty the store", abs(data.traceElements.iodine - floor) < 0.001f)
        check("an empty store is severe iodine deficiency", data.traceElements.iodine < iodine.severeLow)
        check("and it is reported as an imbalance", data.hasTraceElementImbalance)
        check("the thyroid follows it down", OutbreakPhysiology.targetTemperature(data) < 36.5f)

        repeat(24_000) { data = OutbreakPhysiology.tick(data) }
        check("a fourth day does not drain it any further", abs(data.traceElements.iodine - floor) < 0.001f)
        check("the five electrolytes are unaffected", !data.hasElectrolyteImbalance)
        check("iodine is not part of the electrolyte set", Electrolytes.MINERALS.none { it == Mineral.IODINE })

        // Kelp is the only way back. The leak costs 0.15 umol/L a day, so a wet kelp (0.10) is not a
        // day's worth and two of them are.
        val oneKelp = OutbreakPhysiology.iodine(data, 0.10f)
        logger.info("PHYS iodine after one kelp from an empty store: {}", oneKelp.traceElements.iodine)
        check("one kelp lifts an empty store off the floor", oneKelp.traceElements.iodine > floor + 0.05f)
        check("but one kelp is less than a day's loss", oneKelp.traceElements.iodine < floor + 0.15f)

        var oneADay = OutbreakData.HEALTHY
        var twoADay = OutbreakData.HEALTHY
        repeat(4 * 24_000) { tick ->
            oneADay = OutbreakPhysiology.tick(oneADay)
            twoADay = OutbreakPhysiology.tick(twoADay)
            if (tick % 24_000 == 0) {
                oneADay = OutbreakPhysiology.iodine(oneADay, 0.10f)
                twoADay = OutbreakPhysiology.iodine(twoADay, 0.10f)
                twoADay = OutbreakPhysiology.iodine(twoADay, 0.10f)
            }
        }
        logger.info(
            "PHYS iodine after four days: one kelp a day {} two a day {}",
            oneADay.traceElements.iodine, twoADay.traceElements.iodine,
        )
        check("one kelp a day does not hold the reference range", oneADay.hasTraceElementImbalance)
        check("two kelp a day does", !twoADay.hasTraceElementImbalance)

        val tooMuch = OutbreakPhysiology.iodine(OutbreakData.HEALTHY, 0.45f)
        logger.info("PHYS iodine after three helpings of kelp: {}", tooMuch.traceElements.iodine)
        check("too much kelp pushes iodine into excess", tooMuch.traceElements.direction > 0)
        check("an iodine excess is reported as an imbalance", tooMuch.hasTraceElementImbalance)

        // Nothing stores it: the surplus is drained at the same rate as the rest, all the way down.
        var recovered = tooMuch
        repeat(6 * 24_000) { recovered = OutbreakPhysiology.tick(recovered) }
        check(
            "an iodine excess is drained away rather than stored",
            abs(recovered.traceElements.iodine - floor) < 0.001f,
        )

        // A sustained fever sweats iodine out on top of the leak. The temperature is pinned every tick
        // because a fever that is not fed by an infection or a pyrogen would otherwise break within a
        // minute.
        var sober = OutbreakData.HEALTHY
        var feverish = OutbreakData.HEALTHY.copy(temperature = 41f)
        repeat(12_000) {
            sober = OutbreakPhysiology.tick(sober)
            feverish = OutbreakPhysiology.tick(feverish.copy(temperature = 41f))
        }
        logger.info(
            "PHYS iodine after half a day: at 37 {} at 41 {}",
            sober.traceElements.iodine, feverish.traceElements.iodine,
        )
        check("a fever drains iodine faster than the leak alone", feverish.traceElements.iodine < sober.traceElements.iodine)
    }

    // ================================================================== temperature

    /** Nothing but a missed kelp should move a healthy body off 37 degrees. */
    private fun temperatureHomeostasis() {
        var data = OutbreakData.HEALTHY
        var min = data.temperature
        var max = data.temperature
        repeat(24_000) {
            data = OutbreakPhysiology.tick(data)
            // Iodine has no homeostat any more, and the thyroid reads it: without a day's kelp the
            // set point sinks and this stops being a test of thermoregulation. See [iodineDepletion].
            if (it % 24_000 == 0) {
                data = OutbreakPhysiology.iodine(data, 0.15f)
            }
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
        val iodine = Mineral.IODINE
        // A deficit below the reference range, and an excess above it, both realistic values.
        var hypothyroid = OutbreakData.HEALTHY.copy(traceElements = TraceElements(0.30f))
        var hyperthyroid = OutbreakData.HEALTHY.copy(traceElements = TraceElements(1.10f))
        repeat(12_000) {
            hypothyroid = OutbreakPhysiology.tick(hypothyroid)
            hyperthyroid = OutbreakPhysiology.tick(hyperthyroid)
        }
        logger.info(
            "PHYS resting temperature with iodine {} (range {}..{}): {} / with {}: {}",
            hypothyroid.traceElements.iodine, iodine.safeLow, iodine.safeHigh, hypothyroid.temperature,
            hyperthyroid.traceElements.iodine, hyperthyroid.temperature,
        )
        check("hypothyroidism lowers the resting temperature", hypothyroid.temperature < 37f)
        check("thyrotoxicosis raises it", hyperthyroid.temperature > 37f)
        check("neither is a fever on its own", !hyperthyroid.isFebrile && !hypothyroid.isHypothermic)
    }

    // ================================================================== anticholinergics

    /**
     * The mandrake alkaloids: scopolamine for the delirium, atropine for the fever it drags along.
     *
     * A fruit is a full dose of scopolamine and a tenth of atropine; the seeds are three quarters of
     * one and the same tenth. Both are capped at 5 and cleared over a game day, so one fruit is a
     * trip that wears off and a stack of them is an overdose the player has to wait out.
     *
     * Past 1.5 combined the body runs a temperature - 38, then 39.5, then 41 - and past 2.3 of either
     * one, or 2.7 of the two, the sight blurs. None of it goes through prostaglandin, so it is a set
     * point shift that *stacks* with an infection's fever rather than replacing it.
     */
    private fun anticholinergics() {
        var data = OutbreakData.HEALTHY
        data = OutbreakPhysiology.anticholinergic(data, 1.0f, 0.1f)
        logger.info(
            "PHYS one mandrake fruit: scopolamine {} atropine {} load {}",
            data.scopolamine, data.atropine, data.anticholinergicLoad,
        )
        check(
            "one fruit is a full dose of scopolamine and a tenth of atropine",
            abs(data.scopolamine - 1.0f) < 0.001f && abs(data.atropine - 0.1f) < 0.001f,
        )
        check("1.1 combined is below the fever threshold", abs(OutbreakPhysiology.targetTemperature(data) - 37f) < 0.001f)
        check("and a single fruit does not blur the sight", !data.isVisionBlurred)

        data = OutbreakPhysiology.anticholinergic(data, 1.0f, 0.1f)
        logger.info("PHYS two fruits: load {} target {}", data.anticholinergicLoad, OutbreakPhysiology.targetTemperature(data))
        check("two fruits start a fever", abs(OutbreakPhysiology.targetTemperature(data) - 38f) < 0.001f)
        check("and 2.1 is still short of the blur", !data.isVisionBlurred)

        data = OutbreakPhysiology.anticholinergic(data, 1.0f, 0.1f)
        check("three fruits step the fever up to 39.5", abs(OutbreakPhysiology.targetTemperature(data) - 39.5f) < 0.001f)
        check("and 3.1 combined blurs the sight", data.isVisionBlurred)

        // The seeds are a milder dose and a tenth of atropine, so they add up the same way.
        val fromSeeds = OutbreakPhysiology.anticholinergic(OutbreakData.HEALTHY, 0.75f, 0.1f)
        check(
            "the seeds are three quarters of a dose and the same atropine",
            abs(fromSeeds.scopolamine - 0.75f) < 0.001f && abs(fromSeeds.atropine - 0.1f) < 0.001f,
        )
        check("and one dose of seeds is not enough for either effect", !fromSeeds.isVisionBlurred && abs(OutbreakPhysiology.targetTemperature(fromSeeds) - 37f) < 0.001f)

        // Each alkaloid is capped on its own, so the pair can reach 10 between them.
        val capped = OutbreakPhysiology.anticholinergic(OutbreakData.HEALTHY, 99f, 99f)
        check(
            "both alkaloids are capped at five",
            abs(capped.scopolamine - OutbreakData.ANTICHOLINERGIC_CAP) < 0.001f &&
                abs(capped.atropine - OutbreakData.ANTICHOLINERGIC_CAP) < 0.001f,
        )
        check("and 10 combined drives the fever to 41", abs(OutbreakPhysiology.targetTemperature(capped) - 41f) < 0.001f)

        // Either one on its own is enough to blur, without the sum being anywhere near 2.7. The two
        // samples either side of the sum use 1.375, which is exact in binary, so the boundary is not
        // decided by a rounding error.
        val blindFromScopolamine = OutbreakData.HEALTHY.withScopolamine(2.3f)
        val blindFromAtropine = OutbreakData.HEALTHY.withAtropine(2.3f)
        val togetherBlind = OutbreakData.HEALTHY.withScopolamine(1.375f).withAtropine(1.375f)
        val togetherClear = OutbreakData.HEALTHY.withScopolamine(1.3f).withAtropine(1.3f)
        check("2.3 of scopolamine alone blurs the sight", blindFromScopolamine.isVisionBlurred)
        check("2.3 of atropine alone blurs it too", blindFromAtropine.isVisionBlurred)
        check("and so does 2.75 of the two together", togetherBlind.isVisionBlurred)
        check("but 2.6 of the two together is still clear", !togetherClear.isVisionBlurred)

        // A real overdose has to actually heat the body up, not merely move a set point.
        var overdosed = capped
        repeat(3_000) { overdosed = OutbreakPhysiology.tick(overdosed) }
        logger.info(
            "PHYS overdose after 3000 ticks: temperature {} load {}",
            overdosed.temperature, overdosed.anticholinergicLoad,
        )
        check("an overdose really heats the body", overdosed.temperature > 39f)
        check("and it is not a fever salicin can treat", OutbreakPhysiology.dose(overdosed, 3f).let {
            abs(OutbreakPhysiology.targetTemperature(it) - OutbreakPhysiology.targetTemperature(overdosed)) < 0.001f
        })

        // Nothing clears them but time: both are gone a game day later.
        var clearing = capped
        repeat(OutbreakData.ANTICHOLINERGIC_METABOLISM_TICKS) { clearing = OutbreakPhysiology.tick(clearing) }
        logger.info("PHYS after a game day: scopolamine {} atropine {}", clearing.scopolamine, clearing.atropine)
        check("a game day clears both alkaloids", clearing.scopolamine == 0f && clearing.atropine == 0f)
        check("and the sight clears with them", !clearing.isVisionBlurred)

        // The drug fever and an infection fever are separate terms, and they add up. The infection
        // here is a mild one on purpose: 37 + 1 + 4 lands exactly on the model's 42 degree ceiling,
        // so the sum can be read straight off the target instead of being clipped by it.
        val infected = OutbreakData.HEALTHY.copy(mediators = OutbreakData.HEALTHY.mediators.withProstaglandin(50f))
        val stacked = overdosed.withMediators(infected.mediators)
        logger.info(
            "PHYS fever terms: infection {} drug {} both {}",
            OutbreakPhysiology.targetTemperature(infected),
            OutbreakPhysiology.targetTemperature(overdosed),
            OutbreakPhysiology.targetTemperature(stacked),
        )
        check("a drug fever stacks on top of an infection fever", abs(OutbreakPhysiology.targetTemperature(stacked) - 42f) < 0.01f)
        check(
            "and each of them is milder on its own",
            OutbreakPhysiology.targetTemperature(stacked) > OutbreakPhysiology.targetTemperature(overdosed) &&
                OutbreakPhysiology.targetTemperature(stacked) > OutbreakPhysiology.targetTemperature(infected),
        )
    }

    // ================================================================== psilocybin

    /**
     * The gymnopilus compounds.
     *
     * A raw mushroom is a dose (1.3) of each. Psilocybin does nothing on its own - it has no
     * threshold, no tier and no fever - it simply becomes psilocin one for one over half a game day.
     * Psilocin is what the trip and the fever come from, and it leaves at a flat 1.3 a game day
     * rather than as a fraction of what is in the body, so the length of a trip is proportional to
     * how much was eaten: one mushroom is two days, five of them are ten.
     */
    private fun psilocybinAndPsilocin() {
        var data = OutbreakPhysiology.mushroom(OutbreakData.HEALTHY, 1.3f, 1.3f)
        logger.info(
            "PHYS one raw gymnopilus: psilocybin {} psilocin {} tier {}",
            data.psilocybin, data.psilocin, data.psilocinTier,
        )
        check("a raw mushroom is a dose of each compound", abs(data.psilocybin - 1.3f) < 0.001f && abs(data.psilocin - 1.3f) < 0.001f)
        check("1.3 is enough for the outlines and no more", data.psilocinTier == 1)
        check("and not enough for any fever", abs(OutbreakPhysiology.targetTemperature(data) - 37f) < 0.001f)

        // Half a game day in: half the prodrug has turned into psilocin, and psilocin is already
        // leaving at its own flat rate, so the level is above the 1.3 that was eaten.
        repeat(OutbreakData.PSILOCYBIN_METABOLISM_TICKS / 2) { data = OutbreakPhysiology.tick(data) }
        logger.info("PHYS half a day after one mushroom: psilocybin {} psilocin {}", data.psilocybin, data.psilocin)
        check("half the psilocybin is gone after half a game day", abs(data.psilocybin - 0.65f) < 0.02f)
        check("and it arrived as psilocin, against the psilocin leaving", data.psilocin > 1.55f)

        var day = OutbreakPhysiology.mushroom(OutbreakData.HEALTHY, 1.3f, 1.3f)
        repeat(OutbreakData.PSILOCIN_METABOLISM_TICKS) { day = OutbreakPhysiology.tick(day) }
        logger.info("PHYS a day after one mushroom: psilocybin {} psilocin {}", day.psilocybin, day.psilocin)
        check("a game day finishes the conversion", day.psilocybin == 0f)
        check("and half of the total psilocin is left", abs(day.psilocin - 1.3f) < 0.02f)

        repeat(OutbreakData.PSILOCIN_METABOLISM_TICKS) { day = OutbreakPhysiology.tick(day) }
        logger.info("PHYS two days after one mushroom: psilocin {}", day.psilocin)
        check("one mushroom is gone after two game days", day.psilocin < 0.02f)

        // Five mushrooms: thirteen units of psilocin in total, at a flat 1.3 a day.
        var heavy = OutbreakPhysiology.mushroom(OutbreakData.HEALTHY, 6.5f, 6.5f)
        var days = 0
        while (days < 15 && heavy.psilocin > 0f) {
            repeat(OutbreakData.PSILOCIN_METABOLISM_TICKS) { heavy = OutbreakPhysiology.tick(heavy) }
            days++
        }
        logger.info("PHYS five mushrooms took {} game days to clear", days)
        check("five mushrooms take ten game days at a flat 1.3 a day", days in 10..11)

        // Psilocybin on its own is inert, and it stays inert: it converts into psilocin at exactly the
        // rate psilocin is cleared, so on its own it can never build up enough to start a trip. What
        // it does is replace what is being cleared, which is why a raw mushroom lasts twice as long
        // as the psilocin in it would on its own.
        val prodrug = OutbreakData.HEALTHY.withPsilocybin(5f)
        check(
            "psilocybin on its own has no effect at all",
            prodrug.psilocinTier == 0 && abs(OutbreakPhysiology.targetTemperature(prodrug) - 37f) < 0.001f,
        )
        var coming = prodrug
        repeat(OutbreakData.PSILOCYBIN_METABOLISM_TICKS / 2) { coming = OutbreakPhysiology.tick(coming) }
        logger.info("PHYS half a day of psilocybin alone: psilocybin {} psilocin {}", coming.psilocybin, coming.psilocin)
        check("the prodrug does turn into psilocin", coming.psilocin > 0.25f)
        check("and it never gets anywhere near a trip on its own", coming.psilocinTier == 0)

        // Every stage of the trip, either side of its threshold: these are strict, so 1.2 exactly is
        // still nothing, and the fever that comes with the last stage only starts past 5.
        val stages = listOf(
            1.19f to 0, 1.21f to 1,
            1.69f to 1, 1.71f to 2,
            2.49f to 2, 2.51f to 3,
            4.99f to 3, 5.01f to 4,
        )
        for ((level, tier) in stages) {
            val body = OutbreakData.HEALTHY.withPsilocin(level)
            check("psilocin $level is trip stage $tier", body.psilocinTier == tier)
        }
        check("the thresholds are strict: 1.2 exactly is still no trip", OutbreakData.HEALTHY.withPsilocin(1.2f).psilocinTier == 0)
        check("5 exactly is still the mild warp", OutbreakData.HEALTHY.withPsilocin(5f).psilocinTier == 3)
        check("and 5 exactly is not yet a fever", abs(OutbreakPhysiology.targetTemperature(OutbreakData.HEALTHY.withPsilocin(5f)) - 37f) < 0.001f)
        check("5.01 is 39 degrees", abs(OutbreakPhysiology.targetTemperature(OutbreakData.HEALTHY.withPsilocin(5.01f)) - 39f) < 0.001f)
        check("and 7 is 41", abs(OutbreakPhysiology.targetTemperature(OutbreakData.HEALTHY.withPsilocin(7f)) - 41f) < 0.001f)

        val capped = OutbreakPhysiology.mushroom(OutbreakData.HEALTHY, 99f, 99f)
        check(
            "both compounds are capped at ten",
            capped.psilocybin == OutbreakData.PSILOCYBIN_CAP && capped.psilocin == OutbreakData.PSILOCIN_CAP,
        )

        // Three set point shifts that all stack: this mushroom's (+2), the mandrake's (+1) and, when
        // an infection is added to them, whatever the immune system is doing.
        val stacked = OutbreakData.HEALTHY.withPsilocin(5.5f).withScopolamine(2f)
        check(
            "the mushroom fever stacks on the mandrake fever",
            abs(OutbreakPhysiology.targetTemperature(stacked) - 40f) < 0.001f,
        )
    }

    // ================================================================== symptoms

    /**
     * The trip on the screen: one stage at a time, never two, and off again when it wears off.
     *
     * The stage is a server-side decision like any other screen effect - the client is only told
     * which of the four post effects to run - so this is checked on the requested effects rather
     * than on anything drawn.
     */
    private fun psilocinSymptoms(player: ServerPlayer) {
        val stages = listOf(
            1.5f to OutbreakSymptoms.PSILOCIN_OUTLINE,
            2.0f to OutbreakSymptoms.PSILOCIN_COLOUR,
            3.0f to OutbreakSymptoms.PSILOCIN_WARP,
            6.0f to OutbreakSymptoms.PSILOCIN_STORM,
        )
        val all = stages.map { it.second }

        for ((level, expected) in stages) {
            applyWith(player, OutbreakData.HEALTHY.withPsilocin(level))
            OutbreakSymptoms.tick(player)
            val onScreen = player.getPostEffects().filter { it in all }
            logger.info("PHYS psilocin {} asked for {}", level, onScreen)
            check("psilocin $level asks for exactly one trip effect", onScreen.size == 1)
            check("psilocin $level asks for $expected", onScreen.singleOrNull() == expected)
        }

        // It stacks with the rest of the screen: a bad trip while feverish shows both.
        applyWith(player, OutbreakData.HEALTHY.withPsilocin(6f).withTemperature(41f))
        OutbreakSymptoms.tick(player)
        logger.info("PHYS trip and fever at once: {}", player.getPostEffects())
        check(
            "the trip stacks with the fever's own effects",
            player.getPostEffects().contains(OutbreakSymptoms.PSILOCIN_STORM) &&
                player.getPostEffects().contains(OutbreakSymptoms.HEAT_BLUR),
        )

        applyWith(player, OutbreakData.HEALTHY)
        OutbreakSymptoms.tick(player)
        check("the trip comes off once the psilocin is gone", player.getPostEffects().none { it in all })
    }

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
        anticholinergicSymptoms(player)
        psilocinSymptoms(player)
        seaWaterIsNotFoul(level, player)
        infectionRolls(player)
        severeInfectionDamages()
    }

    /**
     * The visible half of a mandrake overdose.
     *
     * Two things have to happen and both are checked here: the blur is requested as a post effect like
     * any other screen effect, and the synced client state carries the flag that makes the client
     * close the fog in to eight blocks. The fever that comes with it is the model's, and is tested
     * with the rest of the model.
     */
    private fun anticholinergicSymptoms(player: ServerPlayer) {
        applyWith(player, OutbreakData.HEALTHY.withScopolamine(3f))
        OutbreakSymptoms.tick(player)
        val blurred = player.getAttachedOrCreate(OutbreakAttachments.CLIENT)
        logger.info("PHYS blurred client state: {}", blurred)
        check("a blurred player gets the blur post effect", player.getPostEffects().contains(OutbreakSymptoms.ANTICHOLINERGIC_BLUR))
        check("and the client is told to close the fog in", blurred.blurred)
        check("with no fever effect while the temperature is normal", !player.getPostEffects().contains(OutbreakSymptoms.HEAT_HAZE))

        // The two are independent: a fever's own effects stay on the screen alongside the blur.
        applyWith(player, OutbreakData.HEALTHY.withScopolamine(3f).withTemperature(41f))
        OutbreakSymptoms.tick(player)
        logger.info("PHYS blurred and feverish: {}", player.getPostEffects())
        check(
            "the drug blur stacks with the fever's own effects",
            player.getPostEffects().contains(OutbreakSymptoms.ANTICHOLINERGIC_BLUR) &&
                player.getPostEffects().contains(OutbreakSymptoms.HEAT_HAZE) &&
                player.getPostEffects().contains(OutbreakSymptoms.HEAT_BLUR),
        )

        applyWith(player, OutbreakData.HEALTHY)
        OutbreakSymptoms.tick(player)
        check("the blur comes off once the alkaloids are gone", !player.getPostEffects().contains(OutbreakSymptoms.ANTICHOLINERGIC_BLUR))
        check("and the fog opens back up", !player.getAttachedOrCreate(OutbreakAttachments.CLIENT).blurred)
    }

    /**
     * The two per-second contagion rolls.
     *
     * A headless dev server has no entity-ticking chunks, so nothing can be put next to the player
     * to be caught: the dice themselves are tested directly, and the "is anything there at all"
     * gate is tested by the negative case plus the cooldown, which is what makes the roll per
     * second rather than per tick.
     */
    private fun infectionRolls(player: ServerPlayer) {
        val random = player.level().random
        val trials = 400

        // Nothing nearby: nothing to catch, however many times this runs.
        var clear = OutbreakData.HEALTHY
        val runtime = OutbreakRuntime()
        repeat(100) { clear = OutbreakInfection.rollContact(player, clear, runtime) }
        check("with no creature nearby there is nothing to catch", clear.virus == 0f)

        // The cadence: exactly one contact roll per second, whatever the outcome.
        val paced = OutbreakRuntime()
        OutbreakInfection.rollContact(player, OutbreakData.HEALTHY, paced)
        check("a contact roll arms a one second cooldown", paced.contactCooldown == OutbreakInfection.ROLL_TICKS)
        repeat(OutbreakInfection.ROLL_TICKS - 1) {
            OutbreakInfection.rollContact(player, OutbreakData.HEALTHY, paced)
        }
        check("and does not roll again inside that second", paced.contactCooldown == 1)
        OutbreakInfection.rollContact(player, OutbreakData.HEALTHY, paced)
        check("then rolls again on the following tick", paced.contactCooldown == OutbreakInfection.ROLL_TICKS)

        var contacts = 0
        repeat(trials) { if (OutbreakInfection.contactRoll(random)) contacts++ }
        logger.info("PHYS contact dice: {}/{} rolls came up", contacts, trials)
        check("contact is a 5% roll", contacts in 5..40)

        var opportunistic = 0
        val seeds = mutableSetOf<Float>()
        repeat(trials) {
            if (OutbreakInfection.opportunisticRoll(random)) {
                opportunistic++
                seeds += OutbreakInfection.opportunisticLoad(random)
            }
        }
        logger.info("PHYS immunosuppression dice: {} distinct loads over {} hits", seeds.size, opportunistic)
        check("immunosuppression is a 15% roll", opportunistic in 40..85)
        check("the load it seeds is random rather than fixed", seeds.size > 3)
        check("and it stays inside the configured band", seeds.all { it in 4f..12f })

        // And the whole path, driven through the real entry point rather than the dice.
        val suppressed = OutbreakData.HEALTHY.copy(mediators = Mediators.CALM)
        check("a calm mediator set really is immunosuppressed", suppressed.isImmunosuppressed)
        var caught = 0
        repeat(trials) {
            if (OutbreakInfection.rollImmunosuppression(player, suppressed, OutbreakRuntime()).bacteria > 0f) {
                caught++
            }
        }
        logger.info("PHYS immunosuppression: {}/{} rolls seeded bacteria", caught, trials)
        check("an immunosuppressed body catches bacteria about 15% of the time", caught in 40..85)

        // A healthy immune system never does this, however long it stands there.
        var healthy = OutbreakData.HEALTHY
        repeat(trials) { healthy = OutbreakInfection.rollImmunosuppression(player, healthy, OutbreakRuntime()) }
        check("a healthy immune system never catches anything on its own", healthy.bacteria == 0f)
    }

    /**
     * Past the severe threshold the pathogen load costs health directly, and it costs more the
     * further past the threshold it is. It is the one symptom in this mod that can kill without a
     * monster being involved.
     *
     * Fabric's `FakePlayer` overrides `isInvulnerableTo` to always return true, so the health of one
     * can never move. What is asserted here is the damage the tick asks for, which is the whole of
     * the policy; applying it is a single `hurtServer` call that the mineral table already uses.
     */
    private fun severeInfectionDamages() {
        val symptomatic = OutbreakData.HEALTHY.copy(bacteria = 20f)
        check("20 points of load is symptomatic", symptomatic.isSymptomatic)
        check("but not severe enough to do damage", !symptomatic.isSevereInfection)

        check("59 points of load does no damage", OutbreakSymptoms.sepsisDamage(symptomatic.copy(bacteria = 59f)) == 0f)
        val atThreshold = OutbreakSymptoms.sepsisDamage(OutbreakData.HEALTHY.copy(bacteria = 60f))
        val critical = OutbreakSymptoms.sepsisDamage(OutbreakData.HEALTHY.copy(bacteria = 100f))
        logger.info("PHYS sepsis damage per two-second pass: 60 -> {} 100 -> {}", atThreshold, critical)
        check("60 points of load is the sepsis threshold", atThreshold > 0f)
        check("a worse infection does more damage", critical > atThreshold)

        // A low immune value is not itself harmful. It only lets an infection climb, and it is the
        // infection that does the damage - so a body with no immune response and no pathogen takes
        // nothing at all.
        val suppressed = OutbreakData.HEALTHY.copy(mediators = Mediators.CALM)
        check("a calm mediator set really is immunosuppressed", suppressed.isImmunosuppressed)
        check("an immunosuppressed body with no infection takes no damage", OutbreakSymptoms.sepsisDamage(suppressed) == 0f)
        check(
            "but the same body with an infection on top does",
            OutbreakSymptoms.sepsisDamage(suppressed.withBacteria(70f)) > 0f,
        )
    }

    /**
     * The mineral table, probed from the specs rather than from hard-coded numbers.
     *
     * Every mineral is checked on both sides of both of its thresholds, and just inside each end of
     * its reference range, so the test states the *rule* - inside is silent, outside is a symptom -
     * and cannot drift away from the model when a range is retuned. The handful of rows worth naming
     * are then asserted by effect.
     */
    private fun mineralSymptoms(player: ServerPlayer) {
        applyWith(player, minerals())
        check("a body with everything inside its ranges produces no symptoms", player.activeEffects.isEmpty())

        for (mineral in Electrolytes.MINERALS + Mineral.IODINE) {
            val name = mineral.name.lowercase()
            val margin = 0.02f * mineral.normal

            applyWith(player, at(mineral, mineral.safeLow + margin))
            check("$name just inside the bottom of its range is silent", player.activeEffects.isEmpty())
            applyWith(player, at(mineral, mineral.safeHigh - margin))
            check("$name just inside the top of its range is silent", player.activeEffects.isEmpty())

            applyWith(player, at(mineral, mineral.safeLow - margin))
            check("$name just below its range shows a symptom", player.activeEffects.isNotEmpty())
            applyWith(player, at(mineral, mineral.safeHigh + margin))
            check("$name just above its range shows a symptom", player.activeEffects.isNotEmpty())

            applyWith(player, at(mineral, mineral.severeLow - margin))
            check("$name in severe deficit shows a symptom", player.activeEffects.isNotEmpty())
            applyWith(player, at(mineral, mineral.severeHigh + margin))
            check("$name in severe excess shows a symptom", player.activeEffects.isNotEmpty())
        }

        // --- the rows worth naming, each probed just past the threshold it is about
        val sodium = Mineral.SODIUM
        expect(player, "severe hyponatraemia causes nausea", at(sodium, sodium.severeLow - 1f), MobEffects.NAUSEA)
        expect(player, "hypernatraemia causes thirst", at(sodium, sodium.safeHigh + 1f), MobEffects.HUNGER)

        val potassium = Mineral.POTASSIUM
        expect(player, "severe hypokalaemia causes mining fatigue", at(potassium, potassium.severeLow - 0.1f), MobEffects.MINING_FATIGUE)
        expect(player, "hyperkalaemia slows the heart", at(potassium, potassium.severeHigh + 0.1f), MobEffects.SLOWNESS)

        val magnesium = Mineral.MAGNESIUM
        expect(player, "hypomagnesaemia causes tremor", at(magnesium, magnesium.severeLow - 0.02f), MobEffects.SLOWNESS)
        expect(player, "hypermagnesaemia causes lethargy", at(magnesium, magnesium.safeHigh + 0.02f), MobEffects.SLOWNESS)

        val chloride = Mineral.CHLORIDE
        expect(player, "hypochloraemia causes nausea", at(chloride, chloride.severeLow - 1f), MobEffects.NAUSEA)
        expect(player, "hyperchloraemia causes nausea", at(chloride, chloride.severeHigh + 1f), MobEffects.NAUSEA)

        val calcium = Mineral.CALCIUM
        expect(player, "hypocalcaemia causes tetany", at(calcium, calcium.severeLow - 0.05f), MobEffects.SLOWNESS)
        expect(player, "hypercalcaemia causes lethargy", at(calcium, calcium.safeHigh + 0.05f), MobEffects.SLOWNESS)

        val iodine = Mineral.IODINE
        expect(player, "subclinical hypothyroidism is just weakness", at(iodine, iodine.safeLow - 0.01f), MobEffects.WEAKNESS)
        expect(player, "severe iodine deficiency causes mining fatigue", at(iodine, iodine.severeLow - 0.01f), MobEffects.MINING_FATIGUE)
        expect(player, "thyrotoxicosis causes nausea", at(iodine, iodine.safeHigh + 0.01f), MobEffects.NAUSEA)
        expect(player, "and an appetite without weight gain", at(iodine, iodine.safeHigh + 0.01f), MobEffects.HUNGER)

        // The exhaustion multiplier picks up the minerals that genuinely raise metabolic cost.
        player.setAttached(OutbreakAttachments.DATA, at(magnesium, magnesium.safeLow - 0.02f))
        check("low magnesium raises the metabolic cost", OutbreakSymptoms.exhaustionMultiplier(player) > 1f)
        player.setAttached(OutbreakAttachments.DATA, minerals())
        check("a body with everything in range pays nothing extra", OutbreakSymptoms.exhaustionMultiplier(player) == 1f)
    }

    /** The same healthy body with one mineral moved to [value]. */
    private fun at(mineral: Mineral, value: Float): OutbreakData = when (mineral) {
        Mineral.SODIUM -> minerals(sodium = value)
        Mineral.POTASSIUM -> minerals(potassium = value)
        Mineral.MAGNESIUM -> minerals(magnesium = value)
        Mineral.CHLORIDE -> minerals(chloride = value)
        Mineral.CALCIUM -> minerals(calcium = value)
        Mineral.IODINE -> minerals(iodine = value)
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
        // 1.10 umol/L is over the reference range but nowhere near thyrotoxicosis.
        var warm = OutbreakData.HEALTHY.copy(traceElements = TraceElements(1.10f))
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
        check(
            "one bottle of sea water stays inside the sodium reference range",
            after.electrolytes.sodium < Mineral.SODIUM.safeHigh,
        )
        check("and it carries the magnesium the sea has", after.electrolytes.magnesium > Mineral.MAGNESIUM.normal)
    }

    /** Runs one real server tick for [player] against [data], from a clean slate. */
    private fun applyWith(player: ServerPlayer, data: OutbreakData) {
        player.removeAllEffects()
        player.setAttached(OutbreakAttachments.DATA, data)
        // The effect pass runs on a cadence; a fresh runtime sits on it.
        player.setAttached(OutbreakAttachments.RUNTIME, OutbreakRuntime())
        // Damage has twenty ticks of invulnerability after it lands, which would silently swallow
        // the second and third assertion in a row; every tick here is meant to be independent.
        player.setInvulnerableTime(0)
        OutbreakSymptoms.tick(player)
    }

    /** Applies [data] and asserts that [effect] landed on the player. */
    private fun expect(player: ServerPlayer, label: String, data: OutbreakData, effect: Holder<MobEffect>) {
        applyWith(player, data)
        check(label, player.hasEffect(effect))
    }

    /** Builds healthy data with specific mineral values, everything else at normal. */
    private fun minerals(
        sodium: Float = Mineral.SODIUM.normal,
        potassium: Float = Mineral.POTASSIUM.normal,
        magnesium: Float = Mineral.MAGNESIUM.normal,
        chloride: Float = Mineral.CHLORIDE.normal,
        calcium: Float = Mineral.CALCIUM.normal,
        iodine: Float = Mineral.IODINE.normal,
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

    // ================================================================== chest loot

    /**
     * The two treatments Outbreak leaves in village and outpost chests.
     *
     * The chance cannot be read back out of a built pool - `LootPool` exposes nothing - so the pool
     * the mod really adds is rolled a few thousand times and the hits are counted. That measures the
     * number that decides what a player finds, rather than a constant that happens to be next to it.
     */
    private fun chestLoot(level: ServerLevel) {
        val additions = OutbreakLoot.ADDITIONS
        logger.info("PHYS chest additions: {}", additions.map { "${it.item} at ${it.chance}" })
        check("two items go into chests", additions.size == 2)
        check("the first is a dexamethasone injection", additions[0].item == OutbreakItems.DEXAMETHASONE_INJECTION)
        check("and it is a 3% find", additions[0].chance == 0.03f)
        check("the second is a bowl of willow bark soup", additions[1].item == OutbreakItems.WILLOW_BARK_SOUP_BOWL)
        check("and it is a 35% find", additions[1].chance == 0.35f)

        check("a plains village chest is a target", OutbreakLoot.targets(vanilla("chests/village/village_plains_house")))
        check("so is a snowy one", OutbreakLoot.targets(vanilla("chests/village/village_snowy_house")))
        check("so is the pillager outpost", OutbreakLoot.targets(vanilla("chests/pillager_outpost")))
        check("a dungeon chest is not", !OutbreakLoot.targets(vanilla("chests/simple_dungeon")))
        check("a woodland mansion chest is not", !OutbreakLoot.targets(vanilla("chests/woodland_mansion")))
        check(
            "nor is another mod's village table",
            !OutbreakLoot.targets(Identifier.fromNamespaceAndPath("someothermod", "chests/village/house")),
        )

        val params = LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
            .create(LootContextParamSets.CHEST)

        for (addition in additions) {
            val table = LootTable.Builder()
                .withPool(OutbreakLoot.poolFor(addition))
                .setParamSet(LootContextParamSets.CHEST)
                .build()
            val trials = 8_000
            var hits = 0
            repeat(trials) { table.getRandomItems(params) { hits++ } }
            val rate = hits.toFloat() / trials
            logger.info("PHYS chest roll for {}: {} / {} = {}", addition.item, hits, trials, rate)
            check(
                "a chest holds it about ${addition.chance * 100}% of the time",
                abs(rate - addition.chance) < 0.02f,
            )
        }
    }

    /** A vanilla loot table id, spelled the way the vanilla datapack spells it. */
    private fun vanilla(path: String): Identifier = Identifier.fromNamespaceAndPath("minecraft", path)

    // ================================================================== creative and death

    /**
     * Creative mode is not playing the game, so the physiology leaves it alone completely: the model
     * does not advance, nothing is applied, anything already on the screen is taken off, and nothing
     * eaten reaches the body.
     *
     * Freezing rather than resetting is what makes that safe. Going creative is not a cure - the
     * infection is still there - and going back to survival resumes exactly where the body was.
     */
    private fun creativeIsFrozen(level: ServerLevel) {
        val player = FakePlayer.get(level)
        check("a fake player is in survival, like a real one on a server", !player.isCreative)

        val ill = OutbreakData.HEALTHY.copy(bacteria = 70f, temperature = 41f)
        player.setAttached(OutbreakAttachments.DATA, ill)
        player.addPostEffect(OutbreakSymptoms.HEAT_HAZE)
        player.setGameMode(GameType.CREATIVE)
        OutbreakSymptoms.tick(player)

        val frozen = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        logger.info(
            "PHYS creative after a tick: bacteria {} temperature {} health {}",
            frozen.bacteria, frozen.temperature, player.health,
        )
        check("a creative body does not advance", frozen.bacteria == 70f && frozen.temperature == 41f)
        check("a severe infection does no damage to it", player.health == player.maxHealth)
        check("and applies no symptoms", !player.hasEffect(MobEffects.WEAKNESS) && !player.hasEffect(MobEffects.MINING_FATIGUE))
        check("the fever shimmer comes off the screen", player.getPostEffects().isEmpty())
        check("and there is no exhaustion penalty", OutbreakSymptoms.exhaustionMultiplier(player) == 1f)

        ItemStack(Items.KELP, 1).finishUsingItem(level, player)
        check(
            "nothing eaten reaches a creative body",
            player.getAttachedOrCreate(OutbreakAttachments.DATA).traceElements.iodine == ill.traceElements.iodine,
        )

        player.setGameMode(GameType.SURVIVAL)
        OutbreakSymptoms.tick(player)
        val resumed = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        logger.info("PHYS back in survival after a tick: bacteria {} temperature {}", resumed.bacteria, resumed.temperature)
        check("going back to survival resumes the model", resumed.bacteria < frozen.bacteria || resumed.temperature != frozen.temperature)
        check("and the fever shimmer comes back", player.getPostEffects().contains(OutbreakSymptoms.HEAT_HAZE))
    }

    /**
     * Dying resets the body.
     *
     * The `physiology` attachment is deliberately not marked `copyOnDeath`. Fabric copies attachments
     * from the old player to the new one on every respawn, but `PlayerList.respawn` passes `alive` to
     * the event, and Fabric only enforces `copyOnDeath` when it is `false` - a real death, where one
     * instance is thrown away. So the new player is initialised from `HEALTHY` instead: no infection,
     * no fever, and death is the one cure that always works.
     */
    private fun deathResetsTheBody(level: ServerLevel) {
        check("the body is not carried across death", !OutbreakAttachments.DATA.copyOnDeath())

        val player = FakePlayer.get(level)
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY.copy(bacteria = 40f, temperature = 40f))
        player.kill(level)
        val respawned = level.server.playerList.respawn(player, false, Entity.RemovalReason.KILLED)
        val after = respawned.getAttachedOrCreate(OutbreakAttachments.DATA)
        logger.info(
            "PHYS after respawn: bacteria {} virus {} temperature {} water {}",
            after.bacteria, after.virus, after.temperature, after.water,
        )
        check("a respawned body has no infection", after.bacteria == 0f && after.virus == 0f)
        check("a normal temperature", after.temperature == OutbreakData.TEMPERATURE_NORMAL)
        check("and the rest of a healthy body", after == OutbreakData.HEALTHY)
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
        check("salted sea water raises sodium", saltedSea.electrolytes.sodium > Mineral.SODIUM.normal)
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

        // Kelp is the only iodine source. Start from a real deficiency rather than a normal body.
        val deficient = OutbreakData.HEALTHY.copy(
            traceElements = TraceElements.HEALTHY.withIodine(0.30f),
        )
        player.setAttached(OutbreakAttachments.DATA, deficient)
        ItemStack(Items.KELP, 1).finishUsingItem(level, player)
        val afterKelp = player.getAttachedOrCreate(OutbreakAttachments.DATA).traceElements.iodine
        player.setAttached(OutbreakAttachments.DATA, deficient)
        ItemStack(Items.DRIED_KELP, 1).finishUsingItem(level, player)
        val afterDried = player.getAttachedOrCreate(OutbreakAttachments.DATA).traceElements.iodine
        logger.info("PHYS iodine from kelp {} / dried kelp {}", afterKelp, afterDried)
        check("kelp adds 0.10 umol/L of iodine", abs(afterKelp - 0.40f) < 0.001f)
        check("dried kelp adds 0.20 umol/L", abs(afterDried - 0.50f) < 0.001f)

        // The mandrake, eaten: the fruit and the seeds both carry the two alkaloids.
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        ItemStack(OutbreakItems.MANDRAKE_FRUIT, 1).finishUsingItem(level, player)
        val afterFruit = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        ItemStack(OutbreakItems.MANDRAKE_SEEDS, 1).finishUsingItem(level, player)
        val afterSeeds = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        logger.info(
            "PHYS mandrake eaten: fruit {} / {} seeds {} / {}",
            afterFruit.scopolamine, afterFruit.atropine, afterSeeds.scopolamine, afterSeeds.atropine,
        )
        check(
            "eating a fruit adds a full dose of scopolamine and a tenth of atropine",
            abs(afterFruit.scopolamine - 1.0f) < 0.001f && abs(afterFruit.atropine - 0.1f) < 0.001f,
        )
        check(
            "eating the seeds adds three quarters of a dose and the same atropine",
            abs(afterSeeds.scopolamine - 0.75f) < 0.001f && abs(afterSeeds.atropine - 0.1f) < 0.001f,
        )

        // The mushroom, raw and cooked: the raw one carries both compounds, cooking destroys them.
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        ItemStack(OutbreakItems.GYMNOPILUS, 1).finishUsingItem(level, player)
        val afterRaw = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        ItemStack(OutbreakItems.COOKED_GYMNOPILUS, 1).finishUsingItem(level, player)
        val afterCooked = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        logger.info(
            "PHYS gymnopilus eaten: raw {} / {} cooked {} / {}",
            afterRaw.psilocybin, afterRaw.psilocin, afterCooked.psilocybin, afterCooked.psilocin,
        )
        check(
            "eating a raw gymnopilus adds a dose of each compound",
            abs(afterRaw.psilocybin - 1.3f) < 0.001f && abs(afterRaw.psilocin - 1.3f) < 0.001f,
        )
        check(
            "eating a cooked one adds neither",
            afterCooked.psilocybin == 0f && afterCooked.psilocin == 0f,
        )

        // Food is written as hunger plus saturation *points*. `FoodProperties.saturation()` is
        // already the points - the builder's `saturationModifier` is what multiplies them out - so
        // these two read straight off the item, which is the point of deriving rather than hardcoding
        // the multiplier in `OutbreakItems`.
        val rawFood = OutbreakItems.GYMNOPILUS.components().get(DataComponents.FOOD)
        val cookedFood = OutbreakItems.COOKED_GYMNOPILUS.components().get(DataComponents.FOOD)
        logger.info(
            "PHYS gymnopilus food: raw {} hunger {} saturation, cooked {} hunger {} saturation",
            rawFood?.nutrition(), rawFood?.saturation(), cookedFood?.nutrition(), cookedFood?.saturation(),
        )
        check(
            "the raw mushroom is 3 hunger and 4 saturation",
            rawFood != null && rawFood.nutrition() == 3 && abs(rawFood.saturation() - 4f) < 0.01f,
        )
        check(
            "and the cooked one is 4 hunger and 5 saturation",
            cookedFood != null && cookedFood.nutrition() == 4 && abs(cookedFood.saturation() - 5f) < 0.01f,
        )
        check(
            "a raw mushroom can be eaten even on a full stomach",
            rawFood != null && rawFood.canAlwaysEat(),
        )

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
        check("refined salt water raises sodium", refined.electrolytes.sodium > Mineral.SODIUM.normal)
        check("crude salt water carries the extra minerals", crude.electrolytes.magnesium > refined.electrolytes.magnesium)

        // The whole point of the reference ranges: one serving is fine, two tip sodium over.
        check(
            "one serving of salt water stays inside the sodium range",
            refined.electrolytes.sodium < Mineral.SODIUM.safeHigh,
        )
        player.setAttached(OutbreakAttachments.DATA, refined)
        ItemStack(OutbreakItems.SALT_WATER, 1).finishUsingItem(level, player)
        val twoCups = player.getAttachedOrCreate(OutbreakAttachments.DATA).electrolytes.sodium
        logger.info("PHYS sodium after two servings of salt water: {}", twoCups)
        check("two servings of salt water go past it", twoCups > Mineral.SODIUM.safeHigh)

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
            mediators = base.mediators.withCytokine((base.mediators.cytokine + extra).coerceIn(0f, Mediators.MAX)),
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
