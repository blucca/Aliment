package com.github.kusa233.outbreak.registry

import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityType
import net.minecraft.world.level.block.entity.BlockEntityTypes

/**
 * Vanilla's sign and shelf block entity types hold a fixed set of valid blocks; Fabric exposes
 * [FabricBlockEntityType.addValidBlock] so modded blocks can join them instead of having to
 * register a duplicate block entity type (and a duplicate renderer).
 */
object OutbreakBlockEntities {

    fun initialize() {
        (BlockEntityTypes.SIGN as FabricBlockEntityType).addValidBlock(OutbreakBlocks.WILLOW_SIGN)
        (BlockEntityTypes.SIGN as FabricBlockEntityType).addValidBlock(OutbreakBlocks.WILLOW_WALL_SIGN)
        (BlockEntityTypes.HANGING_SIGN as FabricBlockEntityType).addValidBlock(OutbreakBlocks.WILLOW_HANGING_SIGN)
        (BlockEntityTypes.HANGING_SIGN as FabricBlockEntityType).addValidBlock(OutbreakBlocks.WILLOW_WALL_HANGING_SIGN)
        (BlockEntityTypes.SHELF as FabricBlockEntityType).addValidBlock(OutbreakBlocks.WILLOW_SHELF)
    }
}
