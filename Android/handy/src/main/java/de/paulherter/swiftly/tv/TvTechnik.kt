package de.paulherter.swiftly.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.paulherter.swiftly.SwiftlyAnwendung
import de.paulherter.swiftly.gemeinsam.Stil
import de.paulherter.swiftly.gemeinsam.uebersetzt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * **Technische Angaben eines Titels auf der Detailseite** — Bild (Codec, Aufloesung, HDR/Dolby Vision mit
 * Profil, Bildrate), Ton, Untertitel, Datei. Nur mit der Einstellung `technikAnzeigen` (Vorgabe aus).
 * Dieselben Teile wie das Technikschild im Player, hier aus `Kern.technikangaben` ohne zu oeffnen.
 */
object TvTechnik {
    /** Je Titel einmal gefragt — das Wandern durch die Folgen soll den Server nicht jedes Mal fragen. */
    private val gemerkt = LinkedHashMap<String, List<Pair<String, String>>>()

    fun gemerkt(id: String) = gemerkt[id]

    suspend fun holen(app: SwiftlyAnwendung, id: String): List<Pair<String, String>> {
        gemerkt[id]?.let { return it }
        val roh = withContext(Dispatchers.IO) { app.kern.technikangaben(id).await() }
        val a = runCatching { JSONArray(roh) }.getOrNull() ?: return emptyList()
        val zeilen = (0 until a.length()).map { a.getJSONObject(it).let { o ->
            o.optString("schluessel").let { k -> if (k.isEmpty()) "" else uebersetzt(k) } to o.optString("text")
        } }
        gemerkt[id] = zeilen
        if (gemerkt.size > 100) gemerkt.remove(gemerkt.keys.first())
        return zeilen
    }
}

/**
 * Die Angaben zu `id` als kleine Tabelle, Name leise, Wert hell. `warten`: erst nach einer kurzen Ruhe
 * fragen — beim schnellen Durchblaettern der Folgen nicht fuer jede einzelne.
 */
@Composable
fun TvTechnikangaben(app: SwiftlyAnwendung, id: String?, modifier: Modifier = Modifier, warten: Boolean = false) {
    var zeilen by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(id) {
        if (id == null) { zeilen = emptyList(); return@LaunchedEffect }
        TvTechnik.gemerkt(id)?.let { zeilen = it; return@LaunchedEffect }
        if (warten) delay(400)
        zeilen = try { TvTechnik.holen(app, id) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }
    if (zeilen.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        zeilen.forEach { (name, wert) ->
            Row {
                Text(name, style = TvStil.klein, color = Stil.schriftLeise, maxLines = 1, modifier = Modifier.width(130.dp))
                Text(wert, style = TvStil.klein, color = Stil.schrift, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
