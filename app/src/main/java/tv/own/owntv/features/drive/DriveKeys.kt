package tv.own.owntv.features.drive

/**
 * Kevin's copy: the Google sign-in key for Drive, from Kevin's own Google Cloud project
 * ("TVs and Limited Input devices" client).
 *
 * Google treats the secret of an app installed on a device as not secret: it is inside every copy of
 * the app anyway. It only identifies the app to Google; on its own it opens nobody's Drive.
 * Read from the build (the repo's secrets, v2); blank when not set — Drive then says it is not set up yet.
 */
object DriveKeys {
    const val CLIENT_ID = tv.own.owntv.BuildConfig.KUETV_DRIVE_CLIENT_ID
    const val CLIENT_SECRET = tv.own.owntv.BuildConfig.KUETV_DRIVE_CLIENT_SECRET
}
