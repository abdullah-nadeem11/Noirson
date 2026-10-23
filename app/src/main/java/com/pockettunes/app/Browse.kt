package com.pockettunes.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

enum class SearchKind(val label: String, val filter: String) {
    Songs("Songs", YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
    Albums("Albums", YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS),
    Artists("Artists", YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS),
    Playlists("Playlists", YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS),
}

/** One row in search results. */
sealed class Hit {
    data class Song(val track: Track) : Hit()
    data class Collection(
        val url: String,
        val title: String,
        val subtitle: String,
        val art: String?,
        val isAlbum: Boolean,
    ) : Hit()
    data class Artist(val name: String, val art: String?, val subtitle: String) : Hit()
}

data class HomeSection(val title: String, val tracks: List<Track>)

private val vidRegex = Regex("[?&]v=([^&]+)")

fun StreamInfoItem.toTrackOrNull(fallbackArt: String? = null): Track? {
    val id = vidRegex.find(url)?.groupValues?.get(1) ?: return null
    return Track(
        id = "yt:$id",
        title = name ?: "Unknown",
        artist = (uploaderName ?: "").removeSuffix(" - Topic"),
        artworkUrl = thumbnails.maxByOrNull { it.height }?.url ?: fallbackArt,
        durationSec = duration,
        uri = "yt://resolve/$id",
    )
}

object Browse {

    suspend fun search(query: String, kind: SearchKind): List<Hit> = withContext(Dispatchers.IO) {
        NewPipeInit.ensure()
        val yt = ServiceList.YouTube
        val handler = yt.searchQHFactory.fromQuery(query, listOf(kind.filter), "")
        val items = SearchInfo.getInfo(yt, handler).relatedItems
        items.mapNotNull { item ->
            when (item) {
                is StreamInfoItem -> item.toTrackOrNull()?.let { Hit.Song(it) }
                is PlaylistInfoItem -> Hit.Collection(
                    url = item.url,
                    title = item.name ?: "Untitled",
                    subtitle = listOfNotNull(
                        if (kind == SearchKind.Albums) "Album" else "Playlist",
                        item.uploaderName?.takeIf { it.isNotBlank() },
                    ).joinToString(" • "),
                    art = item.thumbnails.maxByOrNull { it.height }?.url,
                    isAlbum = kind == SearchKind.Albums,
                )
                is ChannelInfoItem -> Hit.Artist(
                    name = item.name ?: "Unknown",
                    art = item.thumbnails.maxByOrNull { it.height }?.url,
                    subtitle = "Artist",
                )
                else -> null
            }
        }
    }

    /** All songs of an album or playlist (follows up to a few extra pages). */
    suspend fun loadCollection(url: String, fallbackArt: String?): List<Track> =
        withContext(Dispatchers.IO) {
            NewPipeInit.ensure()
            val yt = ServiceList.YouTube
            val info = PlaylistInfo.getInfo(yt, url)
            val items = ArrayList<StreamInfoItem>(info.relatedItems)
            var page = info.nextPage
            var guard = 0
            while (page != null && guard < 5 && items.size < 200) {
                val more = PlaylistInfo.getMoreItems(yt, url, page)
                items.addAll(more.items)
                page = more.nextPage
                guard++
            }
            items.mapNotNull { it.toTrackOrNull(fallbackArt) }
        }

    /** Songs YouTube suggests after the given one (used for autoplay). */
    suspend fun related(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        NewPipeInit.ensure()
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toTrackOrNull() }
            .filter { it.durationSec in 60..900 }
    }

    /**
     * YouTube's own "radio" for a song: the playlist YouTube Music plays after it.
     * Falls back to the related-songs list if the radio can't be loaded.
     */
    suspend fun radio(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        NewPipeInit.ensure()
        try {
            val info = PlaylistInfo.getInfo(
                ServiceList.YouTube,
                "https://www.youtube.com/watch?v=$videoId&list=RD$videoId",
            )
            val tracks = info.relatedItems.mapNotNull { it.toTrackOrNull() }
            if (tracks.size >= 3) return@withContext tracks
        } catch (e: Exception) {
            // fall through to related songs
        }
        related(videoId)
    }

    /** Combines several searches into one mixed, de-duplicated song list. */
    suspend fun loadMix(queries: List<String>): List<Track> = coroutineScope {
        val lists = queries
            .map { q -> async { runCatching { YtRepo.search(q) }.getOrDefault(emptyList()) } }
            .awaitAll()
        val out = ArrayList<Track>()
        val seen = HashSet<String>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) {
            for (l in lists) {
                val t = l.getOrNull(i) ?: continue
                if (seen.add(t.id)) out.add(t)
            }
        }
        out.take(45)
    }

    /** Discover home: a few music shelves built from searches. */
    suspend fun home(): List<HomeSection> = coroutineScope {
        listOf(
            "Top hits" to "top hits this week",
            "New releases" to "new songs this week",
            "Trending now" to "trending music",
            "Pop" to "best pop songs",
            "Hip hop" to "best hip hop songs",
            "Chill" to "chill lofi songs",
        ).map { (title, q) ->
            async {
                runCatching { HomeSection(title, YtRepo.search(q).take(12)) }
                    .getOrNull()?.takeIf { it.tracks.isNotEmpty() }
            }
        }.awaitAll().filterNotNull()
    }
}
