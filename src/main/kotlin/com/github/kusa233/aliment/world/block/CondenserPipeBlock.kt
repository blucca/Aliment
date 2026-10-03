package com.github.kusa233.aliment.world.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Glass Condenser Pipe.
 *
 * When placed as a single pipe above a fermentation tank, its outlet faces UP.
 * When connected to a horizontal neighboring condenser pipe, its outlet faces DOWN towards
 * the collection vessel (cauldron).
 */
class CondenserPipeBlock(properties: BlockBehaviour.Properties) : Block(properties) {

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(OUTLET_DOWN, false)
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false),
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(OUTLET_DOWN, NORTH, SOUTH, EAST, WEST)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        val level = context.level
        val pos = context.clickedPos
        return updateConnections(defaultBlockState(), level, pos)
    }

    override fun updateShape(
        state: BlockState,
        level: LevelReader,
        tickAccess: ScheduledTickAccess,
        pos: BlockPos,
        direction: Direction,
        neighborPos: BlockPos,
        neighborState: BlockState,
        random: RandomSource,
    ): BlockState {
        return updateConnections(state, level, pos)
    }

    private fun updateConnections(state: BlockState, level: LevelReader, pos: BlockPos): BlockState {
        val n = level.getBlockState(pos.north()).block is CondenserPipeBlock
        val s = level.getBlockState(pos.south()).block is CondenserPipeBlock
        val e = level.getBlockState(pos.east()).block is CondenserPipeBlock
        val w = level.getBlockState(pos.west()).block is CondenserPipeBlock
        val hasSide = n || s || e || w

        return state
            .setValue(OUTLET_DOWN, hasSide)
            .setValue(NORTH, n)
            .setValue(SOUTH, s)
            .setValue(EAST, e)
            .setValue(WEST, w)
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape {
        val outletDown = state.getValue(OUTLET_DOWN)
        var shape = if (outletDown) CORE_SHAPE_DOWN else CORE_SHAPE_UP
        if (state.getValue(NORTH)) shape = Shapes.or(shape, NORTH_ARM)
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, SOUTH_ARM)
        if (state.getValue(EAST)) shape = Shapes.or(shape, EAST_ARM)
        if (state.getValue(WEST)) shape = Shapes.or(shape, WEST_ARM)
        return shape
    }

    companion object {
        /** True when connected horizontally so the condensation spout turns DOWN. */
        val OUTLET_DOWN: BooleanProperty = BooleanProperty.create("outlet_down")
        val NORTH: BooleanProperty = BooleanProperty.create("north")
        val SOUTH: BooleanProperty = BooleanProperty.create("south")
        val EAST: BooleanProperty = BooleanProperty.create("east")
        val WEST: BooleanProperty = BooleanProperty.create("west")

        private val CORE_SHAPE_UP: VoxelShape = Block.box(5.0, 0.0, 5.0, 11.0, 16.0, 11.0)
        private val CORE_SHAPE_DOWN: VoxelShape = Block.box(5.0, 0.0, 5.0, 11.0, 12.0, 11.0)
        private val NORTH_ARM: VoxelShape = Block.box(5.0, 4.0, 0.0, 11.0, 12.0, 5.0)
        private val SOUTH_ARM: VoxelShape = Block.box(5.0, 4.0, 11.0, 11.0, 12.0, 16.0)
        private val EAST_ARM: VoxelShape = Block.box(11.0, 4.0, 5.0, 16.0, 12.0, 11.0)
        private val WEST_ARM: VoxelShape = Block.box(0.0, 4.0, 5.0, 5.0, 12.0, 11.0)
    }
}
