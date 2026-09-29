package com.github.kusa233.outbreak.dev

import com.github.kusa233.outbreak.registry.OutbreakBlocks
import com.github.kusa233.outbreak.registry.OutbreakItems
import com.github.kusa233.outbreak.registry.Registration
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
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
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
