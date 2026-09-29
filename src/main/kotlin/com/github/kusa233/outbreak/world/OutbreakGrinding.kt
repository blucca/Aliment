package com.github.kusa233.outbreak.world

import com.github.kusa233.outbreak.registry.OutbreakBlocks
import com.github.kusa233.outbreak.registry.OutbreakItems
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * What the grindstone does with Outbreak's items.
 *
 * Vanilla's grindstone only accepts damageable or enchanted items, and its result logic knows
 * nothing about our conversions, so both the input slots and [net.minecraft.world.inventory.GrindstoneMenu]
 * are widened by mixins that forward here. Keeping the table in one place means the mixins stay
 * one-liners and the behaviour is testable without a menu.
 */
object OutbreakGrinding {

    /** One input item to (output item, how many it yields). */
    private val RECIPES: Map<Item, Pair<Item, Int>> = mapOf(
        OutbreakItems.WILLOW_BARK to (OutbreakItems.WILLOW_BARK_PIECES to 2),
        OutbreakBlocks.ROCK_SALT_ORE.asItem() to (OutbreakItems.CRUDE_SALT to 9),
        OutbreakItems.CRUDE_SALT to (OutbreakItems.CRUDE_SALT_POWDER to 1),
    )

    /** True when the grindstone has something to do with this item. */
    @JvmStatic
    fun isGrindable(stack: ItemStack): Boolean = RECIPES.containsKey(stack.item)

    /** How many items the given input yields, or 0. Used by the sneak-right-click shortcut. */
    @JvmStatic
    fun outputFor(stack: ItemStack): Pair<Item, Int>? = RECIPES[stack.item]

    /**
     * The grindstone result for a pair of input slots.
     *
     * Deliberately limited to a single item in a single slot: the result slot's `onTake` clears
     * both input slots, so anything larger would silently swallow the rest of the stack.
     */
    @JvmStatic
    fun resultFor(input: ItemStack, additional: ItemStack): ItemStack {
        val single = when {
            !input.isEmpty && additional.isEmpty -> input
            !additional.isEmpty && input.isEmpty -> additional
            else -> return ItemStack.EMPTY
        }
        if (single.count != 1) {
            return ItemStack.EMPTY
        }
        val recipe = RECIPES[single.item] ?: return ItemStack.EMPTY
        return ItemStack(recipe.first, recipe.second)
    }

    /** True when the item may sit in a grindstone input slot at all. */
    @JvmStatic
    fun mayPlace(stack: ItemStack): Boolean = RECIPES.containsKey(stack.item) && stack.count == 1
}
