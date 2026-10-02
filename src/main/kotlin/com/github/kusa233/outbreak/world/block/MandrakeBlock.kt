package com.github.kusa233.outbreak.world.block

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.BonemealSource
import net.minecraft.world.level.block.BushBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * The mandrake: a four-stage plant that goes into the ground rather than into a tilled field.
 *
 * It is deliberately not a `CropBlock`. Vanilla's crops all require farmland, and a mandrake is
 * supposed to be sown on plain dirt and grass, so this is a [BushBlock] with an age property of its
 * own - the same shape as vanilla's sweet berry bush, which is the other plant that grows on soil.
 *
 * Only the last stage carries fruit, and the fruit is what the plant is for: see
 * `data/outbreak/loot_table/blocks/mandrake.json`, which drops 1-2 of them at `age=3` and nothing at
 * all before that. Breaking an unripe mandrake therefore wastes the seed, which is the whole tension
 * of the plant - a wild patch is worth walking back to.
 */
class MandrakeBlock(properties: BlockBehaviour.Properties) : BushBlock(properties) {

    init {
        registerDefaultState(stateDefinition.any().setValue(AGE, 0))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(AGE)
    }

    /**
     * Everything the overworld grows plants in, farmland included.
     *
     * `#minecraft:supports_vegetation` is vanilla's own "this is soil" tag - dirt, coarse dirt,
     * rooted dirt, grass, mud, moss, and farmland. A mandrake takes any of them: it is a weed, not a
     * crop, so a tilled field is as good as a lawn.
     */
    override fun mayPlaceOn(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean =
        state.`is`(BlockTags.SUPPORTS_VEGETATION)

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = SHAPES[state.getValue(AGE)]

    override fun isRandomlyTicking(state: BlockState): Boolean = state.getValue(AGE) < MAX_AGE

    override fun randomTick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        val age = state.getValue(AGE)
        // Vanilla's rule for a crop: nothing grows in the dark.
        if (age >= MAX_AGE || level.getRawBrightness(pos, 0) < MIN_LIGHT) {
            return
        }
        if (random.nextInt(GROWTH_ODDS) != 0) {
            return
        }
        level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS)
    }

    // ---------------------------------------------------------------- bone meal

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

    /** One stage per application, so three bone meals take a seedling to fruit. */
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
        /** Four visible stages, `age=0` a seedling and `age=3` the flowering plant with fruit. */
        val AGE: IntegerProperty = BlockStateProperties.AGE_3

        const val MAX_AGE = 3

        /** Vanilla's light level for a growing crop. */
        private const val MIN_LIGHT = 9

        /** One step in eight random ticks, so a plant ripens over a few in-game days. */
        private const val GROWTH_ODDS = 8

        private val SHAPES: Array<VoxelShape> = Block.boxes(MAX_AGE + 1) { age ->
            when (age) {
                0 -> Block.box(4.0, 0.0, 4.0, 12.0, 6.0, 12.0)
                1 -> Block.box(3.0, 0.0, 3.0, 13.0, 10.0, 13.0)
                2 -> Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0)
                else -> Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0)
            }
        }
    }
}
