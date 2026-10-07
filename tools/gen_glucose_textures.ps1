<#
    gen_glucose_textures.ps1 - the five item sprites of the glucose chain.

    A focused companion to gen_textures.ps1: same 16x16, same palette discipline, same PNG writer,
    but it only ever writes the five files listed at the bottom, so it can be re-run on its own
    after changing one of them.

    Run with (from the repository root):
        pwsh -ExecutionPolicy Bypass -File tools\gen_glucose_textures.ps1

    Idempotent: every target is deleted and rewritten, byte for byte, on every run.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$groot = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$gdir = Join-Path $groot "src\main\resources\assets\aliment\textures\item"
if (-not (Test-Path $gdir)) { New-Item -ItemType Directory -Force -Path $gdir | Out-Null }

# --------------------------------------------------------------------- palette
$gClear = '#00000000'

# the meter and the lancet are the same grey clinical plastic, so they share a ramp
$gCaseHi = '#6E7A88'
$gCaseM = '#4A5563'
$gCaseLo = '#2B333D'
$gCaseEdge = '#171C23'

# the strip is the same cream card in both of its states
$gCardHi = '#F2EDDD'
$gCardM = '#DFD8C4'
$gCardLo = '#B9B29C'
$gCardEdge = '#8E8874'
$gGoldHi = '#E8C55C'
$gGoldLo = '#A8811F'
$gPadM = '#C7C0AA'
$gBloodHi = '#C5343C'
$gBloodLo = '#7E161E'

# insulin is a clear, faintly blue fluid, which is what tells it from dexamethasone's yellow-green
$gInsHi = '#DCEBF2'
$gInsM = '#A9C6D6'
$gInsLo = '#6E8FA3'
$gInsEdge = '#3F5566'
$gInsLiquid = '#BFE3F0'
$gInsLiquidLo = '#7FB6CC'
$gInsCap = '#B03A3A'
$gInsCapLo = '#7A2222'

# the screen of the meter is an unlit LCD, and what it shows is drawn in its own two tones
$gLcdBg = '#0E1A16'
$gLcdLit = '#7FE8B8'
$gLcdLitLo = '#3E9E78'

# --------------------------------------------------------------------- helpers
function Save-GPng([string]$gpPath, $gpGrid, [int]$gpW, [int]$gpH) {
    $gpBmp = New-Object System.Drawing.Bitmap($gpW, $gpH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($gpY = 0; $gpY -lt $gpH; $gpY++) {
        for ($gpX = 0; $gpX -lt $gpW; $gpX++) {
            $gpHx = $gpGrid[$gpX, $gpY]
            $gpR = 0; $gpG = 0; $gpB = 0; $gpA = 0
            if ($gpHx -and $gpHx.StartsWith('#')) {
                if ($gpHx.Length -ge 9) {
                    $gpR = [Convert]::ToInt32($gpHx.Substring(1, 2), 16)
                    $gpG = [Convert]::ToInt32($gpHx.Substring(3, 2), 16)
                    $gpB = [Convert]::ToInt32($gpHx.Substring(5, 2), 16)
                    $gpA = [Convert]::ToInt32($gpHx.Substring(7, 2), 16)
                }
                elseif ($gpHx.Length -ge 7) {
                    $gpR = [Convert]::ToInt32($gpHx.Substring(1, 2), 16)
                    $gpG = [Convert]::ToInt32($gpHx.Substring(3, 2), 16)
                    $gpB = [Convert]::ToInt32($gpHx.Substring(5, 2), 16)
                    $gpA = 255
                }
            }
            $gpBmp.SetPixel($gpX, $gpY, [System.Drawing.Color]::FromArgb($gpA, $gpR, $gpG, $gpB))
        }
    }
    $gpFull = [System.IO.Path]::GetFullPath($gpPath)
    $gpBmp.Save($gpFull, [System.Drawing.Imaging.ImageFormat]::Png)
    $gpBmp.Dispose()
}

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

# A beveled box: outline in $bcEdge, a lit top/left edge and a shaded bottom/right edge.
function Set-GBox($gg, [int]$bx, [int]$by, [int]$bw, [int]$bh, [string]$bcFill, [string]$bcHi, [string]$bcLo, [string]$bcEdge) {
    Set-GRect $gg $bx $by $bw $bh $bcFill
    for ($gx = $bx; $gx -lt ($bx + $bw); $gx++) {
        Set-GPx $gg $gx $by $bcHi
        Set-GPx $gg $gx ($by + $bh - 1) $bcLo
    }
    for ($gy = $by; $gy -lt ($by + $bh); $gy++) {
        Set-GPx $gg $bx $gy $bcHi
        Set-GPx $gg ($bx + $bw - 1) $gy $bcLo
    }
    for ($gx = $bx; $gx -lt ($bx + $bw); $gx++) {
        Set-GPx $gg $gx $by $bcEdge
        Set-GPx $gg $gx ($by + $bh - 1) $bcEdge
    }
    for ($gy = $by; $gy -lt ($by + $bh); $gy++) {
        Set-GPx $gg $bx $gy $bcEdge
        Set-GPx $gg ($bx + $bw - 1) $gy $bcEdge
    }
    # put the bevel back inside the outline
    for ($gx = ($bx + 1); $gx -lt ($bx + $bw - 1); $gx++) {
        Set-GPx $gg $gx ($by + 1) $bcHi
        Set-GPx $gg $gx ($by + $bh - 2) $bcLo
    }
    for ($gy = ($by + 1); $gy -lt ($by + $bh - 1); $gy++) {
        Set-GPx $gg ($bx + 1) $gy $bcHi
        Set-GPx $gg ($bx + $bw - 2) $gy $bcLo
    }
}

# A seven-segment style digit, 3 wide by 5 tall, drawn from a per-digit neighbour table so every
# glyph on the meter screen comes out of the same routine.
function Set-GDigit($gg, [int]$dx, [int]$dy, [string]$dch) {
    $dSeg = @{
        '5' = @('111', '100', '111', '001', '111')
        '1' = @('010', '110', '010', '010', '111')
        '.' = @('000', '000', '000', '010', '010')
    }
    $dRows = $dSeg[$dch]
    if (-not $dRows) { return }
    for ($gy = 0; $gy -lt 5; $gy++) {
        $dRow = $dRows[$gy]
        for ($gx = 0; $gx -lt $dRow.Length; $gx++) {
            if ($dRow[$gx] -eq '1') {
                Set-GPx $gg ($dx + $gx) ($dy + $gy) $gLcdLit
            }
        }
    }
}

# ---------------------------------------------------------------------
# 1. item/insulin_injection.png
#    The dexamethasone syringe's twin: same diagonal, needle lower-left, but a
#    clear blue dose and a red cap band, so the two are never confused in a chest.
# ---------------------------------------------------------------------
function New-InsulinInjection([string]$ipOut) {
    $ig = New-GGrid 16 16
    $iax = 3.0; $iay = 13.0; $ibx = 12.2; $iby = 3.0
    $idx = $ibx - $iax; $idy = $iby - $iay
    $ilen = [Math]::Sqrt(($idx * $idx) + ($idy * $idy))
    $iux = $idx / $ilen; $iuy = $idy / $ilen
    $ipx = -1.0 * $iuy; $ipy = $iux

    # the needle is walked pixel by pixel so the 1px line stays connected
    for ($isi = 0; $isi -le 48; $isi++) {
        $iwt = 0.26 * $isi / 48.0
        $iwx = [int][Math]::Round($iax + ($iux * $iwt * $ilen))
        $iwy = [int][Math]::Round($iay + ($iuy * $iwt * $ilen))
        Set-GPx $ig $iwx $iwy $gInsEdge
        if ($isi % 4 -eq 0) { Set-GPx $ig $iwx $iwy $gCaseHi }
    }
    for ($iy = 0; $iy -lt 16; $iy++) {
        for ($ix = 0; $ix -lt 16; $ix++) {
            $irx = ([double]$ix) - $iax; $iry = ([double]$iy) - $iay
            $ist = (($irx * $iux) + ($iry * $iuy)) / $ilen
            $iss = ($irx * $ipx) + ($iry * $ipy)
            if ($ist -le 0.26 -or $ist -gt 1.03) { continue }
            $icol = ''
            if ($ist -le 0.78) {
                if ([Math]::Abs($iss) -gt 1.70) { continue }
                if ([Math]::Abs($iss) -gt 1.15) { $icol = $gInsLo }
                else {
                    $icol = $gInsM
                    if ($iss -lt -0.72) { $icol = $gInsHi }
                }
                if (($ist -gt 0.31) -and ($ist -lt 0.74) -and ([Math]::Abs($iss) -le 1.00)) {
                    $icol = $gInsLiquid
                    if ([Math]::Abs($iss) -gt 0.58) { $icol = $gInsLiquidLo }
                    if ($ist -gt 0.68) { $icol = $gInsLiquidLo }
                }
            }
            elseif ($ist -le 0.83) {
                if ([Math]::Abs($iss) -gt 1.95) { continue }
                $icol = $gInsM
                if ([Math]::Abs($iss) -gt 1.30) { $icol = $gInsLo }
            }
            else {
                if ([Math]::Abs($iss) -gt 1.85) { continue }
                $icol = $gInsCap
                if ([Math]::Abs($iss) -gt 1.15) { $icol = $gInsCapLo }
                if (($ist -gt 0.95) -and ([Math]::Abs($iss) -gt 0.95)) { $icol = $gInsCapLo }
            }
            if ($icol -ne '') { Set-GPx $ig $ix $iy $icol }
        }
    }
    Save-GPng $ipOut $ig 16 16
}

# ---------------------------------------------------------------------
# 2. item/glucose_meter.png
#    A handheld meter: case, an unlit LCD showing "5.1", a button and the strip slot.
# ---------------------------------------------------------------------
function New-GlucoseMeter([string]$gmOut) {
    $mg = New-GGrid 16 16
    Set-GBox $mg 2 2 12 12 $gCaseM $gCaseHi $gCaseLo $gCaseEdge
    # the screen is inset into the case, so it gets its own bezel. The window is 8 wide because
    # "5.1" needs 3 + 1 + 3 pixels and a pixel of margin at each end.
    Set-GRect $mg 3 4 10 6 $gCaseEdge
    Set-GRect $mg 4 5 8 4 $gLcdBg
    # "5.1" - the flagship fasting reading, which is what the meter is for. The decimal point gets
    # its own column and a gap, so a full screen never reads as the alarmingly high "51".
    Set-GDigit $mg 4 5 '5'
    Set-GDigit $mg 7 5 '.'
    Set-GDigit $mg 9 5 '1'
    # the button a real one uses to switch between mmol/L and mg/dL
    Set-GBox $mg 5 11 3 2 $gCaseHi $gCaseHi $gCaseLo $gCaseEdge
    Set-GBox $mg 9 11 2 2 $gCaseLo $gCaseM $gCaseLo $gCaseEdge
    Save-GPng $gmOut $mg 16 16
}

# ---------------------------------------------------------------------
# 3/4. item/glucose_test_strip.png and item/bloodied_test_strip.png
#      One routine, because the second is the first with blood on the pad:
#      gold contacts at the top, cream card, a reagent pad at the bottom.
# ---------------------------------------------------------------------
function New-TestStrip([string]$tsOut, [bool]$tsBloodied) {
    $tg = New-GGrid 16 16
    # card
    Set-GRect $tg 6 2 4 11 $gCardM
    for ($ty = 2; $ty -lt 13; $ty++) {
        Set-GPx $tg 6 $ty $gCardHi
        Set-GPx $tg 9 $ty $gCardLo
    }
    Set-GPx $tg 6 2 $gCardEdge
    Set-GPx $tg 9 2 $gCardEdge
    # the three gold contacts the meter reads
    Set-GRect $tg 6 3 4 3 $gGoldLo
    Set-GPx $tg 7 3 $gGoldHi
    Set-GPx $tg 8 3 $gGoldHi
    Set-GPx $tg 7 4 $gGoldHi
    # the reagent pad at the sampling end
    Set-GRect $tg 6 10 4 3 $gPadM
    Set-GPx $tg 7 10 $gCardHi
    if ($tsBloodied) {
        # a drop soaked into the pad, darker at the edges
        Set-GRect $tg 7 10 2 3 $gBloodLo
        Set-GPx $tg 7 11 $gBloodHi
        Set-GPx $tg 8 11 $gBloodHi
        Set-GPx $tg 7 12 $gBloodHi
    }
    Save-GPng $tsOut $tg 16 16
}

# ---------------------------------------------------------------------
# 5. item/microneedle.png
#    A lancet: a squat grey body with a red trigger band and a fine needle point.
# ---------------------------------------------------------------------
function New-Microneedle([string]$mnOut) {
    $ng = New-GGrid 16 16
    # body, with the rounded shoulders a moulded pen has
    Set-GBox $ng 5 2 6 8 $gCaseM $gCaseHi $gCaseLo $gCaseEdge
    Set-GPx $ng 5 2 $gClear; Set-GPx $ng 10 2 $gClear
    # the depth setting dial, which is what makes a lancet adjustable
    Set-GBox $ng 4 9 8 2 $gCaseLo $gCaseM $gCaseEdge $gCaseEdge
    # the trigger, in the one warm colour on the sprite
    Set-GBox $ng 5 6 6 2 $gInsCap $gInsCapLo $gInsCapLo $gCaseEdge
    # the needle, one pixel wide, tapering to a point
    Set-GPx $ng 7 11 $gCaseHi
    Set-GPx $ng 8 11 $gCaseLo
    Set-GPx $ng 7 12 $gCaseHi
    Set-GPx $ng 7 13 '#FFFFFF'
    Save-GPng $mnOut $ng 16 16
}

# --------------------------------------------------------------------- targets
New-InsulinInjection (Join-Path $gdir 'insulin_injection.png')
New-GlucoseMeter (Join-Path $gdir 'glucose_meter.png')
New-TestStrip (Join-Path $gdir 'glucose_test_strip.png') $false
New-TestStrip (Join-Path $gdir 'bloodied_test_strip.png') $true
New-Microneedle (Join-Path $gdir 'microneedle.png')

Write-Host "wrote 5 glucose-chain sprites to $gdir"
