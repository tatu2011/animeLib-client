package com.example.animelibclient

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal object AnimeFavoritesStore {
    private const val PREFS_NAME = "anime_lib_favorites"
    private const val FAVORITES_KEY = "entries"

    fun load(context: Context): List<AnimeRepository.Entry> = runCatching {
        val data = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(FAVORITES_KEY, "[]")
            ?: "[]"
        val array = JSONArray(data)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val title = item.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: continue
                add(
                    AnimeRepository.Entry(
                        title = title,
                        url = item.optString("url"),
                        thumbnail = item.optString("thumbnail"),
                        description = item.optString("description"),
                        id = item.optString("id")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, entries: List<AnimeRepository.Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("title", entry.title)
                    .put("url", entry.url)
                    .put("thumbnail", entry.thumbnail)
                    .put("description", entry.description)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(FAVORITES_KEY, array.toString())
            .apply()
    }
}
