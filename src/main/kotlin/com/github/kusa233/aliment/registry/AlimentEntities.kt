package com.github.kusa233.aliment.registry

import java.util.function.Supplier
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.vehicle.boat.Boat
import net.minecraft.world.entity.vehicle.boat.ChestBoat

/**
 * 26.2 gives every wood its own boat entity type, so the willow boat needs two registrations.
 * The dropped item is resolved lazily through a [Supplier] to avoid an initialisation cycle
 * between [AlimentEntities] and [AlimentItems].
 */
object AlimentEntities {

    private fun boatKey(path: String): ResourceKey<EntityType<*>> =
        ResourceKey.create(Registries.ENTITY_TYPE, Registration.id(path))

    val WILLOW_BOAT: EntityType<Boat> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        boatKey("willow_boat"),
        EntityType.Builder.of(
            EntityType.EntityFactory<Boat> { type, level -> Boat(type, level, Supplier { AlimentItems.WILLOW_BOAT }) },
            MobCategory.MISC,
        )
            .noLootTable()
            .sized(1.375F, 0.5625F)
            .eyeHeight(0.5625F)
            .clientTrackingRange(10)
            .build(boatKey("willow_boat")),
    )

    val WILLOW_CHEST_BOAT: EntityType<ChestBoat> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        boatKey("willow_chest_boat"),
        EntityType.Builder.of(
            EntityType.EntityFactory<ChestBoat> { type, level ->
                ChestBoat(type, level, Supplier { AlimentItems.WILLOW_CHEST_BOAT })
            },
            MobCategory.MISC,
        )
            .noLootTable()
            .sized(1.375F, 0.5625F)
            .eyeHeight(0.5625F)
            .clientTrackingRange(10)
            .build(boatKey("willow_chest_boat")),
    )

    fun initialize() {
    }
}
