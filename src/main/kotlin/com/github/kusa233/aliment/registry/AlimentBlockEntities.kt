package com.github.kusa233.aliment.registry

import com.github.kusa233.aliment.world.block.FermentationTankBlockEntity
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityType
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.entity.BlockEntityTypes

/**
 * Block entity registrations for Aliment.
 */
object AlimentBlockEntities {

    val FERMENTATION_TANK: BlockEntityType<FermentationTankBlockEntity> = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        Registration.id("fermentation_tank"),
        BlockEntityType(::FermentationTankBlockEntity, setOf(AlimentBlocks.FERMENTATION_TANK)),
    )

    fun initialize() {
        (BlockEntityTypes.SIGN as FabricBlockEntityType).addValidBlock(AlimentBlocks.WILLOW_SIGN)
        (BlockEntityTypes.SIGN as FabricBlockEntityType).addValidBlock(AlimentBlocks.WILLOW_WALL_SIGN)
        (BlockEntityTypes.HANGING_SIGN as FabricBlockEntityType).addValidBlock(AlimentBlocks.WILLOW_HANGING_SIGN)
        (BlockEntityTypes.HANGING_SIGN as FabricBlockEntityType).addValidBlock(AlimentBlocks.WILLOW_WALL_HANGING_SIGN)
        (BlockEntityTypes.SHELF as FabricBlockEntityType).addValidBlock(AlimentBlocks.WILLOW_SHELF)
    }
}
