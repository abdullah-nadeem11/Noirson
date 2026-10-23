package com.pockettunes.app

import androidx.annotation.DrawableRes

/** A themed collection of songs (a mood mix or a genre) built from music searches. */
data class MixDef(
    val key: String,
    val title: String,
    val subtitle: String,
    @DrawableRes val art: Int,
    val queries: List<String>,
)

object Mixes {
    val forYou = listOf(
        MixDef(
            "late_nights", "Late Nights", "Playlist · Noirson", R.drawable.mix_late_nights,
            listOf("late night chill songs", "night drive songs", "slow late night vibes"),
        ),
        MixDef(
            "deep_focus", "Deep Focus", "Playlist · Noirson", R.drawable.mix_deep_focus,
            listOf("deep focus instrumental", "ambient focus music", "study focus songs"),
        ),
        MixDef(
            "chill_vibes", "Chill Vibes", "Playlist · Noirson", R.drawable.mix_chill_vibes,
            listOf("chill vibes songs", "relaxing chill songs", "lofi chill songs"),
        ),
    )

    val genres = listOf(
        MixDef("pop", "Pop", "Genre · Noirson", R.drawable.genre_pop,
            listOf("best pop songs", "new pop hits", "pop hits")),
        MixDef("hiphop", "Hip-Hop", "Genre · Noirson", R.drawable.genre_hiphop,
            listOf("best hip hop songs", "new rap hits", "hip hop hits")),
        MixDef("rock", "Rock", "Genre · Noirson", R.drawable.genre_rock,
            listOf("best rock songs", "classic rock hits", "new rock songs")),
        MixDef("electronic", "Electronic", "Genre · Noirson", R.drawable.genre_electronic,
            listOf("best electronic songs", "electronic dance music hits", "synthwave songs")),
        MixDef("rnb", "R&B", "Genre · Noirson", R.drawable.genre_rnb,
            listOf("best r&b songs", "new r&b hits", "r&b soul songs")),
        MixDef("indie", "Indie", "Genre · Noirson", R.drawable.genre_indie,
            listOf("best indie songs", "indie pop songs", "indie rock songs")),
    )

    fun find(key: String): MixDef? = (forYou + genres).firstOrNull { it.key == key }
}
