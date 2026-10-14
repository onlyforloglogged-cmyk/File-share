# FileBeam

Send files between two Android devices (phone and phone, or phone and Android TV)
on the same WiFi. Install on both, open Receive on one and Send on the other.
No accounts, no server, no dependencies beyond the Android SDK.

## Get an APK with no computer (free)
1. Create a GitHub repo and upload this whole folder (keep `.github/workflows/build.yml`).
2. Open the repo's Actions tab, run "Build APK".
3. Download the `filebeam-debug-apk` artifact, unzip, install `app-debug.apk`.

## Or build locally
Open this folder in Android Studio and press Run. Or run `gradle assembleDebug`
with Gradle 8.7+ and JDK 17.

## How it works
- Receiver listens on TCP port 48484 and advertises itself over mDNS (`_filebeam._tcp.`).
- Sender discovers receivers, or you type `ip:port` from the Receive screen.
- Files are saved to Downloads/FileBeam (Android 10+).

## Known limits
- Not encrypted or authenticated. Use trusted WiFi only.
- WiFi with "client isolation" (many guest networks) blocks device-to-device traffic.
- Some Android TVs have no file picker, so sending from a TV may not work; receiving does.
- Untested on real devices as of this first version.
