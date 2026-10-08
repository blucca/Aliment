package com.github.kusa233.aliment.world.block

import com.github.kusa233.aliment.registry.AlimentBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * A grapefruit, hanging in the block directly underneath the canopy it grew from.
 *
 * It is a fruit and not a plant: it never grows, never ages and never spreads, so the whole class is
 * the one rule that it may only exist while there is something to hang from. Break the leaves above
 * it - or the world generator put it somewhere the trunk was later removed from - and it drops the
 * same way it would have if the player had mined it.
 *
 * The tree decorator places these with vanilla's `minecraft:attached_to_leaves`, so the world
 * generator knows nothing about this class beyond its block state; see
 * `data/aliment/worldgen/feature/grapefruit.json`.
 */
class GrapefruitBlock(properties: BlockBehaviour.Properties) : Block(properties) {

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean =
        canHangUnder(level.getBlockState(pos.above()))

    /**
     * Falls away when the block it hangs from is replaced with anything it cannot hang from.
     *
     * Only `UP` matters: a fruit is a single block with nothing attached to its sides or its bottom,
     * so every other direction leaves it exactly as it was.
     */
    override fun updateShape(
        state: BlockState,
        level: LevelReader,
        ticks: ScheduledTickAccess,
        pos: BlockPos,
        direction: Direction,
        neighborPos: BlockPos,
        neighborState: BlockState,
        random: RandomSource,
    ): BlockState =
        if (direction == Direction.UP && !canSurvive(state, level, pos)) {
            Blocks.AIR.defaultBlockState()
        } else {
            super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random)
        }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = SHAPE

    companion object {

        /**
         * What a grapefruit will hang from: leaves and logs, of any tree and not only its own.
         *
         * Accepting every tree's leaves is what lets a player hang a fruit under an oak, and it costs
         * nothing - the decorator only ever places them under its own canopy.
         */
        fun canHangUnder(above: BlockState): Boolean =
            above.`is`(AlimentBlocks.GRAPEFRUIT_LEAVES) ||
                above.`is`(AlimentBlocks.GRAPEFRUIT_LOG) ||
                above.`is`(BlockTags.LEAVES) ||
                above.`is`(BlockTags.LOGS)

        /**
         * The fruit's footprint, and it reaches the top of the block.
         *
         * The model is a `minecraft:block/cross`, which spans the whole cell, but its sprite draws
         * the fruit in the top half and leaves the bottom half empty - so the visible fruit hangs
         * from the ceiling of its own block, which is the leaf above it. The outline follows the
         * sprite: full width down to the stem, and nothing at all below the fruit.
         */
        private val SHAPE: VoxelShape = Block.box(3.0, 6.0, 3.0, 13.0, 16.0, 13.0)
    }
}
