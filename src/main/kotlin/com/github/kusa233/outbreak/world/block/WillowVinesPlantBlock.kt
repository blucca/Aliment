package com.github.kusa233.outbreak.world.block

import com.github.kusa233.outbreak.registry.OutbreakBlocks
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.GrowingPlantBodyBlock
import net.minecraft.world.level.block.GrowingPlantHeadBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.phys.shapes.VoxelShape

/** The middle section of a willow vine strand. Has no item form; [WillowVinesBlock] is the tip. */
class WillowVinesPlantBlock(properties: BlockBehaviour.Properties) :
    GrowingPlantBodyBlock(properties, Direction.DOWN, SHAPE, false) {
    override fun getHeadBlock(): GrowingPlantHeadBlock = OutbreakBlocks.WILLOW_VINES

    companion object {
        private val SHAPE: VoxelShape = Block.column(14.0, 0.0, 16.0)
    }
}
