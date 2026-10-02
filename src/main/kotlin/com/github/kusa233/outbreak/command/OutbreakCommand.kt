package com.github.kusa233.outbreak.command

import com.github.kusa233.outbreak.physiology.Electrolytes
import com.github.kusa233.outbreak.physiology.Mediators
import com.github.kusa233.outbreak.physiology.Mineral
import com.github.kusa233.outbreak.physiology.OutbreakAttachments
import com.github.kusa233.outbreak.physiology.OutbreakData
import com.github.kusa233.outbreak.physiology.OutbreakPhysiology
import com.github.kusa233.outbreak.physiology.OutbreakSymptoms
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component

/**
 * `/outbreak` - inspect and poke at the physiology system.
 *
 * Useful both for players (to see how ill they are) and for anyone tuning the numbers, since the
 * whole model is data driven and otherwise invisible.
 */
object OutbreakCommand {

    /** Every field `set` understands, in the order the suggestions list them. */
    private val FIELDS = listOf(
        "water",
        "sodium", "potassium", "magnesium", "chloride", "calcium",
        "iodine",
        "histamine", "prostaglandin", "leukotriene", "cytokine", "bradykinin",
        "bacteria", "virus",
        "salicin", "dexamethasone",
        "scopolamine", "atropine",
        "temperature", "pyrogen",
    )

    /** `/outbreak fever` with no argument lands here: a solid, clearly symptomatic fever. */
    private const val DEFAULT_FEVER = 39.5f

    fun initialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("outbreak")
                    .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                    .then(Commands.literal("status").executes(::status))
                    .then(
                        Commands.literal("set")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .then(
                                Commands.argument("field", StringArgumentType.word())
                                    .suggests { _, builder -> SharedSuggestionProvider.suggest(FIELDS, builder) }
                                    .then(
                                        // Negative values are allowed so that `set pyrogen -2`, an
                                        // antipyretic offset, is expressible.
                                        Commands.argument("value", DoubleArgumentType.doubleArg(-20.0, 200.0))
                                            .executes(::setField),
                                    ),
                            ),
                    )
                    .then(
                        Commands.literal("fever")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes { fever(it, DEFAULT_FEVER) }
                            .then(
                                // The reachable range is what a pyrogen can shift the set point by,
                                // not the whole scale: asking for 30 would silently come up short.
                                Commands.argument(
                                    "degrees",
                                    DoubleArgumentType.doubleArg(
                                        (OutbreakData.TEMPERATURE_NORMAL - OutbreakData.PYROGEN_CAP).toDouble(),
                                        OutbreakData.TEMPERATURE_MAX.toDouble(),
                                    ),
                                ).executes { fever(it, DoubleArgumentType.getDouble(it, "degrees").toFloat()) },
                            ),
                    )
                    .then(
                        Commands.literal("cure")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(::cure),
                    ),
            )
        }
    }

    private fun status(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val data = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        val e = data.electrolytes
        val m = data.mediators
        val condition = when {
            data.isImmuneStorm -> "immune storm"
            data.isImmunosuppressed -> "immunosuppressed"
            data.isSymptomatic -> "infected"
            data.isFebrile -> "fever"
            data.isHypothermic -> "hypothermia"
            data.hasElectrolyteImbalance -> "electrolyte imbalance"
            else -> "healthy"
        }

        val electrolytes = Electrolytes.MINERALS.joinToString("  ") { mineral ->
            "%s %s".format(shortName(mineral), mineral.display(e.of(mineral)))
        }
        val lines = listOf(
            "Outbreak: %s".format(condition),
            "  inflammation %.1f  (histamine %.0f, prostaglandin %.0f, leukotriene %.0f, cytokine %.0f, bradykinin %.0f)"
                .format(data.inflammation, m.histamine, m.prostaglandin, m.leukotriene, m.cytokine, m.bradykinin),
            "  water %.1f  (%d/%d cells)%s".format(
                data.water, data.thirstCells, OutbreakData.THIRST_CELLS,
                if (data.isOverhydrated) "  [over-hydrated]" else if (data.isDehydrated) "  [dehydrated]" else "",
            ),
            "  electrolytes (mmol/L)  %s%s".format(
                electrolytes,
                if (data.hasElectrolyteImbalance) "  [out of range]" else "",
            ),
            "  trace elements (umol/L)  I %s%s".format(
                Mineral.IODINE.display(data.traceElements.iodine),
                if (data.hasTraceElementImbalance) "  [out of range]" else "",
            ),
            "  temperature %.2f C  [%s]  pyrogen %+.2f".format(
                data.temperature, thermalName(data.thermalTier), data.pyrogen,
            ),
            "  pathogens  bacteria %.1f  virus %.1f%s".format(
                data.bacteria, data.virus,
                if (data.isSevereInfection) "  [severe: taking damage]" else "",
            ),
            "  drugs  salicin %.2f  dexamethasone %.2f".format(data.salicin, data.dexamethasone),
            "  mandrake  scopolamine %.2f  atropine %.2f  (load %.2f%s)%s".format(
                data.scopolamine, data.atropine, data.anticholinergicLoad,
                if (data.isVisionBlurred) ", sight blurred" else "",
                if (data.thermalTier > 0 && data.anticholinergicLoad >= 1.5f) "  [drug fever]" else "",
            ),
        )
        for (line in lines) {
            context.source.sendSuccess({ Component.literal(line) }, false)
        }
        return 1
    }

    /** The symbol `/outbreak status` prints for [mineral]. */
    private fun shortName(mineral: Mineral): String = when (mineral) {
        Mineral.SODIUM -> "Na"
        Mineral.POTASSIUM -> "K"
        Mineral.MAGNESIUM -> "Mg"
        Mineral.CHLORIDE -> "Cl"
        Mineral.CALCIUM -> "Ca"
        Mineral.IODINE -> "I"
    }

    private fun thermalName(tier: Int): String = when {
        tier >= 2 -> "super-high fever"
        tier == 1 -> "fever"
        tier == -1 -> "hypothermia"
        tier <= -2 -> "severe hypothermia"
        else -> "normal"
    }

    private fun setField(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val field = StringArgumentType.getString(context, "field")
        val value = DoubleArgumentType.getDouble(context, "value").toFloat()
        val data = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        val e = data.electrolytes
        val m = data.mediators

        val updated = when (field) {
            "water" -> data.copy(water = value.coerceIn(OutbreakData.WATER_MIN, OutbreakData.WATER_MAX))
            "sodium" -> data.withElectrolytes(e.withSodium(Mineral.SODIUM.clamp(value)))
            "potassium" -> data.withElectrolytes(e.withPotassium(Mineral.POTASSIUM.clamp(value)))
            "magnesium" -> data.withElectrolytes(e.withMagnesium(Mineral.MAGNESIUM.clamp(value)))
            "chloride" -> data.withElectrolytes(e.withChloride(Mineral.CHLORIDE.clamp(value)))
            "calcium" -> data.withElectrolytes(e.withCalcium(Mineral.CALCIUM.clamp(value)))
            "iodine" -> data.withTraceElements(data.traceElements.withIodine(Mineral.IODINE.clamp(value)))
            "histamine" -> data.withMediators(m.withHistamine(clamp(value, Mediators.MAX)))
            "prostaglandin" -> data.withMediators(m.withProstaglandin(clamp(value, Mediators.MAX)))
            "leukotriene" -> data.withMediators(m.withLeukotriene(clamp(value, Mediators.MAX)))
            "cytokine" -> data.withMediators(m.withCytokine(clamp(value, Mediators.MAX)))
            "bradykinin" -> data.withMediators(m.withBradykinin(clamp(value, Mediators.MAX)))
            "bacteria" -> data.copy(bacteria = clamp(value, OutbreakData.MAX_PATHOGEN))
            "virus" -> data.copy(virus = clamp(value, OutbreakData.MAX_PATHOGEN))
            "salicin" -> data.copy(salicin = clamp(value, OutbreakData.SALICIN_CAP))
            "dexamethasone" -> data.copy(dexamethasone = clamp(value, OutbreakData.DEXAMETHASONE_CAP))
            "scopolamine" -> data.withScopolamine(clamp(value, OutbreakData.ANTICHOLINERGIC_CAP))
            "atropine" -> data.withAtropine(clamp(value, OutbreakData.ANTICHOLINERGIC_CAP))
            // Setting the temperature moves the body itself; setting the pyrogen moves the target
            // it is walking towards, which is what makes a fever persist.
            "temperature" -> data.copy(
                temperature = value.coerceIn(OutbreakData.TEMPERATURE_MIN, OutbreakData.TEMPERATURE_MAX),
            )
            "pyrogen" -> data.copy(pyrogen = value.coerceIn(-OutbreakData.PYROGEN_CAP, OutbreakData.PYROGEN_CAP))
            else -> {
                context.source.sendFailure(Component.literal("Unknown field. Try one of $FIELDS"))
                return 0
            }
        }

        player.setAttached(OutbreakAttachments.DATA, updated)
        // The electrolytes are in mmol/L and iodine in umol/L, and each has its own clamp, so the
        // value the player typed is not always the value that landed: report what the field holds.
        val landed = when (field) {
            "sodium" -> Mineral.SODIUM.display(updated.electrolytes.sodium)
            "potassium" -> Mineral.POTASSIUM.display(updated.electrolytes.potassium)
            "magnesium" -> Mineral.MAGNESIUM.display(updated.electrolytes.magnesium)
            "chloride" -> Mineral.CHLORIDE.display(updated.electrolytes.chloride)
            "calcium" -> Mineral.CALCIUM.display(updated.electrolytes.calcium)
            "iodine" -> Mineral.IODINE.display(updated.traceElements.iodine)
            else -> "$value"
        }
        context.source.sendSuccess({ Component.literal("$field = $landed") }, false)
        return 1
    }

    /**
     * `/outbreak fever [degrees]` - induces a fever (or, below 37, hypothermia) for testing.
     *
     * The temperature is not set directly: a pyrogen is injected so that the fever really *peaks*
     * at `degrees`, whatever the infection is already doing, and the core temperature then walks
     * towards it the way it does for a real fever. It is cleared over one in-game day, so the
     * screen effects are gone within twenty minutes of real time - and `/outbreak cure` takes them
     * off immediately, which the feedback line says, because a fever you forgot about is
     * indistinguishable from a bug.
     */
    private fun fever(context: CommandContext<CommandSourceStack>, degrees: Float): Int {
        val player = context.source.playerOrException
        val data = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        val updated = OutbreakPhysiology.induceFever(data, degrees, OutbreakSymptoms.ambientTemperature(player))
        player.setAttached(OutbreakAttachments.DATA, updated)

        val direction = if (degrees >= OutbreakData.TEMPERATURE_NORMAL) "fever" else "hypothermia"
        val screen = when {
            degrees >= OutbreakData.FEVER_SEVERE -> "heat haze + motion blur"
            degrees >= OutbreakData.FEVER_MILD -> "heat haze"
            degrees <= OutbreakData.COLD_MILD -> "cold shiver"
            else -> "no screen effect at this temperature"
        }
        context.source.sendSuccess(
            {
                Component.literal(
                    "Outbreak: %s peaking at %.2f C (%s) - about two minutes to get there, /outbreak cure to stop it"
                        .format(direction, degrees, screen),
                )
            },
            true,
        )
        return 1
    }

    private fun cure(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        // The screen effects are driven by the data, but clearing them here means `/outbreak cure`
        // takes the shimmer off the screen on the same tick rather than on the next one.
        OutbreakSymptoms.clearPostEffects(player)
        context.source.sendSuccess({ Component.literal("Outbreak: physiology reset") }, false)
        return 1
    }

    /** Clamp for the fields that are not minerals: mediators, pathogens and drugs. */
    private fun clamp(value: Float, max: Float): Float = value.coerceIn(0f, max)
}
