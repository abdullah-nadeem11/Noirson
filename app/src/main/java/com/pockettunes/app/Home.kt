package com.pockettunes.app

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

@Composable
fun HomeScreen(
    disc: DiscoverVM,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val env = LocalEnv.current
    val vm = env.player
    val store = env.store

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..4 -> "Good night"
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val resume = store.history.firstOrNull()
    val firstShelf = disc.home.firstOrNull()

    fun startListening() {
        if (resume != null) vm.playRadio(resume)
        else if (firstShelf != null) vm.play(firstShelf.tracks, 0, firstShelf.title)
    }

    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // header: logo, wordmark, search, profile
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search") }
                Image(
                    painter = painterResource(R.drawable.avatar),
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.dp, Brand.Line, CircleShape)
                        .clickable(onClick = onLibrary),
                )
            }
        }

        // hero card
        item {
            val shape = RoundedCornerShape(24.dp)
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(shape)
                    .border(1.dp, Brand.Line, shape),
            ) {
                Image(
                    painter = painterResource(R.drawable.hero),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0x22050B10), Color(0xD9050B10))))
                )
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("$greeting,", fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Let the music find you.",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable { startListening() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Brand.Pale),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.PlayArrow, "Play", tint = Brand.Black, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                if (resume != null) "Continue Listening" else "Start Listening",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                resume?.title ?: firstShelf?.title ?: "Tap to play",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (resume != null) {
                                Text(
                                    resume.artist,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // made for you
        item { SectionTitle("Made for You", Modifier.padding(top = 0.dp)) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(Mixes.forYou) { def ->
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier
                            .width(140.dp)
                            .clip(shape)
                            .clickable { env.openDetail(Detail.Mix(def.key)) },
                    ) {
                        Image(
                            painter = painterResource(def.art),
                            contentDescription = def.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(140.dp)
                                .clip(shape)
                                .border(1.dp, Brand.Line, shape),
                        )
                        Text(
                            def.title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            def.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // recently played
        if (store.history.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("Recently Played", Modifier.weight(1f))
                    TextButton(onClick = { env.openDetail(Detail.History) }) { Text("See all") }
                }
            }
            itemsIndexed(store.history.take(5).toList()) { _, t ->
                TrackRow(t, vm.current?.mediaId == t.id) { vm.playRadio(t) }
            }
        }

        // shelves
        for (section in disc.home) {
            item(key = "shelf_" + section.title) { TrackShelf(section.title, section.tracks, vm) }
        }
        if (disc.homeLoading && disc.home.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() }
            }
        }
        if (!disc.homeLoading && disc.home.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load suggestions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { disc.loadHome() }) { Text("Try again") }
                }
            }
        }
    }
}
