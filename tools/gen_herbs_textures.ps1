Add-Type -AssemblyName System.Drawing

$resDir = "src/main/resources/assets/aliment/textures"
$blockDir = Join-Path $resDir "block"
$itemDir = Join-Path $resDir "item"

if (!(Test-Path $blockDir)) { New-Item -ItemType Directory -Path $blockDir -Force }
if (!(Test-Path $itemDir)) { New-Item -ItemType Directory -Path $itemDir -Force }

function New-Image16 {
    return New-Object System.Drawing.Bitmap 16, 16
}

function Set-Pixels($bmp, $coords, $color) {
    foreach ($pt in $coords) {
        $bmp.SetPixel($pt[0], $pt[1], $color)
    }
}

function Draw-PotionBottle($bmp, $fluidDark, $fluidMid, $fluidLgt, $fluidHi) {
    # Bottle glass outline
    $C_CORK     = [System.Drawing.Color]::FromArgb(255, 145, 95, 55)
    $C_CORK_DRK = [System.Drawing.Color]::FromArgb(255, 95, 60, 35)
    $C_GLASS_OUT= [System.Drawing.Color]::FromArgb(180, 50, 70, 75)
    $C_GLASS_HI = [System.Drawing.Color]::FromArgb(200, 220, 240, 245)
    $C_GLASS_SHN= [System.Drawing.Color]::FromArgb(130, 255, 255, 255)

    # Cork stopper (y=2..3, x=7..8)
    $bmp.SetPixel(7, 2, $C_CORK)
    $bmp.SetPixel(8, 2, $C_CORK)
    $bmp.SetPixel(7, 3, $C_CORK_DRK)
    $bmp.SetPixel(8, 3, $C_CORK)

    # Neck (y=4..5, x=6..9)
    $bmp.SetPixel(6, 4, $C_GLASS_OUT)
    $bmp.SetPixel(9, 4, $C_GLASS_OUT)
    $bmp.SetPixel(7, 4, $C_GLASS_HI)
    $bmp.SetPixel(8, 4, $C_GLASS_SHN)
    $bmp.SetPixel(6, 5, $C_GLASS_OUT)
    $bmp.SetPixel(9, 5, $C_GLASS_OUT)
    $bmp.SetPixel(7, 5, $fluidMid)
    $bmp.SetPixel(8, 5, $fluidLgt)

    # Shoulders (y=6)
    $bmp.SetPixel(5, 6, $C_GLASS_OUT)
    $bmp.SetPixel(6, 6, $fluidDark)
    $bmp.SetPixel(7, 6, $fluidMid)
    $bmp.SetPixel(8, 6, $fluidLgt)
    $bmp.SetPixel(9, 6, $fluidMid)
    $bmp.SetPixel(10, 6, $C_GLASS_OUT)

    # Body (y=7..13)
    for ($y = 7; $y -le 13; $y++) {
        $bmp.SetPixel(4, $y, $C_GLASS_OUT)
        $bmp.SetPixel(11, $y, $C_GLASS_OUT)
        $bmp.SetPixel(5, $y, $fluidDark)
        $bmp.SetPixel(6, $y, $fluidMid)
        $bmp.SetPixel(7, $y, $fluidMid)
        $bmp.SetPixel(8, $y, $fluidLgt)
        $bmp.SetPixel(9, $y, $fluidMid)
        $bmp.SetPixel(10, $y, $fluidDark)
    }

    # Liquid highlight & shine
    $bmp.SetPixel(6, 7, $fluidHi)
    $bmp.SetPixel(6, 8, $fluidHi)
    $bmp.SetPixel(5, 8, $C_GLASS_SHN)
    $bmp.SetPixel(5, 9, $C_GLASS_SHN)
    $bmp.SetPixel(5, 10, $C_GLASS_SHN)

    # Base (y=14)
    $bmp.SetPixel(5, 14, $C_GLASS_OUT)
    $bmp.SetPixel(6, 14, $fluidDark)
    $bmp.SetPixel(7, 14, $fluidDark)
    $bmp.SetPixel(8, 14, $fluidDark)
    $bmp.SetPixel(9, 14, $fluidDark)
    $bmp.SetPixel(10, 14, $C_GLASS_OUT)
}

# ==============================================================================
# 1. COPTIS (黄连) - Golden rhizome, rich dark green leaves, yellow/white flowers
# ==============================================================================
$C_COP_ROOT_DRK = [System.Drawing.Color]::FromArgb(255, 140, 90, 15)
$C_COP_ROOT_MID = [System.Drawing.Color]::FromArgb(255, 205, 145, 25)
$C_COP_ROOT_LGT = [System.Drawing.Color]::FromArgb(255, 240, 190, 45)
$C_COP_LEAF_DRK = [System.Drawing.Color]::FromArgb(255, 28, 65, 24)
$C_COP_LEAF_MID = [System.Drawing.Color]::FromArgb(255, 45, 100, 36)
$C_COP_LEAF_LGT = [System.Drawing.Color]::FromArgb(255, 75, 145, 55)
$C_COP_LEAF_HI  = [System.Drawing.Color]::FromArgb(255, 115, 185, 75)
$C_COP_FLW_WHT  = [System.Drawing.Color]::FromArgb(255, 250, 250, 230)
$C_COP_FLW_YEL  = [System.Drawing.Color]::FromArgb(255, 255, 220, 60)

# Coptis Stage 0 (Sprout, y=12..15)
$c0 = New-Image16
Set-Pixels $c0 @(@(7,15), @(8,15)) $C_COP_ROOT_DRK
Set-Pixels $c0 @(@(7,14), @(8,14)) $C_COP_ROOT_MID
Set-Pixels $c0 @(@(6,13), @(7,13), @(8,13), @(9,13)) $C_COP_LEAF_DRK
Set-Pixels $c0 @(@(6,12), @(9,12)) $C_COP_LEAF_MID
Set-Pixels $c0 @(@(7,12), @(8,12)) $C_COP_LEAF_LGT
$c0.Save((Join-Path $blockDir "coptis_stage0.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$c0.Dispose()

# Coptis Stage 1 (Growing leaves, y=9..15)
$c1 = New-Image16
Set-Pixels $c1 @(@(7,15), @(8,15)) $C_COP_ROOT_DRK
Set-Pixels $c1 @(@(7,14), @(8,14)) $C_COP_ROOT_MID
Set-Pixels $c1 @(@(6,13), @(7,13), @(8,13), @(9,13)) $C_COP_LEAF_DRK
Set-Pixels $c1 @(@(5,12), @(6,12), @(9,12), @(10,12)) $C_COP_LEAF_MID
Set-Pixels $c1 @(@(7,12), @(8,12)) $C_COP_LEAF_LGT
Set-Pixels $c1 @(@(4,11), @(5,11), @(10,11), @(11,11)) $C_COP_LEAF_LGT
Set-Pixels $c1 @(@(6,10), @(7,10), @(8,10), @(9,10)) $C_COP_LEAF_LGT
Set-Pixels $c1 @(@(7,9), @(8,9)) $C_COP_LEAF_HI
$c1.Save((Join-Path $blockDir "coptis_stage1.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$c1.Dispose()

# Coptis Stage 2 (Bushy herb, y=5..15)
$c2 = New-Image16
Set-Pixels $c2 @(@(6,15), @(7,15), @(8,15), @(9,15)) $C_COP_ROOT_DRK
Set-Pixels $c2 @(@(7,14), @(8,14)) $C_COP_ROOT_MID
Set-Pixels $c2 @(@(6,14), @(9,14)) $C_COP_ROOT_LGT
Set-Pixels $c2 @(@(6,13), @(7,13), @(8,13), @(9,13)) $C_COP_LEAF_DRK
Set-Pixels $c2 @(@(4,12), @(5,12), @(10,12), @(11,12)) $C_COP_LEAF_MID
Set-Pixels $c2 @(@(3,11), @(4,11), @(11,11), @(12,11)) $C_COP_LEAF_LGT
Set-Pixels $c2 @(@(5,10), @(6,10), @(9,10), @(10,10)) $C_COP_LEAF_MID
Set-Pixels $c2 @(@(7,11), @(8,11), @(7,10), @(8,10)) $C_COP_LEAF_LGT
Set-Pixels $c2 @(@(5,9), @(6,9), @(9,9), @(10,9)) $C_COP_LEAF_HI
Set-Pixels $c2 @(@(7,8), @(8,8), @(6,7), @(9,7)) $C_COP_LEAF_LGT
Set-Pixels $c2 @(@(7,6), @(8,6), @(7,5)) $C_COP_LEAF_HI
$c2.Save((Join-Path $blockDir "coptis_stage2.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$c2.Dispose()

# Coptis Stage 3 (Mature flowering herb, y=2..15)
$c3 = New-Image16
Set-Pixels $c3 @(@(6,15), @(7,15), @(8,15), @(9,15)) $C_COP_ROOT_DRK
Set-Pixels $c3 @(@(6,14), @(7,14), @(8,14), @(9,14)) $C_COP_ROOT_MID
Set-Pixels $c3 @(@(5,14), @(10,14)) $C_COP_ROOT_LGT
Set-Pixels $c3 @(@(5,13), @(6,13), @(7,13), @(8,13), @(9,13), @(10,13)) $C_COP_LEAF_DRK
Set-Pixels $c3 @(@(3,12), @(4,12), @(11,12), @(12,12)) $C_COP_LEAF_MID
Set-Pixels $c3 @(@(2,11), @(3,11), @(12,11), @(13,11)) $C_COP_LEAF_LGT
Set-Pixels $c3 @(@(4,10), @(5,10), @(10,10), @(11,10)) $C_COP_LEAF_MID
Set-Pixels $c3 @(@(6,11), @(7,11), @(8,11), @(9,11)) $C_COP_LEAF_LGT
Set-Pixels $c3 @(@(5,9), @(6,9), @(9,9), @(10,9)) $C_COP_LEAF_HI
Set-Pixels $c3 @(@(6,8), @(7,8), @(8,8), @(9,8)) $C_COP_LEAF_MID
Set-Pixels $c3 @(@(4,7), @(5,7), @(10,7), @(11,7)) $C_COP_LEAF_LGT
# Flowers
Set-Pixels $c3 @(@(4,6), @(11,6), @(7,4), @(8,4)) $C_COP_FLW_WHT
Set-Pixels $c3 @(@(4,5), @(11,5), @(7,3), @(8,3)) $C_COP_FLW_YEL
$c3.SetPixel(7, 2, $C_COP_FLW_WHT)
$c3.Save((Join-Path $blockDir "coptis_stage3.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$c3.Dispose()

# Item: coptis.png (Golden-yellow rhizome cluster with stem)
$itemCoptis = New-Image16
Set-Pixels $itemCoptis @(@(7,13), @(8,13), @(9,12), @(6,14), @(7,14), @(10,11)) $C_COP_ROOT_DRK
Set-Pixels $itemCoptis @(@(6,12), @(7,12), @(8,11), @(9,10), @(5,13), @(8,12)) $C_COP_ROOT_MID
Set-Pixels $itemCoptis @(@(5,11), @(6,11), @(7,10), @(8,9), @(7,11), @(6,10)) $C_COP_ROOT_LGT
Set-Pixels $itemCoptis @(@(6,8), @(7,7), @(8,6), @(5,7), @(9,5)) $C_COP_LEAF_MID
Set-Pixels $itemCoptis @(@(5,8), @(6,6), @(7,5), @(8,4), @(9,3), @(10,4)) $C_COP_LEAF_LGT
Set-Pixels $itemCoptis @(@(6,5), @(7,4), @(8,3)) $C_COP_LEAF_HI
$itemCoptis.Save((Join-Path $itemDir "coptis.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$itemCoptis.Dispose()

# Item: crushed_coptis.png (Golden-yellow shredded flakes)
$itemCrushedCoptis = New-Image16
$cCoordsDrk = @(@(4,12), @(5,13), @(7,14), @(10,13), @(11,12), @(6,11), @(8,12))
$cCoordsMid = @(@(5,11), @(6,12), @(7,11), @(8,10), @(9,11), @(10,12), @(7,9), @(6,8))
$cCoordsLgt = @(@(6,10), @(7,10), @(8,9), @(9,10), @(5,10), @(8,8), @(7,7))
$cCoordsHi  = @(@(7,8), @(8,7), @(6,9))
Set-Pixels $itemCrushedCoptis $cCoordsDrk $C_COP_ROOT_DRK
Set-Pixels $itemCrushedCoptis $cCoordsMid $C_COP_ROOT_MID
Set-Pixels $itemCrushedCoptis $cCoordsLgt $C_COP_ROOT_LGT
Set-Pixels $itemCrushedCoptis $cCoordsHi  ([System.Drawing.Color]::FromArgb(255, 255, 230, 80))
$itemCrushedCoptis.Save((Join-Path $itemDir "crushed_coptis.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$itemCrushedCoptis.Dispose()

# Item: coptis_potion.png (Golden-yellow medicine)
$itemPotionCoptis = New-Image16
Draw-PotionBottle $itemPotionCoptis `
    ([System.Drawing.Color]::FromArgb(255, 160, 110, 10)) `
    ([System.Drawing.Color]::FromArgb(255, 220, 160, 20)) `
    ([System.Drawing.Color]::FromArgb(255, 245, 205, 45)) `
    ([System.Drawing.Color]::FromArgb(255, 255, 240, 110))
$itemPotionCoptis.Save((Join-Path $itemDir "coptis_potion.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$itemPotionCoptis.Dispose()


# ==============================================================================
# 2. PHELLODENDRON (黄柏) - Woody bark, yellow cork cambium, dark berries
# ==============================================================================
$C_PHEL_BARK_DRK = [System.Drawing.Color]::FromArgb(255, 75, 55, 30)
$C_PHEL_BARK_MID = [System.Drawing.Color]::FromArgb(255, 125, 95, 40)
$C_PHEL_YEL_MID  = [System.Drawing.Color]::FromArgb(255, 200, 155, 20)
$C_PHEL_YEL_LGT  = [System.Drawing.Color]::FromArgb(255, 235, 195, 40)
$C_PHEL_LEAF_DRK = [System.Drawing.Color]::FromArgb(255, 35, 70, 30)
$C_PHEL_LEAF_MID = [System.Drawing.Color]::FromArgb(255, 55, 110, 45)
$C_PHEL_LEAF_LGT = [System.Drawing.Color]::FromArgb(255, 85, 150, 65)
$C_PHEL_BERRY    = [System.Drawing.Color]::FromArgb(255, 35, 25, 50)
$C_PHEL_BERRY_HI = [System.Drawing.Color]::FromArgb(255, 75, 55, 95)

# Phellodendron Stage 0 (Sprout, y=11..15)
$p0 = New-Image16
Set-Pixels $p0 @(@(7,15), @(8,15)) $C_PHEL_BARK_DRK
Set-Pixels $p0 @(@(7,14), @(8,14)) $C_PHEL_YEL_MID
Set-Pixels $p0 @(@(7,13), @(8,13)) $C_PHEL_LEAF_DRK
Set-Pixels $p0 @(@(6,12), @(9,12)) $C_PHEL_LEAF_MID
Set-Pixels $p0 @(@(5,11), @(6,11), @(9,11), @(10,11)) $C_PHEL_LEAF_LGT
$p0.Save((Join-Path $blockDir "phellodendron_stage0.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$p0.Dispose()

# Phellodendron Stage 1 (Shrub sapling, y=7..15)
$p1 = New-Image16
Set-Pixels $p1 @(@(7,15), @(8,15)) $C_PHEL_BARK_DRK
Set-Pixels $p1 @(@(7,14), @(8,14)) $C_PHEL_BARK_MID
Set-Pixels $p1 @(@(7,13), @(8,13)) $C_PHEL_YEL_MID
Set-Pixels $p1 @(@(6,12), @(9,12)) $C_PHEL_BARK_MID
Set-Pixels $p1 @(@(5,11), @(6,11), @(9,11), @(10,11)) $C_PHEL_LEAF_DRK
Set-Pixels $p1 @(@(4,10), @(5,10), @(10,10), @(11,10)) $C_PHEL_LEAF_MID
Set-Pixels $p1 @(@(3,9), @(4,9), @(11,9), @(12,9)) $C_PHEL_LEAF_LGT
Set-Pixels $p1 @(@(6,8), @(7,8), @(8,8), @(9,8)) $C_PHEL_LEAF_MID
Set-Pixels $p1 @(@(7,7), @(8,7)) $C_PHEL_LEAF_LGT
$p1.Save((Join-Path $blockDir "phellodendron_stage1.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$p1.Dispose()

# Phellodendron Stage 2 (Bushy shrub, y=3..15)
$p2 = New-Image16
Set-Pixels $p2 @(@(7,15), @(8,15)) $C_PHEL_BARK_DRK
Set-Pixels $p2 @(@(7,14), @(8,14), @(7,13), @(8,13)) $C_PHEL_BARK_MID
Set-Pixels $p2 @(@(6,13), @(9,13)) $C_PHEL_YEL_MID
Set-Pixels $p2 @(@(6,12), @(7,12), @(8,12), @(9,12)) $C_PHEL_BARK_MID
Set-Pixels $p2 @(@(5,11), @(10,11)) $C_PHEL_YEL_MID
Set-Pixels $p2 @(@(4,10), @(5,10), @(10,10), @(11,10)) $C_PHEL_LEAF_DRK
Set-Pixels $p2 @(@(3,9), @(4,9), @(11,9), @(12,9)) $C_PHEL_LEAF_MID
Set-Pixels $p2 @(@(2,8), @(3,8), @(12,8), @(13,8)) $C_PHEL_LEAF_LGT
Set-Pixels $p2 @(@(5,8), @(6,8), @(9,8), @(10,8)) $C_PHEL_LEAF_MID
Set-Pixels $p2 @(@(6,7), @(7,7), @(8,7), @(9,7)) $C_PHEL_LEAF_LGT
Set-Pixels $p2 @(@(6,6), @(7,5), @(8,5), @(9,6)) $C_PHEL_LEAF_MID
Set-Pixels $p2 @(@(7,4), @(8,4), @(7,3)) $C_PHEL_LEAF_LGT
$p2.Save((Join-Path $blockDir "phellodendron_stage2.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$p2.Dispose()

# Phellodendron Stage 3 (Mature shrub with dark berries & golden inner bark hints, y=0..15)
$p3 = New-Image16
Set-Pixels $p3 @(@(7,15), @(8,15)) $C_PHEL_BARK_DRK
Set-Pixels $p3 @(@(6,14), @(7,14), @(8,14), @(9,14)) $C_PHEL_BARK_MID
Set-Pixels $p3 @(@(7,13), @(8,13)) $C_PHEL_YEL_MID
Set-Pixels $p3 @(@(5,13), @(10,13)) $C_PHEL_YEL_LGT
Set-Pixels $p3 @(@(5,12), @(6,12), @(9,12), @(10,12)) $C_PHEL_BARK_MID
Set-Pixels $p3 @(@(4,11), @(5,11), @(10,11), @(11,11)) $C_PHEL_LEAF_DRK
Set-Pixels $p3 @(@(3,10), @(4,10), @(11,10), @(12,10)) $C_PHEL_LEAF_MID
Set-Pixels $p3 @(@(2,9), @(3,9), @(12,9), @(13,9)) $C_PHEL_LEAF_LGT
Set-Pixels $p3 @(@(5,9), @(6,9), @(9,9), @(10,9)) $C_PHEL_LEAF_MID
Set-Pixels $p3 @(@(6,8), @(7,8), @(8,8), @(9,8)) $C_PHEL_LEAF_LGT
Set-Pixels $p3 @(@(4,7), @(5,7), @(10,7), @(11,7)) $C_PHEL_LEAF_MID
Set-Pixels $p3 @(@(5,6), @(6,6), @(9,6), @(10,6)) $C_PHEL_LEAF_LGT
Set-Pixels $p3 @(@(6,5), @(7,5), @(8,5), @(9,5)) $C_PHEL_LEAF_MID
Set-Pixels $p3 @(@(7,4), @(8,4), @(7,3), @(8,3)) $C_PHEL_LEAF_LGT
# Berries
Set-Pixels $p3 @(@(4,8), @(11,8), @(6,7), @(9,7), @(6,4), @(9,4)) $C_PHEL_BERRY
Set-Pixels $p3 @(@(4,9), @(11,9), @(6,3)) $C_PHEL_BERRY_HI
$p3.Save((Join-Path $blockDir "phellodendron_stage3.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$p3.Dispose()

# Item: phellodendron.png (Curled golden-yellow herbal bark)
$itemPhel = New-Image16
Set-Pixels $itemPhel @(@(4,12), @(5,13), @(6,14), @(7,14), @(8,13), @(9,12)) $C_PHEL_BARK_DRK
Set-Pixels $itemPhel @(@(5,11), @(6,12), @(7,13), @(8,12), @(9,11), @(10,10)) $C_PHEL_BARK_MID
Set-Pixels $itemPhel @(@(6,10), @(7,11), @(8,11), @(9,10), @(10,9), @(11,8)) $C_PHEL_YEL_MID
Set-Pixels $itemPhel @(@(7,9), @(8,10), @(9,9), @(10,8), @(11,7), @(12,6)) $C_PHEL_YEL_LGT
Set-Pixels $itemPhel @(@(8,8), @(9,8), @(10,7), @(11,6)) ([System.Drawing.Color]::FromArgb(255, 250, 220, 60))
$itemPhel.Save((Join-Path $itemDir "phellodendron.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$itemPhel.Dispose()

# Item: crushed_phellodendron.png (Crushed golden-yellow bark flakes)
$itemCrushedPhel = New-Image16
$pCoordsDrk = @(@(4,12), @(5,13), @(8,14), @(10,13), @(11,12), @(6,11))
$pCoordsMid = @(@(5,11), @(6,12), @(7,12), @(8,11), @(9,12), @(10,11), @(7,9))
$pCoordsYel = @(@(6,10), @(7,11), @(8,10), @(9,10), @(5,10), @(8,9), @(7,8))
$pCoordsHi  = @(@(7,10), @(8,8), @(6,9))
Set-Pixels $itemCrushedPhel $pCoordsDrk $C_PHEL_BARK_DRK
Set-Pixels $itemCrushedPhel $pCoordsMid $C_PHEL_BARK_MID
Set-Pixels $itemCrushedPhel $pCoordsYel $C_PHEL_YEL_MID
Set-Pixels $itemCrushedPhel $pCoordsHi  $C_PHEL_YEL_LGT
$itemCrushedPhel.Save((Join-Path $itemDir "crushed_phellodendron.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$itemCrushedPhel.Dispose()

# Item: phellodendron_potion.png (Golden-ochre bark potion)
$itemPotionPhel = New-Image16
Draw-PotionBottle $itemPotionPhel `
    ([System.Drawing.Color]::FromArgb(255, 140, 95, 20)) `
    ([System.Drawing.Color]::FromArgb(255, 195, 140, 25)) `
    ([System.Drawing.Color]::FromArgb(255, 225, 175, 45)) `
    ([System.Drawing.Color]::FromArgb(255, 245, 210, 90))
$itemPotionPhel.Save((Join-Path $itemDir "phellodendron_potion.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$itemPotionPhel.Dispose()


# ==============================================================================
# 3. LICORICE (甘草) - Pinnate leaves, sweet brown-skinned yellow root, purple flowers
# ==============================================================================
$C_LIC_ROOT_OUT = [System.Drawing.Color]::FromArgb(255, 120, 75, 45)
$C_LIC_ROOT_IN  = [System.Drawing.Color]::FromArgb(255, 215, 175, 60)
$C_LIC_LEAF_DRK = [System.Drawing.Color]::FromArgb(255, 40, 75, 30)
$C_LIC_LEAF_MID = [System.Drawing.Color]::FromArgb(255, 65, 125, 45)
$C_LIC_LEAF_LGT = [System.Drawing.Color]::FromArgb(255, 100, 170, 65)
$C_LIC_FLW_PUR  = [System.Drawing.Color]::FromArgb(255, 155, 120, 185)
$C_LIC_FLW_LGT  = [System.Drawing.Color]::FromArgb(255, 195, 165, 225)

# Licorice Stage 0 (Sprout, y=12..15)
$l0 = New-Image16
Set-Pixels $l0 @(@(7,15), @(8,15)) $C_LIC_ROOT_OUT
Set-Pixels $l0 @(@(7,14), @(8,14)) $C_LIC_LEAF_DRK
Set-Pixels $l0 @(@(6,13), @(9,13)) $C_LIC_LEAF_MID
Set-Pixels $l0 @(@(5,12), @(6,12), @(9,12), @(10,12)) $C_LIC_LEAF_LGT
$l0.Save((Join-Path $blockDir "licorice_stage0.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$l0.Dispose()

# Licorice Stage 1 (Branching pinnate herb, y=8..15)
$l1 = New-Image16
Set-Pixels $l1 @(@(7,15), @(8,15)) $C_LIC_ROOT_OUT
Set-Pixels $l1 @(@(7,14), @(8,14)) $C_LIC_LEAF_DRK
Set-Pixels $l1 @(@(6,13), @(7,13), @(8,13), @(9,13)) $C_LIC_LEAF_MID
Set-Pixels $l1 @(@(5,12), @(10,12)) $C_LIC_LEAF_DRK
Set-Pixels $l1 @(@(4,11), @(5,11), @(10,11), @(11,11)) $C_LIC_LEAF_LGT
Set-Pixels $l1 @(@(6,11), @(7,11), @(8,11), @(9,11)) $C_LIC_LEAF_MID
Set-Pixels $l1 @(@(5,10), @(6,10), @(9,10), @(10,10)) $C_LIC_LEAF_LGT
Set-Pixels $l1 @(@(7,9), @(8,9), @(7,8)) $C_LIC_LEAF_LGT
$l1.Save((Join-Path $blockDir "licorice_stage1.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$l1.Dispose()

# Licorice Stage 2 (Bushy pinnate herb with flower buds, y=4..15)
$l2 = New-Image16
Set-Pixels $l2 @(@(7,15), @(8,15)) $C_LIC_ROOT_OUT
Set-Pixels $l2 @(@(7,14), @(8,14), @(7,13), @(8,13)) $C_LIC_LEAF_DRK
Set-Pixels $l2 @(@(6,12), @(7,12), @(8,12), @(9,12)) $C_LIC_LEAF_MID
Set-Pixels $l2 @(@(5,11), @(10,11)) $C_LIC_LEAF_DRK
Set-Pixels $l2 @(@(4,10), @(5,10), @(10,10), @(11,10)) $C_LIC_LEAF_LGT
Set-Pixels $l2 @(@(3,9), @(4,9), @(11,9), @(12,9)) $C_LIC_LEAF_MID
Set-Pixels $l2 @(@(6,10), @(7,10), @(8,10), @(9,10)) $C_LIC_LEAF_MID
Set-Pixels $l2 @(@(5,9), @(6,9), @(9,9), @(10,9)) $C_LIC_LEAF_LGT
Set-Pixels $l2 @(@(6,8), @(7,8), @(8,8), @(9,8)) $C_LIC_LEAF_MID
Set-Pixels $l2 @(@(5,7), @(6,7), @(9,7), @(10,7)) $C_LIC_LEAF_LGT
Set-Pixels $l2 @(@(7,6), @(8,6)) $C_LIC_LEAF_MID
# Flower buds
Set-Pixels $l2 @(@(7,5), @(8,5)) $C_LIC_FLW_PUR
$l2.SetPixel(7, 4, $C_LIC_FLW_LGT)
$l2.Save((Join-Path $blockDir "licorice_stage2.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$l2.Dispose()

# Licorice Stage 3 (Mature flowering herb with purple blossoms, y=1..15)
$l3 = New-Image16
Set-Pixels $l3 @(@(7,15), @(8,15)) $C_LIC_ROOT_OUT
Set-Pixels $l3 @(@(7,14), @(8,14), @(7,13), @(8,13)) $C_LIC_LEAF_DRK
Set-Pixels $l3 @(@(6,12), @(7,12), @(8,12), @(9,12)) $C_LIC_LEAF_MID
Set-Pixels $l3 @(@(5,11), @(10,11)) $C_LIC_LEAF_DRK
Set-Pixels $l3 @(@(4,10), @(5,10), @(10,10), @(11,10)) $C_LIC_LEAF_LGT
Set-Pixels $l3 @(@(3,9), @(4,9), @(11,9), @(12,9)) $C_LIC_LEAF_MID
Set-Pixels $l3 @(@(2,8), @(3,8), @(12,8), @(13,8)) $C_LIC_LEAF_LGT
Set-Pixels $l3 @(@(6,10), @(7,10), @(8,10), @(9,10)) $C_LIC_LEAF_MID
Set-Pixels $l3 @(@(5,9), @(6,9), @(9,9), @(10,9)) $C_LIC_LEAF_LGT
Set-Pixels $l3 @(@(6,8), @(7,8), @(8,8), @(9,8)) $C_LIC_LEAF_MID
Set-Pixels $l3 @(@(5,7), @(6,7), @(9,7), @(10,7)) $C_LIC_LEAF_LGT
Set-Pixels $l3 @(@(6,6), @(7,6), @(8,6), @(9,6)) $C_LIC_LEAF_MID
# Purple flowers cluster
Set-Pixels $l3 @(@(5,5), @(10,5), @(7,4), @(8,4)) $C_LIC_FLW_PUR
Set-Pixels $l3 @(@(5,4), @(10,4), @(7,3), @(8,3)) $C_LIC_FLW_LGT
Set-Pixels $l3 @(@(7,2), @(8,2)) $C_LIC_FLW_PUR
$l3.SetPixel(7, 1, $C_LIC_FLW_LGT)
$l3.Save((Join-Path $blockDir "licorice_stage3.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$l3.Dispose()

# Item: licorice.png (Dried licorice root sticks - brown skin, yellow center)
$itemLic = New-Image16
Set-Pixels $itemLic @(@(4,12), @(5,11), @(6,10), @(7,9), @(8,8), @(9,7), @(10,6), @(11,5)) $C_LIC_ROOT_OUT
Set-Pixels $itemLic @(@(5,12), @(6,11), @(7,10), @(8,9), @(9,8), @(10,7), @(11,6), @(12,5)) $C_LIC_ROOT_IN
Set-Pixels $itemLic @(@(6,13), @(7,12), @(8,11), @(9,10), @(10,9), @(11,8), @(12,7), @(13,6)) $C_LIC_ROOT_OUT
# Second intersecting stick
Set-Pixels $itemLic @(@(9,12), @(8,11), @(7,10), @(6,9), @(5,8), @(4,7)) $C_LIC_ROOT_OUT
Set-Pixels $itemLic @(@(10,12), @(9,11), @(8,10), @(7,9), @(6,8), @(5,7)) $C_LIC_ROOT_IN
$itemLic.Save((Join-Path $itemDir "licorice.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$itemLic.Dispose()

# Item: crushed_licorice.png (Crushed sweet fibrous licorice root flakes)
$itemCrushedLic = New-Image16
$lCoordsDrk = @(@(4,12), @(5,13), @(7,14), @(10,13), @(11,12), @(6,11))
$lCoordsIn  = @(@(5,11), @(6,12), @(7,12), @(8,11), @(9,12), @(10,11), @(7,9))
$lCoordsLgt = @(@(6,10), @(7,11), @(8,10), @(9,10), @(5,10), @(8,9), @(7,8))
$lCoordsHi  = @(@(7,10), @(8,8), @(6,9))
Set-Pixels $itemCrushedLic $lCoordsDrk $C_LIC_ROOT_OUT
Set-Pixels $itemCrushedLic $lCoordsIn  $C_LIC_ROOT_IN
Set-Pixels $itemCrushedLic $lCoordsLgt ([System.Drawing.Color]::FromArgb(255, 235, 195, 80))
Set-Pixels $itemCrushedLic $lCoordsHi  ([System.Drawing.Color]::FromArgb(255, 255, 225, 120))
$itemCrushedLic.Save((Join-Path $itemDir "crushed_licorice.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$itemCrushedLic.Dispose()

# Item: licorice_potion.png (Warm honey-amber licorice potion)
$itemPotionLic = New-Image16
Draw-PotionBottle $itemPotionLic `
    ([System.Drawing.Color]::FromArgb(255, 150, 85, 25)) `
    ([System.Drawing.Color]::FromArgb(255, 205, 130, 35)) `
    ([System.Drawing.Color]::FromArgb(255, 235, 170, 55)) `
    ([System.Drawing.Color]::FromArgb(255, 255, 215, 115))
$itemPotionLic.Save((Join-Path $itemDir "licorice_potion.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$itemPotionLic.Dispose()

Write-Host "Generated all textures for Coptis, Phellodendron, and Licorice successfully."
