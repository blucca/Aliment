<#
    gen_glass_textures.ps1 - the fermentation tank and condenser pipe casings.

    Both blocks are copper-framed glass vessels, and these two textures are their shells. Each is a
    copper frame around a **frosted glass panel**: the panel is opaque, so the block reads as a solid
    vessel instead of a wireframe cage you can see the world through.

    The tank additionally carries a **sight glass**: a narrow transparent slot in the middle of the
    panel, through which the liquid inside can be seen. It runs the full height of the panel because
    the fill level is what the player is reading, and the three levels sit at very different heights
    - level 1 tops out around texture row 11.5, level 2 at 6.4 and level 3 at 1.3 - so a short window
    would simply not show a nearly empty tank.

    Two details are easy to get wrong and both are handled here:

    * THE WINDOW'S TRANSPARENT PIXELS ARE FROST-COLOURED, NOT BLACK. Minecraft averages RGBA per
      texel when it builds mipmaps, so `(0,0,0,0)` drags the average towards black and the frame
      around the slot renders as a dark smudge. Bleeding the panel's own colour into the invisible
      slot keeps the average light at every mip level.

    * THE FRAME IS SIZED IN WORLD UNITS, NOT TEXELS. A block model stretches whatever rectangle of
      the texture it is handed across the whole face, so the condenser pipe - a 6-unit tube whose
      side faces are 6 wide by 16 tall - squeezes the texture 16 -> 6 horizontally while leaving it
      1:1 vertically. A symmetric frame there comes out 1.5 units down the sides against 4.0 units
      across the top and bottom, which is what "only the ring is copper" looked like in game.

    Run with (from the repository root):
        pwsh -ExecutionPolicy Bypass -File tools\gen_glass_textures.ps1

    Idempotent: both targets are rewritten from scratch on every run.
#>

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }
$dir = Join-Path $root 'src\main\resources\assets\aliment\textures\block'
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

# --------------------------------------------------------------------- palette
# The copper frame keeps the original textures' ramp, including the near-black corner and the amber
# bevel the tank carries where the frame meets the panel.
$corner = '#542208'
$frameLight = '#D9752D'
$tankDark = '#B85918'
$pipeDark = '#A84C14'
$frameAmber = '#F29C4B'

# The frosted glass. Cool and pale, with enough of a bevel that it does not read as flat grey.
$frostBase = '#C9DCE4'
$frostLight = '#DCE9EF'
$frostDark = '#AFC4CE'
$frostHilite = '#EFF7FA'

# --------------------------------------------------------------------- layout
# The tank's glass box is 12 units across, so its texture lands on the faces almost 1:1 and a
# symmetric 3-texel frame is ~2.25 world units each way.
$tankFrameX = 3
$tankFrameY = 3

# The pipe needs the horizontal border widened to survive its 16 -> 6 squeeze; see the header note.
# 4 texels across is 4/16 * 6 = 1.5 world units and 2 rows is 2/16 * 16 = 2.0, near enough to even.
# That is thinner than the tank's rim in world units, which is fine now that the panel behind it is
# opaque: the rim reads because copper against pale frosted glass is a strong edge, not because it is
# wide. Going wider than this - 5 or 6 texels - pushed the panel down to a third of the face.
$pipeFrameX = 4
$pipeFrameY = 2

# The tank's sight glass, in texture coordinates: four texels wide and running to the bottom of the
# panel so that even a nearly empty tank shows something.
$sightLeft = 6
$sightRight = 9
$sightTop = 4
$sightBottom = 12

# --------------------------------------------------------------------- helpers
# NOTE: `$grid` is deliberately untyped. Declaring it `[string[,]]` makes Windows PowerShell 5.1
# rebind the argument into a one-element array wrapping the real grid, so every read comes back
# empty - the same class of typing surprise the other generators in this directory warn about.
function Save-Png([string]$path, $grid) {
    $bmp = New-Object System.Drawing.Bitmap(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $hex = $grid[$x, $y]
            # Both forms are legal: '#RRGGBB' for opaque colours, '#RRGGBBAA' for the bled slot.
            if (-not $hex -or ($hex.Length -ne 7 -and $hex.Length -ne 9)) {
                throw "bad colour [$hex] at ($x,$y): expected #RRGGBB or #RRGGBBAA"
            }
            $r = [Convert]::ToInt32($hex.Substring(1, 2), 16)
            $g = [Convert]::ToInt32($hex.Substring(3, 2), 16)
            $b = [Convert]::ToInt32($hex.Substring(5, 2), 16)
            $a = if ($hex.Length -eq 9) { [Convert]::ToInt32($hex.Substring(7, 2), 16) } else { 255 }
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($a, $r, $g, $b))
        }
    }
    $bmp.Save([System.IO.Path]::GetFullPath($path), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

<#
    One casing: a copper frame $widthX texels thick left and right and $widthY thick top and bottom,
    around an opaque frosted panel. With $sightGlass the panel carries a transparent slot.
#>
function New-CasingTexture([string]$path, [string]$dark, [int]$widthX, [int]$widthY, [bool]$sightGlass) {
    $grid = New-Object 'string[,]' 16, 16

    # Start with the frosted panel everywhere, then lay the frame over it.
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) { $grid[$x, $y] = $frostBase }
    }

    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $fromLeft = [Math]::Min($x, 15 - $x)
            $fromTop = [Math]::Min($y, 15 - $y)
            $inFrame = ($fromLeft -lt $widthX) -or ($fromTop -lt $widthY)
            if (-not $inFrame) { continue }

            if ($fromLeft -eq 0 -or $fromTop -eq 0 -or
                $fromLeft -eq $widthX - 1 -or $fromTop -eq $widthY - 1) {
                $grid[$x, $y] = $dark                        # outline, and the lip of the panel
            } else {
                $grid[$x, $y] = $frameLight                  # the body of the frame
            }
        }
    }

    # The original art's near-black corners, and its amber bevel where the frame meets the panel.
    # Parenthesised because PowerShell binds the array comma tighter than the minus sign.
    $nearX = $widthX - 1
    $nearY = $widthY - 1
    $farX = 16 - $widthX
    $farY = 16 - $widthY
    foreach ($p in @(@(0, 0), @(15, 0), @(0, 15), @(15, 15))) {
        $grid[$p[0], $p[1]] = $corner
    }
    foreach ($p in @(@($nearX, $nearY), @($farX, $nearY), @($nearX, $farY), @($farX, $farY))) {
        $grid[$p[0], $p[1]] = $frameAmber
    }

    # Bevel the panel so the frosted glass has some depth: lit along its top and left, shaded along
    # its bottom and right. Tested against the absolute coordinate rather than the distance from the
    # nearest edge, because the distance is symmetric and would light both sides.
    $panelFarX = 15 - $widthX
    $panelFarY = 15 - $widthY
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $fromLeft = [Math]::Min($x, 15 - $x)
            $fromTop = [Math]::Min($y, 15 - $y)
            if ($fromLeft -lt $widthX -or $fromTop -lt $widthY) { continue }   # the frame

            if ($x -eq $widthX -or $y -eq $widthY) {
                $grid[$x, $y] = $frostLight
            } elseif ($x -eq $panelFarX -or $y -eq $panelFarY) {
                $grid[$x, $y] = $frostDark
            }
        }
    }
    # A couple of highlight flecks on the panel's body columns, so it is not a flat fill. Placed from
    # the frame width so they stay inside the panel whatever the frame is.
    $bodyNearX = $widthX + 1
    $bodyFarX = 14 - $widthX
    $fleckNearY = $widthY + 4
    $fleckFarY = 12 - $widthY
    foreach ($p in @(@($bodyNearX, $fleckNearY), @($bodyFarX, $fleckFarY))) {
        $grid[$p[0], $p[1]] = $frostHilite
    }

    if ($sightGlass) {
        # The slot is transparent, but its RGB is the panel's own colour: see the header note.
        $bled = $frostBase + '00'
        for ($y = $sightTop; $y -le $sightBottom; $y++) {
            for ($x = $sightLeft; $x -le $sightRight; $x++) { $grid[$x, $y] = $bled }
        }
        # A dark lip down each side of the slot, so it reads as a fitting rather than a hole.
        # Parenthesised for the same comma-precedence reason as the corners above.
        $lipLeft = $sightLeft - 1
        $lipRight = $sightRight + 1
        for ($y = $sightTop; $y -le $sightBottom; $y++) {
            $grid[$lipLeft, $y] = $dark
            $grid[$lipRight, $y] = $dark
        }
    }

    Save-Png $path $grid
}

# --------------------------------------------------------------------- targets
New-CasingTexture (Join-Path $dir 'fermentation_tank_glass.png') $tankDark $tankFrameX $tankFrameY $true
New-CasingTexture (Join-Path $dir 'condenser_pipe_glass.png') $pipeDark $pipeFrameX $pipeFrameY $false

Write-Host "wrote the copper-framed frosted panels into $dir"
