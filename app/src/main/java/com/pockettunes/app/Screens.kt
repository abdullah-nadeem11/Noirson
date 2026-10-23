package com.pockettunes.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Art(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun AppHeader(subtitle: String? = null) {
    Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.noirson_mark),
                contentDescription = "Noirson",
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "NOIRSON",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 5.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** The "..." menu on every song: queue actions, favourites, playlists. */
@Composable
fun TrackMenu(t: Track, onRemove: (() -> Unit)? = null, removeLabel: String = "Remove from this list") {
    val env = LocalEnv.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Play next") },
                onClick = { open = false; env.player.playNext(t) },
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                onClick = { open = false; env.player.addToQueue(t) },
            )
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                onClick = { open = false; env.addToPlaylist(t) },
            )
            DropdownMenuItem(
                text = { Text(if (env.store.isFav(t.id)) "Remove from favourites" else "Add to favourites") },
                onClick = { open = false; env.store.toggleFav(t) },
            )
            if (onRemove != null) {
                DropdownMenuItem(
                    text = { Text(removeLabel) },
                    onClick = { open = false; onRemove() },
                )
            }
        }
    }
}

@Composable
fun TrackRow(
    t: Track,
    playing: Boolean,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Remove from this list",
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(t.artworkUrl, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                t.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (t.durationSec > 0) {
            Text(
                fmt(t.durationSec * 1000),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TrackMenu(t, onRemove, removeLabel)
    }
}

@Composable
fun LocalScreen(vm: PlayerVM, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var tracks by remember { mutableStateOf<List<Track>?>(null) }

    LaunchedEffect(granted) {
        if (granted) tracks = withContext(Dispatchers.IO) { LocalRepo.load(ctx) }
    }

    Column(modifier.fillMaxSize()) {
        val list = tracks
        when {
            !granted -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Allow access to your music files to play them here.", textAlign = TextAlign.Center)
                Spacer(Modifier.size(16.dp))
                Button(onClick = { launcher.launch(perm) }) { Text("Grant access") }
            }
            list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No music found") }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, vm.current?.mediaId == t.id) { vm.play(list, i) }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(vm: PlayerVM, onOpen: () -> Unit) {
    val item = vm.current ?: return
    val md = item.mediaMetadata
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column {
            val frac = if (vm.durationMs > 0) (vm.positionMs.toFloat() / vm.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { frac },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Art(md.artworkUri?.toString(), Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(md.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        md.artist?.toString() ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.togglePlay() }) {
                    Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/pause")
                }
                IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, "Next") }
            }
        }
    }
}

/** Decorative waveform: bars before the playhead are lit, and gently move while playing. */
@Composable
fun Waveform(seed: Int, progress: Float, playing: Boolean, modifier: Modifier = Modifier) {
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "phase",
    )
    val bars = remember(seed) {
        val rnd = Random(seed)
        List(56) { 0.3f + rnd.nextFloat() * 0.7f }
    }
    val lit = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    Canvas(modifier) {
        val n = bars.size
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val envelope = sin(PI * i / (n - 1)).toFloat().coerceAtLeast(0f).let { Math.pow(it.toDouble(), 0.6).toFloat() }
            val wobble = if (playing) 0.85f + 0.15f * sin(phase + i * 0.55f) else 1f
            val h = (size.height * bars[i] * envelope * wobble).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                color = if (i.toFloat() / n <= progress) lit else dim,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
        }
    }
}

@Composable
fun NowPlaying(vm: PlayerVM, onClose: () -> Unit) {
    val item = vm.current ?: return
    val env = LocalEnv.current
    val md = item.mediaMetadata
    val track = item.toTrack()
    val title = track.title
    val artist = track.artist
    val dur = track.durationSec
    val mediaId = item.mediaId

    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var lyrics by remember(mediaId) { mutableStateOf<LyricsResult?>(null) }
    var lyricsLoaded by remember(mediaId) { mutableStateOf(false) }
    LaunchedEffect(mediaId) {
        lyricsLoaded = false
        lyrics = Lyrics.fetch(title, artist, dur)
        lyricsLoaded = true
    }

    Box(Modifier.fillMaxSize()) {
        Surface(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Brand.BlueDeep, Brand.Black, Brand.Black))
            ),
            color = Color.Transparent,
        ) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                // top bar: close, "playing from", menu
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "PLAYING FROM",
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            vm.source.ifBlank { "Noirson" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Add to playlist") },
                                onClick = { menuOpen = false; env.addToPlaylist(track) },
                            )
                            DropdownMenuItem(
                                text = { Text(if (env.store.isFav(track.id)) "Remove from favourites" else "Add to favourites") },
                                onClick = { menuOpen = false; env.store.toggleFav(track) },
                            )
                        }
                    }
                }

                // artwork or lyrics
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    if (showLyrics) {
                        LyricsView(vm, lyrics, lyricsLoaded)
                    } else {
                        Art(
                            md.artworkUri?.toString(),
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .shadow(
                                    elevation = 28.dp,
                                    shape = RoundedCornerShape(20.dp),
                                    ambientColor = Brand.Blue,
                                    spotColor = Brand.Blue,
                                )
                                .clip(RoundedCornerShape(20.dp)),
                        )
                    }
                }

                // title, artist, favourite
                val fav = env.store.isFav(track.id)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { env.store.toggleFav(track) }) {
                        Icon(
                            if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Favourite",
                            tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // progress
                var drag by remember { mutableStateOf<Float?>(null) }
                val frac = if (vm.durationMs > 0) (vm.positionMs.toFloat() / vm.durationMs).coerceIn(0f, 1f) else 0f
                Slider(
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    ),
                    value = drag ?: frac,
                    onValueChange = { drag = it },
                    onValueChangeFinished = {
                        drag?.let { vm.seekTo((it * vm.durationMs).toLong()) }
                        drag = null
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(vm.positionMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmt(vm.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // transport
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.toggleShuffle() }) {
                        Icon(
                            Icons.Default.Shuffle, "Shuffle",
                            tint = if (vm.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.previous() }) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(36.dp)) }
                    FilledIconButton(
                        onClick = { vm.togglePlay() },
                        modifier = Modifier.size(68.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Brand.Pale,
                            contentColor = Brand.Black,
                        ),
                    ) {
                        Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/pause", Modifier.size(38.dp))
                    }
                    IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(36.dp)) }
                    IconButton(onClick = { vm.cycleRepeat() }) {
                        Icon(
                            if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            "Repeat",
                            tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // bottom row: lyrics toggle, waveform, queue
                Row(
                    Modifier.fillMaxWidth().height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showLyrics = !showLyrics }) {
                        Text(
                            if (showLyrics) "ART" else "LYRICS",
                            fontSize = 12.sp,
                            letterSpacing = 1.5.sp,
                            color = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Waveform(
                        seed = mediaId.hashCode(),
                        progress = frac,
                        playing = vm.isPlaying,
                        modifier = Modifier.weight(1f).height(36.dp).padding(horizontal = 8.dp),
                    )
                    IconButton(onClick = { showQueue = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic, "Queue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (showQueue) {
            BackHandler { showQueue = false }
            QueueScreen(vm) { showQueue = false }
        }
    }
}

@Composable
private fun LyricsView(vm: PlayerVM, lyrics: LyricsResult?, loaded: Boolean) {
    when {
        !loaded -> CircularProgressIndicator()
        lyrics == null -> Text("No lyrics found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        lyrics.synced.isNotEmpty() -> {
            val lines = lyrics.synced
            val active by remember(lines) {
                derivedStateOf { lines.indexOfLast { it.timeMs <= vm.positionMs + 300 } }
            }
            val state = rememberLazyListState()
            LaunchedEffect(active) {
                if (active >= 0) state.animateScrollToItem((active - 2).coerceAtLeast(0))
            }
            LazyColumn(Modifier.fillMaxSize(), state = state, horizontalAlignment = Alignment.CenterHorizontally) {
                itemsIndexed(lines) { i, line ->
                    Text(
                        line.text.ifBlank { "♪" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (i == active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { vm.seekTo(line.timeMs) },
                    )
                }
            }
        }
        else -> LazyColumn(Modifier.fillMaxSize()) {
            item { Text(lyrics.plain ?: "", fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
    }
}
