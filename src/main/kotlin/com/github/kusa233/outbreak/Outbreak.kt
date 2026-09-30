package com.github.kusa233.outbreak

import com.github.kusa233.outbreak.command.OutbreakCommand
import com.github.kusa233.outbreak.event.OutbreakInteractions
import com.github.kusa233.outbreak.physiology.OutbreakAttachments
import com.github.kusa233.outbreak.physiology.OutbreakSymptoms
import com.github.kusa233.outbreak.registry.OutbreakBlockEntities
import com.github.kusa233.outbreak.registry.OutbreakBlocks
import com.github.kusa233.outbreak.registry.OutbreakCreativeTabs
import com.github.kusa233.outbreak.registry.OutbreakEntities
import com.github.kusa233.outbreak.registry.OutbreakItems
import com.github.kusa233.outbreak.registry.OutbreakWorldGen
import com.github.kusa233.outbreak.world.OutbreakLoot
import com.github.kusa233.outbreak.world.tree.OutbreakTreeDecorators
import com.github.kusa233.outbreak.world.tree.OutbreakTrunkPlacers
import net.fabricmc.api.ModInitializer
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class Outbreak : ModInitializer {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger("Outbreak")
        const val MOD_ID = "outbreak"
    }

    override fun onInitialize() {
        LOGGER.info("Init outbreak")

        // Order matters: the decorator and trunk placer types have to exist before worldgen JSON
        // is decoded, the block entity types need the blocks, and the creative tabs need
        // everything else.
        OutbreakTreeDecorators.initialize()
        OutbreakTrunkPlacers.initialize()
        OutbreakBlocks.initialize()
        OutbreakEntities.initialize()
        OutbreakItems.initialize()
        OutbreakBlockEntities.initialize()
        OutbreakCreativeTabs.initialize()
        OutbreakWorldGen.initialize()
        OutbreakInteractions.initialize()

        // Loot goes last of the world-facing hooks: it edits tables the vanilla datapack provides, so
        // the items it hands out have to exist by the time a chest is first opened.
        OutbreakLoot.initialize()

        // The physiology system: attachments first, then the tick handler and the debug command.
        OutbreakAttachments.initialize()
        OutbreakSymptoms.initialize()
        OutbreakCommand.initialize()

        LOGGER.info("Outbreak content registered")
    }
}
