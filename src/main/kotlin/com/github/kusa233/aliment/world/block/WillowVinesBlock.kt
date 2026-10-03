package com.github.kusa233.aliment.world.block

import com.github.kusa233.aliment.registry.AlimentBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.util.RandomSource
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.GrowingPlantHeadBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Willow vines - the drooping strands that hang from a willow canopy.
 *
 * It behaves like weeping vines: the "head" block grows downwards over time, is bonemealable
 * and turns into [WillowVinesPlantBlock] body blocks as the strand gets longer.
 */
class WillowVinesBlock(properties: BlockBehaviour.Properties) :
    GrowingPlantHeadBlock(properties, Direction.DOWN, SHAPE, false, 0.12) {
    override fun getBlocksToGrowWhenBonemealed(random: RandomSource): Int = 1 + random.nextInt(2)

    override fun getBodyBlock(): Block = AlimentBlocks.WILLOW_VINES_PLANT

    override fun canGrowInto(state: BlockState): Boolean = state.isAir

    /** Willow strands may cling to leaves and logs, not only to full solid faces. */
    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val abovePos = pos.above()
        val above = level.getBlockState(abovePos)
        return above.`is`(AlimentBlocks.WILLOW_LEAVES) ||
            above.`is`(AlimentBlocks.WILLOW_VINES) ||
            above.`is`(AlimentBlocks.WILLOW_VINES_PLANT) ||
            above.`is`(BlockTags.LEAVES) ||
            above.`is`(BlockTags.LOGS) ||
            above.isFaceSturdy(level, abovePos, Direction.DOWN)
    }

    companion object {
        private val SHAPE: VoxelShape = Block.column(8.0, 9.0, 16.0)
    }
}
