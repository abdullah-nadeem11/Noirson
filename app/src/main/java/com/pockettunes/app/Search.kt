package com.pockettunes.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/** Search + home feed state; survives tab switches. */
class DiscoverVM(app: Application) : AndroidViewModel(app) {
    var query by mutableStateOf("")
    var kind by mutableStateOf(SearchKind.Songs)
    var hits by mutableStateOf<List<Hit>>(emptyList())
        private set
    var searched by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var home by mutableStateOf<List<HomeSection>>(emptyList())
        private set
    var homeLoading by mutableStateOf(false)
        private set

    init {
        loadHome()
    }

    fun loadHome() {
        if (homeLoading) return
        homeLoading = true
        viewModelScope.launch {
            try {
                home = Browse.home()
            } catch (e: Exception) {
                // keep whatever we had
            } finally {
                homeLoading = false
            }
        }
    }

    fun onQueryChange(s: String) {
        query = s
        if (s.isBlank()) {
            searched = false
            hits = emptyList()
            error = null
        }
    }

    fun setKindAndSearch(k: SearchKind) {
        kind = k
        if (query.isNotBlank()) search()
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        searched = true
        loading = true
        error = null
        val k = kind
        viewModelScope.launch {
            try {
                hits = Browse.search(q, k)
                if (hits.isEmpty()) error = "No results"
            } catch (e: Exception) {
                hits = emptyList()
                error = "Search failed: ${e.message}"
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
    )
}

/** A horizontal row of song covers. Tapping one starts that song's radio. */
@Composable
fun TrackShelf(title: String, tracks: List<Track>, vm: PlayerVM) {
    SectionTitle(title)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(tracks) { _, t ->
            Column(
                Modifier
                    .width(140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { vm.playRadio(t) },
            ) {
                Art(t.artworkUrl, Modifier.size(140.dp).clip(RoundedCornerShape(14.dp)))
                Text(
                    t.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    t.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CollectionRow(h: Hit.Collection, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(h.art, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            Text(
                h.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArtistRow(h: Hit.Artist, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(h.art, Modifier.size(56.dp).clip(CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            Text(h.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GenreTile(def: MixDef, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .aspectRatio(2.1f)
            .clip(shape)
            .border(1.dp, Brand.Line, shape)
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(def.art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
        )
        Text(
            def.title,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}

@Composable
private fun SuggestionRow(text: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Text(text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val starterSearches = listOf("lofi", "dark ambient", "phonk", "acoustic covers", "workout music", "slowed reverb")

@Composable
fun SearchScreen(disc: DiscoverVM, modifier: Modifier = Modifier) {
    val env = LocalEnv.current
    val vm = env.player
    val store = env.store
    val ctx = LocalContext.current

    fun runSearch() {
        store.addSearch(disc.query)
        disc.search()
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            disc.query = text
            runSearch()
        }
    }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = disc.query,
            onValueChange = { disc.onQueryChange(it) },
            placeholder = { Text("Search songs, artists, albums...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (disc.query.isNotEmpty()) {
                    IconButton(onClick = { disc.onQueryChange("") }) { Icon(Icons.Default.Close, "Clear") }
                } else {
                    IconButton(onClick = {
                        try {
                            speech.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                            )
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Voice search isn't available on this phone", Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(Icons.Default.Mic, "Voice search") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )

        if (disc.searched) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SearchKind.values().toList()) { k ->
                    FilterChip(
                        selected = disc.kind == k,
                        onClick = { disc.setKindAndSearch(k) },
                        label = { Text(k.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            when {
                disc.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                disc.error != null && disc.hits.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(disc.error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp)) {
                    itemsIndexed(disc.hits) { _, hit ->
                        when (hit) {
                            is Hit.Song -> TrackRow(hit.track, vm.current?.mediaId == hit.track.id) {
                                vm.playRadio(hit.track)
                            }
                            is Hit.Collection -> CollectionRow(hit) {
                                env.openDetail(Detail.Remote(hit.url, hit.title, hit.subtitle, hit.art))
                            }
                            is Hit.Artist -> ArtistRow(hit) {
                                env.openDetail(Detail.Artist(hit.name))
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item { SectionTitle("Browse Categories", Modifier.padding(top = 0.dp)) }
                items(Mixes.genres.chunked(2)) { pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { def ->
                            GenreTile(def, Modifier.weight(1f)) { env.openDetail(Detail.Mix(def.key)) }
                        }
                    }
                }
                if (store.searches.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionTitle("Recent Searches", Modifier.weight(1f))
                            TextButton(onClick = { store.clearSearches() }) { Text("Clear") }
                        }
                    }
                    items(store.searches.toList()) { q ->
                        SuggestionRow(
                            text = q,
                            icon = { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                disc.query = q
                                runSearch()
                            },
                        )
                    }
                }
                item { SectionTitle("Try Searching") }
                items(starterSearches) { q ->
                    SuggestionRow(
                        text = q,
                        icon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = {
                            disc.query = q
                            runSearch()
                        },
                    )
                }
            }
        }
    }
}
