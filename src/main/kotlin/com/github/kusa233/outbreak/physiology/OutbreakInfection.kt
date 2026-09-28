package com.github.kusa233.outbreak.physiology

import com.github.kusa233.outbreak.registry.OutbreakItems
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB

/**
 * Where infections come from.
 *
 * * Eating risky food - raw meat, rotten flesh, poisonous potatoes and raw willow bark soup -
 *   has a 30% chance of seeding a bacterial infection.
 * * Coming into contact with a bat has a 15% chance of seeding a viral infection.
 * * Both soup variants also deliver salicin, the drug that controls inflammation.
 */
object OutbreakInfection {

    /** Chance that a risky meal infects the player with bacteria. */
    const val BACTERIA_CHANCE = 0.30f

    /** Chance that touching a bat infects the player with a virus. */
    const val VIRUS_CHANCE = 0.15f

    /** How much pathogen a single successful roll adds. */
    private const val BACTERIA_SEED = 6f
    private const val VIRUS_SEED = 5f

    /** Salicin delivered by one serving of willow bark soup, raw or cooked. */
    private const val SALICIN_PER_SERVING = 1.1f

    /** How close a bat has to be to count as contact, in blocks. */
    private const val BAT_CONTACT_RANGE = 2.0

    /** After a contact roll the player is immune to further rolls for this long. */
    private const val BAT_CONTACT_COOLDOWN = 200

    /** Raw meat plus the explicitly named risky foods. */
    private val RISKY_FOODS: Set<Item> = setOf(
        Items.BEEF,
        Items.PORKCHOP,
        Items.CHICKEN,
        Items.MUTTON,
        Items.RABBIT,
        Items.COD,
        Items.SALMON,
        Items.TROPICAL_FISH,
        Items.ROTTEN_FLESH,
        Items.POISONOUS_POTATO,
    )

    /**
     * Called from `ItemMixin` whenever a living entity finishes using an item, which is the
     * moment the food is actually consumed.
     */
    @JvmStatic
    fun onItemConsumed(player: ServerPlayer, stack: ItemStack) {
        val data = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        var updated = data

        if (isRiskyFood(stack.item)) {
            if (player.level().random.nextFloat() < BACTERIA_CHANCE) {
                updated = OutbreakPhysiology.seed(updated, bacteria = BACTERIA_SEED)
            }
        }

        if (isWillowSoup(stack.item)) {
            updated = OutbreakPhysiology.dose(updated, SALICIN_PER_SERVING)
        }

        if (updated !== data) {
            player.setAttached(OutbreakAttachments.DATA, updated)
        }
    }

    private fun isRiskyFood(item: Item): Boolean =
        item in RISKY_FOODS || item === OutbreakItems.RAW_WILLOW_BARK_SOUP_BOTTLE ||
            item === OutbreakItems.RAW_WILLOW_BARK_SOUP_BOWL

    private fun isWillowSoup(item: Item): Boolean = item === OutbreakItems.WILLOW_BARK_SOUP_BOTTLE ||
        item === OutbreakItems.WILLOW_BARK_SOUP_BOWL ||
        item === OutbreakItems.RAW_WILLOW_BARK_SOUP_BOTTLE ||
        item === OutbreakItems.RAW_WILLOW_BARK_SOUP_BOWL

    /**
     * Checks for a bat standing next to the player and rolls for a viral infection once per
     * contact.
     */
    fun rollBatContact(player: ServerPlayer, data: OutbreakData, runtime: OutbreakRuntime): OutbreakData {
        if (runtime.batCooldown > 0) {
            runtime.batCooldown--
            return data
        }

        val box: AABB = player.boundingBox.inflate(BAT_CONTACT_RANGE)
        val bats = player.level().getEntities(EntityTypes.BAT, box) { true }
        if (bats.isEmpty()) {
            return data
        }

        runtime.batCooldown = BAT_CONTACT_COOLDOWN
        return if (player.level().random.nextFloat() < VIRUS_CHANCE) {
            OutbreakPhysiology.seed(data, virus = VIRUS_SEED)
        } else {
            data
        }
    }
}
