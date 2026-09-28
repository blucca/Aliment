package com.github.kusa233.outbreak.registry

import java.util.Optional
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.grower.TreeGrower
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature

/** Configured features that willow saplings are allowed to grow into. */
object OutbreakTreeGrowers {

    val WILLOW_TREE: ResourceKey<ConfiguredFeature<*, *>> =
        ResourceKey.create(Registries.CONFIGURED_FEATURE, Registration.id("willow"))

    val TALL_WILLOW_TREE: ResourceKey<ConfiguredFeature<*, *>> =
        ResourceKey.create(Registries.CONFIGURED_FEATURE, Registration.id("tall_willow"))

    /**
     * 65% of the time a sapling grows into the regular weeping willow, otherwise the taller
     * riverside variant. Both have the same drooping canopy, only the trunk length differs.
     */
    val WILLOW: TreeGrower = TreeGrower(
        "willow",
        0.35F,
        Optional.empty(),
        Optional.empty(),
        Optional.of(WILLOW_TREE),
        Optional.of(TALL_WILLOW_TREE),
        Optional.empty(),
        Optional.empty(),
    )
}
