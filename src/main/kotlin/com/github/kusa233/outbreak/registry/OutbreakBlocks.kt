package com.github.kusa233.outbreak.registry

import com.github.kusa233.outbreak.world.block.AlcoholCauldronBlock
import com.github.kusa233.outbreak.world.block.BrineCauldronBlock
import com.github.kusa233.outbreak.world.block.CondenserPipeBlock
import com.github.kusa233.outbreak.world.block.FermentationTankBlock
import com.github.kusa233.outbreak.world.block.GymnopilusBlock
import com.github.kusa233.outbreak.world.block.MandrakeBlock
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
            .isViewBlocking { _, _, _, _ -> false }
            .ignitedByLava()
            .pushReaction(PushReaction.POPPED)
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
            .pushReaction(PushReaction.POPPED),
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
            .pushReaction(PushReaction.POPPED),
    ) { WillowVinesBlock(it) }

    val WILLOW_VINES_PLANT: Block = Registration.registerBlock(
        "willow_vines_plant",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.POPPED),
    ) { WillowVinesPlantBlock(it) }

    val POTTED_WILLOW_SAPLING: Block = Registration.registerBlock(
        "potted_willow_sapling",
        BlockBehaviour.Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.POPPED),
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
            .pushReaction(PushReaction.POPPED)
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
            .pushReaction(PushReaction.POPPED),
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
            .pushReaction(PushReaction.POPPED),
    ) { PressurePlateBlock(OutbreakWoodTypes.WILLOW_SET_TYPE, it) }

    val WILLOW_BUTTON: Block = Registration.registerBlockWithItem(
        "willow_button",
        BlockBehaviour.Properties.of().noCollision().strength(0.5F).pushReaction(PushReaction.POPPED),
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

    // ---------------------------------------------------------------- salt

    /**
     * Rock salt ore. Grinding the ore itself (not a drop) yields nine crude salt, so it drops
     * itself and is mined with a pickaxe.
     */
    val ROCK_SALT_ORE: Block = Registration.registerBlockWithItem(
        "rock_salt_ore",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(3.0F, 3.0F)
            .sound(SoundType.STONE),
    ) { Block(it) }

    /**
     * Holds crude brine while it evaporates over a fire. Created by using
     * [OutbreakItems.CRUDE_SALT_POWDER] on a water cauldron, so it has no item form of its own.
     */
    val BRINE_CAULDRON: Block = Registration.registerBlock(
        "brine_cauldron",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(2.0F)
            .noOcclusion(),
    ) { BrineCauldronBlock(it) }

    // ---------------------------------------------------------------- mandrake

    /**
     * The mandrake plant, in four stages.
     *
     * It has no item form on purpose, exactly like vanilla's wheat and sweet berry bush: a mandrake
     * is sown with [OutbreakItems.MANDRAKE_SEEDS] rather than placed from the inventory, which is
     * what makes the fruit - and the wait for it - the point of the plant.
     */
    val MANDRAKE: Block = Registration.registerBlock(
        "mandrake",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .pushReaction(PushReaction.POPPED),
    ) { MandrakeBlock(it) }

    // ---------------------------------------------------------------- gymnopilus

    /**
     * The gymnopilus mushroom. Its item form is an ordinary edible mushroom registered in
     * [OutbreakItems], because the food values live with the rest of the food.
     */
    val GYMNOPILUS: Block = Registration.registerBlock(
        "gymnopilus",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .noCollision()
            .instabreak()
            .sound(SoundType.GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .pushReaction(PushReaction.POPPED),
    ) { GymnopilusBlock(it) }

    // ---------------------------------------------------------------- brewing & distillation

    /**
     * Glass Fermentation Tank for brewing wine from water, sugar, and yeast.
     */
    val FERMENTATION_TANK: Block = Registration.registerBlockWithItem(
        "fermentation_tank",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.NONE)
            .strength(0.3F)
            .sound(SoundType.GLASS)
            .noOcclusion(),
    ) { FermentationTankBlock(it) }

    /**
     * Glass Condenser Pipe for distilling alcohol above a fermentation tank.
     */
    val CONDENSER_PIPE: Block = Registration.registerBlockWithItem(
        "condenser_pipe",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.NONE)
            .strength(0.3F)
            .sound(SoundType.GLASS)
            .noOcclusion(),
    ) { CondenserPipeBlock(it) }

    /**
     * Distilled Alcohol Cauldron to collect condensed alcohol from a condenser pipe.
     */
    val ALCOHOL_CAULDRON: Block = Registration.registerBlock(
        "alcohol_cauldron",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(2.0F)
            .noOcclusion(),
    ) { AlcoholCauldronBlock(it) }

    /** Touching this forces the whole object graph to be built. */
    fun initialize() {
    }
}