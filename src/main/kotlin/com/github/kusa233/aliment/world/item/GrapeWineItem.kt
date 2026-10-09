package com.github.kusa233.aliment.world.item

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.TooltipDisplay
import java.util.function.Consumer

/**
 * Grape wine.
 *
 * Fermented in the glass fermentation tank from grapes, sugar and water with brewer's yeast, and
 * bottled straight out of the tank - unlike beer, it is not distilled, because a wine is what the
 * tank makes rather than what a cauldron concentrates.
 *
 * The 5% is the point of it being a separate item from [WineItem]: that one is the mod's generic
 * "whatever the tank fermented" bottle and sits at 7%, and a player who reaches for a glass of
 * grape wine is choosing the weaker of the two on purpose.
 */
class GrapeWineItem(properties: Properties) : Item(properties) {

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipDisplay: TooltipDisplay,
        tooltip: Consumer<Component>,
        flag: TooltipFlag,
    ) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltip, flag)
        val concentration = getConcentration(stack)
        val percentage = (concentration * 100).toInt()
        tooltip.accept(
            Component.translatable("tooltip.aliment.wine.concentration", "$percentage%")
                .withStyle(ChatFormatting.GOLD),
        )
    }

    companion object {
        /** 5% ethanol: a wine, not a spirit. */
        const val DEFAULT_GRAPE_WINE_CONCENTRATION: Float = 0.05f

        fun getConcentration(stack: ItemStack): Float {
            val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return DEFAULT_GRAPE_WINE_CONCENTRATION
            return customData.copyTag().getFloatOr("concentration", DEFAULT_GRAPE_WINE_CONCENTRATION)
        }

        fun createStack(
            item: Item,
            concentration: Float = DEFAULT_GRAPE_WINE_CONCENTRATION,
        ): ItemStack {
            val stack = ItemStack(item)
            val tag = CompoundTag()
            tag.putFloat("concentration", concentration)
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            return stack
        }
    }
}
