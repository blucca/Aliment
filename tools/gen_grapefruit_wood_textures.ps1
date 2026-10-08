<#
    gen_grapefruit_wood_textures.ps1 - the grapefruit wood set's textures.

    A new wood type is the *same shapes in a different colour*, so this does not redraw anything: it
    reads the willow's wood-family textures and maps their palette onto the grapefruit one, pixel for
    pixel, alpha untouched. That is what keeps the door panels, the trapdoor slats, the sign frames
    and the shelf planks identical in form to the rest of the mod's carpentry - the only thing that
    changes is which wood they look like.

    The two files it does NOT own are `block/grapefruit_log.png` and `block/grapefruit_log_top.png`:
    those are the tree's own bark, drawn by `gen_grapefruit_textures.ps1`, and the block, the world
    generation and the sapling already use them. Everything else in the set is mirrored from the
    willow.

    Leaves and the sapling are not here either - they are foliage rather than wood, they are already
    hand-drawn, and the leaves are greyscale because the biome tints them.

    Run with (from the repository root):
        pwsh -ExecutionPolicy Bypass -File tools\gen_grapefruit_wood_textures.ps1

    Idempotent: every target is rewritten from its willow source on every run, and the palette is a
    fixed table rather than a formula.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$tex = Join-Path $root 'src\main\resources\assets\aliment\textures'

<#
    The willow palette, and what each colour becomes.

    The willow is a cool olive-brown; the grapefruit is the same lightness with the green and blue
    pulled down, which lands on a warm red-brown. Luminance order is preserved exactly, so the
    shading - which is all the art actually encodes - survives the change, and the two woods stay
    distinguishable at a glance.

    Every colour here was read off the willow textures themselves; the generator fails loudly if it
    meets one that is not in the table, so a redrawn source cannot silently pass through unchanged.
#>
$map = @{
    '#3A2A16' = '#3A2418'   # the darkest outline
    '#4A4433' = '#4A3226'   # bark, the dominant dark
    '#5F5741' = '#5F4230'
    '#6B4F2A' = '#6B4128'
    '#72684F' = '#72523A'
    '#776D53' = '#77573E'
    '#7A7055' = '#7A5A40'
    '#7C7155' = '#7C5B41'
    '#7D7255' = '#7D5C42'
    '#807555' = '#805E44'
    '#817555' = '#815E44'
    '#887A55' = '#886448'
    '#8B6A3F' = '#8B5636'
    '#8B7D55' = '#8B6749'
    '#8E7F55' = '#8E694B'   # planks, the dominant light
    '#928358' = '#926C4D'
    '#95865B' = '#956F50'
    '#9B8C60' = '#9B7554'
    '#9C8D61' = '#9C7655'
    '#A09165' = '#A07A59'
    '#A39775' = '#A3815E'
    '#A7986A' = '#A7825F'
    '#A89A6E' = '#A88461'
    '#AA9B6D' = '#AA8562'
    '#B5A576' = '#B58E6A'
    '#B8A876' = '#B8916C'
    '#BCAC7B' = '#BC9571'
    '#BDAD7C' = '#BD9672'
    '#C0B287' = '#C09B78'
    '#CDBF90' = '#CDA681'
    '#CFC293' = '#CFA983'
}

# source (relative to textures\) -> target. The tree's own bark is deliberately absent.
$targets = [ordered]@{
    'block\stripped_willow_log.png'     = 'block\stripped_grapefruit_log.png'
    'block\stripped_willow_log_top.png' = 'block\stripped_grapefruit_log_top.png'
    'block\willow_door_bottom.png'      = 'block\grapefruit_door_bottom.png'
    'block\willow_door_top.png'         = 'block\grapefruit_door_top.png'
    'block\willow_hanging_sign.png'     = 'block\grapefruit_hanging_sign.png'
    'block\willow_planks.png'           = 'block\grapefruit_planks.png'
    'block\willow_shelf.png'            = 'block\grapefruit_shelf.png'
    'block\willow_sign.png'             = 'block\grapefruit_sign.png'
    'block\willow_trapdoor.png'         = 'block\grapefruit_trapdoor.png'
    'item\willow_boat.png'              = 'item\grapefruit_boat.png'
    'item\willow_chest_boat.png'        = 'item\grapefruit_chest_boat.png'
    'item\willow_door.png'              = 'item\grapefruit_door.png'
    'item\willow_hanging_sign.png'      = 'item\grapefruit_hanging_sign.png'
    'item\willow_sign.png'              = 'item\grapefruit_sign.png'
    'entity\boat\willow.png'            = 'entity\boat\grapefruit.png'
    'entity\chest_boat\willow.png'      = 'entity\chest_boat\grapefruit.png'
}

$unmapped = @{}
$written = 0

foreach ($source in $targets.Keys) {
    $from = Join-Path $tex $source
    $to = Join-Path $tex $targets[$source]
    if (-not (Test-Path $from)) { throw "missing willow source $source" }

    $dir = Split-Path -Parent $to
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

    $src = [System.Drawing.Bitmap]::FromFile($from)
    $dst = New-Object System.Drawing.Bitmap($src.Width, $src.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        for ($y = 0; $y -lt $src.Height; $y++) {
            for ($x = 0; $x -lt $src.Width; $x++) {
                $px = $src.GetPixel($x, $y)
                if ($px.A -eq 0) {
                    # Fully transparent pixels keep their colour out of it: nothing draws them, and
                    # rewriting them would only add noise to the diff.
                    $dst.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                    continue
                }
                $key = '#{0:X2}{1:X2}{2:X2}' -f $px.R, $px.G, $px.B
                if (-not $map.ContainsKey($key)) {
                    if (-not $unmapped.ContainsKey($key)) { $unmapped[$key] = 0 }
                    $unmapped[$key]++
                    $dst.SetPixel($x, $y, $px)
                    continue
                }
                $hex = $map[$key]
                $dst.SetPixel(
                    $x, $y,
                    [System.Drawing.Color]::FromArgb(
                        $px.A,
                        [Convert]::ToInt32($hex.Substring(1, 2), 16),
                        [Convert]::ToInt32($hex.Substring(3, 2), 16),
                        [Convert]::ToInt32($hex.Substring(5, 2), 16)))
            }
        }
        $dst.Save([System.IO.Path]::GetFullPath($to), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $src.Dispose()
        $dst.Dispose()
    }
    $written++
}

if ($unmapped.Count -gt 0) {
    $list = ($unmapped.Keys | Sort-Object) -join ', '
    throw "the willow palette has colours this table does not know: $list"
}

Write-Host "recoloured $written wood-set textures into $tex"
