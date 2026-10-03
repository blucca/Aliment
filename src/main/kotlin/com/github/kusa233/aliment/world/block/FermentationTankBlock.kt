package com.github.kusa233.aliment.world.block

import com.github.kusa233.aliment.registry.AlimentBlockEntities
import com.github.kusa233.aliment.registry.AlimentItems
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
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
        val entity = level.getBlockEntity(pos) as? FermentationTankBlockEntity ?: return InteractionResult.PASS

        // 1. Water Bucket -> fills directly to 3
        if (stack.`is`(Items.WATER_BUCKET)) {
            if (entity.waterLevel < 3) {
                if (!level.isClientSide) {
                    entity.waterLevel = 3
                    entity.setChanged()
                    if (!player.isCreative) {
                        player.setItemInHand(hand, ItemStack(Items.BUCKET))
                    }
                    level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f)
                }
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        // 2. Water Bottle -> adds 1 water level
        val potionContents = stack.get(DataComponents.POTION_CONTENTS)
        if (stack.`is`(Items.POTION) && potionContents?.`is`(Potions.WATER) == true) {
            if (entity.waterLevel < 3) {
                if (!level.isClientSide) {
                    entity.waterLevel++
                    entity.setChanged()
                    if (!player.isCreative) {
                        stack.shrink(1)
                        giveItem(player, ItemStack(Items.GLASS_BOTTLE))
                    }
                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f)
                }
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        // 3. Sugar -> add sugar
        if (stack.`is`(Items.SUGAR)) {
            if (entity.waterLevel > 0 && !entity.hasSugar && entity.ethanol <= 0f) {
                if (!level.isClientSide) {
                    entity.hasSugar = true
                    entity.setChanged()
                    if (!player.isCreative) {
                        stack.shrink(1)
                    }
                    level.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 1.0f, 1.2f)
                }
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        // 4. Brewer's Yeast -> add yeast
        if (stack.`is`(AlimentItems.BREWER_YEAST)) {
            if (entity.waterLevel > 0 && !entity.hasYeast && entity.ethanol <= 0f) {
                if (!level.isClientSide) {
                    entity.hasYeast = true
                    entity.setChanged()
                    if (!player.isCreative) {
                        stack.shrink(1)
                    }
                    level.playSound(null, pos, SoundEvents.HONEY_BLOCK_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f)
                }
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }

        // 5. Glass Bottle -> bottle liquid
        if (stack.`is`(Items.GLASS_BOTTLE)) {
            if (entity.waterLevel > 0) {
                if (!level.isClientSide) {
                    val resultItem = if (entity.ethanol > 0f) {
                        AlimentItems.createWine(entity.ethanol)
                    } else {
                        PotionContents.createItemStack(Items.POTION, Potions.WATER)
                    }

                    entity.waterLevel--
                    if (entity.waterLevel == 0) {
                        entity.ethanol = 0f
                    }
                    entity.setChanged()

                    if (!player.isCreative) {
                        stack.shrink(1)
                        giveItem(player, resultItem)
                    } else {
                        giveItem(player, resultItem)
                    }
                    level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f)
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
        WINE("wine");

        override fun getSerializedName(): String = serialized
    }

    companion object {
        val LEVEL: IntegerProperty = IntegerProperty.create("level", 0, 3)
        val LIQUID: EnumProperty<TankLiquid> = EnumProperty.create("liquid", TankLiquid::class.java)
        val FERMENTING: BooleanProperty = BooleanProperty.create("fermenting")
        private val SHAPE: VoxelShape = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0)
    }
}
