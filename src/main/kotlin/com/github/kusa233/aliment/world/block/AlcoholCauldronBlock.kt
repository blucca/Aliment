package com.github.kusa233.aliment.world.block

import com.github.kusa233.aliment.registry.AlimentBlocks
import com.github.kusa233.aliment.registry.AlimentItems
import com.github.kusa233.aliment.world.item.WineItem
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.cauldron.CauldronInteractions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats
import net.minecraft.util.RandomSource
import net.minecraft.util.StringRepresentable
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
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult

/**
 * Distilled alcohol & wine cauldron.
 *
 * Can hold 7% wine, 40% wine, 75% alcohol, or 98% alcohol.
 * When placed over a campfire with a condenser pipe above it, the cauldron acts as a boiler:
 * - 7% wine distills through the condenser pipe into 40% wine in the collection cauldron.
 * - 40% wine distills through the condenser pipe into 75% alcohol in the collection cauldron.
 * - 75% alcohol distills through the condenser pipe into 98% alcohol in the collection cauldron.
 */
class AlcoholCauldronBlock(properties: BlockBehaviour.Properties) :
    AbstractCauldronBlock(properties, CauldronInteractions.EMPTY) {

    enum class AlcoholConcentration(val percent: Int, val fraction: Float) : StringRepresentable {
        P07(7, 0.07f),
        P40(40, 0.40f),
        P75(75, 0.75f),
        P98(98, 0.98f);

        override fun getSerializedName(): String = percent.toString()

        companion object {
            fun fromFraction(fraction: Float): AlcoholConcentration = when {
                fraction <= 0.20f -> P07
                fraction <= 0.60f -> P40
                fraction <= 0.85f -> P75
                else -> P98
            }
        }
    }

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(LEVEL, 1)
                .setValue(CONCENTRATION, AlcoholConcentration.P40),
        )
    }

    override fun isFull(state: BlockState): Boolean = state.getValue(LEVEL) == 3

    override fun getContentHeight(state: BlockState): Double =
        (6.0 + state.getValue(LEVEL) * 3.0) / 16.0

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(LEVEL, CONCENTRATION)
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        startDistillationIfHeated(state, level, pos)
    }

    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        block: Block,
        orientation: Orientation?,
        movedByPiston: Boolean,
    ) {
        startDistillationIfHeated(state, level, pos)
    }

    fun startDistillationIfHeated(state: BlockState, level: Level, pos: BlockPos) {
        if (!isHeated(level, pos)) return
        if (level is ServerLevel && !level.blockTicks.hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, DISTILL_TICKS)
        }
    }

    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        if (isHeated(level, pos)) {
            val h = getContentHeight(state)
            if (random.nextInt(3) == 0) {
                level.addParticle(
                    ParticleTypes.BUBBLE_POP,
                    pos.x + 0.3 + random.nextDouble() * 0.4,
                    pos.y + h,
                    pos.z + 0.3 + random.nextDouble() * 0.4,
                    0.0, 0.02, 0.0,
                )
            }
            if (random.nextInt(4) == 0) {
                level.addParticle(
                    ParticleTypes.SMOKE,
                    pos.x + 0.5,
                    pos.y + h,
                    pos.z + 0.5,
                    0.0, 0.01, 0.0,
                )
            }
        }
    }

    public override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        if (!isHeated(level, pos)) return

        val currentLevel = state.getValue(LEVEL)
        val currentConcentration = state.getValue(CONCENTRATION)

        val nextConcentration = when (currentConcentration) {
            AlcoholConcentration.P07 -> AlcoholConcentration.P40
            AlcoholConcentration.P40 -> AlcoholConcentration.P75
            AlcoholConcentration.P75 -> AlcoholConcentration.P98
            AlcoholConcentration.P98 -> AlcoholConcentration.P98
        }

        // Check condenser pipe system above cauldron
        val riserPos = pos.above()
        val riserState = level.getBlockState(riserPos)

        var collected = false
        if (riserState.block is CondenserPipeBlock) {
            for (dir in Direction.Plane.HORIZONTAL) {
                val sidePos = riserPos.relative(dir)
                val sideState = level.getBlockState(sidePos)
                if (sideState.block is CondenserPipeBlock && sideState.getValue(CondenserPipeBlock.OUTLET_DOWN)) {
                    val cauldronPos = sidePos.below()
                    val cauldronState = level.getBlockState(cauldronPos)

                    if (cauldronState.`is`(Blocks.CAULDRON)) {
                        val targetState = AlimentBlocks.ALCOHOL_CAULDRON.defaultBlockState()
                            .setValue(LEVEL, 1)
                            .setValue(CONCENTRATION, nextConcentration)
                        level.setBlockAndUpdate(cauldronPos, targetState)
                        level.playSound(
                            null, cauldronPos,
                            SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON,
                            SoundSource.BLOCKS, 1.0f, 1.0f,
                        )
                        level.sendParticles(
                            ParticleTypes.DRIPPING_WATER,
                            cauldronPos.x + 0.5, cauldronPos.y + 0.9, cauldronPos.z + 0.5,
                            4, 0.1, 0.1, 0.1, 0.01,
                        )
                        collected = true
                        break
                    } else if (cauldronState.`is`(AlimentBlocks.ALCOHOL_CAULDRON) &&
                        cauldronState.getValue(CONCENTRATION) == nextConcentration
                    ) {
                        val destLevel = cauldronState.getValue(LEVEL)
                        if (destLevel < 3) {
                            level.setBlockAndUpdate(
                                cauldronPos,
                                cauldronState.setValue(LEVEL, destLevel + 1),
                            )
                            level.playSound(
                                null, cauldronPos,
                                SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON,
                                SoundSource.BLOCKS, 1.0f, 1.0f,
                            )
                            level.sendParticles(
                                ParticleTypes.DRIPPING_WATER,
                                cauldronPos.x + 0.5, cauldronPos.y + 0.9, cauldronPos.z + 0.5,
                                4, 0.1, 0.1, 0.1, 0.01,
                            )
                            collected = true
                            break
                        }
                    }
                }
            }
        }

        if (!collected) {
            val ventPos = if (riserState.block is CondenserPipeBlock) riserPos else pos
            level.playSound(null, ventPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.4f)
            level.sendParticles(
                ParticleTypes.CLOUD,
                ventPos.x + 0.5, ventPos.y + 1.0, ventPos.z + 0.5,
                8, 0.2, 0.2, 0.2, 0.05,
            )
        }

        // Lower source level
        if (currentLevel <= 1) {
            level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState())
        } else {
            val newState = state.setValue(LEVEL, currentLevel - 1)
            level.setBlockAndUpdate(pos, newState)
            if (isHeated(level, pos)) {
                level.scheduleTick(pos, this, DISTILL_TICKS)
            }
        }
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
        val currentConcentration = state.getValue(CONCENTRATION)
        val currentLevel = state.getValue(LEVEL)

        // 1. Pour wine/alcohol into cauldron
        if (stack.`is`(AlimentItems.WINE)) {
            val bottleConc = AlcoholConcentration.fromFraction(WineItem.getConcentration(stack))
            if (bottleConc == currentConcentration && currentLevel < 3) {
                if (!level.isClientSide) {
                    val newState = state.setValue(LEVEL, currentLevel + 1)
                    level.setBlockAndUpdate(pos, newState)
                    if (!player.isCreative) {
                        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, ItemStack(Items.GLASS_BOTTLE)))
                    }
                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f)
                    level.gameEvent(null, GameEvent.FLUID_PLACE, pos)
                    player.swing(hand, stack.interactAnimation, true)
                    startDistillationIfHeated(newState, level, pos)
                }
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        // 2. Scoop wine/alcohol with empty glass bottle
        if (stack.`is`(Items.GLASS_BOTTLE)) {
            if (!level.isClientSide) {
                val filledItem = AlimentItems.createWine(currentConcentration.fraction)
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, filledItem))
                player.awardStat(Stats.USE_CAULDRON)
                player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE))

                if (currentLevel <= 1) {
                    level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState())
                } else {
                    val newState = state.setValue(LEVEL, currentLevel - 1)
                    level.setBlockAndUpdate(pos, newState)
                }

                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f)
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos)
                player.swing(hand, stack.interactAnimation, true)
            }
            return InteractionResult.SUCCESS
        }

        return InteractionResult.PASS
    }

    companion object {
        const val DISTILL_TICKS: Int = 600 // 30 seconds
        val LEVEL: IntegerProperty = BlockStateProperties.LEVEL_CAULDRON
        val CONCENTRATION: EnumProperty<AlcoholConcentration> =
            EnumProperty.create("concentration", AlcoholConcentration::class.java)

        fun isHeated(level: BlockGetter, pos: BlockPos): Boolean {
            val below = level.getBlockState(pos.below())
            return (below.`is`(Blocks.CAMPFIRE) || below.`is`(Blocks.SOUL_CAMPFIRE)) &&
                below.getValue(CampfireBlock.LIT)
        }
    }
}
