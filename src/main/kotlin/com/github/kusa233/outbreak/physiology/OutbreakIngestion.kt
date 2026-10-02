package com.github.kusa233.outbreak.physiology

import com.github.kusa233.outbreak.registry.OutbreakItems
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Everything that happens when a player swallows something.
 *
 * One hook covers all of it, because vanilla only gives us one moment: `Item.finishUsingItem`
 * (see `ItemMixin`). From here the effects fan out:
 *
 * * risky food rolls for a bacterial infection;
 * * drinks add water;
 * * salted food adds sodium and chloride (crude salt carries the other minerals too);
 * * willow bark soup adds salicin.
 */
object OutbreakIngestion {

    /** Water added by any drinkable: a water bottle, a potion, stew, or soup. */
    val WATER_PER_DRINK = OutbreakData.WATER_PER_DRINK

    /**
     * Salt from one serving made with crude rock salt, in mmol/L of serum. The impurities that rock
     * salt carries come along.
     *
     * These numbers are chosen against the reference range: sodium runs 135..145 mmol/L, so one
     * serving of 3.0 moves a healthy player from 140 to 143 - still inside - and it takes **two**
     * servings to go past 145. That is the whole point of the scale: "drink two cups of salt water
     * and you are hypernatraemic" is a sentence that means something.
     */
    private const val CRUDE_SODIUM = 3.0f
    private const val CRUDE_CHLORIDE = 3.0f
    private const val CRUDE_MAGNESIUM = 0.04f
    private const val CRUDE_CALCIUM = 0.07f

    /** Refined salt is almost pure sodium chloride. 140 -> 143.5, two of them -> 147. */
    private const val REFINED_SODIUM = 3.5f
    private const val REFINED_CHLORIDE = 3.5f

    /**
     * A bottle of sea water is about 3.5% salt, so it is a sodium load in its own right, and it
     * arrives with the magnesium and calcium sea water actually contains. Two of them also tip
     * sodium over the reference range; one does not.
     */
    private const val SEA_WATER_SODIUM = 3.0f
    private const val SEA_WATER_CHLORIDE = 3.0f
    private const val SEA_WATER_MAGNESIUM = 0.05f
    private const val SEA_WATER_CALCIUM = 0.05f

    /**
     * Iodine from kelp, in umol/L. Dried kelp is the concentrated form.
     *
     * The body loses 0.15 umol/L a day on its own - the whole store is gone in three days - so a
     * regular diet of kelp is what keeps a player out of hypothyroidism: one a day is not quite
     * enough, two a day is comfortable, and dried kelp is worth two wet ones.
     */
    private const val KELP_IODINE = 0.10f
    private const val DRIED_KELP_IODINE = 0.20f

    /** What one mandrake fruit carries, in dose units; the seeds are the same plant, watered down. */
    private const val FRUIT_SCOPOLAMINE = 1.0f
    private const val FRUIT_ATROPINE = 0.1f
    private const val SEED_SCOPOLAMINE = 0.75f
    private const val SEED_ATROPINE = 0.1f

    /** One raw gymnopilus: a dose of the prodrug and a dose of what it becomes. */
    private const val MUSHROOM_PSILOCYBIN = 1.3f
    private const val MUSHROOM_PSILOCIN = 1.3f

    /** How much pathogen a single successful roll adds. */
    private const val BACTERIA_SEED = 6f

    // ---------------------------------------------------------------- foul water

    /** 30 seconds, shared by every foul-water effect. */
    private const val FOUL_EFFECT_TICKS = 600

    private const val FOUL_BACTERIA_CHANCE = 0.30f

    private const val FOUL_NAUSEA_CHANCE = 0.35f

    private const val FOUL_POISON_CHANCE = 0.05f

    /** Salicin delivered by one serving of willow bark soup, raw or cooked. */
    private const val SALICIN_PER_SERVING = 1.1f

    /** Dexamethasone delivered by one injection. */
    const val DEXAMETHASONE_PER_INJECTION = 1.2f

    /**
     * Called from `ItemMixin` whenever a living entity finishes using an item, which is the
     * moment the food or drink is actually consumed.
     *
     * A creative player's body is frozen (`OutbreakSymptoms.isFrozen`), so nothing eaten or drunk
     * reaches it - otherwise the state would keep moving through the kitchen door while the model
     * stood still. The vanilla side of eating, hunger and saturation, is untouched either way.
     */
    @JvmStatic
    fun onItemConsumed(player: ServerPlayer, stack: ItemStack) {
        if (OutbreakSymptoms.isFrozen(player)) {
            return
        }
        val before = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        var data = before

        data = OutbreakInfection.rollRiskyFood(player, data, stack)

        if (isDrink(stack.item)) {
            data = OutbreakPhysiology.drink(data, WATER_PER_DRINK)
        }

        when (stack.item) {
            OutbreakItems.CRUDE_SALT_WATER,
            OutbreakItems.CRUDE_SALT_MUSHROOM_STEW,
            OutbreakItems.CRUDE_SALT_WILLOW_BARK_SOUP,
            OutbreakItems.CRUDE_SALT_RAW_WILLOW_BARK_SOUP,
            OutbreakItems.CRUDE_SALT_SWAMP_WATER,
            OutbreakItems.CRUDE_SALT_SEA_WATER,
            -> data = OutbreakPhysiology.salt(data, CRUDE_SODIUM, CRUDE_CHLORIDE, CRUDE_MAGNESIUM, CRUDE_CALCIUM)

            OutbreakItems.SALT_WATER,
            OutbreakItems.SALT_MUSHROOM_STEW,
            OutbreakItems.SALT_WILLOW_BARK_SOUP,
            OutbreakItems.SALT_RAW_WILLOW_BARK_SOUP,
            OutbreakItems.SALT_SWAMP_WATER,
            OutbreakItems.SALT_SEA_WATER,
            -> data = OutbreakPhysiology.salt(data, REFINED_SODIUM, REFINED_CHLORIDE)

            // Plain sea water carries no salt *serving*, only the salt the sea already has.
            OutbreakItems.SEA_WATER_BOTTLE,
            -> data = OutbreakPhysiology.salt(
                data,
                SEA_WATER_SODIUM,
                SEA_WATER_CHLORIDE,
                SEA_WATER_MAGNESIUM,
                SEA_WATER_CALCIUM,
            )

            else -> Unit
        }

        if (isWillowSoup(stack.item)) {
            data = OutbreakPhysiology.dose(data, SALICIN_PER_SERVING)
        }

        // Kelp is the only dietary source of iodine, so it is the only way to stop the slow drain.
        when (stack.item) {
            Items.KELP -> data = OutbreakPhysiology.iodine(data, KELP_IODINE)
            Items.DRIED_KELP -> data = OutbreakPhysiology.iodine(data, DRIED_KELP_IODINE)

            // The mandrake: scopolamine for the delirium, atropine for the dry mouth and the fever
            // that comes with it. A fruit is a full dose of the first, the seeds three quarters.
            OutbreakItems.MANDRAKE_FRUIT ->
                data = OutbreakPhysiology.anticholinergic(data, FRUIT_SCOPOLAMINE, FRUIT_ATROPINE)
            OutbreakItems.MANDRAKE_SEEDS ->
                data = OutbreakPhysiology.anticholinergic(data, SEED_SCOPOLAMINE, SEED_ATROPINE)

            // The gymnopilus. Raw it hands over both compounds at once; cooked it hands over nothing,
            // because the heat that makes it food is what destroys them.
            OutbreakItems.GYMNOPILUS ->
                data = OutbreakPhysiology.mushroom(data, MUSHROOM_PSILOCYBIN, MUSHROOM_PSILOCIN)
            else -> Unit
        }

        if (data !== before) {
            player.setAttached(OutbreakAttachments.DATA, data)
        }

        if (isFoulWater(stack.item)) {
            applyFoulWater(player)
        }
    }

    /** Anything the player drinks, which counts towards the water index. */
    fun isDrink(item: Item): Boolean = item === Items.POTION ||
        item === Items.MUSHROOM_STEW ||
        item === OutbreakItems.RAW_WILLOW_BARK_SOUP_BOTTLE ||
        item === OutbreakItems.RAW_WILLOW_BARK_SOUP_BOWL ||
        item === OutbreakItems.WILLOW_BARK_SOUP_BOTTLE ||
        item === OutbreakItems.WILLOW_BARK_SOUP_BOWL ||
        item === OutbreakItems.CRUDE_SALT_WATER ||
        item === OutbreakItems.SALT_WATER ||
        item === OutbreakItems.SWAMP_WATER_BOTTLE ||
        item === OutbreakItems.SEA_WATER_BOTTLE ||
        item === OutbreakItems.CRUDE_SALT_SWAMP_WATER ||
        item === OutbreakItems.SALT_SWAMP_WATER ||
        item === OutbreakItems.CRUDE_SALT_SEA_WATER ||
        item === OutbreakItems.SALT_SEA_WATER ||
        item === OutbreakItems.CRUDE_SALT_MUSHROOM_STEW ||
        item === OutbreakItems.SALT_MUSHROOM_STEW ||
        item === OutbreakItems.CRUDE_SALT_WILLOW_BARK_SOUP ||
        item === OutbreakItems.SALT_WILLOW_BARK_SOUP ||
        item === OutbreakItems.CRUDE_SALT_RAW_WILLOW_BARK_SOUP ||
        item === OutbreakItems.SALT_RAW_WILLOW_BARK_SOUP

    /**
     * Untreated swamp water, and its salted versions, carry the risks below.
     *
     * **Sea water is deliberately not on this list.** It is not contaminated, it is *hypertonic*:
     * drinking it causes no immediate effect at all, and everything it does to the player arrives
     * later and through the sodium it carries (see [SEA_WATER_SODIUM]). A bottle of it is a big
     * enough salt load to push sodium past the safe band on its own, and hypernatraemia is what
     * makes the player thirsty and eventually weak.
     */
    fun isFoulWater(item: Item): Boolean = item === OutbreakItems.SWAMP_WATER_BOTTLE ||
        item === OutbreakItems.CRUDE_SALT_SWAMP_WATER ||
        item === OutbreakItems.SALT_SWAMP_WATER

    /**
     * The risks of drinking water you should not have: swamp water is full of bacteria and settles
     * badly on the stomach.
     *
     * * 30% bacterial infection
     * * 35% nausea (`MobEffects.NAUSEA`)
     * * 5% poisoning (`MobEffects.POISON`)
     *
     * Every one of them lasts 30 seconds. Note that there is no equivalent for sea water - see
     * [isFoulWater].
     */
    private fun applyFoulWater(player: ServerPlayer) {
        val random = player.level().random

        if (random.nextFloat() < FOUL_BACTERIA_CHANCE) {
            val data = player.getAttachedOrCreate(OutbreakAttachments.DATA)
            player.setAttached(OutbreakAttachments.DATA, OutbreakPhysiology.seed(data, bacteria = BACTERIA_SEED))
        }
        if (random.nextFloat() < FOUL_NAUSEA_CHANCE) {
            player.addEffect(MobEffectInstance(MobEffects.NAUSEA, FOUL_EFFECT_TICKS, 0))
        }
        if (random.nextFloat() < FOUL_POISON_CHANCE) {
            player.addEffect(MobEffectInstance(MobEffects.POISON, FOUL_EFFECT_TICKS, 0))
        }
    }

    private fun isWillowSoup(item: Item): Boolean =
        item === OutbreakItems.WILLOW_BARK_SOUP_BOTTLE ||
            item === OutbreakItems.WILLOW_BARK_SOUP_BOWL ||
            item === OutbreakItems.RAW_WILLOW_BARK_SOUP_BOTTLE ||
            item === OutbreakItems.RAW_WILLOW_BARK_SOUP_BOWL ||
            item === OutbreakItems.CRUDE_SALT_WILLOW_BARK_SOUP ||
            item === OutbreakItems.SALT_WILLOW_BARK_SOUP ||
            item === OutbreakItems.CRUDE_SALT_RAW_WILLOW_BARK_SOUP ||
            item === OutbreakItems.SALT_RAW_WILLOW_BARK_SOUP

    /** Applies a dexamethasone injection, unless the body is frozen (see [onItemConsumed]). */
    @JvmStatic
    fun injectDexamethasone(player: ServerPlayer) {
        if (OutbreakSymptoms.isFrozen(player)) {
            return
        }
        val data = player.getAttachedOrCreate(OutbreakAttachments.DATA)
        player.setAttached(OutbreakAttachments.DATA, OutbreakPhysiology.inject(data, DEXAMETHASONE_PER_INJECTION))
    }
}
