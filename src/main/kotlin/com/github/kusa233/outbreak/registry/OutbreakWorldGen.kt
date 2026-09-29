package com.github.kusa233.outbreak.registry

import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.placement.PlacedFeature

/**
 * Injects the willow grove into river biomes, which is what makes the trees show up on river
 * banks, and scatters rock salt ore through the overworld.
 *
 * The placed feature itself only succeeds when a willow sapling could survive there, so the trees
 * never end up standing in the water.
 *
 * Both are registered here in code: Fabric's biome modification API is code-only, so a
 * `worldgen/biome_modification` JSON file would simply be ignored.
 */
object OutbreakWorldGen {

    val WILLOW_RIVER: ResourceKey<PlacedFeature> =
        ResourceKey.create(Registries.PLACED_FEATURE, Registration.id("willow_river"))

    val ROCK_SALT_ORE: ResourceKey<PlacedFeature> =
        ResourceKey.create(Registries.PLACED_FEATURE, Registration.id("rock_salt_ore"))

    fun initialize() {
        BiomeModifications.addFeature(
            BiomeSelectors.tag(BiomeTags.IS_RIVER),
            GenerationStep.Decoration.VEGETAL_DECORATION,
            WILLOW_RIVER,
        )
        BiomeModifications.addFeature(
            BiomeSelectors.foundInOverworld(),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            ROCK_SALT_ORE,
        )
    }
}
