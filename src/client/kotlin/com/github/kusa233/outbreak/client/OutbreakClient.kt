package com.github.kusa233.outbreak.client

import com.github.kusa233.outbreak.registry.OutbreakBlocks
import com.github.kusa233.outbreak.registry.OutbreakEntities
import com.github.kusa233.outbreak.registry.Registration
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry
import net.minecraft.client.color.block.BlockTintSources
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.`object`.boat.BoatModel
import net.minecraft.client.renderer.entity.BoatRenderer
import net.minecraft.client.renderer.entity.EntityRenderers

class OutbreakClient : ClientModInitializer {

    override fun onInitializeClient() {
        // Willow leaves use the biome foliage colour the same way vanilla leaves do.
        BlockColorRegistry.register(listOf(BlockTintSources.foliage()), OutbreakBlocks.WILLOW_LEAVES)

        // Camera shake driven by the physiology system on the server.
        OutbreakClientShake.initialize()

        // Registering our own model layers keeps the boat textures in the outbreak namespace.
        // BoatRenderer derives the texture from the layer id, so WILLOW_BOAT_LAYER maps to
        // assets/outbreak/textures/entity/boat/willow.png.
        ModelLayerRegistry.registerModelLayer(WILLOW_BOAT_LAYER) { BoatModel.createBoatModel() }
        ModelLayerRegistry.registerModelLayer(WILLOW_CHEST_BOAT_LAYER) { BoatModel.createChestBoatModel() }

        EntityRenderers.register(OutbreakEntities.WILLOW_BOAT) { context ->
            BoatRenderer(context, WILLOW_BOAT_LAYER)
        }
        EntityRenderers.register(OutbreakEntities.WILLOW_CHEST_BOAT) { context ->
            BoatRenderer(context, WILLOW_CHEST_BOAT_LAYER)
        }
    }

    companion object {
        val WILLOW_BOAT_LAYER: ModelLayerLocation =
            ModelLayerLocation(Registration.id("boat/willow"), "main")

        val WILLOW_CHEST_BOAT_LAYER: ModelLayerLocation =
            ModelLayerLocation(Registration.id("chest_boat/willow"), "main")
    }
}
