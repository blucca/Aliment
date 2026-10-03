package com.github.kusa233.aliment.client.compat.jei

import com.github.kusa233.aliment.registry.Registration
import com.github.kusa233.aliment.world.AlimentGrinding
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks

@JeiPlugin
class AlimentJeiPlugin : IModPlugin {

    override fun getPluginUid(): Identifier = Registration.id("jei_plugin")

    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        registration.addRecipeCategories(
            GrindstoneRecipeCategory(registration.jeiHelpers.guiHelper)
        )
    }

    override fun registerRecipes(registration: IRecipeRegistration) {
        val recipes = AlimentGrinding.allRecipes().map { (inputItem, outputPair) ->
            AlimentGrindstoneRecipe(
                input = ItemStack(inputItem),
                output = ItemStack(outputPair.first, outputPair.second)
            )
        }
        registration.addRecipes(GrindstoneRecipeCategory.RECIPE_TYPE, recipes)
    }

    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        registration.addCraftingStation(
            GrindstoneRecipeCategory.RECIPE_TYPE,
            Blocks.GRINDSTONE
        )
    }
}
