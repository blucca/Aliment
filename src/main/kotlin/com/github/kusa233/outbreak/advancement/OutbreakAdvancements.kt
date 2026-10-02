package com.github.kusa233.outbreak.advancement

import com.github.kusa233.outbreak.registry.OutbreakItems
import com.github.kusa233.outbreak.registry.Registration
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * Outbreak's advancement identifiers and award helpers.
 */
object OutbreakAdvancements {
    val ANCIENT_ANTI_INFLAMMATORY: Identifier = Registration.id("ancient_anti_inflammatory")
    val JUST_CRUDE_SALT: Identifier = Registration.id("just_crude_salt")
    val CRUSHED_AGAIN: Identifier = Registration.id("crushed_again")
    val REFINED_SALT: Identifier = Registration.id("refined_salt")
    val EVEN_IF_DANGEROUS: Identifier = Registration.id("even_if_dangerous")
    val PSYCHEDELIC_WORLD: Identifier = Registration.id("psychedelic_world")
    val EXTREME_FEVER: Identifier = Registration.id("extreme_fever")

    /**
     * Awards every criterion on the given advancement to the player, granting it.
     */
    @JvmStatic
    fun award(player: ServerPlayer, id: Identifier) {
        val server = player.level().server
        val holder = server.advancements.get(id) ?: return
        val progress = player.advancements.getOrStartProgress(holder)
        if (!progress.isDone) {
            for (criterion in holder.value().criteria().keys) {
                player.advancements.award(holder, criterion)
            }
        }
    }

    /**
     * Handles grindstone output for salt crushing advancements.
     */
    @JvmStatic
    fun onGrind(player: ServerPlayer, stack: ItemStack) {
        if (stack.`is`(OutbreakItems.CRUDE_SALT)) {
            award(player, JUST_CRUDE_SALT)
        } else if (stack.`is`(OutbreakItems.CRUDE_SALT_POWDER)) {
            award(player, CRUSHED_AGAIN)
        }
    }
}
