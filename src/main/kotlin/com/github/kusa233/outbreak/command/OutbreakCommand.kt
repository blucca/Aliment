package com.github.kusa233.outbreak.command

import com.github.kusa233.outbreak.physiology.OutbreakAttachments
import com.github.kusa233.outbreak.physiology.OutbreakData
import com.github.kusa233.outbreak.physiology.OutbreakPhysiology
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * `/outbreak` - inspect and poke at the physiology system.
 *
 * Useful both for players (to see how ill they are) and for anyone tuning the numbers, since the
 * whole model is data driven and otherwise invisible.
 */
object OutbreakCommand {

    private val FIELDS = listOf("inflammation", "electrolytes", "bacteria", "virus", "salicin")

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
                                        Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 200.0))
                                            .executes(::setField),
                                    ),
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
        val condition = when {
            data.isImmuneStorm -> "immune storm"
            data.isImmunosuppressed -> "immunosuppressed"
            data.isSymptomatic -> "infected"
            else -> "healthy"
        }
        context.source.sendSuccess(
            {
                Component.literal(
                    "Outbreak: %s | inflammation %.1f | electrolytes %.1f | bacteria %.1f | virus %.1f | salicin %.2f"
                        .format(
                            condition,
                            data.inflammation,
                            data.electrolytes,
                            data.bacteria,
                            data.virus,
                            data.salicin,
                        ),
                )
            },
            false,
        )
        return 1
    }

    private fun setField(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val field = StringArgumentType.getString(context, "field")
        val value = DoubleArgumentType.getDouble(context, "value").toFloat()
        val data = player.getAttachedOrCreate(OutbreakAttachments.DATA)

        val updated = when (field) {
            "inflammation" -> data.copy(inflammation = value.coerceIn(OutbreakData.MIN_INFLAMMATION, OutbreakData.MAX_INFLAMMATION))
            "electrolytes" -> data.copy(electrolytes = value.coerceIn(OutbreakData.MIN_ELECTROLYTES, OutbreakData.MAX_ELECTROLYTES))
            "bacteria" -> data.copy(bacteria = value.coerceIn(0f, OutbreakData.MAX_PATHOGEN))
            "virus" -> data.copy(virus = value.coerceIn(0f, OutbreakData.MAX_PATHOGEN))
            "salicin" -> data.copy(salicin = value.coerceIn(0f, OutbreakData.SALICIN_CAP))
            else -> {
                context.source.sendFailure(Component.literal("Unknown field. Try one of $FIELDS"))
                return 0
            }
        }

        player.setAttached(OutbreakAttachments.DATA, updated)
        context.source.sendSuccess({ Component.literal("$field = $value") }, false)
        return 1
    }

    private fun cure(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        player.setAttached(OutbreakAttachments.DATA, OutbreakData.HEALTHY)
        context.source.sendSuccess({ Component.literal("Outbreak: physiology reset") }, false)
        return 1
    }

    /** Seeds a pathogen directly; handy for testing without eating rotten flesh. */
    fun infect(player: ServerPlayer, bacteria: Float, virus: Float) {
        val data = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        player.setAttached(OutbreakAttachments.DATA, OutbreakPhysiology.seed(data, bacteria, virus))
    }
}
