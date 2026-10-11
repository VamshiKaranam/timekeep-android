# Timekeep

A personal Android app for keeping phone use in check. Everything stays on the phone; the app has no internet permission.

- **Screen time** – today's total, the last seven days, and time and opens per app.
- **Daily limits** – give an app a time budget; when it is used up the app is blocked until midnight.
- **Schedules** – block a group of apps at set hours (work, bedtime), on the days you choose.
- **Pause before opening** – a short countdown before a distracting app opens.
- **Easy override** – the block screen always offers a few more minutes, and counts how often you take them.

## Install

1. On your phone, open this repository's **Releases** page and tap `Timekeep.apk` in the newest release.
2. Open the downloaded file. Android will ask to allow installs from your browser; allow it, then tap Install.
3. Open Timekeep and follow **Finish setup**. It needs two switches:
   - **Usage access** – to read how long each app is used.
   - **Accessibility** – to notice when an app opens so it can show the pause or block screen. It only receives the name of the app in front, never screen contents.

If Android shows "Restricted setting" or a switch is greyed out (Android 13 and later do this for apps installed outside the Play Store): open Timekeep's **App info**, tap the **⋮** menu at the top right, choose **Allow restricted settings**, then turn the switch on again.

To update, install a newer `Timekeep.apk` over the old one. Your settings are kept.

## Good to know

- Limits reset at midnight.
- If blocking stops working after a while, the phone's battery saver has probably switched the accessibility service off. Turn it back on, and set Timekeep's battery use to "Unrestricted" in App info.
- Android keeps roughly a week of detailed usage history, which is what the seven-day chart is built from.

## Building

Every push to `main` builds the APK with GitHub Actions and publishes it as a release. To build locally: `./gradlew assembleRelease` (JDK 17, Android SDK 34).

The signing key in `keystore/` is committed on purpose so every build is signed identically and updates install cleanly. It is only suitable for a personal, sideloaded app.
