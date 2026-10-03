package com.github.kusa233.aliment.registry

import com.github.kusa233.aliment.Aliment
import com.github.kusa233.aliment.world.item.WineItem
import net.minecraft.core.Direction
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.BoatItem
import net.minecraft.world.item.HangingSignItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.StandingAndWallBlockItem
import net.minecraft.world.item.component.Consumable
import net.minecraft.world.item.component.Consumables
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect

/**
 * Items that are not plain `BlockItem`s: the two sign variants and the boat pair.
 * Everything else is registered next to its block in [AlimentBlocks].
 */
object AlimentItems {

    /**
     * The standing/wall sign pair.
     *
     * `useBlockDescriptionPrefix()` is not optional here: a sign's name lives under
     * `block.Aliment.willow_sign` (that is where vanilla keeps its own signs, and where
     * `tools/gen_data.ps1` writes it), so without it the item looks up `item.Aliment.willow_sign`
     * and the player is shown the raw key in their inventory instead of a name.
     */
    val WILLOW_SIGN: Item = Registration.registerItem(
        "willow_sign",
        Item.Properties().stacksTo(16).useBlockDescriptionPrefix().signText(),
    ) { properties ->
        StandingAndWallBlockItem(AlimentBlocks.WILLOW_SIGN, AlimentBlocks.WILLOW_WALL_SIGN, Direction.DOWN, properties)
    }

    val WILLOW_HANGING_SIGN: Item = Registration.registerItem(
        "willow_hanging_sign",
        Item.Properties().stacksTo(16).useBlockDescriptionPrefix(),
    ) { properties ->
        HangingSignItem(AlimentBlocks.WILLOW_HANGING_SIGN, AlimentBlocks.WILLOW_WALL_HANGING_SIGN, properties)
    }

    val WILLOW_BOAT: Item = Registration.registerItem(
        "willow_boat",
        Item.Properties().stacksTo(1),
    ) { properties ->
        BoatItem(AlimentEntities.WILLOW_BOAT, properties)
    }

    val WILLOW_CHEST_BOAT: Item = Registration.registerItem(
        "willow_chest_boat",
        Item.Properties().stacksTo(1),
    ) { properties ->
        BoatItem(AlimentEntities.WILLOW_CHEST_BOAT, properties)
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

    // ---------------------------------------------------------------- salt chain

    /** Ground out of [AlimentBlocks.ROCK_SALT_ORE] - nine per ore. */
    val CRUDE_SALT: Item = Registration.registerItem("crude_salt", Item.Properties()) { Item(it) }

    /** [CRUDE_SALT] ground again; this is what goes into the cauldron. */
    val CRUDE_SALT_POWDER: Item = Registration.registerItem("crude_salt_powder", Item.Properties()) { Item(it) }

    /** What is left when a brine cauldron boils dry. Refined, so it is almost pure sodium chloride. */
    val SALT_POWDER: Item = Registration.registerItem("salt_powder", Item.Properties()) { Item(it) }

    /** Right-click a brine cauldron to skip it to the next evaporation stage. Wears out. */
    val STIRRING_ROD: Item = Registration.registerItem(
        "stirring_rod",
        Item.Properties().durability(16).stacksTo(1),
    ) { Item(it) }

    /** A corticosteroid injection: a much stronger anti-inflammatory than willow bark soup. */
    val DEXAMETHASONE_INJECTION: Item = Registration.registerItem(
        "dexamethasone_injection",
        Item.Properties().stacksTo(8),
    ) { Item(it) }

    // ---------------------------------------------------------------- salted food

    val CRUDE_SALT_WATER: Item = Registration.registerItem(
        "crude_salt_water",
        brineProperties(Items.GLASS_BOTTLE),
    ) { Item(it) }

    val SALT_WATER: Item = Registration.registerItem(
        "salt_water",
        brineProperties(Items.GLASS_BOTTLE),
    ) { Item(it) }

    // ---------------------------------------------------------------- biome water

    /**
     * Filling a glass bottle from a water source gives different water depending on the biome:
     * ordinary rivers keep vanilla's water bottle, but swamps and oceans have their own.
     */
    val SWAMP_WATER_BOTTLE: Item = Registration.registerItem(
        "swamp_water_bottle",
        brineProperties(Items.GLASS_BOTTLE),
    ) { Item(it) }

    val SEA_WATER_BOTTLE: Item = Registration.registerItem(
        "sea_water_bottle",
        brineProperties(Items.GLASS_BOTTLE),
    ) { Item(it) }

    val CRUDE_SALT_SWAMP_WATER: Item = Registration.registerItem(
        "crude_salt_swamp_water",
        brineProperties(Items.GLASS_BOTTLE),
    ) { Item(it) }

    val SALT_SWAMP_WATER: Item = Registration.registerItem(
        "salt_swamp_water",
        brineProperties(Items.GLASS_BOTTLE),
    ) { Item(it) }

    val CRUDE_SALT_SEA_WATER: Item = Registration.registerItem(
        "crude_salt_sea_water",
        brineProperties(Items.GLASS_BOTTLE),
    ) { Item(it) }

    val SALT_SEA_WATER: Item = Registration.registerItem(
        "salt_sea_water",
        brineProperties(Items.GLASS_BOTTLE),
    ) { Item(it) }

    val CRUDE_SALT_MUSHROOM_STEW: Item = Registration.registerItem(
        "crude_salt_mushroom_stew",
        stewProperties(Items.BOWL, nutrition = 6, saturation = 0.6F),
    ) { Item(it) }

    val SALT_MUSHROOM_STEW: Item = Registration.registerItem(
        "salt_mushroom_stew",
        stewProperties(Items.BOWL, nutrition = 6, saturation = 0.6F),
    ) { Item(it) }

    val CRUDE_SALT_WILLOW_BARK_SOUP: Item = Registration.registerItem(
        "crude_salt_willow_bark_soup",
        stewProperties(Items.BOWL, nutrition = SOUP_NUTRITION, saturation = COOKED_SOUP_SATURATION),
    ) { Item(it) }

    val SALT_WILLOW_BARK_SOUP: Item = Registration.registerItem(
        "salt_willow_bark_soup",
        stewProperties(Items.BOWL, nutrition = SOUP_NUTRITION, saturation = COOKED_SOUP_SATURATION),
    ) { Item(it) }

    /** Salted raw soup: still carries the bark's irritants, so it keeps the raw soup effects. */
    val CRUDE_SALT_RAW_WILLOW_BARK_SOUP: Item = Registration.registerItem(
        "crude_salt_raw_willow_bark_soup",
        stewProperties(Items.BOWL, nutrition = SOUP_NUTRITION, saturation = RAW_SOUP_SATURATION, raw = true),
    ) { Item(it) }

    val SALT_RAW_WILLOW_BARK_SOUP: Item = Registration.registerItem(
        "salt_raw_willow_bark_soup",
        stewProperties(Items.BOWL, nutrition = SOUP_NUTRITION, saturation = RAW_SOUP_SATURATION, raw = true),
    ) { Item(it) }

    /**
     * Salt water is a drink that restores no hunger on its own; its whole point is the water and
     * the salt, which the physiology system picks up when it is swallowed.
     */
    private fun brineProperties(container: Item): Item.Properties {
        val food = FoodProperties.Builder().nutrition(0).saturationModifier(0f).alwaysEdible().build()
        return Item.Properties()
            .food(food, Consumables.defaultDrink().build())
            .usingConvertsTo(container)
            .stacksTo(16)
    }

    private fun stewProperties(
        container: Item,
        nutrition: Int,
        saturation: Float,
        raw: Boolean = false,
    ): Item.Properties = Item.Properties()
        .food(buildFood(nutrition, saturation), buildConsumable(drink = false, raw = raw).build())
        .usingConvertsTo(container)
        .stacksTo(1)

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

    private fun buildFood(nutrition: Int, saturation: Float, alwaysEdible: Boolean = false): FoodProperties {
        val builder = FoodProperties.Builder()
            .nutrition(nutrition)
            .saturationModifier(saturation)
        if (alwaysEdible) {
            builder.alwaysEdible()
        }
        return builder.build()
    }

    /**
     * Bottles behave like honey bottles (stack of 16, drink animation, gives the glass bottle
     * back); bowls behave like mushroom stew (stack of one, eat animation, gives the bowl back).
     */
    private fun soupProperties(container: Item, drink: Boolean, raw: Boolean): Item.Properties {
        val food = buildFood(SOUP_NUTRITION, if (raw) RAW_SOUP_SATURATION else COOKED_SOUP_SATURATION)
        val properties = Item.Properties()
            .food(food, buildConsumable(drink, raw).build())
            .usingConvertsTo(container)
        return if (drink) properties.stacksTo(16) else properties.stacksTo(1)
    }

    /** Raw willow bark soup still contains the bark's irritants: nausea and hunger. */
    private fun buildConsumable(drink: Boolean, raw: Boolean): Consumable.Builder {
        val consumable = if (drink) Consumables.defaultDrink() else Consumables.defaultFood()
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
        return consumable
    }

    // ---------------------------------------------------------------- mandrake

    /**
     * The mandrake fruit: what a ripe plant gives up, and the only source of seed.
     *
     * It is edible, and eating it is the point: one fruit is a full dose of scopolamine, which is a
     * trip and, past a couple of them, an overdose. `alwaysEdible` because a drug is not a meal - it
     * has to go down whether or not the player is hungry.
     */
    val MANDRAKE_FRUIT: Item = Registration.registerItem(
        "mandrake_fruit",
        Item.Properties().food(mandrakeFood()),
    ) { Item(it) }

    /**
     * Mandrake seeds, sown straight into soil and - because there is no seed item class left in this
     * version - a plain [BlockItem] that is also edible. Aiming at a block plants it; aiming at
     * nothing eats it, which is the same 0.75 of a dose with less of the delirium.
     */
    val MANDRAKE_SEEDS: Item = Registration.registerItem(
        "mandrake_seeds",
        Item.Properties().food(mandrakeFood()),
    ) { BlockItem(AlimentBlocks.MANDRAKE, it) }

    // ---------------------------------------------------------------- gymnopilus

    /**
     * The gymnopilus mushroom, raw. It is the block's item form.
     *
     * Eating it raw is the whole point: a dose of psilocybin and a dose of psilocin at once, which is
     * why it comes on fast and then keeps topping itself up for the next half a game day. Cooking
     * destroys both compounds, so the cooked mushroom is just food.
     */
    val GYMNOPILUS: Item = Registration.registerItem(
        "gymnopilus",
        Item.Properties().food(mushroomFood(3, 4f)),
    ) { BlockItem(AlimentBlocks.GYMNOPILUS, it) }

    /** The same mushroom out of a furnace, a smoker or a campfire: 4 hunger, 5 saturation, no trip. */
    val COOKED_GYMNOPILUS: Item = Registration.registerItem(
        "cooked_gymnopilus",
        Item.Properties().food(mushroomFood(4, 5f)),
    ) { Item(it) }

    /** Brewer's yeast for the fermentation tank. */
    val BREWER_YEAST: Item = Registration.registerItem(
        "brewer_yeast",
        Item.Properties(),
    ) { Item(it) }

    /** Wine / Booze with ethanol concentration recorded in NBT. */
    val WINE: Item = Registration.registerItem(
        "wine",
        Item.Properties()
            .food(buildFood(1, 0.5f, alwaysEdible = true), Consumables.defaultDrink().build())
            .usingConvertsTo(Items.GLASS_BOTTLE)
            .stacksTo(16),
    ) { WineItem(it) }

    // ---------------------------------------------------------------- ephedra & ephedrine

    /** Ephedra (麻黄): herbal twigs, edible or plantable into soil/sand. */
    val EPHEDRA: Item = Registration.registerItem(
        "ephedra",
        Item.Properties().food(ephedraFood()),
    ) { BlockItem(AlimentBlocks.EPHEDRA, it) }

    /** Crushed Ephedra (碎麻黄): chopped/crushed ephedra herb flakes obtained by shears or grindstone. */
    val CRUSHED_EPHEDRA: Item = Registration.registerItem(
        "crushed_ephedra",
        Item.Properties(),
    ) { Item(it) }

    /** Ephedrine Potion (麻黄碱药水): herbal stimulant medicine brewed from crushed ephedra in a glass bottle. */
    val EPHEDRINE: Item = Registration.registerItem(
        "ephedrine",
        Item.Properties()
            .food(ephedrineFood(), Consumables.defaultDrink().build())
            .usingConvertsTo(Items.GLASS_BOTTLE)
            .stacksTo(16),
    ) { Item(it) }

    // ---------------------------------------------------------------- coptis (黄连)

    /** Coptis (黄连): medicinal herb containing berberine (+1.1), edible or plantable. */
    val COPTIS: Item = Registration.registerItem(
        "coptis",
        Item.Properties().food(herbFood()),
    ) { BlockItem(AlimentBlocks.COPTIS, it) }

    /** Crushed Coptis (碎黄连): ground coptis herb flakes. */
    val CRUSHED_COPTIS: Item = Registration.registerItem(
        "crushed_coptis",
        Item.Properties(),
    ) { Item(it) }

    /** Coptis Potion (黄连药水): concentrated berberine potion brewed with crushed coptis. */
    val COPTIS_POTION: Item = Registration.registerItem(
        "coptis_potion",
        Item.Properties()
            .food(potionFood(), Consumables.defaultDrink().build())
            .usingConvertsTo(Items.GLASS_BOTTLE)
            .stacksTo(16),
    ) { Item(it) }

    // ---------------------------------------------------------------- phellodendron (黄柏)

    /** Phellodendron (黄柏): medicinal bark/shrub containing berberine (+0.6), edible or plantable. */
    val PHELLODENDRON: Item = Registration.registerItem(
        "phellodendron",
        Item.Properties().food(herbFood()),
    ) { BlockItem(AlimentBlocks.PHELLODENDRON, it) }

    /** Crushed Phellodendron (碎黄柏): ground phellodendron herb flakes. */
    val CRUSHED_PHELLODENDRON: Item = Registration.registerItem(
        "crushed_phellodendron",
        Item.Properties(),
    ) { Item(it) }

    /** Phellodendron Potion (黄柏药水): berberine potion brewed with crushed phellodendron. */
    val PHELLODENDRON_POTION: Item = Registration.registerItem(
        "phellodendron_potion",
        Item.Properties()
            .food(potionFood(), Consumables.defaultDrink().build())
            .usingConvertsTo(Items.GLASS_BOTTLE)
            .stacksTo(16),
    ) { Item(it) }

    // ---------------------------------------------------------------- licorice (甘草)

    /** Licorice (甘草): medicinal herb containing glycyrrhizin (+1.1), edible or plantable. */
    val LICORICE: Item = Registration.registerItem(
        "licorice",
        Item.Properties().food(herbFood()),
    ) { BlockItem(AlimentBlocks.LICORICE, it) }

    /** Crushed Licorice (碎甘草): ground licorice herb flakes. */
    val CRUSHED_LICORICE: Item = Registration.registerItem(
        "crushed_licorice",
        Item.Properties(),
    ) { Item(it) }

    /** Licorice Potion (甘草药水): concentrated glycyrrhizin potion brewed with crushed licorice. */
    val LICORICE_POTION: Item = Registration.registerItem(
        "licorice_potion",
        Item.Properties()
            .food(potionFood(), Consumables.defaultDrink().build())
            .usingConvertsTo(Items.GLASS_BOTTLE)
            .stacksTo(16),
    ) { Item(it) }

    fun createWine(concentration: Float): ItemStack = WineItem.createStack(WINE, concentration)

    private fun ephedraFood(): FoodProperties = buildFood(1, 0.2f, alwaysEdible = true)

    private fun ephedrineFood(): FoodProperties = buildFood(0, 0f, alwaysEdible = true)

    private fun herbFood(): FoodProperties = buildFood(1, 0.2f, alwaysEdible = true)

    private fun potionFood(): FoodProperties = buildFood(0, 0f, alwaysEdible = true)

    /**
     * Food written the way a player reads it off the tooltip: hunger first, saturation points second.
     *
     * Minecraft stores saturation as a *multiplier* on hunger - points = hunger * multiplier * 2 - so
     * the multiplier is derived here rather than written down, and cannot drift away from the two
     * numbers that actually matter. `alwaysEdible` because a mushroom is not a meal: being full must
     * not stand between a player and the trip.
     */
    private fun mushroomFood(nutrition: Int, saturationPoints: Float): FoodProperties =
        buildFood(nutrition, saturationPoints / (nutrition * 2f), alwaysEdible = true)

    /** One nutrition and a token of saturation: a mandrake fills a stomach, it does not feed one. */
    private fun mandrakeFood(): FoodProperties = buildFood(1, 0.1f, alwaysEdible = true)

    fun initialize() {
    }
}
