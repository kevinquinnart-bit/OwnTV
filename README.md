# KueTV 2

Kevin's own IPTV app for Android TV, written from scratch. No code from any other IPTV player.

- `app/src/main/java/art/kquinn/kuetv/Provider.kt`: signing in to the provider and loading channels and films
- `PhoneSetup.kt`: typing the provider details on a phone instead of with the remote
- `MainActivity.kt`: the screens
- `Player.kt`: the player
- `TvApps.kt`: the free UK and Irish TV apps (opens them, or the app store)
- `app/src/test`: tests against a pretend provider

Every push to the `kuetv2` branch builds the app and publishes it as `KueTV2.apk` on the `kuetv2` release.
