package com.github.kusa233.aliment.world.block

import com.github.kusa233.aliment.registry.AlimentItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.BonemealSource
import net.minecraft.world.level.block.BonemealableBlock
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Seaweed: an underwater edible crop with 4 growth stages, yielding iodine.
 * Harvested at stage 3 by right-clicking. Bonemealable in water.
 */
class SeaweedBlock(properties: BlockBehaviour.Properties) : Block(properties), SimpleWaterloggedBlock, BonemealableBlock {

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(WATERLOGGED, true),
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(AGE, WATERLOGGED)
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = SHAPES[state.getValue(AGE)]

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val belowPos = pos.below()
        val belowState = level.getBlockState(belowPos)
        val hasSupport = belowState.`is`(BlockTags.SAND) ||
            belowState.`is`(BlockTags.DIRT) ||
            belowState.`is`(Blocks.GRAVEL)
        val inWater = level.getFluidState(pos).`is`(FluidTags.WATER) || state.getValue(WATERLOGGED)
        return hasSupport && inWater
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val fluidState = context.level.getFluidState(context.clickedPos)
        if (!fluidState.`is`(FluidTags.WATER) || fluidState.amount < 8) {
            return null
        }
        return defaultBlockState()
            .setValue(AGE, 0)
            .setValue(WATERLOGGED, true)
    }

    override fun updateShape(
        state: BlockState,
        level: LevelReader,
        scheduledTickAccess: ScheduledTickAccess,
        pos: BlockPos,
        direction: Direction,
        neighborPos: BlockPos,
        neighborState: BlockState,
        random: RandomSource,
    ): BlockState {
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))
        }
        if (!canSurvive(state, level, pos)) {
            return Blocks.WATER.defaultBlockState()
        }
        return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random)
    }

    override fun getFluidState(state: BlockState): FluidState =
        if (state.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(state)

    override fun isRandomlyTicking(state: BlockState): Boolean = state.getValue(AGE) < MAX_AGE

    override fun randomTick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        val age = state.getValue(AGE)
        if (age >= MAX_AGE || level.getRawBrightness(pos, 0) < MIN_LIGHT) {
            return
        }
        if (random.nextInt(GROWTH_ODDS) != 0) {
            return
        }
        level.setBlock(pos, state.setValue(AGE, age + 1), UPDATE_CLIENTS)
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): InteractionResult {
        val age = state.getValue(AGE)
        if (age == MAX_AGE) {
            if (!level.isClientSide) {
                val count = 1 + level.random.nextInt(2)
                popResource(level, pos, ItemStack(AlimentItems.SEAWEED, count))
                level.playSound(
                    null,
                    pos,
                    SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                    SoundSource.BLOCKS,
                    1.0f,
                    0.8f + level.random.nextFloat() * 0.4f,
                )
                val nextState = state.setValue(AGE, 1)
                level.setBlock(pos, nextState, UPDATE_CLIENTS)
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, nextState))
            }
            return InteractionResult.SUCCESS
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit)
    }

    override fun isValidBonemealTarget(
        level: LevelReader,
        pos: BlockPos,
        state: BlockState,
        source: BonemealSource,
    ): Boolean = state.getValue(AGE) < MAX_AGE

    override fun isBonemealSuccess(
        level: Level,
        random: RandomSource,
        pos: BlockPos,
        state: BlockState,
        source: BonemealSource,
    ): Boolean = true

    override fun performBonemeal(
        level: ServerLevel,
        random: RandomSource,
        pos: BlockPos,
        state: BlockState,
        source: BonemealSource,
    ) {
        level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), UPDATE_CLIENTS)
    }

    companion object {
        val AGE: IntegerProperty = BlockStateProperties.AGE_3
        val WATERLOGGED: BooleanProperty = BlockStateProperties.WATERLOGGED
        const val MAX_AGE = 3
        private const val MIN_LIGHT = 6
        private const val GROWTH_ODDS = 8

        private val SHAPES: Array<VoxelShape> = Block.boxes(MAX_AGE + 1) { age ->
            when (age) {
                0 -> Block.box(3.0, 0.0, 3.0, 13.0, 4.0, 13.0)
                1 -> Block.box(2.0, 0.0, 2.0, 14.0, 8.0, 14.0)
                2 -> Block.box(1.0, 0.0, 1.0, 15.0, 12.0, 15.0)
                else -> Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
            }
        }
    }
}
