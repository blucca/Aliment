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
towards the water.

### `dev/OutbreakPhysiologySelfTest.kt` - physiology

Runs the whole inflammation / electrolyte / pathogen / salicin model headlessly in a few
milliseconds, then exercises the mixins end to end: it really eats raw meat through
`ItemStack.finishUsingItem` and counts the infection rate, drinks soup to check the salicin dose,
and checks the exhaustion multiplier, the mining penalty and the camera-shake counter. 28 checks.

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
