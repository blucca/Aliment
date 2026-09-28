package com.github.kusa233.outbreak.world.tree

import com.github.kusa233.outbreak.registry.Registration
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType

object OutbreakTreeDecorators {

    val WILLOW_HANGING: TreeDecoratorType<WillowHangingDecorator> =
        Registry.register(
            BuiltInRegistries.TREE_DECORATOR_TYPE,
            Registration.id("willow_hanging"),
            TreeDecoratorType(WillowHangingDecorator.CODEC),
        )

    fun initialize() {
        // Forces class initialization so the decorator type exists before worldgen JSON is parsed.
    }
}
