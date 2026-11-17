package art.kquinn.kuetv

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** A programme the user wants to be told about when it starts. */
data class Reminder(val id: Int, val channelId: String, val channel: String, val title: String, val startMs: Long, val stopMs: Long) {
    fun toJson(): String = JsonObject().apply {
        addProperty("id", id); addProperty("channelId", channelId); addProperty("channel", channel)
        addProperty("title", title); addProperty("start", startMs); addProperty("stop", stopMs)
    }.toString()

    companion object {
        fun fromJson(text: String): Reminder? = runCatching {
            val o = JsonParser.parseString(text).asJsonObject
            Reminder(o.get("id").asInt, o.get("channelId").asString, o.get("channel").asString, o.get("title").asString, o.get("start").asLong, o.get("stop").asLong)
        }.getOrNull()

        /** The same programme on the same channel always gets the same number, so setting it twice is setting it once. */
        fun make(channelId: String, channel: String, title: String, startMs: Long, stopMs: Long): Reminder =
            Reminder(((channelId.hashCode() * 31L + startMs / 1000).toInt()) and 0x7fffffff, channelId, channel, title, startMs, stopMs)

        /** The reminder to show at [now]: one starting within the next minute, or started in the last five. */
        fun due(all: List<Reminder>, now: Long): Reminder? =
            all.filter { now >= it.startMs - 60_000 && now < it.startMs + 5 * 60_000 }.minByOrNull { it.startMs }
    }
}

/**
 * Programme reminders. While KueTV is on screen, a reminder pops up over whatever is showing when the
 * programme starts, with OK to watch it. With KueTV closed, an alarm puts up a notice instead.
 */
object Reminders {
    var changes by mutableIntStateOf(0)
        private set

    /** The reminder popped up on screen now; null when none is. */
    var showing by mutableStateOf<Reminder?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    /** Reminders for programmes that haven't finished, soonest first. */
    fun all(context: Context): List<Reminder> {
        val now = System.currentTimeMillis()
        return prefs(context).getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() }.mapNotNull { Reminder.fromJson(it) }
            .filter { it.stopMs > now }.sortedBy { it.startMs }
    }

    private fun save(context: Context, list: List<Reminder>) {
        prefs(context).edit().putString("list", list.joinToString("\n") { it.toJson() }).apply()
        changes++
    }

    fun add(context: Context, reminder: Reminder) {
        save(context, all(context).filter { it.id != reminder.id } + reminder)
        arm(context, reminder)
    }

    /** Takes another device's reminders in place of this one's, and sets their alarms. */
    fun replaceAll(context: Context, list: List<Reminder>) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (old in all(context)) runCatching { alarms.cancel(alarm(context, old.id)) }
        save(context, list)
        arm(context)
    }

    fun remove(context: Context, reminder: Reminder) {
        save(context, all(context).filter { it.id != reminder.id })
        runCatching { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarm(context, reminder.id)) }
    }

    /** Checked every few seconds while KueTV is on screen: pops up a reminder that is due, once. */
    fun check(context: Context) {
        if (showing != null) return
        val due = Reminder.due(all(context), System.currentTimeMillis()) ?: return
        Report.note("Reminders", "popped up: " + due.channel + " · " + due.title)
        // Shown once: it is taken off the list as it pops up.
        remove(context, due)
        showing = due
    }

    private fun alarm(context: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context.applicationContext, id,
            Intent(context.applicationContext, ReminderReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** The alarm for a notice a minute before the programme starts, in case KueTV is closed then. */
    private fun arm(context: Context, reminder: Reminder) {
        val at = reminder.startMs - 60_000
        if (at <= System.currentTimeMillis()) return
        val alarms = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context, reminder.id)) }
    }

    /** Sets every alarm again: Android forgets them when the device restarts or the app is updated. */
    fun arm(context: Context) {
        for (reminder in all(context)) arm(context, reminder)
    }

    /** The notice for when KueTV is closed. Choosing it opens KueTV, where the reminder then pops up. */
    fun notify(context: Context, id: Int) {
        if (Screen.visible) return
        val reminder = all(context).firstOrNull { it.id == id } ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("reminders", "Programme reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notice = Notification.Builder(context, "reminders")
            .setContentTitle("Starting soon on " + reminder.channel)
            .setContentText(reminder.title)
            .setSmallIcon(R.drawable.kuetv_mark)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(1000 + (id % 100000), notice) }
        Report.note("Reminders", "notice put up with KueTV closed: " + reminder.channel + " · " + reminder.title)
    }
}

/** Woken a minute before a reminded programme starts. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.notify(context.applicationContext, intent.getIntExtra("id", 0))
    }
}
