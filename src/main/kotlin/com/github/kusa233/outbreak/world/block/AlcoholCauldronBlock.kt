package com.github.kusa233.outbreak.world.block

import com.github.kusa233.outbreak.registry.OutbreakItems
import net.minecraft.core.BlockPos
import net.minecraft.core.cauldron.CauldronInteractions
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUtils
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AbstractCauldronBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.BlockHitResult

/**
 * Distilled alcohol cauldron, 酒精炼药锅.
 *
 * Placed beside the fermentation tank underneath the condenser pipe outlet to collect distilled alcohol.
 * Fills up to 3 levels. Using a glass bottle on it yields "酒" with distilled concentration (40%).
 */
class AlcoholCauldronBlock(properties: BlockBehaviour.Properties) :
    AbstractCauldronBlock(properties, CauldronInteractions.EMPTY) {

    init {
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 1))
    }

    override fun isFull(state: BlockState): Boolean = state.getValue(LEVEL) == 3

    override fun getContentHeight(state: BlockState): Double =
        (6.0 + state.getValue(LEVEL) * 3.0) / 16.0

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(LEVEL)
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult,
    ): InteractionResult {
        if (!stack.`is`(Items.GLASS_BOTTLE)) {
            return InteractionResult.PASS
        }

        if (!level.isClientSide) {
            val wine = OutbreakItems.createWine(DISTILLED_ETHANOL)
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, wine))
            player.awardStat(Stats.USE_CAULDRON)
            player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE))

            val currentLevel = state.getValue(LEVEL)
            if (currentLevel <= 1) {
                level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState())
            } else {
                level.setBlockAndUpdate(pos, state.setValue(LEVEL, currentLevel - 1))
            }

            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f)
            level.gameEvent(null, GameEvent.FLUID_PICKUP, pos)
        }

        return InteractionResult.SUCCESS
    }

    companion object {
        val LEVEL: IntegerProperty = BlockStateProperties.LEVEL_CAULDRON
        const val DISTILLED_ETHANOL: Float = 0.40f
    }
}
