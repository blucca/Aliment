package com.github.kusa233.aliment.world.item

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/**
 * The reading a bloodied test strip carries.
 *
 * A strip is not a live sensor: it holds the drop that was caught on it, so the glucose it reports
 * is the one the player had **when the blood was taken**, not the one they have when they get round
 * to reading it. That is the whole point of taking a sample - a reading that changed with the
 * player's blood sugar would say nothing about the moment it was taken, and would make waiting to
 * see a peak impossible.
 *
 * The value rides on the stack as its own component, so it survives the strip being put in a chest,
 * dropped, or handed to another player, exactly like the concentration on a bottle of wine (see
 * [WineItem]).
 */
object BloodiedTestStripItem {

    /** The component key holding the captured reading, in mmol/L. */
    private const val GLUCOSE_KEY = "glucose"

    /**
     * A bloodied strip carrying [glucose], in mmol/L.
     *
     * The caller is expected to have just taken the sample, so it passes the body's glucose as it
     * stands; nothing here reads the player.
     */
    fun createStack(item: Item, glucose: Float): ItemStack {
        val stack = ItemStack(item)
        val tag = CompoundTag()
        tag.putFloat(GLUCOSE_KEY, glucose)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        return stack
    }

    /**
     * The reading [stack] took, in mmol/L.
     *
     * [fallback] is only ever reached by a strip with no sample on it at all - one left over from
     * before this data existed, or one spawned by a command - and is the body's current glucose, so
     * such a strip behaves the way every strip used to.
     */
    fun getGlucose(stack: ItemStack, fallback: Float): Float {
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return fallback
        return customData.copyTag().getFloatOr(GLUCOSE_KEY, fallback)
    }
}
