# Privacy policy of Xpra Client for Android

Last updated: 26 September 2026

Xpra Client for Android ("the app") is an unofficial, open source client for [Xpra](https://xpra.org)
servers, published under the GNU GPL v3 at <https://github.com/lukaszsobala/xpra-client>.
It is not affiliated with the Xpra project.

**In short: the app does not collect any data. Nothing is sent to the developer or to any third
party. The app only connects to the servers you add to it.**

## What the app sends, and where

The app has no accounts, advertising, analytics, crash reporting or tracking of any kind, and no
third-party services that receive data.

It connects only to the Xpra servers you configure, and only when you ask it to. While
connected, it exchanges with your server what a remote desktop needs:

- your keyboard and mouse/touch input, and the size of your screen,
- the contents of the windows of the server's applications, which the server sends to the app,
- the text of your clipboard, both ways, if clipboard sharing is on for that server (it can be
  turned off in the server's settings),
- the names and icons of the server's applications.

Who can read that data is up to the server and its administrator, usually you.

**Connections over SSH are encrypted. Connections over TCP are not encrypted**: anyone on the
network between your device and the server can read them, including what you type. Use SSH on
networks you do not trust.

## What the app stores on your device

- The servers you add: their names, host names, ports, user names and settings.
- The SSH host keys of the servers you connected to (`known_hosts`), to detect impostors.
- SSH private keys you choose for a server: the app keeps a copy in its private storage, which
  other apps cannot read. It is deleted once the key is removed from the server, or the server
  is deleted.
- Passwords and key passphrases, only if you tick "Remember password". They are encrypted with a
  key kept in the Android Keystore of your device, which cannot be read out of it.
  "Forget saved passwords" in the server's settings, deleting the server, or uninstalling the
  app removes them.

All of it is deleted when you uninstall the app, or clear its data.

## Backups

If Android backup is on, Android may include the list of servers and their settings in your
device's backup (ie: to your Google account), like it does for other apps. Saved passwords and
SSH private keys are always left out of backups and device transfers.

## Permissions

- **Internet and network state**: to connect to your servers, and to reconnect as soon as the
  network comes back.
- **Foreground service and notifications**: to keep the connection open while you use other apps,
  with a notification showing it is connected.

## Children

The app is a tool for administrators and users of Xpra servers. It is not directed at children.

## Changes

Changes to this policy are published in this file; its history is in the repository.

## Contact

Questions about this policy, or the app: open an issue at
<https://github.com/lukaszsobala/xpra-client/issues>.
