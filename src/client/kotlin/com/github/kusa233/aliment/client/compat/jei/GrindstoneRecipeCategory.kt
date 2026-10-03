package com.github.kusa233.aliment.client.compat.jei

import com.github.kusa233.aliment.Aliment
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.drawable.IDrawableStatic
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.recipe.types.IRecipeType
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks

class GrindstoneRecipeCategory(guiHelper: IGuiHelper) : IRecipeCategory<AlimentGrindstoneRecipe> {

    private val icon: IDrawable = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, ItemStack(Blocks.GRINDSTONE))
    private val slotDrawable: IDrawableStatic = guiHelper.getSlotDrawable()

    override fun getRecipeType(): IRecipeType<AlimentGrindstoneRecipe> = RECIPE_TYPE

    override fun getTitle(): Component = Component.translatable("gui.aliment.jei.category.grindstone")

    override fun getWidth(): Int = 82

    override fun getHeight(): Int = 26

    override fun getIcon(): IDrawable = icon

    override fun setRecipe(
        builder: IRecipeLayoutBuilder,
        recipe: AlimentGrindstoneRecipe,
        focuses: IFocusGroup
    ) {
        builder.addSlot(RecipeIngredientRole.INPUT, 4, 4)
            .add(recipe.input)
            .setBackground(slotDrawable, -1, -1)

        builder.addSlot(RecipeIngredientRole.OUTPUT, 58, 4)
            .add(recipe.output)
            .setBackground(slotDrawable, -1, -1)
    }

    override fun createRecipeExtras(
        builder: IRecipeExtrasBuilder,
        recipe: AlimentGrindstoneRecipe,
        focuses: IFocusGroup
    ) {
        builder.addRecipeArrowWidget().setPosition(28, 4)
    }

    companion object {
        val RECIPE_TYPE: IRecipeType<AlimentGrindstoneRecipe> =
            IRecipeType.create(Aliment.MOD_ID, "grindstone", AlimentGrindstoneRecipe::class.java)
    }
}
