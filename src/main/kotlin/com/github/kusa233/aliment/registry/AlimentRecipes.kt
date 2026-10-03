package com.github.kusa233.aliment.registry

import com.github.kusa233.aliment.world.recipe.ShearEphedraRecipe
import net.minecraft.world.item.crafting.RecipeSerializer

object AlimentRecipes {

    val SHEAR_EPHEDRA_SERIALIZER: RecipeSerializer<ShearEphedraRecipe> = Registration.registerRecipeSerializer(
        "crafting_shear_ephedra",
        RecipeSerializer(ShearEphedraRecipe.MAP_CODEC, ShearEphedraRecipe.STREAM_CODEC),
    )

    fun initialize() {
        // Classloading triggers registration
    }
}
