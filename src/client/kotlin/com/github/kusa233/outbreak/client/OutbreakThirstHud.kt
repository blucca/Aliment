package com.github.kusa233.outbreak.client

import com.github.kusa233.outbreak.physiology.OutbreakAttachments
import com.github.kusa233.outbreak.physiology.OutbreakClientState
import com.github.kusa233.outbreak.physiology.OutbreakData
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.ai.attributes.Attributes
import kotlin.math.ceil

/**
 * Draws the thirst bar: ten slim droplets, on the left, directly above the health bar.
 *
 * The bar reads the water index straight off the synced [OutbreakClientState], and the cell count
 * is simply `water / 10` capped at ten - so the normal band of 30..100 fills three to ten cells,
 * and anything above 100 shows as a full bar.
 *
 * Called from `HudMixin`; lives in Kotlin because the mixin only needs one line to reach it.
 */
object OutbreakThirstHud {

    private val THIRST_EMPTY: Identifier = Identifier.fromNamespaceAndPath("outbreak", "hud/thirst_empty")
    private val THIRST_HALF: Identifier = Identifier.fromNamespaceAndPath("outbreak", "hud/thirst_half")
    private val THIRST_FULL: Identifier = Identifier.fromNamespaceAndPath("outbreak", "hud/thirst_full")

    private const val CELL = 9
    private const val SPACING = 8

    /** From the model, which owns the number. */
    private val CELLS = OutbreakData.THIRST_CELLS

    /**
     * Draws the bar for the local player, stacked above vanilla's left-hand bars.
     *
     * Health sits at `guiHeight - 39` and armour, when present, one row above it - so the thirst
     * row goes above whichever of the two is currently topmost, never on top of either.
     */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor) {
        val player = Minecraft.getInstance().player ?: return
        val water = player.getAttachedOrElse(OutbreakAttachments.CLIENT, OutbreakClientState.INACTIVE).water
        val cells = (water / 10).coerceIn(0, CELLS)
        // Half a cell is shown while the last ten points are draining.
        val half = cells < CELLS && water % 10 >= 5

        val xLeft = graphics.guiWidth() / 2 - 91
        val yLineBase = graphics.guiHeight() - 39

        // Mirror vanilla's own row arithmetic so the bar follows multi-row health setups too.
        val maxHealth = maxOf(player.getAttributeValue(Attributes.MAX_HEALTH), player.health.toDouble()).toFloat()
        val absorption = ceil(player.absorptionAmount.toDouble()).toInt()
        val healthRows = ceil((maxHealth + absorption) / 2.0F / 10.0F).toInt()
        val healthRowHeight = maxOf(10 - (healthRows - 2), 3)
        val yArmor = yLineBase - (healthRows - 1) * healthRowHeight - 10
        val y = if (player.armorValue > 0) yArmor - 10 else yLineBase - 10

        for (i in 0 until CELLS) {
            val x = xLeft + i * SPACING
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, THIRST_EMPTY, x, y, CELL, CELL)
            if (i < cells) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, THIRST_FULL, x, y, CELL, CELL)
            } else if (i == cells && half) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, THIRST_HALF, x, y, CELL, CELL)
            }
        }
    }
}
