<#
    gen_data.ps1 - regenerates every data / asset JSON file of the Outbreak willow set.

    The vanilla JSON that ships inside the Minecraft jar is used as the template for all the
    blockstate / model / item-definition boilerplate, so the generated files always match the
    exact format of the Minecraft version this project builds against.

    Run with (from the repository root):
        tools\gen_data.cmd
    or:
        pwsh -ExecutionPolicy Bypass -File tools\gen_data.ps1
#>

$ErrorActionPreference = "Stop"

# $PSScriptRoot is empty when the file is dot-sourced through Invoke-Expression, so fall back
# to the current directory (which must then be the repository root).
$root = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$res = Join-Path $root "src\main\resources"
$ns = "outbreak"

# ---------------------------------------------------------------------------- vanilla source

# assets live in the client-only jar, data (loot tables, tags, ...) in the common jar
$mavenRoot = Join-Path $root ".gradle\loom-cache\minecraftMaven\net\minecraft"

# Several Minecraft versions stay cached side by side, and their asset formats differ (26.3
# flattened loot table conditions, for instance), so only ever read the version this project
# actually builds against.
$mcVersion = (Select-String -Path (Join-Path $root "gradle.properties") -Pattern '^minecraft_version=(.+)$').Matches[0].Groups[1].Value.Trim()
if (-not $mcVersion) { throw "Could not read minecraft_version from gradle.properties" }

$jars = @()
if (Test-Path $mavenRoot) {
    $jars = @(Get-ChildItem $mavenRoot -Recurse -Filter "minecraft-*.jar" |
        Where-Object {
            $_.Name -notlike "*.backup" -and
            $_.Name -match "minecraft-(clientOnly|common)-" -and
            $_.FullName -match [regex]::Escape($mcVersion)
        } |
        Select-Object -ExpandProperty FullName)
}
if ($jars.Count -eq 0) { throw "Could not locate the Minecraft $mcVersion jars in .gradle/loom-cache" }
Write-Host "reading vanilla assets from Minecraft $mcVersion"

Add-Type -AssemblyName System.IO.Compression.FileSystem
$vanilla = @{}
foreach ($jar in $jars) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        foreach ($entry in $zip.Entries) {
            $isWanted = ($entry.FullName -like "assets/minecraft/*.json") -or ($entry.FullName -like "data/minecraft/*.json")
            if ($isWanted -and $entry.Length -gt 0 -and -not $vanilla.ContainsKey($entry.FullName)) {
                $reader = New-Object System.IO.StreamReader($entry.Open())
                try { $vanilla[$entry.FullName] = $reader.ReadToEnd() } finally { $reader.Dispose() }
            }
        }
    } finally { $zip.Dispose() }
}

function Get-Vanilla([string]$path) {
    if (-not $vanilla.ContainsKey($path)) { throw "Vanilla asset not found: $path" }
    return $vanilla[$path]
}

$script:written = 0

function Write-Json([string]$relativePath, [string]$content) {
    $full = Join-Path $res $relativePath
    $dir = Split-Path $full -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    [System.IO.File]::WriteAllText($full, ($content.TrimEnd() + "`n"), (New-Object System.Text.UTF8Encoding($false)))
    $script:written++
}

# ---------------------------------------------------------------------------- name mapping

# vanilla filename fragment -> our filename fragment
function Convert-Name([string]$name) { return ($name -replace 'oak', 'willow') }

# asset JSON body: every oak reference becomes willow, templates/flower pots stay vanilla
function Convert-OakAsset([string]$text) {
    $t = $text
    $t = $t -replace 'minecraft:block/stripped_oak_', "$ns`:block/stripped_willow_"
    $t = $t -replace 'minecraft:block/oak_', "$ns`:block/willow_"
    $t = $t -replace 'minecraft:item/oak_', "$ns`:item/willow_"
    return $t
}

function Convert-VineAsset([string]$text) {
    $t = $text
    $t = $t -replace 'minecraft:block/weeping_vines_plant', "$ns`:block/willow_vines_plant"
    $t = $t -replace 'minecraft:block/weeping_vines', "$ns`:block/willow_vines"
    return $t
}

# loot table JSON body
function Convert-OakLoot([string]$text) {
    $t = $text
    $t = $t -replace 'minecraft:blocks/oak_', "$ns`:blocks/willow_"
    $t = $t -replace 'minecraft:oak_', "$ns`:willow_"
    return $t
}

# ---------------------------------------------------------------------------- assets: blockstates

$blockstates = @(
    'oak_log', 'oak_wood', 'stripped_oak_log', 'stripped_oak_wood', 'oak_planks', 'oak_leaves',
    'oak_sapling', 'potted_oak_sapling', 'oak_stairs', 'oak_slab', 'oak_fence', 'oak_fence_gate',
    'oak_door', 'oak_trapdoor', 'oak_pressure_plate', 'oak_button', 'oak_shelf', 'oak_sign',
    'oak_wall_sign', 'oak_hanging_sign', 'oak_wall_hanging_sign'
)
foreach ($name in $blockstates) {
    $body = Convert-OakAsset (Get-Vanilla "assets/minecraft/blockstates/$name.json")
    Write-Json "assets/$ns/blockstates/$(Convert-Name $name).json" $body
}
foreach ($pair in @(@('weeping_vines', 'willow_vines'), @('weeping_vines_plant', 'willow_vines_plant'))) {
    $body = Convert-VineAsset (Get-Vanilla "assets/minecraft/blockstates/$($pair[0]).json")
    Write-Json "assets/$ns/blockstates/$($pair[1]).json" $body
}

# Rock salt ore is a plain cube, so a single-variant blockstate is all it needs.
Write-Json "assets/$ns/blockstates/rock_salt_ore.json" @"
{
  "variants": {
    "": {
      "model": "$ns`:block/rock_salt_ore"
    }
  }
}
"@

# ---------------------------------------------------------------------------- assets: block models

$blockModels = @(
    'oak_log', 'oak_log_horizontal', 'oak_wood', 'stripped_oak_log', 'stripped_oak_wood',
    'oak_planks', 'oak_leaves', 'oak_sapling', 'potted_oak_sapling',
    'oak_stairs', 'oak_stairs_inner', 'oak_stairs_outer',
    'oak_slab', 'oak_slab_top',
    'oak_fence_post', 'oak_fence_side', 'oak_fence_inventory',
    'oak_fence_gate', 'oak_fence_gate_open', 'oak_fence_gate_wall', 'oak_fence_gate_wall_open',
    'oak_door_bottom_left', 'oak_door_bottom_left_open', 'oak_door_bottom_right', 'oak_door_bottom_right_open',
    'oak_door_top_left', 'oak_door_top_left_open', 'oak_door_top_right', 'oak_door_top_right_open',
    'oak_trapdoor_bottom', 'oak_trapdoor_open', 'oak_trapdoor_top',
    'oak_pressure_plate', 'oak_pressure_plate_down',
    'oak_button', 'oak_button_pressed', 'oak_button_inventory',
    'oak_shelf', 'oak_shelf_center', 'oak_shelf_inventory', 'oak_shelf_left', 'oak_shelf_right',
    'oak_shelf_unconnected', 'oak_shelf_unpowered',
    'oak_sign_rot_0', 'oak_sign_rot_1', 'oak_sign_rot_2', 'oak_sign_rot_3',
    'oak_wall_sign',
    'oak_hanging_sign_rot_0', 'oak_hanging_sign_rot_1', 'oak_hanging_sign_rot_2', 'oak_hanging_sign_rot_3',
    'oak_hanging_sign_attached_rot_0', 'oak_hanging_sign_attached_rot_1',
    'oak_hanging_sign_attached_rot_2', 'oak_hanging_sign_attached_rot_3',
    'oak_wall_hanging_sign'
)
foreach ($name in $blockModels) {
    $body = Convert-OakAsset (Get-Vanilla "assets/minecraft/models/block/$name.json")
    Write-Json "assets/$ns/models/block/$(Convert-Name $name).json" $body
}
foreach ($pair in @(@('weeping_vines', 'willow_vines'), @('weeping_vines_plant', 'willow_vines_plant'))) {
    $body = Convert-VineAsset (Get-Vanilla "assets/minecraft/models/block/$($pair[0]).json")
    Write-Json "assets/$ns/models/block/$($pair[1]).json" $body
}

# ---------------------------------------------------------------------------- assets: item models

foreach ($name in @('oak_boat', 'oak_chest_boat', 'oak_door', 'oak_hanging_sign', 'oak_sapling', 'oak_sign')) {
    $body = Convert-OakAsset (Get-Vanilla "assets/minecraft/models/item/$name.json")
    Write-Json "assets/$ns/models/item/$(Convert-Name $name).json" $body
}

# ---------------------------------------------------------------------------- assets: item definitions

$itemDefinitions = @(
    'oak_boat', 'oak_button', 'oak_chest_boat', 'oak_door', 'oak_fence', 'oak_fence_gate',
    'oak_hanging_sign', 'oak_leaves', 'oak_log', 'oak_planks', 'oak_pressure_plate', 'oak_sapling',
    'oak_shelf', 'oak_sign', 'oak_slab', 'oak_stairs', 'oak_trapdoor', 'oak_wood',
    'stripped_oak_log', 'stripped_oak_wood'
)
foreach ($name in $itemDefinitions) {
    $body = Convert-OakAsset (Get-Vanilla "assets/minecraft/items/$name.json")
    Write-Json "assets/$ns/items/$(Convert-Name $name).json" $body
}

# Willow vines have no vanilla block to borrow from.
Write-Json "assets/$ns/items/willow_vines.json" @"
{
  "model": {
    "type": "minecraft:model",
    "model": "$ns`:block/willow_vines"
  }
}
"@

# ---------------------------------------------------------------------------- assets: bark & soup

# Simple sprite items: bark, bark pieces and the four soup variants.
$spriteItems = @(
    'willow_bark',
    'willow_bark_pieces',
    'raw_willow_bark_soup_bottle',
    'raw_willow_bark_soup_bowl',
    'willow_bark_soup_bottle',
    'willow_bark_soup_bowl'
)
foreach ($name in $spriteItems) {
    Write-Json "assets/$ns/models/item/$name.json" @"
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "$ns`:item/$name"
  }
}
"@
    Write-Json "assets/$ns/items/$name.json" @"
{
  "model": {
    "type": "minecraft:model",
    "model": "$ns`:item/$name"
  }
}
"@
}

# The soup cauldron reuses the vanilla cauldron shell and only swaps the liquid surface, so it
# needs no custom renderer: one model per (cooked, level) combination.
foreach ($cooked in @($false, $true)) {
    $suffix = if ($cooked) { 'cooked' } else { 'raw' }
    foreach ($level in 1..3) {
        $template = if ($level -eq 3) { 'template_cauldron_full' } else { "template_cauldron_level$level" }
        Write-Json "assets/$ns/models/block/willow_soup_cauldron_${suffix}_$level.json" @"
{
  "parent": "minecraft:block/$template",
  "textures": {
    "content": "$ns`:block/willow_soup_$suffix"
  }
}
"@
    }
}

$variants = @()
foreach ($cooked in @($false, $true)) {
    $suffix = if ($cooked) { 'cooked' } else { 'raw' }
    foreach ($level in 1..3) {
        $variants += @"
    "cooked=$($cooked.ToString().ToLower()),level=$level": {
      "model": "$ns`:block/willow_soup_cauldron_${suffix}_$level"
    }
"@
    }
}
Write-Json "assets/$ns/blockstates/willow_soup_cauldron.json" ("{`n  `"variants`": {`n" + ($variants -join ",`n") + "`n  }`n}")

# ---------------------------------------------------------------------------- assets: salt chain

# Plain sprite items: the salt materials, the tool and the injection.
$saltSpriteItems = @(
    'crude_salt',
    'crude_salt_powder',
    'salt_powder',
    'stirring_rod',
    'dexamethasone_injection',
    'crude_salt_water',
    'salt_water',
    'swamp_water_bottle',
    'sea_water_bottle',
    'crude_salt_swamp_water',
    'salt_swamp_water',
    'crude_salt_sea_water',
    'salt_sea_water',
    'crude_salt_mushroom_stew',
    'salt_mushroom_stew',
    'crude_salt_willow_bark_soup',
    'salt_willow_bark_soup',
    'crude_salt_raw_willow_bark_soup',
    'salt_raw_willow_bark_soup'
)
foreach ($name in $saltSpriteItems) {
    Write-Json "assets/$ns/models/item/$name.json" @"
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "$ns`:item/$name"
  }
}
"@
    Write-Json "assets/$ns/items/$name.json" @"
{
  "model": {
    "type": "minecraft:model",
    "model": "$ns`:item/$name"
  }
}
"@
}

# Rock salt ore is an ordinary cube, so its item model just parents the block model.
Write-Json "assets/$ns/models/block/rock_salt_ore.json" @"
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "$ns`:block/rock_salt_ore"
  }
}
"@
Write-Json "assets/$ns/models/item/rock_salt_ore.json" @"
{
  "parent": "$ns`:block/rock_salt_ore"
}
"@
Write-Json "assets/$ns/items/rock_salt_ore.json" @"
{
  "model": {
    "type": "minecraft:model",
    "model": "$ns`:item/rock_salt_ore"
  }
}
"@

# The brine cauldron uses the same vanilla shell as the soup cauldron; three stages, each one
# level lower and each with a saltier surface.
$brineTextures = @('brine_crude', 'brine_concentrated', 'brine_dense')
$brineVariants = @()
foreach ($stage in 0..2) {
    $level = 3 - $stage
    $template = if ($level -eq 3) { 'template_cauldron_full' } else { "template_cauldron_level$level" }
    Write-Json "assets/$ns/models/block/brine_cauldron_$stage.json" @"
{
  "parent": "minecraft:block/$template",
  "textures": {
    "content": "$ns`:block/$($brineTextures[$stage])"
  }
}
"@
    $brineVariants += @"
    "stage=$stage": {
      "model": "$ns`:block/brine_cauldron_$stage"
    }
"@
}
Write-Json "assets/$ns/blockstates/brine_cauldron.json" ("{`n  `"variants`": {`n" + ($brineVariants -join ",`n") + "`n  }`n}")

# ---------------------------------------------------------------------------- assets: language

function HexToSignedInt([string]$hex) {
    $v = [int64][Convert]::ToUInt32($hex, 16)
    if ($v -gt [int]::MaxValue) { return [int]($v - 4294967296) }
    return [int]$v
}

# English labels live here; the Chinese ones are read from tools/lang_zh_cn.json so that this
# script can stay pure ASCII (Windows PowerShell reads .ps1 files with the system code page and
# would otherwise mangle non-ASCII string literals).
$blockEntries = [ordered]@{
    'willow_log'              = 'Willow Log'
    'willow_wood'             = 'Willow Wood'
    'stripped_willow_log'     = 'Stripped Willow Log'
    'stripped_willow_wood'    = 'Stripped Willow Wood'
    'willow_planks'           = 'Willow Planks'
    'willow_leaves'           = 'Willow Leaves'
    'willow_sapling'          = 'Willow Sapling'
    'potted_willow_sapling'   = 'Potted Willow Sapling'
    'willow_vines'            = 'Willow Vines'
    'willow_vines_plant'      = 'Willow Vines Plant'
    'willow_stairs'           = 'Willow Stairs'
    'willow_slab'             = 'Willow Slab'
    'willow_fence'            = 'Willow Fence'
    'willow_fence_gate'       = 'Willow Fence Gate'
    'willow_door'             = 'Willow Door'
    'willow_trapdoor'         = 'Willow Trapdoor'
    'willow_pressure_plate'   = 'Willow Pressure Plate'
    'willow_button'           = 'Willow Button'
    'willow_shelf'            = 'Willow Shelf'
    'willow_sign'             = 'Willow Sign'
    'willow_hanging_sign'     = 'Willow Hanging Sign'
    'willow_soup_cauldron'    = 'Willow Bark Soup Cauldron'
    'rock_salt_ore'           = 'Rock Salt Ore'
    'brine_cauldron'          = 'Brine Cauldron'
    'mandrake'                = 'Mandrake'
}

$itemEntries = [ordered]@{
    'willow_bark'                  = 'Willow Bark'
    'willow_bark_pieces'           = 'Willow Bark Pieces'
    'raw_willow_bark_soup_bottle'  = 'Raw Willow Bark Soup'
    'raw_willow_bark_soup_bowl'    = 'Raw Willow Bark Soup'
    'willow_bark_soup_bottle'      = 'Willow Bark Soup'
    'willow_bark_soup_bowl'        = 'Willow Bark Soup'
    'crude_salt'                   = 'Crude Salt'
    'crude_salt_powder'            = 'Crude Salt Powder'
    'salt_powder'                  = 'Salt Powder'
    'stirring_rod'                 = 'Stirring Rod'
    'dexamethasone_injection'      = 'Dexamethasone Injection'
    'crude_salt_water'             = 'Crude Salt Water'
    'salt_water'                   = 'Salt Water'
    'swamp_water_bottle'           = 'Swamp Water Bottle'
    'sea_water_bottle'             = 'Sea Water Bottle'
    'crude_salt_swamp_water'       = 'Crude Salt Swamp Water'
    'salt_swamp_water'             = 'Salt Swamp Water'
    'crude_salt_sea_water'         = 'Crude Salt Sea Water'
    'salt_sea_water'               = 'Salt Sea Water'
    'crude_salt_mushroom_stew'     = 'Crude Salt Mushroom Stew'
    'salt_mushroom_stew'           = 'Salt Mushroom Stew'
    'crude_salt_willow_bark_soup'  = 'Crude Salt Willow Bark Soup'
    'salt_willow_bark_soup'        = 'Salt Willow Bark Soup'
    'crude_salt_raw_willow_bark_soup' = 'Crude Salt Raw Willow Bark Soup'
    'salt_raw_willow_bark_soup'    = 'Salt Raw Willow Bark Soup'
    'mandrake_fruit'               = 'Mandrake Fruit'
    'mandrake_seeds'               = 'Mandrake Seeds'
}

# the two boats are plain items that also have entity names, like vanilla's
$boatEntries = [ordered]@{
    'willow_boat'             = 'Willow Boat'
    'willow_chest_boat'       = 'Willow Boat with Chest'
}

$zhSource = Get-Content (Join-Path $root "tools\lang_zh_cn.json") -Raw -Encoding UTF8 | ConvertFrom-Json
$zhNames = @{}
foreach ($prop in $zhSource.PSObject.Properties) { $zhNames[$prop.Name] = $prop.Value }

$en = [ordered]@{}
$zh = [ordered]@{}
foreach ($group in @(
    @{ prefix = 'block'; entries = $blockEntries },
    @{ prefix = 'item';  entries = $itemEntries }
)) {
    foreach ($k in $group.entries.Keys) {
        if (-not $zhNames.ContainsKey($k)) { throw "tools/lang_zh_cn.json is missing an entry for $k" }
        $en["$($group.prefix).$ns.$k"] = $group.entries[$k]
        $zh["$($group.prefix).$ns.$k"] = $zhNames[$k]
    }
}
foreach ($k in $boatEntries.Keys) {
    if (-not $zhNames.ContainsKey($k)) { throw "tools/lang_zh_cn.json is missing an entry for $k" }
    $en["item.$ns.$k"] = $boatEntries[$k]
    $zh["item.$ns.$k"] = $zhNames[$k]
    $en["entity.$ns.$k"] = $boatEntries[$k]
    $zh["entity.$ns.$k"] = $zhNames[$k]
}

# The mod's own creative tab. Its Chinese name is "Bao Fa" (see tools/lang_zh_cn.json).
$en["itemGroup.$ns.main"] = 'Outbreak'
$zh["itemGroup.$ns.main"] = $zhNames['itemGroup.outbreak.main']

Write-Json "assets/$ns/lang/en_us.json" ($en | ConvertTo-Json -Depth 4)
Write-Json "assets/$ns/lang/zh_cn.json" ($zh | ConvertTo-Json -Depth 4)

# ---------------------------------------------------------------------------- data: worldgen

$belowTrunkProvider = @'
    "below_trunk_provider": {
      "type": "minecraft:rule_based",
      "rules": [
        {
          "if_true": {
            "type": "minecraft:not",
            "predicate": {
              "type": "minecraft:matching_block_tag",
              "tag": "minecraft:cannot_replace_below_tree_trunk"
            }
          },
          "then": {
            "type": "minecraft:simple",
            "state": "minecraft:dirt"
          }
        }
      ]
    },
'@

function New-WillowFeature([int]$baseHeight, [int]$heightRand, [double]$hangingProbability, [int]$maxLength, [string]$foliageRadius, [int]$leanMax) {
    return @"
{
  "type": "minecraft:tree",
$belowTrunkProvider
    "decorators": [
      {
        "type": "$ns`:willow_hanging",
        "probability": $hangingProbability,
        "max_length": $maxLength
      }
    ],
    "foliage_placer": {
      "type": "minecraft:fancy_foliage_placer",
      "height": 4,
      "offset": 4,
      "radius": $foliageRadius
    },
    "foliage_provider": {
      "type": "minecraft:simple",
      "state": {
        "id": "$ns`:willow_leaves",
        "properties": {
          "distance": "7",
          "persistent": "false",
          "waterlogged": "false"
        }
      }
    },
    "ignore_vines": true,
    "minimum_size": {
      "type": "minecraft:two_layers_feature_size",
      "limit": 0,
      "min_clipped_height": 4,
      "upper_size": 0
    },
    "trunk_placer": {
      "type": "$ns`:leaning_willow_trunk_placer",
      "base_height": $baseHeight,
      "height_rand_a": $heightRand,
      "height_rand_b": 0,
      "lean_length": {
        "type": "minecraft:uniform",
        "min_inclusive": 1,
        "max_inclusive": $leanMax
      },
      "search_radius": 7
    },
    "trunk_provider": {
      "type": "minecraft:simple",
      "state": {
        "id": "$ns`:willow_log",
        "properties": {
          "axis": "y"
        }
      }
    }
}
"@
}

# The drooping willow strand density was deliberately reduced: fewer strands, each shorter.
Write-Json "data/$ns/worldgen/feature/willow.json" (New-WillowFeature 5 2 0.18 3 3 3)
Write-Json "data/$ns/worldgen/feature/tall_willow.json" (New-WillowFeature 8 2 0.28 3 3 4)

Write-Json "data/$ns/worldgen/placed_feature/willow_river.json" @"
{
  "feature": "$ns`:willow",
  "placement": [
    {
      "type": "minecraft:count",
      "count": {
        "type": "minecraft:weighted_list",
        "distribution": [
          {
            "data": 3,
            "weight": 3
          },
          {
            "data": 5,
            "weight": 1
          }
        ]
      }
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:surface_water_depth_filter",
      "max_water_depth": 0
    },
    {
      "type": "minecraft:heightmap",
      "heightmap": "OCEAN_FLOOR"
    },
    {
      "type": "minecraft:block_predicate_filter",
      "predicate": {
        "type": "minecraft:would_survive",
        "state": {
          "id": "$ns`:willow_sapling",
          "properties": {
            "stage": "0"
          }
        }
      }
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
"@

# ---------------------------------------------------------------------------- data: rock salt ore

# A simple underground ore: scattered through the overworld between y=20 and y=90, in small
# clusters, no deeper than vanilla's iron.
Write-Json "data/$ns/worldgen/feature/rock_salt_ore.json" @"
{
  "type": "minecraft:ore",
  "discard_chance_on_air_exposure": 0.0,
  "size": 7,
  "targets": [
    {
      "state": "$ns`:rock_salt_ore",
      "target": {
        "predicate_type": "minecraft:tag_match",
        "tag": "minecraft:stone_ore_replaceables"
      }
    },
    {
      "state": "$ns`:rock_salt_ore",
      "target": {
        "predicate_type": "minecraft:tag_match",
        "tag": "minecraft:deepslate_ore_replaceables"
      }
    }
  ]
}
"@

Write-Json "data/$ns/worldgen/placed_feature/rock_salt_ore.json" @"
{
  "feature": "$ns`:rock_salt_ore",
  "placement": [
    {
      "type": "minecraft:count",
      "count": 6
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:trapezoid",
        "max_inclusive": {
          "absolute": 90
        },
        "min_inclusive": {
          "absolute": 20
        }
      }
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
"@

# NOTE: the ore is injected into the overworld from OutbreakWorldGen.kt, not from a data file -
# Fabric's biome modification API is code-only, so a worldgen/biome_modification JSON would be
# silently ignored.

# ---------------------------------------------------------------------------- data: loot tables

$simpleLoot = @(
    'oak_log', 'oak_wood', 'stripped_oak_log', 'stripped_oak_wood', 'oak_planks',
    'oak_stairs', 'oak_slab', 'oak_fence', 'oak_fence_gate', 'oak_door', 'oak_trapdoor',
    'oak_pressure_plate', 'oak_button', 'oak_shelf', 'oak_sign', 'oak_hanging_sign',
    'oak_sapling', 'potted_oak_sapling', 'oak_leaves'
)
foreach ($name in $simpleLoot) {
    $body = Convert-OakLoot (Get-Vanilla "data/minecraft/loot_table/blocks/$name.json")
    if ($name -eq 'oak_leaves') {
        # Willows do not bear apples.
        $json = $body | ConvertFrom-Json
        $json.pools = @($json.pools | Where-Object {
            -not ($_.entries | Where-Object { $_.name -eq 'minecraft:apple' })
        })
        $body = $json | ConvertTo-Json -Depth 32
    }
    Write-Json "data/$ns/loot_table/blocks/$(Convert-Name $name).json" $body
}

foreach ($pair in @(@('weeping_vines', 'willow_vines'), @('weeping_vines_plant', 'willow_vines_plant'))) {
    $body = Get-Vanilla "data/minecraft/loot_table/blocks/$($pair[0]).json"
    $body = $body -replace 'minecraft:blocks/weeping_vines', "$ns`:blocks/willow_vines"
    $body = $body -replace 'minecraft:weeping_vines', "$ns`:willow_vines"
    Write-Json "data/$ns/loot_table/blocks/$($pair[1]).json" $body
}

# Breaking the soup cauldron gives the cauldron back, exactly like a vanilla water cauldron.
Write-Json "data/$ns/loot_table/blocks/willow_soup_cauldron.json" @"
{
  "type": "minecraft:block",
  "pools": [
    {
      "condition": {
        "type": "minecraft:survives_explosion"
      },
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:cauldron"
        }
      ],
      "rolls": 1.0
    }
  ],
  "random_sequence": "$ns`:blocks/willow_soup_cauldron"
}
"@

# Rock salt ore drops itself, because the grindstone grinds the ore block rather than a drop.
Write-Json "data/$ns/loot_table/blocks/rock_salt_ore.json" @"
{
  "type": "minecraft:block",
  "pools": [
    {
      "condition": {
        "type": "minecraft:survives_explosion"
      },
      "entries": [
        {
          "type": "minecraft:item",
          "name": "$ns`:rock_salt_ore"
        }
      ],
      "rolls": 1.0
    }
  ],
  "random_sequence": "$ns`:blocks/rock_salt_ore"
}
"@

Write-Json "data/$ns/loot_table/blocks/brine_cauldron.json" @"
{
  "type": "minecraft:block",
  "pools": [
    {
      "condition": {
        "type": "minecraft:survives_explosion"
      },
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:cauldron"
        }
      ],
      "rolls": 1.0
    }
  ],
  "random_sequence": "$ns`:blocks/brine_cauldron"
}
"@

# ---------------------------------------------------------------------------- data: recipes

function Write-Recipe([string]$name, [string]$body) { Write-Json "data/$ns/recipe/$name.json" $body }

# Salted versions of the drinks and soups. One salt plus one base item, in either hand order.
function Write-SaltRecipe([string]$name, [string]$salt, [string]$base, [string]$result) {
    Write-Recipe $name @"
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    "$ns`:$salt",
    "$base"
  ],
  "result": {
    "count": 1,
    "id": "$ns`:$result"
  }
}
"@
}

$waterBottle = 'minecraft:potion'

# The stirring rod is two sticks stacked vertically.
Write-Recipe 'stirring_rod' @"
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "key": {
    "X": "minecraft:stick"
  },
  "pattern": [
    "X",
    "X"
  ],
  "result": {
    "count": 1,
    "id": "$ns`:stirring_rod"
  }
}
"@
Write-SaltRecipe 'crude_salt_water' 'crude_salt' $waterBottle 'crude_salt_water'
Write-SaltRecipe 'salt_water' 'salt_powder' $waterBottle 'salt_water'
Write-SaltRecipe 'crude_salt_swamp_water' 'crude_salt' "$ns`:swamp_water_bottle" 'crude_salt_swamp_water'
Write-SaltRecipe 'salt_swamp_water' 'salt_powder' "$ns`:swamp_water_bottle" 'salt_swamp_water'
Write-SaltRecipe 'crude_salt_sea_water' 'crude_salt' "$ns`:sea_water_bottle" 'crude_salt_sea_water'
Write-SaltRecipe 'salt_sea_water' 'salt_powder' "$ns`:sea_water_bottle" 'salt_sea_water'
Write-SaltRecipe 'crude_salt_mushroom_stew' 'crude_salt' 'minecraft:mushroom_stew' 'crude_salt_mushroom_stew'
Write-SaltRecipe 'salt_mushroom_stew' 'salt_powder' 'minecraft:mushroom_stew' 'salt_mushroom_stew'
Write-SaltRecipe 'crude_salt_willow_bark_soup' 'crude_salt' "$ns`:willow_bark_soup_bowl" 'crude_salt_willow_bark_soup'
Write-SaltRecipe 'salt_willow_bark_soup' 'salt_powder' "$ns`:willow_bark_soup_bowl" 'salt_willow_bark_soup'
Write-SaltRecipe 'crude_salt_raw_willow_bark_soup' 'crude_salt' "$ns`:raw_willow_bark_soup_bowl" 'crude_salt_raw_willow_bark_soup'
Write-SaltRecipe 'salt_raw_willow_bark_soup' 'salt_powder' "$ns`:raw_willow_bark_soup_bowl" 'salt_raw_willow_bark_soup'

Write-Recipe 'willow_planks' @"
{
  "type": "minecraft:crafting_shapeless",
  "category": "building",
  "group": "planks",
  "ingredients": [
    "#$ns`:willow_logs"
  ],
  "result": {
    "count": 4,
    "id": "$ns`:willow_planks"
  }
}
"@

Write-Recipe 'willow_wood' @"
{
  "type": "minecraft:crafting_shaped",
  "category": "building",
  "group": "bark",
  "key": {
    "#": "$ns`:willow_log"
  },
  "pattern": [
    "##",
    "##"
  ],
  "result": {
    "count": 3,
    "id": "$ns`:willow_wood"
  }
}
"@

Write-Recipe 'stripped_willow_wood' @"
{
  "type": "minecraft:crafting_shaped",
  "category": "building",
  "group": "bark",
  "key": {
    "#": "$ns`:stripped_willow_log"
  },
  "pattern": [
    "##",
    "##"
  ],
  "result": {
    "count": 3,
    "id": "$ns`:stripped_willow_wood"
  }
}
"@

Write-Recipe 'willow_stairs' @"
{
  "type": "minecraft:crafting_shaped",
  "category": "building",
  "group": "wooden_stairs",
  "key": {
    "#": "$ns`:willow_planks"
  },
  "pattern": [
    "#  ",
    "## ",
    "###"
  ],
  "result": {
    "count": 4,
    "id": "$ns`:willow_stairs"
  }
}
"@

Write-Recipe 'willow_slab' @"
{
  "type": "minecraft:crafting_shaped",
  "category": "building",
  "group": "wooden_slab",
  "key": {
    "#": "$ns`:willow_planks"
  },
  "pattern": [
    "###"
  ],
  "result": {
    "count": 6,
    "id": "$ns`:willow_slab"
  }
}
"@

Write-Recipe 'willow_fence' @"
{
  "type": "minecraft:crafting_shaped",
  "group": "wooden_fence",
  "key": {
    "#": "minecraft:stick",
    "W": "$ns`:willow_planks"
  },
  "pattern": [
    "W#W",
    "W#W"
  ],
  "result": {
    "count": 3,
    "id": "$ns`:willow_fence"
  }
}
"@

Write-Recipe 'willow_fence_gate' @"
{
  "type": "minecraft:crafting_shaped",
  "category": "redstone",
  "group": "wooden_fence_gate",
  "key": {
    "#": "minecraft:stick",
    "W": "$ns`:willow_planks"
  },
  "pattern": [
    "#W#",
    "#W#"
  ],
  "result": {
    "id": "$ns`:willow_fence_gate"
  }
}
"@

Write-Recipe 'willow_door' @"
{
  "type": "minecraft:crafting_shaped",
  "category": "redstone",
  "group": "wooden_door",
  "key": {
    "#": "$ns`:willow_planks"
  },
  "pattern": [
    "##",
    "##",
    "##"
  ],
  "result": {
    "count": 3,
    "id": "$ns`:willow_door"
  }
}
"@

Write-Recipe 'willow_trapdoor' @"
{
  "type": "minecraft:crafting_shaped",
  "category": "redstone",
  "group": "wooden_trapdoor",
  "key": {
    "#": "$ns`:willow_planks"
  },
  "pattern": [
    "###",
    "###"
  ],
  "result": {
    "count": 2,
    "id": "$ns`:willow_trapdoor"
  }
}
"@

Write-Recipe 'willow_pressure_plate' @"
{
  "type": "minecraft:crafting_shaped",
  "category": "redstone",
  "group": "wooden_pressure_plate",
  "key": {
    "#": "$ns`:willow_planks"
  },
  "pattern": [
    "##"
  ],
  "result": {
    "id": "$ns`:willow_pressure_plate"
  }
}
"@

Write-Recipe 'willow_button' @"
{
  "type": "minecraft:crafting_shapeless",
  "category": "redstone",
  "group": "wooden_button",
  "ingredients": [
    "$ns`:willow_planks"
  ],
  "result": {
    "id": "$ns`:willow_button"
  }
}
"@

Write-Recipe 'willow_sign' @"
{
  "type": "minecraft:crafting_shaped",
  "group": "wooden_sign",
  "key": {
    "#": "$ns`:willow_planks",
    "X": "minecraft:stick"
  },
  "pattern": [
    "###",
    "###",
    " X "
  ],
  "result": {
    "count": 3,
    "id": "$ns`:willow_sign"
  }
}
"@

Write-Recipe 'willow_hanging_sign' @"
{
  "type": "minecraft:crafting_shaped",
  "group": "wooden_hanging_sign",
  "key": {
    "#": "$ns`:stripped_willow_log",
    "X": "minecraft:iron_chain"
  },
  "pattern": [
    "X X",
    "###",
    "###"
  ],
  "result": {
    "count": 6,
    "id": "$ns`:willow_hanging_sign"
  }
}
"@

Write-Recipe 'willow_shelf' @"
{
  "type": "minecraft:crafting_shaped",
  "group": "shelf",
  "key": {
    "#": "$ns`:stripped_willow_log"
  },
  "pattern": [
    "###",
    "   ",
    "###"
  ],
  "result": {
    "count": 6,
    "id": "$ns`:willow_shelf"
  }
}
"@

Write-Recipe 'willow_boat' @"
{
  "type": "minecraft:crafting_shaped",
  "group": "boat",
  "key": {
    "#": "$ns`:willow_planks"
  },
  "pattern": [
    "# #",
    "###"
  ],
  "result": {
    "id": "$ns`:willow_boat"
  }
}
"@

Write-Recipe 'willow_chest_boat' @"
{
  "type": "minecraft:crafting_shapeless",
  "group": "chest_boat",
  "ingredients": [
    "minecraft:chest",
    "$ns`:willow_boat"
  ],
  "result": {
    "id": "$ns`:willow_chest_boat"
  }
}
"@

# ---------------------------------------------------------------------------- data: tags

Write-Json "data/$ns/tags/block/willow_logs.json" @"
{
  "values": [
    "$ns`:willow_log",
    "$ns`:willow_wood",
    "$ns`:stripped_willow_log",
    "$ns`:stripped_willow_wood"
  ]
}
"@
Write-Json "data/$ns/tags/item/willow_logs.json" @"
{
  "values": [
    "$ns`:willow_log",
    "$ns`:willow_wood",
    "$ns`:stripped_willow_log",
    "$ns`:stripped_willow_wood"
  ]
}
"@

function Write-VanillaTag([string]$kind, [string]$file, [string[]]$values) {
    $lines = @()
    foreach ($v in $values) { $lines += "    `"$v`"" }
    $body = "{`n  `"values`": [`n" + ($lines -join ",`n") + "`n  ]`n}`n"
    Write-Json "data/minecraft/tags/$kind/$file" $body
}

$allWillowLogs = "#$ns`:willow_logs"

# blocks
Write-VanillaTag 'block' 'planks.json'                    @("$ns`:willow_planks")
Write-VanillaTag 'block' 'logs_that_burn.json'            @($allWillowLogs)
Write-VanillaTag 'block' 'leaves.json'                    @("$ns`:willow_leaves")
Write-VanillaTag 'block' 'saplings.json'                  @("$ns`:willow_sapling")
Write-VanillaTag 'block' 'flower_pots.json'               @("$ns`:potted_willow_sapling")
Write-VanillaTag 'block' 'wooden_stairs.json'             @("$ns`:willow_stairs")
Write-VanillaTag 'block' 'wooden_slabs.json'              @("$ns`:willow_slab")
Write-VanillaTag 'block' 'wooden_fences.json'             @("$ns`:willow_fence")
Write-VanillaTag 'block' 'fence_gates.json'               @("$ns`:willow_fence_gate")
Write-VanillaTag 'block' 'wooden_doors.json'              @("$ns`:willow_door")
Write-VanillaTag 'block' 'wooden_trapdoors.json'          @("$ns`:willow_trapdoor")
Write-VanillaTag 'block' 'wooden_pressure_plates.json'    @("$ns`:willow_pressure_plate")
Write-VanillaTag 'block' 'wooden_buttons.json'            @("$ns`:willow_button")
Write-VanillaTag 'block' 'wooden_shelves.json'            @("$ns`:willow_shelf")
Write-VanillaTag 'block' 'standing_signs.json'            @("$ns`:willow_sign")
Write-VanillaTag 'block' 'wall_signs.json'                @("$ns`:willow_wall_sign")
Write-VanillaTag 'block' 'ceiling_hanging_signs.json'     @("$ns`:willow_hanging_sign")
Write-VanillaTag 'block' 'wall_hanging_signs.json'        @("$ns`:willow_wall_hanging_sign")
Write-VanillaTag 'block' 'mineable/axe.json'              @("$ns`:willow_vines", "$ns`:willow_vines_plant")
Write-VanillaTag 'block' 'mineable/pickaxe.json'          @("$ns`:rock_salt_ore", "$ns`:willow_soup_cauldron", "$ns`:brine_cauldron")
Write-VanillaTag 'block' 'needs_stone_tool.json'          @("$ns`:rock_salt_ore")

# items
Write-VanillaTag 'item' 'planks.json'                     @("$ns`:willow_planks")
Write-VanillaTag 'item' 'logs_that_burn.json'             @($allWillowLogs)
Write-VanillaTag 'item' 'leaves.json'                     @("$ns`:willow_leaves")
Write-VanillaTag 'item' 'saplings.json'                   @("$ns`:willow_sapling")
Write-VanillaTag 'item' 'wooden_stairs.json'              @("$ns`:willow_stairs")
Write-VanillaTag 'item' 'wooden_slabs.json'               @("$ns`:willow_slab")
Write-VanillaTag 'item' 'wooden_fences.json'              @("$ns`:willow_fence")
Write-VanillaTag 'item' 'fence_gates.json'                @("$ns`:willow_fence_gate")
Write-VanillaTag 'item' 'wooden_doors.json'               @("$ns`:willow_door")
Write-VanillaTag 'item' 'wooden_trapdoors.json'           @("$ns`:willow_trapdoor")
Write-VanillaTag 'item' 'wooden_pressure_plates.json'     @("$ns`:willow_pressure_plate")
Write-VanillaTag 'item' 'wooden_buttons.json'             @("$ns`:willow_button")
Write-VanillaTag 'item' 'wooden_shelves.json'             @("$ns`:willow_shelf")
Write-VanillaTag 'item' 'signs.json'                      @("$ns`:willow_sign")
Write-VanillaTag 'item' 'hanging_signs.json'              @("$ns`:willow_hanging_sign")
Write-VanillaTag 'item' 'boats.json'                      @("$ns`:willow_boat")
Write-VanillaTag 'item' 'chest_boats.json'                @("$ns`:willow_chest_boat")

# entities
Write-VanillaTag 'entity_type' 'boat.json'                @("$ns`:willow_boat", "$ns`:willow_chest_boat")

# ---------------------------------------------------------------------------- mandrake

# The mandrake is the mod's own plant, so there is no oak to rename: the vanilla sweet berry bush is
# the template instead. It is the one vanilla plant that behaves the way a mandrake has to - four
# ages driven by random ticks, bone meal, growth on soil rather than on farmland, and fruit that only
# the last age gives up.
function Convert-BerryAsset([string]$text) {
    return ($text -replace 'minecraft:block/sweet_berry_bush_stage', "$ns`:block/mandrake_stage")
}

Write-Json "assets/$ns/blockstates/mandrake.json" `
    (Convert-BerryAsset (Get-Vanilla "assets/minecraft/blockstates/sweet_berry_bush.json"))

foreach ($stage in 0..3) {
    Write-Json "assets/$ns/models/block/mandrake_stage$stage.json" `
        (Convert-BerryAsset (Get-Vanilla "assets/minecraft/models/block/sweet_berry_bush_stage$stage.json"))
}

foreach ($mandrakeItem in @('mandrake_fruit', 'mandrake_seeds')) {
    $itemModel = (Get-Vanilla "assets/minecraft/models/item/sweet_berries.json") `
        -replace 'minecraft:item/sweet_berries', "$ns`:item/$mandrakeItem"
    Write-Json "assets/$ns/models/item/$mandrakeItem.json" $itemModel

    $itemDefinition = (Get-Vanilla "assets/minecraft/items/sweet_berries.json") `
        -replace 'minecraft:item/sweet_berries', "$ns`:item/$mandrakeItem"
    Write-Json "assets/$ns/items/$mandrakeItem.json" $itemDefinition
}

# One pool, gated on the fully grown state: 1-2 fruit from a ripe plant and nothing at all before it.
# Fortune applies, like every other harvest in the game.
Write-Json "data/$ns/loot_table/blocks/mandrake.json" @"
{
  "type": "minecraft:block",
  "modifier": {
    "type": "minecraft:explosion_decay"
  },
  "pools": [
    {
      "condition": {
        "type": "minecraft:match_block",
        "blocks": "$ns`:mandrake",
        "state": {
          "age": "3"
        }
      },
      "entries": [
        {
          "type": "minecraft:item",
          "name": "$ns`:mandrake_fruit"
        }
      ],
      "modifier": [
        {
          "type": "minecraft:set_count",
          "count": {
            "type": "minecraft:uniform",
            "max": 2,
            "min": 1
          }
        },
        {
          "type": "minecraft:apply_bonus",
          "enchantment": "minecraft:fortune",
          "formula": "minecraft:uniform_bonus_count",
          "parameters": {
            "bonusMultiplier": 1
          }
        }
      ],
      "rolls": 1
    }
  ],
  "random_sequence": "$ns`:blocks/mandrake"
}
"@

# The seeds are taken out of the fruit, so they are a crafting action rather than a drop.
Write-Json "data/$ns/recipe/mandrake_seeds.json" @"
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    "$ns`:mandrake_fruit"
  ],
  "result": {
    "count": 2,
    "id": "$ns`:mandrake_seeds"
  }
}
"@

# Wild mandrakes. The patch is placed on grass and left at full age, exactly like vanilla's berry
# bush patches: a plant the player walks up to and picks, rather than one that has to be waited for.
Write-Json "data/$ns/worldgen/feature/mandrake.json" @"
{
  "type": "minecraft:simple_block",
  "to_place": {
    "id": "$ns`:mandrake",
    "properties": {
      "age": "3"
    }
  }
}
"@

Write-Json "data/$ns/worldgen/placed_feature/mandrake_patch.json" @"
{
  "feature": "$ns`:mandrake",
  "placement": [
    {
      "type": "minecraft:count",
      "count": 6
    },
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:heightmap",
      "heightmap": "WORLD_SURFACE_WG"
    },
    {
      "type": "minecraft:biome"
    },
    {
      "type": "minecraft:block_predicate_filter",
      "predicate": {
        "type": "minecraft:all_of",
        "predicates": [
          {
            "type": "minecraft:matching_block_tag",
            "tag": "minecraft:air"
          },
          {
            "type": "minecraft:matching_blocks",
            "blocks": "minecraft:grass_block",
            "offset": [
              0,
              -1,
              0
            ]
          }
        ]
      }
    }
  ]
}
"@

Write-Host "gen_data.ps1 wrote $script:written JSON files into src/main/resources"
