package com.github.kusa233.outbreak.world.block

import com.github.kusa233.outbreak.registry.OutbreakItems
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.cauldron.CauldronInteractions
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUtils
import net.minecraft.world.item.Items
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AbstractCauldronBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult

/**
 * Willow bark soup cauldron - holds raw or cooked willow bark soup.
 *
 * * Created by using [OutbreakItems.WILLOW_BARK_PIECES] on a water cauldron.
 * * A glass bottle or a bowl takes one serving out.
 * * A campfire (or soul campfire) underneath turns the raw soup into cooked soup after 60
 *   seconds, exactly [COOK_TICKS] ticks.
 *
 * The block is a plain cauldron: the liquid surface comes from the block model, so there is no
 * block entity and no custom renderer. Interaction is handled directly instead of through a
 * `CauldronInteraction.Dispatcher`, whose `put` method is package-private in vanilla.
 */
class WillowSoupCauldronBlock(properties: BlockBehaviour.Properties) :
    AbstractCauldronBlock(properties, CauldronInteractions.EMPTY) {

    override fun codec(): MapCodec<WillowSoupCauldronBlock> = CODEC

    init {
        this.registerDefaultState(
            this.stateDefinition.any().setValue(LEVEL, 3).setValue(COOKED, false),
        )
    }

    override fun isFull(state: BlockState): Boolean = state.getValue(LEVEL) == MAX_LEVEL

    override fun getContentHeight(state: BlockState): Double =
        (BASE_CONTENT_HEIGHT + state.getValue(LEVEL) * HEIGHT_PER_LEVEL) / 16.0

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(LEVEL, COOKED)
    }

    // ------------------------------------------------------------------ taking a serving out

    override fun useItemOn(
        itemStack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult,
    ): InteractionResult {
        val cooked = state.getValue(COOKED)
        val result = when {
            itemStack.`is`(Items.GLASS_BOTTLE) -> if (cooked) {
                OutbreakItems.WILLOW_BARK_SOUP_BOTTLE
            } else {
                OutbreakItems.RAW_WILLOW_BARK_SOUP_BOTTLE
            }

            itemStack.`is`(Items.BOWL) -> if (cooked) {
                OutbreakItems.WILLOW_BARK_SOUP_BOWL
            } else {
                OutbreakItems.RAW_WILLOW_BARK_SOUP_BOWL
            }

            else -> null
        } ?: return InteractionResult.PASS

        if (!level.isClientSide) {
            val usedItem = itemStack.item
            player.setItemInHand(hand, ItemUtils.createFilledResult(itemStack, player, ItemStack(result)))
            player.awardStat(Stats.USE_CAULDRON)
            player.awardStat(Stats.ITEM_USED.get(usedItem))
            LayeredCauldronBlock.lowerFillLevel(state, level, pos)
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F)
            level.gameEvent(null, GameEvent.FLUID_PICKUP, pos)
        }

        return InteractionResult.SUCCESS
    }

    // ------------------------------------------------------------------ cooking over a fire

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        this.startCookingIfHeated(state, level, pos)
    }

    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        block: net.minecraft.world.level.block.Block,
        orientation: Orientation?,
        movedByPiston: Boolean,
    ) {
        this.startCookingIfHeated(state, level, pos)
    }

    /**
     * Schedules the 60 second cook. Vanilla ignores a second schedule for the same position and
     * block while one is pending, so repeated neighbour updates cannot restart the timer.
     */
    private fun startCookingIfHeated(state: BlockState, level: Level, pos: BlockPos) {
        if (state.getValue(COOKED) || !isHeated(level, pos)) {
            return
        }
        level.scheduleTick(pos, this, COOK_TICKS)
    }

    override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        if (state.getValue(COOKED) || !isHeated(level, pos)) {
            return
        }
        val cooked = state.setValue(COOKED, true)
        level.setBlockAndUpdate(pos, cooked)
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(cooked))
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.7F, 1.2F)
    }

    companion object {
        const val COOK_TICKS: Int = 1200 // 60 seconds
        private const val BASE_CONTENT_HEIGHT = 6.0
        private const val HEIGHT_PER_LEVEL = 3.0
        private const val MAX_LEVEL = 3

        val CODEC: MapCodec<WillowSoupCauldronBlock> = BlockBehaviour.simpleCodec(::WillowSoupCauldronBlock)

        val LEVEL: IntegerProperty = BlockStateProperties.LEVEL_CAULDRON
        val COOKED: BooleanProperty = BooleanProperty.create("cooked")

        /** True when the block below is a campfire or a soul campfire. */
        fun isHeated(level: BlockGetter, pos: BlockPos): Boolean {
            val below = level.getBlockState(pos.below())
            return below.`is`(Blocks.CAMPFIRE) || below.`is`(Blocks.SOUL_CAMPFIRE)
        }
    }
}
