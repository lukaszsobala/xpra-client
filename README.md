![Android Xpra Client Logo](/docs/images/android-xpra-client.png)

# Xpra Client for Android / Java
[![Android CI](https://github.com/lukaszsobala/xpra-client/actions/workflows/main.yml/badge.svg)](https://github.com/lukaszsobala/xpra-client/actions/workflows/main.yml)
[![License: GPLv3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)


This is an **unofficial** implementation of the Xpra Client for Android,
that is based on original work from [Xpra.org](https://xpra.org).

The purpose of this project is to deliver
a neat remote desktop UX to mobile devices,
by integrating remote apps with Android's ecosystem.

_The App is currently under heavy development._

## Getting Started

The project is in early stages of development, so it's not published in any app stores.

However, you can build it yourself if you are familiar with Android development.

## Compatibility

The client speaks the current Xpra protocol ("rencodeplus" packets, lz4 compression)
and has been tested against Xpra servers 6.5 and 7.0, over TCP and SSH.
Servers older than 5.0 are not supported.

## Contributions

Currently, all pull request will be rejected, because I look for a better licensing options than GPL.
It will be easier to eventually change licensing, if there's only one developer holding IP rights.
Sorry. :'(

Other contributions are welcome and encouraged, like:
* How-To guides
* bug reporting
* new features & ideas
* providing translations

# Licensing

See [LICENSE](/LICENSE)
