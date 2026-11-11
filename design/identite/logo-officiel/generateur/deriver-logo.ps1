# Derives the files of the official logo (marianne-logo.png, decision of the owner, 11 November 2026, D-187)
# for each place it appears: Android launcher (adaptive foreground in the safe zone, monochrome, legacy and
# round), web (favicons, touch icon, PWA, maskable, share image), desktop, iOS, and the application itself.
param([string]$Src, [string]$Out, [string]$Font)
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $Out | Out-Null

$orig = New-Object System.Drawing.Bitmap($Src)
# The drawing, cropped to its opaque pixels and put on a square of transparent background.
$crop = New-Object System.Drawing.Rectangle(12, 56, 1224, 1188)
$side = 1224
$logo = New-Object System.Drawing.Bitmap($side, $side, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($logo)
$g.DrawImage($orig, (New-Object System.Drawing.Rectangle(0, [int](($side - 1188) / 2), 1224, 1188)), $crop, [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()
# For the smallest sizes (16 and 32 px): the Marianne and her flag only, which stay readable.
$small = New-Object System.Drawing.Bitmap(640, 640, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($small)
$g.DrawImage($orig, (New-Object System.Drawing.Rectangle(0, 0, 640, 640)), (New-Object System.Drawing.Rectangle(330, 40, 640, 640)), [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()

function New-Canvas([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gr = [System.Drawing.Graphics]::FromImage($bmp)
    $gr.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gr.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $gr.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $gr.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    return @($bmp, $gr)
}

# One icon: a canvas of $size px, an optional white background (none, square, rounded, circle), the
# drawing ($pic) centred at $ratio of the canvas.
function Write-Icon([string]$name, [int]$size, [double]$ratio, [string]$bg = 'none', $pic = $logo) {
    $c = New-Canvas $size; $bmp = $c[0]; $gr = $c[1]
    $white = [System.Drawing.Brushes]::White
    switch ($bg) {
        'square' { $gr.FillRectangle($white, 0, 0, $size, $size) }
        'circle' { $gr.FillEllipse($white, 0, 0, $size - 1, $size - 1) }
        'rounded' {
            $r = [int]($size * 0.22); $path = New-Object System.Drawing.Drawing2D.GraphicsPath
            $path.AddArc(0, 0, 2 * $r, 2 * $r, 180, 90); $path.AddArc($size - 2 * $r - 1, 0, 2 * $r, 2 * $r, 270, 90)
            $path.AddArc($size - 2 * $r - 1, $size - 2 * $r - 1, 2 * $r, 2 * $r, 0, 90); $path.AddArc(0, $size - 2 * $r - 1, 2 * $r, 2 * $r, 90, 90)
            $path.CloseFigure(); $gr.FillPath($white, $path)
        }
    }
    $d = [int][Math]::Round($size * $ratio); $o = [int](($size - $d) / 2)
    $gr.DrawImage($pic, $o, $o, $d, $d)
    $gr.Dispose()
    $path = Join-Path $Out $name
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
    return $path
}

# The themed icon of Android 13 and later: one colour (Android tints it), the drawing's coloured parts
# solid and its white lines left empty, so that the musicians and the flag stay visible.
function Write-Monochrome([string]$name, [int]$size, [double]$ratio) {
    $c = New-Canvas $size; $bmp = $c[0]; $gr = $c[1]
    $d = [int][Math]::Round($size * $ratio); $o = [int](($size - $d) / 2)
    $gr.DrawImage($logo, $o, $o, $d, $d); $gr.Dispose()
    for ($y = 0; $y -lt $size; $y++) {
        for ($x = 0; $x -lt $size; $x++) {
            $p = $bmp.GetPixel($x, $y)
            $whiteish = ($p.R -gt 200 -and $p.G -gt 200 -and $p.B -gt 200)
            if ($p.A -gt 100 -and -not $whiteish) { $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($p.A, 255, 255, 255)) }
            else { $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0)) }
        }
    }
    $path = Join-Path $Out $name
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
}

# Android: 108 dp layers (the drawing in the 66 dp circle that no launcher cuts: 0.6 of the layer),
# 48 dp legacy icons on a white rounded square and a white circle.
$densities = @{ 'mdpi' = 1.0; 'hdpi' = 1.5; 'xhdpi' = 2.0; 'xxhdpi' = 3.0; 'xxxhdpi' = 4.0 }
foreach ($k in $densities.Keys) {
    $f = $densities[$k]
    Write-Icon "android/mipmap-$k/ic_launcher_foreground.png" ([int](108 * $f)) 0.60 | Out-Null
    Write-Monochrome "android/mipmap-$k/ic_launcher_monochrome.png" ([int](108 * $f)) 0.60
    Write-Icon "android/mipmap-$k/ic_launcher.png" ([int](48 * $f)) 0.80 'rounded' | Out-Null
    Write-Icon "android/mipmap-$k/ic_launcher_round.png" ([int](48 * $f)) 0.76 'circle' | Out-Null
}
# Web: the Marianne alone at 16 and 32 px on a white disc (readable on light and dark tabs), the whole drawing from 48 px.
Write-Icon 'web/favicon-16x16.png' 16 0.94 'circle' $small | Out-Null
Write-Icon 'web/favicon-32x32.png' 32 0.92 'circle' $small | Out-Null
Write-Icon 'web/favicon-48x48.png' 48 0.88 'circle' | Out-Null
Write-Icon 'web/apple-touch-icon.png' 180 0.84 'square' | Out-Null
Write-Icon 'web/icon-192.png' 192 0.90 'circle' | Out-Null
Write-Icon 'web/icon-512.png' 512 0.90 'circle' | Out-Null
Write-Icon 'web/icon-maskable-192.png' 192 0.62 'square' | Out-Null
Write-Icon 'web/icon-maskable-512.png' 512 0.62 'square' | Out-Null
# The application: one image, drawn at 28 to 48 dp (up to 4 times the pixels on a phone).
Write-Icon 'app/ppf_logo.png' 256 1.0 | Out-Null
# Desktop and iOS.
Write-Icon 'desktop/ppf-192.png' 192 0.90 'circle' | Out-Null
Write-Icon 'desktop/ppf.png' 512 0.90 'circle' | Out-Null
Write-Icon 'ios/AppIcon-1024.png' 1024 0.82 'square' | Out-Null

# The preview of a shared link: the logo and the name on white, 1200 x 630.
$og = New-Object System.Drawing.Bitmap(1200, 630, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$gr = [System.Drawing.Graphics]::FromImage($og)
$gr.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gr.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$gr.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$gr.Clear([System.Drawing.Color]::White)
$gr.DrawImage($logo, 70, 65, 500, 500)
$pfc = New-Object System.Drawing.Text.PrivateFontCollection
$pfc.AddFontFile($Font)
$fnt = New-Object System.Drawing.Font($pfc.Families[0], 64, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$ink = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(43, 58, 143))
$name = "Politisation`nPopulaire`nFran" + [char]0x00E7 + "aise"
$gr.DrawString($name, $fnt, $ink, 620, 180)
$gr.Dispose()
$og.Save((Join-Path $Out 'web/og-image-1200x630.png'), [System.Drawing.Imaging.ImageFormat]::Png); $og.Dispose()

$logo.Dispose(); $small.Dispose(); $orig.Dispose()
Get-ChildItem -Recurse $Out -File | ForEach-Object { "{0,-55} {1,6} Ko" -f $_.FullName.Substring($Out.Length + 1), [int]($_.Length / 1KB) }
