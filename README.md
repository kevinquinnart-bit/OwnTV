# KueSounds

Kevin's own music player for Android phones (and, later, Android TV). It plays the music kept in a Google Drive, straight from the Drive. A sister app to KueTV, in the same colours and font.

- `Google.kt`: signing in to the music's Google account with the phone's own Google sign-in (read only)
- `Drive.kt`: listing the music, folders and covers in the Drive
- `Scanner.kt`: finding the music, then reading each song's details from the file
- `Library.kt`: albums, artists, songs, folders and search, kept on the phone
- `PlaybackService.kt`: the player, which carries on with the screen off
- `Playing.kt`: the screens' side of the player
- `Ui.kt`: the screens
- `Updates.kt`: checking for a newer KueSounds each time it opens
- `app/src/test`: checks against pretend Drive listings

Every push to the `kuesounds` branch builds the app and publishes it as `KueSounds.apk` on the `kuesounds` download. That download is never marked "latest", so KueTV's Downloader code is not affected.

Google's sign-in knows the app by its package name `art.kquinn.kuesounds` and its signing key (SHA-1 `17:05:AF:FC:99:35:B9:25:31:9E:4C:10:54:85:A8:99:C9:69:29:2B`), entered once as an Android client in the Google Cloud project.
