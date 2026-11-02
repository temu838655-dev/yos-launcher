# YouClean

YouClean is an Android TV / Google TV launcher build based on the upstream CleanYourTV project by Gathin23.

## What this repository does

- Builds the upstream lightweight Android TV launcher from source.
- Rebrands the launcher label to **YouClean** and uses package ID `com.youclean.tvhome`.
- Publishes the APK as both a GitHub Actions artifact and a GitHub Release asset on every push to `main`.
- Keeps the upstream source and its MIT license intact during the build.

## Build

Open **Actions → YouClean Android TV → Run workflow** to start a build manually. Pushes to `main` also start a build. The generated APK is named `YouClean.apk`.

## Upstream project and license

The launcher, tools, device lists and safety logic originate from [Gathin23/CleanYourTV](https://github.com/Gathin23/cleanyourtv). This repository fetches the upstream source at build time rather than silently copying or stripping its attribution. See the upstream [MIT license](https://github.com/Gathin23/cleanyourtv/blob/main/LICENSE) and the included `UPSTREAM.md`.

YouClean is an independent downstream build and is not affiliated with or endorsed by Gathin23 or any TV manufacturer.

## Safety

The upstream cleanup tools use Android's ADB and `pm disable-user`; they do not root the TV or uninstall packages. Review the exact device list and backup/restore instructions before changing system packages. Never disable packages automatically.
