package com.github.kusa233.outbreak.registry

import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.BoatItem
import net.minecraft.world.item.HangingSignItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.item.SignItem
import net.minecraft.world.item.component.Consumables
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect

/**
 * Items that are not plain `BlockItem`s: the two sign variants and the boat pair.
 * Everything else is registered next to its block in [OutbreakBlocks].
 */
object OutbreakItems {

    val WILLOW_SIGN: Item = Registration.registerItem(
        "willow_sign",
        Item.Properties().stacksTo(16).useBlockDescriptionPrefix(),
    ) { properties ->
        SignItem(OutbreakBlocks.WILLOW_SIGN, OutbreakBlocks.WILLOW_WALL_SIGN, properties)
    }

    val WILLOW_HANGING_SIGN: Item = Registration.registerItem(
        "willow_hanging_sign",
        Item.Properties().stacksTo(16).useBlockDescriptionPrefix(),
    ) { properties ->
        HangingSignItem(OutbreakBlocks.WILLOW_HANGING_SIGN, OutbreakBlocks.WILLOW_WALL_HANGING_SIGN, properties)
    }

    val WILLOW_BOAT: Item = Registration.registerItem(
        "willow_boat",
        Item.Properties().stacksTo(1),
    ) { properties ->
        BoatItem(OutbreakEntities.WILLOW_BOAT, properties)
    }

    val WILLOW_CHEST_BOAT: Item = Registration.registerItem(
        "willow_chest_boat",
        Item.Properties().stacksTo(1),
    ) { properties ->
        BoatItem(OutbreakEntities.WILLOW_CHEST_BOAT, properties)
    }

    // ---------------------------------------------------------------- willow bark & soup

    /** Dropped when a willow log or willow wood block is stripped with an axe. */
    val WILLOW_BARK: Item = Registration.registerItem("willow_bark", Item.Properties()) { Item(it) }

    /** Ground out of [WILLOW_BARK] on a grindstone; the ingredient of the soup. */
    val WILLOW_BARK_PIECES: Item = Registration.registerItem("willow_bark_pieces", Item.Properties()) { Item(it) }

    /**
     * Raw willow bark soup still contains the bark's irritants: every serving has a 25% chance
     * of nausea and a 15% chance of hunger, both for 20 seconds.
     */
    val RAW_WILLOW_BARK_SOUP_BOTTLE: Item = Registration.registerItem(
        "raw_willow_bark_soup_bottle",
        soupProperties(Items.GLASS_BOTTLE, drink = true, raw = true),
    ) { Item(it) }

    val RAW_WILLOW_BARK_SOUP_BOWL: Item = Registration.registerItem(
        "raw_willow_bark_soup_bowl",
        soupProperties(Items.BOWL, drink = false, raw = true),
    ) { Item(it) }

    /** Cooking drives the irritants off, so the cooked soup is safe to eat. */
    val WILLOW_BARK_SOUP_BOTTLE: Item = Registration.registerItem(
        "willow_bark_soup_bottle",
        soupProperties(Items.GLASS_BOTTLE, drink = true, raw = false),
    ) { Item(it) }

    val WILLOW_BARK_SOUP_BOWL: Item = Registration.registerItem(
        "willow_bark_soup_bowl",
        soupProperties(Items.BOWL, drink = false, raw = false),
    ) { Item(it) }

    /** Hunger restored by one serving. Both the raw and the cooked soup give one drumstick. */
    private const val SOUP_NUTRITION = 1

    /**
     * `saturationModifier` is a multiplier, not an absolute value: the saturation a food adds is
     * `nutrition * saturationModifier * 2`. With nutrition 1 that means 0.5 gives 1 saturation
     * for the raw soup and 1.0 gives 2 saturation for the cooked one.
     */
    private const val RAW_SOUP_SATURATION = 0.5F
    private const val COOKED_SOUP_SATURATION = 1.0F

    /** Both effects last 20 seconds. */
    private const val SOUP_EFFECT_TICKS = 400
    private const val RAW_SOUP_NAUSEA_CHANCE = 0.25F
    private const val RAW_SOUP_HUNGER_CHANCE = 0.15F

    /**
     * Bottles behave like honey bottles (stack of 16, drink animation, gives the glass bottle
     * back); bowls behave like mushroom stew (stack of one, eat animation, gives the bowl back).
     */
    private fun soupProperties(container: Item, drink: Boolean, raw: Boolean): Item.Properties {
        val food = FoodProperties.Builder()
            .nutrition(SOUP_NUTRITION)
            .saturationModifier(if (raw) RAW_SOUP_SATURATION else COOKED_SOUP_SATURATION)
            .build()

        val consumable = (if (drink) Consumables.defaultDrink() else Consumables.defaultFood())
        if (raw) {
            consumable.onConsume(
                ApplyStatusEffectsConsumeEffect(
                    MobEffectInstance(MobEffects.NAUSEA, SOUP_EFFECT_TICKS, 0),
                    RAW_SOUP_NAUSEA_CHANCE,
                ),
            )
            consumable.onConsume(
                ApplyStatusEffectsConsumeEffect(
                    MobEffectInstance(MobEffects.HUNGER, SOUP_EFFECT_TICKS, 0),
                    RAW_SOUP_HUNGER_CHANCE,
                ),
            )
        }

        val properties = Item.Properties().food(food, consumable.build()).usingConvertsTo(container)
        return if (drink) properties.stacksTo(16) else properties.stacksTo(1)
    }

    fun initialize() {
    }
}
