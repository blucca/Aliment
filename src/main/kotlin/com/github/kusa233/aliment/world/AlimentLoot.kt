package com.github.kusa233.aliment.world

import com.github.kusa233.aliment.registry.AlimentItems
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders

/**
 * The aliment items that turn up in already-generated chests.
 *
 * These are treatments and diagnostics, and all of them are things an abandoned village would
 * plausibly still have on a shelf. Dexamethasone is the strongest drug in the mod and cannot be
 * crafted at all, so it stays rare; willow bark soup is the entry-level treatment, so a chest hands
 * it out about a third of the time - often enough that a player who has not found a willow tree yet
 * still gets to try the system.
 *
 * The glucose chain is the one part of the mod a player cannot discover by experiment: the meter is
 * useless without a strip, and a strip is useless without a lancet, so the three of them are found
 * together and often. Insulin is the opposite - the only drug here that makes a body worse on
 * purpose - so it is uncommon, at the same order of rarity as dexamethasone.
 *
 * Adding to the vanilla tables rather than replacing them keeps every other village chest item where
 * it was, and works for any mod's world too, because the tables are the vanilla ones either way.
 */
object AlimentLoot {

    /** Every village chest shares this path prefix; there are fourteen of them. */
    private const val VILLAGE_PREFIX = "chests/village/"

    /** The pillager outpost's chest. */
    private const val OUTPOST = "chests/pillager_outpost"

    /** The namespace every chest we care about lives in. */
    private const val VANILLA = "minecraft"

    /**
     * What a village or outpost chest may contain, and how likely each one is to be there.
     *
     * The chances are per chest rather than per item rolled: a chest holds at most one injection and
     * at most one bowl, and the two rolls are independent. A stack size above one is the one
     * exception - a strip is no use on its own, so a chest that has any has several.
     */
    internal val ADDITIONS: List<ChestAddition> = listOf(
        ChestAddition(AlimentItems.DEXAMETHASONE_INJECTION, 0.03f),
        ChestAddition(AlimentItems.WILLOW_BARK_SOUP_BOWL, 0.35f),
        ChestAddition(AlimentItems.WINE, 0.35f),
        ChestAddition(AlimentItems.BREWER_YEAST, 0.40f),
        ChestAddition(AlimentItems.INSULIN_INJECTION, 0.10f),
        ChestAddition(AlimentItems.GLUCOSE_METER, 0.35f),
        ChestAddition(AlimentItems.GLUCOSE_TEST_STRIP, 0.35f, 5, 9),
        ChestAddition(AlimentItems.MICRONEEDLE, 0.35f),
    )

    /** One item this mod adds to a chest, with the chance and the count a given chest holds. */
    internal data class ChestAddition(
        val item: Item,
        val chance: Float,
        val minCount: Int = 1,
        val maxCount: Int = 1,
    )

    /** True for the chests this mod adds to: every village chest, and the pillager outpost's. */
    internal fun targets(lootTable: Identifier): Boolean {
        if (lootTable.namespace != VANILLA) {
            return false
        }
        val path = lootTable.path
        return path.startsWith(VILLAGE_PREFIX) || path == OUTPOST
    }

    /**
     * One pool that drops [addition] with its own chance.
     *
     * The condition goes on the pool rather than on the entry because the pool has a single entry and
     * a single roll, which makes the two identical in play - and a pool-level chance is what a
     * datapack would write for "this chest has a 3% chance of one of these". The count goes the other
     * way, on the entry, because that is the only place a loot function can be attached.
     */
    internal fun poolFor(addition: ChestAddition): LootPool.Builder {
        var entry = LootItem.lootTableItem(addition.item)
        if (addition.maxCount > 1) {
            entry = entry.apply(
                SetItemCountFunction.setCount(ContextIntProviders.between(addition.minCount, addition.maxCount)),
            )
        }
        return LootPool.lootPool()
            .add(entry)
            .`when`(LootItemRandomChanceCondition.randomChance(addition.chance))
    }

    fun initialize() {
        LootTableEvents.MODIFY.register { key, table, source, _ ->
            // A datapack that rewrites one of these tables is not ours to edit; the vanilla ones are.
            if (source.isBuiltin && targets(key.identifier())) {
                for (addition in ADDITIONS) {
                    table.withPool(poolFor(addition))
                }
            }
        }
    }
}
