package com.github.kusa233.aliment.world.block

import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.BushBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * The gymnopilus, 橘黄裸伞: a rustgill mushroom, and the mod's other drug.
 *
 * It is a plain plant with no growth of its own - it does not ripen, spread or answer to bone meal -
 * because what it is for is being eaten raw or cooked. `BushBlock` gives it the shape, the instant
 * break and the "pops off when a piston pushes it" behaviour; the only thing overridden is where it
 * is willing to sit.
 *
 * Unlike a vanilla mushroom there is **no light requirement**: an ordinary mushroom only takes root
 * in the dark, which would have kept the wild patches below out of every biome they are supposed to
 * grow in, since worldgen places them in daylight.
 */
class GymnopilusBlock(properties: BlockBehaviour.Properties) : BushBlock(properties) {

    /**
     * Soil or dead wood. A rustgill is a rotter - it lives on decaying timber - so a log is as good
     * a spot as a lawn, and that is also what lets the worldgen patch sow it under trees.
     */
    override fun mayPlaceOn(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean =
        state.`is`(BlockTags.SUPPORTS_VEGETATION) || state.`is`(BlockTags.LOGS)

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = SHAPE

    companion object {
        /** A cap and a short stem, a little wider than a vanilla mushroom. */
        private val SHAPE: VoxelShape = Block.box(4.0, 0.0, 4.0, 12.0, 10.0, 12.0)
    }
}
