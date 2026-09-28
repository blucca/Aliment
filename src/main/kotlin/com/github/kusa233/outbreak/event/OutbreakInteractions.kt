package com.github.kusa233.outbreak.event

import com.github.kusa233.outbreak.registry.OutbreakBlocks
import com.github.kusa233.outbreak.registry.OutbreakItems
import com.github.kusa233.outbreak.world.block.WillowSoupCauldronBlock
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent

/**
 * Right-click interactions that vanilla block classes do not cover:
 *
 * 1. Stripping a willow log/wood with an axe also drops [OutbreakItems.WILLOW_BARK].
 * 2. Grinding [OutbreakItems.WILLOW_BARK] on a grindstone yields [OutbreakItems.WILLOW_BARK_PIECES].
 * 3. Dropping [OutbreakItems.WILLOW_BARK_PIECES] into a water cauldron turns the water into raw
 *    willow bark soup.
 */
object OutbreakInteractions {

    private val STRIPPABLES: Map<Block, Block> = mapOf(
        OutbreakBlocks.WILLOW_LOG to OutbreakBlocks.STRIPPED_WILLOW_LOG,
        OutbreakBlocks.WILLOW_WOOD to OutbreakBlocks.STRIPPED_WILLOW_WOOD,
    )

    fun initialize() {
        UseBlockCallback.EVENT.register { player, level, hand, hitResult ->
            val pos = hitResult.blockPos
            if (!level.isLoaded(pos)) {
                return@register InteractionResult.PASS
            }
            val state = level.getBlockState(pos)
            val stack = player.getItemInHand(hand)

            stripWithAxe(level, pos, state, stack, player, hand)
                ?: grindBark(level, pos, state, stack, player)
                ?: fillCauldron(level, pos, state, stack)
                ?: InteractionResult.PASS
        }
    }

    // ---------------------------------------------------------------- axe stripping

    private fun stripWithAxe(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        stack: ItemStack,
        player: Player,
        hand: InteractionHand,
    ): InteractionResult? {
        val stripped = STRIPPABLES[state.block] ?: return null
        if (!stack.`is`(ItemTags.AXES)) {
            return null
        }

        if (!level.isClientSide) {
            val axis = if (state.hasProperty(RotatedPillarBlock.AXIS)) {
                state.getValue(RotatedPillarBlock.AXIS)
            } else {
                Direction.Axis.Y
            }
            val newState = stripped.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis)
            level.setBlock(pos, newState, 11)
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, newState))
            stack.hurtAndBreak(1, player, hand.asEquipmentSlot())
            Block.popResource(level, pos, ItemStack(OutbreakItems.WILLOW_BARK))
        }
        level.playSound(player, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F)
        return InteractionResult.SUCCESS
    }

    // ---------------------------------------------------------------- grinding the bark

    private fun grindBark(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        stack: ItemStack,
        player: Player,
    ): InteractionResult? {
        if (!state.`is`(Blocks.GRINDSTONE) || !stack.`is`(OutbreakItems.WILLOW_BARK)) {
            return null
        }

        if (!level.isClientSide) {
            stack.shrink(1)
            val pieces = ItemStack(OutbreakItems.WILLOW_BARK_PIECES, 2)
            if (!player.addItem(pieces)) {
                player.drop(pieces, false)
            }
            level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, 1.0F)
            if (level is ServerLevel) {
                level.sendParticles(
                    ParticleTypes.CRIT,
                    pos.x + 0.5, pos.y + 0.9, pos.z + 0.5,
                    8, 0.25, 0.1, 0.25, 0.05,
                )
            }
        }
        return InteractionResult.SUCCESS
    }

    // ---------------------------------------------------------------- water cauldron -> soup

    private fun fillCauldron(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        stack: ItemStack,
    ): InteractionResult? {
        if (!state.`is`(Blocks.WATER_CAULDRON) || !stack.`is`(OutbreakItems.WILLOW_BARK_PIECES)) {
            return null
        }

        if (!level.isClientSide) {
            val fillLevel = state.getValue(LayeredCauldronBlock.LEVEL)
            val soup = OutbreakBlocks.WILLOW_SOUP_CAULDRON.defaultBlockState()
                .setValue(WillowSoupCauldronBlock.LEVEL, fillLevel)
                .setValue(WillowSoupCauldronBlock.COOKED, false)
            level.setBlockAndUpdate(pos, soup)
            level.gameEvent(null, GameEvent.FLUID_PLACE, pos)
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.7F, 1.4F)
            stack.shrink(1)
        }
        return InteractionResult.SUCCESS
    }
}
