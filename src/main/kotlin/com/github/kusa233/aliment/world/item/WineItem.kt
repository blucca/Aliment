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
 * Wine & Alcohol item.
 *
 * Brewed in the glass fermentation tank (7% ethanol) or distilled through the glass
 * condenser pipe into a cauldron (40% ethanol). Further distillation in a heated cauldron
 * yields 75% and 98% alcohol.
 * When concentration >= 70%, the item name is "Alcohol" (酒精) instead of "Wine" (酒).
 */
class WineItem(properties: Properties) : Item(properties) {

    override fun getName(stack: ItemStack): Component {
        val concentration = getConcentration(stack)
        return if (concentration >= 0.70f) {
            Component.translatable("item.aliment.alcohol")
        } else {
            Component.translatable("item.aliment.wine")
        }
    }

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
        val key = if (concentration >= 0.70f) "tooltip.aliment.alcohol.concentration" else "tooltip.aliment.wine.concentration"
        tooltip.accept(
            Component.translatable(key, "$percentage%")
                .withStyle(ChatFormatting.GOLD),
        )
    }

    companion object {
        const val DEFAULT_FERMENTED_CONCENTRATION: Float = 0.07f
        const val DISTILLED_CONCENTRATION: Float = 0.40f
        const val ALCOHOL_75_CONCENTRATION: Float = 0.75f
        const val ALCOHOL_98_CONCENTRATION: Float = 0.98f

        fun getConcentration(stack: ItemStack): Float {
            val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return DEFAULT_FERMENTED_CONCENTRATION
            return customData.copyTag().getFloatOr("concentration", DEFAULT_FERMENTED_CONCENTRATION)
        }

        fun createStack(item: Item, concentration: Float): ItemStack {
            val stack = ItemStack(item)
            val tag = CompoundTag()
            tag.putFloat("concentration", concentration)
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            return stack
        }
    }
}
