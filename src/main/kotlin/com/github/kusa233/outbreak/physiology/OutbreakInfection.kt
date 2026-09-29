package com.github.kusa233.outbreak.physiology

import com.github.kusa233.outbreak.registry.OutbreakItems
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB

/**
 * Which pathogens a player can pick up, and from what.
 *
 * * Eating risky food - raw meat, rotten flesh, poisonous potatoes and raw willow bark soup -
 *   has a 30% chance of seeding a bacterial infection.
 * * Coming into contact with a bat has a 15% chance of seeding a viral infection.
 */
object OutbreakInfection {

    /** Chance that a risky meal infects the player with bacteria. */
    const val BACTERIA_CHANCE = 0.30f

    /** Chance that touching a bat infects the player with a virus. */
    const val VIRUS_CHANCE = 0.15f

    /** How much pathogen a single successful roll adds. */
    private const val BACTERIA_SEED = 6f
    private const val VIRUS_SEED = 5f

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
     * Rolls for a bacterial infection from a meal, and returns the updated data.
     *
     * Called for anything the player swallows, so it has to be cheap when the item is not risky.
     */
    fun rollRiskyFood(player: ServerPlayer, data: OutbreakData, stack: ItemStack): OutbreakData {
        if (!isRiskyFood(stack.item)) {
            return data
        }
        return if (player.level().random.nextFloat() < BACTERIA_CHANCE) {
            OutbreakPhysiology.seed(data, bacteria = BACTERIA_SEED)
        } else {
            data
        }
    }

    fun isRiskyFood(item: Item): Boolean =
        item in RISKY_FOODS || item === OutbreakItems.RAW_WILLOW_BARK_SOUP_BOTTLE ||
            item === OutbreakItems.RAW_WILLOW_BARK_SOUP_BOWL ||
            item === OutbreakItems.CRUDE_SALT_RAW_WILLOW_BARK_SOUP ||
            item === OutbreakItems.SALT_RAW_WILLOW_BARK_SOUP

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
