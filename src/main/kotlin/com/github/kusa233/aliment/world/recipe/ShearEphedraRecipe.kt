package com.github.kusa233.aliment.world.recipe

import com.github.kusa233.aliment.registry.AlimentItems
import com.github.kusa233.aliment.registry.AlimentRecipes
import com.mojang.serialization.MapCodec
import net.minecraft.core.NonNullList
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.CustomRecipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.level.Level

/**
 * Crafting recipe: Shears + Ephedra -> Crushed Ephedra.
 *
 * Shears takes 1 point of durability damage upon crafting and remains in the crafting grid.
 * If the damage reaches its max durability, the shears break and disappear.
 */
class ShearEphedraRecipe(category: CraftingBookCategory = CraftingBookCategory.MISC) : CustomRecipe() {

    override fun matches(input: CraftingInput, level: Level): Boolean {
        var hasShears = false
        var hasEphedra = false

        for (i in 0 until input.size()) {
            val stack = input.getItem(i)
            if (stack.isEmpty) continue

            if (stack.`is`(Items.SHEARS)) {
                if (hasShears) return false
                hasShears = true
            } else if (stack.`is`(AlimentItems.EPHEDRA)) {
                if (hasEphedra) return false
                hasEphedra = true
            } else {
                return false
            }
        }

        return hasShears && hasEphedra
    }

    override fun assemble(input: CraftingInput): ItemStack {
        return ItemStack(AlimentItems.CRUSHED_EPHEDRA, 1)
    }

    override fun getRemainingItems(input: CraftingInput): NonNullList<ItemStack> {
        val remainder = NonNullList.withSize(input.size(), ItemStack.EMPTY)
        for (i in 0 until input.size()) {
            val stack = input.getItem(i)
            if (stack.`is`(Items.SHEARS)) {
                val newDamage = stack.damageValue + 1
                if (newDamage < stack.maxDamage) {
                    val damaged = stack.copyWithCount(1)
                    damaged.damageValue = newDamage
                    remainder[i] = damaged
                }
            }
        }
        return remainder
    }

    override fun getSerializer(): RecipeSerializer<ShearEphedraRecipe> = AlimentRecipes.SHEAR_EPHEDRA_SERIALIZER

    companion object {
        val INSTANCE = ShearEphedraRecipe()
        val MAP_CODEC: MapCodec<ShearEphedraRecipe> = MapCodec.unit(INSTANCE)
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ShearEphedraRecipe> = StreamCodec.unit(INSTANCE)
    }
}
