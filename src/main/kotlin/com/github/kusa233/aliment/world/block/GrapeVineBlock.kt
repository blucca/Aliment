package com.github.kusa233.aliment.world.block

import com.github.kusa233.aliment.registry.AlimentItems
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
 * The grape vine: a four-stage plant that goes into the ground rather than into a tilled field.
 *
 * Deliberately the same shape as [MandrakeBlock], and for the same reason: vanilla's crops all
 * require farmland, and a vine is supposed to be sown on ordinary soil. So this is a [BushBlock]
 * with an age property of its own rather than a `CropBlock`.
 *
 * Only the last stage carries fruit, and the fruit is picked by hand rather than by breaking the
 * plant: a right click at `age=3` drops 1-3 grapes and knocks the vine back to `age=1`, so the
 * plant survives the harvest and fruits again. That is the shape of vanilla's sweet berry bush, and
 * the same one [PhellodendronBlock], [EphedraBlock], [CoptisBlock], [LicoriceBlock] and
 * [SeaweedBlock] already use - a vine is the raw material of the fermentation tank, which makes it a
 * crop a player keeps rather than a plant they find once.
 *
 * Breaking the vine still works and still pays out, because there is no item for the vine itself:
 * `data/aliment/loot_table/blocks/grape_vine.json` drops the same 1-3 grapes at `age=3` and nothing
 * at all before that. So the two harvests differ in what happens to the plant - picking keeps it,
 * breaking spends it - and an unripe vine is worth nothing either way, which is what makes waiting
 * for the fruit matter.
 *
 * One deliberate asymmetry: the break runs through the loot table and therefore still takes a
 * Fortune bonus, while the pick rolls a flat 1-3. That is exactly what vanilla does with the sweet
 * berry bush, whose `blocks/` table carries the enchantment and whose `harvest/` table does not, and
 * it gives the two options separate reasons to exist: bring a Fortune tool to clear a wild vine, or
 * right-click to farm one you have planted.
 */
class GrapeVineBlock(properties: BlockBehaviour.Properties) : BushBlock(properties) {

    init {
        registerDefaultState(stateDefinition.any().setValue(AGE, 0))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(AGE)
    }

    /**
     * Everything the overworld grows plants in, farmland included - the same rule as the mandrake,
     * so a vine takes a lawn or a tilled field equally.
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

    // ---------------------------------------------------------------- picking the fruit

    /**
     * Picking a ripe bunch by hand, which knocks the vine back to `age=1` instead of destroying it.
     *
     * The revert target is 1 rather than 0 deliberately: the vine has to be a plant the player
     * keeps, and sending it back to a seedling would make every harvest cost the full three stages
     * of growth again while gaining nothing over simply breaking the block. Stopping one stage short
     * of ripe is vanilla's own answer for the sweet berry bush, and it is what makes a vineyard
     * worth walking back to.
     *
     * Below `age=3` this defers to `super`, which is `BlockBehaviour.useItemOn` and therefore returns
     * `TRY_WITH_EMPTY_HAND` - a non-consuming result - so a right click on a green vine falls through
     * to whatever the held item would otherwise do. Bone meal is the case that matters: it reaches
     * `ItemStack.useOn` only because this branch declines to consume the click, and a harvest that
     * reported success unconditionally would silently eat every bone meal applied to a growing vine.
     */
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
        if (age < MAX_AGE) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit)
        }

        if (!level.isClientSide) {
            val count = 1 + level.random.nextInt(MAX_PICK)
            popResource(level, pos, ItemStack(AlimentItems.GRAPE, count))
            level.playSound(
                null,
                pos,
                SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                SoundSource.BLOCKS,
                1.0f,
                0.8f + level.random.nextFloat() * 0.4f,
            )
            val nextState = state.setValue(AGE, PICKED_AGE)
            level.setBlock(pos, nextState, Block.UPDATE_CLIENTS)
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, nextState))
        }
        return InteractionResult.SUCCESS
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
        /** Four visible stages, `age=0` a seedling and `age=3` the vine carrying ripe grapes. */
        val AGE: IntegerProperty = BlockStateProperties.AGE_3

        const val MAX_AGE = 3

        /**
         * Where a picked vine is left: one stage short of ripe, vanilla's own answer for the sweet
         * berry bush. See [useItemOn].
         */
        const val PICKED_AGE = 1

        /** The largest bunch a single pick yields, with the loot table's 1-3 breakout matching it. */
        private const val MAX_PICK = 3

        /** Vanilla's light level for a growing crop. */
        private const val MIN_LIGHT = 9

        /**
         * One step in six random ticks, a little quicker than the mandrake's one in eight: a
         * mandrake is a plant a player finds, but a vine is one a player farms, and the tank it
         * feeds takes 45 seconds a batch.
         */
        private const val GROWTH_ODDS = 6

        private val SHAPES: Array<VoxelShape> = Block.boxes(MAX_AGE + 1) { age ->
            when (age) {
                0 -> Block.box(4.0, 0.0, 4.0, 12.0, 5.0, 12.0)
                1 -> Block.box(3.0, 0.0, 3.0, 13.0, 9.0, 13.0)
                2 -> Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0)
                else -> Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0)
            }
        }
    }
}
