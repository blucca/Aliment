package com.github.kusa233.aliment.world.block

import com.github.kusa233.aliment.registry.AlimentBlockEntities
import com.github.kusa233.aliment.registry.AlimentBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * Block entity for the glass fermentation tank.
 *
 * Tracks water level (0..3), presence of sugar and yeast, fermentation progress
 * (5 minutes = 6000 ticks for 7% ethanol), ethanol concentration, and distillation
 * progress (30 seconds = 600 ticks per water level over a campfire).
 */
class FermentationTankBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(AlimentBlockEntities.FERMENTATION_TANK, pos, state) {

    var waterLevel: Int = 0
        set(value) {
            field = value.coerceIn(0, 3)
            updateBlockState()
        }

    var hasSugar: Boolean = false
        set(value) {
            field = value
            updateBlockState()
        }

    var hasYeast: Boolean = false
        set(value) {
            field = value
            updateBlockState()
        }

    var fermentProgress: Int = 0

    var ethanol: Float = 0f
        set(value) {
            field = value
            updateBlockState()
        }

    var distillProgress: Int = 0

    val isFermenting: Boolean
        get() = waterLevel > 0 && hasSugar && hasYeast && ethanol <= 0f

    val isDistilling: Boolean
        get() = waterLevel > 0 && ethanol > 0f && isHeated

    val isHeated: Boolean
        get() {
            val lvl = level ?: return false
            val below = lvl.getBlockState(worldPosition.below())
            return (below.`is`(Blocks.CAMPFIRE) || below.`is`(Blocks.SOUL_CAMPFIRE)) &&
                below.getValue(CampfireBlock.LIT)
        }

    private fun updateBlockState() {
        val lvl = level ?: return
        val current = blockState
        if (current.block is FermentationTankBlock) {
            val liquidType = when {
                ethanol > 0f -> FermentationTankBlock.TankLiquid.WINE
                hasSugar -> FermentationTankBlock.TankLiquid.SUGAR
                else -> FermentationTankBlock.TankLiquid.WATER
            }
            val updated = current
                .setValue(FermentationTankBlock.LEVEL, waterLevel)
                .setValue(FermentationTankBlock.LIQUID, liquidType)
                .setValue(FermentationTankBlock.FERMENTING, isFermenting)
            if (updated != current) {
                lvl.setBlockAndUpdate(worldPosition, updated)
            }
        }
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        output.putInt("water", waterLevel)
        output.putBoolean("sugar", hasSugar)
        output.putBoolean("yeast", hasYeast)
        output.putInt("ferment_progress", fermentProgress)
        output.putFloat("ethanol", ethanol)
        output.putInt("distill_progress", distillProgress)
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        waterLevel = input.getIntOr("water", 0)
        hasSugar = input.getBooleanOr("sugar", false)
        hasYeast = input.getBooleanOr("yeast", false)
        fermentProgress = input.getIntOr("ferment_progress", 0)
        ethanol = input.getFloatOr("ethanol", 0f)
        distillProgress = input.getIntOr("distill_progress", 0)
    }

    companion object {
        /** 45 seconds of fermentation: 45 * 20 = 900 ticks. */
        const val FERMENT_TICKS: Int = 900

        /** 30 seconds of distillation: 30 * 20 = 600 ticks. */
        const val DISTILL_TICKS: Int = 600

        /** Default fermented ethanol concentration: 7%. */
        const val FERMENTED_ETHANOL: Float = 0.07f

        /** Distilled ethanol concentration: 40%. */
        const val DISTILLED_ETHANOL: Float = 0.40f

        @JvmStatic
        fun serverTick(level: Level, pos: BlockPos, state: BlockState, entity: FermentationTankBlockEntity) {
            if (level !is ServerLevel) return

            var changed = false

            // --- 1. Fermentation ---
            if (entity.isFermenting) {
                entity.fermentProgress++
                changed = true
                if (level.random.nextInt(40) == 0) {
                    level.sendParticles(
                        ParticleTypes.BUBBLE,
                        pos.x + 0.5, pos.y + 0.3 + entity.waterLevel * 0.2, pos.z + 0.5,
                        2, 0.15, 0.05, 0.15, 0.01,
                    )
                }

                if (entity.fermentProgress >= FERMENT_TICKS) {
                    entity.fermentProgress = 0
                    entity.ethanol = FERMENTED_ETHANOL
                    entity.hasSugar = false
                    entity.hasYeast = false
                    level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0f, 1.0f)
                    level.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        pos.x + 0.5, pos.y + 0.8, pos.z + 0.5,
                        6, 0.2, 0.2, 0.2, 0.05,
                    )
                }
            } else if (entity.ethanol <= 0f && entity.fermentProgress > 0) {
                entity.fermentProgress = 0
                changed = true
            }

            // --- 2. Distillation over Campfire ---
            if (entity.isDistilling) {
                entity.distillProgress++
                changed = true

                if (level.random.nextInt(20) == 0) {
                    level.sendParticles(
                        ParticleTypes.BUBBLE_POP,
                        pos.x + 0.5, pos.y + 0.3 + entity.waterLevel * 0.2, pos.z + 0.5,
                        2, 0.15, 0.05, 0.15, 0.02,
                    )
                }

                if (entity.distillProgress >= DISTILL_TICKS) {
                    entity.distillProgress = 0
                    entity.waterLevel--
                    if (entity.waterLevel == 0) {
                        entity.ethanol = 0f
                    }

                    // Check condenser pipe system above the tank
                    val riserPos = pos.above()
                    val riserState = level.getBlockState(riserPos)

                    var collected = false
                    if (riserState.block is CondenserPipeBlock) {
                        // Find connected side pipe
                        for (dir in Direction.Plane.HORIZONTAL) {
                            val sidePos = riserPos.relative(dir)
                            val sideState = level.getBlockState(sidePos)
                            if (sideState.block is CondenserPipeBlock && sideState.getValue(CondenserPipeBlock.OUTLET_DOWN)) {
                                // Side pipe found, check if cauldron is underneath
                                val cauldronPos = sidePos.below()
                                val cauldronState = level.getBlockState(cauldronPos)

                                if (cauldronState.`is`(Blocks.CAULDRON)) {
                                    level.setBlockAndUpdate(
                                        cauldronPos,
                                        AlimentBlocks.ALCOHOL_CAULDRON.defaultBlockState()
                                            .setValue(AlcoholCauldronBlock.LEVEL, 1),
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
                                } else if (cauldronState.`is`(AlimentBlocks.ALCOHOL_CAULDRON)) {
                                    val currentLevel = cauldronState.getValue(AlcoholCauldronBlock.LEVEL)
                                    if (currentLevel < 3) {
                                        level.setBlockAndUpdate(
                                            cauldronPos,
                                            cauldronState.setValue(AlcoholCauldronBlock.LEVEL, currentLevel + 1),
                                        )
                                    }
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

                    if (!collected) {
                        // Liquid evaporated into thin air without collection
                        val ventPos = if (riserState.block is CondenserPipeBlock) riserPos else pos
                        level.playSound(null, ventPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.4f)
                        level.sendParticles(
                            ParticleTypes.CLOUD,
                            ventPos.x + 0.5, ventPos.y + 1.0, ventPos.z + 0.5,
                            8, 0.2, 0.2, 0.2, 0.05,
                        )
                    }
                }
            } else if (entity.distillProgress > 0) {
                entity.distillProgress = 0
                changed = true
            }

            if (changed) {
                entity.setChanged()
                entity.updateBlockState()
            }
        }
    }
}
