package com.github.kusa233.outbreak.registry

import com.github.kusa233.outbreak.Outbreak
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour

/**
 * Small helpers around [Registry.register] that mirror how vanilla 26.2 registers
 * blocks and items (see `net.minecraft.world.level.block.Blocks#register` and
 * `net.minecraft.world.item.Items#registerItem`).
 */
object Registration {

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(Outbreak.MOD_ID, path)

    fun blockKey(path: String): ResourceKey<Block> = ResourceKey.create(Registries.BLOCK, id(path))

    fun itemKey(path: String): ResourceKey<Item> = ResourceKey.create(Registries.ITEM, id(path))

    fun <T : Block> registerBlock(
        path: String,
        properties: BlockBehaviour.Properties,
        factory: (BlockBehaviour.Properties) -> T,
    ): T {
        val key = blockKey(path)
        val block = factory(properties.setId(key))
        return Registry.register(BuiltInRegistries.BLOCK, key, block)
    }

    fun registerItem(path: String, properties: Item.Properties, factory: (Item.Properties) -> Item): Item {
        val key = itemKey(path)
        val item = factory(properties.setId(key))
        if (item is BlockItem) {
            item.registerBlocks(Item.BY_BLOCK, item)
        }
        return Registry.register(BuiltInRegistries.ITEM, key, item)
    }

    /** Registers a [Block] together with the [BlockItem] that places it. */
    fun <T : Block> registerBlockWithItem(
        path: String,
        properties: BlockBehaviour.Properties,
        itemProperties: Item.Properties = Item.Properties(),
        itemFactory: (Block, Item.Properties) -> Item = { block, itemProps -> BlockItem(block, itemProps) },
        factory: (BlockBehaviour.Properties) -> T,
    ): T {
        val block = registerBlock(path, properties, factory)
        registerItem(path, itemProperties.useBlockDescriptionPrefix()) { itemFactory(block, it) }
        return block
    }
}
