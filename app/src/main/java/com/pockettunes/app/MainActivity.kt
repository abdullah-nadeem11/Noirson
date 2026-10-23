package com.pockettunes.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { NoirsonApp() }
    }
}

@Composable
fun NoirsonApp(
    vm: PlayerVM = viewModel(),
    store: StoreVM = viewModel(),
    disc: DiscoverVM = viewModel(),
) {
    AppTheme {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showPlayer by rememberSaveable { mutableStateOf(false) }
        var detail by remember { mutableStateOf<Detail?>(null) }
        var addTarget by remember { mutableStateOf<Track?>(null) }

        val env = remember {
            Env(
                player = vm,
                store = store,
                addToPlaylist = { addTarget = it },
                openDetail = { detail = it },
            )
        }

        if (Build.VERSION.SDK_INT >= 33) {
            val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) { notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }

        // Record a song in "Recently played" once it has played for a few seconds.
        val currentId = vm.current?.mediaId
        LaunchedEffect(currentId) {
            if (currentId != null) {
                delay(5000)
                vm.current?.takeIf { it.mediaId == currentId }?.let { store.addHistory(it.toTrack()) }
            }
        }

        val navColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = Color.Transparent,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CompositionLocalProvider(LocalEnv provides env) {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        Column {
                            if (vm.current != null) MiniPlayer(vm) { showPlayer = true }
                            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                                NavigationBarItem(
                                    selected = tab == 0,
                                    onClick = { tab = 0; detail = null },
                                    icon = { Icon(Icons.Default.Home, null) },
                                    label = { Text("Home") },
                                    colors = navColors,
                                )
                                NavigationBarItem(
                                    selected = tab == 1,
                                    onClick = { tab = 1; detail = null },
                                    icon = { Icon(Icons.Default.Search, null) },
                                    label = { Text("Search") },
                                    colors = navColors,
                                )
                                NavigationBarItem(
                                    selected = tab == 2,
                                    onClick = { tab = 2; detail = null },
                                    icon = { Icon(Icons.Default.LibraryMusic, null) },
                                    label = { Text("Your Library") },
                                    colors = navColors,
                                )
                            }
                        }
                    },
                ) { pad ->
                    val m = Modifier.padding(pad)
                    val d = detail
                    if (d != null) {
                        BackHandler { detail = null }
                        DetailScreen(d, onBack = { detail = null }, modifier = m)
                    } else {
                        when (tab) {
                            0 -> HomeScreen(
                                disc,
                                onSearch = { tab = 1 },
                                onLibrary = { tab = 2 },
                                modifier = m,
                            )
                            1 -> SearchScreen(disc, m)
                            else -> LibraryScreen(m)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showPlayer,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    BackHandler { showPlayer = false }
                    NowPlaying(vm) { showPlayer = false }
                }

                addTarget?.let { t -> AddToPlaylistDialog(t) { addTarget = null } }
            }
        }
    }
}
