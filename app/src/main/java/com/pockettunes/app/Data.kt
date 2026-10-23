package com.pockettunes.app

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A playable song, either from YouTube Music or from the phone's storage. */
data class Track(
    val id: String,          // media id, e.g. "yt:abc123" or "local:42"
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationSec: Long,
    val uri: String,         // what ExoPlayer plays ("yt://resolve/abc123" or a content:// uri)
)

object Net {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/** Bridges NewPipeExtractor's HTTP needs onto OkHttp. */
object OkDownloader : Downloader() {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        var body: RequestBody? = request.dataToSend()?.toRequestBody()
        if (body == null && (method == "POST" || method == "PUT")) {
            body = ByteArray(0).toRequestBody()
        }
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(method, body)
            .header("User-Agent", UA)
        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            for (v in values) builder.addHeader(name, v)
        }
        return Net.http.newCall(builder.build()).execute().use { r ->
            if (r.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }
            Response(
                r.code,
                r.message,
                r.headers.toMultimap(),
                r.body?.string(),
                r.request.url.toString(),
            )
        }
    }
}

object NewPipeInit {
    @Volatile private var done = false
    fun ensure() {
        if (done) return
        synchronized(this) {
            if (!done) {
                NewPipe.init(OkDownloader, Localization("en", "US"))
                done = true
            }
        }
    }
}

object YtRepo {
    private val idRegex = Regex("[?&]v=([^&]+)")

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        NewPipeInit.ensure()
        val yt = ServiceList.YouTube
        val handler = yt.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            "",
        )
        val info = SearchInfo.getInfo(yt, handler)
        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val id = idRegex.find(item.url)?.groupValues?.get(1) ?: return@mapNotNull null
                Track(
                    id = "yt:$id",
                    title = item.name ?: "Unknown",
                    artist = (item.uploaderName ?: "").removeSuffix(" - Topic"),
                    artworkUrl = item.thumbnails.maxByOrNull { it.height }?.url,
                    durationSec = item.duration,
                    uri = "yt://resolve/$id",
                )
            }
    }

    /** Looks up a fresh direct audio URL for a video id (stream URLs expire). */
    fun resolveStreamUrl(videoId: String): String {
        NewPipeInit.ensure()
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val audio = info.audioStreams.filter { it.isUrl }
        val best = audio.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: audio.maxByOrNull { it.averageBitrate }
            ?: throw IOException("No playable audio stream found")
        return best.content
    }
}

object LocalRepo {
    fun load(ctx: Context): List<Track> {
        val out = ArrayList<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
        )
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val art = "content://media/external/audio/albumart/${c.getLong(albumCol)}"
                val artist = c.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: "Unknown artist"
                out += Track(
                    id = "local:$id",
                    title = c.getString(titleCol) ?: "Unknown",
                    artist = artist,
                    artworkUrl = art,
                    durationSec = c.getLong(durCol) / 1000,
                    uri = uri.toString(),
                )
            }
        }
        return out
    }
}

data class LyricLine(val timeMs: Long, val text: String)
data class LyricsResult(val synced: List<LyricLine>, val plain: String?)

/** Synced lyrics from the free LRCLIB service. */
object Lyrics {
    private val lrcLine = Regex("""\[(\d+):(\d+(?:\.\d+)?)](.*)""")

    suspend fun fetch(title: String, artist: String, durationSec: Long): LyricsResult? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                    .addQueryParameter("track_name", title)
                    .addQueryParameter("artist_name", artist)
                    .apply { if (durationSec > 0) addQueryParameter("duration", durationSec.toString()) }
                    .build()
                val req = okhttp3.Request.Builder().url(url)
                    .header("User-Agent", "Noirson/0.1 (Android)")
                    .build()
                Net.http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@withContext null
                    val json = JSONObject(r.body?.string() ?: return@withContext null)
                    val synced = parseLrc(json.optString("syncedLyrics", ""))
                    val plain = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }
                    if (synced.isEmpty() && plain == null) null else LyricsResult(synced, plain)
                }
            } catch (e: Exception) {
                null
            }
        }

    private fun parseLrc(text: String): List<LyricLine> =
        text.lineSequence().mapNotNull { line ->
            val m = lrcLine.matchEntire(line.trim()) ?: return@mapNotNull null
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toDouble()
            LyricLine((min * 60_000 + sec * 1000).toLong(), m.groupValues[3].trim())
        }.toList()
}
