package com.pockettunes.app

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.Timeline
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Turns "yt://resolve/<id>" into a real, fresh googlevideo URL right before playback. */
@UnstableApi
object YtResolver : ResolvingDataSource.Resolver {
    private class Cached(val url: String, val at: Long)

    private val cache = ConcurrentHashMap<String, Cached>()
    private const val TTL_MS = 90L * 60 * 1000

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != "yt") return dataSpec
        val id = uri.lastPathSegment ?: throw IOException("Bad track uri")
        val now = System.currentTimeMillis()
        val url = cache[id]?.takeIf { now - it.at < TTL_MS }?.url
            ?: try {
                YtRepo.resolveStreamUrl(id).also { cache[id] = Cached(it, now) }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException("Could not load stream: ${e.message}", e)
            }
        return dataSpec.withUri(Uri.parse(url))
    }
}

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        NewPipeInit.ensure()
        val upstream = DefaultDataSource.Factory(this)
        val resolving = ResolvingDataSource.Factory(upstream, YtResolver)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
                .setExtras(Bundle().apply { putLong("dur", durationSec) })
                .build()
        )
        .build()

fun MediaItem.toTrack(): Track = Track(
    id = mediaId,
    title = mediaMetadata.title?.toString() ?: "",
    artist = mediaMetadata.artist?.toString() ?: "",
    artworkUrl = mediaMetadata.artworkUri?.toString(),
    durationSec = mediaMetadata.extras?.getLong("dur") ?: 0L,
    uri = localConfiguration?.uri?.toString() ?: "",
)

fun fmt(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** UI-facing playback state; talks to PlaybackService through a MediaController. */
class PlayerVM(private val app: Application) : AndroidViewModel(app) {
    private var controller: MediaController? = null

    var current by mutableStateOf<MediaItem?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    /** The play queue, mirrored from the player so the UI can show and edit it. */
    val queue = mutableStateListOf<MediaItem>()
    var currentIndex by mutableIntStateOf(0)
        private set

    /** When on, similar songs are appended as the queue runs out. */
    var autoplay by mutableStateOf(true)

    /** Where the current queue came from, shown as "Playing from" on the player. */
    var source by mutableStateOf("")
        private set

    private var extending = false
    private var lastExtendedFor: String? = null

    private fun refreshQueue() {
        val c = controller ?: return
        queue.clear()
        for (i in 0 until c.mediaItemCount) queue.add(c.getMediaItemAt(i))
        currentIndex = c.currentMediaItemIndex
    }

    private fun maybeExtendQueue() {
        val c = controller ?: return
        val item = c.currentMediaItem ?: return
        if (!autoplay || extending || !item.mediaId.startsWith("yt:")) return
        if (c.currentMediaItemIndex < c.mediaItemCount - 2) return
        if (lastExtendedFor == item.mediaId) return
        lastExtendedFor = item.mediaId
        extending = true
        viewModelScope.launch {
            try {
                val existing = HashSet<String>()
                for (i in 0 until c.mediaItemCount) existing.add(c.getMediaItemAt(i).mediaId)
                val more = Browse.radio(item.mediaId.removePrefix("yt:"))
                    .filter { it.id !in existing }
                    .take(15)
                if (more.isNotEmpty()) c.addMediaItems(more.map { it.toMediaItem() })
            } catch (e: Exception) {
                lastExtendedFor = null
            } finally {
                extending = false
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            current = mediaItem
            refreshQueue()
            maybeExtendQueue()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            refreshQueue()
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            shuffle = enabled
        }

        override fun onRepeatModeChanged(mode: Int) {
            repeatMode = mode
        }

        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(app, "Playback error: ${error.cause?.message ?: error.message}", Toast.LENGTH_LONG).show()
            controller?.let { if (it.hasNextMediaItem()) { it.seekToNextMediaItem(); it.prepare() } }
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { c ->
                controller = c
                c.addListener(listener)
                current = c.currentMediaItem
                isPlaying = c.isPlaying
                shuffle = c.shuffleModeEnabled
                repeatMode = c.repeatMode
                refreshQueue()
            }
        }, ContextCompat.getMainExecutor(app))

        viewModelScope.launch {
            while (true) {
                delay(400)
                controller?.let {
                    positionMs = it.currentPosition
                    durationMs = it.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L
                }
            }
        }
    }

    fun play(tracks: List<Track>, index: Int, source: String = "") {
        val c = controller ?: return
        this.source = source
        c.setMediaItems(tracks.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    /**
     * Plays one song and then keeps going with songs related to it, like YouTube Music does
     * when you pick a song from search.
     */
    fun playRadio(t: Track) {
        val c = controller ?: return
        source = "Radio"
        c.setMediaItems(listOf(t.toMediaItem()), 0, 0L)
        c.prepare()
        c.play()
        if (!t.id.startsWith("yt:")) return
        lastExtendedFor = t.id
        extending = true
        viewModelScope.launch {
            try {
                val more = Browse.radio(t.id.removePrefix("yt:"))
                    .filter { it.id != t.id }
                    .take(25)
                val stillFirst = c.mediaItemCount > 0 && c.getMediaItemAt(0).mediaId == t.id
                if (more.isNotEmpty() && stillFirst) c.addMediaItems(more.map { it.toMediaItem() })
            } catch (e: Exception) {
                lastExtendedFor = null
            } finally {
                extending = false
            }
        }
    }

    fun playNext(t: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(t), 0)
            return
        }
        c.addMediaItem(c.currentMediaItemIndex + 1, t.toMediaItem())
        Toast.makeText(app, "Playing next", Toast.LENGTH_SHORT).show()
    }

    fun addToQueue(t: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(t), 0)
            return
        }
        c.addMediaItem(t.toMediaItem())
        Toast.makeText(app, "Added to queue", Toast.LENGTH_SHORT).show()
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0L)
        c.play()
    }

    fun removeFromQueue(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (to < 0 || to >= c.mediaItemCount) return
        c.moveMediaItem(from, to)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        positionMs = ms
    }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
