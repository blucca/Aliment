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

### Changed
- Redraw potion and beer textures adhering to authentic vanilla Minecraft item style:
  - Coptis Potion (`aliment:coptis_potion`): vibrant golden-yellow herbal potion based on vanilla potion bottle and grayscale overlay.
  - Phellodendron Potion (`aliment:phellodendron_potion`): deep amber-gold herbal potion based on vanilla potion bottle and grayscale overlay.
  - Licorice Potion (`aliment:licorice_potion`): warm caramel-honey brown herbal potion based on vanilla potion bottle and grayscale overlay.
  - Beer (`aliment:beer`): vanilla bottle shape filled with golden-amber beer and frothy creamy foam at the neck.
- Add hand-swing use animations, client prediction, and insertion particles (`WHITE_SMOKE`, `COMPOSTER`, `FALLING_HONEY`, `SPLASH`) when placing items into the glass fermentation tank and scooping liquid from cauldrons.
