package com.github.kusa233.outbreak.registry

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike

/**
 * Outbreak owns a single creative tab so the mod's content stays in one place as it grows.
 *
 * The tab sits in the top row right after the seven vanilla slots. Its title is localised as
 * `itemGroup.outbreak.main` (see `assets/outbreak/lang`).
 */
object OutbreakCreativeTabs {

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
        OutbreakBlocks.WILLOW_LOG,
        OutbreakBlocks.WILLOW_WOOD,
        OutbreakBlocks.STRIPPED_WILLOW_LOG,
        OutbreakBlocks.STRIPPED_WILLOW_WOOD,
        OutbreakItems.WILLOW_BARK,
        OutbreakItems.WILLOW_BARK_PIECES,
        // planks and shaped blocks
        OutbreakBlocks.WILLOW_PLANKS,
        OutbreakBlocks.WILLOW_STAIRS,
        OutbreakBlocks.WILLOW_SLAB,
        OutbreakBlocks.WILLOW_FENCE,
        OutbreakBlocks.WILLOW_FENCE_GATE,
        OutbreakBlocks.WILLOW_DOOR,
        OutbreakBlocks.WILLOW_TRAPDOOR,
        OutbreakBlocks.WILLOW_PRESSURE_PLATE,
        OutbreakBlocks.WILLOW_BUTTON,
        OutbreakBlocks.WILLOW_SHELF,
        // foliage
        OutbreakBlocks.WILLOW_LEAVES,
        OutbreakBlocks.WILLOW_SAPLING,
        OutbreakBlocks.WILLOW_VINES,
        // signs and boats
        OutbreakItems.WILLOW_SIGN,
        OutbreakItems.WILLOW_HANGING_SIGN,
        OutbreakItems.WILLOW_BOAT,
        OutbreakItems.WILLOW_CHEST_BOAT,
        // soups
        OutbreakItems.RAW_WILLOW_BARK_SOUP_BOTTLE,
        OutbreakItems.RAW_WILLOW_BARK_SOUP_BOWL,
        OutbreakItems.WILLOW_BARK_SOUP_BOTTLE,
        OutbreakItems.WILLOW_BARK_SOUP_BOWL,
    )

    /**
     * Touching this object at all runs the object initialiser, which is what registers the tab
     * in [BuiltInRegistries.CREATIVE_MODE_TAB].
     */
    val MAIN: CreativeModeTab = Registry.register(
        BuiltInRegistries.CREATIVE_MODE_TAB,
        TAB_KEY,
        CreativeModeTab.builder(CreativeModeTab.Row.TOP, TAB_COLUMN)
            .title(Component.translatable("itemGroup.outbreak.main"))
            .icon { ItemStack(OutbreakBlocks.WILLOW_SAPLING) }
            .displayItems { _, output -> CONTENT.forEach(output::accept) }
            .build(),
    )

    fun initialize() {
    }
}
