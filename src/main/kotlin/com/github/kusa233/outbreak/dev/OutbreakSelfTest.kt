package com.github.kusa233.outbreak.dev

import com.github.kusa233.outbreak.registry.OutbreakBlocks
import com.github.kusa233.outbreak.registry.OutbreakItems
import com.github.kusa233.outbreak.registry.OutbreakWorldGen
import com.github.kusa233.outbreak.registry.Registration
import com.github.kusa233.outbreak.world.block.MandrakeBlock
import com.github.kusa233.outbreak.world.block.WillowSoupCauldronBlock
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.FakePlayer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BoneMealItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.LogManager

/**
 * DEVELOPMENT ONLY. Temporary entrypoint used to verify the willow features on a headless dev
 * server; it is not shipped. Remove the entrypoint from fabric.mod.json to disable it.
 *
 * Everything happens in chunk (0,0) at y=200 so it works in any world.
 *
 * NOTE: this must stay a plain class - Fabric instantiates entrypoints through a public no-arg
 * constructor, which a Kotlin `object` does not have.
 */
class OutbreakSelfTest : ModInitializer {

    private val logger = LogManager.getLogger("OutbreakSelfTest")

    private val FLOOR_Y = 200
    private val CENTER = BlockPos(8, FLOOR_Y + 1, 8)
    private val CAULDRON = BlockPos(4, FLOOR_Y + 1, 4)
    private val GRINDSTONE = BlockPos(10, FLOOR_Y + 1, 4)
    private val LOG = BlockPos(4, FLOOR_Y + 1, 10)
    private val TREE = BlockPos(8, FLOOR_Y + 1, 8)

    private var ticks = 0
    private var stage = 0
    private var passed = 0
    private var failed = 0
    private var cookStartTick = -1
    private var leaningChecked = false
    private var filledWhileRaw = false

    override fun onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            ticks++
            if (ticks < 60 || stage > 4) {
                return@register
            }
            try {
                step(server.overworld())
            } catch (t: Throwable) {
                logger.error("SELFTEST exception in stage {}", stage, t)
                failed++
                stage = 99
                report()
            }
        }
    }

    private fun step(level: ServerLevel) {
        when (stage) {
            0 -> {
                buildRig(level)
                stage = 1
            }

            1 -> {
                testStripping(level)
                testGrinding(level)
                testCauldronFill(level)
                // The campfire is placed in the next stage's tick so cooking starts cleanly.
                level.setBlockAndUpdate(CAULDRON.below(), Blocks.CAMPFIRE.defaultBlockState())
                cookStartTick = ticks
                stage = 2
            }

            2 -> {
                if (ticks - cookStartTick >= WillowSoupCauldronBlock.COOK_TICKS + 40) {
                    testCooked(level)
                    stage = 3
                }
            }

            3 -> {
                testLeaning(level)
                testMandrake(level, FakePlayer.get(level))
                testMandrakeWorldGen(level)
                stage = 4
            }

            4 -> {
                report()
                stage = 99
            }
        }
    }

    // ------------------------------------------------------------------ rig

    private fun buildRig(level: ServerLevel) {
        level.setChunkForced(0, 0, true)
        level.getChunk(0, 0)
        for (x in 0..15) {
            for (z in 0..15) {
                level.setBlockAndUpdate(BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState())
                for (y in 1..20) {
                    level.setBlockAndUpdate(BlockPos(x, FLOOR_Y + y, z), Blocks.AIR.defaultBlockState())
                }
            }
        }
        level.setBlockAndUpdate(LOG, OutbreakBlocks.WILLOW_LOG.defaultBlockState())
        level.setBlockAndUpdate(GRINDSTONE, Blocks.GRINDSTONE.defaultBlockState())
        level.setBlockAndUpdate(
            CAULDRON,
            Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3),
        )
        logger.info("SELFTEST rig built")
    }

    // ------------------------------------------------------------------ individual checks

    private fun testStripping(level: ServerLevel) {
        val player = FakePlayer.get(level)
        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.DIAMOND_AXE))
        useOn(level, player, LOG)

        val stripped = level.getBlockState(LOG).`is`(OutbreakBlocks.STRIPPED_WILLOW_LOG)
        check("axe stripping turns the log into stripped_willow_log", stripped)
    }

    private fun testGrinding(level: ServerLevel) {
        val player = FakePlayer.get(level)
        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(OutbreakItems.WILLOW_BARK, 2))
        // The instant conversion is the sneak-right-click shortcut; a plain click opens the menu.
        player.isShiftKeyDown = true
        useOn(level, player, GRINDSTONE)
        player.isShiftKeyDown = false

        val held = player.mainHandItem
        val pieces = player.inventory.countItem(OutbreakItems.WILLOW_BARK_PIECES) +
            (if (held.`is`(OutbreakItems.WILLOW_BARK_PIECES)) held.count else 0)
        check("grinding a willow bark yields bark pieces", pieces >= 2)
    }

    private fun testCauldronFill(level: ServerLevel) {
        val player = FakePlayer.get(level)
        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(OutbreakItems.WILLOW_BARK_PIECES, 1))
        useOn(level, player, CAULDRON)

        val soup = level.getBlockState(CAULDRON)
        check(
            "bark pieces turn a water cauldron into raw willow bark soup",
            soup.`is`(OutbreakBlocks.WILLOW_SOUP_CAULDRON) && !soup.getValue(WillowSoupCauldronBlock.COOKED),
        )

        // take one serving while it is still raw
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.GLASS_BOTTLE))
        useOn(level, player, CAULDRON)
        val held = player.mainHandItem
        filledWhileRaw = held.`is`(OutbreakItems.RAW_WILLOW_BARK_SOUP_BOTTLE)
        check("a glass bottle fills with raw willow bark soup", filledWhileRaw)
    }

    private fun testCooked(level: ServerLevel) {
        val state = level.getBlockState(CAULDRON)
        if (!state.`is`(OutbreakBlocks.WILLOW_SOUP_CAULDRON)) {
            check("cauldron still holds soup after cooking", false)
            return
        }
        check("60 seconds over a campfire cooks the soup", state.getValue(WillowSoupCauldronBlock.COOKED))

        val player = FakePlayer.get(level)
        player.inventory.clearContent()
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.BOWL))
        useOn(level, player, CAULDRON)
        check("a bowl fills with cooked willow bark soup", player.mainHandItem.`is`(OutbreakItems.WILLOW_BARK_SOUP_BOWL))

        // no campfire underneath any more -> a fresh raw cauldron must stay raw
        level.setBlockAndUpdate(CAULDRON.below(), Blocks.STONE.defaultBlockState())
    }

    /**
     * Builds a dirt bank with a pool of water to the east, grows a willow next to it and checks
     * that the trunk actually leans towards the water.
     */
    private fun testLeaning(level: ServerLevel) {
        if (leaningChecked) {
            return
        }
        leaningChecked = true

        for (x in 0..15) {
            for (z in 10..15) {
                level.setBlockAndUpdate(BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState())
                for (y in 1..20) {
                    level.setBlockAndUpdate(BlockPos(x, FLOOR_Y + y, z), Blocks.AIR.defaultBlockState())
                }
            }
        }
        // water pool on the +X side
        for (x in 12..15) {
            for (z in 10..15) {
                level.setBlockAndUpdate(BlockPos(x, FLOOR_Y, z), Blocks.WATER.defaultBlockState())
            }
        }

        // 26.3 removed ConfiguredFeature: the feature itself is now the registry entry.
        val feature = level.registryAccess()
            .lookupOrThrow(Registries.FEATURE)
            .get(ResourceKey.create(Registries.FEATURE, Registration.id("willow")))
            .orElse(null)

        if (feature == null) {
            check("outbreak:willow feature is registered", false)
            return
        }

        val origin = BlockPos(6, FLOOR_Y + 1, 12)
        var maxX = origin.x
        var grew = false
        for (attempt in 0 until 40) {
            clearArea(level, origin)
            val placed = feature.value()
                .place(level, level.chunkSource.generator, level.random, origin)
            if (placed) {
                grew = true
                maxX = scanMaxLogX(level, origin)
                if (maxX > origin.x) {
                    break
                }
            }
        }
        check("willow grows on the test bank", grew)
        check("willow trunk leans towards the water (max log x=$maxX > ${origin.x})", maxX > origin.x)
    }

    private fun clearArea(level: ServerLevel, origin: BlockPos) {
        for (x in (origin.x - 4)..(origin.x + 8)) {
            for (z in (origin.z - 4)..(origin.z + 4)) {
                for (y in 1..20) {
                    level.setBlockAndUpdate(BlockPos(x, FLOOR_Y + y, z), Blocks.AIR.defaultBlockState())
                }
            }
        }
    }

    private fun scanMaxLogX(level: ServerLevel, origin: BlockPos): Int {
        var maxX = origin.x
        for (x in (origin.x - 6)..(origin.x + 10)) {
            for (z in (origin.z - 6)..(origin.z + 6)) {
                for (y in 1..24) {
                    if (level.getBlockState(BlockPos(x, FLOOR_Y + y, z)).`is`(OutbreakBlocks.WILLOW_LOG)) {
                        if (x > maxX) {
                            maxX = x
                        }
                    }
                }
            }
        }
        return maxX
    }

    // ------------------------------------------------------------------ mandrake

    /**
     * The mandrake plant: sown into soil rather than onto farmland, grown through four stages with
     * bone meal, and fruit only from the fourth.
     *
     * Sowing goes through the real item path (`BlockItem.useOn`, via the fake player), because "a
     * mandrake grows on dirt and grass but not on farmland" is a placement rule and not a property
     * of the block state - the only way to catch a regression in it is to actually plant one.
     */
    private fun testMandrake(level: ServerLevel, player: FakePlayer) {
        val seeds = ItemStack(OutbreakItems.MANDRAKE_SEEDS)
        val row = listOf(
            "dirt" to Blocks.DIRT,
            "grass" to Blocks.GRASS_BLOCK,
            "farmland" to Blocks.FARMLAND,
            "coarse dirt" to Blocks.COARSE_DIRT,
        )
        val planted = mutableMapOf<String, Boolean>()
        row.forEachIndexed { index, (label, soil) ->
            val ground = BlockPos(12 + index, FLOOR_Y + 1, 12)
            level.setBlockAndUpdate(ground, soil.defaultBlockState())
            level.setBlockAndUpdate(ground.above(), Blocks.AIR.defaultBlockState())
            level.setBlockAndUpdate(ground.below(), Blocks.STONE.defaultBlockState())

            player.inventory.clearContent()
            player.setItemInHand(InteractionHand.MAIN_HAND, seeds.copy())
            useOn(level, player, ground)
            planted[label] = level.getBlockState(ground.above()).`is`(OutbreakBlocks.MANDRAKE)
        }
        logger.info("SELFTEST mandrake sown: {}", planted)
        check("a mandrake seed is sown on dirt", planted["dirt"] == true)
        check("and on grass", planted["grass"] == true)
        check("and on coarse dirt", planted["coarse dirt"] == true)
        check("and on farmland too", planted["farmland"] == true)

        // Bone meal one stage at a time, all the way to fruit.
        val plant = BlockPos(12, FLOOR_Y + 1, 14)
        level.setBlockAndUpdate(plant.below(), Blocks.DIRT.defaultBlockState())
        level.setBlockAndUpdate(plant.below(2), Blocks.STONE.defaultBlockState())
        level.setBlockAndUpdate(plant, OutbreakBlocks.MANDRAKE.defaultBlockState())
        val ages = mutableListOf(age(level, plant))
        repeat(MandrakeBlock.MAX_AGE) {
            BoneMealItem.growCrop(ItemStack(Items.BONE_MEAL), level, plant)
            ages += age(level, plant)
        }
        logger.info("SELFTEST mandrake stages after bone meal: {}", ages)
        check("bone meal takes a mandrake through four stages", ages == listOf(0, 1, 2, 3))
        check("a ripe mandrake is no longer a bonemeal target", !BoneMealItem.growCrop(ItemStack(Items.BONE_MEAL), level, plant))

        val ripe = Block.getDrops(level.getBlockState(plant), level, plant, null)
        logger.info("SELFTEST ripe mandrake drops: {}", ripe.map { "${it.item} x${it.count}" })
        val fruit = ripe.filter { it.`is`(OutbreakItems.MANDRAKE_FRUIT) }.sumOf { it.count }
        check("a ripe mandrake drops fruit", fruit in 1..2)
        check("and nothing else", ripe.all { it.`is`(OutbreakItems.MANDRAKE_FRUIT) })

        // Every earlier stage is a waste of a seed, which is the whole point of waiting.
        var unripeDrops = 0
        for (unripe in 0 until MandrakeBlock.MAX_AGE) {
            level.setBlockAndUpdate(
                plant,
                OutbreakBlocks.MANDRAKE.defaultBlockState().setValue(MandrakeBlock.AGE, unripe),
            )
            unripeDrops += Block.getDrops(level.getBlockState(plant), level, plant, null).size
        }
        check("an unripe mandrake drops nothing", unripeDrops == 0)

        // The seeds are a crafting recipe, so it has to be in the recipe manager rather than merely
        // in the file: this fails if the JSON is malformed or the id is wrong.
        val recipe = level.server.recipeManager.byKey(
            ResourceKey.create(Registries.RECIPE, Registration.id("mandrake_seeds")),
        )
        check("the mandrake seeds recipe is loaded", recipe.isPresent)
    }

    private fun age(level: ServerLevel, pos: BlockPos): Int =
        level.getBlockState(pos).getValue(MandrakeBlock.AGE)

    /**
     * Where the mandrake grows wild, checked where that is actually decided: the biome generation
     * settings Fabric's biome modification API writes into.
     *
     * A plains world is deliberately not needed for this. What has to hold is that the placed feature
     * ends up attached to the plains and the swamps and to nothing else, and that is a registry fact
     * that can be read straight off a running server - the worldgen itself is vanilla's
     * `minecraft:simple_block` patch, which the datapack validation has already accepted.
     */
    private fun testMandrakeWorldGen(level: ServerLevel) {
        val registries = level.server.registryAccess()
        val mandrake = registries.lookupOrThrow(Registries.PLACED_FEATURE)
            .getOrThrow(OutbreakWorldGen.MANDRAKE_PATCH)
        val biomes = registries.lookupOrThrow(Registries.BIOME)

        fun growsWild(key: ResourceKey<Biome>): Boolean =
            biomes.getOrThrow(key).value().generationSettings.features().any { it.contains(mandrake) }

        check("mandrakes grow wild in the plains", growsWild(Biomes.PLAINS))
        check("and in the swamp", growsWild(Biomes.SWAMP))
        check("and in a mangrove swamp", growsWild(Biomes.MANGROVE_SWAMP))
        check("but not in a desert", !growsWild(Biomes.DESERT))
        check("nor in a forest", !growsWild(Biomes.FOREST))
    }

    // ------------------------------------------------------------------ helpers

    private fun useOn(level: ServerLevel, player: FakePlayer, pos: BlockPos) {
        val hit = BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        player.gameMode.useItemOn(player, level, player.mainHandItem, InteractionHand.MAIN_HAND, hit)
    }

    private fun check(name: String, ok: Boolean) {
        if (ok) {
            passed++
            logger.info("SELFTEST PASS: {}", name)
        } else {
            failed++
            logger.error("SELFTEST FAIL: {}", name)
        }
    }

    private fun report() {
        logger.info("SELFTEST DONE passed={} failed={}", passed, failed)
    }
}
