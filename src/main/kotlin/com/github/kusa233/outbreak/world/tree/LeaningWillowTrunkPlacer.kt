package com.github.kusa233.outbreak.world.tree

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.function.BiConsumer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.FluidTags
import net.minecraft.util.RandomSource
import net.minecraft.util.valueproviders.IntProvider
import net.minecraft.util.valueproviders.IntProviders
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.feature.TreeFeature
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer

/**
 * Willow trunk placer: a straight trunk whose upper part leans sideways **towards the nearest
 * water**, so riverside willows arch out over the river the way real weeping willows do.
 *
 * The water search is bounded by [searchRadius] and never leaves the world-generation region,
 * so a tree near a chunk border simply grows straight instead of crashing world generation.
 *
 * Serialised as:
 * ```json
 * { "type": "outbreak:leaning_willow_trunk_placer",
 *   "base_height": 5, "height_rand_a": 2, "height_rand_b": 0,
 *   "lean_length": { "type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 3 },
 *   "search_radius": 6 }
 * ```
 */
class LeaningWillowTrunkPlacer(
    baseHeight: Int,
    heightRandA: Int,
    heightRandB: Int,
    private val leanLength: IntProvider,
    private val searchRadius: Int,
) : TrunkPlacer(baseHeight, heightRandA, heightRandB) {

    override fun type(): net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType<*> =
        OutbreakTrunkPlacers.LEANING_WILLOW

    /** The inherited fields are protected, so the codec builder goes through these. */
    fun baseHeightValue(): Int = this.baseHeight
    fun heightRandAValue(): Int = this.heightRandA
    fun heightRandBValue(): Int = this.heightRandB
    fun leanLengthValue(): IntProvider = this.leanLength
    fun searchRadiusValue(): Int = this.searchRadius

    override fun placeTrunk(
        level: WorldGenLevel,
        trunkSetter: BiConsumer<BlockPos, BlockState>,
        random: RandomSource,
        treeHeight: Int,
        origin: BlockPos,
        config: TreeConfiguration,
    ): List<FoliagePlacer.FoliageAttachment> {
        val pos = origin.mutable()
        placeBelowTrunkBlock(level, trunkSetter, random, pos.below(), config)

        val leanDirection = this.findWaterDirection(level, origin)
        // Never lean further than the trunk is tall, otherwise the "bend" would start below the
        // ground.
        val lean = if (leanDirection == null) 0 else this.leanLength.sample(random).coerceIn(0, (treeHeight - 2).coerceAtLeast(0))

        // Spread `lean` sideways steps over the topmost part of the trunk.
        val shiftAt = HashSet<Int>()
        if (lean > 0) {
            val start = (treeHeight - lean).coerceAtLeast(1)
            val span = treeHeight - start
            for (step in 0 until lean) {
                shiftAt.add((start + (step * span) / lean).coerceAtMost(treeHeight - 1))
            }
        }

        for (height in 0 until treeHeight) {
            if (height in shiftAt && leanDirection != null) {
                pos.move(leanDirection)
            }
            if (TreeFeature.validTreePos(level, pos)) {
                this.placeLog(level, trunkSetter, random, pos, config)
            }
            pos.move(Direction.UP)
        }

        // `pos` is now one above the topmost log, exactly like StraightTrunkPlacer returns.
        return listOf(FoliagePlacer.FoliageAttachment(pos.immutable(), 0, false))
    }

    /**
     * Returns the horizontal direction with the most water within [searchRadius], or null when
     * there is no water nearby (or when the scan would leave the generation region).
     */
    private fun findWaterDirection(level: WorldGenLevel, origin: BlockPos): Direction? {
        var best: Direction? = null
        var bestScore = 0
        for (direction in Direction.Plane.HORIZONTAL) {
            var score = 0
            for (distance in 1..this.searchRadius) {
                val column = origin.relative(direction, distance)
                for (dy in -1..1) {
                    if (this.isWater(level, column.offset(0, dy, 0))) {
                        // Closer water counts for more, so the tree leans at the nearest bank.
                        score += this.searchRadius - distance + 1
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score
                best = direction
            }
        }
        return best
    }

    private fun isWater(level: WorldGenLevel, pos: BlockPos): Boolean {
        // WorldGenRegion#getChunk throws for chunks outside the region, so ask first.
        if (pos.y < level.minY || pos.y >= level.maxY) {
            return false
        }
        if (!level.hasChunk(pos.x shr 4, pos.z shr 4)) {
            return false
        }
        return level.getFluidState(pos).`is`(FluidTags.WATER)
    }

    companion object {
        val CODEC: MapCodec<LeaningWillowTrunkPlacer> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.intRange(0, 32).fieldOf("base_height").forGetter { it.baseHeightValue() },
                Codec.intRange(0, 24).fieldOf("height_rand_a").forGetter { it.heightRandAValue() },
                Codec.intRange(0, 24).fieldOf("height_rand_b").forGetter { it.heightRandBValue() },
                IntProviders.codec(0, 8).fieldOf("lean_length").forGetter { it.leanLengthValue() },
                Codec.intRange(1, 12).fieldOf("search_radius").forGetter { it.searchRadiusValue() },
            ).apply(instance) { base, randA, randB, lean, radius ->
                LeaningWillowTrunkPlacer(base, randA, randB, lean, radius)
            }
        }
    }
}
