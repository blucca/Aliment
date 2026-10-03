[English Version](SPOILER.md) | [中文版本](SPOILER_zh.md)

# Spoilers Guide

**Aliment** is a Minecraft mod centered around physiology, disease, pharmacology, and contagion.

This document contains **complete gameplay and mechanics spoilers**: hidden numerical thresholds, crafting progression, post-processing screen effects, and diagnostic commands.
If you prefer discovering mechanics organically—such as discovering why drinking sea water produces no immediate ill effect but causes unbearable thirst later, or why raw willow bark soup makes you ill—do not read further.

Related Technical Documents:
- Mathematical and Biological Model: [`PHYSIOLOGY.md`](PHYSIOLOGY.md) ([中文版](PHYSIOLOGY_zh.md))
- Architecture and Implementation: [`TECHNICAL.md`](TECHNICAL.md) ([中文版](TECHNICAL_zh.md))
- Asset Generators and Tests: [`tools/README.md`](tools/README.md)

---

## 30-Second Summary

You do **not** take immediate hitpoint damage from eating spoiled food. Infection follows a continuous curve: pathogens reproduce logistically over time, while your immune system operates with peak clearance efficiency **only in the optimal middle range**. Too little inflammation leaves the immune system inactive; excessive inflammation triggers a destructive cytokine storm that attacks your own body.
Your goal is to manage inflammation (via salicin from willow bark soup and dexamethasone injections), maintain hydration, electrolytes, iodine, vitamin C, and core body temperature in their homeostatic windows, and avoid drinking untreated swamp water or handling wild bats barehanded.

---

## 1. Botanical World Generation & Ecological Distribution Overview

The mod introduces 8 distinct plants and fungi, all dynamically injected into the Overworld vegetal decoration step (`GenerationStep.Decoration.VEGETAL_DECORATION`) with tailored biome filters, substrate surface predicates, and harvesting mechanics:

| Plant / Fungus | Target Biomes | Frequency / Density | Substrate & Surface Predicates | Initial Generated State | Harvesting & Active Bioactive Yield |
| --- | --- | --- | --- | --- | --- |
| **Weeping Willow**<br>`aliment:willow` | All river biomes<br>(`#minecraft:is_river`: River, Frozen River, etc.) | Rarity filter 1/2<br>(50% chance per chunk) | Riverbank dirt/grass; water depth must be 0 (`max_water_depth: 0`, never submerged); heightmap `OCEAN_FLOOR` | Full Weeping / Tall Willow<br>(cascading vines) | Strip logs with axe for Willow Bark (raw Salicin +1.1) |
| **Mandrake**<br>`aliment:mandrake` | Plains, Sunflower Plains, Swamp, Mangrove Swamp | 1 per chunk<br>(`count: 1`) | Position must be air (`#minecraft:air`); block directly beneath must be **Grass Block** (`grass_block`); heightmap `WORLD_SURFACE_WG` | Mature stage (`age: 3`)<br>(blooming with hanging fruit) | Break to harvest 1–2 fruits (Scopolamine +1.0, Atropine +0.1) |
| **Gymnopilus Mushroom**<br>`aliment:gymnopilus` | Dark Forest, Swamp, Mangrove Swamp, Taiga, Old Growth Pine Taiga, Old Growth Spruce Taiga | 3 per chunk<br>(`count: 3`) | Block directly beneath must be Overworld substrate (`#minecraft:substrate_overworld`, dirt or wood logs); heightmap `WORLD_SURFACE_WG`; **no darkness restriction** | Mature solitary mushroom | Break by hand; eat raw for Psilocybin & Psilocin (+1.3 each); cooked yields zero psychedelics |
| **Ephedra**<br>`aliment:ephedra` | Desert, Badlands, Eroded Badlands, Wooded Badlands, Windswept Hills, Savanna, Savanna Plateau, Windswept Savanna | Rarity filter 1/8<br>(12.5% chance per chunk) | Supports 4 substrate blocks: Sand, Red Sand, Terracotta, Grass Block; heightmap `WORLD_SURFACE_WG` | Mature stage (`age: 3`) | **Right-click harvest** 1–2 twigs, resetting plant to stage 1 (Ephedrine +0.5) |
| **Coptis**<br>`aliment:coptis` | All non-cold Overworld biomes<br>(`baseTemperature >= 0.2` & `!#minecraft:spawns_cold_variant_frogs`) | Rarity filter 1/12<br>(~8.3% chance per chunk) | Block directly beneath must be **Grass Block** (`grass_block`); heightmap `WORLD_SURFACE_WG` | Mature stage (`age: 3`) | Right-click harvest or break for raw Coptis (Berberine +1.1) |
| **Phellodendron**<br>`aliment:phellodendron` | All non-cold Overworld biomes<br>(`baseTemperature >= 0.2` & `!#minecraft:spawns_cold_variant_frogs`) | Rarity filter 1/12<br>(~8.3% chance per chunk) | Block directly beneath must be **Grass Block** (`grass_block`); heightmap `WORLD_SURFACE_WG` | Mature shrub (`age: 3`) | Right-click harvest or break for Phellodendron Bark (Berberine +0.6) |
| **Licorice**<br>`aliment:licorice` | All non-cold Overworld biomes<br>(`baseTemperature >= 0.2` & `!#minecraft:spawns_cold_variant_frogs`) | Rarity filter 1/12<br>(~8.3% chance per chunk) | Block directly beneath must be **Grass Block** (`grass_block`); heightmap `WORLD_SURFACE_WG` | Mature stage (`age: 3`) | Right-click harvest or break for Licorice Root (Glycyrrhizin +1.1) |
| **Seaweed**<br>`aliment:seaweed` | All ocean biomes<br>(`#minecraft:is_ocean`: Warm, Lukewarm, Cold, Deep, Frozen Oceans, etc.) | 4 per chunk<br>(`count: 4`) | Must be fully submerged in water (`fluids: "minecraft:water"`); seabed must be **Sand or Suspicious Sand**; heightmap `OCEAN_FLOOR_WG` | Submerged mature stage (`age: 3, waterlogged: true`) | **Underwater right-click harvest** 1–2 seaweed, resetting to stage 1 (Iodine +0.20 µmol/L) |

---

## 2. Willow Ecology

### Generation Rules
* **Riparian Exclusivity**: Injected into `#minecraft:is_river` (River, Frozen River) biomes during the `VEGETAL_DECORATION` phase. Trees adhere to vanilla placement criteria (`surface_water_depth_filter`), generating only on riverbank dirt/grass blocks and **never underwater**.
* **Two Tree Morphologies**: Standard Weeping Willow (trunk height 5–7 blocks, canopy radius 3 blocks) and Tall Weeping Willow (trunk height 8–10 blocks). Saplings select between them with a **65% / 35%** probability distribution.
* **Leaning Growth Pattern**: Utilizes a custom `leaning_willow_trunk_placer`. Scans water presence within a 7-block radius, dynamically curving the trunk 1–4 blocks toward the densest body of water to mimic trees overhanging rivers; grows vertically if no water is detected.
* **Cascading Vines**: Hanging vines (`willow_vines` tips and `willow_vines_plant` stems) suspend 1–3 blocks below the foliage canopy. Vines grow naturally downward over time and can be accelerated with bone meal.
* **Saplings**: Plantable on dirt, grass blocks, farmland, coarse dirt, and mud. **Bone meal accelerates growth**. Willow leaves **never drop apples** (apple drop tables are stripped).

---

## 3. Willow Blocks and Items Roster

All items are indexed under the dedicated Creative Tab **"Aliment"** (`itemGroup.aliment.main`).

| Item / Block ID | English Name | Notes |
| --- | --- | --- |
| `aliment:willow_log` | Willow Log | Contains `axis` property; strip with an axe to obtain willow bark |
| `aliment:willow_wood` | Willow Wood | 6-sided barked wood block |
| `aliment:stripped_willow_log` | Stripped Willow Log | Result of stripping |
| `aliment:stripped_willow_wood` | Stripped Willow Wood | 6-sided stripped wood block |
| `aliment:willow_planks` | Willow Planks | Fundamental carpentry material crafted from logs |
| `aliment:willow_leaves` | Willow Leaves | Foliage dynamically tinted by local biome foliage color |
| `aliment:willow_sapling` | Willow Sapling | Grows into a willow tree |
| `aliment:potted_willow_sapling` | Potted Willow Sapling | Decorative potted plant |
| `aliment:willow_vines` | Willow Vines | Trailing vine tip hanging beneath canopies |
| `aliment:willow_vines_plant` | Willow Vines Plant | Intermediate vine stem block |
| `aliment:willow_stairs` / `_slab` | Willow Stairs / Slab | Architectural carpentry blocks |
| `aliment:willow_fence` / `_fence_gate` | Willow Fence / Gate | Standard enclosure carpentry |
| `aliment:willow_door` / `_trapdoor` | Willow Door / Trapdoor | Complete woodset doors |
| `aliment:willow_pressure_plate` / `_button` | Willow Pressure Plate / Button | Redstone triggers |
| `aliment:willow_shelf` | Willow Shelf | Display block backed by the vanilla `SHELF` block entity |
| `aliment:willow_sign` / `_wall_sign` | Willow Sign / Wall Sign | Standing and wall signage |
| `aliment:willow_hanging_sign` / `_wall_hanging_sign` | Willow Hanging Sign / Wall Hanging Sign | Suspended ceiling and wall signage |
| `aliment:willow_boat` | Willow Boat | Willow rowing vessel with custom entity rendering |
| `aliment:willow_chest_boat` | Willow Chest Boat | Boat with onboard storage container |
| `aliment:willow_bark` | Willow Bark | Dropped by stripping willow logs with an axe |
| `aliment:willow_bark_pieces` | Willow Bark Pieces | Obtained by grinding willow bark on a grindstone |
| `aliment:willow_soup_cauldron` | Willow Broth Cauldron | Cauldron holding medicinal willow broth (`level` and `cooked` states) |
| `aliment:raw_willow_bark_soup_bottle` | Raw Willow Broth Bottle | Bottled unboiled broth; carries bacterial infection risk |
| `aliment:raw_willow_bark_soup_bowl` | Raw Willow Broth Bowl | Bowled unboiled broth |
| `aliment:willow_bark_soup_bottle` | Willow Broth Bottle | Fully boiled broth bottle; safe salicin remedy |
| `aliment:willow_bark_soup_bowl` | Willow Broth Bowl | Fully boiled broth bowl; restores hunger and administers salicin |

---

## 3. Bark to Willow Broth Processing

```
Willow Log / Wood ──Right-click with Axe──> Stripped Log/Wood + Willow Bark ×1
Willow Bark ──Grindstone──> Willow Bark Pieces ×2
Willow Bark Pieces ──Right-click Water Cauldron──> Raw Willow Broth Cauldron
                                                         │
                                        Campfire or Soul Campfire underneath; heat for 60s
                                                         ▼
                                                    Boiled Willow Broth Cauldron (darker color)
                                                         │
                                  Glass Bottle Right-click ──> Broth Bottle (returns bottle)
                                  Bowl Right-click         ──> Broth Bowl (returns bowl)
```

* **Cauldron Mechanics**:
  - The water level (1–3) determines how many servings can be ladled out.
  - Ladling a portion reduces the liquid level by 1; emptying it returns an empty cauldron.
  - Breaking a cauldron holding broth drops only an empty cauldron.
  - Extinguishing or removing the campfire pauses brewing progress without resetting; reigniting resumes the 60-second brew cycle.
* **Raw Broth vs. Boiled Broth**:
  - Raw broth is cloudy, pale, and contains suspended raw fibers; boiled broth is dark, clear, and fragrant.
  - Raw broth carries dangerous environmental contaminants that were not boiled off.

| Broth Category | Nutrition | Saturation | Physiological and Pharmacological Effects |
| --- | --- | --- | --- |
| **Raw Willow Broth** (Bottle / Bowl / Salted) | 1 | 1 | **25% Nausea** (20s), **15% Hunger** (20s), **30% Bacterial Infection**, +1.1 Salicin, +15 Water |
| **Boiled Willow Broth** (Bottle / Bowl / Salted) | 1 | 2 | **Completely safe**, +1.1 Salicin (antipyretic & anti-inflammatory), +15 Water |

---

## 4. Salt Processing Chain

```
Rock Salt Ore (underground y=20–90; requires stone pickaxe; drops itself)
   └─Grindstone─> Crude Salt ×9 ──Grindstone──> Crude Salt Powder
                                                   └─Right-click Water Cauldron─> Brine Cauldron
                                                                                       │ Campfire underneath
                                                                                       │ Evaporates every 20s (3 stages)
                                                                                       ▼ Boiled dry
                                                                                   Salt Powder ×1 (cauldron empties)

Crude Salt / Salt Powder + Water / Mushroom Stew / Willow Broth / Raw Broth
   ──> Salted Water, Salted Stew, Salted Willow Broth, Salted Raw Broth
```

* **Stirring Rod** (crafted with two sticks vertically) right-clicks a brine cauldron to **instantly advance evaporation by one stage**, consuming 1 durability (16 uses total).
* Crude salt carries trace rock minerals: beyond sodium and chloride, it provides small amounts of **magnesium and calcium**; refined salt is nearly pure NaCl and pushes sodium higher.
* All salted beverages **replenish hydration (+15) and sodium**, with crude salt versions providing supplemental magnesium and calcium.

---

## 5. Grindstone Mechanics

| Input (1 Item) | Output |
| --- | --- |
| Willow Bark ×1 | Willow Bark Pieces ×2 |
| Rock Salt Ore ×1 | Crude Salt ×9 |
| Crude Salt ×1 | Crude Salt Powder ×1 |
| Ephedra Herb ×1 | Crushed Ephedra ×1 |
| Coptis ×1 | Crushed Coptis ×1 |
| Phellodendron ×1 | Crushed Phellodendron ×1 |
| Licorice ×1 | Crushed Licorice ×1 |
| Seaweed ×1 | Crushed Seaweed ×1 |

Items can be placed directly into the **vanilla grindstone GUI**, but **only one item at a time** (stacked items are rejected to prevent output loss).
For fast bulk processing, **sneak + right-click** the grindstone with an item: it processes an item directly from your hand without opening the interface.

---

## 6. Hydration and Thirst

* A **10-pip thirst bar** is displayed above your health indicators on the left side of the HUD (each pip represents 10 units of water).
* Optimal range is **30 – 100**, maximum ceiling is 200, and standard resting health sits at 80.
* Beverages grant **+15 hydration** (water, potions, stew, willow broths, salted drinks).
* **Depletes completely from full to empty (100 → 0) in 5 game days** under temperate, healthy conditions.
* **Fever accelerates water depletion**: 39.0 °C fever empties reserves in 3.5 game days; 40.0 °C hyperthermia empties reserves in 2.0 game days.
* Hydration above 100 triggers **overhydration**: weakness, reduced mining speed, accelerated food exhaustion, and electrolyte dilution; exceeding 150 adds **nausea**.

Water collection source determines beverage properties: rivers yield vanilla water bottles, **swamps** yield swamp water bottles, and **oceans** yield sea water bottles.

| Water Type | Consequence of Ingestion |
| --- | --- |
| Swamp Water Bottle (and salted variants) | 30% Bacterial Infection, 35% Nausea, 5% Poisoning (each for 30s) |
| **Sea Water Bottle** (and salted variants) | **No immediate negative effect**; it is purely **hypertonic**: one bottle pushes sodium past the clinical safe limit, causing severe hypernatremic thirst and rapid fluid loss later |

---

## 7. Electrolytes and Trace Elements

Aliment uses **clinical units**: electrolytes in **mmol/L**, and trace elements in **µmol/L**. Four bilateral thresholds define clinical states:

| Mineral | Baseline | Reference Range | Units |
| --- | --- | --- | --- |
| Sodium `sodium` | 140 | **135 – 145** | mmol/L |
| Potassium `potassium` | 4.2 | **3.5 – 5.0** | mmol/L |
| Magnesium `magnesium` | 0.85 | **0.70 – 1.00** | mmol/L |
| Chloride `chloride` | 101 | **96 – 106** | mmol/L |
| Calcium `calcium` | 2.35 | **2.10 – 2.60** | mmol/L |
| Iodine `iodine` | 0.50 | **0.40 – 0.80** | µmol/L |
| Vitamin C `vitamin_c` | 60.0 | **40.0 – 80.0** | µmol/L |

Serum electrolytes return to normal homeostatically. **Iodine and Vitamin C are exceptions**:

* **Iodine drains constantly without endogenous replenishment**: depletes from normal 0.50 to the floor (0.05) in **exactly 3 in-game days**. Replenish by consuming:
  * Vanilla Kelp: `+0.10 µmol/L`
  * Vanilla Dried Kelp: `+0.20 µmol/L`
  * Seaweed: `+0.20 µmol/L`
  * Cooked Seaweed: `+0.25 µmol/L`
  * Seaweed Iodized Salt: `+0.40 µmol/L` (also provides `+1.5 mmol/L` Na and Cl)
  Basal daily drain is 0.15 µmol/L; 1 dried kelp or raw seaweed daily balances metabolism. Severe deficiency (0.05) drops hypothalamic set point by 0.8 °C and causes severe hypothyroidism (slowness II, weakness II, fatigue).
* **Rapid water ingestion washes out sodium first**, followed by chloride; magnesium and calcium clear most slowly.
* **Salt Intake Realism**: Crude salt adds +3.0 mmol/L Na, and refined salt adds +3.5 mmol/L. Consuming two salted servings pushes sodium to 146–147 mmol/L, causing hypernatremic thirst.

| Mineral | Severe Deficit | Mild Deficit | Mild Excess | Severe Excess |
| --- | --- | --- | --- | --- |
| Sodium | Nausea + Slowness (Confusion) | Weakness | Hunger (Thirst) | Hunger + Weakness |
| Potassium | Weakness II + Mining Fatigue | Weakness | Weakness | Slowness II + Periodic Magic Damage (Arrhythmia) |
| Magnesium | Weakness + Slowness (Tremors) | Weakness | Slowness | Slowness + Weakness (Lethargy) |
| Chloride | Nausea (Metabolic Alkalosis) | Weakness | Hunger (Metabolic Acidosis) | Hunger + Nausea |
| Calcium | Slowness + Weakness (Tetany) | Weakness | Slowness | Slowness II + Weakness (Lethargy) |
| Iodine | Slowness II + Weakness II + Fatigue | Weakness + Slowness + Hunger | Hunger + Nausea | Adds Weakness |
| Vitamin C | Mining Fatigue + Weakness | Mining Fatigue | None (Safely Excreted) | None (Safely Excreted) |

---

## 8. Immune System Dynamics

Inflammation is calculated as a weighted composite of five mediators, centering at 25.0 in health:

| Mediator | Function | Inhibited By |
| --- | --- | --- |
| Histamine | Vasodilation, itching, swelling | Mildly by both |
| Prostaglandin | Pain, **fever** | **Salicin** |
| Leukotriene | Bronchial constriction, mucus | **Dexamethasone** |
| Cytokine | Systemic fever, cytokine storm driver | **Dexamethasone** (most potent) |
| Bradykinin | Pain, vascular permeability | Salicin |

* **Low Inflammation (< 12)**: Immune paralysis; infection grows unchecked, and opportunistic bacterial infection occurs spontaneously.
* **High Inflammation (> 75)**: Cytokine storm; immune clearance fails and host tissue is damaged.
* **Optimal Band (20–40)**: Peak clearance competence where infections are suppressed.

Overdosing on anti-inflammatories suppresses basal inflammation into immunosuppression, accelerating pathogen proliferation.

---

## 9. Infection Mechanisms

Three infection vectors exist:

| Vector | Pathogen | Chance | Inoculum Load |
| --- | --- | --- | --- |
| Raw meat (beef, pork, chicken, mutton, rabbit, cod, salmon, tropical fish), rotten flesh, poisonous potato | Bacteria | **30%** | +6 |
| Raw willow broth (including salted) | Bacteria | **30%** | +6 |
| Within 2 blocks of **any living mob** | Virus | **5% / second** | +5 |
| **Severe Immunosuppression** (inflammation ≤ 12) | Bacteria | **15% / second** | **+4 to +12 (random)** |

* Proximity contact evaluates against all living `Mob` entities (cows, wolves, villagers, bats). Standing near dense livestock quickly spreads viral infection.
* **Opportunistic Colonization**: Immunosuppression permits resident flora to colonize without external vectors.
* **Sepsis Magic Damage (Load $\ge 60.0$)**: Deals unblockable magic damage every 2 seconds:
  $$\text{Damage}(L) = 1.0 + \frac{L - 60.0}{40.0}\quad (L \ge 60.0)$$
  At load 100, this deals 2 damage (1 full heart) every 10 seconds.

---

## 10. Pharmacology

| Drug | Source | Dose | Elimination Window | Elimination Rate |
| --- | --- | --- | --- | --- |
| Salicin | Willow Broth (raw or boiled) | +1.1 | **3 game days** | $\frac{d[\text{Salicin}]}{dt} = - \frac{3.0}{72000} \approx -4.167 \times 10^{-5}\text{ / tick}$ |
| Dexamethasone | Injection syringe (right-click) | +1.2 | **2 game days** | $\frac{d[\text{Dex}]}{dt} = - \frac{2.0}{48000} \approx -4.167 \times 10^{-5}\text{ / tick}$ |

Salicin is a COX inhibitor acting as an antipyretic by blocking prostaglandin synthesis. Dexamethasone strongly halts cytokine transcription, aborting cytokine storms. **Neither directly kills pathogens.**

Remedies generate naturally in loot chests:

| Loot Chest Location | Dexamethasone Injection | Willow Broth Bowl |
| --- | --- | --- |
| Village Chests (all 14 types) | **3%** | **35%** |
| Pillager Outposts | **3%** | **35%** |

---

## 11. Thermoregulation

| Thermal State | Core Temperature |
| --- | --- |
| Normal Baseline | **37.0 °C** |
| Comfort Zone | 36.0 – 38.5 °C (no symptoms) |
| Fever / Hyperthermia | **≥ 38.5 °C** / **≥ 40.0 °C** |
| Mild / Severe Hypothermia | ≤ 36.0 °C / ≤ 35.0 °C |

Temperature is determined by infection pyrogenesis, injected pyrogens, thyroid activity, and environment:

| Environmental Exposure | Thermal Consequence |
| --- | --- |
| Temperate biomes, Desert | Minimal impact (deserts raise temp to ~37.4 °C) |
| Snowy Plains | Drops temp to ~36.6 °C (asymptomatic) |
| Submerged in water in cold biomes | Drops temp to ~35.1 °C (**mild hypothermia**) |
| Buried in powder snow while wet | Drops temp to ~32.8 °C (**severe hypothermia**) |
| On Fire | Rises to ~38.2 °C (sub-fever) |
| In Lava | Rises to ~39.4 °C (**fever**, triggering heat haze) |

38.0 °C is achievable without illness in hot biomes with thyroid activity; fever symptoms strictly begin at **38.5 °C**.

---

## 12. Post-Processing Screen Effects

Full-screen shader effects distort and tint **only the peripheral edges** (`smoothstep(0.45, 1.0, ...)`), leaving crosshairs and central view crystal clear:

| Effect | Trigger Condition | Visual Appearance |
| --- | --- | --- |
| Heat Haze (`aliment:heat_haze`) | Temp ≥ 38.5 °C | Peripheral reddish wavy shimmering |
| Heat Motion Blur (`aliment:heat_blur`) | Temp ≥ 40.0 °C | Peripheral haze with camera motion blur trails |
| Cold Shiver (`aliment:cold_shiver`) | Temp ≤ 36.0 °C | Slower, vertical shivering with bluish tint |

---

## 13. Camera Tremors

Evaluated every **15 seconds**; on success, shakes the camera for 16 ticks. Shivering requires an accompanying symptom icon:

| Cause | Probability |
| --- | --- |
| Active symptomatic infection | 20% (doubles during cytokine storm) |
| Temperature tier deviation | +15% per tier with increased roll amplitude |
| Low magnesium | +15% |
| Severe low calcium | +20% |
| Iodine excess (palpitations) | +10% |
| Severe overhydration (with nausea icon) | +10% |

Temperatures below 38.0 °C and hydration between 100–149 do not trigger tremors.

---

## 14. Diagnostic Commands

```
/aliment status                 Inspect all physiological metrics
/aliment fever [temperature]    Induce calibrated fever/hypothermia (default 39.5, range 31–42; OP only)
/aliment cure                   Reset body to perfect health and clear screen shaders (OP only)
/aliment set <field> <value>    Directly set values (OP only, tab autocomplete)
```

`/aliment fever` injects a calculated dose of pyrogen so that peak temperature reaches the target within two minutes and metabolizes completely within one game day.

---

## 15. Mandrake Botany and Toxicology

A wild herbaceous nightshade plant with four growth stages, **planted on soil blocks rather than farmland**.

### Natural Biomes and Generation Criteria
* **Target Biomes**: `minecraft:plains`, `minecraft:sunflower_plains`, `minecraft:swamp`, `minecraft:mangrove_swamp`.
* **Generation Step**: `GenerationStep.Decoration.VEGETAL_DECORATION`.
* **Placement & Frequency**: 1 plant per chunk (`count: 1`, `in_square`) evaluated against surface heightmap `WORLD_SURFACE_WG`.
* **Substrate Predicate**: Current position must be air (`#minecraft:air`); block directly beneath (offset `[0, -1, 0]`) must be **Grass Block** (`minecraft:grass_block`).
* **Initial State**: Naturally generates in its **mature stage** (`age: 3`), displaying flowering top leaves and hanging green fruits.

### Cultivation and Harvesting
* **Cultivation**: Plantable on grass blocks, dirt, coarse dirt, rooted dirt, mud, moss blocks, and farmland. Grows via random ticks when light level $\ge 9$ (1/8 chance per step); bone meal advances 1 stage per use (3 applications to mature).
* **Harvesting**: Only breaking **Stage 4 (mature)** drops **1–2 fruits** (affected by Fortune); breaking immature stages drops nothing.
* **Seed Propagation**: In crafting grid, **1 fruit $\to$ 2 seeds**, allowing exponential agricultural expansion.

### Ingestion: Scopolamine and Atropine
Edible when full. Fruits provide **+1.0 Scopolamine / +0.1 Atropine**; seeds provide **+0.75 Scopolamine / +0.1 Atropine**. Both clear linearly over 1 game day:
$$\frac{dS_{\text{scop}}}{dt} = - \frac{5.0}{24000} \approx -2.083 \times 10^{-4}\text{ / tick}, \quad \frac{dA_{\text{atro}}}{dt} = - \frac{5.0}{24000} \approx -2.083 \times 10^{-4}\text{ / tick}$$

Combined alkaloid load $\Sigma_{\text{alk}} = S_{\text{scop}} + A_{\text{atro}}$ elevates core body temperature independently of prostaglandins (**salicin cannot break mandrake fever**):
$$\Delta T_{\text{anticholinergic}} = \begin{cases} 0\ ^\circ\text{C}, & \Sigma_{\text{alk}} < 1.5 \\ 1.0\ ^\circ\text{C} \implies T \to 38.0\ ^\circ\text{C}, & 1.5 \le \Sigma_{\text{alk}} < 2.5 \\ 2.5\ ^\circ\text{C} \implies T \to 39.5\ ^\circ\text{C}, & 2.5 \le \Sigma_{\text{alk}} < 4.0 \\ 4.0\ ^\circ\text{C} \implies T \to 41.0\ ^\circ\text{C}, & \Sigma_{\text{alk}} \ge 4.0 \end{cases}$$
* Visual blur contracts view fog to 8 blocks when $S_{\text{scop}} \ge 2.3 \lor A_{\text{atro}} \ge 2.3 \lor \Sigma_{\text{alk}} \ge 2.7$.

---

## 16. Gymnopilus Mushrooms and Psychedelics

A rust-colored wood-decay mushroom flourishing in damp, shady, and wooded biomes.

### Natural Biomes and Generation Criteria
* **Target Biomes**: `minecraft:dark_forest` (Dark Forest), `minecraft:swamp` (Swamp), `minecraft:mangrove_swamp` (Mangrove Swamp), `minecraft:taiga` (Taiga), `minecraft:old_growth_pine_taiga` (Old Growth Pine Taiga), `minecraft:old_growth_spruce_taiga` (Old Growth Spruce Taiga).
* **Generation Step**: `GenerationStep.Decoration.VEGETAL_DECORATION`.
* **Placement & Frequency**: 3 attempts per chunk (`count: 3`, `in_square`) evaluated against surface heightmap `WORLD_SURFACE_WG`.
* **Substrate Predicate**: Current position must be air (`#minecraft:air`); block directly beneath must be Overworld substrate (`#minecraft:substrate_overworld`, supporting dirt, grass, and wood logs).
* **Cultivation & Light Independence**: As a lignicolous wood-decay fungus, it **completely bypasses vanilla mushroom darkness restrictions** (no light $\le 12$ requirement), growing freely under open sunlight and on tree trunks.

### Consumption and Kinetics
* **Raw Consumption**: Eating raw grants **1.3 Psilocybin + 1.3 Psilocin** (3 hunger / 4 saturation).
* **Cooking**: Cooked in a furnace, smoker, or campfire yields **Cooked Gymnopilus** (4 hunger / 5 saturation) with zero psychedelic compounds (heat destroys alkaloids).
* **Metabolic Kinetics**: Psilocybin is an inactive prodrug converted 1:1 into psilocin over half a game day. Psilocin clears at a constant zero-order rate of **1.3 per game day**:
  $$\frac{d[\text{Psilocybin}]}{dt} = - \min\left([\text{Psilocybin}], \frac{1.3}{12000}\right)$$
  $$\frac{d[\text{Psilocin}]}{dt} = \min\left([\text{Psilocybin}], \frac{1.3}{12000}\right) - \frac{1.3}{24000}$$
  Consuming 5 raw mushrooms extends the trip linearly to 10 game days.

### Visual Distortions
* $[\text{Psilocin}] > 1.2$: Chromatic rays emit from block edges.
* $[\text{Psilocin}] > 1.7$: Chromatic color shifts on blocks + full-screen color noise.
* $[\text{Psilocin}] > 2.5$: Mild full-screen spatial wave warping.
* $[\text{Psilocin}] > 5.0$: Intense spatial warping + drug fever climbing up to 39.0 °C.
* $[\text{Psilocin}] \ge 7.0$: Hyperthermia reaching 41.0 °C.

---

## 17. Fermentation and Distillation

### 1. Glass Fermentation Tank
* Crafted with glass surrounding any wood planks in the top center slot.
* Holds 3 water levels. Add **Sugar ×1** and **Brewer's Yeast ×1** to start bubbling fermentation (lasts 45s / 900 ticks), yielding **7% ABV Wine**. Ladle with a glass bottle.

### 2. Brewer's Yeast
* Shapeless crafting: Wheat + Sugar + Brown Mushroom.

### 3. Glass Condenser Pipe and Still
* Crafted with two horizontal rows of glass leaving the center row empty.
* Placed above a heated fermentation tank with campfires below. Every 30 seconds, 1 water level of wine evaporates:
  * Horizontal condenser pipes (facing down) over a cauldron catch distilled vapor, yielding **40% ABV Distilled Wine** (in an Alcohol Cauldron).
  * Improper connections vent vapor into the air with hissing smoke.

### 4. Ethanol Ingestion and Intoxication
* Drinking wine restores **10 hydration**.
* Increases blood ethanol index (0.0 to 1.0): 7% wine adds +0.07; 40% distilled spirits add +0.40.
* Elimination follows zero-order kinetics:
  $$\frac{d[\text{Ethanol}]}{dt} = - \frac{1.0}{24000} \approx -4.167 \times 10^{-5}\text{ / tick}$$
* Intoxication thresholds:
  - $[\text{Ethanol}] \ge 0.35$: Nausea I and Slowness I.
  - $[\text{Ethanol}] \ge 0.70$: Nausea II and Slowness II.

---

## 18. Ephedra and Ephedrine

### Natural Biomes and Generation Criteria
* **Target Biomes**: Arid deserts, badlands plateaus, savannas, and windswept hills:
  - Desert: `minecraft:desert`
  - Badlands: `minecraft:badlands`, `minecraft:eroded_badlands`, `minecraft:wooded_badlands`
  - Savanna: `minecraft:savanna`, `minecraft:savanna_plateau`, `minecraft:windswept_savanna`
  - Hills: `minecraft:windswept_hills`
* **Generation Step & Placement Rules**:
  - `GenerationStep.Decoration.VEGETAL_DECORATION`.
  - Rarity filter: `rarity_filter` with `chance: 8` (12.5% chance per chunk, `in_square`).
  - Heightmap: `WORLD_SURFACE_WG`.
  - Substrate Predicate: Current position must be air (`#minecraft:air`); block directly beneath supports 4 substrates: **Sand (`sand`), Red Sand (`red_sand`), Terracotta (`terracotta`), or Grass Block (`grass_block`)**.
* **Initial State & Growth**:
  - Naturally generates in its **mature stage** (`age: 3`).
  - 4 growth stages (0–3); plantable on sand, red sand, terracotta, and dirt soils. Bone meal accelerates growth (1 stage per application).
* **Sustainable Harvesting**:
  - **Right-Click Harvest**: Right-clicking a mature plant yields 1–2 twigs and **resets the plant to stage 1 without uprooting**, eliminating replanting overhead.
  - **Breaking**: Breaking mature plants drops 1–3 twigs (Fortune applies); breaking immature plants drops 1 twig.

### Crushed Ephedra and Potion Processing
* **Crushed Ephedra**: Ground on a grindstone or crafted in a grid with shears (consumes 1 durability).
* **Ephedrine Potion**: Shapeless crafting with Glass Bottle + Crushed Ephedra (yields potion and restores bottle upon drinking).
* **Pharmacology & Kinetics**: Ingesting raw twigs adds +0.5; drinking a potion adds +2.5 ephedrine (capped at 5.0).
* **Elimination & Haste Effect**:
  $$\frac{d[\text{Ephedrine}]}{dt} = - \frac{5.0}{24000} \approx -2.083 \times 10^{-4}\text{ / tick}$$
  Concentrations $[\text{Ephedrine}] > 1.0$ grant **Haste I** (faster mining speed). A full dose (5.0) provides over 16 continuous minutes of Haste.

---

## 19. Traditional Medicinal Herbs (Coptis, Phellodendron, Licorice)

### Botanical Species and Natural Distribution
1. **Coptis**: Shade-loving herb; raw consumption provides **+1.1 Berberine**.
2. **Phellodendron**: Bark-producing shrub; raw consumption provides **+0.6 Berberine**.
3. **Licorice**: Drought-hardy legume; raw consumption provides **+1.1 Glycyrrhizin**.

* **Ecological Generation Criteria**:
  - **Target Biomes**: All non-cold Overworld biomes (dynamic predicate: `baseTemperature >= 0.2f` and lacking the cold frog tag `!#minecraft:spawns_cold_variant_frogs`). Encompasses Plains, Sunflower Plains, Forests, Birch Forests, Jungles, Savannas, etc.; excludes Snowy Plains, Taiga snow peaks, and Frozen Oceans.
  - **Generation Step**: `GenerationStep.Decoration.VEGETAL_DECORATION`.
  - **Rarity & Density**: Independent rarity filter `rarity_filter` with `chance: 12` (~8.3% chance per chunk, `in_square`).
  - **Substrate Predicate**: Current position must be air (`#minecraft:air`); block directly beneath must strictly be **Grass Block (`minecraft:grass_block`)**; heightmap `WORLD_SURFACE_WG`.
  - **Initial State**: All three species generate naturally in their **mature stage (`age: 3`)**, ready for immediate right-click harvesting or breaking.

### Processing and Potions
* Grind on a grindstone into Crushed Coptis, Crushed Phellodendron, or Crushed Licorice.
* Brew with a Water Bottle in crafting:
  * Crushed Coptis + Water Bottle $\to$ **Coptis Potion** (+2.5 Berberine).
  * Crushed Phellodendron + Water Bottle $\to$ **Phellodendron Potion** (+1.5 Berberine).
  * Crushed Licorice + Water Bottle $\to$ **Licorice Potion** (+2.5 Glycyrrhizin).

### Antimicrobial Actions
For drug concentration $D \in [0.0, 7.0]$, deceleration threshold $D_{\text{slow}} = 1.5$, and suppression threshold $D_{\text{suppress}} = 3.0$:
$$\frac{dL}{dt} = \begin{cases} \dfrac{dL_{\text{base}}}{dt} - C_{\text{immune}}, & D \le 1.5 \\[8pt] \dfrac{dL_{\text{base}}}{dt} \cdot \left(1.0 - 0.75 \cdot \dfrac{D - 1.5}{1.5}\right) - C_{\text{immune}}, & 1.5 < D < 3.0 \\[8pt] - \left( C_{\text{immune}} + c_{\text{suppress}} \cdot \dfrac{D}{3.0} \right), & D \ge 3.0  \end{cases}$$
where $c_{\text{suppress}} = \frac{100.0}{1.5 \times 24000} \approx 2.778 \times 10^{-3}\text{ / tick}$.

* **Berberine (0.0 – 7.0, Antibacterial)**:
  - Specifically targets bacteria. Clears linearly at $\frac{d[\text{Berberine}]}{dt} = - \frac{7.0}{60000} \approx -1.167 \times 10^{-4}\text{ / tick}$ (2.5 game days).
* **Glycyrrhizin (0.0 – 7.0, Antiviral)**:
  - Specifically targets viruses. Clears linearly at $\frac{d[\text{Glycyrrhizin}]}{dt} = - \frac{7.0}{48000} \approx -1.458 \times 10^{-4}\text{ / tick}$ (2.0 game days).

---

## 20. Seaweed Aquaculture and Iodine Supply

### Marine Generation and Ecology
* **Target Biomes**: All ocean biomes (under the `#minecraft:is_ocean` tag: `minecraft:ocean`, `minecraft:deep_ocean`, `minecraft:warm_ocean`, `minecraft:lukewarm_ocean`, `minecraft:deep_lukewarm_ocean`, `minecraft:cold_ocean`, `minecraft:deep_cold_ocean`, `minecraft:frozen_ocean`, `minecraft:deep_frozen_ocean`).
* **Generation Step**: `GenerationStep.Decoration.VEGETAL_DECORATION`.
* **Density & Placement**: 4 attempts per chunk (`count: 4`, `in_square`) evaluated against seafloor heightmap `OCEAN_FLOOR_WG`.
* **Substrate & Fluid Predicate**: Position must be water (`fluids: "minecraft:water"`); seabed directly beneath must be **Sand (`minecraft:sand`)** or **Suspicious Sand (`minecraft:suspicious_sand`)**.
* **Initial State & Aquaculture**:
  - Naturally generates in a **submerged mature state** (`age: 3, waterlogged: true`).
  - 4 growth stages (0–3), requiring full water submersion. Plantable on sand, red sand, gravel, dirt, grass, and terracotta.
  - **Underwater Right-Click Harvesting**: Harvesting mature seaweed yields 1–2 items and resets the plant to stage 1 without uprooting. Bone meal applied underwater accelerates growth.

### Culinary and Medicinal Uses
* **Raw Seaweed**: Eaten raw for +0.20 µmol/L iodine.
* **Cooked Seaweed**: Cooked in furnace, smoker, or campfire for +0.25 µmol/L iodine (3 hunger / 0.6 saturation).
* **Crushed Seaweed**: Milled on a grindstone.
* **Seaweed Iodized Salt**: Crafted with Crushed Seaweed + Salt Powder. Provides **+0.40 µmol/L Iodine**, **+1.5 mmol/L Sodium**, and **+1.5 mmol/L Chloride**.

---

## 21. Vitamin C and Plant Nutrition

* **Clinical Reference Range**:
  * Baseline Normal: **60.0 µmol/L**
  * Safe Range: **40.0 – 80.0 µmol/L**
  * Scurvy Threshold: **15.0 µmol/L**
* **First-Order Clearance Kinetics**:
  $$\frac{dC}{dt} = -k \cdot C, \quad k = \frac{\ln(2)}{120000} \approx 5.776 \times 10^{-6}\text{ / tick}$$
  Decays from 80.0 to 40.0 µmol/L in **exactly 5 in-game days** (120,000 ticks) without plant food.
* **Deficiency Pathology**:
  * Mild (< 40.0 µmol/L): **Mining Fatigue I**.
  * Severe (< 15.0 µmol/L): **Mining Fatigue I + Weakness I**.
* **Dietary Sources**:
  * Apple (+12.0), Golden Apple (+20.0), Enchanted Golden Apple (+30.0)
  * Melon Slice (+8.0)
  * Sweet Berries / Glow Berries (+6.0)
  * Carrot (+10.0), Golden Carrot (+15.0)
  * Pumpkin Pie (+15.0)
  * Beetroot (+6.0), Beetroot Soup (+16.0)
  * Mandrake Fruit (+10.0)
  * Seaweed (+5.0), Cooked Seaweed (+3.0)

---

## 22. Common Pitfalls

1. **Sea water is not dirty; it is hypertonic.** Drinking it causes no immediate nausea; the large sodium load creates severe dehydration and hypernatremic thirst later.
2. **The thirst bar does not indicate overhydration.** 100 and 200 both render as 10 full pips; watch for the weakness icon.
3. **Raw willow broth causes bacterial infections.** Boiled broth does not—the 60-second brew time is essential.
4. **Iodine depletes in 3 game days without retention.** 1 dried kelp or raw seaweed daily is necessary to prevent hypothyroidism.
5. **Salicin is both an anti-inflammatory and an antipyretic.** It reduces fever by inhibiting prostaglandins.
6. **Iatrogenic immunosuppression is fatal.** Suppressing inflammation below 12 triggers spontaneous bacterial infection every second; once load reaches 60, septic magic damage will kill you.
7. **Keep distance from livestock.** Standing within 2 blocks of any mob rolls a 5% viral infection chance every second.
8. **Willow leaves never drop apples.**
9. **Camera tremors always provide a diagnostic symptom icon.** You will never tremor without an active clinical reason.
