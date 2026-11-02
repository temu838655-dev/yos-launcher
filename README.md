# YouClean

YouClean is an Android TV / Google TV launcher build based on the upstream CleanYourTV project by Gathin23.

## Current build contents

- **YouClean.apk** — the upstream lightweight Android TV launcher, rebranded with the app label **YouClean** and application ID `com.youclean.tvhome`.
- **YouClean-upstream-source.zip** — the upstream source tree, including launcher, tools, examples/device profiles, tests and themes, bundled separately for inspection and further development.
- **SHA256SUMS.txt** — SHA-256 checksums for the APK and source archive.

Important: the current APK is the launcher build. The ADB cleanup scripts and device profiles are included in the separate source archive; they are **not yet integrated as interactive cleanup features inside the APK**.

## Build and downloads

Open **Actions → YouClean Android TV → Run workflow** to start a build manually. Pushes to `main` also start a build. Each successful build uploads the APK, source archive and checksums as a GitHub Actions artifact and attaches them to a GitHub Release.

## Upstream project and license

The launcher, tools, device lists and safety logic originate from [Gathin23/CleanYourTV](https://github.com/Gathin23/cleanyourtv). The workflow fetches the upstream repository at build time and packages a source archive without its Git metadata. See the upstream [MIT license](https://github.com/Gathin23/cleanyourtv/blob/main/LICENSE), the separate [Outfit font license](https://github.com/Gathin23/cleanyourtv/blob/main/launcher/FONT-LICENSE-OFL.txt), and `UPSTREAM.md`.

YouClean is an independent downstream build and is not affiliated with or endorsed by Gathin23 or any TV manufacturer.

## Safety

The upstream cleanup tools use Android's ADB and `pm disable-user`; they do not root the TV or uninstall packages. Review the exact device list and backup/restore instructions before changing system packages. Never disable packages automatically.
