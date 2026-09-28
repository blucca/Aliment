package com.github.kusa233.outbreak.registry

import com.github.kusa233.outbreak.world.block.WillowSoupCauldronBlock
import com.github.kusa233.outbreak.world.block.WillowVinesBlock
import com.github.kusa233.outbreak.world.block.WillowVinesPlantBlock
import net.minecraft.world.item.DoubleHighBlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.CeilingHangingSignBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.FlowerPotBlock
import net.minecraft.world.level.block.PressurePlateBlock
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.level.block.SaplingBlock
import net.minecraft.world.level.block.ShelfBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.StandingSignBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.TintedParticleLeavesBlock
import net.minecraft.world.level.block.WallHangingSignBlock
import net.minecraft.world.level.block.WallSignBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction

/**
 * Every willow block. Each entry registers the block and (where applicable) its [Item] in one
 * step, mirroring how vanilla's `Blocks` + `Items` pair cooperates in 26.2.
 *
 * Declaration order matters: later blocks derive their properties from earlier ones.
 */
object OutbreakBlocks {

    /** Bark / plank-ish properties shared by most of the set. */
    private fun woodProperties(): BlockBehaviour.Properties = BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_BROWN)
        .instrument(NoteBlockInstrument.BASS)
        .strength(2.0F, 3.0F)
        .sound(SoundType.WOOD)
        .ignitedByLava()

    // ---------------------------------------------------------------- logs & bark

    val WILLOW_LOG: Block = Registration.registerBlockWithItem(
        "willow_log",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F)
            .sound(SoundType.WOOD)
            .ignitedByLava(),
    ) { RotatedPillarBlock(it) }

    val WILLOW_WOOD: Block = Registration.registerBlockWithItem(
        "willow_wood",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F)
            .sound(SoundType.WOOD)
            .ignitedByLava(),
    ) { RotatedPillarBlock(it) }

    val STRIPPED_WILLOW_LOG: Block = Registration.registerBlockWithItem(
        "stripped_willow_log",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F)
            .sound(SoundType.WOOD)
            .ignitedByLava(),
    ) { RotatedPillarBlock(it) }

    val STRIPPED_WILLOW_WOOD: Block = Registration.registerBlockWithItem(
        "stripped_willow_wood",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.0F)
            .sound(SoundType.WOOD)
            .ignitedByLava(),
    ) { RotatedPillarBlock(it) }

    // ---------------------------------------------------------------- planks

    val WILLOW_PLANKS: Block = Registration.registerBlockWithItem("willow_planks", woodProperties()) { Block(it) }

    // ---------------------------------------------------------------- leaves & sapling

    val WILLOW_LEAVES: Block = Registration.registerBlockWithItem(
        "willow_leaves",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .strength(0.2F)
            .randomTicks()
            .sound(SoundType.GRASS)
            .noOcclusion()
            .isSuffocating { _, _, _ -> false }
            .isViewBlocking { _, _, _ -> false }
            .ignitedByLava()
            .pushReaction(PushReaction.DESTROY)
            .isRedstoneConductor { _, _, _ -> false },
    ) { TintedParticleLeavesBlock(0.01F, it) }

    val WILLOW_SAPLING: Block = Registration.registerBlockWithItem(
        "willow_sapling",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.DESTROY),
    ) { SaplingBlock(OutbreakTreeGrowers.WILLOW, it) }

    // ---------------------------------------------------------------- willow vines (drooping strands)

    val WILLOW_VINES: WillowVinesBlock = Registration.registerBlockWithItem(
        "willow_vines",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.DESTROY),
    ) { WillowVinesBlock(it) }

    val WILLOW_VINES_PLANT: Block = Registration.registerBlock(
        "willow_vines_plant",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.DESTROY),
    ) { WillowVinesPlantBlock(it) }

    val POTTED_WILLOW_SAPLING: Block = Registration.registerBlock(
        "potted_willow_sapling",
        BlockBehaviour.Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.DESTROY),
    ) { FlowerPotBlock(WILLOW_SAPLING, it) }

    // ---------------------------------------------------------------- shaped wooden blocks

    val WILLOW_STAIRS: Block = Registration.registerBlockWithItem("willow_stairs", woodProperties()) {
        StairBlock(WILLOW_PLANKS.defaultBlockState(), it)
    }

    val WILLOW_SLAB: Block = Registration.registerBlockWithItem("willow_slab", woodProperties()) { SlabBlock(it) }

    val WILLOW_FENCE: Block = Registration.registerBlockWithItem("willow_fence", woodProperties()) { FenceBlock(it) }

    val WILLOW_FENCE_GATE: Block = Registration.registerBlockWithItem("willow_fence_gate", woodProperties()) {
        FenceGateBlock(OutbreakWoodTypes.WILLOW, it)
    }

    val WILLOW_DOOR: Block = Registration.registerBlockWithItem(
        "willow_door",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(3.0F)
            .noOcclusion()
            .pushReaction(PushReaction.DESTROY)
            .ignitedByLava(),
        Item.Properties().stacksTo(16),
        { block, props -> DoubleHighBlockItem(block, props) },
    ) { DoorBlock(OutbreakWoodTypes.WILLOW_SET_TYPE, it) }

    val WILLOW_TRAPDOOR: Block = Registration.registerBlockWithItem(
        "willow_trapdoor",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .strength(3.0F)
            .noOcclusion()
            .isValidSpawn { _, _, _, _ -> false }
            .ignitedByLava()
            .pushReaction(PushReaction.DESTROY),
    ) { TrapDoorBlock(OutbreakWoodTypes.WILLOW_SET_TYPE, it) }

    val WILLOW_PRESSURE_PLATE: Block = Registration.registerBlockWithItem(
        "willow_pressure_plate",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(0.5F)
            .ignitedByLava()
            .pushReaction(PushReaction.DESTROY),
    ) { PressurePlateBlock(OutbreakWoodTypes.WILLOW_SET_TYPE, it) }

    val WILLOW_BUTTON: Block = Registration.registerBlockWithItem(
        "willow_button",
        BlockBehaviour.Properties.of().noCollision().strength(0.5F).pushReaction(PushReaction.DESTROY),
    ) { ButtonBlock(OutbreakWoodTypes.WILLOW_SET_TYPE, 30, it) }

    val WILLOW_SHELF: Block = Registration.registerBlockWithItem(
        "willow_shelf",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .instrument(NoteBlockInstrument.BASS)
            .sound(SoundType.SHELF)
            .ignitedByLava()
            .strength(2.0F, 3.0F),
    ) { ShelfBlock(it) }

    // ---------------------------------------------------------------- signs

    val WILLOW_SIGN: Block = Registration.registerBlock(
        "willow_sign",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(1.0F)
            .ignitedByLava(),
    ) { StandingSignBlock(OutbreakWoodTypes.WILLOW, it) }

    val WILLOW_WALL_SIGN: Block = Registration.registerBlock(
        "willow_wall_sign",
        BlockBehaviour.Properties.of()
            .overrideLootTable(WILLOW_SIGN.lootTable)
            .overrideDescription(WILLOW_SIGN.descriptionId)
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(1.0F)
            .ignitedByLava(),
    ) { WallSignBlock(OutbreakWoodTypes.WILLOW, it) }

    val WILLOW_HANGING_SIGN: Block = Registration.registerBlock(
        "willow_hanging_sign",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(1.0F)
            .ignitedByLava(),
    ) { CeilingHangingSignBlock(OutbreakWoodTypes.WILLOW, it) }

    val WILLOW_WALL_HANGING_SIGN: Block = Registration.registerBlock(
        "willow_wall_hanging_sign",
        BlockBehaviour.Properties.of()
            .overrideLootTable(WILLOW_HANGING_SIGN.lootTable)
            .overrideDescription(WILLOW_HANGING_SIGN.descriptionId)
            .mapColor(MapColor.COLOR_BROWN)
            .forceSolidOn()
            .instrument(NoteBlockInstrument.BASS)
            .noCollision()
            .strength(1.0F)
            .ignitedByLava(),
    ) { WallHangingSignBlock(OutbreakWoodTypes.WILLOW, it) }

    // ---------------------------------------------------------------- willow bark soup cauldron

    /**
     * Filled with raw willow bark soup, then cooked by a campfire underneath. Created by using
     * [OutbreakItems.WILLOW_BARK_PIECES] on a water cauldron, so it has no item form of its own.
     */
    val WILLOW_SOUP_CAULDRON: Block = Registration.registerBlock(
        "willow_soup_cauldron",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(2.0F)
            .noOcclusion(),
    ) { WillowSoupCauldronBlock(it) }

    /** Touching this forces the whole object graph to be built. */
    fun initialize() {
    }
}
