![Android Xpra Client Logo](/docs/images/android-xpra-client.png)

# Xpra Client for Android / Java
[![Android CI](https://github.com/lukaszsobala/xpra-client/actions/workflows/main.yml/badge.svg)](https://github.com/lukaszsobala/xpra-client/actions/workflows/main.yml)
[![License: GPLv3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)


This is an **unofficial** implementation of the Xpra Client for Android,
that is based on original work from [Xpra.org](https://xpra.org).

This repository is a fork of [jksiezni/xpra-client](https://github.com/jksiezni/xpra-client) by Jakub Księżniak,
whose development stopped in 2020. The fork updates the app for current Android versions and current Xpra servers.

The purpose of this project is to deliver
a neat remote desktop UX to mobile devices,
by integrating remote apps with Android's ecosystem.

## Getting Started

The app is not published in any app stores.

Every push is built by [GitHub Actions](https://github.com/lukaszsobala/xpra-client/actions/workflows/main.yml),
and the debug APK can be downloaded from the `xpra-client-android-debug` artifact of a run.
You can also build it yourself with `./gradlew assembleDebug` (JDK 17 and the Android SDK are needed).

## Compatibility

The client speaks the current Xpra protocol ("rencodeplus" packets, lz4 compression)
and has been tested against Xpra servers 6.5 and 7.0, over TCP and SSH.
Servers older than 5.0 are not supported.

The app runs on Android 6.0 (API 23) and newer, and targets Android 16 (API 36).

## Contributions

Contributions are welcome, including pull requests and:
* How-To guides
* bug reporting
* new features & ideas
* providing translations

# Licensing

See [LICENSE](/LICENSE)
