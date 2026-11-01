# Builds RAM Clean.apk on Windows without Gradle: draw banner/icon, aapt2, javac, d8, zipalign, apksigner.
# CI builds it with build.sh. Needs ANDROID_HOME (platforms;android-34, build-tools;34.0.0) and a JDK.
# Signing key: KEYSTORE / KEYSTORE_PASS / KEY_ALIAS / KEY_PASS, the same key as Plume's; without them a
# throwaway debug key is made (that build can't install over a release one).
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$sdk = $env:ANDROID_HOME
# Zips unpacked by hand keep an extra folder level (build-tools\34.0.0\android-14); sdkmanager's don't.
$bt = @("$sdk\build-tools\34.0.0\android-14", "$sdk\build-tools\34.0.0") | Where-Object { Test-Path "$_\aapt2.exe" } | Select-Object -First 1
$jar = @("$sdk\platforms\android-34\android-34\android.jar", "$sdk\platforms\android-34\android.jar") | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $bt -or -not $jar) { throw "Set ANDROID_HOME to an SDK with build-tools 34.0.0 and platform android-34." }
$out = Join-Path $root 'build'
Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\classes", "$root\res\drawable-xhdpi" | Out-Null

# Banner (320x180) and icon (192x192): blue-to-purple gradient, white ring and label.
Add-Type -AssemblyName System.Drawing
function New-Art([int]$w, [int]$h, [string]$path, [bool]$withText) {
  $bmp = New-Object System.Drawing.Bitmap $w, $h
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAliasGridFit'
  $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush (New-Object System.Drawing.Rectangle 0, 0, $w, $h),
    ([System.Drawing.Color]::FromArgb(14, 165, 233)), ([System.Drawing.Color]::FromArgb(147, 51, 234)), 35
  $g.FillRectangle($brush, 0, 0, $w, $h)
  $d = [int]($h * 0.42); $x = if ($withText) { [int]($w * 0.1) } else { [int](($w - $d) / 2) }; $y = [int](($h - $d) / 2)
  $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(90, 255, 255, 255)), ([float]($h * 0.06))
  $g.DrawEllipse($pen, $x, $y, $d, $d)
  $pen.Color = [System.Drawing.Color]::White; $pen.StartCap = 'Round'; $pen.EndCap = 'Round'
  $g.DrawArc($pen, $x, $y, $d, $d, -90, 250)
  if ($withText) {
    $font = New-Object System.Drawing.Font 'Segoe UI Semibold', ([float]($h * 0.17)), ([System.Drawing.GraphicsUnit]::Pixel)
    $sf = New-Object System.Drawing.StringFormat; $sf.LineAlignment = 'Center'
    $g.DrawString("RAM`nClean", $font, [System.Drawing.Brushes]::White, (New-Object System.Drawing.RectangleF ([float]($x + $d + $w * 0.07)), 0, ([float]$w), ([float]$h)), $sf)
  }
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $bmp.Dispose()
}
New-Art 320 180 "$root\res\drawable-xhdpi\banner.png" $true
New-Art 192 192 "$root\res\drawable-xhdpi\icon.png" $false

& "$bt\aapt2.exe" compile --dir "$root\res" -o "$out\res.zip"
& "$bt\aapt2.exe" link -I $jar --manifest "$root\AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 34 -o "$out\base.apk" "$out\res.zip"
if ($LASTEXITCODE) { throw 'aapt2 link failed' }

& javac -encoding UTF-8 --release 8 -classpath $jar -d "$out\classes" (Get-ChildItem "$root\src" -Recurse -Filter *.java).FullName 2>&1 | Where-Object { $_ -notmatch 'bootstrap classpath|^1 warning' }
if ($LASTEXITCODE) { throw 'javac failed' }
& "$bt\d8.bat" --lib $jar --min-api 26 --output $out (Get-ChildItem "$out\classes" -Recurse -Filter *.class).FullName
if ($LASTEXITCODE) { throw 'd8 failed' }

Push-Location $out; & jar uf0M base.apk classes.dex; Pop-Location
& "$bt\zipalign.exe" -f 4 "$out\base.apk" "$out\aligned.apk"

$ks = $env:KEYSTORE
if (-not $ks) {
  $ks = "$out\throwaway.keystore"
  $env:KEYSTORE_PASS = $env:KEY_PASS = 'android'; $env:KEY_ALIAS = 'ramclean'
  & keytool -genkeypair -keystore $ks -storepass android -keypass android -alias ramclean -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=RAM Clean' 2>&1 | Out-Null
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass env:KEYSTORE_PASS --ks-key-alias $env:KEY_ALIAS --key-pass env:KEY_PASS --out "$root\RAM-Clean.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw 'apksigner failed' }
"Built $root\RAM-Clean.apk ($((Get-Item "$root\RAM-Clean.apk").Length) bytes)"
