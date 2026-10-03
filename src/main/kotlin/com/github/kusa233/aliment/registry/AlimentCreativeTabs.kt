package com.github.kusa233.aliment.registry

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike

/**
 * aliment owns a single creative tab so the mod's content stays in one place as it grows.
 *
 * The tab sits in the top row right after the seven vanilla slots. Its title is localised as
 * `itemGroup.Aliment.main` (see `assets/aliment/lang`).
 */
object AlimentCreativeTabs {

    /** Placed after the seven vanilla top-row tab slots. */
    private const val TAB_COLUMN = 7

    private val TAB_KEY: ResourceKey<CreativeModeTab> =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, Registration.id("main"))

    /**
     * Everything the mod can currently show, in the order it appears in the tab.
     *
     * Blocks without an item form (`willow_vines_plant`, the wall signs, the potted sapling and
     * the soup cauldron) are deliberately absent because they cannot be held.
     */
    private val CONTENT: List<ItemLike> = listOf(
        // logs and bark
        AlimentBlocks.WILLOW_LOG,
        AlimentBlocks.WILLOW_WOOD,
        AlimentBlocks.STRIPPED_WILLOW_LOG,
        AlimentBlocks.STRIPPED_WILLOW_WOOD,
        AlimentItems.WILLOW_BARK,
        AlimentItems.WILLOW_BARK_PIECES,
        // planks and shaped blocks
        AlimentBlocks.WILLOW_PLANKS,
        AlimentBlocks.WILLOW_STAIRS,
        AlimentBlocks.WILLOW_SLAB,
        AlimentBlocks.WILLOW_FENCE,
        AlimentBlocks.WILLOW_FENCE_GATE,
        AlimentBlocks.WILLOW_DOOR,
        AlimentBlocks.WILLOW_TRAPDOOR,
        AlimentBlocks.WILLOW_PRESSURE_PLATE,
        AlimentBlocks.WILLOW_BUTTON,
        AlimentBlocks.WILLOW_SHELF,
        // foliage
        AlimentBlocks.WILLOW_LEAVES,
        AlimentBlocks.WILLOW_SAPLING,
        AlimentBlocks.WILLOW_VINES,
        // signs and boats
        AlimentItems.WILLOW_SIGN,
        AlimentItems.WILLOW_HANGING_SIGN,
        AlimentItems.WILLOW_BOAT,
        AlimentItems.WILLOW_CHEST_BOAT,
        // soups
        AlimentItems.RAW_WILLOW_BARK_SOUP_BOTTLE,
        AlimentItems.RAW_WILLOW_BARK_SOUP_BOWL,
        AlimentItems.WILLOW_BARK_SOUP_BOTTLE,
        AlimentItems.WILLOW_BARK_SOUP_BOWL,
        // salt chain
        AlimentBlocks.ROCK_SALT_ORE,
        AlimentItems.CRUDE_SALT,
        AlimentItems.CRUDE_SALT_POWDER,
        AlimentItems.SALT_POWDER,
        AlimentItems.STIRRING_ROD,
        AlimentItems.DEXAMETHASONE_INJECTION,
        AlimentItems.CRUDE_SALT_WATER,
        AlimentItems.SALT_WATER,
        AlimentItems.SWAMP_WATER_BOTTLE,
        AlimentItems.SEA_WATER_BOTTLE,
        AlimentItems.CRUDE_SALT_SWAMP_WATER,
        AlimentItems.SALT_SWAMP_WATER,
        AlimentItems.CRUDE_SALT_SEA_WATER,
        AlimentItems.SALT_SEA_WATER,
        AlimentItems.CRUDE_SALT_MUSHROOM_STEW,
        AlimentItems.SALT_MUSHROOM_STEW,
        AlimentItems.CRUDE_SALT_WILLOW_BARK_SOUP,
        AlimentItems.SALT_WILLOW_BARK_SOUP,
        AlimentItems.CRUDE_SALT_RAW_WILLOW_BARK_SOUP,
        AlimentItems.SALT_RAW_WILLOW_BARK_SOUP,
        // mandrake: the plant has no item form, so the tab carries the fruit and the seeds
        AlimentItems.MANDRAKE_FRUIT,
        AlimentItems.MANDRAKE_SEEDS,
        // gymnopilus: raw and cooked, the second one only food
        AlimentItems.GYMNOPILUS,
        AlimentItems.COOKED_GYMNOPILUS,
        // brewing & distillation
        AlimentBlocks.FERMENTATION_TANK,
        AlimentBlocks.CONDENSER_PIPE,
        AlimentItems.BREWER_YEAST,
        AlimentItems.WINE,
        // ephedra & ephedrine
        AlimentItems.EPHEDRA,
        AlimentItems.CRUSHED_EPHEDRA,
        AlimentItems.EPHEDRINE,
        // traditional herbs (coptis, phellodendron, licorice)
        AlimentItems.COPTIS,
        AlimentItems.CRUSHED_COPTIS,
        AlimentItems.COPTIS_POTION,
        AlimentItems.PHELLODENDRON,
        AlimentItems.CRUSHED_PHELLODENDRON,
        AlimentItems.PHELLODENDRON_POTION,
        AlimentItems.LICORICE,
        AlimentItems.CRUSHED_LICORICE,
        AlimentItems.LICORICE_POTION,
        // seaweed
        AlimentItems.SEAWEED,
        AlimentItems.COOKED_SEAWEED,
        AlimentItems.CRUSHED_SEAWEED,
        AlimentItems.SEAWEED_IODIZED_SALT,
    )

    /**
     * Touching this object at all runs the object initialiser, which is what registers the tab
     * in [BuiltInRegistries.CREATIVE_MODE_TAB].
     */
    val MAIN: CreativeModeTab = Registry.register(
        BuiltInRegistries.CREATIVE_MODE_TAB,
        TAB_KEY,
        CreativeModeTab.builder(CreativeModeTab.Row.TOP, TAB_COLUMN)
            .title(Component.translatable("itemGroup.aliment.main"))
            .icon { ItemStack(AlimentBlocks.WILLOW_SAPLING) }
            .displayItems { _, output -> CONTENT.forEach(output::accept) }
            .build(),
    )

    fun initialize() {
    }
}
