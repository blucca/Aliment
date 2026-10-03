[English Version](TECHNICAL.md) | [中文版本](TECHNICAL_zh.md)

# Aliment Technical Architecture and Developer Guide

This document details the software architecture, engineering standards, cross-language interop seams, build system mechanics, and automated test harness of the **Aliment** mod.

Target Platform: **Minecraft 26.3**, built on **Fabric Loader 0.19.5** and **Fabric API 0.161.0+26.3**.

---

## 1. Multi-Language Tiered Architecture

To guarantee the pure mathematical integrity of physiological calculations and ensure decoupling from Minecraft platform logic, the project enforces a strict, unidirectional four-tier architecture:
`src/main/scala` $\rightarrow$ `src/main/kotlin` $\rightarrow$ `src/main/java` $\rightarrow$ `src/client`

```
   src/main/scala (Scala 3.9)
   [Pure numerical model, zero Minecraft/Fabric dependencies]
             │
             ▼
   src/main/java (Java 25)
   [AlimentModelBridge: Cross-language seam and type barrier]
             │
             ▼
   src/main/kotlin (Kotlin 2.4)
   [Minecraft game logic: Registries, Data Attachments, Block Entities, Events & Symptoms]
             │
             ▼
   src/client (Kotlin + Java)
   [Client HUD, Camera oscillations, Fog convergence, Post-processing Shaders]
```

### 1.1 Scala 3 Model Layer (`src/main/scala/.../physiology/model`)
- **Responsibilities**: Houses all differential equations, physiological steady states, electrolyte clinical reference ranges, bell-shaped immune clearance curves, in vivo pharmacokinetics, and per-tick numerical state integration.
- **Pure Function Invariants**: This layer strictly prohibits importing any Minecraft, Kotlin, or Fabric packages. All inputs and outputs are pure numeric primitives and standard immutable case classes (`ModelState`, `ModelMineral`, `ModelMediators`, `ModelElectrolytes`, `ModelTraceElements`, `ModelDrugs`).
- **Stateless Computation**: `Physiology.tick(...)` consumes the current state and environmental parameters, functionally returning the integrated next state; all active compounds and alkaloids are encapsulated in `ModelDrugs` and metabolically stepped by `Physiology.stepDrugs`.

### 1.2 Java Interop Seam (`src/main/java/.../physiology/AlimentModelBridge.java`)
- **Root Justification**: When the Kotlin K2 compiler references a class mentioning a Scala type, it eagerly attempts to resolve all super-interfaces (including `scala.Product`). Even with a correctly configured classpath, Kotlin fails with `Cannot access 'scala.Product'`.
- **Architectural Constraints**:
  - `AlimentModelBridge.java` is the **only file in the entire repository permitted to mention Scala types**.
  - All Scala types must remain strictly encapsulated within `private` internal fields and method bodies, never exposed as `public` fields, parameters, or return types.
  - Exposes only Kotlin-side storage carriers (`AlimentData`) and Java primitive types to the Kotlin layer.
  - All constants are defined exclusively in the Scala model and re-exported via static methods on the Bridge to prevent dual-threshold drift.

### 1.3 Kotlin Domain Layer (`src/main/kotlin/...`)
- **Responsibilities**: Implements all engine integration and game mechanics.
  - **Data Persistence and Codec Extension**: Binds `AlimentData` to Player entities using the Fabric Data Attachment API. To bypass the Mojang DataFixerUpper `RecordCodecBuilder.instance.group(...)` limit of 16 fields (`Products.P16`), a flattened auxiliary `Compounds` record and nested `MapCodec` are inlined into the root Codec, maintaining flat NBT backward compatibility while supporting extensive biomarkers.
  - **Symptom Tick Engine**: `AlimentSymptoms` evaluates per-tick core temperature, dehydration penalties, electrolyte imbalance effects (slowness, nausea, blindness), ephedrine digging haste, etc.
  - **Ingestion Hooks**: `AlimentIngestion` intercepts food, drink, and medicine consumption to update hydration, electrolyte pools, and pathogen seeds.
  - **Interactions**: `AlimentInteractions` manages sneak-grinding, cauldron brewing, injection syringes, and related interactions.

### 1.4 Client Rendering Layer (`src/client`)
- **HUD Extensions**: `AlimentThirstHud` renders an independent 10-pip thirst bar above the player's health indicators (supporting empty, half, and full states).
- **Post-Processing Shaders**: Screen distortion, chromatic aberration, and fever blur shaders triggered during psilocin intoxication, hyperthermia, or mandrake delirium.
- **Dynamic Fog Shaders**: During anticholinergic intoxication, client Mixins contract render distance fog down to 8 blocks to simulate pupil dilation (mydriasis) and loss of visual accommodation.

---

## 2. Core Build System and Compiler Protections

### 2.1 Independent `compileModelScala` Task
The standard Gradle Scala plugin's `compileScala` task unconditionally depends on `compileJava`, regardless of whether Java sources exist in that source set. This creates an unresolvable cyclic dependency:
$$\text{compileJava} \rightarrow \text{compileKotlin} \rightarrow \text{compileScala} \rightarrow \text{compileJava}$$

**Solution**:
A custom `ScalaCompile` task named `compileModelScala` is explicitly configured in `build.gradle.kts` with four underlying conventions:
1. `incrementalOptions.analysisFile`
2. `incrementalOptions.classfileBackupDir`
3. `targetCompatibility`
4. `javaLauncher`
The default `compileScala` task is emptied of sources. Compiled model classes are injected into downstream compilation classpaths as plain file dependencies (`files(compileModelScala)`).

### 2.2 IntelliJ IDEA Resource Shadow Copy Protection (`dropIdeResourceCopies`)
When IntelliJ IDEA is configured with "Build and run using: IntelliJ IDEA", the IDE automatically copies `src/main/resources` directly into `build/classes/java/main`. Because this directory precedes resources on the classpath, unprocessed `${version}` tokens in `fabric.mod.json` crash Fabric at runtime, and subsequent Gradle `jar` tasks fail on duplicate entries.

**Solution**:
`build.gradle.kts` injects the dedicated `dropIdeResourceCopies` task, which scrubs all non-`.class` copies from `build/classes/java/main` immediately before executing any `runClient`, `runServer`, or `jar` task.

---

## 3. Mixin Registry

| Mixin Class | Target Class | Implementation Hook |
| --- | --- | --- |
| `ItemMixin.java` | `net.minecraft.world.item.ItemStack` | Intercepts completed item consumption to trigger `AlimentIngestion` metabolism |
| `PlayerMixin.java` | `net.minecraft.world.entity.player.Player` | Modulates exhaustion rates and severe dehydration damage |
| `GrindstoneInputSlotMixin.java` | `net.minecraft.world.inventory.GrindstoneMenu` (Input Slot) | Lifts vanilla damaged/enchanted item restrictions, allowing botanical herbs, rock salt, and bark |
| `GrindstoneMenuMixin.java` | `net.minecraft.world.inventory.GrindstoneMenu` | Hooks into `AlimentGrinding` recipe maps to produce milled powders and botanical pieces |
| `CameraMixin.java` (Client) | `net.minecraft.client.Camera` | Applies damped oscillatory camera roll during hypothermic shivering without displacing entity physics |
| `FogRendererMixin.java` (Client) | `net.minecraft.client.renderer.FogRenderer` | Contracts view fog distance down to 8 blocks during anticholinergic intoxication |
| `HudMixin.java` (Client) | `net.minecraft.client.gui.Gui` | Injects thirst HUD rendering hooks above player health hearts |

---

## 4. Automated Asset and Data Generation (`tools/`)

The mod adheres strictly to a **data-driven, code-generation-first** design. All JSON descriptors and sprite textures can be idempotently generated from scratch:

### 4.1 Data Generator (`tools/gen_data.ps1`)
- Scrapes the latest data schemas directly from the cached Minecraft 26.3 client JAR.
- Generates 400+ JSON descriptor files:
  - `blockstates/` and `models/block/`: Willow sets, cauldron variations (raw/cooked broth, brine), fermentation tanks, condenser pipes, and 4 growth stages each for ephedra, coptis, phellodendron, and licorice.
  - `items/` and `models/item/`: Definitions for all custom items (botanical raw parts, crushed herbs, potions, and tools).
  - `recipes/`: Carpentry, brewing yeast, glassware, stirring rods, shearing recipes, and medicinal infusions.
  - `loot_tables/`: Block destruction and crop harvesting tables with Fortune scaling and maturation stages.
  - `worldgen/`: Riparian willow river placements, subterranean rock salt veins, and arid ephedra vegetation features.
  - `lang/`: Aligns and synchronizes `en_us.json`, `zh_cn.json`, and `ja_jp.json`.

### 4.2 Procedural Texture Engine (`tools/gen_textures.ps1`, `tools/gen_ephedra_textures.ps1`, `tools/gen_herbs_textures.ps1`)
- All textures are procedurally synthesized using ImageMagick scripting, eliminating manual drawing.
- **Vanilla Potion Composite Algorithm**: Extracts the vanilla `potion.png` bottle mask and `potion_overlay.png` liquid overlay, blending custom chromatic matrices (e.g. amber gold for ephedrine, limpid bitter yellow for coptis, golden-brown for phellodendron, dark brown for licorice) using multiply blend modes to match vanilla pixel aesthetics.

---

## 5. Automated Headless Test Suite

Because standard JUnit runners cannot emulate world generation checks, chunk boundaries, player inventory interactions, and network synchronization, the mod incorporates a headless test suite built on Fabric's `FakePlayer`:

### 5.1 Block and Interaction Tests (`AlimentSelfTest.kt`, 107 assertions)
- **Arboreal Growth**: Verifies riparian riverbank detection, directional trunk angling toward open water, and vine draping.
- **Block Mechanics**: Axe stripping drops bark; grindstone slots accept botanical herbs, rock salt, and bark; shear crafting degrades tool durability by 1; cauldrons brew broth after 60 seconds over active campfires; condenser pipes validate directional connections.
- **Recipes and Loot**: Validates that all `RecipeSerializer` and `LootTable` entries parse cleanly on reload.

### 5.2 Physiological Model and Localization Suite (`AlimentPhysiologySelfTest.kt`, 400+ assertions)
- **Equilibrium Values**: Verifies that healthy baseline states remain centered in normal clinical reference ranges.
- **Immune Bell Curve**: Confirms pathogen growth suppression across low, optimal, and cytokine-storm inflammation bands, as well as reactive stress storms above load 55.
- **Electrolyte Pathophysiology**: Validates hypernatremia, hyponatremia, and hyperkalemia symptom onset and lethal collapse.
- **Targeted Pharmacokinetics**:
  - Salicin fever reduction and dexamethasone cytokine storm arrest;
  - Ephedrine per-tick decay (clearing within 1 game day) and haste activation;
  - Berberine antibacterial efficacy: normal growth at $\le 1.5$, deceleration at $>1.5$, complete replication block and suppression at $\ge 3.0$, eradicating full infection within 1.5 game days with a 2.5-day clearance window;
  - Glycyrrhizin antiviral efficacy: normal replication at $\le 1.5$, deceleration at $>1.5$, complete block at $\ge 3.0$, clearing full viral load within 1.5 game days with a 2.0-day clearance window.
- **Localization Completeness**: Uses runtime reflection over all registered items and blocks to assert 100% dictionary key coverage across English, Chinese, and Japanese without missing keys.
