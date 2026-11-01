# Plume, TV fork

My own build of [Plume](https://github.com/aradhyas8/Plume_Launcher) for a Sony BRAVIA VU31 (K-55S25, Google TV 14),
kept in step with upstream. Everything upstream does, plus:

| Change | Where |
|---|---|
| Defaults: Dark theme, Colour icons, selected app at 1.3× | `app/src/main/java/dev/plume/TvDefaults.kt` |
| **Selected app size** setting (1.08× … 1.5×), for icons and banners | Plume settings › Apps row |
| Selected banner glows white on the dark theme | `bannerTile()` in `MainActivity.kt` |
| Top-bar pill `● RAM 52%  ↓ 2 KB/s  VPN off`; OK opens RAM Clean | `Stats.kt`, `activity_main.xml` |
| Updates come from this repo's releases | `TvDefaults.UPDATE_REPO`, `app/build.gradle.kts` (TV fork block) |
| RAM Clean app: lists background apps, force-stops them | `tv-setup/ramclean/` |
| TV debloat and setup scripts | `tv-setup/setup-tv.ps1`, `tv-setup/restore-tv.ps1` |

Fork changes stay in their own files, or in short marked edits, so upstream fixes merge without conflicts.

## Automation

- **TV build** (`.github/workflows/tv-build.yml`): every push to `main` builds Plume and RAM Clean, signed with the
  TV's key, and publishes release `v<version>.0.<run>` with `Plume.apk` and `RAM-Clean.apk`. On the TV:
  Plume settings › Updates installs it. Pull requests get the APKs as artifacts.
- **Sync upstream Plume** (`.github/workflows/sync-upstream.yml`): every day at 08:00 IST, if upstream has new
  commits, opens a pull request *Sync upstream Plume* (branch `upstream-sync`). It says whether the merge is clean
  and passes the tests, or which files conflict. Merge it and a new TV build follows.
- Upstream's own **Pull request checks** still run on pull requests I push.

### One-time setup on GitHub

1. **Actions** tab: enable workflows (forks start with them off).
2. Settings › Actions › General › *Workflow permissions*: **Read and write**, and tick
   **Allow GitHub Actions to create and approve pull requests** (needed by the sync).
3. Settings › Secrets and variables › Actions › *New repository secret*, from the keystore that signed the APKs
   already on the TV (a different key can't update them):
   - `SIGNING_KEYSTORE_B64`: the keystore file, base64 (`[Convert]::ToBase64String([IO.File]::ReadAllBytes('debug.keystore'))`)
   - `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`

## Building locally (Windows)

```powershell
$env:JAVA_HOME = '<JDK 21>'; $env:ANDROID_HOME = '<Android SDK>'
# keystore.properties (git-ignored): storeFile=…, storePassword=…, keyAlias=…, keyPassword=…
.\gradlew.bat assembleRelease                      # app\build\outputs\apk\release\app-release.apk
$env:KEYSTORE = '…'; $env:KEYSTORE_PASS = '…'; $env:KEY_ALIAS = '…'; $env:KEY_PASS = '…'
tv-setup\ramclean\build.ps1                        # tv-setup\ramclean\RAM-Clean.apk
```

## Setting up the TV after a reset

Turn on USB debugging and Wireless debugging, then:

```powershell
tv-setup\setup-tv.ps1 -Tv <ip:port> -Pair <ip:port> -Code <6 digits>
```

It downloads the latest `Plume.apk` and `RAM-Clean.apk` from this repo's releases. `restore-tv.ps1` undoes it.
