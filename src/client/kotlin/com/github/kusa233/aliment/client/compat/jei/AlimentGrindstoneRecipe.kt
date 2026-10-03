package com.github.kusa233.aliment.client.compat.jei

import net.minecraft.world.item.ItemStack

/**
 * Represents a single Grindstone crushing recipe for JEI display.
 *
 * @param input The input item stack (count is 1).
 * @param output The produced result item stack with count.
 */
data class AlimentGrindstoneRecipe(
    val input: ItemStack,
    val output: ItemStack
)
