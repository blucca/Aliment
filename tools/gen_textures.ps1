# =====================================================================
#  Outbreak - Willow wood set texture generator
#  Regenerates every PNG used by the willow wood set from scratch.
#  Idempotent: deletes and recreates all target files.
#  Writes ONLY inside src/main/resources/assets/outbreak/.
# =====================================================================

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$magick = "D:\Codes\Kotlin\Outbreak\.tools\imagemagick\magick.exe"
$root   = "D:\Codes\Kotlin\Outbreak"
$assets = Join-Path $root "src\main\resources\assets\outbreak"
$bdir   = Join-Path $assets "textures\block"
$idir   = Join-Path $assets "textures\item"
$edir   = Join-Path $assets "textures\entity\boat"
$cdir   = Join-Path $assets "textures\entity\chest_boat"
$gdir   = Join-Path $assets "textures\gui\sprites\hud"  # thirst hud cells (gui atlas sprites)
$work   = Join-Path $root ".scratch\genwork"           # scratch intermediates
$van    = Join-Path $work "vanilla-ref"                # READ-ONLY reference art

if (-not (Test-Path $magick)) { throw "ImageMagick not found at $magick" }

New-Item -ItemType Directory -Force -Path $bdir, $idir, $edir, $cdir, $gdir | Out-Null
New-Item -ItemType Directory -Force -Path $work, $van | Out-Null

# ---------------------------------------------------------------------
# Vanilla reference art, pulled straight out of the Minecraft client jar so
# this script stays reproducible on a clean checkout.
# ---------------------------------------------------------------------
$mavenRoot = Join-Path $root ".gradle\loom-cache\minecraftMaven\net\minecraft"
$clientJar = @(Get-ChildItem $mavenRoot -Recurse -Filter "minecraft-clientOnly-*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "*.backup" } | Select-Object -First 1)
if ($clientJar.Count -eq 0) { throw "Could not find the Minecraft client jar under $mavenRoot" }
$clientJar = $clientJar[0].FullName

$refNames = @('oak_sign.png', 'oak_hanging_sign.png', 'oak_boat.png', 'oak_chest_boat.png',
              'mushroom_stew.png', 'dragon_breath.png',
              'stick.png')   # shading + palette reference for item/stirring_rod.png
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($clientJar)
try {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -like "assets/minecraft/textures/*" -and $refNames -contains (Split-Path $entry.FullName -Leaf)) {
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $van (Split-Path $entry.FullName -Leaf)), $true)
        }
    }
} finally { $zip.Dispose() }
foreach ($n in $refNames) {
    if (-not (Test-Path (Join-Path $van $n))) { throw "Missing vanilla reference texture: $n" }
}

# ---------------------------------------------------------------------
# Palette
# ---------------------------------------------------------------------
$cBarkD='#4A4433'; $cBarkM='#5F5741'; $cBarkL='#7A7055'
$cStrD ='#A89A6E'; $cStrM ='#BDAD7C'; $cStrL ='#CFC293'
$cPlD  ='#8E7F55'; $cPlM  ='#B8A876'; $cPlL  ='#CDBF90'
$cFol  ='#7E9A57'; $cFolD ='#5B7440'; $cFolH ='#9DB86E'
$cChestD='#3A2A16'; $cChestM='#6B4F2A'; $cChestL='#8B6A3F'
$cLock  ='#3A2A16'

# --- daffodil icon (task A) + willow bark soup set (tasks B/C) -------------
$cPetH  ='#FFFFFF'; $cPetM ='#F2EFE0'; $cPetD ='#D9D4BE'; $cPetS ='#BEB89F'
$cTruRim='#FFE08A'; $cTruO ='#F5C542'; $cTruM ='#E8A317'; $cTruD ='#C67C0E'
$cTruSh ='#9A6209'
$cStemD ='#4E7A2E'; $cStemL='#6B9B3F'
$cIconT ='#3E6B4A'; $cIconB='#2C4A34'
$cSoupRD='#6B7F4C'; $cSoupRM='#8FA36B'; $cSoupRL='#A3B67C'
$cBarkIn='#A89A6E'
$cGlassH='#FFFFFF'; $cGlassM='#C9DCE4'; $cGlassD='#8FA6B0'

# --- soup bowl / bottle redraw (vanilla mushroom_stew + dragon_breath) ------
# Bowl and glass tones are palette-driven; only the broth itself differs
# between the raw and the cooked sprite (cooked is always the darker one).
$cBowlWood ='#8B6A3F'   # bowl body
$cBowlShad ='#6B4F2A'   # bowl wall / inner wall shadow
$cBowlRim  ='#A8834F'   # lit rim
$cBowlEdge ='#3E2A12'   # outer shadow / outline
$cGlassOl  ='#5E7480'   # glass outline
$cCorkLite ='#B08A5A'   # cork light
$cCorkDark ='#8A6A40'   # cork dark
$cBrothRB  ='#8FA36B'; $cBrothRS='#7A8C58'; $cBrothRH='#A3B67C'   # raw broth
$cBrothCB  ='#6E8248'; $cBrothCS='#5A6B39'; $cBrothCH='#8AA05E'   # cooked broth
$cFleckA   ='#5F7440'; $cFleckB ='#4A4433'                        # raw bark fibres
$cTopCkBase='#6B7F45'; $cTopCkPool='#5A6B39'; $cTopCkHi='#84995A' # cooked cauldron top

$cClear = '#00000000'
$cNull  = '#00000000'
$sT     = '#7A7055'   # shadow / trim tone
$sD     = '#4A4433'

# ---------------------------------------------------------------------
# Small helpers
# ---------------------------------------------------------------------
# Colour helpers.
# NOTE: Windows PowerShell 5.1 resolves simple variable reads/writes against
# enclosing scopes, so short generic names ($a/$b/$r/$g/$b/$c/$t/$A/$B, which
# the rest of this script also uses for unrelated things) got cross-wired and
# silently produced garbage. Every name below is deliberately unique, and all
# arithmetic is fully parenthesised.
# ---------------------------------------------------------------------
function Get-Hextuple([string]$hx){
    # accepts "#RRGGBB" or "#RRGGBBAA"; always returns a 4-element int array
    $hh = $hx.Trim()
    if($hh.StartsWith('#')){ $hh = $hh.Substring(1) }
    if($hh.Length -lt 6){ throw "bad colour literal: $hx" }
    $out = New-Object 'int[]' 4
    $out[0] = [Convert]::ToInt32($hh.Substring(0,2),16)
    $out[1] = [Convert]::ToInt32($hh.Substring(2,2),16)
    $out[2] = [Convert]::ToInt32($hh.Substring(4,2),16)
    if($hh.Length -ge 8){ $out[3] = [Convert]::ToInt32($hh.Substring(6,2),16) } else { $out[3] = 255 }
    return ,$out
}
function Get-Hextuple3([string]$hx){
    $q = Get-Hextuple $hx
    return ,@([int]$q[0],[int]$q[1],[int]$q[2])
}
function Format-RgbHex([int]$hr,[int]$hg,[int]$hb){
    if($hr -lt 0){ $hr = 0 } elseif($hr -gt 255){ $hr = 255 }
    if($hg -lt 0){ $hg = 0 } elseif($hg -gt 255){ $hg = 255 }
    if($hb -lt 0){ $hb = 0 } elseif($hb -gt 255){ $hb = 255 }
    return ('#{0:X2}{1:X2}{2:X2}' -f $hr, $hg, $hb)
}
function Get-MixHex([string]$hxA,[string]$hxB,[double]$fT){
    $qA = Get-Hextuple3 $hxA
    $qB = Get-Hextuple3 $hxB
    $fF = [double]$fT
    $mr = [int][Math]::Round(([double]$qA[0]) + ((([double]$qB[0]) - ([double]$qA[0])) * $fF))
    $mg = [int][Math]::Round(([double]$qA[1]) + ((([double]$qB[1]) - ([double]$qA[1])) * $fF))
    $mb = [int][Math]::Round(([double]$qA[2]) + ((([double]$qB[2]) - ([double]$qA[2])) * $fF))
    return Format-RgbHex $mr $mg $mb
}
# backwards-compatible aliases used throughout the generator below
function MixHex([string]$a0,[string]$a1,[double]$a2){ return Get-MixHex $a0 $a1 $a2 }
function RgbToHex([int]$b0,[int]$b1,[int]$b2){ return Format-RgbHex $b0 $b1 $b2 }
function HexToRgb([string]$c0){ return Get-Hextuple3 $c0 }

# 3x3 ordered dither matrix (values 0..8) combined with a per-texture salt
$dither = @(
    @(0,6,2),
    @(5,8,4),
    @(1,7,3)
)

# Deterministic value hash -> 0..1
function Noise([int]$x,[int]$y,[int]$salt){
    $h = ($x * 374761393) + ($y * 668265263) + ($salt * 1274126177)
    $h = $h -band 0x7FFFFFFF
    $h = ($h -bxor ($h -shr 13)) * 1274126177
    $h = $h -band 0x7FFFFFFF
    return ($h % 10007) / 10007.0
}

# ---------------------------------------------------------------------
# PNG writer (System.Drawing) - writes RGBA, no resampling
# ---------------------------------------------------------------------
function Save-Png([string]$path,$grid,[int]$w,[int]$h){
    # NOTE: every local here uses an sp_ prefix. PowerShell resolves simple
    # variable writes against the enclosing scope when the name already
    # exists there, which silently zeroed short names like $r/$g/$b/$a.
    $spBmp = New-Object System.Drawing.Bitmap($w,$h,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for($spY=0;$spY -lt $h;$spY++){
        for($spX=0;$spX -lt $w;$spX++){
            $spHex = $grid[$spX,$spY]
            $spR=0; $spG=0; $spB=0; $spA=0
            if($spHex -and $spHex.StartsWith('#')){
                if($spHex.Length -ge 9){
                    $spR=[Convert]::ToInt32($spHex.Substring(1,2),16)
                    $spG=[Convert]::ToInt32($spHex.Substring(3,2),16)
                    $spB=[Convert]::ToInt32($spHex.Substring(5,2),16)
                    $spA=[Convert]::ToInt32($spHex.Substring(7,2),16)
                }
                elseif($spHex.Length -ge 7){
                    $spR=[Convert]::ToInt32($spHex.Substring(1,2),16)
                    $spG=[Convert]::ToInt32($spHex.Substring(3,2),16)
                    $spB=[Convert]::ToInt32($spHex.Substring(5,2),16)
                    $spA=255
                }
            }
            $spBmp.SetPixel($spX,$spY,[System.Drawing.Color]::FromArgb($spA,$spR,$spG,$spB))
        }
    }
    $spFull = [System.IO.Path]::GetFullPath($path)
    $spBmp.Save($spFull,[System.Drawing.Imaging.ImageFormat]::Png)
    $spBmp.Dispose()
}
function New-Grid([int]$w,[int]$h){
    $g = New-Object 'string[,]' $w,$h
    for($y=0;$y -lt $h;$y++){ for($x=0;$x -lt $w;$x++){ $g[$x,$y] = '#00000000' } }
    return ,$g
}
function GridRect($g,[int]$x0,[int]$y0,[int]$w,[int]$h,[string]$c){
    for($y=$y0;$y -lt $y0+$h;$y++){ for($x=$x0;$x -lt $x0+$w;$x++){
        if($x -ge 0 -and $y -ge 0 -and $x -lt $g.GetLength(0) -and $y -lt $g.GetLength(1)){ $g[$x,$y] = $c }
    } }
}
function GridPx($g,[int]$x,[int]$y,[string]$c){
    if($x -ge 0 -and $y -ge 0 -and $x -lt $g.GetLength(0) -and $y -lt $g.GetLength(1)){ $g[$x,$y] = $c }
}

# ---------------------------------------------------------------------
# Shared wood-pattern generators
# ---------------------------------------------------------------------

# Bark: 4x4 seamless vertical-grain tile in the bark colours.
$BarkTile = @(
    @($cBarkD,$cBarkM,$cBarkL,$cBarkM),
    @($cBarkM,$cBarkL,$cBarkM,$cBarkD),
    @($cBarkL,$cBarkD,$cBarkM,$cBarkM),
    @($cBarkM,$cBarkM,$cBarkD,$cBarkL)
)

# Stripped bark: 4x4 seamless pale vertical-grain tile.
$StripTile = @(
    @($cStrM,$cStrL,$cStrD,$cStrM),
    @($cStrD,$cStrM,$cStrL,$cStrL),
    @($cStrL,$cStrD,$cStrM,$cStrD),
    @($cStrM,$cStrL,$cStrD,$cStrM)
)

function Add-BarkRuns($g,[int]$w,[int]$h,[int]$salt, [string]$loudCol){
    # a few long wrapped furrows so the grain reads vertically
    $runs = @(
        @(1,($salt%5),      6,$loudCol),
        @(6,(($salt+2)%6),  7,$loudCol),
        @(11,(($salt+1)%7), 6,$loudCol)
    )
    foreach($r in $runs){
        $x=$r[0]; $y0=$r[1]; $len=$r[2]; $col=$r[3]
        for($i=0;$i -lt $len;$i++){
            $y = ($y0 + $i) % $h
            if($x -lt $w){ $g[$x,$y] = $col }
        }
    }
}

# Vertical grain for the log sides: wraps horizontally and vertically so the
# four log textures tile seamlessly against each other.
function New-GrainGrid([int]$w,[int]$h,[string]$baseCol,[string]$darkCol,[string]$lightCol,[int]$seed){
    $gg = New-Grid $w $h
    # per-column grain width profile: 0=plain, 1=dark streak, 2=pale ridge
    for($y=0;$y -lt $h;$y++){
        for($x=0;$x -lt $w;$x++){
            $col = $baseCol
            $cw = Noise $x 0 $seed
            $seg = [int][Math]::Floor($y / 5)
            # the streak changes character slightly down the trunk
            $wig = Noise ($x+$seg) 1 ($seed+57)
            if($cw -lt 0.34){ $col = $darkCol }
            elseif($cw -gt 0.68){ $col = $lightCol }
            if($wig -gt 0.88 -and $cw -lt 0.50){ $col = $darkCol }
            # rare chinks break up the runs (full-column, so streaks stay vertical)
            $ck = Noise ($x*7) 0 ($seed+91)
            if($ck -gt 0.955){ $col = $darkCol }
            elseif($ck -lt 0.045){ $col = $lightCol }
            $gg[$x,$y] = $col
        }
    }
    return ,$gg
}

# Pre-computed plank field: 40 wide x 16 tall.
# Rows 0-3 / 4-7 / 8-11 / 12-15 = the four boards written bottom-up:
#   output row 15 = board 0 (bottom), row 0 = board 3 (top).
function New-PlankField([int]$seed){
    $W = 40; $H = 16
    # four horizontal boards; output rows 12-15 = board0(bottom) ... rows 0-3 = board3(top)
    $boards = @(
        @{ r1=$cPlD; r2=$cPlD; r3=$cPlM; joint=8  }, # board 0 (rows 12-15)
        @{ r1=$cPlD; r2=$cPlD; r3=$cPlM; joint=20 }, # board 1 (rows  8-11)
        @{ r1=$cPlM; r2=$cPlD; r3=$cPlD; joint=13 }, # board 2 (rows  4-7), darker board
        @{ r1=$cPlD; r2=$cPlD; r3=$cPlL; joint=30 }  # board 3 (rows  0-3), lightest board
    )
    $f = New-Object 'string[,]' $W,$H
    for($y=0;$y -lt $H;$y++){
        # integer arithmetic only: board index + in-board row
        $bi = 3 - [int]([Math]::Floor([double]$y / 4.0))
        if($bi -lt 0){ $bi = 0 } elseif($bi -gt 3){ $bi = 3 }
        $board = $boards[$bi]
        $ly = $y % 4
        for($x=0;$x -lt $W;$x++){
            # row 0 of each board is the 1px dark seam; rows 1-3 lighten downwards
            if($ly -eq 0){ $col = $cPlD }
            elseif($ly -eq 1){ $col = $board.r1 }
            elseif($ly -eq 2){ $col = $board.r2 }
            else { $col = $board.r3 }
            # horizontal grain variation inside the board
            if($ly -ne 0){
                $gr = Noise ([int]([Math]::Floor([double]$x / 2.0))) ($bi*3) ($seed+13)
                if($gr -gt 0.62 -and $ly -ne 1){ $col = $board.r3 }
                elseif($gr -lt 0.20 -and $ly -ne 1){ $col = $cPlD }
            }
            # sparse knots and pale flecks
            $nf = Noise ($x*3) ($y*5) ($seed*13+$bi)
            if($nf -gt 0.975){ $col = $cPlD } elseif($nf -lt 0.018){ $col = $cPlL }
            # one vertical board joint per board, full height of that board
            if($x -eq $board.joint){ $col = $cPlD }
            $f[$x,$y] = $col
        }
    }
    return ,$f
}

$script:Plank = (New-PlankField 17)

# Draw a plank-filled rectangle into a draw-string list
function Add-PlankRect($cmds,[int]$px,[int]$py,[int]$pw,[int]$ph){
    for($y=0;$y -lt $ph;$y++){
        for($x=0;$x -lt $pw;$x++){
            $c = $script:Plank[(($px+$x) % 40), (($py+$y) % 16)]
            $cmds.Add("fill $c rectangle $($px+$x),$($py+$y) $($px+$x),$($py+$y)") | Out-Null
        }
    }
}
function Add-SolidRect($cmds,[int]$px,[int]$py,[int]$pw,[int]$ph,[string]$c){
    if($pw -le 0 -or $ph -le 0){ return }
    $cmds.Add("fill $c rectangle $px,$py $($px+$pw-1),$($py+$ph-1)") | Out-Null
}
function Add-RectOutline($cmds,[int]$px,[int]$py,[int]$pw,[int]$ph,[string]$c){
    if($pw -le 0 -or $ph -le 0){ return }
    Add-SolidRect $cmds $px $py $pw 1 $c
    Add-SolidRect $cmds $px ($py+$ph-1) $pw 1 $c
    Add-SolidRect $cmds $px $py 1 $ph $c
    Add-SolidRect $cmds ($px+$pw-1) $py 1 $ph $c
}
function Add-Px($cmds,[int]$px,[int]$py,[string]$c){
    $cmds.Add("fill $c rectangle $px,$py $px,$py") | Out-Null
}
# Render a draw-command list. ImageMagick's argument list has a hard length
# limit on Windows, so the commands always go through a @file.
function Invoke-MagickDraw([int]$w,[int]$h,[string]$out,$cmdList){
    if($cmdList -eq $null){ throw "Invoke-MagickDraw: no command list for $out" }
    $df = Join-Path $work 'draw_cmds.txt'
    [System.IO.File]::WriteAllText($df, ($cmdList -join "`n"), (New-Object System.Text.UTF8Encoding($false)))
    & $magick -size "${w}x${h}" "xc:$cNull" -draw "@$df" $out
    if($LASTEXITCODE -ne 0){ throw "magick draw failed for $out" }
}

# ---------------------------------------------------------------------
# 1. willow_log.png  (bark side)
# ---------------------------------------------------------------------
$cmds = New-Object System.Collections.Generic.List[string]
$g = New-GrainGrid 16 16 $cBarkM $cBarkD $cBarkL 11
Add-BarkRuns $g 16 16 1 $cBarkD
for($y=0;$y -lt 16;$y++){ for($x=0;$x -lt 16;$x++){
    $c = $g[$x,$y]
    $cmds.Add("fill $c rectangle $x,$y $x,$y") | Out-Null } }
# a few longer, deeper furrows and one pale ridge
Add-SolidRect $cmds 2 0 1 16 $cBarkD
Add-SolidRect $cmds 8 0 1 16 $cBarkM
Add-SolidRect $cmds 12 0 1 16 $cBarkL
Add-SolidRect $cmds 5 3 1 7 $cBarkD
Add-SolidRect $cmds 10 8 1 6 $cBarkD
Add-SolidRect $cmds 0 0 1 16 $cBarkD
Add-SolidRect $cmds 15 0 1 16 $cBarkD
$dest = Join-Path $bdir 'willow_log.png'
Invoke-MagickDraw 16 16 $dest $cmds

# ---------------------------------------------------------------------
# 2. willow_log_top.png  (end grain, concentric rings)
# ---------------------------------------------------------------------
$cmds = New-Object System.Collections.Generic.List[string]
Add-PlankRect $cmds 0 0 16 16
$cx=7.5; $cy=7.5
for($y=0;$y -lt 16;$y++){ for($x=0;$x -lt 16;$x++){
    $d = [Math]::Sqrt([Math]::Pow($x-$cx,2)+[Math]::Pow($y-$cy,2))
    $ring = [int][Math]::Floor($d) % 3
    $n = Noise $x $y 5
    if($d -gt 7.2){ $col = $cStrD }
    elseif($d -gt 6.2){ $col = $cBarkM }
    else{
        if($ring -eq 0){ $col = $cStrD }
        elseif($ring -eq 1){ $col = $cStrM }
        else { $col = $cStrL }
        if($n -gt 0.90){ $col = $cStrL } elseif($n -lt 0.07){ $col = $cStrD }
    }
    Add-Px $cmds $x $y $col
} }
# heartwood notch / radial crack
Add-SolidRect $cmds 7 3 2 2 $cPlD
Add-SolidRect $cmds 3 7 2 2 $cStrD
Add-SolidRect $cmds 11 10 2 2 $cStrD
Add-RectOutline $cmds 0 0 16 16 $sD
$dest = Join-Path $bdir 'willow_log_top.png'
Invoke-MagickDraw 16 16 $dest $cmds

# ---------------------------------------------------------------------
# 3. stripped_willow_log.png
# ---------------------------------------------------------------------
$cmds = New-Object System.Collections.Generic.List[string]
$g = New-GrainGrid 16 16 $cStrM $cStrD $cStrL 23
Add-BarkRuns $g 16 16 2 $cStrD
for($y=0;$y -lt 16;$y++){ for($x=0;$x -lt 16;$x++){
    $c = $g[$x,$y]
    $cmds.Add("fill $c rectangle $x,$y $x,$y") | Out-Null } }
Add-SolidRect $cmds 3 0 1 16 $cStrD
Add-SolidRect $cmds 9 0 1 16 $cStrL
Add-SolidRect $cmds 13 0 1 16 $cStrD
Add-SolidRect $cmds 6 2 1 9 $cStrL
Add-SolidRect $cmds 11 5 1 7 $cStrD
Add-SolidRect $cmds 0 0 1 16 $cStrM
Add-SolidRect $cmds 15 0 1 16 $cStrM
# a couple of broken pale streaks
Add-SolidRect $cmds 5 2 1 5 $cStrL
Add-SolidRect $cmds 10 9 1 4 $cStrL
$dest = Join-Path $bdir 'stripped_willow_log.png'
Invoke-MagickDraw 16 16 $dest $cmds

# ---------------------------------------------------------------------
# 4. stripped_willow_log_top.png
# ---------------------------------------------------------------------
$cmds = New-Object System.Collections.Generic.List[string]
Add-PlankRect $cmds 0 0 16 16
$cx=7.5; $cy=7.5
for($y=0;$y -lt 16;$y++){ for($x=0;$x -lt 16;$x++){
    $d = [Math]::Sqrt([Math]::Pow($x-$cx,2)+[Math]::Pow($y-$cy,2))
    $ring = [int][Math]::Floor($d) % 3
    $n = Noise $x $y 9
    if($d -gt 7.2){ $col = $cBarkL }
    elseif($d -gt 6.2){ $col = $cStrD }
    else{
        if($ring -eq 0){ $col = $cStrM }
        elseif($ring -eq 1){ $col = $cStrL }
        else { $col = $cPlL }
        if($n -gt 0.91){ $col = $cPlL } elseif($n -lt 0.06){ $col = $cStrD }
    }
    Add-Px $cmds $x $y $col
} }
Add-SolidRect $cmds 7 3 2 2 $cStrD
Add-SolidRect $cmds 3 8 2 2 $cStrM
Add-SolidRect $cmds 11 11 2 2 $cStrM
Add-RectOutline $cmds 0 0 16 16 $sT
$dest = Join-Path $bdir 'stripped_willow_log_top.png'
Invoke-MagickDraw 16 16 $dest $cmds

# ---------------------------------------------------------------------
# 5. willow_planks.png  - exactly 4 horizontal boards separated by 1px seams
# ---------------------------------------------------------------------
$cmds = New-Object System.Collections.Generic.List[string]
Add-PlankRect $cmds 0 0 16 16
$g = New-Grid 16 16
for($y=0;$y -lt 16;$y++){ for($x=0;$x -lt 16;$x++){ $g[$x,$y] = $script:Plank[$x,$y] } }
Save-Png (Join-Path $bdir 'willow_planks.png') $g 16 16

# ---------------------------------------------------------------------
# 6. willow_leaves.png  - GRAYSCALE, biome tinted, ~1/3 holes
# ---------------------------------------------------------------------
$G = @(0x4A,0x57,0x64,0x70,0x7C)
$leafSeed = @(2,0,1, 0,1,2, 1,2,3, 0,0,1, 1,0,2, 0,0)
$g = New-Grid 16 16
$blobCenters = @(
    @(2,2),@(7,2),@(12,2),
    @(4,7),@(9,7),@(14,7),
    @(1,11),@(6,11),@(11,11),
    @(3,14),@(8,14),@(13,14),
    @(6,4),@(11,9),@(1,6),@(14,13)
)
for($y=0;$y -lt 16;$y++){
    for($x=0;$x -lt 16;$x++){
        $best = 9.0
        foreach($b in $blobCenters){
            $d = [Math]::Sqrt([Math]::Pow($x-$b[0],2)+[Math]::Pow($y-$b[1],2))
            if($d -lt $best){ $best = $d }
        }
        $kind = 0
        if($best -lt 1.10){ $kind = 1 }        # blob centre
        elseif($best -lt 1.90){ $kind = 2 }    # crown
        elseif($best -lt 2.45){ $kind = 3 }    # fringe
        $n = Noise $x $y 23
        if($kind -eq 0){
            $g[$x,$y] = '#00000000'
        }
        elseif($kind -eq 3){
            if($n -lt 0.50){ $g[$x,$y] = '#00000000' }
            else {
                # fringe: mid-dark greys only
                $fv = 0x5C + [int](14*$n)
                $g[$x,$y] = Format-RgbHex $fv $fv $fv
            }
        }
        else{
            $lv = 0x4A + ([int]($leafSeed[($y%6)*3 + ($x%3)] + ((0.6*$n) - 0.3))) * 0x0C
            $g[$x,$y] = Format-RgbHex $lv $lv $lv
        }
    }
}
# small highlights + a couple of dark leaf clusters
$g[4,8]  = RgbToHex 0x7C 0x7C 0x7C
$g[12,5] = RgbToHex 0x7C 0x7C 0x7C
$g[2,10] = RgbToHex 0x70 0x70 0x70
$g[9,2]  = RgbToHex 0x64 0x64 0x64
$g[7,12] = RgbToHex 0x4A 0x4A 0x4A
$g[13,11]= RgbToHex 0x4A 0x4A 0x4A
Save-Png (Join-Path $bdir 'willow_leaves.png') $g 16 16

# ---------------------------------------------------------------------
# 7. willow_sapling.png  - miniature weeping willow
# ---------------------------------------------------------------------
$g = New-Grid 16 16
# rounded crown: disc of radius ~5 around (7.5,5)
for($y=0;$y -lt 16;$y++){ for($x=0;$x -lt 16;$x++){
    $d = [Math]::Sqrt([Math]::Pow($x-7.5,2)+[Math]::Pow($y-5.0,2))
    $n = Noise $x $y 41
    if($d -lt 4.1){
        if($n -gt 0.80){ $g[$x,$y] = $cFolH }
        elseif($n -lt 0.22){ $g[$x,$y] = $cFolD }
        else { $g[$x,$y] = $cFol }
    }
    elseif($d -lt 5.2 -and $n -gt 0.42){
        if($n -gt 0.80){ $g[$x,$y] = $cFol }
        else { $g[$x,$y] = $cFolD }
    }
} }
# trunk (bottom-centre)
GridRect $g 7 11 2 5 $sD
GridRect $g 7 11 1 5 $cBarkM
GridRect $g 8 12 1 4 $cBarkD
GridRect $g 6 10 2 2 $cBarkM
GridRect $g 8 9  2 2 $cBarkM
# drooping fronds down the sides, tapering
$fronds = @(
    @(1, 7, 9),  @(3, 6, 13), @(12,6, 13), @(14,7, 9),
    @(5, 8, 11), @(10,8, 11), @(0, 9, 8),  @(15,9, 8),
    @(6, 9, 14), @(9, 9, 14)
)
foreach($fr in $fronds){
    $fx=$fr[0]; $fy0=$fr[1]; $fy1=$fr[2]
    $jitter = 0
    for($y=$fy0;$y -le $fy1;$y++){
        $n = Noise $fx $y (53+$fx)
        if($n -lt 0.22){ $jitter = -1 } elseif($n -gt 0.82){ $jitter = 1 } else { $jitter = 0 }
        $xx = $fx + $jitter
        if($xx -lt 0 -or $xx -gt 15){ $xx = $fx }
        $col = $cFol
        if(($y - $fy0) -gt (($fy1-$fy0) * 0.65)){ $col = $cFolD }
        if($n -gt 0.72){ $col = $cFolH }
        $g[$xx,$y] = $col
    }
}
# tip leaves on the fronds
foreach($pt in @(@(1,9),@(3,13),@(12,13),@(14,9),@(5,11),@(10,11),@(6,14),@(9,14))){
    $g[$pt[0],$pt[1]] = $cFolD
}
Save-Png (Join-Path $bdir 'willow_sapling.png') $g 16 16

# ---------------------------------------------------------------------
# 8. willow_vines.png  (strand tip)
# ---------------------------------------------------------------------
$g = New-Grid 16 16
# bands: x0,x1 per row-range -> strand confined to ~x=4..12, tapering down
for($y=0;$y -lt 16;$y++){
    $n = Noise 3 $y 61
    switch($y){
        {$_ -le 1}{ $x0=4; $x1=12 }
        {$_ -le 3}{ $x0=5; $x1=12 }
        {$_ -le 5}{ $x0=5; $x1=11 }
        {$_ -le 7}{ $x0=6; $x1=11 }
        {$_ -le 9}{ $x0=7; $x1=10 }
        {$_ -le 11}{ $x0=7; $x1=10 }
        default  { $x0=8; $x1=9 }
    }
    if($n -gt 0.72){ $x0 = $x0 - 1 }
    if($n -lt 0.28){ $x1 = $x1 + 1 }
    for($x=$x0;$x -le $x1;$x++){
        if($x -lt 0 -or $x -gt 15){ continue }
        $m = Noise $x $y 67
        if($m -lt 0.14){ continue }                 # occasional gap
        $col = $cFol
        if($m -lt 0.36){ $col = $cFolD }
        elseif($m -gt 0.86){ $col = $cFolH }
        $g[$x,$y] = $col
    }
}
# dense leaf tips around the top of the tip block
foreach($pt in @(@(4,0),@(5,0),@(6,1),@(7,0),@(8,0),@(9,1),@(10,0),@(11,0),@(12,1),@(12,2),@(4,2),@(5,3))){
    $g[$pt[0],$pt[1]] = $cFolH
}
GridRect $g 8 13 2 3 $cFol
GridRect $g 8 15 1 1 $cFolD
Save-Png (Join-Path $bdir 'willow_vines.png') $g 16 16

# ---------------------------------------------------------------------
# 9. willow_vines_plant.png  (mid strand, x=1..15)
# ---------------------------------------------------------------------
$g = New-Grid 16 16
for($y=0;$y -lt 16;$y++){
    $n1 = Noise 11 $y 71
    $x0 = 1 + [int][Math]::Round(1.2*$n1)
    $x1 = 15 - [int][Math]::Round(1.2*(1.0-$n1))
    # the strand leaves a 1px seam near the centre, like vanilla weeping vines
    $gap = 7 + [int][Math]::Round(1.2*$n1)
    for($x=$x0;$x -le $x1;$x++){
        if($x -eq $gap){ continue }
        $m = Noise $x $y 73
        if($m -lt 0.06){ continue }
        $col = $cFol
        if($m -lt 0.38){ $col = $cFolD }
        elseif($m -gt 0.82){ $col = $cFolH }
        $g[$x,$y] = $col
    }
    # woven spines so the strand reads as a continuous hanging vine
    $sp = 2 + [int][Math]::Round(2.0*(1.0-$n1))
    $g[$sp,$y]     = $cFolD
    $g[($sp+1),$y] = $cFol
    $g[($sp+2),$y] = $cFolD
    $sp2 = 11 - [int][Math]::Round(1.5*$n1)
    $g[$sp2,$y]     = $cFol
    $g[($sp2+1),$y] = $cFolD
    $g[($sp2+2),$y] = $cFol
}
Save-Png (Join-Path $bdir 'willow_vines_plant.png') $g 16 16

# ---------------------------------------------------------------------
# 10/11. willow_door_top.png / willow_door_bottom.png
# ---------------------------------------------------------------------
$cmds = New-Object System.Collections.Generic.List[string]
Add-PlankRect $cmds 0 0 16 16
Add-RectOutline $cmds 0 0 16 16 $sD
# outer frame + centre stile
Add-SolidRect $cmds 1 1 1 14 $cPlL
Add-SolidRect $cmds 14 1 1 14 $cPlD
Add-SolidRect $cmds 0 7 16 1 $sD
Add-SolidRect $cmds 0 8 16 1 $cPlD
Add-SolidRect $cmds 7 0 1 16 $cPlD
Add-SolidRect $cmds 8 0 1 16 $cPlD
# upper half: two 3x4 glass windows (transparent, like the vanilla door)
foreach($rx in @(3,10)){
    Add-SolidRect $cmds $rx 3 3 4 $cNull
    Add-RectOutline $cmds ($rx-1) 2 5 6 $sD
    Add-Px $cmds ($rx-1) 2 $cPlL
    Add-Px $cmds ($rx+3) 2 $cPlL
    Add-SolidRect $cmds $rx 4 3 1 '#FFFFFF30'
}
# lower half: a raised plank panel
Add-SolidRect $cmds 3 10 10 4 $cPlL
Add-RectOutline $cmds 2 9 12 6 $sD
Add-SolidRect $cmds 3 10 10 1 $cPlL
Add-SolidRect $cmds 3 13 10 1 $cPlD
$dest = Join-Path $bdir 'willow_door_top.png'
Invoke-MagickDraw 16 16 $dest $cmds

$cmds = New-Object System.Collections.Generic.List[string]
Add-PlankRect $cmds 0 0 16 16
Add-RectOutline $cmds 0 0 16 16 $sD
Add-SolidRect $cmds 1 1 1 14 $cPlL
Add-SolidRect $cmds 14 1 1 14 $cPlD
Add-SolidRect $cmds 0 7 16 1 $sD
Add-SolidRect $cmds 0 8 16 1 $cPlD
Add-SolidRect $cmds 7 0 1 16 $cPlD
Add-SolidRect $cmds 8 0 1 16 $cPlD
# upper rail
Add-SolidRect $cmds 2 2 5 2 $cPlM
Add-SolidRect $cmds 9 2 5 2 $cPlM
# two tall raised panels
foreach($rx in @(2,9)){
    Add-SolidRect $cmds $rx 5 5 7 $cPlL
    Add-RectOutline $cmds $rx 5 5 7 $sD
    Add-SolidRect $cmds ($rx+1) 6 3 1 $cPlL
    Add-SolidRect $cmds ($rx+1) 11 3 1 $cPlD
}
Add-SolidRect $cmds 3 3 3 1 '#FFFFFF20'
$dest = Join-Path $bdir 'willow_door_bottom.png'
Invoke-MagickDraw 16 16 $dest $cmds

# ---------------------------------------------------------------------
# 12. willow_trapdoor.png  - plank grid, mostly opaque
# ---------------------------------------------------------------------
$cmds = New-Object System.Collections.Generic.List[string]
Add-PlankRect $cmds 1 1 14 14
# erase the 1px surround so alphaMean lands ~0.86
foreach($x in 0..15){ Add-SolidRect $cmds $x 0 1 1 $cNull; Add-SolidRect $cmds $x 15 1 1 $cNull }
foreach($y in 0..15){ Add-SolidRect $cmds 0 $y 1 1 $cNull; Add-SolidRect $cmds 15 $y 1 1 $cNull }
# a couple of corner notches
Add-SolidRect $cmds 1 1 1 1 $cNull
Add-SolidRect $cmds 14 1 1 1 $cNull
Add-SolidRect $cmds 1 14 1 1 $cNull
Add-SolidRect $cmds 14 14 1 1 $cNull
# frame + grid
Add-RectOutline $cmds 1 1 14 14 $sD
Add-SolidRect $cmds 1 5 14 1 $cPlD
Add-SolidRect $cmds 1 10 14 1 $cPlD
Add-SolidRect $cmds 5 1 1 14 $cPlD
Add-SolidRect $cmds 10 1 1 14 $cPlD
Add-SolidRect $cmds 2 2 3 1 $cPlL
Add-SolidRect $cmds 11 11 3 1 $cPlL
Add-SolidRect $cmds 6 6 1 1 $sD
Add-SolidRect $cmds 6 12 1 1 $sD
Add-SolidRect $cmds 12 7 1 1 $sD
$dest = Join-Path $bdir 'willow_trapdoor.png'
Invoke-MagickDraw 16 16 $dest $cmds

# ---------------------------------------------------------------------
# 13. willow_sign.png  (32x32, silhouette from oak_sign)
# ---------------------------------------------------------------------
$cmds = New-Object System.Collections.Generic.List[string]
# base: a big plank board covering rows 0..29, x 0..25
for($y=0;$y -lt 32;$y++){
    for($x=0;$x -lt 32;$x++){
        $cmds.Add("fill $($script:Plank[($x % 40),(($y+1) % 16)]) rectangle $x,$y $x,$y") | Out-Null
    }
}
Add-SolidRect $cmds 0 14 32 2 $cNull            # the gap between the two plank halves
Add-RectOutline $cmds 0 0 26 14 $sD
Add-RectOutline $cmds 0 16 26 14 $sD
# knots + grain on the board
foreach($k in @(@(4,4),@(17,3),@(9,20),@(20,24),@(6,26),@(15,10))){
    Add-SolidRect $cmds $k[0] $k[1] 2 2 $cPlD
    Add-Px $cmds $k[0] $k[1] $cPlL
}
Add-SolidRect $cmds 2 2 20 1 $cPlL
Add-SolidRect $cmds 3 21 18 1 $cPlL
Add-SolidRect $cmds 5 5 14 1 $cPlD
Add-SolidRect $cmds 6 24 15 1 $cPlD
$dest = "$work\sign_pre.png"
Invoke-MagickDraw 32 32 $dest $cmds
& $magick "$work\sign_pre.png" (Join-Path $van 'oak_sign.png') -alpha off -compose CopyOpacity -composite (Join-Path $bdir 'willow_sign.png')

# ---------------------------------------------------------------------
# 14. willow_hanging_sign.png  (32x32)
# ---------------------------------------------------------------------
$cmds = New-Object System.Collections.Generic.List[string]
for($y=0;$y -lt 32;$y++){
    for($x=0;$x -lt 32;$x++){
        $cmds.Add("fill $($script:Plank[($x % 40),(($y+2) % 16)]) rectangle $x,$y $x,$y") | Out-Null
    }
}
# chains rising from the board
Add-SolidRect $cmds 0 0 32 16 $sD
foreach($cx in @(17,20,24,27,29)){
    for($y=0;$y -lt 16;$y++){
        if($y % 3 -eq 1){ continue }
        Add-Px $cmds $cx $y $cBarkM
        Add-Px $cmds ($cx+1) $y $cBarkD
    }
}
Add-SolidRect $cmds 20 14 8 2 $cBarkM
# board outline + grain
Add-RectOutline $cmds 0 16 32 12 $sD
Add-SolidRect $cmds 1 17 30 1 $cPlL
Add-SolidRect $cmds 1 26 30 1 $cPlD
foreach($k in @(@(4,19),@(24,22),@(12,25),@(19,18),@(8,22))){
    Add-SolidRect $cmds $k[0] $k[1] 2 2 $cPlD
    Add-Px $cmds $k[0] $k[1] $cPlL
}
# the two side tabs at rows 26-27
Add-RectOutline $cmds 2 26 14 2 $sD
$dest = "$work\hsign_pre.png"
Invoke-MagickDraw 32 32 $dest $cmds
& $magick "$work\hsign_pre.png" (Join-Path $van 'oak_hanging_sign.png') -alpha off -compose CopyOpacity -composite (Join-Path $bdir 'willow_hanging_sign.png')

# ---------------------------------------------------------------------
# 15. willow_shelf.png  (32x32, fully opaque)
# ---------------------------------------------------------------------
$cmds = New-Object System.Collections.Generic.List[string]
for($y=0;$y -lt 32;$y++){ for($x=0;$x -lt 32;$x++){
    $cmds.Add("fill $($script:Plank[($x % 40),(($y+3) % 16)]) rectangle $x,$y $x,$y") | Out-Null } }
# three vertical dividers make four cubbies, plus shelf rails
foreach($dx in @(7,15,23)){ Add-SolidRect $cmds $dx 0 2 32 $cPlD; Add-SolidRect $cmds ($dx+2) 0 1 32 $sT }
Add-SolidRect $cmds 0 0 32 3 $cPlM
Add-SolidRect $cmds 0 0 32 1 $cPlL
Add-SolidRect $cmds 0 2 32 1 $cPlD
Add-SolidRect $cmds 0 29 32 3 $cPlM
Add-SolidRect $cmds 0 29 32 1 $cPlD
Add-SolidRect $cmds 0 31 32 1 $cPlL
Add-RectOutline $cmds 0 0 32 32 $sD
# knots
foreach($k in @(@(3,9),@(11,20),@(19,6),@(27,25),@(4,25),@(20,27))){
    Add-SolidRect $cmds $k[0] $k[1] 2 2 $cPlD
    Add-Px $cmds $k[0] $k[1] $cPlL
}
$dest = Join-Path $bdir 'willow_shelf.png'
Invoke-MagickDraw 32 32 $dest $cmds

# ---------------------------------------------------------------------
# 16. willow_door.png  (16x16 item sprite, flat door)
# ---------------------------------------------------------------------
$g = New-Grid 16 16
# door body
GridRect $g 3 1 10 14 $cPlM
for($y=1;$y -lt 15;$y++){ for($x=3;$x -lt 13;$x++){
    $n = Noise $x $y 91
    if($n -gt 0.86){ $g[$x,$y] = $cPlL } elseif($n -lt 0.14){ $g[$x,$y] = $cPlD }
} }
# outline
GridRect $g 3 1 10 1 $sD
GridRect $g 3 14 10 1 $sD
GridRect $g 3 1 1 14 $sD
GridRect $g 12 1 1 14 $sD
GridRect $g 3 1 1 14 $sT
# window (transparent, like the vanilla door sprite)
GridRect $g 5 3 2 3 $cNull
GridRect $g 9 3 2 3 $cNull
foreach($r in @(@(5,3),@(9,3))){
    GridRect $g $r[0]-1 $r[1]-1 4 1 $sD
    GridRect $g $r[0]-1 ($r[1]+3) 4 1 $sD
    GridRect $g ($r[0]-1) $r[1] 1 3 $sD
    GridRect $g ($r[0]+2) $r[1] 1 3 $sD
}
# lower panel
GridRect $g 5 8 6 5 $cPlM
GridRect $g 5 8 6 1 $cPlL
GridRect $g 5 12 6 1 $cPlD
GridRect $g 5 8 1 5 $cPlD
GridRect $g 10 8 1 5 $cPlD
# handle
GridRect $g 11 9 1 2 $sD
Save-Png (Join-Path $idir 'willow_door.png') $g 16 16

# ---------------------------------------------------------------------
# 17. willow_sign.png  (16x16 item sprite)
# ---------------------------------------------------------------------
$g = New-Grid 16 16
GridRect $g 2 3 10 8 $cPlM
for($y=3;$y -lt 11;$y++){ for($x=2;$x -lt 12;$x++){
    $n = Noise $x $y 97
    if($n -gt 0.88){ $g[$x,$y] = $cPlL } elseif($n -lt 0.12){ $g[$x,$y] = $cPlD }
} }
GridRect $g 2 3 10 1 $sD
GridRect $g 2 10 10 1 $sD
GridRect $g 2 3 1 8 $sD
GridRect $g 11 3 1 8 $sD
GridRect $g 3 4 8 1 $cPlL
# post
GridRect $g 7 11 2 5 $cBarkM
GridRect $g 7 11 1 5 $cBarkD
GridRect $g 8 11 1 5 $cBarkL
GridRect $g 7 15 2 1 $sD
# grain lines
GridRect $g 4 6 6 1 $cPlD
GridRect $g 3 8 7 1 $cPlD
# knot
GridRect $g 6 5 1 1 $cPlD
Save-Png (Join-Path $idir 'willow_sign.png') $g 16 16

# ---------------------------------------------------------------------
# 18. willow_hanging_sign.png  (16x16 item sprite)
# ---------------------------------------------------------------------
$g = New-Grid 16 16
# chains
foreach($cx in @(4,11)){
    for($y=0;$y -lt 8;$y++){
        $xx = $cx + [int][Math]::Floor($y/3)
        if($y % 3 -eq 1){ $g[$xx,$y] = $cBarkD } else { $g[$xx,$y] = $cBarkM }
    }
}
GridRect $g 4 7 8 1 $cBarkM
# board
GridRect $g 2 8 12 6 $cPlM
for($y=8;$y -lt 14;$y++){ for($x=2;$x -lt 14;$x++){
    $n = Noise $x $y 101
    if($n -gt 0.88){ $g[$x,$y] = $cPlL } elseif($n -lt 0.12){ $g[$x,$y] = $cPlD }
} }
GridRect $g 2 8 12 1 $sD
GridRect $g 2 13 12 1 $sD
GridRect $g 2 8 1 6 $sD
GridRect $g 13 8 1 6 $sD
GridRect $g 3 9 10 1 $cPlL
GridRect $g 4 11 8 1 $cPlD
Save-Png (Join-Path $idir 'willow_hanging_sign.png') $g 16 16

# ---------------------------------------------------------------------
# 19/20. boat + chest boat item sprites
#        silhouette + shading structure from vanilla, recoloured to willow
# ---------------------------------------------------------------------
& $magick -size 1x7 `
    "xc:#5F5741" `
    -fill '#7A7055' -draw "point 0,1" `
    -fill '#8E7F55' -draw "point 0,2" `
    -fill '#A8996B' -draw "point 0,3" `
    -fill '#BDAD7C' -draw "point 0,4" `
    -fill '#CDBF90' -draw "point 0,5" `
    -fill '#DED3A8' -draw "point 0,6" `
    "$work\boat_lut.png"

function New-BoatItem([string]$ref,[string]$out){
    & $magick (Join-Path $van $ref) -colorspace Gray -alpha off "$work\bi_gray.png"
    & $magick "$work\bi_gray.png" -interpolate bilinear "$work\boat_lut.png" -clut -alpha off "$work\bi_lum.png"
    & $magick (Join-Path $van $ref) -alpha extract -threshold 50% "$work\bi_sil.png"
    & $magick "$work\bi_lum.png" "$work\bi_sil.png" -alpha off -compose CopyOpacity -composite $out
}
New-BoatItem 'oak_boat.png'       (Join-Path $idir 'willow_boat.png')
New-BoatItem 'oak_chest_boat.png' (Join-Path $idir 'willow_chest_boat.png')

# ---------------------------------------------------------------------
# 21. entity/boat/willow.png  (128x64)
# ---------------------------------------------------------------------
function New-BoxCmds($cmds,[int]$u,[int]$v,[int]$w,[int]$h,[int]$d,[string]$base,[string]$light){
    # region geometry (see BoatModel / Cube net)
    $t  = @($u,        $v,     $d, $d)    # top
    $s1 = @(($u+$d),   $v,     $w, $d)    # long side 1
    $bo = @(($u+$d+$w),$v,     $d, $d)    # bottom
    $s2 = @(($u+$d+$w+$d),$v,  $w, $d)    # long side 2
    $e1 = @($u,        ($v+$d),$d, $h)    # short side 1
    $f  = @(($u+$d),   ($v+$d),$w, $h)    # big face
    $e2 = @(($u+$d+$w),($v+$d),$d, $h)    # short side 2
    $regions = @($t,$s1,$bo,$s2,$e1,$f,$e2)
    foreach($r in $regions){
        $rx=$r[0]; $ry=$r[1]; $rw=$r[2]; $rh=$r[3]
        if($rw -le 0 -or $rh -le 0){ continue }
        for($y=0;$y -lt $rh;$y++){
            for($x=0;$x -lt $rw;$x++){
                $n = Noise ($rx+$x) ($ry+$y) 7
                $col = $script:Plank[(($rx+$x)%40), (($ry+$y)%16)]
                if($n -gt 0.93){ $col = $light }
                elseif($n -lt 0.07){ $col = $base }
                Add-Px $cmds ($rx+$x) ($ry+$y) $col
            }
        }
        # 1px darker outline between the sub-rects
        Add-RectOutline $cmds $rx $ry $rw $rh $sD
    }
    # horizontal plank seams across the whole net
    $seamYs = @(($v+$d+3), ($v+$d+8), ($v+$d+13))
    foreach($r in $regions){
        $rx=$r[0]; $ry=$r[1]; $rw=$r[2]; $rh=$r[3]
        for($y=0;$y -lt $rh;$y++){
            $gy = $ry + $y
            foreach($sy in $seamYs){
                if($gy -eq $sy){
                    for($x=0;$x -lt $rw;$x++){ Add-Px $cmds ($rx+$x) $gy $cPlD }
                }
            }
        }
    }
}

$cmds = New-Object System.Collections.Generic.List[string]
New-BoxCmds $cmds 0  0  28 16 3  $cPlD $cPlL
New-BoxCmds $cmds 0  19 18 6  2  $cPlD $cPlL
New-BoxCmds $cmds 0  27 16 6  2  $cPlD $cPlL
New-BoxCmds $cmds 0  35 28 6  2  $cPlD $cPlL
New-BoxCmds $cmds 0  43 28 6  2  $cPlD $cPlL
New-BoxCmds $cmds 62 0  2  2  18 $cPlD $cPlL   # left paddle shaft
New-BoxCmds $cmds 62 0  1  6  7  $cPlD $cPlL   # left paddle blade (task-specified offset)
New-BoxCmds $cmds 62 20 2  2  18 $cPlD $cPlL   # right paddle shaft
New-BoxCmds $cmds 62 20 1  6  7  $cPlD $cPlL   # right paddle blade (task-specified offset)
$dest = Join-Path $edir 'willow.png'
Invoke-MagickDraw 128 64 $dest $cmds

# ---------------------------------------------------------------------
# 22. entity/chest_boat/willow.png  (128x128)
# ---------------------------------------------------------------------
$cmds = New-Object System.Collections.Generic.List[string]
New-BoxCmds $cmds 0  0  28 16 3  $cPlD $cPlL
New-BoxCmds $cmds 0  19 18 6  2  $cPlD $cPlL
New-BoxCmds $cmds 0  27 16 6  2  $cPlD $cPlL
New-BoxCmds $cmds 0  35 28 6  2  $cPlD $cPlL
New-BoxCmds $cmds 0  43 28 6  2  $cPlD $cPlL
New-BoxCmds $cmds 62 0  2  2  18 $cPlD $cPlL
New-BoxCmds $cmds 62 0  1  6  7  $cPlD $cPlL
New-BoxCmds $cmds 62 20 2  2  18 $cPlD $cPlL
New-BoxCmds $cmds 62 20 1  6  7  $cPlD $cPlL
# chest: lid (12x4x12) then bottom (12x8x12), both in the chest palette
New-BoxCmds $cmds 0  59 12 4  12 $cChestD $cChestL
New-BoxCmds $cmds 0  76 12 8  12 $cChestD $cChestM
# the chest lid sits directly above the chest bottom: darken the seam row
Add-SolidRect $cmds 0 76 40 1 $cChestD
# lock: 2x4x1, painted explicitly in the lock colour
New-BoxCmds $cmds 0 59 2 4 1 $cLock $cLock
Add-SolidRect $cmds 0 59 6 10 $cLock
Add-SolidRect $cmds 0 59 6 1 $cChestD
Add-SolidRect $cmds 0 64 6 1 $cChestD
$dest = Join-Path $cdir 'willow.png'
Invoke-MagickDraw 128 128 $dest $cmds

# ---------------------------------------------------------------------
# 23. icon.png  (128x128, opaque) - daffodil (narcissus)
# ---------------------------------------------------------------------
# A flat six-pointed star of white/cream petals, a golden-orange trumpet in
# the middle and a leafy green stem running off the bottom edge.
function Get-AngCos([double]$q){ return [Math]::Cos($q) }
function Get-AngSin([double]$q){ return [Math]::Sin($q) }

function Add-Petal($ig,[double]$fAng){
    $pCx = 63.5; $pCy = 52.0
    $pBase = 15.0; $pLen = 44.0
    $pAx = Get-AngCos $fAng; $pAy = Get-AngSin $fAng
    $pPx = -1.0 * $pAy;     $pPy = $pAx
    for($py=0;$py -lt 128;$py++){
        for($px=0;$px -lt 128;$px++){
            $pDx = ([double]$px) - $pCx
            $pDy = ([double]$py) - $pCy
            $pa = ($pDx * $pAx) + ($pDy * $pAy)
            if($pa -lt $pBase){ continue }
            $pt = ($pa - $pBase) / $pLen
            if($pt -gt 1.0){ continue }
            $pS = ($pDx * $pPx) + ($pDy * $pPy)
            $pHw = 9.0 * [Math]::Sin([Math]::PI * [Math]::Pow($pt, 1.15))
            $pHw = $pHw + 1.0
            $pTip = ($pt - 0.72) / 0.28
            if($pTip -gt 0.0){ $pHw = $pHw - ($pTip * $pTip * $pHw) }
            if([Math]::Abs($pS) -gt $pHw){ continue }
            $pN = Noise ($px*3) ($py*7) (301 + [int]($fAng*10.0))
            $pCol = $cPetM
            if($pDx + $pDy -lt -22.0){ $pCol = $cPetH }
            elseif($pDx + $pDy -gt 20.0){ $pCol = $cPetD }
            if([Math]::Abs($pS) -gt ($pHw - 1.2)){ $pCol = $cPetD }
            if($pt -gt 0.86){ $pCol = $cPetD }
            if([Math]::Abs($pS) -lt 0.9){
                $pCol = $cPetH
                if($pN -lt 0.25){ $pCol = $cPetD }
            }
            if($pN -gt 0.965){ $pCol = $cPetD }
            $ig[$px,$py] = $pCol
        }
    }
}

# A long blade-shaped leaf: curve from (bX0,bDist) to (bX1,bDist2), given as
# signed perpendicular offsets from the vertical stem at x=63.5, so the blade
# always starts on the stem even when bX1 is far outside the canvas. The blade
# is widest in the middle, and every second row gains an extra pixel on its
# outer side so a slanted blade still looks continuous.
function Add-Blade($ig,[int]$bD0,[double]$bY0,[int]$bDist2,[double]$bY1,[double]$bWmax,[double]$bTaper){
    $bDy = $bY1 - $bY0
    if([Math]::Abs($bDy) -lt 0.001){ return }
    $bSgn = 1.0
    if($bDy -lt 0.0){ $bSgn = -1.0 }
    $bOuter = 1
    if($bDist2 -lt $bD0){ $bOuter = -1 }
    $bRow = $bY0
    while((($bSgn -gt 0.0) -and ($bRow -le $bY1)) -or (($bSgn -lt 0.0) -and ($bRow -ge $bY1))){
        $bRy = [int][Math]::Round($bRow)
        if($bRy -lt 0 -or $bRy -gt 127){ $bRow = $bRow + $bSgn; continue }
        $bT = ($bRow - $bY0) / $bDy
        if($bT -lt 0.0){ $bT = 0.0 } elseif($bT -gt 1.0){ $bT = 1.0 }
        $bDist = ([double]$bD0) + ((([double]$bDist2) - ([double]$bD0)) * $bT * $bT)
        $bCx = 63.5 + $bDist
        $bTp = $bT / $bTaper
        if($bTp -gt 1.0){ $bTp = 1.0 }
        $bHw = $bWmax * [Math]::Pow([Math]::Sin([Math]::PI * $bTp), 0.75)
        if($bHw -lt 1.6){ $bHw = 1.6 }
        if($bT -gt 0.74){ $bHw = $bHw * (($bT - 0.74) / 0.26) }
        if($bHw -lt 0.9){ $bHw = 0.9 }
        $bX0i = [int][Math]::Round($bCx - $bHw)
        $bX1i = [int][Math]::Round($bCx + $bHw)
        if((($bRy % 2) -eq 0) -and ($bSgn -gt 0.0) -and ($bT -lt 0.72)){
            if($bOuter -gt 0){ $bX1i = $bX1i + 1 } else { $bX0i = $bX0i - 1 }
        }
        for($bX=$bX0i;$bX -le $bX1i;$bX++){
            if($bX -lt 0 -or $bX -gt 127){ continue }
            $bN = Noise $bX ($bRy*5) 311
            $bCol = $cStemL
            if(($bX -eq $bX0i) -or ($bX -eq $bX1i)){ $bCol = $cStemD }
            if([Math]::Abs(([double]$bX) - $bCx) -lt 0.7){ $bCol = '#8DBB58' }
            if($bN -gt 0.86 -and $bCol -ne $cStemD){ $bCol = '#8DBB58' }
            $ig[$bX,$bRy] = $bCol
        }
        $bRow = $bRow + $bSgn
    }
}

function Add-Trumpet($ig){
    $tCx = 63.5; $tCy = 52.0
    for($ty=0;$ty -lt 128;$ty++){
        for($tx=0;$tx -lt 128;$tx++){
            $tDx = ([double]$tx) - $tCx
            $tDy = ([double]$ty) - $tCy
            $tD = [Math]::Sqrt(($tDx*$tDx) + ($tDy*$tDy))
            if($tD -gt 17.6){ continue }
            $tCol = $cTruM
            if($tD -gt 16.0){ $tCol = $cTruD }
            if($tD -gt 16.85){ $tCol = $cTruO }
            if($tD -lt 3.6){ $tCol = $cTruD }
            $tLit = ($tDx + $tDy) / 2.0
            if($tLit -lt -4.0){ $tCol = $cTruO }
            if($tLit -gt 7.0){ $tCol = $cTruD }
            if($tLit -gt 12.0){ $tCol = $cTruSh }
            # bright rim highlight on the upper-left of the corona mouth
            if($tD -gt 13.6 -and $tD -lt 16.1 -and $tLit -lt -1.0){ $tCol = $cTruRim }
            if($tD -gt 15.0 -and $tLit -lt -6.0){ $tCol = $cTruRim }
            $ig[$tx,$ty] = $tCol
        }
    }
    # little stamen dot so the trumpet mouth reads as a cup
    GridRect $ig 62 52 4 3 $cTruRim
    GridRect $ig 63 53 2 1 $cTruO
}

$ig = New-Grid 128 128
# background: subtle vertical gradient
for($gy=0;$gy -lt 128;$gy++){
    GridRect $ig 0 $gy 128 1 (Get-MixHex $cIconT $cIconB ($gy / 127.0))
}
# stem: starts under the bloom and runs off the bottom edge
for($sy=62;$sy -lt 128;$sy++){
    $sT = ([double]($sy - 62)) / 65.0
    $sCx = 63.5 + ($sT * $sT * 2.0)
    $sHw = 3.2 - ($sT * 1.2)
    $sX0 = [int][Math]::Round($sCx - $sHw)
    $sX1 = [int][Math]::Round($sCx + $sHw)
    for($sx=$sX0;$sx -le $sX1;$sx++){
        if($sx -lt 0 -or $sx -gt 127){ continue }
        $sN = Noise $sx $sy 331
        $sCol = $cStemD
        if($sx -le ($sX0 + 1)){ $sCol = $cStemL }
        if($sN -gt 0.72 -and $sCol -ne $cStemD){ $sCol = $cStemD }
        $ig[$sx,$sy] = $sCol
    }
}
# leaves: two long blades fanning out from the lower stem, plus a shorter
# inner pair. They start below the bloom so the bottom petal stays clean.
# Arguments are signed horizontal offsets from the stem, so every blade
# starts on the stem and fans outwards to the bottom edge.
Add-Blade $ig 0     110.0  28.0 168.0 5.5 0.62
Add-Blade $ig 0     110.0 -28.0 168.0 5.5 0.62
Add-Blade $ig -4.0  120.0  24.0 168.0 4.5 0.50
Add-Blade $ig 4.0   120.0 -24.0 168.0 4.5 0.50
# six petals at 30/90/150/210/270/330 degrees
foreach($pDeg in @(90.0,150.0,210.0,270.0,330.0,30.0)){
    Add-Petal $ig ($pDeg * [Math]::PI / 180.0)
}
Add-Trumpet $ig
# soft drop shadow: darken background pixels just below/right of the bloom and
# stem, so the flower sits in front of the gradient instead of on top of it
$igC = New-Object 'string[,]' 128,128
for($iy=0;$iy -lt 128;$iy++){ for($ix=0;$ix -lt 128;$ix++){ $igC[$ix,$iy] = $ig[$ix,$iy] } }
$shapeSet = New-Object 'System.Collections.Generic.HashSet[string]'
foreach($p in @('F2EFE0','FFFFFF','D9D4BE','F5C542','E8A317','C67C0E','FFE08A','4E7A2E','6B9B3F','8DBB58')){
    $shapeSet.Add($p) | Out-Null
}
for($iy=0;$iy -lt 128;$iy++){
    for($ix=0;$ix -lt 128;$ix++){
        $iCol = $igC[$ix,$iy]
        if($shapeSet.Contains($iCol)){ continue }
        $iTouch = $false
        foreach($iv in @(@(0,-1),@(-1,-1),@(1,-1),@(-1,0),@(1,0),@(0,-2),@(-2,0),@(2,0))){
            $ixx = $ix + $iv[0]; $iyy = $iy + $iv[1]
            if($ixx -lt 0 -or $iyy -lt 0 -or $ixx -gt 127 -or $iyy -gt 127){ continue }
            $iNbr = $igC[$ixx,$iyy]
            if($shapeSet.Contains($iNbr)){ $iTouch = $true; break }
        }
        if($iTouch){ $ig[$ix,$iy] = Get-MixHex $iCol '#14240F' 0.50 }
    }
}
Save-Png (Join-Path $assets 'icon.png') $ig 128 128

# ---------------------------------------------------------------------
# 24/25. willow_bark.png + willow_bark_pieces.png  (16x16 item sprites)
# ---------------------------------------------------------------------
# A strip of bark peeled off the trunk: it rises in a flat S-curve, widening
# towards the bottom, and its top two rows roll away to show the paler inner
# face. The shape mask is filled in as the strip is painted, so the outline
# pass can trace the rim afterwards.
$g  = New-Grid 16 16
$bs = New-Object 'bool[,]' 16,16
# Spine of the strip, bottom row first. Two explicit integer tables (left and
# right edge per row) instead of trig: PowerShell 5.1 mishandled a computed
# float centre/width pair here and collapsed every band to the same columns.
$edgeL = @(1,2,2,2,2,3,3,3,3,4,4,4,4,5,5,6)
$edgeR = @(7,8,8,8,8,9,9,9,9,9,10,10,10,10,10,10)
# Explicit per-row colour pattern inside the band, left to right:
#   D = bark dark shaded rim, m = bark mid body, L = bark light lit rim.
$barkPat = @(
    'DmmmmLm',      # row  0: x 1..7   (7)
    'DDmmmLL',      # row  1: x 2..8   (7)
    'DLmmmLm',      # row  2: x 2..8   (7)
    'DmDmmPL',      # row  3: x 2..8   (7)
    'DDmmmLL',      # row  4: x 2..8   (7)
    'LmDmmmL',      # row  5: x 3..9   (7)
    'DDmLmmL',      # row  6: x 3..9   (7)
    'mmmmmmL',      # row  7: x 3..9   (7)
    'DDmLmmL',      # row  8: x 3..9   (7)
    'DDmmLL',       # row  9: x 4..9   (6)
    'DDmmmLL',      # row 10: x 4..10  (7)
    'DDmmmLm',      # row 11: x 4..10  (7)
    'DDmLLLL',      # row 12: x 4..10  (7)
    'DDmmLL',       # row 13: x 5..10  (6)
    'DDDmLL',       # row 14: x 5..10  (6)
    'LmDDmm'        # row 15: x 6..10  (5)
)
for($sy=0;$sy -lt 16;$sy++){
    $sL = $edgeL[$sy]; $sR = $edgeR[$sy]
    $sPat = $barkPat[$sy]
    for($x=$sL;$x -le $sR;$x++){
        if($x -lt 0 -or $x -gt 15){ continue }
        $bs[$x,$sy] = $true
        $sCh = $sPat.Substring($x - $sL, 1)
        $col = $cBarkM
        if($sCh -eq 'D'){ $col = $cBarkD }
        elseif($sCh -eq 'L'){ $col = $cBarkL }
        elseif($sCh -eq 'P'){ $col = $cBarkIn }
        $g[$x,$sy] = $col
    }
}
# paler inner face: the top two rows of the strip roll away from the trunk,
# so their upper surface is the pale inner bark rather than the rough outer one
foreach($f in @(@(3,0),@(4,0),@(5,0),@(6,0),@(7,0),@(4,1),@(5,1),@(6,1),@(7,1),@(9,1),@(9,2),@(10,2),@(9,3),@(10,3))){
    $g[$f[0],$f[1]] = $cBarkIn
    $bs[$f[0],$f[1]] = $true
}
$g[3,0] = $cStrM
$g[4,0] = $cStrL
$g[6,0] = $cStrM
$g[5,1] = $cStrL
$g[8,1] = $cStrM
$g[10,2] = $cStrL
$g[9,3] = $cStrM
# cracks and a torn bottom edge
foreach($c in @(@(4,12),@(5,10),@(4,8),@(6,6),@(4,14))){
    $g[$c[0],$c[1]] = $cBarkD
}
$g[5,15] = $cBarkL
# Outline: paint the dark rim on the background pixels just outside the shape,
# then copy the interior back. (The interior cannot simply be overwritten here:
# that would flatten the surface pattern painted above into one dark tone.)
$gSurf = New-Object 'string[,]' 16,16
for($cy=0;$cy -lt 16;$cy++){ for($cx=0;$cx -lt 16;$cx++){ $gSurf[$cx,$cy] = $g[$cx,$cy] } }
for($oy=0;$oy -lt 16;$oy++){
    for($ox=0;$ox -lt 16;$ox++){
        if($bs[$ox,$oy]){ continue }
        $oTouch = $false
        foreach($ov in @(@(1,0),@(-1,0),@(0,1),@(0,-1))){
            $oxx = $ox + $ov[0]; $oyy = $oy + $ov[1]
            if($oxx -lt 0 -or $oyy -lt 0 -or $oxx -gt 15 -or $oyy -gt 15){ continue }
            if($bs[$oxx,$oyy]){ $oTouch = $true; break }
        }
        if($oTouch){ $g[$ox,$oy] = '#3A3426' }
    }
}
for($cy=0;$cy -lt 16;$cy++){
    for($cx=0;$cx -lt 16;$cx++){
        if($bs[$cx,$cy]){ $g[$cx,$cy] = $gSurf[$cx,$cy] }
    }
}
Save-Png (Join-Path $idir 'willow_bark.png') $g 16 16

$g = New-Grid 16 16
# five chips, each an irregular short run of pixels, plus a few crumbs
$chips = @(
    @(2,2,3,0), @(6,3,3,1), @(10,2,2,0), @(4,7,4,1), @(10,8,2,0)
)
foreach($ch in $chips){
    $chx = $ch[0]; $chy = $ch[1]; $chw = $ch[2]; $chv = $ch[3]
    for($r=0;$r -lt $chw;$r++){
        $j = 0
        $n = Noise ($chx+$r) ($chy+$r) (421 + $chw)
        if($n -gt 0.62){ $j = 1 } elseif($n -lt 0.34){ $j = -1 }
        $wl = $chw - [Math]::Abs($r - [int]($chw / 2))
        if($chv -eq 1){ $wl = $chw - $r }
        if($wl -gt $chw){ $wl = $chw }
        if($wl -lt 1){ $wl = 1 }
        for($k=0;$k -lt $wl;$k++){
            $cx2 = $chx + $k + $j
            $cy2 = $chy + $r
            if($cx2 -lt 0 -or $cy2 -lt 0 -or $cx2 -gt 15 -or $cy2 -gt 15){ continue }
            $cn = Noise $cx2 ($cy2*3) (431 + $r + $chx)
            $col = $cBarkM
            if($k -eq 0){ $col = $cBarkL }
            if($k -eq ($wl-1)){ $col = $cBarkD }
            if($cn -gt 0.74){ $col = $cBarkD } elseif($cn -lt 0.22){ $col = $cBarkL }
            if($r -eq 0){ $col = $cBarkL }
            if($r -eq ($chw-1)){ $col = $cBarkD }
            $g[$cx2,$cy2] = $col
        }
    }
}
$g[6,5]  = $cBarkD
$g[5,7]  = $cBarkD
$g[7,10] = $cBarkD
$g[10,9] = $cBarkD
$g[3,3]  = $cBarkL
$g[11,3] = $cBarkL
# crumbs
foreach($cm in @(@(1,6),@(8,1),@(13,6),@(2,11),@(12,12),@(7,13),@(14,1))){
    $g[$cm[0],$cm[1]] = $cBarkD
}
$g[13,6] = $cBarkM
$g[7,13] = $cBarkM
$g[2,11] = $cBarkIn
$g[12,12] = $cBarkIn
Save-Png (Join-Path $idir 'willow_bark_pieces.png') $g 16 16

# ---------------------------------------------------------------------
# 26/27. raw / cooked willow bark soup bowl  (16x16 item sprites)
#        The silhouette and the shading structure are copied pixel for pixel
#        from the vanilla mushroom_stew sprite (extracted above); the wooden
#        bowl is recoloured to the willow palette and only the broth inside
#        the opening differs. Role map, one character per pixel:
#          .  clear        s  bowl wall / inner wall    b  bowl body
#          o  outer shadow r  lit rim                   B/S/H  broth tones
# ---------------------------------------------------------------------
# Optional $grainCoords / $grainCol: a handful of salt grains scattered over
# the finished surface, used by the salted variants of the willow bark soups.
function New-SoupBowl([string]$colOut,[string]$brothBase,[string]$brothShad,[string]$brothLite,[bool]$raw,
                      [array]$grainCoords = $null,[string]$grainCol = ''){
    if($raw){
        # vanilla broth placement: the lit band sits across the middle row
        $rows = @(
            '................',
            '................',
            '................',
            '................',
            '................',
            '.....oooooo.....',
            '...ssSSBBSSss...',
            '..sSHHHHBBBSSo..',
            '..sssSSBBSSsso..',
            '..srrsssssssoo..',
            '...obbbbbbsoo...',
            '....oosbbsoo....',
            '......oooo......',
            '................',
            '................',
            '................'
        )
    } else {
        # cooked broth: a smooth, even surface so the two bubbles sitting in
        # the middle row read clearly (raw instead keeps vanilla's lit band)
        $rows = @(
            '................',
            '................',
            '................',
            '................',
            '................',
            '.....oooooo.....',
            '...ssSSBBSSss...',
            '..sSBBBBBBBBSo..',
            '..sssSSBBSSsso..',
            '..srrsssssssoo..',
            '...obbbbbbsoo...',
            '....oosbbsoo....',
            '......oooo......',
            '................',
            '................',
            '................'
        )
    }
    foreach($bLine in $rows){ if($bLine.Length -ne 16){ throw "bowl row is not 16px: [$bLine]" } }
    $g = New-Grid 16 16
    for($by=0;$by -lt 16;$by++){
        $bLine = $rows[$by]
        for($bx=0;$bx -lt 16;$bx++){
            $bCh = $bLine.Substring($bx,1)
            # NOTE: -ceq, not -eq: PowerShell's -eq is case-insensitive, so the
            # uppercase broth roles would collide with the lowercase bowl ones.
            if($bCh -ceq 's'){ $g[$bx,$by] = $cBowlShad }
            elseif($bCh -ceq 'b'){ $g[$bx,$by] = $cBowlWood }
            elseif($bCh -ceq 'o'){ $g[$bx,$by] = $cBowlEdge }
            elseif($bCh -ceq 'r'){ $g[$bx,$by] = $cBowlRim }
            elseif($bCh -ceq 'B'){ $g[$bx,$by] = $brothBase }
            elseif($bCh -ceq 'S'){ $g[$bx,$by] = $brothShad }
            elseif($bCh -ceq 'H'){ $g[$bx,$by] = $brothLite }
        }
    }
    if($raw){
        # murky, cloudy broth: a deterministic cloud pass over the base tone
        for($by=6;$by -le 8;$by++){
            for($bx=0;$bx -lt 16;$bx++){
                if($g[$bx,$by] -ne $brothBase){ continue }
                $bn = Noise $bx $by 613
                if($bn -gt 0.86){ $g[$bx,$by] = $brothLite }
                elseif($bn -lt 0.24){ $g[$bx,$by] = $brothShad }
            }
        }
        # a few fibrous bark flecks still suspended in the broth
        foreach($bf in @(@(8,6),@(10,7),@(7,8))){ $g[$bf[0],$bf[1]] = $cFleckA }
        $g[7,6] = $cFleckB
        $g[8,8] = $cFleckB
    } else {
        # two tiny bubbles: a bright cap with a dark pinch below it
        foreach($bb in @(@(5,7),@(9,7))){
            $g[$bb[0],$bb[1]]     = $brothLite
            $g[($bb[0]+1),$bb[1]] = $brothLite
            $g[$bb[0],($bb[1]+1)] = $brothShad
        }
    }
    # the salt grains go on last so they always sit on the surface
    if($null -ne $grainCoords){
        foreach($sgp in $grainCoords){ GridPx $g $sgp[0] $sgp[1] $grainCol }
    }
    Save-Png $colOut $g 16 16
}
New-SoupBowl (Join-Path $idir 'raw_willow_bark_soup_bowl.png') $cBrothRB $cBrothRS $cBrothRH $true
New-SoupBowl (Join-Path $idir 'willow_bark_soup_bowl.png')     $cBrothCB $cBrothCS $cBrothCH $false

# ---------------------------------------------------------------------
# 28/29. raw / cooked willow bark soup bottle  (16x16 item sprites)
#        Silhouette and shading structure copied pixel for pixel from the
#        vanilla dragon_breath sprite (extracted above); the glass and the
#        cork are recoloured and only the liquid differs. Role map:
#          .  clear       W  glass highlight   G  glass mid   D  glass shade
#          O  glass line  C  cork light        K  cork dark
#          B/S/L  liquid base / shadow / surface highlight
# ---------------------------------------------------------------------
# Optional $speckCol overrides the suspended-fleck colour of the cloudy (raw)
# variant, so the same bottle can hold murky brine instead of murky broth.
# Optional $grainCoords / $grainCol: a handful of salt grains scattered over
# the finished liquid surface, used by the salted water bottles. The whole
# silhouette is identical for every water type; only the three liquid tones,
# the speck colour and the grains differ.
function New-SoupBottle([string]$colOut,[string]$brothBase,[string]$brothShad,[string]$brothLite,[bool]$raw,
                        [string]$speckCol = '',[array]$grainCoords = $null,[string]$grainCol = ''){
    $rows = @(
        '................',
        '................',
        '.......CCK......',
        '......WCCCG.....',
        '......GKKKG.....',
        '.......GSD......',
        '.......GSD......',
        '......WSLSD.....',
        '.....WSGBSSD....',
        '....WBGSBBLBO...',
        '....WBGBBBBBO...',
        '....GBBBBBBBO...',
        '....OBBBLLLBO...',
        '.....OBLLLBO....',
        '......OOOOO.....',
        '................'
    )
    foreach($bLine in $rows){ if($bLine.Length -ne 16){ throw "bottle row is not 16px: [$bLine]" } }
    $g = New-Grid 16 16
    for($by=0;$by -lt 16;$by++){
        $bLine = $rows[$by]
        for($bx=0;$bx -lt 16;$bx++){
            $bCh = $bLine.Substring($bx,1)
            if($bCh -ceq 'W'){ $g[$bx,$by] = $cGlassH }
            elseif($bCh -ceq 'G'){ $g[$bx,$by] = $cGlassM }
            elseif($bCh -ceq 'D'){ $g[$bx,$by] = $cGlassD }
            elseif($bCh -ceq 'O'){ $g[$bx,$by] = $cGlassOl }
            elseif($bCh -ceq 'C'){ $g[$bx,$by] = $cCorkLite }
            elseif($bCh -ceq 'K'){ $g[$bx,$by] = $cCorkDark }
            elseif($bCh -ceq 'B'){ $g[$bx,$by] = $brothBase }
            elseif($bCh -ceq 'S'){ $g[$bx,$by] = $brothShad }
            elseif($bCh -ceq 'L'){ $g[$bx,$by] = $brothLite }
        }
    }
    if($speckCol -eq ''){ $speckCol = $cFleckA }
    if($raw){
        # cloudy broth: a deterministic cloud pass over the base tone
        for($by=7;$by -le 13;$by++){
            for($bx=0;$bx -lt 16;$bx++){
                if($g[$bx,$by] -ne $brothBase){ continue }
                $bn = Noise $bx $by 631
                if($bn -gt 0.80){ $g[$bx,$by] = $brothLite }
                elseif($bn -lt 0.26){ $g[$bx,$by] = $brothShad }
            }
        }
        # a couple of dark specks still suspended in the broth
        $g[7,10]  = $speckCol
        $g[11,12] = $speckCol
    }
    # the salt grains go on last so they always sit on the surface
    if($null -ne $grainCoords){
        foreach($bgp in $grainCoords){ GridPx $g $bgp[0] $bgp[1] $grainCol }
    }
    Save-Png $colOut $g 16 16
}
New-SoupBottle (Join-Path $idir 'raw_willow_bark_soup_bottle.png') $cBrothRB $cBrothRS $cBrothRH $true
New-SoupBottle (Join-Path $idir 'willow_bark_soup_bottle.png')     $cBrothCB $cBrothCS $cBrothCH $false

# ---------------------------------------------------------------------
# 30/31. cauldron liquid tops  (16x16, fully opaque)
#        Applied to a flat model face, so this is the broth surface seen
#        from directly above: still, with soft swirls and bark fibres.
# ---------------------------------------------------------------------
function New-SoupTop([string]$colOut,[string]$baseCol,[string]$darkCol,[string]$lightCol,
                     [string]$swirlCol,[string]$patchCol,[bool]$cooked){
    $g = New-Grid 16 16
    # height field: four crossed sine waves plus soft domes, so the surface
    # has broad slow swirls rather than pixel-sized static
    for($y=0;$y -lt 16;$y++){
        for($x=0;$x -lt 16;$x++){
            $mv = 0.0
            $mv = $mv + (1.00 * [Math]::Sin(($x * 0.42) + ($y * 0.30)))
            $mv = $mv + (0.85 * [Math]::Sin(($x * 0.28) - ($y * 0.47) + 1.7))
            $mv = $mv + (0.60 * [Math]::Sin(($x * 0.72) + ($y * 0.66) + 0.4))
            $mv = $mv + (0.50 * [Math]::Sin(($x * 0.95) - ($y * 0.31) + 2.6))
            # wide dome so swirls sit off-centre like a stirred pot
            $mv = $mv + (0.80 * [Math]::Exp(-(([Math]::Pow([double]$x - 5.5,2)) + ([Math]::Pow([double]$y - 6.0,2))) / 46.0))
            $mv = $mv + (0.35 * (Noise $x $y 503))
            $col = $baseCol
            if($mv -gt 1.50){ $col = $lightCol }
            if($mv -gt 2.15){ $col = $swirlCol }
            if($mv -lt -1.45){ $col = $darkCol }
            $g[$x,$y] = $col
        }
    }
    # suspended bark fibres: short runs with a dark tip, plus a few specks
    $fib = @(@(2,4,3,0),@(11,6,3,1),@(5,11,3,0),@(9,13,2,0),@(12,2,2,1),@(6,3,2,0),@(1,9,2,1),@(13,9,2,0))
    foreach($f in $fib){
        $fx = $f[0]; $fy = $f[1]; $fl = $f[2]; $fv = $f[3]
        for($i=0;$i -lt $fl;$i++){
            $xx = $fx + $i
            $yy = $fy
            if($fv -eq 1){ $yy = $fy + $i }
            if($xx -gt 15 -or $yy -gt 15){ continue }
            $g[$xx,$yy] = $patchCol
        }
        $g[$fx,$fy] = $cBarkD
    }
    if(-not $cooked){
        # a few harder dark chips floating in the murky broth
        foreach($sp in @(@(7,6),@(4,10),@(10,4),@(2,13),@(8,8),@(13,12))){
            $g[$sp[0],$sp[1]] = $cBarkD
        }
        $g[6,12] = $cSoupRD
        $g[11,3] = $cSoupRD
    } else {
        # two tiny bubbles: pale rim with a darker pinch
        foreach($bb in @(@(4,5),@(11,10))){
            $g[$bb[0],$bb[1]] = $swirlCol
            $g[($bb[0]+1),$bb[1]] = $swirlCol
            $g[$bb[0],($bb[1]+1)] = $darkCol
            $g[($bb[0]+1),($bb[1]+1)] = $baseCol
        }
        $g[5,5]  = $lightCol
        $g[12,10] = $lightCol
    }
    Save-Png $colOut $g 16 16
}
New-SoupTop (Join-Path $bdir 'willow_soup_raw.png')    $cSoupRM $cSoupRD $cSoupRL $cSoupRL $cSoupRD $false
New-SoupTop (Join-Path $bdir 'willow_soup_cooked.png') $cTopCkBase $cTopCkPool $cTopCkHi $cTopCkHi $cTopCkPool $true

# =====================================================================
#  SALT / BRINE / THIRST SET
#  Same recipe as the willow set above: a palette block, ASCII role maps for
#  the shaped sprites, deterministic Noise() passes for the granular and the
#  liquid surfaces, and Save-Png so every pixel lands on the integer grid
#  with no resampling (hard pixels, no anti-aliasing).
# =====================================================================

# --- palette ---------------------------------------------------------
# rock salt: item lump + block crystals
$cSaltRkH ='#F2E2E0'; $cSaltRkM ='#E4C9C6'; $cSaltRkD ='#D3AFAB'; $cSaltRkS ='#BE9A96'
$cSaltOl  ='#5E5E5E'
$cStnL    ='#A8A8A8'; $cStnM    ='#8A8A8A'; $cStnD    ='#6E6E6E'
# salt grades: coarse grains, ground crude powder, refined powder
$cGrainL  ='#EDE7DC'; $cGrainM  ='#DCD3C4'; $cGrainD  ='#C4B9A6'; $cGrainSp ='#9C907C'
$cPwdrL   ='#E8E0D2'; $cPwdrM   ='#D6CCB9'; $cPwdrD   ='#BDB098'
$cRefL    ='#FFFFFF'; $cRefM    ='#EFEFEF'; $cRefD    ='#D8D8D8'; $cRefSh   ='#B4B4B4'
# wooden stirring rod: the vanilla stick.png browns (lit upper-left, shaded
# lower-right), reused by A5 below instead of the old lab-glass blues.
$cRodHi   ='#C39A63'; $cRodMid  ='#A0784A'; $cRodLo   ='#6B4F2A'
$cRodRim  ='#4A3418'; $cRodKnob ='#6B4F2A'
# lab glassware
$cSyGlassH='#FFFFFF'; $cSyGlassM='#E8F2F6'; $cSyGlassD='#A8BDC7'
$cSyLiqH  ='#D9E8A8'; $cSyLiqD  ='#BCD07E'
$cSyPlH   ='#5A5A5A'; $cSyPlD   ='#3E3E3E'
$cSyNdH   ='#C0C6CC'; $cSyNdD   ='#8E959B'
# brine: cloudy / dirty versus clear / bright
$cBr1B    ='#C9CDBE'; $cBr1D    ='#B0B5A4'; $cBr1L    ='#DDE0D4'; $cBr1Sp   ='#8E937F'
$cBr2B    ='#DDE8EA'; $cBr2D    ='#C2D2D6'; $cBr2L    ='#F2F7F8'
# swamp / sea water: stagnant olive-brown versus clean deep sea blue
$cSwB     ='#5E6B3A'; $cSwD     ='#4A5530'; $cSwL     ='#78854C'; $cSwSp    ='#3A4226'
$cSeaB    ='#2E6C9E'; $cSeaD    ='#23547C'; $cSeaL    ='#4E90C4'
# mushroom stew broth (the bowl wood reuses the vanilla-derived bowl palette)
$cStewB   ='#96613A'; $cStewD   ='#7A4C2B'; $cStewL   ='#B07A4A'
# salt grains scattered over a finished dish
$cSltGr   ='#EDE7DC'; $cSltGrW  ='#FFFFFF'
# rock salt ore block
$cOreStnB ='#7E7E7E'; $cOreStnD ='#6A6A6A'; $cOreStnL ='#939393'
$cOreCrH  ='#F4E6E4'; $cOreCrM  ='#E0C6C3'; $cOreCrD  ='#C9A9A5'; $cOreCrW  ='#FFFFFF'
# cauldron brine surfaces: crude, concentrated, dense
$cBrT1B   ='#B9BFAE'; $cBrT1D   ='#A2A896'; $cBrT1L   ='#CDD2C2'
$cBrT2B   ='#A8B097'; $cBrT2D   ='#8F977E'; $cBrT2L   ='#C0C7AE'; $cBrT2P   ='#767D66'
$cBrT3B   ='#D8DCC8'; $cBrT3D   ='#BFC4AB'; $cBrT3L   ='#EDEFE0'; $cBrT3C   ='#F7F7F0'
$cBrT3P   ='#A9AE95'
# thirst hud cell
$cGuiOl   ='#1E1E1E'; $cGuiIn   ='#3A3A3A'; $cGuiSh   ='#2C2C2C'
$cGuiWat  ='#3E7BD6'; $cGuiWatH ='#7FB2F0'; $cGuiWatD ='#2A5AA8'

# ---------------------------------------------------------------------
# NOTE: there is deliberately no item/rock_salt_ore.png. The ore is a block
# item, so its item model parents the block model and shows the block sprite
# (block/rock_salt_ore.png, generated below) instead of a separate icon.
# ---------------------------------------------------------------------

# ---------------------------------------------------------------------
# A2/A3/A4. the three salt grades
#     One heap silhouette, drawn as a smooth mound: half-width grows like a
#     square root from the top row down to the ground row, so the profile is
#     a proper pile rather than a triangle.
#       $coarse   true  -> irregular grain-by-grain tone scatter + strays
#                 false -> smooth lit-to-shaded gradient (powder)
#       $heapRim  the right edge / ground shadow tone
# ---------------------------------------------------------------------
function New-SaltHeap([string]$colOut,[string]$heapL,[string]$heapM,[string]$heapD,[string]$heapRim,
                      [string]$speckCol,[int]$seed,[bool]$coarse,[int]$strayCount,[int]$speckCount){
    $g = New-Grid 16 16
    for($y=6;$y -le 14;$y++){
        $hw = 6.05 * [Math]::Sqrt(([double]($y - 5)) / 10.0)
        $jv = (Noise 1 $y $seed) - 0.5
        $jit = $jv * 0.5
        if($coarse){ $jit = $jv * 1.0 }
        $x0 = [int][Math]::Round(7.5 - $hw - $jit)
        $x1 = [int][Math]::Round(7.5 + $hw + $jit)
        if($x0 -lt 1){ $x0 = 1 }
        if($x1 -gt 14){ $x1 = 14 }
        if($x1 -lt $x0){ $x1 = $x0 }
        $hRow = ([double]($y - 6)) / 8.0
        for($x=$x0;$x -le $x1;$x++){
            $col = $heapM
            $hn = Noise ($x*5) ($y*3) ($seed + 7)
            if($coarse){
                if($hn -gt 0.44){ $col = $heapL }
                elseif($hn -lt 0.20){ $col = $heapD }
                else { $col = $heapM }
                # global lighting layered over the grain scatter
                if(($hRow -lt 0.25) -and ($hn -gt 0.22)){ $col = $heapL }
                if(($hRow -gt 0.80) -and ($hn -lt 0.74)){ $col = $heapD }
            } else {
                if($hRow -lt 0.34){ $col = $heapL }
                elseif($hRow -lt 0.72){ $col = $heapM }
                else { $col = $heapD }
            }
            if($x -eq $x1){ $col = $heapRim }
            if($coarse -and $x -eq $x0){ $col = $heapD }
            if($y -eq 14){
                # the ground shadow of a granular heap is grainy, not a plinth
                $col = $heapRim
                if($coarse){
                    $gb = Noise ($x*3) 14 ($seed + 11)
                    if($gb -gt 0.62){ $col = $heapM }
                    elseif($gb -lt 0.20){ $col = $heapL }
                }
            }
            $g[$x,$y] = $col
        }
    }
    # a few grains thrown clear of the pile (coarse salt only)
    $strays = @(@(11,4),@(4,4),@(13,7),@(2,8),@(9,3))
    for($i=0;$i -lt $strayCount;$i++){
        $sp = $strays[$i]
        $g[$sp[0],$sp[1]] = $heapL
        if((Noise $sp[0] $sp[1] ($seed + 3)) -gt 0.5){ $g[$sp[0],$sp[1]] = $heapM }
    }
    # undissolved specks left in the heap
    $specks = @(@(6,10),@(9,12),@(5,12),@(10,9))
    for($i=0;$i -lt $speckCount;$i++){
        $sp = $specks[$i]
        $g[$sp[0],$sp[1]] = $speckCol
    }
    Save-Png $colOut $g 16 16
}

# ---------------------------------------------------------------------
# A5. item/stirring_rod.png
#     A thin wooden stirrer running lower-left to upper-right, with a rounded
#     knob at either end. Distance-to-segment shading: the upper-left side of
#     the rod is the lit face, the lower-right side the shaded one, exactly as
#     vanilla stick.png is shaded. The palette is stick.png's brown family, so
#     the sprite reads as a plain whittled stick rather than a glass rod, and
#     the end knobs sit a shade darker than the shaft.
# ---------------------------------------------------------------------
function New-StirringRod([string]$colOut){
    $g = New-Grid 16 16
    $ax = 3.0; $ay = 12.6; $bx = 11.4; $by = 3.2
    $dx = $bx - $ax; $dy = $by - $ay
    $len = [Math]::Sqrt(($dx*$dx) + ($dy*$dy))
    $ux = $dx / $len; $uy = $dy / $len
    $px = -1.0 * $uy; $py = $ux
    for($y=0;$y -lt 16;$y++){
        for($x=0;$x -lt 16;$x++){
            $rx = ([double]$x) - $ax; $ry = ([double]$y) - $ay
            $rt = (($rx*$ux) + ($ry*$uy)) / $len
            $rs = ($rx*$px) + ($ry*$py)
            if($rt -lt -0.02 -or $rt -gt 1.02){ continue }
            if([Math]::Abs($rs) -gt 0.95){ continue }
            $rc = $cRodMid
            if($rs -lt -0.35){ $rc = $cRodHi }        # lit upper-left edge
            elseif($rs -gt 0.30){ $rc = $cRodRim }    # dark lower-right edge
            elseif(($y % 2) -eq 1){ $rc = $cRodLo }   # grain, as stick.png alternates
            $g[$x,$y] = $rc
        }
    }
    # Rounded knobs at both ends: darker wood than the shaft and only faintly
    # lit, so they read as whittled ends rather than glass bulbs. The lit/dark
    # split uses the same cross-axis as the shaft, so both ends match.
    foreach($kn in @(@($ax,$ay),@($bx,$by))){
        for($y=0;$y -lt 16;$y++){
            for($x=0;$x -lt 16;$x++){
                $kdx = ([double]$x) - ([double]$kn[0])
                $kdy = ([double]$y) - ([double]$kn[1])
                if((($kdx*$kdx) + ($kdy*$kdy)) -gt 2.9){ continue }
                $ks = ($kdx*$px) + ($kdy*$py)
                $g[$x,$y] = $cRodKnob
                if($ks -lt -0.55){ $g[$x,$y] = $cRodMid }
                if($ks -gt 0.90){ $g[$x,$y] = $cRodRim }
            }
        }
    }
    Save-Png $colOut $g 16 16
}

# ---------------------------------------------------------------------
# A6. item/dexamethasone_injection.png
#     A syringe lying diagonally, needle at the lower left, plunger at the
#     upper right. Everything is placed by distance along the axis ($st) and
#     signed distance across it ($ss), so the parts line up exactly:
#       st 0.00-0.26 needle | 0.26-0.78 barrel | 0.78-0.83 grip | 0.83-1 plunger
# ---------------------------------------------------------------------
function New-Syringe([string]$colOut){
    $g = New-Grid 16 16
    $ax = 3.0; $ay = 13.0; $bx = 12.2; $by = 3.0
    $dx = $bx - $ax; $dy = $by - $ay
    $len = [Math]::Sqrt(($dx*$dx) + ($dy*$dy))
    $ux = $dx / $len; $uy = $dy / $len
    $px = -1.0 * $uy; $py = $ux
    # the needle is walked pixel by pixel so the 1px line stays connected
    # (a distance test alone leaves a dotted diagonal at 16x16)
    for($si=0;$si -le 48;$si++){
        $wt = 0.26 * $si / 48.0
        $wx = [int][Math]::Round($ax + ($ux * $wt * $len))
        $wy = [int][Math]::Round($ay + ($uy * $wt * $len))
        if($wx -lt 0 -or $wy -lt 0 -or $wx -gt 15 -or $wy -gt 15){ continue }
        $g[$wx,$wy] = $cSyNdD
        if($si % 4 -eq 0){ $g[$wx,$wy] = $cSyNdH }
    }
    for($y=0;$y -lt 16;$y++){
        for($x=0;$x -lt 16;$x++){
            $rx = ([double]$x) - $ax; $ry = ([double]$y) - $ay
            $st = (($rx*$ux) + ($ry*$uy)) / $len
            $ss = ($rx*$px) + ($ry*$py)
            if($st -le 0.26 -or $st -gt 1.03){ continue }
            $col = ''
            if($st -le 0.78){
                # clear barrel with a pale yellow-green dose inside it
                if([Math]::Abs($ss) -gt 1.70){ continue }
                if([Math]::Abs($ss) -gt 1.15){ $col = $cSyGlassD }
                else {
                    $col = $cSyGlassM
                    if($ss -lt -0.72){ $col = $cSyGlassH }
                }
                if(($st -gt 0.31) -and ($st -lt 0.74) -and ([Math]::Abs($ss) -le 1.00)){
                    $col = $cSyLiqH
                    if([Math]::Abs($ss) -gt 0.58){ $col = $cSyLiqD }
                    if($st -gt 0.68){ $col = $cSyLiqD }
                }
            }
            elseif($st -le 0.83){
                # finger grip: a wider glass flange at the top of the barrel
                if([Math]::Abs($ss) -gt 1.95){ continue }
                $col = $cSyGlassM
                if([Math]::Abs($ss) -gt 1.30){ $col = $cSyGlassD }
            }
            else {
                # dark grey plunger head, wider than the barrel bore
                if([Math]::Abs($ss) -gt 1.85){ continue }
                $col = $cSyPlH
                if([Math]::Abs($ss) -gt 1.15){ $col = $cSyPlD }
                if(($st -gt 0.95) -and ([Math]::Abs($ss) -gt 0.95)){ $col = $cSyPlD }
            }
            if($col -ne ''){ $g[$x,$y] = $col }
        }
    }
    Save-Png $colOut $g 16 16
}

# ---------------------------------------------------------------------
# A7/A8. the two brine bottles reuse the willow soup bottle silhouette (see
#        New-SoupBottle) with brine tones in place of broth, so the crude and
#        the clean bottle line up pixel for pixel.
#
# B16/B17/B18. cauldron brine surfaces, seen from above and fully opaque.
#        Same swirl language as New-SoupTop above (four crossed sine waves
#        plus a wide dome), so the brine pot matches the soup pot; only the
#        specks and the dry crust are brine-specific.
# ---------------------------------------------------------------------
function New-BrineTop([string]$colOut,[string]$baseCol,[string]$darkCol,[string]$lightCol,[string]$deepCol,
                      [string]$crustCol,[string]$speckCol,[int]$seed,[bool]$dense,[int]$speckCount){
    $g = New-Grid 16 16
    for($y=0;$y -lt 16;$y++){
        for($x=0;$x -lt 16;$x++){
            $mv = 0.0
            $mv = $mv + (1.00 * [Math]::Sin(($x * 0.42) + ($y * 0.30)))
            $mv = $mv + (0.85 * [Math]::Sin(($x * 0.28) - ($y * 0.47) + 1.7))
            $mv = $mv + (0.60 * [Math]::Sin(($x * 0.72) + ($y * 0.66) + 0.4))
            $mv = $mv + (0.50 * [Math]::Sin(($x * 0.95) - ($y * 0.31) + 2.6))
            $mv = $mv + (0.80 * [Math]::Exp(-(([Math]::Pow([double]$x - 5.5,2)) + ([Math]::Pow([double]$y - 6.0,2))) / 46.0))
            $mv = $mv + (0.35 * (Noise $x $y $seed))
            $col = $baseCol
            # five tones: the swirls only read if the crests and the troughs
            # are separated further than the three base tones alone allow
            if($mv -gt 1.20){ $col = $lightCol }
            if($mv -gt 2.10){ $col = Get-MixHex $lightCol '#FFFFFF' 0.40 }
            if($mv -lt -1.20){ $col = $darkCol }
            if($mv -lt -2.00){ $col = $deepCol }
            $g[$x,$y] = $col
        }
    }
    # undissolved salt: mostly single grains, a couple of short clusters,
    # each with a darker underside so it sits in the liquid
    $runs = @(@(2,4,1,0),@(11,6,2,1),@(5,11,1,0),@(9,3,1,0),@(13,9,2,1),
              @(6,3,1,0),@(1,9,2,1),@(12,13,1,0),@(4,7,1,1),@(8,1,2,0))
    for($i=0;$i -lt $speckCount;$i++){
        $r = $runs[$i]
        for($k=0;$k -lt $r[2];$k++){
            $xx = $r[0] + $k; $yy = $r[1]
            if($r[3] -eq 1){ $yy = $r[1] + $k }
            if($xx -gt 15 -or $yy -gt 15){ continue }
            $g[$xx,$yy] = $speckCol
            if(($yy + 1) -le 15){ $g[$xx,($yy + 1)] = Get-MixHex $speckCol $darkCol 0.55 }
        }
    }
    if($dense){
        # salt crust creeping in from the rim: the pool is nearly dry.
        # The crust is F7F7F0, which is too close to the pale brine to read on
        # its own, so it is 1-3px thick in places and gets a dark seam where it
        # meets the open brine.
        $crust = New-Object 'bool[,]' 16,16
        for($y=0;$y -lt 16;$y++){
            for($x=0;$x -lt 16;$x++){
                $ce = [Math]::Min([Math]::Min($x, 15 - $x), [Math]::Min($y, 15 - $y))
                if($ce -gt 2){ continue }
                $cn = Noise ($x*3) ($y*5) ($seed + 31)
                $cis = $false
                if($ce -eq 0){ $cis = ($cn -lt 0.82) }
                elseif(($ce -eq 1) -and ($cn -gt 0.28)){ $cis = $true }
                elseif(($ce -eq 2) -and ($cn -gt 0.74)){ $cis = $true }
                if($cis){
                    $g[$x,$y] = $crustCol
                    $crust[$x,$y] = $true
                    if($cn -gt 0.93){ $g[$x,$y] = $cRefL }
                }
            }
        }
        for($y=0;$y -lt 16;$y++){
            for($x=0;$x -lt 16;$x++){
                if($crust[$x,$y]){ continue }
                $ctouch = $false
                foreach($cv in @(@(1,0),@(-1,0),@(0,1),@(0,-1))){
                    $cxx = $x + $cv[0]; $cyy = $y + $cv[1]
                    if($cxx -lt 0 -or $cyy -lt 0 -or $cxx -gt 15 -or $cyy -gt 15){ continue }
                    if($crust[$cxx,$cyy]){ $ctouch = $true; break }
                }
                if($ctouch){ $g[$x,$y] = $deepCol }
            }
        }
    }
    Save-Png $colOut $g 16 16
}

# ---------------------------------------------------------------------
# B15. block/rock_salt_ore.png  (fully opaque)
#      Vanilla-stone-like speckle with a few 2x2 clumps, then five embedded
#      pale pink salt crystal clusters:
#        H  crystal pale   P  crystal mid   q  crystal deep   W  sparkle
# ---------------------------------------------------------------------
function New-RockSaltOreBlock([string]$colOut){
    $g = New-Grid 16 16
    for($y=0;$y -lt 16;$y++){
        for($x=0;$x -lt 16;$x++){
            # clumpy base: a 5-tap blur of the value hash gives irregular
            # blobs, which is how vanilla stone reads at 16x16 (per-pixel
            # noise alone is flat static, 2x2 blocks look like a quilt)
            $sm = 4.0 * (Noise $x $y 811)
            $sm = $sm + (Noise ($x - 1) $y 811) + (Noise ($x + 1) $y 811)
            $sm = $sm + (Noise $x ($y - 1) 811) + (Noise $x ($y + 1) 811)
            $sm = $sm / 8.0
            $col = $cOreStnB
            if($sm -gt 0.660){ $col = $cOreStnL }
            elseif($sm -lt 0.400){ $col = $cOreStnD }
            $jn = Noise ($x*7) ($y*5) 823
            if($jn -gt 0.92){ $col = $cOreStnL }
            elseif($jn -lt 0.08){ $col = $cOreStnD }
            $g[$x,$y] = $col
        }
    }
    # a few larger dark and light chips so the stone is not uniform
    foreach($cl in @(@(4,1,$cOreStnD),@(10,6,$cOreStnD),@(1,12,$cOreStnL),
                     @(13,12,$cOreStnD),@(6,9,$cOreStnL))){
        GridPx $g $cl[0] $cl[1] $cl[2]
        GridPx $g ($cl[0]+1) $cl[1] $cl[2]
        GridPx $g $cl[0] ($cl[1]+1) $cl[2]
    }
    $crustA = @('.HP.','HPWP','.qPq')
    $crustB = @('HPq','qP.')
    $clusters = @(@(1,3,'A'),@(10,2,'B'),@(11,8,'A'),@(2,10,'A'),@(7,12,'B'))
    foreach($cl in $clusters){
        $pat = $crustB
        if($cl[2] -eq 'A'){ $pat = $crustA }
        for($py=0;$py -lt $pat.Count;$py++){
            $pline = $pat[$py]
            for($px=0;$px -lt $pline.Length;$px++){
                $pch = $pline.Substring($px,1)
                $pc = ''
                if($pch -ceq 'H'){ $pc = $cOreCrH }
                elseif($pch -ceq 'P'){ $pc = $cOreCrM }
                elseif($pch -ceq 'q'){ $pc = $cOreCrD }
                elseif($pch -ceq 'W'){ $pc = $cOreCrW }
                if($pc -ne ''){ GridPx $g ($cl[0] + $px) ($cl[1] + $py) $pc }
            }
        }
        # a dark chip under the cluster so the crystal reads as embedded
        GridPx $g ($cl[0] + 1) ($cl[1] + $pat.Count) $cOreStnD
    }
    Save-Png $colOut $g 16 16
}

# ---------------------------------------------------------------------
# C19/C20/C21. the nine-by-nine thirst hud cells
#      One droplet silhouette shared by all three sprites, so they can be
#      swapped in place: '#' is the dark outline, '+' is interior and '.' is
#      background, which must be left fully transparent.
#      The droplet is deliberately slim - only five pixels wide at its widest -
#      so a row of ten cells does not crowd out the hunger bar beside it.
# ---------------------------------------------------------------------
function New-ThirstCell([string]$colOut,[string]$cellMode){
    $rows = @(
        '....#....',
        '....#....',
        '....#....',
        '...#+#...',
        '...#+#...',
        '..#+++#..',
        '..#+++#..',
        '..#+++#..',
        '..#####..'
    )
    foreach($row in $rows){ if($row.Length -ne 9){ throw "thirst cell row is not 9px: [$row]" } }
    $g = New-Grid 9 9
    for($y=0;$y -lt 9;$y++){
        $line = $rows[$y]
        for($x=0;$x -lt 9;$x++){
            $ch = $line.Substring($x,1)
            # Background: skip entirely so New-Grid's transparent fill survives.
            # (Writing an interior colour here is what made the whole cell opaque.)
            if($ch -ceq '.'){ continue }
            if($ch -ceq '#'){
                $g[$x,$y] = $cGuiOl
                continue
            }
            # Interior. 'full' is all water, 'half' fills the left of the droplet.
            $isWater = $false
            if($cellMode -eq 'full'){ $isWater = $true }
            elseif($cellMode -eq 'half'){ $isWater = ($x -le 4) }
            if($isWater){
                $col = $cGuiWat
                if($y -ge 6){ $col = $cGuiWatD }
                if(($x -le 3) -and ($y -le 5)){ $col = $cGuiWatH }
            } else {
                $col = $cGuiIn
                if($y -ge 6){ $col = $cGuiSh }
            }
            $g[$x,$y] = $col
        }
    }
    Save-Png $colOut $g 9 9
}

# --- the twenty new sprites ------------------------------------------
# New-SaltHeap: out, light, mid, dark, rim/shadow, speck, seed, coarse, strays, specks
New-SaltHeap (Join-Path $idir 'crude_salt.png')        $cGrainL $cGrainM $cGrainD $cGrainD $cGrainSp 701 $true  3 3
New-SaltHeap (Join-Path $idir 'crude_salt_powder.png') $cPwdrL  $cPwdrM  $cPwdrD  $cPwdrD  $cGrainSp 719 $false 0 2
New-SaltHeap (Join-Path $idir 'salt_powder.png')       $cRefL   $cRefM   $cRefD   $cRefSh  $cRefSh  733 $false 0 0

New-StirringRod   (Join-Path $idir 'stirring_rod.png')
New-Syringe       (Join-Path $idir 'dexamethasone_injection.png')

# brine bottles: same silhouette as raw_willow_bark_soup_bottle.png, cloudy
# grey brine for the crude one, clean bright brine for the refined one
New-SoupBottle (Join-Path $idir 'crude_salt_water.png') $cBr1B $cBr1D $cBr1L $true  $cBr1Sp
New-SoupBottle (Join-Path $idir 'salt_water.png')       $cBr2B $cBr2D $cBr2L $false

# ---------------------------------------------------------------------
# A9/A10 + C22..C25. swamp and sea water bottles  (16x16 item sprites)
#        The very same squat water-bottle silhouette as crude_salt_water.png
#        / salt_water.png above; only the liquid inside changes. The swamp
#        pair keeps the cloudy pass and the two suspended dark specks (it is
#        stagnant, dirty water), the sea pair is left clean and speck-free.
#        The salted variants then lay hard-coded salt grains over the liquid
#        surface: pale beige for the crude grade, bright white for the
#        refined one, exactly like the salted soups higher up. The grain
#        coordinates are literals, so every run reproduces them exactly.
# ---------------------------------------------------------------------
# Grain coordinates are literals so every run reproduces them exactly. They all
# sit on liquid pixels of the bottle interior (x >= 8), never on the glass and
# never on the two suspended specks at (7,10) / (11,12): the white refined
# grains would otherwise fuse with the white glass highlight on the left rim.
$grainsCrudeB = @(@(8,8),@(10,8),@(9,9),@(11,9))
$grainsSaltB  = @(@(8,8),@(10,8),@(9,9),@(11,9),@(8,10),@(11,11))

New-SoupBottle (Join-Path $idir 'swamp_water_bottle.png') $cSwB  $cSwD  $cSwL  $true  $cSwSp
New-SoupBottle (Join-Path $idir 'sea_water_bottle.png')   $cSeaB $cSeaD $cSeaL $false

New-SoupBottle (Join-Path $idir 'crude_salt_swamp_water.png') $cSwB  $cSwD  $cSwL  $true  $cSwSp -grainCoords $grainsCrudeB -grainCol $cSltGr
New-SoupBottle (Join-Path $idir 'salt_swamp_water.png')       $cSwB  $cSwD  $cSwL  $true  $cSwSp -grainCoords $grainsSaltB  -grainCol $cSltGrW
New-SoupBottle (Join-Path $idir 'crude_salt_sea_water.png')   $cSeaB $cSeaD $cSeaL $false         -grainCoords $grainsCrudeB -grainCol $cSltGr
New-SoupBottle (Join-Path $idir 'salt_sea_water.png')         $cSeaB $cSeaD $cSeaL $false         -grainCoords $grainsSaltB  -grainCol $cSltGrW

# salted soups: the existing bowls, with salt grains scattered on the surface
$grainsCrude = @(@(4,7),@(7,6),@(8,7),@(11,7))
$grainsSalt  = @(@(4,7),@(5,6),@(7,6),@(8,7),@(9,6),@(10,7),@(11,7),@(6,8))
New-SoupBowl (Join-Path $idir 'crude_salt_mushroom_stew.png') $cStewB $cStewD $cStewL $false $grainsCrude $cSltGr
New-SoupBowl (Join-Path $idir 'salt_mushroom_stew.png')       $cStewB $cStewD $cStewL $false $grainsSalt  $cSltGrW
New-SoupBowl (Join-Path $idir 'crude_salt_willow_bark_soup.png')     $cBrothCB $cBrothCS $cBrothCH $false $grainsCrude $cSltGr
New-SoupBowl (Join-Path $idir 'salt_willow_bark_soup.png')           $cBrothCB $cBrothCS $cBrothCH $false $grainsSalt  $cSltGrW
New-SoupBowl (Join-Path $idir 'crude_salt_raw_willow_bark_soup.png') $cBrothRB $cBrothRS $cBrothRH $true  $grainsCrude $cSltGr
New-SoupBowl (Join-Path $idir 'salt_raw_willow_bark_soup.png')       $cBrothRB $cBrothRS $cBrothRH $true  $grainsSalt  $cSltGrW

New-RockSaltOreBlock (Join-Path $bdir 'rock_salt_ore.png')

# New-BrineTop: out, base, dark, light, deep trough, crust, speck, seed, dense, specks
New-BrineTop (Join-Path $bdir 'brine_crude.png')        $cBrT1B $cBrT1D $cBrT1L $cBr1Sp $cBrT1L $cGrainL 837 $false 4
New-BrineTop (Join-Path $bdir 'brine_concentrated.png') $cBrT2B $cBrT2D $cBrT2L $cBrT2P $cBrT2L $cGrainL 853 $false 8
New-BrineTop (Join-Path $bdir 'brine_dense.png')        $cBrT3B $cBrT3D $cBrT3L $cBrT3P $cBrT3C $cGrainL 877 $true  6

New-ThirstCell (Join-Path $gdir 'thirst_empty.png') 'empty'
New-ThirstCell (Join-Path $gdir 'thirst_half.png')  'half'
New-ThirstCell (Join-Path $gdir 'thirst_full.png')  'full'

# ---------------------------------------------------------------------
# 32-37. mandrake - four growth stages, the fruit and the seeds
# ---------------------------------------------------------------------
# A dark leafy plant, a pale trumpet flower and a spiky green capsule. The four stages are meant to
# read at a glance from a distance: a seedling, a leafy plant, a bud, and a flower with fruit.
$cMkLeafD ='#26401A'; $cMkLeaf ='#3C6329'; $cMkLeafL='#5D9142'
$cMkStemD ='#3A5A22'; $cMkStemL='#54802F'
$cMkPetalD='#B9B5A0'; $cMkPetal ='#DCD8C4'; $cMkPetalL='#F2F0E2'
$cMkThroat='#C9BE7A'
$cMkFruitD='#2F4C1C'; $cMkFruit ='#4F7A34'; $cMkFruitL='#79A94E'
$cMkSeedD ='#5A3F1E'; $cMkSeed  ='#8A6A3C'; $cMkSeedL ='#B08A5A'

# A one pixel gap between two parts of one sprite is invisible while writing the code and glaring in
# the inventory - the fruit shipped once with its brown stalk stopping at y=3 while the green capsule
# starts at y=5. So the silhouette is proved to be one piece here, with a flood fill over the opaque
# pixels. Throw rather than warn: art that is in two pieces is not worth writing out.
function Assert-ConnectedArt($g,[string]$what,[int]$fromX,[int]$fromY,[int]$toX,[int]$toY){
    $seen = New-Object 'bool[,]' 16,16
    $queue = New-Object System.Collections.Generic.Queue[int[]]
    $queue.Enqueue(@($fromX,$fromY))
    $seen[$fromX,$fromY] = $true
    while($queue.Count -gt 0){
        $here = $queue.Dequeue()
        foreach($step in @(@(1,0),@(-1,0),@(0,1),@(0,-1))){
            $nx = $here[0] + $step[0]
            $ny = $here[1] + $step[1]
            if($nx -lt 0 -or $ny -lt 0 -or $nx -gt 15 -or $ny -gt 15){ continue }
            if($seen[$nx,$ny]){ continue }
            if($g[$nx,$ny] -eq $cClear){ continue }
            $seen[$nx,$ny] = $true
            $queue.Enqueue(@($nx,$ny))
        }
    }
    if(-not $seen[$toX,$toY]){
        throw "$what is in two pieces: ($fromX,$fromY) cannot reach ($toX,$toY)"
    }
}

# stage 0: a seedling - one short stem and a pair of seed leaves
$g = New-Grid 16 16
GridRect $g 7 11 2 5 $cMkStemD
GridPx  $g 7 10 $cMkStemL
GridRect $g 4 11 3 1 $cMkLeaf
GridRect $g 4 12 3 1 $cMkLeafD
GridRect $g 9 11 3 1 $cMkLeafL
GridRect $g 9 12 3 1 $cMkLeaf
Save-Png (Join-Path $bdir 'mandrake_stage0.png') $g 16 16

# stage 1: taller, two pairs of leaves
$g = New-Grid 16 16
GridRect $g 7 7 2 9 $cMkStemD
GridRect $g 7 7 1 9 $cMkStemL
GridRect $g 3 10 4 1 $cMkLeafD
GridRect $g 3 11 4 1 $cMkLeaf
GridRect $g 9 8 4 1 $cMkLeafL
GridRect $g 9 9 4 1 $cMkLeaf
GridRect $g 5 7 2 1 $cMkLeaf
GridRect $g 9 6 2 1 $cMkLeafD
GridPx  $g 7 6 $cMkLeafL
Save-Png (Join-Path $bdir 'mandrake_stage1.png') $g 16 16

# stage 2: a bushy plant with the bud that says "almost"
$g = New-Grid 16 16
GridRect $g 7 5 2 11 $cMkStemD
GridRect $g 7 5 1 11 $cMkStemL
GridRect $g 2 9 5 1 $cMkLeafD
GridRect $g 2 10 5 1 $cMkLeaf
GridRect $g 9 8 5 1 $cMkLeafL
GridRect $g 9 9 5 1 $cMkLeaf
GridRect $g 4 6 3 1 $cMkLeaf
GridRect $g 9 5 3 1 $cMkLeafD
GridRect $g 6 3 4 2 $cMkLeaf
GridRect $g 7 2 2 2 $cMkLeafL
Save-Png (Join-Path $bdir 'mandrake_stage2.png') $g 16 16

# stage 3: flowering - the pale trumpet opens upwards and the fruit hangs at its side
$g = New-Grid 16 16
GridRect $g 7 8 2 8 $cMkStemD
GridRect $g 7 8 1 8 $cMkStemL
GridRect $g 2 11 5 1 $cMkLeafD
GridRect $g 2 12 5 1 $cMkLeaf
GridRect $g 9 9 5 1 $cMkLeafL
GridRect $g 9 10 5 1 $cMkLeaf
GridRect $g 4 10 3 1 $cMkLeaf
GridRect $g 9 8 3 1 $cMkLeafD
# the trumpet: a rounded bell sitting one pixel lower than it used to, widening to a full-width rim
# two thirds of the way down and then narrowing into the stem, with the throat showing inside
GridPx  $g 7 3 $cMkPetalL
GridPx  $g 8 3 $cMkPetalL
GridPx  $g 6 4 $cMkPetal
GridPx  $g 7 4 $cMkPetalL
GridPx  $g 8 4 $cMkPetalL
GridPx  $g 9 4 $cMkPetal
GridRect $g 5 5 6 1 $cMkPetalL
GridPx  $g 5 5 $cMkPetalD
GridPx  $g 10 5 $cMkPetalD
GridPx  $g 6 6 $cMkPetal
GridPx  $g 7 6 $cMkThroat
GridPx  $g 8 6 $cMkThroat
GridPx  $g 9 6 $cMkPetal
GridRect $g 7 7 2 1 $cMkPetal
GridPx  $g 7 7 $cMkPetalD
# the capsule, knobbly and darker underneath
foreach($fy in 12..15){ foreach($fx in 10..13){
    $dx = $fx - 11.5; $dy = $fy - 13.5
    if(($dx*$dx + $dy*$dy) -le 3.4){
        $col = $cMkFruit
        if($dy -gt 0.8){ $col = $cMkFruitD }
        if($dx -lt -0.5 -and $dy -lt 0.5){ $col = $cMkFruitL }
        $g[$fx,$fy] = $col
    }
} }
foreach($pt in @(@(10,12),@(13,12),@(10,15),@(13,15),@(11,11),@(12,11))){
    if($g[$pt[0],$pt[1]] -eq '#00000000'){ GridPx $g $pt[0] $pt[1] $cMkFruitD }
}
Save-Png (Join-Path $bdir 'mandrake_stage3.png') $g 16 16
# The whole plant, one piece: the flower on top has to reach the fruit hanging below it, which only
# holds if the flower meets the stem, the leaves meet the stem and the fruit meets a leaf.
Assert-ConnectedArt $g 'block/mandrake_stage3.png' 7 3 11 13

# the fruit on its own: the same capsule, seen up close, with the stalk still on it
$g = New-Grid 16 16
foreach($y in 0..15){ foreach($x in 0..15){
    $dx = $x - 7.5; $dy = $y - 8.5
    $d2 = ($dx*$dx*0.85) + ($dy*$dy)
    $n = Noise $x $y 71
    if($d2 -le 20.0){
        $col = $cMkFruit
        if($dy -lt -1.5){ $col = $cMkFruitL }
        if($dy -gt 2.0 -or $d2 -gt 15.0){ $col = $cMkFruitD }
        if($n -gt 0.86){ $col = $cMkFruitL }
        elseif($n -lt 0.16){ $col = $cMkFruitD }
        $g[$x,$y] = $col
    }
} }
foreach($pt in @(@(2,7),@(3,5),@(13,7),@(12,5),@(3,12),@(4,13),@(12,12),@(11,13),@(7,14),@(8,14))){
    GridPx $g $pt[0] $pt[1] $cMkFruitD
}
GridRect $g 7 1 2 4 $cMkSeedD
GridPx  $g 7 1 $cMkSeed
GridPx  $g 6 6 $cMkFruitL
GridPx  $g 5 6 $cMkFruitL
GridPx  $g 6 7 $cMkFruitL
Save-Png (Join-Path $idir 'mandrake_fruit.png') $g 16 16
Assert-ConnectedArt $g 'item/mandrake_fruit.png' 7 1 7 10

# the seeds: a scatter of little brown pips
$g = New-Grid 16 16
foreach($sp in @(@(3,4),@(9,3),@(12,5),@(6,8),@(11,9),@(4,12),@(9,13))){
    $sx = $sp[0]; $sy = $sp[1]
    GridRect $g $sx $sy 2 3 $cMkSeed
    GridRect $g $sx $sy 2 1 $cMkSeedL
    GridPx  $g $sx ($sy + 2) $cMkSeedD
    GridPx  $g ($sx + 1) ($sy + 2) $cMkSeedD
}
Save-Png (Join-Path $idir 'mandrake_seeds.png') $g 16 16

# ---------------------------------------------------------------------
# Normalise every output: force RGBA8 and strip all metadata, so that
# re-running the script produces byte-identical files.
# ---------------------------------------------------------------------
$outputs = @()
$outputs += Get-ChildItem $bdir -Filter '*.png' -File
$outputs += Get-ChildItem $idir -Filter '*.png' -File
$outputs += Get-ChildItem $edir -Filter '*.png' -File
$outputs += Get-ChildItem $cdir -Filter '*.png' -File
$outputs += Get-ChildItem $gdir -Filter '*.png' -File
$outputs += Get-Item (Join-Path $assets 'icon.png')
$normPath = Join-Path $work 'norm.png'
foreach($f in $outputs){
    & $magick $f.FullName -strip -depth 8 -define png:color-type=6 -define png:compression-level=9 $normPath
    if($LASTEXITCODE -ne 0){ throw "normalise failed for $($f.FullName)" }
    Move-Item $normPath $f.FullName -Force
}

Write-Host ("Generated {0} PNG files." -f $outputs.Count)
