package com.github.kusa233.aliment.world.block

import com.github.kusa233.aliment.registry.AlimentBlockEntities
import com.github.kusa233.aliment.registry.AlimentBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.StringRepresentable
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
 * Tracks water level (0..3), substrate (none, sugar, wheat), presence of yeast,
 * fermentation progress (45 seconds = 900 ticks for 7% ethanol), ethanol concentration,
 * fermented product type (wine or beer), and distillation progress (30 seconds = 600 ticks
 * per water level over a campfire).
 */
class FermentationTankBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(AlimentBlockEntities.FERMENTATION_TANK, pos, state) {

    enum class Substrate(private val serialized: String) : StringRepresentable {
        NONE("none"),
        SUGAR("sugar"),
        WHEAT("wheat"),
        GRAPE("grape");

        override fun getSerializedName(): String = serialized
    }

    enum class FermentedProduct(private val serialized: String) : StringRepresentable {
        NONE("none"),
        WINE("wine"),
        BEER("beer"),
        GRAPE_WINE("grape_wine");

        override fun getSerializedName(): String = serialized
    }

    var waterLevel: Int = 0
        set(value) {
            field = value.coerceIn(0, 3)
            updateBlockState()
        }

    var substrate: Substrate = Substrate.NONE
        set(value) {
            field = value
            updateBlockState()
        }

    var fermentedProduct: FermentedProduct = FermentedProduct.NONE
        set(value) {
            field = value
            updateBlockState()
        }

    /**
     * Whether sugar went in *on top of* the substrate.
     *
     * Only grape must uses this, and it is the one thing [Substrate] cannot express on its own: a
     * single enum says what the tank is fermenting, and grape wine is the one recipe that needs two
     * things in the water at once - the grapes for the flavour and the sugar for the yeast to eat.
     * Sugar on its own is `substrate == SUGAR`; sugar *with* grapes is `substrate == GRAPE` plus
     * this flag, which is why the two can never be confused.
     */
    var sugarWithGrape: Boolean = false
        set(value) {
            field = value
            updateBlockState()
        }

    /**
     * Backward-compatible sugar accessor.
     *
     * "Has sugar" is true for plain sugar *and* for the sugar that completes a grape must, so the
     * setter has to clear both - otherwise `hasSugar = false` would leave a grape must still
     * reporting sugar.
     */
    var hasSugar: Boolean
        get() = substrate == Substrate.SUGAR || sugarWithGrape
        set(value) {
            if (value) {
                substrate = Substrate.SUGAR
            } else {
                sugarWithGrape = false
                if (substrate == Substrate.SUGAR) {
                    substrate = Substrate.NONE
                }
            }
        }

    val hasWheat: Boolean
        get() = substrate == Substrate.WHEAT

    val hasGrape: Boolean
        get() = substrate == Substrate.GRAPE

    /**
     * Whether the tank holds everything the yeast needs.
     *
     * Sugar and wheat are complete substrates on their own; grapes are not, because a grape is
     * mostly water and the sugar is what actually ferments. So grape must is only ready once the
     * sugar is in as well.
     */
    val hasCompleteSubstrate: Boolean
        get() = when (substrate) {
            Substrate.NONE -> false
            Substrate.GRAPE -> sugarWithGrape
            else -> true
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
        get() = waterLevel > 0 && hasCompleteSubstrate && hasYeast && ethanol <= 0f

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
                fermentedProduct == FermentedProduct.BEER -> FermentationTankBlock.TankLiquid.BEER
                fermentedProduct == FermentedProduct.GRAPE_WINE -> FermentationTankBlock.TankLiquid.GRAPE_WINE
                fermentedProduct == FermentedProduct.WINE -> FermentationTankBlock.TankLiquid.WINE
                ethanol > 0f -> FermentationTankBlock.TankLiquid.WINE
                substrate == Substrate.GRAPE -> FermentationTankBlock.TankLiquid.GRAPE
                substrate == Substrate.WHEAT -> FermentationTankBlock.TankLiquid.WHEAT
                substrate == Substrate.SUGAR -> FermentationTankBlock.TankLiquid.SUGAR
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
        output.putString("substrate", substrate.serializedName)
        output.putString("product", fermentedProduct.serializedName)
        output.putBoolean("sugar", hasSugar)
        output.putBoolean("sugar_with_grape", sugarWithGrape)
        output.putBoolean("yeast", hasYeast)
        output.putInt("ferment_progress", fermentProgress)
        output.putFloat("ethanol", ethanol)
        output.putInt("distill_progress", distillProgress)
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        waterLevel = input.getIntOr("water", 0)
        val substrateStr = input.getStringOr("substrate", "")
        substrate = when (substrateStr) {
            "wheat" -> Substrate.WHEAT
            "sugar" -> Substrate.SUGAR
            "grape" -> Substrate.GRAPE
            else -> Substrate.NONE
        }
        sugarWithGrape = input.getBooleanOr("sugar_with_grape", false)
        // Legacy worlds saved only a boolean, and only ever for plain sugar.
        if (substrate == Substrate.NONE && input.getBooleanOr("sugar", false)) {
            substrate = Substrate.SUGAR
        }
        val productStr = input.getStringOr("product", "")
        fermentedProduct = when (productStr) {
            "beer" -> FermentedProduct.BEER
            "grape_wine" -> FermentedProduct.GRAPE_WINE
            "wine" -> FermentedProduct.WINE
            else -> if (input.getFloatOr("ethanol", 0f) > 0f) FermentedProduct.WINE else FermentedProduct.NONE
        }
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

        /**
         * Grape wine's ethanol concentration: 5%.
         *
         * Weaker than [FERMENTED_ETHANOL] on purpose, and the same figure as beer. A wine is drunk
         * as it comes out of the tank rather than distilled, so the tank has to produce the drinking
         * strength directly instead of leaving it to the cauldron.
         */
        const val GRAPE_WINE_ETHANOL: Float = 0.05f

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
                    // Beer is 5% and grape wine is 5% but the tank's generic ferment is 7%: the
                    // product decides the strength, because the two weaker drinks are the two that
                    // are meant to be drunk rather than distilled.
                    when (entity.substrate) {
                        Substrate.WHEAT -> {
                            entity.ethanol = FERMENTED_ETHANOL
                            entity.fermentedProduct = FermentedProduct.BEER
                        }

                        Substrate.GRAPE -> {
                            entity.ethanol = GRAPE_WINE_ETHANOL
                            entity.fermentedProduct = FermentedProduct.GRAPE_WINE
                        }

                        else -> {
                            entity.ethanol = FERMENTED_ETHANOL
                            entity.fermentedProduct = FermentedProduct.WINE
                        }
                    }
                    entity.substrate = Substrate.NONE
                    entity.sugarWithGrape = false
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
                    val isBeer = entity.fermentedProduct == FermentedProduct.BEER
                    if (entity.waterLevel == 0) {
                        entity.ethanol = 0f
                        entity.fermentedProduct = FermentedProduct.NONE
                        entity.substrate = Substrate.NONE
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
                                    val targetState = if (isBeer) {
                                        AlimentBlocks.BEER_CAULDRON.defaultBlockState()
                                            .setValue(BeerCauldronBlock.LEVEL, 1)
                                    } else {
                                        AlimentBlocks.ALCOHOL_CAULDRON.defaultBlockState()
                                            .setValue(AlcoholCauldronBlock.LEVEL, 1)
                                            .setValue(AlcoholCauldronBlock.CONCENTRATION, AlcoholCauldronBlock.AlcoholConcentration.P40)
                                    }
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
                                } else if (isBeer && cauldronState.`is`(AlimentBlocks.BEER_CAULDRON)) {
                                    val currentLevel = cauldronState.getValue(BeerCauldronBlock.LEVEL)
                                    if (currentLevel < 3) {
                                        level.setBlockAndUpdate(
                                            cauldronPos,
                                            cauldronState.setValue(BeerCauldronBlock.LEVEL, currentLevel + 1),
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
                                } else if (!isBeer && cauldronState.`is`(AlimentBlocks.ALCOHOL_CAULDRON) &&
                                    cauldronState.getValue(AlcoholCauldronBlock.CONCENTRATION) == AlcoholCauldronBlock.AlcoholConcentration.P40
                                ) {
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
