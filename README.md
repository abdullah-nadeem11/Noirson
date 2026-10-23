# Noirson

An Android music player inspired by the desktop apps Sonora and LiMusic. It is an original
Kotlin / Jetpack Compose app, not a port of either one's code.

## Features
- Search and stream songs from YouTube Music (via NewPipeExtractor, no account needed)
- Play music files stored on the phone
- Background playback with notification and lock-screen controls (Media3)
- Queue, shuffle, repeat, seek, previous / next
- Synced lyrics from LRCLIB (tap a line to jump to it)
- Favourites and your own playlists, saved on the phone
- Recently played and recent searches
- Queue screen (jump, reorder, remove, play next, add to queue)
- Discover home with music shelves, plus search for songs, albums, artists and playlists
- Song radio: play a song from search or Home and Noirson keeps going with YouTube's radio for it
- Autoplay: keeps similar songs coming when the queue runs out
- Home, Search (genre categories, voice search) and Your Library (playlists, artists, on-phone files)

## Build the APK

### Option A: GitHub (no Android Studio needed)
1. Create a new repository on github.com and upload everything in this folder
   (including the hidden `.github` folder).
2. Open the repo's **Actions** tab, pick **Build APK**, and wait for it to finish (about 5 minutes).
   You can also trigger it with **Run workflow**.
3. Open the finished run and download the **Noirson-debug-apk** artifact (a zip containing the APK).
4. Copy the APK to your phone, open it, and allow "install unknown apps" when asked.

### Option B: Android Studio
Open this folder in Android Studio (Ladybug or newer), let it sync, then
**Build > Build APK(s)**, or run `./gradlew assembleDebug`.
The APK is at `app/build/outputs/apk/debug/Noirson-debug.apk`.

## Notes
- If YouTube search or playback stops working, bump `NewPipeExtractor` in `app/build.gradle.kts`
  to the newest tag (YouTube changes often and the library is updated to match).
- Streaming YouTube this way is outside YouTube's terms of service. Use it for personal use.
