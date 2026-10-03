package com.github.kusa233.outbreak.world.block

import com.github.kusa233.outbreak.registry.OutbreakItems
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.BonemealSource
import net.minecraft.world.level.block.BonemealableBlock
import net.minecraft.world.level.block.BushBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Licorice (甘草): drought-tolerant medicinal herb containing glycyrrhizin (甘草酸).
 */
class LicoriceBlock(properties: BlockBehaviour.Properties) : BushBlock(properties), BonemealableBlock {

    init {
        registerDefaultState(stateDefinition.any().setValue(AGE, 0))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(AGE)
    }

    override fun mayPlaceOn(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean =
        state.`is`(BlockTags.SUPPORTS_VEGETATION) ||
            state.`is`(BlockTags.DIRT) ||
            state.`is`(BlockTags.SAND)

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = SHAPES[state.getValue(AGE)]

    override fun isRandomlyTicking(state: BlockState): Boolean = state.getValue(AGE) < MAX_AGE

    override fun randomTick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        val age = state.getValue(AGE)
        if (age >= MAX_AGE || level.getRawBrightness(pos, 0) < MIN_LIGHT) {
            return
        }
        if (random.nextInt(GROWTH_ODDS) != 0) {
            return
        }
        level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS)
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
                popResource(level, pos, ItemStack(OutbreakItems.LICORICE, count))
                level.playSound(
                    null,
                    pos,
                    SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                    SoundSource.BLOCKS,
                    1.0f,
                    0.8f + level.random.nextFloat() * 0.4f,
                )
                val nextState = state.setValue(AGE, 1)
                level.setBlock(pos, nextState, Block.UPDATE_CLIENTS)
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
        level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), Block.UPDATE_CLIENTS)
    }

    companion object {
        val AGE: IntegerProperty = BlockStateProperties.AGE_3
        const val MAX_AGE = 3
        private const val MIN_LIGHT = 8
        private const val GROWTH_ODDS = 8

        private val SHAPES: Array<VoxelShape> = Block.boxes(MAX_AGE + 1) { age ->
            when (age) {
                0 -> Block.box(4.0, 0.0, 4.0, 12.0, 5.0, 12.0)
                1 -> Block.box(3.0, 0.0, 3.0, 13.0, 8.0, 13.0)
                2 -> Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0)
                else -> Block.box(2.0, 0.0, 2.0, 14.0, 15.0, 14.0)
            }
        }
    }
}
