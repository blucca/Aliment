package com.github.kusa233.aliment.world.block

import com.github.kusa233.aliment.registry.AlimentBlockEntities
import com.github.kusa233.aliment.registry.AlimentItems
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Prediction
import net.minecraft.util.StringRepresentable
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Glass Fermentation Tank.
 *
 * Crafted from 7 glass blocks with 1 plank in the top-middle slot.
 * Holds up to 3 water levels, sugar, and brewer's yeast.
 * Ferments water + sugar + yeast into 7% ethanol wine over 5 minutes.
 */
class FermentationTankBlock(properties: BlockBehaviour.Properties) : BaseEntityBlock(properties) {

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(LEVEL, 0)
                .setValue(LIQUID, TankLiquid.WATER)
                .setValue(FERMENTING, false),
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(LEVEL, LIQUID, FERMENTING)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        FermentationTankBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T>,
    ): BlockEntityTicker<T>? {
        return if (level.isClientSide) null else createTickerHelper(
            blockEntityType,
            AlimentBlockEntities.FERMENTATION_TANK,
            FermentationTankBlockEntity::serverTick,
        )
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = SHAPE

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): InteractionResult {
        val currentLevel = state.getValue(LEVEL)
        val currentLiquid = state.getValue(LIQUID)

        // 1. Water Bucket -> fills directly to 3
        if (stack.`is`(Items.WATER_BUCKET)) {
            if (currentLevel < 3) {
                if (!level.isClientSide) {
                    val entity = level.getBlockEntity(pos) as? FermentationTankBlockEntity ?: return InteractionResult.PASS
                    // No topping up a tank that already holds a fermented product. The ethanol is
                    // stored as a *concentration*, not as a total, so water poured in after
                    // fermentation comes back out as another full-strength bottle: bottle one, refill
                    // to three, bottle three more, and a single dose of sugar and yeast yields without
                    // limit. The water the tank held when fermentation finished is the whole batch.
                    if (entity.ethanol > 0f) {
                        return InteractionResult.PASS
                    }
                    entity.waterLevel = 3
                    entity.setChanged()
                    if (!player.isCreative) {
                        player.setItemInHand(hand, ItemStack(Items.BUCKET))
                    }
                    level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f)
                    if (level is ServerLevel) {
                        level.sendParticles(ParticleTypes.SPLASH, pos.x + 0.5, pos.y + 0.8, pos.z + 0.5, 8, 0.15, 0.1, 0.15, 0.05)
                    }
                    player.swing(hand, stack.getInteractAnimation(), true)
                }
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        // 2. Water Bottle -> adds 1 water level
        val potionContents = stack.get(DataComponents.POTION_CONTENTS)
        if (stack.`is`(Items.POTION) && potionContents?.`is`(Potions.WATER) == true) {
            if (currentLevel < 3) {
                if (!level.isClientSide) {
                    val entity = level.getBlockEntity(pos) as? FermentationTankBlockEntity ?: return InteractionResult.PASS
                    // Same rule as the water bucket above, and it has to be repeated here rather than
                    // shared, because this branch reads the *block state* level while the bucket
                    // branch reaches for the block entity.
                    if (entity.ethanol > 0f) {
                        return InteractionResult.PASS
                    }
                    entity.waterLevel++
                    entity.setChanged()
                    if (!player.isCreative) {
                        stack.shrink(1)
                        giveItem(player, ItemStack(Items.GLASS_BOTTLE))
                    }
                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f)
                    if (level is ServerLevel) {
                        level.sendParticles(ParticleTypes.SPLASH, pos.x + 0.5, pos.y + 0.8, pos.z + 0.5, 5, 0.1, 0.1, 0.1, 0.05)
                    }
                    player.swing(hand, stack.getInteractAnimation(), true)
                }
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        // 3a. Sugar -> add sugar
        if (stack.`is`(Items.SUGAR)) {
            if (currentLevel > 0 && (currentLiquid == TankLiquid.WATER || currentLiquid == TankLiquid.GRAPE)) {
                if (!level.isClientSide) {
                    val entity = level.getBlockEntity(pos) as? FermentationTankBlockEntity ?: return InteractionResult.PASS
                    if (entity.ethanol > 0f) {
                        return InteractionResult.PASS
                    }
                    when (entity.substrate) {
                        // Sugar on its own is the whole substrate: this is the plain wine path.
                        FermentationTankBlockEntity.Substrate.NONE ->
                            entity.substrate = FermentationTankBlockEntity.Substrate.SUGAR

                        // Grapes went in first, so the sugar completes the must instead of
                        // replacing it.
                        FermentationTankBlockEntity.Substrate.GRAPE -> {
                            if (entity.sugarWithGrape) {
                                return InteractionResult.PASS
                            }
                            entity.sugarWithGrape = true
                        }

                        else -> return InteractionResult.PASS
                    }
                    entity.setChanged()
                    if (!player.isCreative) {
                        stack.shrink(1)
                    }
                    level.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 1.0f, 1.2f)
                    if (level is ServerLevel) {
                        level.sendParticles(ParticleTypes.WHITE_SMOKE, pos.x + 0.5, pos.y + 0.85, pos.z + 0.5, 6, 0.1, 0.05, 0.1, 0.02)
                    }
                    player.swing(hand, stack.getInteractAnimation(), true)
                }
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        // 3b. Wheat -> add wheat (alternative to sugar)
        if (stack.`is`(Items.WHEAT)) {
            if (currentLevel > 0 && currentLiquid == TankLiquid.WATER) {
                if (!level.isClientSide) {
                    val entity = level.getBlockEntity(pos) as? FermentationTankBlockEntity ?: return InteractionResult.PASS
                    if (entity.substrate != FermentationTankBlockEntity.Substrate.NONE || entity.ethanol > 0f) {
                        return InteractionResult.PASS
                    }
                    entity.substrate = FermentationTankBlockEntity.Substrate.WHEAT
                    entity.setChanged()
                    if (!player.isCreative) {
                        stack.shrink(1)
                    }
                    level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f)
                    if (level is ServerLevel) {
                        level.sendParticles(ParticleTypes.COMPOSTER, pos.x + 0.5, pos.y + 0.85, pos.z + 0.5, 5, 0.1, 0.05, 0.1, 0.02)
                    }
                    player.swing(hand, stack.getInteractAnimation(), true)
                }
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        // 3c. Grapes -> add grapes. Sugar must be in as well before yeast will take, but the two
        // may go in either order: a recipe that reads "grapes and sugar" should not depend on which
        // the player happens to be holding first.
        if (stack.`is`(AlimentItems.GRAPE)) {
            if (currentLevel > 0 && (currentLiquid == TankLiquid.WATER || currentLiquid == TankLiquid.SUGAR)) {
                if (!level.isClientSide) {
                    val entity = level.getBlockEntity(pos) as? FermentationTankBlockEntity ?: return InteractionResult.PASS
                    if (entity.ethanol > 0f) {
                        return InteractionResult.PASS
                    }
                    when (entity.substrate) {
                        // Grapes first: the sugar still has to follow.
                        FermentationTankBlockEntity.Substrate.NONE ->
                            entity.substrate = FermentationTankBlockEntity.Substrate.GRAPE

                        // Sugar first: the grapes complete the must instead, and the tank is
                        // promoted from plain sugar to grape must.
                        FermentationTankBlockEntity.Substrate.SUGAR -> {
                            entity.substrate = FermentationTankBlockEntity.Substrate.GRAPE
                            entity.sugarWithGrape = true
                        }

                        else -> return InteractionResult.PASS
                    }
                    entity.setChanged()
                    if (!player.isCreative) {
                        stack.shrink(1)
                    }
                    level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 0.8f)
                    if (level is ServerLevel) {
                        level.sendParticles(ParticleTypes.COMPOSTER, pos.x + 0.5, pos.y + 0.85, pos.z + 0.5, 5, 0.1, 0.05, 0.1, 0.02)
                    }
                    player.swing(hand, stack.getInteractAnimation(), true)
                }
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        // 4. Brewer's Yeast -> add yeast
        if (stack.`is`(AlimentItems.BREWER_YEAST)) {
            if (currentLevel > 0 &&
                (currentLiquid == TankLiquid.SUGAR || currentLiquid == TankLiquid.WHEAT || currentLiquid == TankLiquid.GRAPE)
            ) {
                if (!level.isClientSide) {
                    val entity = level.getBlockEntity(pos) as? FermentationTankBlockEntity ?: return InteractionResult.PASS
                    if (!entity.hasCompleteSubstrate || entity.hasYeast || entity.ethanol > 0f) {
                        return InteractionResult.PASS
                    }
                    entity.hasYeast = true
                    entity.setChanged()
                    if (!player.isCreative) {
                        stack.shrink(1)
                    }
                    level.playSound(null, pos, SoundEvents.HONEY_BLOCK_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f)
                    if (level is ServerLevel) {
                        level.sendParticles(ParticleTypes.FALLING_HONEY, pos.x + 0.5, pos.y + 0.85, pos.z + 0.5, 6, 0.1, 0.05, 0.1, 0.02)
                    }
                    player.swing(hand, stack.getInteractAnimation(), true)
                }
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        // 5. Glass Bottle -> bottle liquid
        if (stack.`is`(Items.GLASS_BOTTLE)) {
            if (currentLevel > 0) {
                // Fermented wheat mash cannot be bottled directly - it must be distilled to obtain beer!
                if (currentLiquid == TankLiquid.BEER) {
                    return InteractionResult.PASS
                }

                if (!level.isClientSide) {
                    val entity = level.getBlockEntity(pos) as? FermentationTankBlockEntity ?: return InteractionResult.PASS
                    if (entity.ethanol > 0f && entity.fermentedProduct == FermentationTankBlockEntity.FermentedProduct.BEER) {
                        return InteractionResult.PASS
                    }

                    val resultItem = if (entity.ethanol > 0f) {
                        // A grape wine is bottled as itself at 5%: unlike beer it needs no cauldron,
                        // because 5% is already the strength it is meant to be drunk at.
                        if (entity.fermentedProduct == FermentationTankBlockEntity.FermentedProduct.GRAPE_WINE) {
                            AlimentItems.createGrapeWine()
                        } else {
                            AlimentItems.createWine(entity.ethanol)
                        }
                    } else {
                        PotionContents.createItemStack(Items.POTION, Potions.WATER)
                    }

                    entity.waterLevel--
                    if (entity.waterLevel == 0) {
                        entity.ethanol = 0f
                        entity.fermentedProduct = FermentationTankBlockEntity.FermentedProduct.NONE
                        entity.substrate = FermentationTankBlockEntity.Substrate.NONE
                        entity.sugarWithGrape = false
                    }
                    entity.setChanged()

                    if (!player.isCreative) {
                        stack.shrink(1)
                        giveItem(player, resultItem)
                    } else {
                        giveItem(player, resultItem)
                    }
                    level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f)
                    player.swing(hand, stack.getInteractAnimation(), true)
                }
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        return InteractionResult.PASS
    }

    private fun giveItem(player: Player, stack: ItemStack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false, Prediction.SERVER_ONLY)
        }
    }

    enum class TankLiquid(private val serialized: String) : StringRepresentable {
        WATER("water"),
        SUGAR("sugar"),
        WINE("wine"),
        WHEAT("wheat"),
        BEER("beer"),
        GRAPE("grape"),
        GRAPE_WINE("grape_wine");

        override fun getSerializedName(): String = serialized
    }

    companion object {
        val LEVEL: IntegerProperty = IntegerProperty.create("level", 0, 3)
        val LIQUID: EnumProperty<TankLiquid> = EnumProperty.create("liquid", TankLiquid::class.java)
        val FERMENTING: BooleanProperty = BooleanProperty.create("fermenting")
        private val SHAPE: VoxelShape = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0)
    }
}
