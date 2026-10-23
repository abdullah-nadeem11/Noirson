package com.pockettunes.app

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared handles every screen needs. */
class Env(
    val player: PlayerVM,
    val store: StoreVM,
    val addToPlaylist: (Track) -> Unit,
    val openDetail: (Detail) -> Unit,
)

val LocalEnv = staticCompositionLocalOf<Env> { error("Env not provided") }

/** A page that lists songs. */
sealed class Detail {
    data class Remote(val url: String, val title: String, val subtitle: String, val art: String?) : Detail()
    data class Mix(val key: String) : Detail()
    data class Artist(val name: String) : Detail()
    data class UserPlaylist(val id: String) : Detail()
    object Favourites : Detail()
    object History : Detail()
}

@Composable
fun AddToPlaylistDialog(track: Track, onDismiss: () -> Unit) {
    val store = LocalEnv.current.store
    val ctx = LocalContext.current
    var newName by remember { mutableStateOf("") }

    fun done(msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                if (store.playlists.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 240.dp).padding(bottom = 8.dp)) {
                        itemsIndexed(store.playlists.toList()) { _, p ->
                            Text(
                                "${p.name}  (${p.tracks.size})",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        store.addToPlaylist(p.id, track)
                                        done("Added to ${p.name}")
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank(),
                onClick = {
                    val id = store.createPlaylist(newName)
                    store.addToPlaylist(id, track)
                    done("Added to ${newName.trim()}")
                },
            ) { Text("Create & add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NewPlaylistDialog(onDismiss: () -> Unit) {
    val store = LocalEnv.current.store
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    store.createPlaylist(name)
                    onDismiss()
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    round: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = if (round) CircleShape else RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, Brand.Line, shape),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun PlaylistsTab(onNew: () -> Unit) {
    val env = LocalEnv.current
    val store = env.store
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            LibraryRow(
                title = "Create Playlist",
                subtitle = "Make your own mix",
                icon = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurface) },
                onClick = onNew,
            )
        }
        item {
            LibraryRow(
                title = "My Favorites",
                subtitle = "${store.favourites.size} songs",
                icon = { Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { env.openDetail(Detail.Favourites) },
            )
        }
        itemsIndexed(store.playlists.toList(), key = { _, p -> p.id }) { _, p ->
            var menu by remember { mutableStateOf(false) }
            LibraryRow(
                title = p.name,
                subtitle = "${p.tracks.size} songs",
                icon = { Art(p.tracks.firstOrNull()?.artworkUrl, Modifier.fillMaxSize()) },
                trailing = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete playlist") },
                                onClick = {
                                    menu = false
                                    store.deletePlaylist(p.id)
                                },
                            )
                        }
                    }
                },
                onClick = { env.openDetail(Detail.UserPlaylist(p.id)) },
            )
        }
    }
}

private data class ArtistEntry(val name: String, val count: Int, val art: String?)

@Composable
private fun ArtistsTab() {
    val env = LocalEnv.current
    val store = env.store
    val artists = remember(store.favourites.size, store.history.size) {
        (store.favourites + store.history)
            .filter { it.id.startsWith("yt:") && it.artist.isNotBlank() }
            .groupBy { it.artist }
            .map { (name, list) -> ArtistEntry(name, list.size, list.first().artworkUrl) }
            .sortedByDescending { it.count }
            .take(60)
    }
    if (artists.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
            Text(
                "Favourite or play some songs and your artists will show up here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(artists, key = { _, a -> a.name }) { _, a ->
                LibraryRow(
                    title = a.name,
                    subtitle = "${a.count} songs in your library",
                    icon = { Art(a.art, Modifier.fillMaxSize()) },
                    round = true,
                    onClick = { env.openDetail(Detail.Artist(a.name)) },
                )
            }
        }
    }
}

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    val env = LocalEnv.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showNew by remember { mutableStateOf(false) }
    val tabs = listOf("Playlists", "Artists", "On phone")

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        AppHeader()
        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {},
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(label) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (tab) {
            0 -> PlaylistsTab(onNew = { showNew = true })
            1 -> ArtistsTab()
            else -> LocalScreen(env.player, Modifier.fillMaxSize())
        }
    }
    if (showNew) NewPlaylistDialog { showNew = false }
}

@Composable
fun DetailScreen(detail: Detail, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val env = LocalEnv.current
    val store = env.store
    val vm = env.player

    var remote by remember(detail) { mutableStateOf<List<Track>?>(null) }
    var error by remember(detail) { mutableStateOf<String?>(null) }
    LaunchedEffect(detail) {
        try {
            when (detail) {
                is Detail.Remote -> remote = Browse.loadCollection(detail.url, detail.art)
                is Detail.Mix ->
                    remote = Mixes.find(detail.key)?.let { Browse.loadMix(it.queries) } ?: emptyList()
                is Detail.Artist ->
                    remote = Browse.search(detail.name, SearchKind.Songs)
                        .filterIsInstance<Hit.Song>()
                        .map { it.track }
                else -> {}
            }
        } catch (e: Exception) {
            error = "Couldn't load: ${e.message}"
            remote = emptyList()
        }
        Unit
    }

    val playlist = (detail as? Detail.UserPlaylist)?.let { d -> store.playlists.firstOrNull { it.id == d.id } }
    val mix = (detail as? Detail.Mix)?.let { Mixes.find(it.key) }

    val tracks: List<Track>? = when (detail) {
        is Detail.Remote, is Detail.Mix, is Detail.Artist -> remote
        is Detail.Favourites -> store.favourites.toList()
        is Detail.History -> store.history.toList()
        is Detail.UserPlaylist -> playlist?.tracks
    }
    val title = when (detail) {
        is Detail.Remote -> detail.title
        is Detail.Mix -> mix?.title ?: "Mix"
        is Detail.Artist -> detail.name
        is Detail.Favourites -> "My Favorites"
        is Detail.History -> "Recently Played"
        is Detail.UserPlaylist -> playlist?.name ?: "Playlist"
    }
    val subtitle = when (detail) {
        is Detail.Remote -> detail.subtitle
        is Detail.Mix -> "${mix?.subtitle ?: ""}${if (tracks != null) " · ${tracks.size} songs" else ""}"
        is Detail.Artist -> "Artist · songs"
        else -> "${tracks?.size ?: 0} songs"
    }
    val artUrl = (detail as? Detail.Remote)?.art ?: tracks?.firstOrNull()?.artworkUrl

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            if (detail is Detail.UserPlaylist) {
                IconButton(onClick = {
                    store.deletePlaylist(detail.id)
                    onBack()
                }) { Icon(Icons.Default.Delete, "Delete playlist") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(14.dp)
            if (mix != null) {
                Image(
                    painter = painterResource(mix.art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(shape).border(1.dp, Brand.Line, shape),
                )
            } else {
                Art(artUrl, Modifier.size(96.dp).clip(shape))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val list = tracks.orEmpty()
            Button(onClick = { vm.play(list, 0, title) }, enabled = list.isNotEmpty()) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Play")
            }
            OutlinedButton(onClick = { vm.play(list.shuffled(), 0, title) }, enabled = list.isNotEmpty()) {
                Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Shuffle")
            }
        }

        when {
            tracks == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            tracks.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    error ?: "No songs here yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(tracks) { i, t ->
                    val remove: (() -> Unit)? = when (detail) {
                        is Detail.Favourites -> ({ store.toggleFav(t) })
                        is Detail.UserPlaylist -> ({ store.removeFromPlaylist(detail.id, t.id) })
                        else -> null
                    }
                    TrackRow(
                        t = t,
                        playing = vm.current?.mediaId == t.id,
                        onRemove = remove,
                        removeLabel = if (detail is Detail.Favourites) "Remove from favourites" else "Remove from playlist",
                    ) { vm.play(tracks, i, title) }
                }
            }
        }
    }
}
