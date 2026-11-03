# YouClean

YouClean is an Android TV / Google TV launcher based on the upstream CleanYourTV project by Gathin23, with downstream YouClean tools.

## Included in the APK

- **YouClean launcher** — upstream TV launcher rebranded as YouClean, application ID `com.youclean.tvhome`.
- **App manager** — opened from the launcher's Settings screen; lists installed apps, searches and filters user/system apps, and opens Android's App info page.
- **JSON app-list backup** — exports package names, labels and system/user classification only. It does not copy APK files or private app data.
- **Backup comparison** — imports a YouClean JSON list and reports packages that are missing on the current TV. It does not install or modify apps.
- **ADB Wi-Fi guide** — step-by-step guidance for Android 11+ Wireless debugging and older firmware.
- **ADB Wi-Fi prototype** — pairing-code and TLS connection flow for Android 11+. After connection it runs only `pm list packages` to read package names for both user and system apps. It does not disable, uninstall, or change packages.

The manager and ADB tools are opened from the launcher's Settings screen; they are not intended to appear as separate launcher icons.

## Important limitations

- ADB pairing and TLS connection have passed CI compilation, but still require testing against real TV firmware and network configurations.
- The current in-app ADB prototype supports Android 11+ Wireless debugging. Android 8–10 has guidance only; it does not yet have an in-app legacy ADB transport.
- App-list backup is metadata-only. It is not a full device backup and cannot restore application data.
- The current ADB command is read-only. No package disabling, uninstalling, or automatic cleanup is implemented in this prototype.

## Build and downloads

Open **Actions → YouClean Android TV → Run workflow** to start a build manually. Pushes to `feature/adb-wifi-prototype` start builds. The stable `main` branch is left untouched until the experimental branch is reviewed and merged. Each successful build uploads the APK, source archive and checksums as an Actions artifact and attaches them to a GitHub Release.

- Stable branch: `main`
- Experimental branch: `feature/adb-wifi-prototype`

Do not merge the experimental branch into `main` until the APK has been installed and tested on the target TV.

## Upstream project and license

The launcher, tools, device lists and safety logic originate from [Gathin23/CleanYourTV](https://github.com/Gathin23/cleanyourtv). The complete upstream repository is vendored in `upstream/`, with the exact revision recorded in `UPSTREAM_COMMIT`. The build compiles this checked-in snapshot and packages the patched source archive without Git metadata. See the upstream [MIT license](https://github.com/Gathin23/cleanyourtv/blob/main/LICENSE), the separate [Outfit font license](https://github.com/Gathin23/cleanyourtv/blob/main/launcher/FONT-LICENSE-OFL.txt), and `UPSTREAM.md`.

YouClean is an independent downstream build and is not affiliated with or endorsed by Gathin23 or any TV manufacturer.

## Safety

The prototype does not root the TV. Pair only on a trusted local network, keep the pairing code private, and turn Wireless debugging off when it is not needed. Do not disable system packages unless their purpose and recovery path are understood.
