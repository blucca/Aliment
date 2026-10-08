package com.github.kusa233.aliment.registry

import net.fabricmc.fabric.api.`object`.builder.v1.block.type.BlockSetTypeBuilder
import net.fabricmc.fabric.api.`object`.builder.v1.block.type.WoodTypeBuilder
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.properties.BlockSetType
import net.minecraft.world.level.block.state.properties.WoodType

/**
 * The mod's wood sets use vanilla's own oak-like sound palette. Fabric's builders take care
 * of pushing the new instances into the (otherwise private) [BlockSetType] / [WoodType] maps,
 * which keeps datapack-driven block definitions and `wood_type` codecs working.
 */
object AlimentWoodTypes {

    val WILLOW_SET_TYPE: BlockSetType = BlockSetTypeBuilder()
        .soundType(SoundType.WOOD)
        .register(Registration.id("willow"))

    val WILLOW: WoodType = WoodTypeBuilder()
        .soundType(SoundType.WOOD)
        .hangingSignSoundType(SoundType.HANGING_SIGN)
        .register(Registration.id("willow"), WILLOW_SET_TYPE)

    /**
     * The grapefruit set.
     *
     * A second wood needs its own pair even though it sounds the same as the willow: the door,
     * trapdoor, pressure plate, button and both sign blocks take one in their constructor, and the
     * set type is what ties a wood to its own door/trapdoor sounds and to its own boat.
     */
    val GRAPEFRUIT_SET_TYPE: BlockSetType = BlockSetTypeBuilder()
        .soundType(SoundType.WOOD)
        .register(Registration.id("grapefruit"))

    val GRAPEFRUIT: WoodType = WoodTypeBuilder()
        .soundType(SoundType.WOOD)
        .hangingSignSoundType(SoundType.HANGING_SIGN)
        .register(Registration.id("grapefruit"), GRAPEFRUIT_SET_TYPE)
}
