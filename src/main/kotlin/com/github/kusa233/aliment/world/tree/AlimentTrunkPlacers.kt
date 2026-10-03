package com.github.kusa233.aliment.world.tree

import com.github.kusa233.aliment.registry.Registration
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType

object AlimentTrunkPlacers {

    val LEANING_WILLOW: TrunkPlacerType<LeaningWillowTrunkPlacer> =
        Registry.register(
            BuiltInRegistries.TRUNK_PLACER_TYPE,
            Registration.id("leaning_willow_trunk_placer"),
            TrunkPlacerType(LeaningWillowTrunkPlacer.CODEC),
        )

    fun initialize() {
        // Forces class initialisation so the placer type exists before worldgen JSON is parsed.
    }
}
