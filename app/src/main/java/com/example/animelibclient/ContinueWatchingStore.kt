package com.example.animelibclient

import android.content.Context
import org.json.JSONObject

internal data class ContinueWatching(
    val anime: AnimeRepository.Entry,
    val episode: AnimeRepository.Episode,
    val team: String,
    val translation: String,
    val quality: String,
    val positionMs: Long,
    val durationMs: Long
)

internal object ContinueWatchingStore {
    private const val PREFS_NAME = "anime_lib_watch_progress"
    private const val ENTRY_KEY = "continue_watching"

    fun load(context: Context): ContinueWatching? = runCatching {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ENTRY_KEY, null)
            ?: return null
        val root = JSONObject(json)
        val animeJson = root.optJSONObject("anime") ?: return null
        val episodeJson = root.optJSONObject("episode") ?: return null
        val title = animeJson.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: return null
        val anime = AnimeRepository.Entry(
            id = animeJson.optString("id"),
            title = title,
            url = animeJson.optString("url"),
            thumbnail = animeJson.optString("thumbnail"),
            description = animeJson.optString("description")
        )
        val episodeId = episodeJson.optInt("id", -1)
        if (episodeId < 0) return null
        ContinueWatching(
            anime = anime,
            episode = AnimeRepository.Episode(
                id = episodeId,
                number = episodeJson.optString("number"),
                name = episodeJson.optString("name"),
                season = episodeJson.optString("season")
            ),
            team = root.optString("team"),
            translation = root.optString("translation"),
            quality = root.optString("quality"),
            positionMs = root.optLong("positionMs", 0L).coerceAtLeast(0L),
            durationMs = root.optLong("durationMs", 0L).coerceAtLeast(0L)
        )
    }.getOrNull()

    fun save(context: Context, item: ContinueWatching?) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (item == null) {
            preferences.edit().remove(ENTRY_KEY).apply()
            return
        }
        val anime = JSONObject()
            .put("id", item.anime.id)
            .put("title", item.anime.title)
            .put("url", item.anime.url)
            .put("thumbnail", item.anime.thumbnail)
            .put("description", item.anime.description)
        val episode = JSONObject()
            .put("id", item.episode.id)
            .put("number", item.episode.number)
            .put("name", item.episode.name)
            .put("season", item.episode.season)
        val json = JSONObject()
            .put("anime", anime)
            .put("episode", episode)
            .put("team", item.team)
            .put("translation", item.translation)
            .put("quality", item.quality)
            .put("positionMs", item.positionMs)
            .put("durationMs", item.durationMs)
        preferences.edit().putString(ENTRY_KEY, json.toString()).apply()
    }
}
