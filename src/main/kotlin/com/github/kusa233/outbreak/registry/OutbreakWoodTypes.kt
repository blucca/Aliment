package com.github.kusa233.outbreak.registry

import net.fabricmc.fabric.api.`object`.builder.v1.block.type.BlockSetTypeBuilder
import net.fabricmc.fabric.api.`object`.builder.v1.block.type.WoodTypeBuilder
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.properties.BlockSetType
import net.minecraft.world.level.block.state.properties.WoodType

/**
 * The willow wood set uses vanilla's own oak-like sound palette. Fabric's builders take care
 * of pushing the new instances into the (otherwise private) [BlockSetType] / [WoodType] maps,
 * which keeps datapack-driven block definitions and `wood_type` codecs working.
 */
object OutbreakWoodTypes {

    val WILLOW_SET_TYPE: BlockSetType = BlockSetTypeBuilder()
        .soundType(SoundType.WOOD)
        .register(Registration.id("willow"))

    val WILLOW: WoodType = WoodTypeBuilder()
        .soundType(SoundType.WOOD)
        .hangingSignSoundType(SoundType.HANGING_SIGN)
        .register(Registration.id("willow"), WILLOW_SET_TYPE)
}
