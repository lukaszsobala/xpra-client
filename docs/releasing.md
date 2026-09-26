# Releasing on Google Play

## Once: the upload key

Google Play signs the app it delivers with a key of its own ("Play App Signing"); the bundles
uploaded to it are signed with an *upload key*, which only proves they come from you. If the
upload key is lost or leaked, Google can reset it.

1. Create the key (keytool comes with the JDK), and keep the file and its passwords somewhere
   safe, out of the repository:

       keytool -genkeypair -v -keystore xpra-upload.jks -alias upload \
           -keyalg RSA -keysize 4096 -validity 10000

2. Add it to the secrets of the repository (Settings → Secrets and variables → Actions):

   | Secret                        | Value                                    |
   |-------------------------------|------------------------------------------|
   | `XPRA_UPLOAD_KEYSTORE_BASE64` | the output of `base64 -w0 xpra-upload.jks` |
   | `XPRA_UPLOAD_STORE_PASSWORD`  | the password of the keystore             |
   | `XPRA_UPLOAD_KEY_ALIAS`       | `upload`                                 |
   | `XPRA_UPLOAD_KEY_PASSWORD`    | the password of the key                  |

## Each release

1. Set `versionMajor` and `versionMinor` in `xpra-client-android/build.gradle`, if needed.
2. Tag the commit and push the tag:

       git tag v1.0 && git push origin v1.0

3. The `release` job of the CI builds `xpra-client-android-<version>-release.aab`, signed with
   the upload key: download it from the `xpra-client-android-release` artifact of the run, and
   upload it to the Play Console.

The version code is the number of the CI run, so it grows with each build, as Play requires.
A signed bundle can also be built locally, with the same variables set in the environment
(`XPRA_UPLOAD_STORE_FILE` being the path of the keystore) and `GITHUB_RUN_NUMBER` set to a
number higher than the last upload: `./gradlew bundleRelease`.

Debug builds are signed with the debug key of the repository: the app from Play cannot be
installed over them, or the other way round, without uninstalling it first (which deletes
the servers).

## Once: the Play Console

- **Application ID**: `io.github.lukaszsobala.xpra`, which can never change once uploaded.
  (Builds before it used `io.github.xpra.client`, the ID of the original author: they are a
  separate app, which can be uninstalled once the servers are added again.)
- **Testing**: new personal developer accounts must run a closed test with at least 12 testers,
  opted in for 14 days in a row, before they can apply for production access.
- **Privacy policy**: the URL of [privacy-policy.md](privacy-policy.md), ie:
  <https://github.com/lukaszsobala/xpra-client/blob/master/docs/privacy-policy.md>.
- **App access**: all features need an Xpra server, which reviewers do not have. Say so, and
  explain how to run one, ie: `xpra start --bind-tcp=0.0.0.0:10000 --start=xterm`.
- **Ads**: none. **Content rating**: a utility, with no user-generated content shared by the app.
- **Data safety**: the app sends nothing to the developer or to third parties; the data it
  exchanges goes to the user's own server, at their request. Connections over TCP are not
  encrypted, so "encrypted in transit" cannot be claimed for all of them.
- **Foreground service** (App content → Foreground service permissions): the app declares a
  `specialUse` service, which keeps a remote desktop session open while its windows are in the
  background. Play asks for a short video of it: connect, switch to another app, and show the
  notification of the connection, then come back to the session.
- **Store listing**: the icon is [play/icon-512.png](play/icon-512.png). Play also needs a
  feature graphic (1024×500) and at least 2 phone screenshots. Describe the app as an
  unofficial client for Xpra, and link the source code, as the GPL asks.
