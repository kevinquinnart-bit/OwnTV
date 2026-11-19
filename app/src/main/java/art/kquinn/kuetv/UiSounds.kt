package art.kquinn.kuetv

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Quiet sounds as the remote moves around KueTV: a soft tick for each move, a slightly fuller one for OK,
 * and a lower one for Back. They are made by KueTV itself (a few milliseconds of a gently fading tone),
 * kept short and soft, and never play while something is being watched full screen.
 */
object UiSounds {
    private var pool: SoundPool? = null
    private var move = 0
    private var ok = 0
    private var back = 0
    private var lastMove = 0L

    /** True while the player is on screen: no sounds over a programme. */
    @Volatile var muted = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("choices", Context.MODE_PRIVATE)
    fun on(context: Context): Boolean = prefs(context).getBoolean("uiSounds", true)
    fun on(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("uiSounds", value).apply()
        Choices.touch()
    }

    fun load(context: Context) {
        if (pool != null) return
        val p = SoundPool.Builder().setMaxStreams(2)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        val dir = File(context.cacheDir, "ui-sounds").apply { mkdirs() }
        move = p.load(tone(dir, "move", 1900.0, 0.018, 0.0), 1)
        ok = p.load(tone(dir, "ok", 1300.0, 0.045, 1950.0), 1)
        back = p.load(tone(dir, "back", 900.0, 0.04, 0.0), 1)
        pool = p
    }

    fun move(context: Context) {
        val now = SystemClock.uptimeMillis()
        // Held down, the remote repeats quickly: one tick every 70 ms at most.
        if (now - lastMove < 70) return
        lastMove = now
        play(context, move, 0.10f)
    }

    fun ok(context: Context) = play(context, ok, 0.16f)
    fun back(context: Context) = play(context, back, 0.12f)

    private fun play(context: Context, id: Int, volume: Float) {
        if (muted || id == 0 || !on(context)) return
        pool?.play(id, volume, volume, 1, 0, 1f)
    }

    /** A short tone that fades away quickly (and, with [second], a second note on top), as a 16-bit WAV file. */
    private fun tone(dir: File, name: String, freq: Double, seconds: Double, second: Double): String {
        val file = File(dir, "$name.wav")
        if (file.exists() && file.length() > 44) return file.path
        val rate = 44_100
        val n = (rate * seconds).toInt()
        val data = ByteArray(n * 2)
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val fade = exp(-t / (seconds / 4)) * (if (i < 40) i / 40.0 else 1.0)
            var v = sin(2 * PI * freq * t)
            if (second > 0) v = 0.6 * v + 0.4 * sin(2 * PI * second * t)
            val s = (v * fade * 0.8 * Short.MAX_VALUE).toInt()
            data[i * 2] = (s and 0xff).toByte()
            data[i * 2 + 1] = ((s shr 8) and 0xff).toByte()
        }
        FileOutputStream(file).use { out ->
            fun int(v: Int) = out.write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(), ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte()))
            fun short(v: Int) = out.write(byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte()))
            out.write("RIFF".toByteArray()); int(36 + data.size); out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray()); int(16); short(1); short(1); int(rate); int(rate * 2); short(2); short(16)
            out.write("data".toByteArray()); int(data.size); out.write(data)
        }
        return file.path
    }
}
