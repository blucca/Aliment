[English Version](CHANGE_LOG.md) | [中文版本](CHANGE_LOG_zh.md)

# Changelog

All notable changes to the **Aliment** mod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- Add JEI (Just Enough Items) recipe viewer support for grindstone grinding/crushing recipes.
- Add wheat brewing and beer distillation pipeline:
  - Wheat (`minecraft:wheat`) can now be added into the glass fermentation tank as a fermentable substrate alternative to sugar.
  - Inoculating the tank with brewer's yeast (`aliment:brewer_yeast`) initiates fermentation, producing fermented beer mash.
  - Distilling the fermented wheat mash over a campfire through a condenser pipe into an empty cauldron produces a Beer Cauldron (`aliment:beer_cauldron`).
  - Bottling the beer cauldron with a glass bottle yields Beer (`aliment:beer`, 5% ethanol), providing hydration and mild alcoholic effect when consumed.
  - Added full visual textures and models for beer item, beer cauldron, and fermentation tank liquid states (`wheat` and `beer`).
- Add multi-tier alcohol cauldron distillation pipeline and dynamic alcohol naming:
  - 7% wine and 40% wine can now be poured into a cauldron.
  - Heating an alcohol cauldron over a lit campfire with a condenser pipe above it distills the liquid into a collection cauldron:
    - 7% wine distills through the condenser pipe into 40% wine.
    - 40% wine distills through the condenser pipe into 75% alcohol.
    - 75% alcohol can be poured back into a cauldron and distilled into 98% alcohol.
  - Dynamic naming: Items with ethanol concentration >= 70% (75% and 98%) are named "Alcohol" instead of "Wine".
  - Added dedicated visual models for 7% wine cauldron (rich reddish liquid) and clear distilled alcohol cauldrons (40%, 75%, 98%).
- Add full Korean language localization support (`ko_kr.json`) covering all blocks, items, entities, creative tabs, advancements, tooltips, and JEI recipes.
- Add a stimulant sleep restriction: a player whose ephedrine concentration exceeds 0.5 can no longer fall asleep, and attempting to use a bed shows "You cannot sleep while stimulated".
  - The threshold lives in the Scala model (`EPHEDRINE_SLEEP_BLOCK_THRESHOLD`), is re-exported through `AlimentModelBridge`, and is mirrored in `AlimentData`.
  - The restriction is enforced by `PlayerSleepMixin`, which injects into `Player.startSleepInBed` and returns a vanilla `BedSleepingProblem` carrying the message, so the refusal reuses vanilla's own display path (the same one that reports an occupied bed) with no extra HUD code.
  - Creative players stay frozen and are unaffected; sleep becomes possible again as the ephedrine is metabolised (fully cleared within one in-game day).

### Changed
- Redraw potion and beer textures adhering to authentic vanilla Minecraft item style:
  - Coptis Potion (`aliment:coptis_potion`): vibrant golden-yellow herbal potion based on vanilla potion bottle and grayscale overlay.
  - Phellodendron Potion (`aliment:phellodendron_potion`): deep amber-gold herbal potion based on vanilla potion bottle and grayscale overlay.
  - Licorice Potion (`aliment:licorice_potion`): warm caramel-honey brown herbal potion based on vanilla potion bottle and grayscale overlay.
  - Beer (`aliment:beer`): vanilla bottle shape filled with golden-amber beer and frothy creamy foam at the neck.
- Add hand-swing use animations, client prediction, and insertion particles (`WHITE_SMOKE`, `COMPOSTER`, `FALLING_HONEY`, `SPLASH`) when placing items into the glass fermentation tank and scooping liquid from cauldrons.
- Consolidate all documentation into the `docs/` directory:
  - Moved `SPOILER.md`, `PHYSIOLOGY.md`, `TECHNICAL.md`, `CHANGE_LOG.md`, and `MODRINTH.md` (with their `_zh` counterparts) from the repository root into `docs/`.
  - `README.md` and `README_zh.md` stay at the root so GitHub renders them as the repository landing page.
  - Updated every cross-reference in `README.md`, `README_zh.md`, `AGENTS.md`, `docs/MODRINTH.md`, `docs/SPOILER.md`, and `docs/SPOILER_zh.md`; all documentation links were re-verified to resolve.
