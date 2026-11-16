package art.kquinn.kuetv

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** One programme set to be recorded when it comes on: the channel, its address, and when. */
data class Timed(val id: Int, val channel: String, val title: String, val url: String, val startMs: Long, val stopMs: Long) {
    fun toJson(): String = JsonObject().apply {
        addProperty("id", id); addProperty("channel", channel); addProperty("title", title)
        addProperty("url", url); addProperty("start", startMs); addProperty("stop", stopMs)
    }.toString()

    companion object {
        fun fromJson(text: String): Timed? = runCatching {
            val o = JsonParser.parseString(text).asJsonObject
            Timed(o.get("id").asInt, o.get("channel").asString, o.get("title").asString, o.get("url").asString, o.get("start").asLong, o.get("stop").asLong)
        }.getOrNull()

        /** The same programme on the same channel always gets the same number, so setting it twice is setting it once. */
        fun make(channel: String, title: String, url: String, startMs: Long, stopMs: Long): Timed =
            Timed(((url.hashCode() * 31L + startMs / 1000).toInt()) and 0x7fffffff, channel, title, url, startMs, stopMs)

        /** What is still worth keeping at [now]: anything that hasn't finished. */
        fun stillToCome(all: List<Timed>, now: Long): List<Timed> = all.filter { it.stopMs > now }.sortedBy { it.startMs }
    }
}

/** The programmes set to record, kept on this TV or phone, and the alarms that start them. */
object TimedStore {
    var changes by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("timed", Context.MODE_PRIVATE)

    fun all(context: Context): List<Timed> =
        Timed.stillToCome(prefs(context).getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() }.mapNotNull { Timed.fromJson(it) }, System.currentTimeMillis())

    private fun save(context: Context, list: List<Timed>) {
        prefs(context).edit().putString("list", list.joinToString("\n") { it.toJson() }).apply()
        changes++
    }

    fun add(context: Context, timed: Timed) {
        save(context, all(context).filter { it.id != timed.id } + timed)
        arm(context, timed)
    }

    fun remove(context: Context, timed: Timed) {
        save(context, all(context).filter { it.id != timed.id })
        runCatching { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarm(context, timed.id)) }
    }

    /** Called when the recording has been made, or its time has gone. */
    fun finished(context: Context, id: Int) = save(context, all(context).filter { it.id != id })

    private fun alarm(context: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context.applicationContext, id,
            Intent(context.applicationContext, TimedReceiver::class.java).setAction(TimedReceiver.START).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Sets the alarm that starts [timed] a few seconds before it comes on. One already on is started now. */
    private fun arm(context: Context, timed: Timed) {
        val app = context.applicationContext
        val at = timed.startMs - 20_000
        if (at <= System.currentTimeMillis()) {
            TimedService.start(app, timed.id)
            return
        }
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = Build.VERSION.SDK_INT < 31 || runCatching { alarms.canScheduleExactAlarms() }.getOrDefault(false)
        val set = exact && runCatching { alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, alarm(app, timed.id)), alarm(app, timed.id)) }.isSuccess
        // Without leave to be exact, Android may start it some minutes late; better late than not at all.
        if (!set) runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(app, timed.id)) }
    }

    /** Sets every alarm again. Android forgets them when the device restarts or the app is updated. */
    fun arm(context: Context) {
        for (timed in all(context)) if (timed.id !in TimedService.running) arm(context, timed)
    }
}

/** Woken by the alarm for a programme, and after a restart or an update to set the alarms again. */
class TimedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == START) TimedService.start(context.applicationContext, intent.getIntExtra("id", 0))
        else TimedStore.arm(context)
    }

    companion object {
        const val START = "art.kquinn.kuetv.TIMED_START"
    }
}

/**
 * Records a programme that was set to record, with KueTV's screens closed. It takes the channel
 * straight from the provider into a file, on a connection of its own, from a few seconds before the
 * programme until a minute after, joining again if the stream drops. The file then goes the way of
 * every recording: into Recordings, and from there to Google Drive.
 */
class TimedService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching { Notices.hold(this, 42, "Recording a programme") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra("id", 0) ?: 0
        val timed = TimedStore.all(this).firstOrNull { it.id == id }
        if (timed == null || !running.add(id)) {
            if (running.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }
        Thread({
            val lock = runCatching {
                (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kuetv:timed").apply {
                    acquire(timed.stopMs - System.currentTimeMillis() + 5 * 60_000)
                }
            }.getOrNull()
            try {
                record(timed)
            } finally {
                runCatching { lock?.release() }
                running.remove(id)
                TimedStore.finished(applicationContext, id)
                Recordings.changed()
                Recordings.wake(applicationContext)
                if (running.isEmpty()) stopSelf()
            }
        }, "kuetv-timed-$id").start()
        return START_NOT_STICKY
    }

    private fun record(timed: Timed) {
        val file = Recordings.newFile(this, timed.channel + " " + timed.title)
        val end = timed.stopMs + 60_000
        val http = Provider.http.newBuilder().readTimeout(20, TimeUnit.SECONDS).build()
        Recordings.timedNow += file
        var failures = 0
        var written = 0L
        var checkedAt = 0L
        try {
            BufferedOutputStream(FileOutputStream(file), 64 * 1024).use { out ->
                val buffer = ByteArray(64 * 1024)
                while (System.currentTimeMillis() < end && failures < 40) {
                    try {
                        http.newCall(Request.Builder().url(timed.url).header("User-Agent", Provider.USER_AGENT).build()).execute().use { response ->
                            if (!response.isSuccessful) throw IOException("The provider answered " + response.code)
                            val input = response.body.byteStream()
                            while (System.currentTimeMillis() < end) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                out.write(buffer, 0, count)
                                written += count
                                failures = 0
                                if (written - checkedAt >= 16L * 1024 * 1024) {
                                    checkedAt = written
                                    // Stop cleanly rather than fill the storage.
                                    if ((file.parentFile?.usableSpace ?: Long.MAX_VALUE) < 200L * 1024 * 1024) return
                                }
                            }
                        }
                    } catch (e: IOException) {
                        failures++
                    }
                    // The stream ended or dropped before the programme did: join again after a short wait.
                    if (System.currentTimeMillis() < end) runCatching { Thread.sleep(3_000) }
                }
            }
        } catch (e: IOException) {
            // The file couldn't be written. What was written is kept.
        } finally {
            Recordings.timedNow -= file
            if (file.length() == 0L) file.delete()
        }
    }

    companion object {
        /** The recordings being made right now, by their number. */
        val running: MutableSet<Int> = ConcurrentHashMap.newKeySet()

        fun start(context: Context, id: Int) {
            runCatching { context.startForegroundService(Intent(context, TimedService::class.java).putExtra("id", id)) }
        }
    }
}
