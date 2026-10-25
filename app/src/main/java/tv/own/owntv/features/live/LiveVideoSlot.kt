package tv.own.owntv.features.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * Kevin's copy: where the Live TV list (or the TV Guide) draws its small video screen, in screen
 * coordinates, while that screen is showing. The mini player sits exactly there while a live channel
 * plays in it and the list or guide is open, so the channel plays in the small screen — picture and
 * sound, on whichever engine it uses — instead of in a corner window with the channel logo beside it.
 */
object LiveVideoSlot {
    var bounds by mutableStateOf<Rect?>(null)
}
