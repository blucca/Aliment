package com.github.kusa233.aliment.world.tree

import com.github.kusa233.aliment.registry.AlimentBlocks
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType

/**
 * Hangs [AlimentBlocks.WILLOW_VINES] strands underneath the canopy of a willow tree.
 *
 * Serialised as `{"type": "aliment:willow_hanging", "probability": 0.6, "max_length": 5}`.
 */
class WillowHangingDecorator(
    private val probability: Float,
    private val maxLength: Int,
) : TreeDecorator() {

    override fun type(): TreeDecoratorType<*> = AlimentTreeDecorators.WILLOW_HANGING

    override fun place(context: TreeDecorator.Context) {
        val random = context.random()
        // Bottom-up so that strands growing from lower leaves are placed first.
        for (pos in context.leaves()) {
            if (random.nextFloat() >= this.probability) {
                continue
            }
            val below = pos.below()
            if (!context.isAir(below)) {
                continue
            }
            this.hangStrand(below, context)
        }
    }

    private fun hangStrand(start: BlockPos, context: TreeDecorator.Context) {
        val random = context.random()
        var pos = start
        var length = 0
        // The tip (head) block is what the client renders as the tip of the strand, so we walk
        // down while it is still air, then convert the last visited block back into the head.
        while (length < this.maxLength && context.isAir(pos)) {
            val tip = length == this.maxLength - 1 || !context.isAir(pos.below()) || random.nextFloat() < 0.25F
            context.setBlock(
                pos,
                if (tip) AlimentBlocks.WILLOW_VINES.defaultBlockState()
                else AlimentBlocks.WILLOW_VINES_PLANT.defaultBlockState(),
            )
            if (tip) {
                return
            }
            pos = pos.below()
            length++
        }
    }

    companion object {
        val CODEC: MapCodec<WillowHangingDecorator> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter { it.probability },
                Codec.intRange(1, 16).fieldOf("max_length").orElse(5).forGetter { it.maxLength },
            ).apply(instance) { probability, maxLength -> WillowHangingDecorator(probability, maxLength) }
        }
    }
}
