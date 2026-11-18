package art.kquinn.kuetv

import androidx.compose.ui.unit.sp

/**
 * The sizes lettering comes in, so every page matches Settings. Settings › Text size scales them all.
 * The player's box over the picture and the Home tiles keep sizes of their own, made for those places.
 */
object Sizes {
    /** A page's name at its top. */
    val TITLE = 26.sp
    /** A row to press, and anything said as plainly as a row. */
    val ROW = 21.sp
    /** Explanations, notes and answers in boxes. */
    val BODY = 18.sp
    /** A row's second line. */
    val SUB = 16.sp
    /** The small capitals over a group of rows. */
    val HEADING = 14.sp
    /** Something to read off the screen and type elsewhere: an address or a code. */
    val CODE = 34.sp
}
