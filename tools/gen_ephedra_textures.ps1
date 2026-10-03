Add-Type -AssemblyName System.Drawing

$resDir = "src/main/resources/assets/aliment/textures"
$blockDir = Join-Path $resDir "block"
$itemDir = Join-Path $resDir "item"

if (!(Test-Path $blockDir)) { New-Item -ItemType Directory -Path $blockDir -Force }
if (!(Test-Path $itemDir)) { New-Item -ItemType Directory -Path $itemDir -Force }

function New-Image16 {
    return New-Object System.Drawing.Bitmap 16, 16
}

# --- Palettes
$C_TRANS = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
$C_STEM_DARK = [System.Drawing.Color]::FromArgb(255, 42, 60, 22)
$C_STEM_NODE = [System.Drawing.Color]::FromArgb(255, 65, 88, 32)
$C_STEM_MID  = [System.Drawing.Color]::FromArgb(255, 92, 128, 48)
$C_STEM_LGT  = [System.Drawing.Color]::FromArgb(255, 126, 168, 64)
$C_STEM_HIGH = [System.Drawing.Color]::FromArgb(255, 160, 202, 80)

$C_BERRY_DRK = [System.Drawing.Color]::FromArgb(255, 140, 25, 20)
$C_BERRY_MID = [System.Drawing.Color]::FromArgb(255, 200, 48, 36)
$C_BERRY_LGT = [System.Drawing.Color]::FromArgb(255, 245, 90, 70)
$C_BERRY_YEL = [System.Drawing.Color]::FromArgb(255, 240, 160, 50)

# ==============================================================================
# Block Stage 0: 3 small sprout twigs (height ~5px, y=11..15)
# ==============================================================================
$bmp0 = New-Image16
# Twig 1 (left)
$bmp0.SetPixel(6, 15, $C_STEM_DARK)
$bmp0.SetPixel(6, 14, $C_STEM_MID)
$bmp0.SetPixel(5, 13, $C_STEM_LGT)
$bmp0.SetPixel(5, 12, $C_STEM_HIGH)

# Twig 2 (center)
$bmp0.SetPixel(7, 15, $C_STEM_DARK)
$bmp0.SetPixel(8, 15, $C_STEM_DARK)
$bmp0.SetPixel(7, 14, $C_STEM_MID)
$bmp0.SetPixel(8, 14, $C_STEM_MID)
$bmp0.SetPixel(7, 13, $C_STEM_NODE)
$bmp0.SetPixel(8, 13, $C_STEM_LGT)
$bmp0.SetPixel(7, 12, $C_STEM_MID)
$bmp0.SetPixel(8, 12, $C_STEM_HIGH)
$bmp0.SetPixel(7, 11, $C_STEM_HIGH)

# Twig 3 (right)
$bmp0.SetPixel(9, 15, $C_STEM_DARK)
$bmp0.SetPixel(9, 14, $C_STEM_MID)
$bmp0.SetPixel(10, 13, $C_STEM_LGT)
$bmp0.SetPixel(10, 12, $C_STEM_HIGH)

$bmp0.Save((Join-Path $blockDir "ephedra_stage0.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp0.Dispose()

# ==============================================================================
# Block Stage 1: Taller branching stems (height ~9px, y=7..15)
# ==============================================================================
$bmp1 = New-Image16
# Main stalk
for ($y = 15; $y -ge 8; $y--) {
    $bmp1.SetPixel(7, $y, $C_STEM_MID)
    $bmp1.SetPixel(8, $y, $C_STEM_LGT)
}
$bmp1.SetPixel(7, 15, $C_STEM_DARK)
$bmp1.SetPixel(8, 15, $C_STEM_DARK)
$bmp1.SetPixel(7, 12, $C_STEM_NODE)
$bmp1.SetPixel(8, 12, $C_STEM_NODE)
$bmp1.SetPixel(7, 8, $C_STEM_HIGH)
$bmp1.SetPixel(8, 7, $C_STEM_HIGH)

# Left branch
$bmp1.SetPixel(6, 14, $C_STEM_DARK)
$bmp1.SetPixel(5, 13, $C_STEM_MID)
$bmp1.SetPixel(5, 12, $C_STEM_LGT)
$bmp1.SetPixel(4, 11, $C_STEM_NODE)
$bmp1.SetPixel(4, 10, $C_STEM_LGT)
$bmp1.SetPixel(4, 9, $C_STEM_HIGH)

# Right branch
$bmp1.SetPixel(9, 13, $C_STEM_DARK)
$bmp1.SetPixel(10, 12, $C_STEM_MID)
$bmp1.SetPixel(10, 11, $C_STEM_NODE)
$bmp1.SetPixel(11, 10, $C_STEM_LGT)
$bmp1.SetPixel(11, 9, $C_STEM_HIGH)

# Sub-sprig
$bmp1.SetPixel(6, 10, $C_STEM_MID)
$bmp1.SetPixel(6, 9, $C_STEM_HIGH)

$bmp1.Save((Join-Path $blockDir "ephedra_stage1.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp1.Dispose()

# ==============================================================================
# Block Stage 2: Bushy jointed stems (height ~13px, y=3..15)
# ==============================================================================
$bmp2 = New-Image16
# Central trunk & branches
for ($y = 15; $y -ge 5; $y--) {
    $bmp2.SetPixel(7, $y, $C_STEM_MID)
    $bmp2.SetPixel(8, $y, $C_STEM_LGT)
    if ($y % 3 -eq 0) {
        $bmp2.SetPixel(7, $y, $C_STEM_NODE)
        $bmp2.SetPixel(8, $y, $C_STEM_NODE)
    }
}
$bmp2.SetPixel(7, 4, $C_STEM_HIGH)
$bmp2.SetPixel(8, 3, $C_STEM_HIGH)

# Left flank
for ($y = 14; $y -ge 6; $y--) {
    $x = if ($y -gt 11) { 5 } elseif ($y -gt 8) { 4 } else { 3 }
    $bmp2.SetPixel($x, $y, $C_STEM_MID)
    if ($y % 3 -eq 0) { $bmp2.SetPixel($x, $y, $C_STEM_NODE) }
}
$bmp2.SetPixel(3, 5, $C_STEM_HIGH)
$bmp2.SetPixel(4, 5, $C_STEM_LGT)

# Inner left twig
$bmp2.SetPixel(6, 9, $C_STEM_LGT)
$bmp2.SetPixel(6, 8, $C_STEM_HIGH)
$bmp2.SetPixel(5, 7, $C_STEM_HIGH)

# Right flank
for ($y = 14; $y -ge 6; $y--) {
    $x = if ($y -gt 12) { 10 } elseif ($y -gt 9) { 11 } else { 12 }
    $bmp2.SetPixel($x, $y, $C_STEM_LGT)
    if ($y % 3 -eq 0) { $bmp2.SetPixel($x, $y, $C_STEM_NODE) }
}
$bmp2.SetPixel(12, 5, $C_STEM_HIGH)
$bmp2.SetPixel(11, 5, $C_STEM_HIGH)

# Inner right twig
$bmp2.SetPixel(9, 10, $C_STEM_MID)
$bmp2.SetPixel(9, 9, $C_STEM_LGT)
$bmp2.SetPixel(10, 8, $C_STEM_HIGH)
$bmp2.SetPixel(9, 7, $C_STEM_HIGH)

# Base shadows
$bmp2.SetPixel(6, 15, $C_STEM_DARK)
$bmp2.SetPixel(7, 15, $C_STEM_DARK)
$bmp2.SetPixel(8, 15, $C_STEM_DARK)
$bmp2.SetPixel(9, 15, $C_STEM_DARK)

$bmp2.Save((Join-Path $blockDir "ephedra_stage2.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp2.Dispose()

# ==============================================================================
# Block Stage 3: Fully mature ephedra shrub with red medicinal berries
# ==============================================================================
$bmp3 = New-Image16
# Copy stems from stage 2 and extend to top
for ($y = 15; $y -ge 2; $y--) {
    $bmp3.SetPixel(7, $y, $C_STEM_MID)
    $bmp3.SetPixel(8, $y, $C_STEM_LGT)
    if ($y % 3 -eq 0) {
        $bmp3.SetPixel(7, $y, $C_STEM_NODE)
        $bmp3.SetPixel(8, $y, $C_STEM_NODE)
    }
}
$bmp3.SetPixel(7, 1, $C_STEM_HIGH)
$bmp3.SetPixel(8, 1, $C_STEM_HIGH)

# Wide branches left
for ($y = 14; $y -ge 4; $y--) {
    $x = if ($y -gt 11) { 5 } elseif ($y -gt 8) { 4 } elseif ($y -gt 5) { 3 } else { 2 }
    $bmp3.SetPixel($x, $y, $C_STEM_MID)
    if ($y % 3 -eq 0) { $bmp3.SetPixel($x, $y, $C_STEM_NODE) }
}
$bmp3.SetPixel(2, 3, $C_STEM_HIGH)
$bmp3.SetPixel(3, 3, $C_STEM_HIGH)

# Mid-left sprig
$bmp3.SetPixel(6, 10, $C_STEM_MID)
$bmp3.SetPixel(6, 9, $C_STEM_LGT)
$bmp3.SetPixel(5, 8, $C_STEM_LGT)
$bmp3.SetPixel(5, 7, $C_STEM_HIGH)
$bmp3.SetPixel(6, 6, $C_STEM_HIGH)

# Wide branches right
for ($y = 14; $y -ge 4; $y--) {
    $x = if ($y -gt 12) { 10 } elseif ($y -gt 9) { 11 } elseif ($y -gt 6) { 12 } else { 13 }
    $bmp3.SetPixel($x, $y, $C_STEM_LGT)
    if ($y % 3 -eq 0) { $bmp3.SetPixel($x, $y, $C_STEM_NODE) }
}
$bmp3.SetPixel(13, 3, $C_STEM_HIGH)
$bmp3.SetPixel(12, 3, $C_STEM_HIGH)

# Mid-right sprig
$bmp3.SetPixel(9, 11, $C_STEM_MID)
$bmp3.SetPixel(10, 10, $C_STEM_LGT)
$bmp3.SetPixel(10, 9, $C_STEM_LGT)
$bmp3.SetPixel(11, 8, $C_STEM_HIGH)
$bmp3.SetPixel(10, 7, $C_STEM_HIGH)

# Base roots / shadows
$bmp3.SetPixel(5, 15, $C_STEM_DARK)
$bmp3.SetPixel(6, 15, $C_STEM_DARK)
$bmp3.SetPixel(7, 15, $C_STEM_DARK)
$bmp3.SetPixel(8, 15, $C_STEM_DARK)
$bmp3.SetPixel(9, 15, $C_STEM_DARK)
$bmp3.SetPixel(10, 15, $C_STEM_DARK)

# Red/orange ephedra cones / berries clusters
# Cluster 1 (center-left)
$bmp3.SetPixel(5, 10, $C_BERRY_DRK)
$bmp3.SetPixel(5, 9,  $C_BERRY_MID)
$bmp3.SetPixel(4, 9,  $C_BERRY_LGT)
$bmp3.SetPixel(4, 10, $C_BERRY_YEL)

# Cluster 2 (center-right)
$bmp3.SetPixel(9, 8,  $C_BERRY_DRK)
$bmp3.SetPixel(9, 7,  $C_BERRY_MID)
$bmp3.SetPixel(10, 7, $C_BERRY_LGT)

# Cluster 3 (upper center)
$bmp3.SetPixel(7, 5,  $C_BERRY_DRK)
$bmp3.SetPixel(8, 5,  $C_BERRY_MID)
$bmp3.SetPixel(8, 4,  $C_BERRY_LGT)
$bmp3.SetPixel(7, 4,  $C_BERRY_YEL)

# Cluster 4 (lower right)
$bmp3.SetPixel(11, 11, $C_BERRY_DRK)
$bmp3.SetPixel(12, 11, $C_BERRY_MID)
$bmp3.SetPixel(12, 10, $C_BERRY_LGT)

# Cluster 5 (left branch)
$bmp3.SetPixel(3, 7,  $C_BERRY_MID)
$bmp3.SetPixel(3, 6,  $C_BERRY_LGT)

$bmp3.Save((Join-Path $blockDir "ephedra_stage3.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp3.Dispose()

# ==============================================================================
# Item: ephedra.png (A bundle of dried jointed herbal ephedra twigs)
# ==============================================================================
$bmpItemEph = New-Image16
$C_TWIG_DRK = [System.Drawing.Color]::FromArgb(255, 68, 88, 34)
$C_TWIG_MID = [System.Drawing.Color]::FromArgb(255, 108, 138, 52)
$C_TWIG_LGT = [System.Drawing.Color]::FromArgb(255, 148, 184, 72)
$C_TWIG_HGH = [System.Drawing.Color]::FromArgb(255, 180, 218, 92)
$C_CORD_DRK = [System.Drawing.Color]::FromArgb(255, 130, 95, 45)
$C_CORD_MID = [System.Drawing.Color]::FromArgb(255, 185, 145, 75)
$C_CORD_LGT = [System.Drawing.Color]::FromArgb(255, 225, 185, 110)

# Stems running diagonally from bottom-left (2, 14) to top-right (13, 2)
# Stalk A
$bmpItemEph.SetPixel(2, 14, $C_TWIG_DRK)
$bmpItemEph.SetPixel(3, 13, $C_TWIG_MID)
$bmpItemEph.SetPixel(4, 12, $C_TWIG_LGT)
$bmpItemEph.SetPixel(5, 11, $C_TWIG_MID)
$bmpItemEph.SetPixel(6, 10, $C_TWIG_LGT)
$bmpItemEph.SetPixel(7, 9,  $C_TWIG_MID)
$bmpItemEph.SetPixel(8, 8,  $C_TWIG_LGT)
$bmpItemEph.SetPixel(9, 7,  $C_TWIG_MID)
$bmpItemEph.SetPixel(10, 6, $C_TWIG_LGT)
$bmpItemEph.SetPixel(11, 5, $C_TWIG_HGH)
$bmpItemEph.SetPixel(12, 4, $C_TWIG_HGH)
$bmpItemEph.SetPixel(13, 3, $C_TWIG_HGH)

# Stalk B
$bmpItemEph.SetPixel(3, 15, $C_TWIG_DRK)
$bmpItemEph.SetPixel(4, 14, $C_TWIG_MID)
$bmpItemEph.SetPixel(5, 13, $C_TWIG_LGT)
$bmpItemEph.SetPixel(6, 12, $C_TWIG_MID)
$bmpItemEph.SetPixel(7, 11, $C_TWIG_LGT)
$bmpItemEph.SetPixel(8, 10, $C_TWIG_HGH)
$bmpItemEph.SetPixel(9, 9,  $C_TWIG_LGT)
$bmpItemEph.SetPixel(10, 8, $C_TWIG_HGH)
$bmpItemEph.SetPixel(11, 7, $C_TWIG_MID)
$bmpItemEph.SetPixel(12, 6, $C_TWIG_LGT)
$bmpItemEph.SetPixel(13, 5, $C_TWIG_HGH)

# Stalk C (lower branch)
$bmpItemEph.SetPixel(4, 15, $C_TWIG_DRK)
$bmpItemEph.SetPixel(5, 14, $C_TWIG_MID)
$bmpItemEph.SetPixel(6, 13, $C_TWIG_LGT)
$bmpItemEph.SetPixel(7, 12, $C_TWIG_MID)
$bmpItemEph.SetPixel(8, 11, $C_TWIG_MID)
$bmpItemEph.SetPixel(9, 10, $C_TWIG_LGT)
$bmpItemEph.SetPixel(10, 9, $C_TWIG_LGT)
$bmpItemEph.SetPixel(11, 8, $C_TWIG_HGH)

# Branch offshoots
$bmpItemEph.SetPixel(9, 6,  $C_TWIG_LGT)
$bmpItemEph.SetPixel(9, 5,  $C_TWIG_HGH)
$bmpItemEph.SetPixel(8, 4,  $C_TWIG_HGH)

$bmpItemEph.SetPixel(11, 4, $C_TWIG_LGT)
$bmpItemEph.SetPixel(12, 3, $C_TWIG_HGH)

# Hemp string / straw tie in center (around x=7..9, y=10..12)
$bmpItemEph.SetPixel(6, 12, $C_CORD_DRK)
$bmpItemEph.SetPixel(7, 11, $C_CORD_MID)
$bmpItemEph.SetPixel(8, 11, $C_CORD_LGT)
$bmpItemEph.SetPixel(9, 10, $C_CORD_MID)
$bmpItemEph.SetPixel(8, 12, $C_CORD_DRK)
$bmpItemEph.SetPixel(7, 12, $C_CORD_MID)
$bmpItemEph.SetPixel(8, 13, $C_CORD_MID)

# Small red berries at branches
$bmpItemEph.SetPixel(10, 5, $C_BERRY_MID)
$bmpItemEph.SetPixel(10, 4, $C_BERRY_LGT)
$bmpItemEph.SetPixel(13, 4, $C_BERRY_MID)
$bmpItemEph.SetPixel(12, 2, $C_BERRY_LGT)

$bmpItemEph.Save((Join-Path $itemDir "ephedra.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bmpItemEph.Dispose()

# ==============================================================================
# Item: crushed_ephedra.png (A pile of chopped/crushed dried ephedra flakes)
# ==============================================================================
$bmpCrushed = New-Image16
$C_FLK_DRK = [System.Drawing.Color]::FromArgb(255, 45, 62, 24)
$C_FLK_NOD = [System.Drawing.Color]::FromArgb(255, 72, 95, 36)
$C_FLK_MID = [System.Drawing.Color]::FromArgb(255, 110, 142, 54)
$C_FLK_LGT = [System.Drawing.Color]::FromArgb(255, 152, 188, 74)
$C_FLK_HGH = [System.Drawing.Color]::FromArgb(255, 188, 222, 96)
$C_FLK_RED = [System.Drawing.Color]::FromArgb(255, 195, 50, 35)

# Chopped pieces and herbal shreds scattered in an organic mound:
# Base shadow row y=13..14
$bmpCrushed.SetPixel(4, 14, $C_FLK_DRK)
$bmpCrushed.SetPixel(5, 14, $C_FLK_DRK)
$bmpCrushed.SetPixel(6, 14, $C_FLK_NOD)
$bmpCrushed.SetPixel(7, 14, $C_FLK_DRK)
$bmpCrushed.SetPixel(8, 14, $C_FLK_DRK)
$bmpCrushed.SetPixel(9, 14, $C_FLK_NOD)
$bmpCrushed.SetPixel(10, 14, $C_FLK_DRK)
$bmpCrushed.SetPixel(11, 14, $C_FLK_DRK)

# Row y=13
$bmpCrushed.SetPixel(3, 13, $C_FLK_DRK)
$bmpCrushed.SetPixel(4, 13, $C_FLK_MID)
$bmpCrushed.SetPixel(5, 13, $C_FLK_LGT)
$bmpCrushed.SetPixel(6, 13, $C_FLK_MID)
$bmpCrushed.SetPixel(7, 13, $C_FLK_NOD)
$bmpCrushed.SetPixel(8, 13, $C_FLK_MID)
$bmpCrushed.SetPixel(9, 13, $C_FLK_LGT)
$bmpCrushed.SetPixel(10, 13, $C_FLK_MID)
$bmpCrushed.SetPixel(11, 13, $C_FLK_NOD)
$bmpCrushed.SetPixel(12, 13, $C_FLK_DRK)

# Row y=12
$bmpCrushed.SetPixel(3, 12, $C_FLK_MID)
$bmpCrushed.SetPixel(4, 12, $C_FLK_LGT)
$bmpCrushed.SetPixel(5, 12, $C_FLK_HGH)
$bmpCrushed.SetPixel(6, 12, $C_FLK_MID)
$bmpCrushed.SetPixel(7, 12, $C_FLK_LGT)
$bmpCrushed.SetPixel(8, 12, $C_FLK_HGH)
$bmpCrushed.SetPixel(9, 12, $C_FLK_MID)
$bmpCrushed.SetPixel(10, 12, $C_FLK_RED)
$bmpCrushed.SetPixel(11, 12, $C_FLK_MID)
$bmpCrushed.SetPixel(12, 12, $C_FLK_DRK)

# Row y=11
$bmpCrushed.SetPixel(4, 11, $C_FLK_NOD)
$bmpCrushed.SetPixel(5, 11, $C_FLK_LGT)
$bmpCrushed.SetPixel(6, 11, $C_FLK_HGH)
$bmpCrushed.SetPixel(7, 11, $C_FLK_MID)
$bmpCrushed.SetPixel(8, 11, $C_FLK_NOD)
$bmpCrushed.SetPixel(9, 11, $C_FLK_LGT)
$bmpCrushed.SetPixel(10, 11, $C_FLK_MID)
$bmpCrushed.SetPixel(11, 11, $C_FLK_NOD)

# Row y=10
$bmpCrushed.SetPixel(5, 10, $C_FLK_MID)
$bmpCrushed.SetPixel(6, 10, $C_FLK_LGT)
$bmpCrushed.SetPixel(7, 10, $C_FLK_HGH)
$bmpCrushed.SetPixel(8, 10, $C_FLK_MID)
$bmpCrushed.SetPixel(9, 10, $C_FLK_RED)
$bmpCrushed.SetPixel(10, 10, $C_FLK_LGT)

# Row y=9 (top crest of flakes)
$bmpCrushed.SetPixel(6, 9, $C_FLK_LGT)
$bmpCrushed.SetPixel(7, 9, $C_FLK_HGH)
$bmpCrushed.SetPixel(8, 9, $C_FLK_MID)

# Scattered loose bits around mound
$bmpCrushed.SetPixel(2, 13, $C_FLK_MID)
$bmpCrushed.SetPixel(2, 14, $C_FLK_DRK)
$bmpCrushed.SetPixel(13, 14, $C_FLK_DRK)
$bmpCrushed.SetPixel(13, 13, $C_FLK_MID)
$bmpCrushed.SetPixel(4, 10, $C_FLK_LGT)
$bmpCrushed.SetPixel(11, 9, $C_FLK_LGT)

$bmpCrushed.Save((Join-Path $itemDir "crushed_ephedra.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bmpCrushed.Dispose()

# ==============================================================================
# Item: ephedrine.png (Ephedrine Potion / 麻黄碱药水 in vanilla potion style)
# ==============================================================================
# "所有药水的贴图都改成原版样式，只在颜色有区别"
# Uses vanilla Minecraft potion bottle & grayscale liquid overlay, tinted with
# ephedrine's distinctive stimulant golden-amber haste color (RGB: 230, 165, 45).
$potionPath = Join-Path $PSScriptRoot "vanilla_potion.png"
$overlayPath = Join-Path $PSScriptRoot "vanilla_potion_overlay.png"

if (!(Test-Path $potionPath) -or !(Test-Path $overlayPath)) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $clientJar = (Get-ChildItem -Path "D:\Codes\Kotlin\Outbreak\.gradle\loom-cache" -Recurse -Filter "*minecraft-clientOnly*.jar" | Select-Object -First 1).FullName
    $zip = [System.IO.Compression.ZipFile]::OpenRead($clientJar)
    $entryPotion = $zip.GetEntry("assets/minecraft/textures/item/potion.png")
    $entryOverlay = $zip.GetEntry("assets/minecraft/textures/item/potion_overlay.png")

    $s1 = $entryPotion.Open()
    $fs1 = [System.IO.File]::Create($potionPath)
    $s1.CopyTo($fs1)
    $fs1.Dispose()
    $s1.Dispose()

    $s2 = $entryOverlay.Open()
    $fs2 = [System.IO.File]::Create($overlayPath)
    $s2.CopyTo($fs2)
    $fs2.Dispose()
    $s2.Dispose()

    $zip.Dispose()
}

$bmpPotionBottle = [System.Drawing.Bitmap]::FromFile($potionPath)
$bmpPotionOverlay = [System.Drawing.Bitmap]::FromFile($overlayPath)

function New-VanillaPotionSprite([System.Drawing.Color]$tint) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $cp = $bmpPotionBottle.GetPixel($x, $y)
            $co = $bmpPotionOverlay.GetPixel($x, $y)
            if ($cp.A -gt 0) {
                $bmp.SetPixel($x, $y, $cp)
            } elseif ($co.A -gt 0) {
                $r = [int][Math]::Round(($co.R / 255.0) * $tint.R)
                $g = [int][Math]::Round(($co.G / 255.0) * $tint.G)
                $b = [int][Math]::Round(($co.B / 255.0) * $tint.B)
                $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
            }
        }
    }
    return $bmp
}

$ephedrineTint = [System.Drawing.Color]::FromArgb(255, 230, 165, 45)
$bmpEphedrine = New-VanillaPotionSprite $ephedrineTint
$bmpEphedrine.Save((Join-Path $itemDir "ephedrine.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bmpEphedrine.Dispose()
$bmpPotionBottle.Dispose()
$bmpPotionOverlay.Dispose()

Write-Output "Textures generated successfully!"
