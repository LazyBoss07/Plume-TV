# Undo the Sony BRAVIA VU31 debloat: re-enable every disabled app, bring back the
# stock Google TV home, reset animations and the Ambient screensaver.
# Usage:  .\restore-tv.ps1 10.211.79.215:43823
#   (wireless debugging must be on and this PC paired; the port changes after a TV reboot)
param([Parameter(Mandatory)][string]$Tv)

$adb = Join-Path $PSScriptRoot 'tools\platform-tools\adb.exe'
& $adb connect $Tv | Out-Null
$s = '-s', $Tv

$packages = @(
  'com.sony.dtv.promos', 'com.sony.dtv.sonyselect', 'com.sony.dtv.sonytab',
  'com.sony.dtv.multiscreendemo', 'com.sony.dtv.smarthelp', 'com.sony.dtv.helpguidecontents',
  'com.sonypicturescore',
  'com.android.adservices.api', 'com.android.federatedcompute.services',
  'com.android.ondevicepersonalization.services',
  'com.google.android.feedback', 'com.google.android.partnersetup',
  'com.google.android.onetimeinitializer', 'com.google.android.syncadapters.calendar',
  'com.android.printspooler', 'android.autoinstalls.config.google.gtvpai',
  'com.android.gallery3d', 'com.android.camera2',
  'com.google.android.apps.tv.dreamx', 'com.google.android.videos',
  'com.sony.dtv.airplayapp', 'com.google.android.tts',
  'com.netflix.ninja', 'com.netflix.tokenmanager', 'com.amazon.amazonvideo.livingroom',
  'com.google.android.marvin.talkback', 'com.android.wallpaper.livepicker', 'com.android.htmlviewer',
  'com.sony.dtv.m2.seeds.iot', 'com.sony.dtv.m2.seeds.iot.remotestart',
  'com.google.android.tungsten.setupwraith',
  'com.google.android.apps.tv.launcherx'
)
foreach ($p in $packages) {
  & $adb @s shell pm enable --user 0 $p
}

& $adb @s shell settings put global window_animation_scale 1.0
& $adb @s shell settings put global transition_animation_scale 1.0
& $adb @s shell settings put global animator_duration_scale 1.0
& $adb @s shell settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop

# Remove RAM Clean (its chip and helper go with it).
& $adb @s uninstall tv.ramclean
& $adb @s shell settings put secure enabled_accessibility_services dev.plume/.HomeOverride

# Make the stock home the default again; Plume stays installed (uninstall it from Settings > Apps if wanted).
& $adb @s shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
& $adb @s shell input keyevent KEYCODE_HOME
"Restored. Reboot the TV if anything looks off."
