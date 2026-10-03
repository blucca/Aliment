package com.github.kusa233.aliment.registry

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.util.random.Weighted
import net.minecraft.util.random.WeightedList
import net.minecraft.world.level.block.grower.TreeGrower
import net.minecraft.world.level.levelgen.feature.Feature

/**
 * The features a willow sapling is allowed to grow into.
 *
 * 26.3 removed ConfiguredFeature: a feature is now a registry entry of its own, so these keys
 * point straight at the `worldgen/feature` JSON files.
 */
object AlimentTreeGrowers {

    val WILLOW_TREE: ResourceKey<Feature> =
        ResourceKey.create(Registries.FEATURE, Registration.id("willow"))

    val TALL_WILLOW_TREE: ResourceKey<Feature> =
        ResourceKey.create(Registries.FEATURE, Registration.id("tall_willow"))

    /**
     * 65% of the time a sapling grows into the regular weeping willow, otherwise the taller
     * riverside variant. Both have the same drooping canopy, only the trunk length differs.
     */
    val WILLOW: TreeGrower = TreeGrower(
        "willow",
        WeightedList.of(
            Weighted(WILLOW_TREE, 65),
            Weighted(TALL_WILLOW_TREE, 35),
        ),
        // No 2x2 mega variant and no flowering variant.
        WeightedList.of(),
        WeightedList.of(),
        WILLOW_TREE,
    )
}
