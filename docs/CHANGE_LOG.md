[English Version](CHANGE_LOG.md) | [中文版本](CHANGE_LOG_zh.md)

# Changelog

All notable changes to the **Aliment** mod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- Add the **grapefruit tree** and the fruit that hangs under it:
  - `aliment:grapefruit_log`, `aliment:grapefruit_leaves` and `aliment:grapefruit_sapling` are a small citrus of its own. It generates in the jungles and the savannas at one attempt per six chunks, and a sapling grows the same tree.
  - `aliment:grapefruit` is the fruit, and it hangs: vanilla's `minecraft:attached_to_leaves` decorator puts it in the block directly under a canopy leaf, and it can only exist while there is still a leaf or a log above it, so breaking the canopy drops it.
  - One grapefruit crafts into **eight** `aliment:grapefruit_slice`, which is the part a player eats: **2 hunger**, **3 saturation points**, **5 water**, **1 naringin** and **10 vitamin C** - the vitamin C being what a carrot is worth, because a slice is a plant food and a citrus besides. It is the only food in the mod that also drinks.
- Add the **grapefruit wood set**, a full second wood alongside the willow's: `grapefruit_wood`, `stripped_grapefruit_log`, `stripped_grapefruit_wood`, planks, stairs, slab, fence, fence gate, door, trapdoor, pressure plate, button, shelf, standing and hanging signs, a potted sapling, and its own **boat and boat with chest** - each with its own entity type, model layers and textures, as every wood's boat now needs. Its own `aliment:grapefruit_logs` tag is what its recipes take, so a plank recipe cannot be satisfied with willow logs, and it joins the vanilla `planks`, `logs`, `wooden_*`, `signs` and `fence_gates` tags, which is what makes the whole set axe-mineable and craftable the way any other wood is.
- Add **naringin** and the **CYP3A4** index:
  - Naringin is what grapefruit carries, 0..**10** - ten slices - and it is cleared over one game day. It has no effect of its own; everything it does, it does through the enzyme.
  - CYP3A4 runs 0..**100** and sits at **85** in a body that has eaten no grapefruit. Naringin is a step function on it rather than a curve, so the index only ever holds one of five values: **85** at 2 or below, **60** past 2, **45** past 4, **25** past 7, and **10** at 8.5 or above.
- Milk now counts as a drink, so drinking a bucket of it adds the standard **15 water**. In 26.3 vanilla's milk is a plain `Item` carrying a `CONSUMABLE` component rather than the `MilkBucketItem` of older versions, which is what lets the mod's `Item.finishUsingItem` hook see it at all.

### Changed
- **Berberine is now cleared by CYP3A4**, where before it was cleared at a fixed rate. The rate is scaled by the enzyme index against the 85 baseline, so at 85 the metabolism is exactly the one the model always had and at 10 the same dose takes eight and a half times as long. It is the first interaction in the mod where one thing a player eats changes how long another lasts: a single coptis herb clears in about 9,400 ticks on its own and about 17,800 with a body full of grapefruit.

### Fixed
- The **potted willow sapling** now renders as a potted willow. Its blockstate pointed at `minecraft:block/potted_oak_sapling`, so it had always drawn itself as a potted *oak* while the correct `aliment:block/potted_willow_sapling` model sat unused beside it. Found while adding the grapefruit's, which had been mirrored from the same file.

