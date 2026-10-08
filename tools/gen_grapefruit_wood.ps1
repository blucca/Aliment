<#
    gen_grapefruit_wood.ps1 - the grapefruit wood set's data and asset JSON.

    The grapefruit tree started life as "a citrus that carries fruit and nothing else", and this
    script is how it grew the rest of a wood set: every file it writes is the willow's own file with
    `willow` swapped for `grapefruit`. The two sets are meant to be structurally identical, so
    mirroring is both the fastest way to add one and the most honest description of it - there is no
    second template to keep in step.

    What it owns: the blockstates, block models, item models, item definitions, recipes and block
    loot tables of the wood set, plus the `aliment:grapefruit_logs` tags and the vanilla tag
    entries. It does NOT touch the fruit itself - `grapefruit`, `grapefruit_slice`,
    `grapefruit_leaves`, `grapefruit_log`, `grapefruit_sapling` and their textures are hand-written,
    and the exclusion list below keeps them that way. Re-running it therefore refreshes the mirrored
    files and leaves every hand-tuned one alone.

    Run with (from the repository root):
        pwsh -ExecutionPolicy Bypass -File tools\gen_grapefruit_wood.ps1

    Idempotent: every mirrored target is rewritten from its willow source, and a tag entry that is
    already present is not added twice.
#>

$ErrorActionPreference = 'Stop'

$root = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$res = Join-Path $root 'src\main\resources'
$enc = New-Object System.Text.UTF8Encoding($false)

# Files whose names match this are the willow's vines, its bark and its soup cauldron - not part of
# the wood set, and with no grapefruit equivalent.
$exclude = 'vines|soup|bark'

# `*willow*` and not `willow*`: the set also contains `stripped_willow_log`, `stripped_willow_wood`
# and `potted_willow_sapling`, and a leading-anchored pattern skips all three in silence - which is
# exactly the bug this pattern replaced.
$match = '*willow*.json'

# Every directory that holds a mirrored category. The willow files in each are copied across.
$categories = @(
    'assets\aliment\blockstates',
    'assets\aliment\models\block',
    'assets\aliment\models\item',
    'assets\aliment\items',
    'data\aliment\recipe',
    'data\aliment\loot_table\blocks'
)

$written = 0
foreach ($category in $categories) {
    $dir = Join-Path $res $category
    if (-not (Test-Path $dir)) { continue }
    foreach ($src in (Get-ChildItem $dir -File -Filter $match)) {
        if ($src.BaseName -match $exclude) { continue }

        $target = Join-Path $dir ($src.Name -replace 'willow', 'grapefruit')
        $text = [System.IO.File]::ReadAllText($src.FullName, [System.Text.Encoding]::UTF8)
        $text = $text -replace 'willow', 'grapefruit'

        # Only rewrite when something actually changed, so the mtimes of untouched files stay put.
        if ((Test-Path $target) -and ([System.IO.File]::ReadAllText($target, [System.Text.Encoding]::UTF8) -ceq $text)) {
            continue
        }
        [System.IO.File]::WriteAllText($target, $text, $enc)
        $written++
    }
}
Write-Host "mirrored $written wood-set files from the willow set"

# --------------------------------------------------------------------- the logs tag
# Willow keeps its four log-shaped blocks in `aliment:willow_logs` and refers to that tag from the
# vanilla ones, so a recipe that takes "#aliment:willow_logs" means all four. Grapefruit gets the
# same tag for the same reason.
$logValues = @(
    'aliment:grapefruit_log',
    'aliment:grapefruit_wood',
    'aliment:stripped_grapefruit_log',
    'aliment:stripped_grapefruit_wood'
)
$logBody = "{`r`n  `"values`": [`r`n" +
    (($logValues | ForEach-Object { "    `"$_`"" }) -join ",`r`n") +
    "`r`n  ]`r`n}`r`n"
foreach ($side in @('block', 'item')) {
    $p = Join-Path $res "data\aliment\tags\$side\grapefruit_logs.json"
    $dir = Split-Path -Parent $p
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    [System.IO.File]::WriteAllText($p, $logBody, $enc)
}
Write-Host "wrote aliment:grapefruit_logs (block + item)"

# --------------------------------------------------------------------- vanilla tags
# Each entry here is one vanilla tag the grapefruit now belongs in. The vanilla tags already put
# `#minecraft:logs`, `#minecraft:planks` and the `wooden_*` families into `mineable/axe`, so joining
# those is what makes the whole set axe-mineable - the mod does not list its own wood there.
$tagAdditions = [ordered]@{
    'data\minecraft\tags\block\ceiling_hanging_signs.json' = @('aliment:grapefruit_hanging_sign')
    'data\minecraft\tags\block\fence_gates.json'           = @('aliment:grapefruit_fence_gate')
    'data\minecraft\tags\block\flower_pots.json'           = @('aliment:potted_grapefruit_sapling')
    'data\minecraft\tags\block\logs_that_burn.json'        = @('#aliment:grapefruit_logs')
    'data\minecraft\tags\block\planks.json'                = @('aliment:grapefruit_planks')
    'data\minecraft\tags\block\standing_signs.json'        = @('aliment:grapefruit_sign')
    'data\minecraft\tags\block\wall_hanging_signs.json'    = @('aliment:grapefruit_wall_hanging_sign')
    'data\minecraft\tags\block\wall_signs.json'            = @('aliment:grapefruit_wall_sign')
    'data\minecraft\tags\block\wooden_buttons.json'        = @('aliment:grapefruit_button')
    'data\minecraft\tags\block\wooden_doors.json'          = @('aliment:grapefruit_door')
    'data\minecraft\tags\block\wooden_fences.json'         = @('aliment:grapefruit_fence')
    'data\minecraft\tags\block\wooden_pressure_plates.json' = @('aliment:grapefruit_pressure_plate')
    'data\minecraft\tags\block\wooden_shelves.json'        = @('aliment:grapefruit_shelf')
    'data\minecraft\tags\block\wooden_slabs.json'          = @('aliment:grapefruit_slab')
    'data\minecraft\tags\block\wooden_stairs.json'         = @('aliment:grapefruit_stairs')
    'data\minecraft\tags\block\wooden_trapdoors.json'      = @('aliment:grapefruit_trapdoor')

    'data\minecraft\tags\item\boats.json'                  = @('aliment:grapefruit_boat')
    'data\minecraft\tags\item\chest_boats.json'            = @('aliment:grapefruit_chest_boat')
    'data\minecraft\tags\item\fence_gates.json'            = @('aliment:grapefruit_fence_gate')
    'data\minecraft\tags\item\hanging_signs.json'          = @('aliment:grapefruit_hanging_sign')
    'data\minecraft\tags\item\logs_that_burn.json'         = @('#aliment:grapefruit_logs')
    'data\minecraft\tags\item\planks.json'                 = @('aliment:grapefruit_planks')
    'data\minecraft\tags\item\signs.json'                  = @('aliment:grapefruit_sign')
    'data\minecraft\tags\item\wooden_buttons.json'         = @('aliment:grapefruit_button')
    'data\minecraft\tags\item\wooden_doors.json'           = @('aliment:grapefruit_door')
    'data\minecraft\tags\item\wooden_fences.json'          = @('aliment:grapefruit_fence')
    'data\minecraft\tags\item\wooden_pressure_plates.json' = @('aliment:grapefruit_pressure_plate')
    'data\minecraft\tags\item\wooden_shelves.json'         = @('aliment:grapefruit_shelf')
    'data\minecraft\tags\item\wooden_slabs.json'           = @('aliment:grapefruit_slab')
    'data\minecraft\tags\item\wooden_stairs.json'          = @('aliment:grapefruit_stairs')
    'data\minecraft\tags\item\wooden_trapdoors.json'       = @('aliment:grapefruit_trapdoor')

    'data\minecraft\tags\entity_type\boat.json'            = @('aliment:grapefruit_boat', 'aliment:grapefruit_chest_boat')
}

$tagged = 0
foreach ($relative in $tagAdditions.Keys) {
    $path = Join-Path $res $relative
    if (-not (Test-Path $path)) { Write-Warning "missing tag $relative"; continue }
    $text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

    $add = ''
    foreach ($value in $tagAdditions[$relative]) {
        if ($text -match ('"' + [regex]::Escape($value) + '"')) { continue }
        $add += ",`r`n    `"$value`""
    }
    if ($add -eq '') { continue }

    # Inserted just before the closing bracket, which is what keeps the file's own indentation.
    $close = $text.LastIndexOf(']')
    if ($close -lt 0) { throw "no closing bracket in $relative" }
    $text = $text.Substring(0, $close) + $add + "`r`n  " + $text.Substring($close)
    [System.IO.File]::WriteAllText($path, $text, $enc)
    $tagged++
}
Write-Host "extended $tagged vanilla tags"
