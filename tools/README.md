# tools

Helper scripts used while developing the Outbreak willow (柳树) content. None of them are part
of the built mod — they only regenerate files under `src/main/resources`.

| script | what it does |
| --- | --- |
| `gen_data.ps1` / `gen_data.cmd` | Regenerates **all** data and asset JSON files (blockstates, block/item models, item definitions, worldgen, loot tables, recipes, tags, language files). Uses the vanilla JSON inside the Minecraft jar as the template, so the output always matches the exact format of the Minecraft version in `gradle.properties`. |
| `lang_zh_cn.json` | The Chinese block/item names, read by `gen_data.ps1`. Kept out of the script so the script itself stays pure ASCII and therefore safe to run with either Windows PowerShell 5.1 or PowerShell 7. |
| `gen_textures.ps1` / `gen_textures.cmd` | Regenerates all 31 PNG textures (blocks, items, entity boats, bark and soup sprites, the cauldron liquid surfaces and the daffodil mod icon) from scratch with ImageMagick. |
| `verify-datapack/` | A dev-only data pack that proves the willow world generation actually runs. See below. |

Both generators are idempotent: running them twice produces byte-identical output.

## Requirements

* **ImageMagick** at `.tools\imagemagick\magick.exe`. It is not committed to the repository
  (`.tools/` is git-ignored); download the portable Q16 x64 build from
  <https://imagemagick.org/download/> and unpack it there.
* The Minecraft jars that Loom downloads into `.gradle\loom-cache\minecraftMaven\...`
  (created automatically by any Gradle build).

## Running them

```
tools\gen_data.cmd
tools\gen_textures.cmd
```

Both `.ps1` files are pure ASCII, so they also run fine when invoked directly:

```powershell
powershell -ExecutionPolicy Bypass -File tools\gen_data.ps1
```

## verify-datapack

`verify-datapack` is a small data pack that runs on world load and counts willow blocks in the
generated chunks, reporting its findings with `say`. It is how the world generation of this mod
was verified end to end.

To use it:

1. Copy it into a test world: `run/world/datapacks/outbreak-verify/`.
2. Start the dev server with a superflat **river** world so every generated chunk goes through
   the river biome that Outbreak injects the willow feature into. In `run/server.properties`:

   ```
   level-type=minecraft:flat
   generator-settings={"biome":"minecraft:river","features":true,"lakes":false,"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"structure_overrides":[]}
   ```

3. Run `gradlew runServer`. After a few seconds the log should contain:

   ```
   [Server] OUTBREAK_RIVERWORLD_VINES_OK
   [Server] OUTBREAK_RIVERWORLD_TREES_OK
   [Server] OUTBREAK_RIVERWORLD_LEAVES_OK
   ```

Delete the world afterwards — the data pack clears the blocks it inspects.

Add `pause-when-empty-seconds=0` to `run/server.properties` while testing. Without it the
dedicated server stops ticking after 60 s with no players, and any tick-driven test stalls.

## Self test

There are two development-only entrypoints. Neither is referenced by `fabric.mod.json`, so both
are dead code in the shipped jar.

### `dev/OutbreakSelfTest.kt` - content

Drives the player-facing willow features with Fabric's `FakePlayer` on a headless server: axe
stripping, grindstone grinding, filling the cauldron, the 60 second campfire cook, taking a serving
with a bottle and with a bowl, and growing a willow next to a pool to check that the trunk leans
towards the water. Then the plants: sowing a mandrake seed on dirt, grass, coarse dirt and farmland
(through the real item path), bone meal through all four stages, the loot table dropping 1-2 fruit
when ripe and nothing at all before that, the seeds recipe being in the recipe manager, the mandrake
patches being attached to the plains and the swamps and to nothing else, the gymnopilus' three
cooking recipes loading with the same timings raw beef has, and its patches being attached to the dark
forest and the taiga and to nothing else. It reads the worldgen answer out of the biome registry
rather than by scanning a world, which is what the patches are actually decided by, and verifies the advancement tree and triggers, and the fermentation tank and condenser pipe distillation machinery. 85 checks.

### `dev/OutbreakPhysiologySelfTest.kt` - physiology

Runs the whole model headlessly in a few milliseconds: homeostasis, infection clearance,
untreated immune storm, salicin and dexamethasone control, overdose, drug metabolism, the immune
competence curve, the mediator weights, thirst over a game day, over-hydration, electrolyte
dilution from heavy drinking, the iodine store draining to its floor in exactly three game days and
what a day's kelp does about it, the two mandrake alkaloids (the three fever steps, the blur
thresholds, the cap and the metabolism, and that the drug fever stacks on an infection's), the two
gymnopilus compounds (that psilocybin is inert and converts one for one over half a day, that
psilocin leaves at a flat 1.3 a day so five doses are ten game days, all four trip stages either
side of their thresholds, and the two fever steps), the
temperature model (fever, hypothermia, the environment, the
thyroid) and the fever command. Then it exercises the mixins and the symptom layer end to end: it
really eats raw meat through `ItemStack.finishUsingItem` and counts the infection rate, eats a
mandrake fruit and its seeds and a raw and a cooked gymnopilus to check what each carries, reads the
two mushrooms' food values off their item components, drinks all
twelve of the drinks to check the 15 water each, drinks salt water and sea water to check the
minerals, checks that swamp water is foul and sea water is not, probes every mineral on both sides of
both of its thresholds, asserts the camera-shake chance is zero for every state the player cannot
see, counts the two per-second contagion dice, checks which screen effects each fever tier asks for,
that the drug blur stacks with them and that exactly one trip stage is on the screen at a time,
drives a real `GrindstoneMenu` to prove the two grindstone
mixins applied, rolls the chest-loot pool
the mod actually adds, freezes a creative player and respawns a dead one, and checks the exhaustion
multiplier, the mining penalty and the synced client state. It also exercises the ephedra
and ephedrine system: eating ephedra (+0.5 ephedrine), purified ephedrine (+2.5 ephedrine),
capping at 5.0, granting Haste I when > 1.0, and 1-game-day (24000 ticks) linear metabolism decay.
It also enumerates every item and entity
type the mod registers and fails, naming the key, if any of them has no name in `en_us.json` or
`zh_cn.json` or `ja_jp.json`. 381 checks.

### Running either one

Temporarily add it to the `main` entrypoint in `src/main/resources/fabric.mod.json`:

```json
"main": [
  "com.github.kusa233.outbreak.Outbreak",
  "com.github.kusa233.outbreak.dev.OutbreakPhysiologySelfTest"
]
```

then `gradlew runServer` and look for the `SELFTEST` lines. The content test takes about 75 seconds
(most of it the 60 second cook); the physiology test finishes on the first server tick. Remove the
entrypoint again afterwards.

Note: a player-less dev server has no entity-ticking chunks, so dropped items never show up in
entity queries there. The bark drop is therefore asserted by the code path rather than by looking
for the dropped item.
