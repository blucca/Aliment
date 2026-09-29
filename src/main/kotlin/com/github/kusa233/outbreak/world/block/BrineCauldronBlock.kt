package com.github.kusa233.outbreak.world.block

import com.github.kusa233.outbreak.registry.OutbreakItems
import net.minecraft.core.BlockPos
import net.minecraft.core.cauldron.CauldronInteractions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AbstractCauldronBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.redstone.Orientation

/**
 * Brine cauldron - the evaporation vessel of the salt chain.
 *
 * * Created by using [OutbreakItems.CRUDE_SALT_POWDER] on a water cauldron.
 * * With a campfire or soul campfire underneath it starts evaporating after 30 seconds, then
 *   concentrates one stage every [EVAPORATE_TICKS]. * * When it runs completely dry it drops one [OutbreakItems.SALT_POWDER] and leaves an empty
 *   cauldron behind.
 * * Stirring it with a [OutbreakItems.STIRRING_ROD] skips straight to the next stage.
 *
 * The liquid surface comes from the block model, so there is no block entity and no custom
 * renderer - the three stages are three blockstates with three content textures.
 */
class BrineCauldronBlock(properties: BlockBehaviour.Properties) :
    AbstractCauldronBlock(properties, CauldronInteractions.EMPTY) {
    init {
        this.registerDefaultState(this.stateDefinition.any().setValue(STAGE, 0))
    }

    override fun isFull(state: BlockState): Boolean = false

    override fun getContentHeight(state: BlockState): Double =
        (BASE_CONTENT_HEIGHT + (MAX_STAGE - state.getValue(STAGE)) * HEIGHT_PER_STAGE) / 16.0

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(STAGE)
    }

    // ------------------------------------------------------------------ evaporating over a fire

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        this.scheduleIfHeated(state, level, pos)
    }

    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        block: Block,
        orientation: Orientation?,
        movedByPiston: Boolean,
    ) {
        this.scheduleIfHeated(state, level, pos)
    }

    /** Vanilla ignores a duplicate schedule for the same position, so the timer cannot restart. */
    private fun scheduleIfHeated(state: BlockState, level: Level, pos: BlockPos) {
        if (!isHeated(level, pos)) {
            return
        }
        level.scheduleTick(pos, this, EVAPORATE_TICKS)
    }

    override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        if (!isHeated(level, pos)) {
            // The fire is gone: evaporation pauses and resumes when it is lit again.
            return
        }

        if (state.getValue(STAGE) >= MAX_STAGE) {
            this.dryOut(level, pos)
            return
        }

        level.setBlockAndUpdate(pos, state.setValue(STAGE, state.getValue(STAGE) + 1))
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F)
        level.scheduleTick(pos, this, EVAPORATE_TICKS)
    }

    /** Boiled dry: drop the salt and leave an empty cauldron. */
    private fun dryOut(level: ServerLevel, pos: BlockPos) {
        level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState())
        Block.popResource(level, pos, ItemStack(OutbreakItems.SALT_POWDER))
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 0.8F)
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(Blocks.CAULDRON.defaultBlockState()))
    }

    /**
     * Speeds the brine along by one stage, used by the stirring rod.
     *
     * @return true when the brine actually moved on.
     */
    fun stir(state: BlockState, level: ServerLevel, pos: BlockPos): Boolean {
        if (state.getValue(STAGE) >= MAX_STAGE) {
            this.dryOut(level, pos)
            return true
        }
        level.setBlockAndUpdate(pos, state.setValue(STAGE, state.getValue(STAGE) + 1))
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.5F, 1.8F)
        level.sendParticles(ParticleTypes.SPLASH, pos.x + 0.5, pos.y + 0.9, pos.z + 0.5, 6, 0.2, 0.1, 0.2, 0.0)
        return true
    }

    companion object {
        /** Evaporation time per stage, 20 seconds. */
        const val EVAPORATE_TICKS: Int = 400

        /** Stage 0 is weak brine, [MAX_STAGE] is nearly dry. */
        const val MAX_STAGE: Int = 2

        private const val BASE_CONTENT_HEIGHT = 3.0
        private const val HEIGHT_PER_STAGE = 3.0
        /** 0 = crude brine, 1 = concentrated, 2 = nearly crystallised. */
        val STAGE: IntegerProperty = IntegerProperty.create("stage", 0, MAX_STAGE)

        fun isHeated(level: BlockGetter, pos: BlockPos): Boolean {
            val below = level.getBlockState(pos.below())
            return below.`is`(Blocks.CAMPFIRE) || below.`is`(Blocks.SOUL_CAMPFIRE)
        }
    }
}
