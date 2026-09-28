package com.github.kusa233.outbreak.dev

import com.github.kusa233.outbreak.physiology.OutbreakAttachments
import com.github.kusa233.outbreak.physiology.OutbreakData
import com.github.kusa233.outbreak.physiology.OutbreakPhysiology
import com.github.kusa233.outbreak.physiology.OutbreakShakeState
import com.github.kusa233.outbreak.physiology.OutbreakSymptoms
import com.github.kusa233.outbreak.registry.OutbreakItems
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.FakePlayer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.apache.logging.log4j.LogManager

/**
 * DEVELOPMENT ONLY. Second self test entrypoint, covering the physiology model.
 *
 * Nothing here needs a world: the simulation is pure data, so the whole thing runs in a few
 * milliseconds on the first server tick. Enable it from `fabric.mod.json` the same way as
 * [OutbreakSelfTest].
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
                mixinChecks(server.overworld())
            } catch (t: Throwable) {
                logger.error("PHYSIOLOGY SELFTEST exception", t)
                failed++
            }
            logger.info("PHYSIOLOGY SELFTEST DONE passed={} failed={}", passed, failed)
        }
    }

    /**
     * End to end checks for the parts that need a real player: everything here goes through the
     * mixins rather than calling the helpers directly.
     */
    private fun mixinChecks(level: ServerLevel) {
        val player = FakePlayer.get(level)

        // Eating raw meat goes through ItemMixin -> OutbreakInfection.onItemConsumed.
        val meatInfections = countInfections(level, player, Items.BEEF, 400)
        logger.info("PHYS raw meat infections: {}/400 = {}%", meatInfections, meatInfections / 4.0)
        check("eating raw meat infects about 30% of the time", meatInfections in 90..150)

        val fleshInfections = countInfections(level, player, Items.ROTTEN_FLESH, 400)
        logger.info("PHYS rotten flesh infections: {}/400 = {}%", fleshInfections, fleshInfections / 4.0)
        check("rotten flesh is a risky food too", fleshInfections in 90..150)

        val potatoInfections = countInfections(level, player, Items.POISONOUS_POTATO, 400)
        logger.info("PHYS poisonous potato infections: {}/400 = {}%", potatoInfections, potatoInfections / 4.0)
        check("poisonous potatoes are a risky food too", potatoInfections in 90..150)

        val breadInfections = countInfections(level, player, Items.BREAD, 400)
        logger.info("PHYS bread infections (control): {}/400", breadInfections)
        check("safe food never infects", breadInfections == 0)

        // Soup delivers salicin through the same hook.
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        ItemStack(OutbreakItems.WILLOW_BARK_SOUP_BOWL, 1).finishUsingItem(level, player)
        val cookedSalicin = player.getAttachedOrCreate(OutbreakAttachments.DATA).salicin
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        ItemStack(OutbreakItems.RAW_WILLOW_BARK_SOUP_BOTTLE, 1).finishUsingItem(level, player)
        val rawSalicin = player.getAttachedOrCreate(OutbreakAttachments.DATA).salicin
        logger.info("PHYS salicin from soup: cooked={} raw={}", cookedSalicin, rawSalicin)
        check("cooked soup raises salicin to the effective level", cookedSalicin >= OutbreakData.SALICIN_EFFECTIVE)
        check("raw soup raises salicin to the effective level", rawSalicin >= OutbreakData.SALICIN_EFFECTIVE)

        // PlayerMixin scales food exhaustion.
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        val healthyMultiplier = OutbreakSymptoms.exhaustionMultiplier(player)
        val ill = OutbreakData.HEALTHY.copy(bacteria = 55f, inflammation = 60f)
        player.setAttached(OutbreakAttachments.DATA, ill)
        val illMultiplier = OutbreakSymptoms.exhaustionMultiplier(player)
        logger.info("PHYS exhaustion multiplier: healthy={} ill={}", healthyMultiplier, illMultiplier)
        check("a healthy player has no exhaustion penalty", healthyMultiplier == 1f)
        check("an ill player burns food faster", illMultiplier > 1.25f)

        // The mining penalty rides on BLOCK_BREAK_SPEED, which vanilla multiplies unconditionally.
        OutbreakSymptoms.tick(player)
        val illMining = player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED)
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        OutbreakSymptoms.tick(player)
        val healthyMining = player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED)
        logger.info("PHYS mining speed: ill={} healthy={}", illMining, healthyMining)
        check("an infection slows mining down", illMining < 1.0)
        check("mining speed returns to normal once healthy", healthyMining == 1.0)

        // The shake counter is what the client watches. Count *transitions*, not ticks.
        var lastSequence = player.getAttachedOrElse(OutbreakAttachments.SHAKE, OutbreakShakeState.INACTIVE).sequence
        var shakes = 0
        var rolls = 0
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY.copy(bacteria = 80f, inflammation = 90f))
        repeat(OutbreakSymptoms.SHAKE_INTERVAL_TICKS * 40) {
            OutbreakSymptoms.tick(player)
            val now = player.getAttachedOrElse(OutbreakAttachments.SHAKE, OutbreakShakeState.INACTIVE)
            if (now.sequence != lastSequence) {
                lastSequence = now.sequence
                shakes++
                rolls++
            }
        }
        logger.info("PHYS shakes over 40 rolls: {} (base chance 20%)", shakes)
        check("a symptomatic player shakes sometimes", shakes in 2..18)
    }

    /** Feeds [item] to [player] [trials] times and counts how often bacteria were seeded. */
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

    private fun runAll() {
        homeostasis()
        infectionResolvesWhenMild()
        untreatedInfectionRunsAway()
        salicinControlsInfection()
        overdoseLetsInfectionRun()
        salicinMetabolisesInThreeDays()
        immuneCompetenceCurve()
    }

    // ------------------------------------------------------------------ checks

    /** A healthy player must stay inside the safe band indefinitely. */
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
            "PHYS homeostasis over 3 days: inflammation {}..{} electrolytes {} bacteria {} salicin {}",
            min, max, data.electrolytes, data.bacteria, data.salicin,
        )
        check("homeostasis keeps inflammation inside 20..30", min >= 20f && max <= 30f)
        check("homeostasis keeps electrolytes topped up", data.electrolytes > 99f)
    }

    /** One bad meal is survivable. */
    private fun infectionResolvesWhenMild() {
        var data = OutbreakPhysiology.seed(OutbreakData.HEALTHY, bacteria = 6f)
        var peak = 0f
        var peakInflammation = 0f
        repeat(24_000) {
            data = OutbreakPhysiology.tick(data)
            peak = maxOf(peak, data.bacteria)
            peakInflammation = maxOf(peakInflammation, data.inflammation)
        }
        logger.info(
            "PHYS mild infection: peak load {} peak inflammation {} final load {}",
            peak, peakInflammation, data.bacteria,
        )
        check("a single 6 point infection is cleared", data.bacteria < 0.5f)
        check("a mild infection does not cause a storm", peakInflammation < OutbreakData.IMMUNE_STORM_THRESHOLD)
    }

    /** A heavy, untreated load must beat the immune system and end in a storm. */
    private fun untreatedInfectionRunsAway() {
        var data = OutbreakPhysiology.seed(OutbreakData.HEALTHY, bacteria = 40f)
        var peakInflammation = 0f
        repeat(24_000) {
            data = OutbreakPhysiology.tick(data)
            peakInflammation = maxOf(peakInflammation, data.inflammation)
        }
        logger.info(
            "PHYS untreated infection: final load {} peak inflammation {} electrolytes {}",
            data.bacteria, peakInflammation, data.electrolytes,
        )
        check("an untreated 40 point infection takes hold", data.bacteria > 50f)
        check("an untreated infection ends in an immune storm", peakInflammation >= OutbreakData.IMMUNE_STORM_THRESHOLD)
        check("a long fever drains electrolytes", data.electrolytes < 95f)
    }

    /** A single serving of willow bark soup puts salicin at the effective level and controls it. */
    private fun salicinControlsInfection() {
        var data = OutbreakPhysiology.seed(OutbreakData.HEALTHY, bacteria = 40f)
        data = OutbreakPhysiology.dose(data, 1.1f)
        var peakInflammation = 0f
        repeat(24_000) {
            data = OutbreakPhysiology.tick(data)
            peakInflammation = maxOf(peakInflammation, data.inflammation)
        }
        logger.info(
            "PHYS treated infection: final load {} peak inflammation {} salicin {}",
            data.bacteria, peakInflammation, data.salicin,
        )
        check("salicin clears the same infection", data.bacteria < 0.5f)
        check("salicin keeps inflammation out of the storm", peakInflammation < OutbreakData.IMMUNE_STORM_THRESHOLD)
        check("salicin holds inflammation near the safe band", peakInflammation <= 32f)
    }

    /** Too much salicin suppresses the immune system and lets the infection run. */
    private fun overdoseLetsInfectionRun() {
        var data = OutbreakPhysiology.seed(OutbreakData.HEALTHY, bacteria = 40f)
        repeat(24_000) {
            // Keep topping the drug up, as an over-drinking player would.
            data = OutbreakPhysiology.tick(OutbreakPhysiology.dose(data, 0.5f))
        }
        logger.info(
            "PHYS overdose: final load {} inflammation {} salicin {}",
            data.bacteria, data.inflammation, data.salicin,
        )
        check("an overdose suppresses inflammation", data.inflammation < OutbreakData.IMMUNOSUPPRESSION_THRESHOLD + 8f)
        check("an overdose lets the infection run away", data.bacteria > 50f)
    }

    /** Three in-game days after the last dose the drug is gone. */
    private fun salicinMetabolisesInThreeDays() {
        var data = OutbreakData.HEALTHY.copy(salicin = OutbreakData.SALICIN_CAP)
        repeat(OutbreakData.SALICIN_METABOLISM_TICKS) {
            data = OutbreakPhysiology.tick(data)
        }
        logger.info("PHYS salicin after 3 days: {}", data.salicin)
        check("salicin is fully metabolised after three game days", data.salicin <= 0f)

        var half = OutbreakData.HEALTHY.copy(salicin = OutbreakData.SALICIN_CAP)
        repeat(OutbreakData.SALICIN_METABOLISM_TICKS / 2) {
            half = OutbreakPhysiology.tick(half)
        }
        logger.info("PHYS salicin after 1.5 days: {}", half.salicin)
        check("salicin decays linearly", kotlin.math.abs(half.salicin - OutbreakData.SALICIN_CAP / 2f) < 0.01f)
    }

    /** The competence curve must peak in the safe band and collapse at both extremes. */
    private fun immuneCompetenceCurve() {
        val atBaseline = OutbreakPhysiology.immuneCompetence(OutbreakData.BASELINE_INFLAMMATION)
        val atZero = OutbreakPhysiology.immuneCompetence(0f)
        val atStorm = OutbreakPhysiology.immuneCompetence(OutbreakData.IMMUNE_STORM_THRESHOLD)
        logger.info("PHYS competence: 0 -> {} baseline -> {} storm -> {}", atZero, atBaseline, atStorm)
        check("competence peaks at the baseline", atBaseline > 0.99f)
        check("competence collapses at zero inflammation", atZero <= 0f)
        check("competence collapses during a storm", atStorm < 0.1f)
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
