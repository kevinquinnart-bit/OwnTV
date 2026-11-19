package art.kquinn.kuetv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The weather where the TV is, for the bar along the top: a small picture and the temperature, e.g. "🌧 8°".
 * The place comes from the internet connection's rough location (town level); the weather from Open-Meteo.
 * Neither needs an account. Fetched again every 30 minutes.
 */
object Weather {
    var now by mutableStateOf<String?>(null)
        private set

    /** The country the TV is in, from the same rough location ("IE", "GB", …); null until known. */
    var country by mutableStateOf<String?>(null)
        private set

    private var fetchedAt = 0L
    private var place: Pair<Double, Double>? = null
    private var placedAt = System.currentTimeMillis()
    private val http by lazy { Provider.http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build() }

    private fun get(url: String): JSONObject = http.newCall(Request.Builder().url(url).header("User-Agent", Provider.USER_AGENT).build())
        .execute().use { r -> if (!r.isSuccessful) throw IllegalStateException("answered " + r.code); JSONObject(r.body.string()) }

    suspend fun refresh() {
        if (System.currentTimeMillis() - fetchedAt < 30 * 60_000L && now != null) return
        // The TV moves between houses: where it is gets checked again every 6 hours.
        if (System.currentTimeMillis() - placedAt > 6 * 60 * 60_000L) { place = null; placedAt = System.currentTimeMillis() }
        val line = withContext(Dispatchers.IO) {
            runCatching {
                val (lat, lon) = place ?: get("https://ipapi.co/json/").let { j ->
                    country = j.optString("country_code").ifBlank { null }
                    j.getDouble("latitude") to j.getDouble("longitude")
                }.also { place = it }
                val c = get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code").getJSONObject("current")
                picture(c.getInt("weather_code")) + " " + Math.round(c.getDouble("temperature_2m")) + "°"
            }.onFailure { Report.note("Weather", it.message.orEmpty()) }.getOrNull()
        }
        if (line != null) {
            now = line
            fetchedAt = System.currentTimeMillis()
        }
    }

    /** Open-Meteo's weather codes as a small picture. */
    internal fun picture(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "☁️"
    }
}
