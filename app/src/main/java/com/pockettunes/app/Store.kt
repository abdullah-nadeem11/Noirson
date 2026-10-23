package com.pockettunes.app

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

data class Playlist(val id: String, val name: String, val tracks: List<Track>)

fun Track.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("artist", artist)
    .put("art", artworkUrl ?: JSONObject.NULL)
    .put("dur", durationSec)
    .put("uri", uri)

fun JSONObject.toTrack(): Track = Track(
    id = getString("id"),
    title = optString("title"),
    artist = optString("artist"),
    artworkUrl = if (isNull("art")) null else optString("art"),
    durationSec = optLong("dur"),
    uri = optString("uri"),
)

private fun jsonArrayOf(items: List<JSONObject>): JSONArray =
    JSONArray().also { arr -> items.forEach { arr.put(it) } }

private fun JSONArray?.toTracks(): List<Track> =
    if (this == null) emptyList() else (0 until length()).map { getJSONObject(it).toTrack() }

/** Favourites, playlists, play history and search history, saved to a JSON file on the phone. */
class StoreVM(app: Application) : AndroidViewModel(app) {
    private val file = File(app.filesDir, "library.json")
    private val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    val favourites = mutableStateListOf<Track>()
    val history = mutableStateListOf<Track>()
    val searches = mutableStateListOf<String>()
    val playlists = mutableStateListOf<Playlist>()

    init {
        viewModelScope.launch {
            val text = withContext(io) { runCatching { file.readText() }.getOrNull() }
            if (text != null) runCatching { load(JSONObject(text)) }
        }
    }

    private fun load(j: JSONObject) {
        favourites.clear()
        favourites.addAll(j.optJSONArray("fav").toTracks())
        history.clear()
        history.addAll(j.optJSONArray("history").toTracks())
        searches.clear()
        j.optJSONArray("searches")?.let { a ->
            for (i in 0 until a.length()) searches.add(a.getString(i))
        }
        playlists.clear()
        j.optJSONArray("playlists")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i)
                playlists.add(
                    Playlist(p.getString("id"), p.optString("name"), p.optJSONArray("tracks").toTracks())
                )
            }
        }
    }

    private fun save() {
        val j = JSONObject()
        j.put("fav", jsonArrayOf(favourites.map { it.toJson() }))
        j.put("history", jsonArrayOf(history.map { it.toJson() }))
        j.put("searches", JSONArray().also { a -> searches.forEach { a.put(it) } })
        j.put(
            "playlists",
            jsonArrayOf(
                playlists.map { p ->
                    JSONObject()
                        .put("id", p.id)
                        .put("name", p.name)
                        .put("tracks", jsonArrayOf(p.tracks.map { it.toJson() }))
                }
            ),
        )
        val text = j.toString()
        viewModelScope.launch(io) { runCatching { file.writeText(text) } }
    }

    // favourites
    fun isFav(id: String): Boolean = favourites.any { it.id == id }

    fun toggleFav(t: Track) {
        val i = favourites.indexOfFirst { it.id == t.id }
        if (i >= 0) favourites.removeAt(i) else favourites.add(0, t)
        save()
    }

    // history
    fun addHistory(t: Track) {
        history.removeAll { it.id == t.id }
        history.add(0, t)
        while (history.size > 40) history.removeAt(history.lastIndex)
        save()
    }

    // search history
    fun addSearch(q: String) {
        val s = q.trim()
        if (s.isEmpty()) return
        searches.removeAll { it.equals(s, ignoreCase = true) }
        searches.add(0, s)
        while (searches.size > 8) searches.removeAt(searches.lastIndex)
        save()
    }

    fun removeSearch(q: String) {
        searches.remove(q)
        save()
    }

    fun clearSearches() {
        searches.clear()
        save()
    }

    // playlists
    fun createPlaylist(name: String): String {
        val p = Playlist(UUID.randomUUID().toString(), name.trim().ifEmpty { "New playlist" }, emptyList())
        playlists.add(p)
        save()
        return p.id
    }

    fun deletePlaylist(id: String) {
        playlists.removeAll { it.id == id }
        save()
    }

    fun addToPlaylist(id: String, t: Track) {
        val i = playlists.indexOfFirst { it.id == id }
        if (i < 0) return
        val p = playlists[i]
        if (p.tracks.any { it.id == t.id }) return
        playlists[i] = p.copy(tracks = p.tracks + t)
        save()
    }

    fun removeFromPlaylist(id: String, trackId: String) {
        val i = playlists.indexOfFirst { it.id == id }
        if (i < 0) return
        val p = playlists[i]
        playlists[i] = p.copy(tracks = p.tracks.filter { it.id != trackId })
        save()
    }
}
