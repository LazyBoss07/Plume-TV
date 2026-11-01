# Sets up the Sony BRAVIA VU31 (K-55S25) again after a factory reset or firmware update:
# debloat, speed tweaks, Plume launcher, RAM Clean with its Home-screen RAM chip.
# Safe to run more than once. Everything it does is undone by restore-tv.ps1.
#
# On the TV first: Settings > System > About > tap "Build" 7 times, then
#   Settings > System > Developer options > turn on USB debugging and Wireless debugging.
#
# Usage (PowerShell, in this folder):
#   .\setup-tv.ps1 -Tv 172.22.64.215:5555
#   .\setup-tv.ps1 -Tv 172.22.64.215:40885 -Pair 172.22.64.215:35141 -Code 531750   # first time after a reset
#     (-Pair and -Code come from Wireless debugging > "Pair device with pairing code")
#   .\setup-tv.ps1 -Tv ... -WhatIf      # only show what would change
param(
  [Parameter(Mandatory)][string]$Tv,
  [string]$Pair,
  [string]$Code,
  [string]$Repo = 'LazyBoss07/Plume-TV',   # where the APKs come from: this fork on GitHub
  [switch]$WhatIf
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = $PSScriptRoot

# ---- tools and APKs (downloaded if missing) ------------------------------------------------
$adb = Join-Path $root 'tools\platform-tools\adb.exe'
if (-not (Test-Path $adb)) {
  "Downloading Google platform-tools (adb)..."
  New-Item -ItemType Directory -Force "$root\dl", "$root\tools" | Out-Null
  Invoke-WebRequest https://dl.google.com/android/repository/platform-tools-latest-windows.zip -OutFile "$root\dl\pt.zip"
  Expand-Archive "$root\dl\pt.zip" -DestinationPath "$root\tools" -Force
}
# Your Plume (this repo: bigger selected tile, RAM pill, your defaults) and RAM Clean, from this repo's latest
# GitHub release, which CI publishes on every push to main. Files already in dl\ are used as they are.
New-Item -ItemType Directory -Force "$root\dl" | Out-Null
$plumeApk = Join-Path $root 'dl\Plume.apk'
$ramApk = Join-Path $root 'dl\RAM-Clean.apk'
foreach ($apk in $plumeApk, $ramApk) {
  if (Test-Path $apk) { continue }
  $name = Split-Path $apk -Leaf
  "Downloading $name from $Repo..."
  Invoke-WebRequest "https://github.com/$Repo/releases/latest/download/$name" -OutFile $apk
}

# ---- connect ------------------------------------------------------------------------------------
if ($Pair) {
  if (-not $Code) { throw '-Pair needs -Code' }
  & $adb pair $Pair $Code
}
& $adb connect $Tv | Out-Host
$s = '-s', $Tv
function Sh([string]$cmd) { & $adb @s shell $cmd }
$model = Sh 'getprop ro.product.model'
if (-not $model) { throw "Can't reach the TV at $Tv. Check Wireless/USB debugging and the address." }
"Connected: $model (Android $(Sh 'getprop ro.build.version.release'))"
if ($model -notmatch 'BRAVIA VU31') { Write-Warning "This was made for BRAVIA VU31; continuing, but check the result." }

$installed = (Sh 'pm list packages') -replace '^package:', ''
$disabled = (Sh 'pm list packages -d') -replace '^package:', ''

function Disable([string[]]$pkgs) {
  foreach ($p in $pkgs) {
    if ($installed -notcontains $p) { "  - $p (not on this TV)"; continue }
    if ($disabled -contains $p) { "  = $p (already off)"; continue }
    if ($WhatIf) { "  would disable $p"; continue }
    "  x $(Sh "pm disable-user --user 0 $p")"
  }
}
function Run([string]$what, [string]$cmd) {
  if ($WhatIf) { "  would: $what"; return }
  "  $what"; Sh $cmd | Out-Null
}

# ---- 1. bloat (cleanyourtv's tested BRAVIA VU31 list, plus your choices) ------------------
"1. Turning off bloat (disabled, not deleted)"
Sh 'logcat -b crash -c' | Out-Null
Disable @(
  # Sony promos, demos and help
  'com.sony.dtv.promos', 'com.sony.dtv.sonyselect', 'com.sony.dtv.sonytab', 'com.sony.dtv.multiscreendemo',
  'com.sony.dtv.smarthelp', 'com.sony.dtv.helpguidecontents', 'com.sonypicturescore',
  # ads and tracking
  'com.android.adservices.api', 'com.android.federatedcompute.services', 'com.android.ondevicepersonalization.services',
  # Google leftovers
  'com.google.android.feedback', 'com.google.android.play.games', 'com.google.android.partnersetup',
  'com.google.android.onetimeinitializer', 'com.google.android.syncadapters.calendar', 'com.android.printspooler',
  'android.autoinstalls.config.google.gtvpai', 'com.android.gallery3d', 'com.android.camera2',
  'com.google.android.videos', 'com.google.android.youtube.tvmusic',
  # your choices: AirPlay, Text-to-speech, Netflix, Prime, TalkBack, phone leftovers, Alexa
  'com.sony.dtv.airplayapp', 'com.google.android.tts',
  'com.netflix.ninja', 'com.netflix.tokenmanager', 'com.amazon.amazonvideo.livingroom',
  'com.google.android.marvin.talkback', 'com.android.wallpaper.livepicker', 'com.android.htmlviewer',
  'com.sony.dtv.m2.seeds.iot', 'com.sony.dtv.m2.seeds.iot.remotestart'
)

# Screensaver: switch to the simple one before turning off the Ambient (photo) screensaver.
Run 'screensaver -> simple Colors' 'settings put secure screensaver_components com.android.dreams.basic/.Colors'
Disable @('com.google.android.apps.tv.dreamx')

# ---- 2. speed -----------------------------------------------------------------------------------
"2. Animations at 0.5x"
foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') {
  Run "$k = 0.5" "settings put global $k 0.5"
}

# ---- 3. Plume launcher ---------------------------------------------------------------------
"3. Plume launcher (your build)"
if ($WhatIf) { '  would install Plume and make it Home' } else {
  $r = & $adb @s install -r $plumeApk 2>&1 | Select-Object -Last 1
  if ("$r" -match 'UPDATE_INCOMPATIBLE|signatures do not match') {
    # The official Plume is signed with another key; it has to go first (its settings reset).
    "  replacing the official Plume: $(& $adb @s uninstall dev.plume)"
    $r = & $adb @s install $plumeApk 2>&1 | Select-Object -Last 1
  }
  "  $r"
  Sh 'pm grant dev.plume android.permission.WRITE_SECURE_SETTINGS' | Out-Null
  Sh 'pm grant dev.plume android.permission.READ_TV_LISTINGS' | Out-Null   # Continue Watching row
  Sh 'cmd package set-home-activity dev.plume/.MainActivity' | Out-Null
  # A fresh Plume picks its own Gallery screensaver (downloads paintings): keep the simple system one.
  Sh 'settings put secure screensaver_components com.android.dreams.basic/.Colors' | Out-Null
}
# The setup wizard and stock home both claim Home; they go only once Plume is installed.
# Check first that Wi-Fi does not belong to the setup wizard, or turning it off drops Wi-Fi (and adb).
$wifiOwner = Sh "dumpsys wifi | grep -o 'cname=[^ ]*' | sort -u"
if ($wifiOwner -match 'setupwraith|tungsten') {
  Write-Warning "Wi-Fi was saved by the setup wizard ($wifiOwner); leaving setupwraith and the stock home ON."
} else {
  Disable @('com.google.android.tungsten.setupwraith', 'com.google.android.apps.tv.launcherx')
}

# ---- 4. RAM Clean ----------------------------------------------------------------------------
"4. RAM Clean (opened from Plume's RAM pill; lists and force-stops background apps)"
if ($WhatIf) { '  would install RAM Clean and turn on both helpers' } else {
  & $adb @s install -r $ramApk | Select-Object -Last 1
  Sh 'appops set tv.ramclean GET_USAGE_STATS allow' | Out-Null
  Start-Sleep 2   # set right after install, Android can drop the helper setting
  Sh 'settings put secure enabled_accessibility_services dev.plume/.HomeOverride:tv.ramclean/.Helper' | Out-Null
  Sh 'settings put secure accessibility_enabled 1' | Out-Null
}

# ---- 5. storage -----------------------------------------------------------------------------
"5. Removing downloaded updates of apps that are off (frees ~170 MB)"
foreach ($p in 'com.sonypicturescore', 'com.sony.dtv.promos', 'com.google.android.apps.tv.dreamx') {
  if ($installed -contains $p) { Run "updates of $p" "cmd package uninstall-system-updates $p" }
}

# ---- check ------------------------------------------------------------------------------------
if (-not $WhatIf) {
  Sh 'input keyevent HOME' | Out-Null
  Start-Sleep 4
  "Home is now: $(Sh 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | Select-Object -Last 1)"
  "Helpers on: $(Sh 'settings get secure enabled_accessibility_services')"
  "Apps turned off: $((Sh 'pm list packages -d').Count)"
  "Free RAM: $([math]::Round([long]((Sh 'grep MemAvailable /proc/meminfo') -replace '\D','') / 1024)) MB"
  $crashes = & $adb @s logcat -b crash -d
  if ($crashes) { Write-Warning "Crash log is not empty:"; $crashes | Select-Object -First 10 }
}

@"

Done. A few settings are only reachable with the remote:
  - Plume: sliders icon (top right) > Wallpaper & theme > Theme: Dark
  - Plume: Apps row > Icon style: Banner (colour logos); Selected app size: 1.3x by default
  - Plume: Apps row > Choose apps, to hide Eco Dashboard (Sony turns it back on at every boot)
  - Plume: Remote & Home button > Remote keys, for the remote's app buttons
  - Sony clock box: Timers & Clock > Clock display > Off
Then turn off USB debugging and Wireless debugging in Developer options.
"@
