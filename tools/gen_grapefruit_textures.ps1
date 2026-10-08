<#
    gen_grapefruit_textures.ps1 - the seven textures of the grapefruit tree.

    A focused companion to gen_textures.ps1, in the same spirit as gen_glucose_textures.ps1: same
    16x16 canvases, same palette discipline, same PNG writer, but it only ever writes the seven files
    listed at the bottom, so it can be re-run on its own after changing one of them.

    The one structural decision worth knowing before reading the rest: the hanging fruit is drawn as
    a **cross** (`minecraft:block/cross`), and the sprite puts the fruit in the *upper* half of the
    canvas with the lower half empty. A cross model spans the whole block, so drawing the fruit high
    is what makes it hang from the leaves above instead of floating in the middle of the cell.

    Leaves are greyscale on purpose, exactly like the willow's: `TintedParticleLeavesBlock` tints
    them by the biome, so any colour baked in here would be multiplied by the biome's and come out
    wrong. The sapling is not tinted and is therefore drawn in its real greens.

    Run with (from the repository root):
        pwsh -ExecutionPolicy Bypass -File tools\gen_grapefruit_textures.ps1

    Idempotent: the scatter is drawn from a fixed-seed generator and every target is rewritten from
    scratch, so two runs produce byte-identical files.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$groot = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$gBlockDir = Join-Path $groot "src\main\resources\assets\aliment\textures\block"
$gItemDir = Join-Path $groot "src\main\resources\assets\aliment\textures\item"
foreach ($d in @($gBlockDir, $gItemDir)) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }
}

# --------------------------------------------------------------------- palette
$gClear = '#00000000'

# The rind: a grapefruit is yellow-orange outside, and the blush is what separates it from a lemon.
$gRindSpec = '#F7DC8A'
$gRindHi = '#F0C255'
$gRindM = '#DDA234'
$gRindLo = '#B87C1E'
$gRindEdge = '#8A5710'
$gBlushHi = '#E08A6A'
$gBlushM = '#C96A50'
$gBlushLo = '#9E4A36'

# The flesh: pink, with the pale membranes a real cross-section shows.
$gFleshHi = '#F7A6B4'
$gFleshM = '#E8738C'
$gFleshLo = '#C24A66'
$gFleshEdge = '#93304A'
$gPith = '#FBEDE4'
$gPithLo = '#E6CFC2'

# Bark: a redder brown than the willow's grey-brown, so the two trees differ at a glance.
$gBarkHi = '#8A6244'
$gBarkM = '#6A4831'
$gBarkLo = '#4E3522'
$gBarkEdge = '#372416'

# The sapling is drawn in real greens because nothing tints it.
$gLeafHi = '#9CC46A'
$gLeafM = '#77A04C'
$gLeafLo = '#567A36'
$gStem = '#6A4831'
$gStemLo = '#4E3522'

# --------------------------------------------------------------------- helpers
function Save-GPng([string]$gpPath, $gpGrid, [int]$gpW, [int]$gpH) {
    $gpBmp = New-Object System.Drawing.Bitmap($gpW, $gpH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($gpY = 0; $gpY -lt $gpH; $gpY++) {
        for ($gpX = 0; $gpX -lt $gpW; $gpX++) {
            $gpHx = $gpGrid[$gpX, $gpY]
            $gpR = 0; $gpG = 0; $gpB = 0; $gpA = 0
            if ($gpHx -and $gpHx.StartsWith('#')) {
                $gpR = [Convert]::ToInt32($gpHx.Substring(1, 2), 16)
                $gpG = [Convert]::ToInt32($gpHx.Substring(3, 2), 16)
                $gpB = [Convert]::ToInt32($gpHx.Substring(5, 2), 16)
                $gpA = if ($gpHx.Length -ge 9) { [Convert]::ToInt32($gpHx.Substring(7, 2), 16) } else { 255 }
            }
            $gpBmp.SetPixel($gpX, $gpY, [System.Drawing.Color]::FromArgb($gpA, $gpR, $gpG, $gpB))
        }
    }
    $gpFull = [System.IO.Path]::GetFullPath($gpPath)
    $gpBmp.Save($gpFull, [System.Drawing.Imaging.ImageFormat]::Png)
    $gpBmp.Dispose()
}

# The leading comma matters: without it PowerShell unrolls the 2D array into a list of rows and every
# later read of $gg[$x, $y] comes back empty.
function New-GGrid([int]$gw, [int]$gh) {
    $gg = New-Object 'string[,]' $gw, $gh
    for ($gy = 0; $gy -lt $gh; $gy++) { for ($gx = 0; $gx -lt $gw; $gx++) { $gg[$gx, $gy] = $gClear } }
    return , $gg
}

function Set-GPx($gg, [int]$gx, [int]$gy, [string]$gc) {
    if ($gx -ge 0 -and $gy -ge 0 -and $gx -lt $gg.GetLength(0) -and $gy -lt $gg.GetLength(1)) {
        $gg[$gx, $gy] = $gc
    }
}

function Set-GRect($gg, [int]$rx, [int]$ry, [int]$rw, [int]$rh, [string]$rc) {
    for ($gy = $ry; $gy -lt ($ry + $rh); $gy++) {
        for ($gx = $rx; $gx -lt ($rx + $rw); $gx++) { Set-GPx $gg $gx $gy $rc }
    }
}

# A fixed-seed LCG, so the bark speckle is the same on every run. [long] because the multiply
# overflows a 32-bit int long before the modulus brings it back into range.
$script:gRng = [long]20261008
function Next-GRnd([int]$max) {
    $script:gRng = (($script:gRng * 1103515245) + 12345) % 2147483648
    return [int]($script:gRng % $max)
}

function Reset-GRng() { $script:gRng = [long]20261008 }

# A shaded ball. `spec` is the highlight, `edge` the outline; the band between `mid` and the rim is
# `lo`. `blush`/`blushLo`, when given, tint the lower-right third, which is what makes a grapefruit
# look like a grapefruit rather than an orange.
function New-GBall(
    $gg,
    [double]$cx, [double]$cy, [double]$rx, [double]$ry,
    [string]$spec, [string]$hi, [string]$mid, [string]$lo, [string]$edge,
    [string]$blush = '', [string]$blushLo = '') {
    for ($gy = 0; $gy -lt 16; $gy++) {
        for ($gx = 0; $gx -lt 16; $gx++) {
            $nx = (($gx + 0.5) - $cx) / $rx
            $ny = (($gy + 0.5) - $cy) / $ry
            $nd = ($nx * $nx) + ($ny * $ny)
            if ($nd -gt 1.0) { continue }

            $col = $mid
            $lx = (($gx + 0.5) - ($cx - ($rx * 0.42))) / ($rx * 1.20)
            $ly = (($gy + 0.5) - ($cy - ($ry * 0.42))) / ($ry * 1.20)
            $ld = ($lx * $lx) + ($ly * $ly)
            if ($ld -lt 0.14) { $col = $spec }
            elseif ($ld -lt 0.52) { $col = $hi }
            elseif ($nd -gt 0.84) { $col = $lo }
            if ($nd -gt 0.945) { $col = $edge }

            # The blush sits on the shaded side, so it reads as colour on the skin rather than as a
            # second highlight.
            if ($blush -ne '' -and $nx + $ny -gt 0.35) {
                if ($col -eq $mid) { $col = $blush }
                elseif ($col -eq $lo) { $col = $blushLo }
            }
            Set-GPx $gg $gx $gy $col
        }
    }
}

# ---------------------------------------------------------------------
# 1. block/grapefruit.png
#    The hanging fruit: a cross sprite, so the fruit is drawn in rows 1..9 and
#    everything below is empty. That gap is what makes it hang from the leaf.
# ---------------------------------------------------------------------
function New-GrapefruitHanging([string]$ghOut) {
    $gg = New-GGrid 16 16
    New-GBall $gg 7.5 5.2 4.4 4.2 $gRindSpec $gRindHi $gRindM $gRindLo $gRindEdge $gBlushM $gBlushLo
    # The stalk: two pixels up into the block above, which is the leaf it hangs from.
    Set-GPx $gg 7 1 $gStem
    Set-GPx $gg 8 1 $gStemLo
    Set-GPx $gg 7 0 $gStemLo
    Set-GPx $gg 8 0 $gStemLo
    Save-GPng $ghOut $gg 16 16
}

# ---------------------------------------------------------------------
# 2. item/grapefruit.png
#    The same fruit as a flat inventory sprite: bigger, centred, with a leaf.
# ---------------------------------------------------------------------
function New-GrapefruitItem([string]$giOut) {
    $gg = New-GGrid 16 16
    New-GBall $gg 7.5 8.4 6.3 6.0 $gRindSpec $gRindHi $gRindM $gRindLo $gRindEdge $gBlushM $gBlushLo
    # A short stalk and two leaves, which is what tells a fruit from a vegetable at a glance.
    Set-GPx $gg 7 2 $gStem
    Set-GPx $gg 8 2 $gStemLo
    Set-GPx $gg 6 1 $gLeafM
    Set-GPx $gg 5 1 $gLeafLo
    Set-GPx $gg 4 2 $gLeafLo
    Set-GPx $gg 9 1 $gLeafHi
    Set-GPx $gg 10 2 $gLeafM
    Set-GPx $gg 11 3 $gLeafLo
    Save-GPng $giOut $gg 16 16
}

# ---------------------------------------------------------------------
# 3. item/grapefruit_slice.png
#    A wedge: flat side up, rind along the bottom arc, pink segments divided
#    by pale membranes - the cross-section a slice actually shows.
# ---------------------------------------------------------------------
function New-GrapefruitSlice([string]$gsOut) {
    $gg = New-GGrid 16 16
    $scx = 7.5; $scy = 5.0
    $srx = 6.4; $sry = 8.4
    for ($gy = 0; $gy -lt 16; $gy++) {
        for ($gx = 0; $gx -lt 16; $gx++) {
            $nx = (($gx + 0.5) - $scx) / $srx
            $ny = (($gy + 0.5) - $scy) / $sry
            if ($ny -lt 0) { continue }                       # the wedge is the half below the cut
            $nd = ($nx * $nx) + ($ny * $ny)
            if ($nd -gt 1.0) { continue }

            # The outer band is rind; inside it is pith, then the flesh.
            $inner = ((($gx + 0.5) - $scx) / ($srx - 1.35)) * ((($gx + 0.5) - $scx) / ($srx - 1.35)) +
                ((($gy + 0.5) - $scy) / ($sry - 1.35)) * ((($gy + 0.5) - $scy) / ($sry - 1.35))
            $col = $gRindM
            if ($nd -gt 0.93) { $col = $gRindLo }
            if ($nd -gt 0.80 -and $nd -le 0.93) { $col = $gRindHi }
            if ($inner -le 1.0) {
                # Three segments, divided every third column, exactly like a cut citrus.
                $seg = [int][Math]::Floor(($gx - $scx + 12) / 3.2) % 3
                $col = $gFleshM
                if (($gx - 7) % 3 -eq 0) { $col = $gPith }
                elseif ($seg -eq 0) { $col = $gFleshHi }
                elseif ($seg -eq 2) { $col = $gFleshLo }
                if ($inner -gt 0.86) { $col = $gPithLo }
            }
            # The flat cut across the top of the wedge.
            if ($gy -le 5) { $col = $gPith }
            if ($gy -le 4) { $col = $gPithLo }
            Set-GPx $gg $gx $gy $col
        }
    }
    # A single pip, which is what a grapefruit slice is famous for.
    Set-GPx $gg 9 9 $gFleshEdge
    Set-GPx $gg 9 10 $gFleshEdge
    Save-GPng $gsOut $gg 16 16
}

# ---------------------------------------------------------------------
# 4/5. block/grapefruit_log.png and grapefruit_log_top.png
#      Vertical bark on the side; growth rings on the top, the way every
#      vanilla log is built.
# ---------------------------------------------------------------------
function New-GrapefruitLogSide([string]$glOut) {
    Reset-GRng
    $gg = New-GGrid 16 16
    for ($gx = 0; $gx -lt 16; $gx++) {
        # Each column gets one base tone, so the bark reads as vertical grain rather than noise.
        $base = $gBarkM
        $tone = Next-GRnd 3
        if ($tone -eq 0) { $base = $gBarkHi }
        elseif ($tone -eq 2) { $base = $gBarkLo }
        for ($gy = 0; $gy -lt 16; $gy++) { Set-GPx $gg $gx $gy $base }
    }
    # Short darker cracks, one or two per column, which is what stops it looking like a flat fill.
    for ($gx = 0; $gx -lt 16; $gx++) {
        $cracks = Next-GRnd 2
        for ($c = 0; $c -le $cracks; $c++) {
            $cy = Next-GRnd 16
            $len = 2 + (Next-GRnd 4)
            for ($i = 0; $i -lt $len; $i++) { Set-GPx $gg $gx ($cy + $i) $gBarkEdge }
        }
    }
    Save-GPng $glOut $gg 16 16
}

function New-GrapefruitLogTop([string]$glOut) {
    $gg = New-GGrid 16 16
    Set-GRect $gg 0 0 16 16 $gBarkM
    # Rings, drawn as the set of pixels whose max(|dx|,|dy|) is even.
    for ($gy = 0; $gy -lt 16; $gy++) {
        for ($gx = 0; $gx -lt 16; $gx++) {
            $dx = [Math]::Abs($gx - 7.5)
            $dy = [Math]::Abs($gy - 7.5)
            $ring = [Math]::Max($dx, $dy)
            $col = $gBarkLo
            if ($ring -lt 1.5) { $col = $gBarkEdge }
            elseif ([int][Math]::Floor($ring) % 2 -eq 0) { $col = $gBarkHi }
            if ($ring -gt 6.4) { $col = $gBarkHi }
            if ($ring -gt 7.3) { $col = $gBarkM }
            Set-GPx $gg $gx $gy $col
        }
    }
    Save-GPng $glOut $gg 16 16
}

# ---------------------------------------------------------------------
# 6. block/grapefruit_leaves.png
#    Greyscale and holed: the tint layer multiplies this by the biome colour,
#    so it must carry no colour of its own. About a quarter of the canvas is
#    transparent, the same density the willow's canopy uses.
# ---------------------------------------------------------------------
function New-GrapefruitLeaves([string]$glOut) {
    Reset-GRng
    $gg = New-GGrid 16 16
    $tones = @('#454545', '#515151', '#5D5D5D', '#696969')
    for ($gy = 0; $gy -lt 16; $gy++) {
        for ($gx = 0; $gx -lt 16; $gx++) {
            if ((Next-GRnd 100) -lt 25) { continue }          # the holes
            Set-GPx $gg $gx $gy $tones[(Next-GRnd 4)]
        }
    }
    # A few two-pixel holes, so the canopy lets light through in patches and not only in single
    # pixels - a uniform dither reads as noise from a distance.
    for ($i = 0; $i -lt 6; $i++) {
        $hx = Next-GRnd 15
        $hy = Next-GRnd 15
        Set-GPx $gg $hx $hy $gClear
        Set-GPx $gg ($hx + 1) $hy $gClear
        Set-GPx $gg $hx ($hy + 1) $gClear
    }
    Save-GPng $glOut $gg 16 16
}

# ---------------------------------------------------------------------
# 7. block/grapefruit_sapling.png
#    A cross sprite in real greens, because nothing tints a sapling.
# ---------------------------------------------------------------------
function New-GrapefruitSapling([string]$glOut) {
    $gg = New-GGrid 16 16
    # Stem, one pixel wide, up the middle from the soil to the crown.
    for ($gy = 5; $gy -lt 16; $gy++) { Set-GPx $gg 7 $gy $gStem }
    for ($gy = 5; $gy -lt 16; $gy++) { Set-GPx $gg 8 $gy $gStemLo }
    Set-GPx $gg 7 15 $gStemLo
    Set-GPx $gg 8 15 $gStemLo
    # Three leaf clusters, the lowest pair at the shoulders and a crown on top.
    $clusters = @(
        @(3, 9), @(4, 8), @(5, 9), @(4, 10), @(5, 10), @(6, 9),
        @(9, 9), @(10, 8), @(11, 9), @(10, 10), @(9, 10), @(12, 10),
        @(6, 4), @(7, 3), @(8, 3), @(9, 4), @(7, 2), @(8, 2), @(6, 3), @(9, 3),
        @(5, 5), @(10, 5), @(8, 5), @(7, 6)
    )
    foreach ($c in $clusters) { Set-GPx $gg $c[0] $c[1] $gLeafM }
    # Lit along the top-left of each cluster, shaded along the bottom-right.
    foreach ($c in @(@(4, 8), @(7, 2), @(8, 2), @(10, 8), @(5, 5))) { Set-GPx $gg $c[0] $c[1] $gLeafHi }
    foreach ($c in @(@(4, 10), @(6, 9), @(9, 10), @(11, 9), @(9, 4), @(10, 5), @(7, 6))) {
        Set-GPx $gg $c[0] $c[1] $gLeafLo
    }
    Save-GPng $glOut $gg 16 16
}

# --------------------------------------------------------------------- targets
New-GrapefruitHanging (Join-Path $gBlockDir 'grapefruit.png')
New-GrapefruitLogSide (Join-Path $gBlockDir 'grapefruit_log.png')
New-GrapefruitLogTop (Join-Path $gBlockDir 'grapefruit_log_top.png')
New-GrapefruitLeaves (Join-Path $gBlockDir 'grapefruit_leaves.png')
New-GrapefruitSapling (Join-Path $gBlockDir 'grapefruit_sapling.png')
New-GrapefruitItem (Join-Path $gItemDir 'grapefruit.png')
New-GrapefruitSlice (Join-Path $gItemDir 'grapefruit_slice.png')

Write-Host "wrote 7 grapefruit-tree textures to $gBlockDir and $gItemDir"
