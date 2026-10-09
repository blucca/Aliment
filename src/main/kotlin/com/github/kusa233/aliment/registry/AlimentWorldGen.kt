package com.github.kusa233.aliment.registry

import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.placement.PlacedFeature

/**
 * Injects the willow grove into river biomes, which is what makes the trees show up on river
 * banks, scatters rock salt ore through the overworld, and sows mandrake patches in the plains and
 * the swamps.
 *
 * The placed feature itself only succeeds when a willow sapling could survive there, so the trees
 * never end up standing in the water, and the mandrake patch asks for grass before it plants
 * anything, so it never ends up in the water either.
 *
 * All of them are registered here in code: Fabric's biome modification API is code-only, so a
 * `worldgen/biome_modification` JSON file would simply be ignored.
 */
object AlimentWorldGen {

    val WILLOW_RIVER: ResourceKey<PlacedFeature> =
        ResourceKey.create(Registries.PLACED_FEATURE, Registration.id("willow_river"))

    val ROCK_SALT_ORE: ResourceKey<PlacedFeature> =
        ResourceKey.create(Registries.PLACED_FEATURE, Registration.id("rock_salt_ore"))

    val MANDRAKE_PATCH: ResourceKey<PlacedFeature> =
        ResourceKey.create(Registries.PLACED_FEATURE, Registration.id("mandrake_patch"))

    val GYMNOPILUS_PATCH: ResourceKey<PlacedFeature> =
        ResourceKey.create(Registries.PLACED_FEATURE, Registration.id("gymnopilus_patch"))

    val EPHEDRA_PATCH: ResourceKey<PlacedFeature> =
        ResourceKey.create(Registries.PLACED_FEATURE, Registration.id("ephedra_patch"))

    val COPTIS_PATCH: ResourceKey<PlacedFeature> =
        ResourceKey.create(Registries.PLACED_FEATURE, Registration.id("coptis_patch"))

    val PHELLODENDRON_PATCH: ResourceKey<PlacedFeature> =
        ResourceKey.create(Registries.PLACED_FEATURE, Registration.id("phellodendron_patch"))

    val LICORICE_PATCH: ResourceKey<PlacedFeature> =
        ResourceKey.create(Registries.PLACED_FEATURE, Registration.id("licorice_patch"))

    val SEAWEED_PATCH: ResourceKey<PlacedFeature> =
        ResourceKey.create(Registries.PLACED_FEATURE, Registration.id("seaweed_patch"))

    val GRAPEFRUIT_GROVE: ResourceKey<PlacedFeature> =
        ResourceKey.create(Registries.PLACED_FEATURE, Registration.id("grapefruit_grove"))

    val GRAPE_VINE_PATCH: ResourceKey<PlacedFeature> =
        ResourceKey.create(Registries.PLACED_FEATURE, Registration.id("grape_vine_patch"))

    /**
     * Where a grape vine grows wild: the temperate, sunlit biomes a vineyard belongs in.
     *
     * This is not decoration - it is how a player gets their first grapes. The vine's own seeds
     * only come out of a ripe bunch, so without a wild patch the crop would be unobtainable in
     * survival, which is the same reason the mandrake gets one.
     */
    private val GRAPE_VINE_BIOMES = listOf(
        Biomes.PLAINS,
        Biomes.SUNFLOWER_PLAINS,
        Biomes.FOREST,
        Biomes.FLOWER_FOREST,
        Biomes.BIRCH_FOREST,
        Biomes.OLD_GROWTH_BIRCH_FOREST,
        Biomes.MEADOW,
    )

    /**
     * Where a grapefruit tree grows wild: the warm, wet and warm-dry biomes a citrus belongs in.
     *
     * Deliberately not every non-cold biome - a grapefruit in a taiga would be as out of place as
     * kelp in a desert - but wide enough that a player who wants one can find one without a hunt.
     */
    private val GRAPEFRUIT_BIOMES = listOf(
        Biomes.JUNGLE,
        Biomes.SPARSE_JUNGLE,
        Biomes.BAMBOO_JUNGLE,
        Biomes.SAVANNA,
        Biomes.SAVANNA_PLATEAU,
        Biomes.WINDSWEPT_SAVANNA,
    )

    /**
     * Where a mandrake grows wild: both plains and both swamps, since a sunflower plain is still a
     * plain and a mangrove swamp is still a swamp.
     */
    private val MANDRAKE_BIOMES = listOf(
        Biomes.PLAINS,
        Biomes.SUNFLOWER_PLAINS,
        Biomes.SWAMP,
        Biomes.MANGROVE_SWAMP,
    )

    /**
     * Where the gymnopilus grows wild: the damp, shaded and woody biomes a rustgill belongs in. It
     * also grows in the two swamps the mandrake likes, but the patches are placed separately.
     */
    private val GYMNOPILUS_BIOMES = listOf(
        Biomes.DARK_FOREST,
        Biomes.SWAMP,
        Biomes.MANGROVE_SWAMP,
        Biomes.OLD_GROWTH_PINE_TAIGA,
        Biomes.OLD_GROWTH_SPRUCE_TAIGA,
        Biomes.TAIGA,
    )

    /**
     * Where ephedra grows wild: arid deserts, badlands, and windswept hills.
     */
    private val EPHEDRA_BIOMES = listOf(
        Biomes.DESERT,
        Biomes.BADLANDS,
        Biomes.ERODED_BADLANDS,
        Biomes.WOODED_BADLANDS,
        Biomes.WINDSWEPT_HILLS,
        Biomes.SAVANNA,
        Biomes.SAVANNA_PLATEAU,
        Biomes.WINDSWEPT_SAVANNA,
    )

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
        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(MANDRAKE_BIOMES),
            GenerationStep.Decoration.VEGETAL_DECORATION,
            MANDRAKE_PATCH,
        )
        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(GYMNOPILUS_BIOMES),
            GenerationStep.Decoration.VEGETAL_DECORATION,
            GYMNOPILUS_PATCH,
        )
        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(EPHEDRA_BIOMES),
            GenerationStep.Decoration.VEGETAL_DECORATION,
            EPHEDRA_PATCH,
        )

        val nonColdOverworld = BiomeSelectors.foundInOverworld().and { context ->
            context.biome.baseTemperature >= 0.2f && !context.hasTag(BiomeTags.SPAWNS_COLD_VARIANT_FROGS)
        }
        BiomeModifications.addFeature(
            nonColdOverworld,
            GenerationStep.Decoration.VEGETAL_DECORATION,
            COPTIS_PATCH,
        )
        BiomeModifications.addFeature(
            nonColdOverworld,
            GenerationStep.Decoration.VEGETAL_DECORATION,
            PHELLODENDRON_PATCH,
        )
        BiomeModifications.addFeature(
            nonColdOverworld,
            GenerationStep.Decoration.VEGETAL_DECORATION,
            LICORICE_PATCH,
        )
        BiomeModifications.addFeature(
            BiomeSelectors.tag(BiomeTags.IS_OCEAN),
            GenerationStep.Decoration.VEGETAL_DECORATION,
            SEAWEED_PATCH,
        )
        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(GRAPEFRUIT_BIOMES),
            GenerationStep.Decoration.VEGETAL_DECORATION,
            GRAPEFRUIT_GROVE,
        )
        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(GRAPE_VINE_BIOMES),
            GenerationStep.Decoration.VEGETAL_DECORATION,
            GRAPE_VINE_PATCH,
        )
    }
}
