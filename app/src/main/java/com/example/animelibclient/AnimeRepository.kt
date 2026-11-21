
package com.example.animelibclient

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.ConcurrentHashMap

class AnimeRepository {

    data class Entry(
        val title: String,
        val url: String,
        val thumbnail: String = "",
        val description: String = "",
        val id: String = "",
        val statusId: Int = 0
    )

    data class Episode(
        val id: Int,
        val number: String,
        val name: String,
        val season: String
    )

    data class StreamSource(
        val team: String,
        val translation: String,
        val quality: String,
        val url: String,
        val embedded: Boolean = false,
        val fallbackUrls: List<String> = emptyList()
    )

    private val episodeCache = ConcurrentHashMap<String, List<Episode>>()

    fun search(query: String): List<Entry> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return request(
            "$API_BASE/anime?q=$encoded&site_id%5B%5D=5&status%5B%5D=1&status%5B%5D=2"
        )
    }

    fun ongoing(): List<Entry> {
        return request(
            "$API_BASE/anime?site_id%5B%5D=5&status%5B%5D=1&status%5B%5D=2&sort_by=created_at&sort_type=desc&page=1"
        )
    }

    fun getEpisodes(animeId: String, token: String = ""): List<Episode> {
        return episodeCache.computeIfAbsent(animeId) {
            loadEpisodes(animeId, token)
        }
    }

    private fun loadEpisodes(animeId: String, token: String): List<Episode> {
        val root = requestJson(
            "$API_BASE/episodes?anime_id=${URLEncoder.encode(animeId, "UTF-8")}",
            token
        )
        val data = root.optJSONArray("data")
            ?: throw IllegalStateException("В ответе API нет списка серий")

        return buildList {
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val id = obj.optInt("id", -1)
                if (id < 0) continue
                val number = obj.optString("number")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?: obj.optString("item_number")
                add(
                    Episode(
                        id = id,
                        number = number,
                        name = obj.optString("name").takeIf { it != "null" }.orEmpty(),
                        season = obj.optString("season").takeIf { it != "null" }.orEmpty()
                    )
                )
            }
        }
    }

    fun getEpisodeSources(episodeId: Int, token: String = ""): List<StreamSource> {
        val root = requestJson("$API_BASE/episodes/$episodeId", token)
        val data = root.optJSONObject("data")
            ?: throw IllegalStateException("В ответе API нет данных серии")
        val players = data.optJSONArray("players") ?: return emptyList()
        val sources = mutableListOf<StreamSource>()

        for (i in 0 until players.length()) {
            val player = players.optJSONObject(i) ?: continue
            val team = player.optJSONObject("team")?.optString("name")
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?: player.optString("player").takeIf { it.isNotBlank() && it != "null" }
                ?: "AnimeLib"
            val translation = player.optJSONObject("translation_type")?.optString("label")
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?: "Озвучка"
            val sourceCountBeforePlayer = sources.size
            val videoDomain = player.optString("video_domain")

            val qualities = player.optJSONObject("video")?.optJSONArray("quality")
            if (qualities != null) {
                for (qIndex in 0 until qualities.length()) {
                    val quality = qualities.optJSONObject(qIndex) ?: continue
                    val rawUrl = quality.optString("href")
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?: quality.optString("src")
                    val url = normalizeVideoUrl(rawUrl, videoDomain) ?: continue
                    val qualityLabel = quality.optInt("quality", 0)
                        .takeIf { it > 0 }
                        ?.let { "${it}p" }
                        ?: quality.optString("label").takeIf { it.isNotBlank() && it != "null" }
                        ?: "Авто"
                    sources += StreamSource(
                        team = team,
                        translation = translation,
                        quality = qualityLabel,
                        url = url,
                        fallbackUrls = alternateVideoUrls(rawUrl, url)
                    )
                }
            }

            // src может быть прямым файлом или страницей Kodik. Не отбрасываем embed-плееры:
            // на некоторых сериях AnimeLib отдаёт только их, без video.quality.
            if (sources.size == sourceCountBeforePlayer) {
                val src = normalizeVideoUrl(player.optString("src"), videoDomain)
                if (src != null) {
                    val direct = isDirectVideoUrl(src)
                    sources += StreamSource(
                        team = team,
                        translation = translation,
                        quality = if (direct) "Авто" else "Онлайн-плеер",
                        url = src,
                        embedded = !direct,
                        fallbackUrls = if (direct) alternateVideoUrls(player.optString("src"), src) else emptyList()
                    )
                }
            }
        }
        return sources.distinctBy { it.url }
    }

    fun getAccountName(token: String): String? {
        if (token.isBlank()) return null
        val root = requestJson("$API_BASE/auth/me", token)
        val data = root.optJSONObject("data") ?: root
        val candidates = listOfNotNull(
            data,
            data.optJSONObject("user"),
            data.optJSONObject("auth"),
            root.optJSONObject("user"),
            root.optJSONObject("auth")
        )
        val nameKeys = listOf("username", "name", "login", "nickname", "email")
        for (candidate in candidates) {
            for (key in nameKeys) {
                val value = candidate.optString(key)
                if (value.isNotBlank() && value != "null") return value
            }
        }
        return null
    }

    private fun normalizeVideoUrl(raw: String, videoDomain: String): String? {
        val value = raw.trim()
        if (value.isBlank() || value == "null") return null
        if (value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("//")
        ) {
            val absolute = if (value.startsWith("//")) "https:$value" else value
            val cdnPath = extractVideoCdnPath(absolute)
            if (cdnPath != null) {
                val base = videoBaseForDomain(videoDomain)
                return base.trimEnd('/') + if (cdnPath.startsWith('/')) cdnPath else "/$cdnPath"
            }
            return replaceVideoDomainAlias(absolute, videoDomain)
        }

        val base = videoBaseForDomain(videoDomain)
        return if (value.startsWith('/')) "$base$value" else "$base/$value"
    }

    private fun videoBaseForDomain(domain: String): String {
        return when (domain.trim().lowercase()) {
            "secondary_1" -> SECONDARY_VIDEO_BASE_1
            "secondary_2" -> SECONDARY_VIDEO_BASE_2
            "", "null", "main", "default" -> DEFAULT_VIDEO_BASE
            else -> when {
                domain.startsWith("https://", ignoreCase = true) || domain.startsWith("http://", ignoreCase = true) -> domain.trimEnd('/')
                domain.startsWith("//") -> "https:${domain.trimEnd('/')}"
                domain.contains('.') -> "https://${domain.trim('/')}"
                else -> DEFAULT_VIDEO_BASE
            }
        }
    }

    private fun replaceVideoDomainAlias(url: String, preferredDomain: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return url
        val authorityStart = schemeEnd + 3
        val suffixStart = url.indexOfAny(charArrayOf('/', '?', '#'), startIndex = authorityStart)
        val authorityEnd = if (suffixStart < 0) url.length else suffixStart
        val authority = url.substring(authorityStart, authorityEnd)
        val alias = authority.substringBefore(':').lowercase()
        if (alias !in setOf("main", "secondary_1", "secondary_2")) return url
        val base = videoBaseForDomain(preferredDomain)
        val suffix = if (suffixStart < 0) "" else url.substring(suffixStart)
        return base.trimEnd('/') + suffix
    }

    private fun alternateVideoUrls(rawUrl: String, primaryUrl: String): List<String> =
        listOf("secondary_1", "secondary_2")
            .mapNotNull { domain -> normalizeVideoUrl(rawUrl, domain) }
            .filter { it != primaryUrl && isDirectVideoUrl(it) }
            .distinct()

    private fun extractVideoCdnPath(url: String): String? {
        val normalized = url.lowercase()
        val prefixes = listOf(
            "https://video1.cdnlibs.org/.%d0%b0s",
            "https://video1.cdnlibs.org/.аs",
            "http://video1.cdnlibs.org/.%d0%b0s",
            "http://video1.cdnlibs.org/.аs",
            "https://video1.cdnlibs.org",
            "http://video1.cdnlibs.org",
            "https://video2.cdnlibs.org",
            "http://video2.cdnlibs.org",
            "https://video1.imglib.info",
            "http://video1.imglib.info"
        )
        val prefix = prefixes.firstOrNull { normalized.startsWith(it) } ?: return null
        var path = url.substring(prefix.length)
        listOf("/.%D0%B0s", "/.%d0%b0s", "/.аs").forEach { segment ->
            if (path.startsWith(segment, ignoreCase = true)) path = path.substring(segment.length)
        }
        return path.ifBlank { "/" }
    }

    private fun isDirectVideoUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return listOf(".m3u8", ".mp4", ".mpd", ".webm").any { extension -> path.contains(extension) }
    }

    private fun request(address: String): List<Entry> {
        val root = requestJson(address)
        val data = root.optJSONArray("data")
            ?: throw IllegalStateException("В ответе API нет массива data")
        return parse(data)
    }

    private companion object {
        const val API_BASE = "https://hapi.hentaicdn.org/api"
        const val DEFAULT_VIDEO_BASE = "https://video1.cdnlibs.org/.%D0%B0s"
        const val SECONDARY_VIDEO_BASE_1 = "https://video2.cdnlibs.org"
        const val SECONDARY_VIDEO_BASE_2 = "https://video1.imglib.info"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/134.0.0.0 Mobile Safari/537.36"
    }

    private fun requestJson(address: String, token: String = ""): JSONObject {
        val connection = URL(address).openConnection() as HttpsURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.useCaches = false
            connection.instanceFollowRedirects = true

            connection.setRequestProperty(
                "User-Agent",
                USER_AGENT
            )
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Origin", "https://animelib.org")
            if (token.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            }
            connection.setRequestProperty(
                "Referer",
                "https://animelib.org/"
            )
            connection.setRequestProperty("Site-Id", "5")
            connection.setRequestProperty("Client-Time-Zone", java.util.TimeZone.getDefault().id)

            connection.connect()

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream?.bufferedReader()?.use {
                it.readText()
            }.orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException(
                    "AnimeLib HTTP $code: ${body.take(300)}"
                )
            }

            return JSONTokener(body).nextValue() as? JSONObject
                ?: throw IllegalStateException("Ответ API не является JSON-объектом")

        } catch (e: SocketTimeoutException) {
            throw IllegalStateException(
                "Тайм-аут соединения с AnimeLib: ${e.message ?: "нет ответа"}"
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Ошибка AnimeLib: ${e.javaClass.simpleName}: ${e.message}"
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(data: JSONArray): List<Entry> {
        val result = mutableListOf<Entry>()

        for (i in 0 until data.length()) {
            val obj = data.optJSONObject(i) ?: continue

            val title = obj.optString("rus_name")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: obj.optString("name")
                    .takeIf { it.isNotBlank() && it != "null" }
                ?: continue

            val id = obj.optString("id")
            val slug = obj.optString("slug_url")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: obj.optString("slug")
                    .takeIf { it.isNotBlank() && it != "null" }
                ?: id

            val cover = obj.optJSONObject("cover")
            val rawImage = cover?.optString("default")
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?: cover?.optString("md")?.takeIf { it.isNotBlank() && it != "null" }
                ?: cover?.optString("thumbnail")?.takeIf { it.isNotBlank() && it != "null" }
                ?: ""
            val image = when {
                rawImage.startsWith("https://") || rawImage.startsWith("http://") -> rawImage
                rawImage.startsWith("//") -> "https:$rawImage"
                rawImage.startsWith("/") -> "https://cover.hentaicdn.org$rawImage"
                else -> ""
            }

            result.add(
                Entry(
                    title = title,
                    url = slug,
                    thumbnail = image,
                    description = obj.optString("description"),
                    id = id,
                    statusId = obj.optJSONObject("status")?.optInt("id", 0) ?: 0
                )
            )
        }

        return result
    }
}
