package com.github.kusa233.aliment.event

import com.github.kusa233.aliment.advancement.AlimentAdvancements
import com.github.kusa233.aliment.physiology.AlimentIngestion
import com.github.kusa233.aliment.registry.AlimentBlocks
import com.github.kusa233.aliment.registry.AlimentItems
import com.github.kusa233.aliment.world.AlimentGrinding
import com.github.kusa233.aliment.world.block.BrineCauldronBlock
import com.github.kusa233.aliment.world.block.WillowSoupCauldronBlock
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats
import net.minecraft.tags.BiomeTags
import net.minecraft.tags.FluidTags
import net.minecraft.tags.ItemTags
import net.minecraft.util.Prediction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUtils
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.HitResult
import org.apache.logging.log4j.LogManager

/**
 * Right-click interactions that vanilla block classes do not cover:
 *
 * 1. Stripping a willow log/wood with an axe also drops [AlimentItems.WILLOW_BARK].
 * 2. Grinding on a grindstone: willow bark to bark pieces, rock salt ore to nine crude salt,
 *    and crude salt to crude salt powder.
 * 3. Dropping [AlimentItems.WILLOW_BARK_PIECES] into a water cauldron turns the water into raw
 *    willow bark soup.
 * 4. Dropping [AlimentItems.CRUDE_SALT_POWDER] into a water cauldron turns it into brine.
 * 5. Stirring a brine cauldron with a [AlimentItems.STIRRING_ROD] skips it on one stage.
 * 6. Injecting [AlimentItems.DEXAMETHASONE_INJECTION].
 */
object AlimentInteractions {

    private val LOGGER = LogManager.getLogger("AlimentInteractions")

    private val STRIPPABLES: Map<Block, Block> = mapOf(
        AlimentBlocks.WILLOW_LOG to AlimentBlocks.STRIPPED_WILLOW_LOG,
        AlimentBlocks.WILLOW_WOOD to AlimentBlocks.STRIPPED_WILLOW_WOOD,
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
                ?: grind(level, pos, state, stack, player)
                ?: fillCauldron(level, pos, state, stack)
                ?: stirBrine(level, pos, state, stack, player, hand)
                ?: fillBottleAt(player, level, hand, pos)
                ?: InteractionResult.PASS
        }

        UseItemCallback.EVENT.register { player, level, hand ->
            inject(player, level, hand)
                ?: fillBottleFromView(player, level, hand)
                ?: InteractionResult.PASS
        }
    }

    // ---------------------------------------------------------------- biome water bottles

    /**
     * Fills a glass bottle from the water the player right-clicked.
     *
     * Two hooks feed this, because the two code paths reach different places: a block hit (the
     * riverbed, when the crosshair ray passes through water) arrives through `UseBlockCallback`,
     * and a ray that ends on the water surface itself arrives through `UseItemCallback`. Whichever
     * fires first consumes the bottle, so the other finds nothing to do.
     */
    private fun fillBottleAt(
        player: Player,
        level: Level,
        hand: InteractionHand,
        hitPos: BlockPos,
    ): InteractionResult? {
        // The crosshair ray ignores fluids, so the water is usually one block above the hit.
        val waterPos = when {
            level.getFluidState(hitPos).`is`(FluidTags.WATER) -> hitPos
            level.getFluidState(hitPos.above()).`is`(FluidTags.WATER) -> hitPos.above()
            else -> return null
        }
        return fillBottle(player, level, hand, waterPos)
    }

    /** The `UseItemCallback` path: cast the same ray vanilla's bottle uses and take the fluid hit. */
    private fun fillBottleFromView(player: Player, level: Level, hand: InteractionHand): InteractionResult? {
        if (!player.getItemInHand(hand).`is`(Items.GLASS_BOTTLE)) {
            return null
        }
        val reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)
        val eye = player.getEyePosition(1.0F)
        val target = eye.add(player.getViewVector(1.0F).scale(reach))
        val hit = level.clip(
            ClipContext(eye, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player),
        )
        if (hit.type != HitResult.Type.BLOCK) {
            return null
        }
        return fillBottle(player, level, hand, hit.blockPos)
    }

    private fun fillBottle(
        player: Player,
        level: Level,
        hand: InteractionHand,
        waterPos: BlockPos,
    ): InteractionResult? {
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.GLASS_BOTTLE)) {
            return null
        }
        if (!level.getFluidState(waterPos).`is`(FluidTags.WATER)) {
            return null
        }

        val biome = level.getBiome(waterPos)
        val biomeId = biome.unwrapKey().map { it.identifier().toString() }.orElse("unknown")
        val filled = biomeWater(biome) ?: run {
            // A river or anything else: vanilla hands out its own water bottle.
            LOGGER.info("bottle fill at {} left to vanilla (biome {})", waterPos, biomeId)
            return null
        }

        if (!level.isClientSide) {
            LOGGER.info("bottle fill at {} gave {} (biome {})", waterPos, filled.descriptionId, biomeId)
            val used = stack.item
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, ItemStack(filled)))
            player.awardStat(Stats.ITEM_USED.get(used))
            level.playSound(null, waterPos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F)
            level.gameEvent(null, GameEvent.FLUID_PICKUP, waterPos)
        }
        return InteractionResult.SUCCESS
    }

    /** Swamp and ocean water have their own bottles; everything else is vanilla's business. */
    private fun biomeWater(biome: Holder<Biome>): Item? {
        if (biome.`is`(BiomeTags.IS_OCEAN)) {
            return AlimentItems.SEA_WATER_BOTTLE
        }
        val key = biome.unwrapKey().orElse(null) ?: return null
        val path = key.identifier().path
        return when {
            path == "swamp" || path == "mangrove_swamp" -> AlimentItems.SWAMP_WATER_BOTTLE
            else -> null
        }
    }

    // ---------------------------------------------------------------- dexamethasone

    /**
     * Applies a dexamethasone injection.
     *
     * Returns null - never `PASS` - when the held item is something else. Every handler in this
     * class is chained with `?:`, so a non-null "did nothing" value would silently stop the chain
     * and nothing after it would ever run.
     */
    private fun inject(player: Player, level: Level, hand: InteractionHand): InteractionResult? {
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(AlimentItems.DEXAMETHASONE_INJECTION)) {
            return null
        }
        if (player.cooldowns.isOnCooldown(stack)) {
            return InteractionResult.FAIL
        }
        if (level.isClientSide || player !is ServerPlayer) {
            return InteractionResult.SUCCESS
        }

        AlimentIngestion.injectDexamethasone(player)
        player.cooldowns.addCooldown(stack, INJECTION_COOLDOWN_TICKS)
        level.playSound(
            null,
            player.x, player.y, player.z,
            SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 1.0F, 0.7F,
        )
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1)
        }
        return InteractionResult.SUCCESS
    }

    /** Long enough that a player cannot stack injections faster than the drug clears. */
    private const val INJECTION_COOLDOWN_TICKS = 200

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
            Block.popResource(level, pos, ItemStack(AlimentItems.WILLOW_BARK))
            if (player is ServerPlayer) {
                AlimentAdvancements.award(player, AlimentAdvancements.ANCIENT_ANTI_INFLAMMATORY)
            }
        }
        level.playSound(player, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 1.0F, 1.0F)
        return InteractionResult.SUCCESS
    }

    // ---------------------------------------------------------------- grindstone

    /**
     * Sneak-right-click shortcut: converts one item without opening the menu.
     *
     * A **plain** right click deliberately falls through to vanilla so the grindstone menu opens
     * and the item can be placed in the input slot, which is what the mixins widen. Sneaking keeps
     * the fast path for bulk work.
     */
    private fun grind(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        stack: ItemStack,
        player: Player,
    ): InteractionResult? {
        if (!state.`is`(Blocks.GRINDSTONE) || !player.isSecondaryUseActive) {
            return null
        }
        val output = AlimentGrinding.outputFor(stack) ?: return null

        if (!level.isClientSide) {
            stack.shrink(1)
            val produced = ItemStack(output.first, output.second)
            if (!player.addItem(produced)) {
                // 26.3 added an explicit client-prediction argument to the drop helpers.
                player.drop(produced, false, Prediction.SERVER_ONLY)
            }
            if (player is ServerPlayer) {
                AlimentAdvancements.onGrind(player, produced)
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

    // ---------------------------------------------------------------- water cauldron -> soup or brine

    private fun fillCauldron(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        stack: ItemStack,
    ): InteractionResult? {
        if (!state.`is`(Blocks.WATER_CAULDRON)) {
            return null
        }

        if (stack.`is`(AlimentItems.WILLOW_BARK_PIECES)) {
            if (!level.isClientSide) {
                val fillLevel = state.getValue(LayeredCauldronBlock.LEVEL)
                val soup = AlimentBlocks.WILLOW_SOUP_CAULDRON.defaultBlockState()
                    .setValue(WillowSoupCauldronBlock.LEVEL, fillLevel)
                    .setValue(WillowSoupCauldronBlock.COOKED, false)
                level.setBlockAndUpdate(pos, soup)
                level.gameEvent(null, GameEvent.FLUID_PLACE, pos)
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.7F, 1.4F)
                stack.shrink(1)
            }
            return InteractionResult.SUCCESS
        }

        if (stack.`is`(AlimentItems.CRUDE_SALT_POWDER)) {
            if (!level.isClientSide) {
                val brine = AlimentBlocks.BRINE_CAULDRON.defaultBlockState()
                    .setValue(BrineCauldronBlock.STAGE, 0)
                level.setBlockAndUpdate(pos, brine)
                level.gameEvent(null, GameEvent.FLUID_PLACE, pos)
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.7F, 1.2F)
                if (level is ServerLevel) {
                    level.sendParticles(
                        ParticleTypes.SPLASH,
                        pos.x + 0.5, pos.y + 0.9, pos.z + 0.5,
                        10, 0.2, 0.05, 0.2, 0.0,
                    )
                }
                stack.shrink(1)
            }
            return InteractionResult.SUCCESS
        }

        return null
    }

    // ---------------------------------------------------------------- stirring brine

    private fun stirBrine(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        stack: ItemStack,
        player: Player,
        hand: InteractionHand,
    ): InteractionResult? {
        if (state.block !is BrineCauldronBlock || !stack.`is`(AlimentItems.STIRRING_ROD)) {
            return null
        }

        if (!level.isClientSide && level is ServerLevel) {
            (state.block as BrineCauldronBlock).stir(state, level, pos)
            stack.hurtAndBreak(1, player, hand.asEquipmentSlot())
        }
        return InteractionResult.SUCCESS
    }
}
