package com.example.animelibclient

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.util.Rational
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var authResultListener: ((String?, String?) -> Unit)? = null
    private var waitingForBrowserAuth = false
    internal val inPictureInPicture = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AnimeApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverAuthResult(intent)
    }

    override fun onResume() {
        super.onResume()
        if (waitingForBrowserAuth) {
            waitingForBrowserAuth = false
            authResultListener?.invoke(
                null,
                "Вход закончился в браузере, но Android не передал ссылку приложению. Включи animelib.org через кнопку настройки ниже и повтори вход."
            )
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture.value = isInPictureInPictureMode
    }

    internal fun enterPlayerPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } else {
            Toast.makeText(this, "Картинка в картинке доступна начиная с Android 8", Toast.LENGTH_SHORT).show()
        }
    }

    internal fun beginBrowserAuth() {
        waitingForBrowserAuth = true
    }

    internal fun setAuthResultListener(listener: ((String?, String?) -> Unit)?) {
        authResultListener = listener
        if (listener != null) deliverAuthResult(intent)
    }

    private fun deliverAuthResult(resultIntent: Intent?) {
        val token = resultIntent?.getStringExtra(EXTRA_AUTH_TOKEN)
        val error = resultIntent?.getStringExtra(EXTRA_AUTH_ERROR)
        if (!token.isNullOrBlank() || !error.isNullOrBlank()) {
            waitingForBrowserAuth = false
            authResultListener?.let { listener ->
                listener(token, error)
                resultIntent?.removeExtra(EXTRA_AUTH_TOKEN)
                resultIntent?.removeExtra(EXTRA_AUTH_ERROR)
            }
        }
    }

    companion object {
        const val EXTRA_AUTH_TOKEN = "com.example.animelibclient.extra.AUTH_TOKEN"
        const val EXTRA_AUTH_ERROR = "com.example.animelibclient.extra.AUTH_ERROR"
    }
}

private fun sameAnime(first: AnimeRepository.Entry, second: AnimeRepository.Entry): Boolean {
    return if (first.id.isNotBlank() && second.id.isNotBlank()) first.id == second.id else first.url == second.url
}

private suspend fun loadPlayableEntries(
    repository: AnimeRepository,
    candidates: List<AnimeRepository.Entry>,
    token: String,
    limit: Int = 24,
    batchSize: Int = 8
): List<AnimeRepository.Entry> = coroutineScope {
    val playable = mutableListOf<AnimeRepository.Entry>()
    var atLeastOneCheckSucceeded = false

    for (batch in candidates.chunked(batchSize)) {
        val checks = batch.map { anime ->
            async(Dispatchers.IO) {
                runCatching { anime to repository.getEpisodes(anime.id, token).isNotEmpty() }
            }
        }.awaitAll()

        checks.forEach { result ->
            result.onSuccess { (anime, hasEpisodes) ->
                atLeastOneCheckSucceeded = true
                if (hasEpisodes) playable += anime
            }
        }
        if (playable.size >= limit) break
    }

    if (playable.isEmpty() && !atLeastOneCheckSucceeded && candidates.isNotEmpty()) {
        throw IllegalStateException("Не удалось проверить наличие серий. Попробуй ещё раз.")
    }
    playable.take(limit)
}

@Composable
private fun AnimeApp() {
    val context = LocalContext.current
    val colorScheme = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            darkColorScheme(
                primary = Color(0xFFD0BCFF),
                onPrimary = Color(0xFF381E72),
                primaryContainer = Color(0xFF4F378B),
                onPrimaryContainer = Color(0xFFEADDFF),
                secondary = Color(0xFFCCC2DC),
                secondaryContainer = Color(0xFF4A4458),
                onSecondaryContainer = Color(0xFFE8DEF8),
                background = Color(0xFF100F13),
                onBackground = Color(0xFFE6E0E9),
                surface = Color(0xFF100F13),
                onSurface = Color(0xFFE6E0E9),
                surfaceVariant = Color(0xFF25232A),
                onSurfaceVariant = Color(0xFFCAC4D0),
                outline = Color(0xFF938F99)
            )
        }
    }
    MaterialTheme(colorScheme = colorScheme) {
        AnimeAppContent()
    }
}

@Composable
private fun AnimeAppContent() {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("Главная") }
    var query by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<AnimeRepository.Entry>>(emptyList()) }
    var selectedAnime by remember { mutableStateOf<AnimeRepository.Entry?>(null) }
    var episodes by remember { mutableStateOf<List<AnimeRepository.Episode>>(emptyList()) }
    var selectedEpisode by remember { mutableStateOf<AnimeRepository.Episode?>(null) }
    var sources by remember { mutableStateOf<List<AnimeRepository.StreamSource>>(emptyList()) }
    var activeSource by remember { mutableStateOf<AnimeRepository.StreamSource?>(null) }
    var favorites by remember { mutableStateOf(AnimeFavoritesStore.load(context)) }
    var continueWatching by remember { mutableStateOf(ContinueWatchingStore.load(context)) }
    var pendingResume by remember { mutableStateOf<ContinueWatching?>(null) }
    var playerStartPositionMs by remember { mutableLongStateOf(0L) }
    var apiToken by remember {
        mutableStateOf(
            (context as? MainActivity)?.intent?.getStringExtra(MainActivity.EXTRA_AUTH_TOKEN)
                ?.takeIf { it.isNotBlank() }
                ?: SessionTokenStore.load(context).orEmpty()
        )
    }
    var accountName by remember { mutableStateOf("") }
    var accountNameLoading by remember { mutableStateOf(false) }
    var authLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var episodeLoading by remember { mutableStateOf(false) }
    var sourceLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var episodeError by remember { mutableStateOf<String?>(null) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var lastRootBackAt by remember { mutableStateOf(0L) }

    val repository = remember { AnimeRepository() }
    val scope = rememberCoroutineScope()
    val toggleFavorite: (AnimeRepository.Entry) -> Unit = { anime ->
        favorites = if (favorites.any { sameAnime(it, anime) }) {
            favorites.filterNot { sameAnime(it, anime) }
        } else {
            listOf(anime) + favorites
        }
        AnimeFavoritesStore.save(context, favorites)
    }
    DisposableEffect(context) {
        val activity = context as? MainActivity
        activity?.setAuthResultListener { token, message ->
            authLoading = false
            if (!token.isNullOrBlank()) {
                apiToken = token.trim()
                authError = if (SessionTokenStore.save(context, apiToken)) {
                    null
                } else {
                    "Вход выполнен, но не удалось сохранить сессию на устройстве."
                }
            } else if (!message.isNullOrBlank()) {
                authError = message
            }
        }
        onDispose { activity?.setAuthResultListener(null) }
    }

    BackHandler {
        when {
            activeSource != null -> activeSource = null
            selectedAnime != null && selectedEpisode != null -> {
                selectedEpisode = null
                sources = emptyList()
                sourceError = null
            }
            selectedAnime != null -> {
                selectedAnime = null
                selectedEpisode = null
                sources = emptyList()
                sourceError = null
            }
            selectedTab != "Главная" -> selectedTab = "Главная"
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastRootBackAt < 2_000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastRootBackAt = now
                    Toast.makeText(context, "Нажми ещё раз, чтобы выйти", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == "Главная" || selectedTab == "Каталог") {
            loading = true
            error = null
            try {
                val candidates = withContext(Dispatchers.IO) { repository.ongoing() }
                entries = loadPlayableEntries(repository, candidates, apiToken)
            } catch (e: Exception) {
                error = e.message ?: "Не удалось загрузить аниме"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(selectedAnime?.id, apiToken) {
        val anime = selectedAnime ?: return@LaunchedEffect
        val resume = pendingResume?.takeIf { sameAnime(it.anime, anime) }
        episodeLoading = true
        episodeError = null
        episodes = emptyList()
        selectedEpisode = null
        sources = emptyList()
        try {
            episodes = withContext(Dispatchers.IO) {
                repository.getEpisodes(anime.id, apiToken)
            }
            if (episodes.isEmpty()) episodeError = "У этого тайтла пока нет доступных серий"
            if (resume != null) {
                selectedEpisode = resume.episode
                sourceLoading = true
                sourceError = null
                try {
                    sources = withContext(Dispatchers.IO) {
                        repository.getEpisodeSources(resume.episode.id, apiToken)
                    }
                    val source = sources.firstOrNull {
                        it.team == resume.team && it.translation == resume.translation && it.quality == resume.quality
                    } ?: sources.firstOrNull { it.team == resume.team && it.translation == resume.translation }
                        ?: sources.firstOrNull { it.translation == resume.translation }
                        ?: sources.firstOrNull()
                    if (source != null) {
                        playerStartPositionMs = resume.positionMs
                        activeSource = source
                    } else {
                        sourceError = "Не нашёл прошлую озвучку. Выбери её в списке плееров."
                    }
                } catch (e: Exception) {
                    sourceError = e.message ?: "Не удалось восстановить плеер"
                } finally {
                    sourceLoading = false
                    pendingResume = null
                }
            }
        } catch (e: Exception) {
            episodeError = e.message ?: "Не удалось загрузить серии"
        } finally {
            episodeLoading = false
        }
    }

    LaunchedEffect(apiToken) {
        if (apiToken.isBlank()) {
            accountName = ""
            accountNameLoading = false
        } else {
            accountNameLoading = true
            try {
                accountName = withContext(Dispatchers.IO) {
                    repository.getAccountName(apiToken).orEmpty()
                }
            } catch (_: Exception) {
                accountName = ""
            } finally {
                accountNameLoading = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (selectedAnime == null && activeSource == null) {
                FloatingBottomBar(
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    ) { padding ->
        when {
            activeSource != null -> NativePlayerScreen(
                source = activeSource!!,
                startPositionMs = playerStartPositionMs,
                onBack = { activeSource = null },
                onPictureInPicture = { (context as? MainActivity)?.enterPlayerPictureInPicture() },
                onProgress = { source, positionMs, durationMs ->
                    val anime = selectedAnime
                    val episode = selectedEpisode
                    if (anime != null && episode != null) {
                        val completed = durationMs > 0L && positionMs >= durationMs - 15_000L
                        if (completed) {
                            continueWatching = null
                        } else {
                            continueWatching = ContinueWatching(
                                anime = anime,
                                episode = episode,
                                team = source.team,
                                translation = source.translation,
                                quality = source.quality,
                                positionMs = positionMs.coerceAtLeast(0L),
                                durationMs = durationMs.coerceAtLeast(0L)
                            )
                        }
                        ContinueWatchingStore.save(context, continueWatching)
                    }
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )

            selectedAnime != null -> AnimeDetailsScreen(
                anime = selectedAnime!!,
                episodes = episodes,
                selectedEpisode = selectedEpisode,
                sources = sources,
                episodesLoading = episodeLoading,
                sourcesLoading = sourceLoading,
                episodeError = episodeError,
                sourceError = sourceError,
                isFavorite = favorites.any { sameAnime(it, selectedAnime!!) },
                onToggleFavorite = { toggleFavorite(selectedAnime!!) },
                onBack = {
                    selectedAnime = null
                    selectedEpisode = null
                    sources = emptyList()
                },
                onEpisodeClick = { episode ->
                    selectedEpisode = episode
                    sources = emptyList()
                    sourceError = null
                    sourceLoading = true
                    scope.launch {
                        try {
                            sources = withContext(Dispatchers.IO) {
                                repository.getEpisodeSources(episode.id, apiToken)
                            }
                            if (sources.isEmpty()) {
                                sourceError = if (apiToken.isBlank()) {
                                    "AnimeLib не отдал прямой поток без входа. Войди в аккаунт во вкладке «Профиль»."
                                } else {
                                    "Не удалось получить плееры для этой серии. Попробуй другую озвучку или серию."
                                }
                            }
                        } catch (e: Exception) {
                            sourceError = e.message ?: "Не удалось загрузить плееры"
                        } finally {
                            sourceLoading = false
                        }
                    }
                },
                onSourceClick = {
                    playerStartPositionMs = 0L
                    activeSource = it
                    continueWatching = ContinueWatching(
                        anime = selectedAnime!!,
                        episode = selectedEpisode!!,
                        team = it.team,
                        translation = it.translation,
                        quality = it.quality,
                        positionMs = 0L,
                        durationMs = 0L
                    )
                    ContinueWatchingStore.save(context, continueWatching)
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )

            selectedTab == "Профиль" -> ProfileScreen(
                loggedIn = apiToken.isNotBlank(),
                accountName = accountName,
                accountNameLoading = accountNameLoading,
                loading = authLoading,
                error = authError,
                onLogin = {
                    authError = null
                    authLoading = true
                    try {
                        val browserIntent = AnimeLibOAuth.createBrowserIntent(context)
                        context.startActivity(browserIntent)
                        (context as? MainActivity)?.beginBrowserAuth()
                    } catch (_: ActivityNotFoundException) {
                        authLoading = false
                        authError = "На устройстве не найден браузер для входа в AnimeLib."
                    } catch (e: Exception) {
                        authLoading = false
                        authError = e.message ?: "Не удалось открыть страницу входа AnimeLib."
                    }
                },
                onLogout = {
                    SessionTokenStore.clear(context)
                    apiToken = ""
                    accountName = ""
                    authError = null
                },
                onOpenLinkSettings = {
                    val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS
                    } else {
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    }
                    runCatching {
                        context.startActivity(Intent(action, Uri.parse("package:${context.packageName}")))
                    }
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )

            selectedTab == "Моё" -> FavoritesScreen(
                favorites = favorites,
                onAnimeClick = { selectedAnime = it },
                onToggleFavorite = toggleFavorite,
                modifier = Modifier.fillMaxSize().padding(padding)
            )

            else -> AnimeListScreen(
                title = if (selectedTab == "Каталог") "Каталог и поиск" else "Новинки",
                showSearch = selectedTab == "Каталог",
                favorites = favorites,
                onToggleFavorite = toggleFavorite,
                continueWatching = if (selectedTab == "Главная") continueWatching else null,
                onContinueWatching = { item ->
                    pendingResume = item
                    selectedAnime = item.anime
                },
                query = query,
                onQueryChange = { query = it },
                onSearch = {
                    if (query.isNotBlank()) {
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                val matches = withContext(Dispatchers.IO) { repository.search(query.trim()) }
                                entries = loadPlayableEntries(repository, matches, apiToken)
                            } catch (e: Exception) {
                                error = e.message ?: "Поиск не сработал"
                            } finally {
                                loading = false
                            }
                        }
                    }
                },
                entries = entries,
                loading = loading,
                error = error,
                onAnimeClick = { selectedAnime = it },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}

@Composable
private fun AnimeListScreen(
    title: String,
    showSearch: Boolean,
    favorites: List<AnimeRepository.Entry>,
    onToggleFavorite: (AnimeRepository.Entry) -> Unit,
    continueWatching: ContinueWatching?,
    onContinueWatching: (ContinueWatching) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    entries: List<AnimeRepository.Entry>,
    loading: Boolean,
    error: String?,
    onAnimeClick: (AnimeRepository.Entry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        continueWatching?.let { item ->
            Spacer(Modifier.height(10.dp))
            ContinueWatchingCard(item, onClick = { onContinueWatching(item) })
        }
        if (showSearch) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Название аниме") }
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                Text("Найти")
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading -> LoadingIndicator()
            error != null -> ErrorMessage(error)
            entries.isEmpty() -> EmptyMessage("Аниме не найдено")
            else -> AnimeResults(
                entries = entries,
                favorites = favorites,
                onAnimeClick = onAnimeClick,
                onToggleFavorite = onToggleFavorite
            )
        }
    }
}

@Composable
private fun FavoritesScreen(
    favorites: List<AnimeRepository.Entry>,
    onAnimeClick: (AnimeRepository.Entry) -> Unit,
    onToggleFavorite: (AnimeRepository.Entry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Избранное", color = MaterialTheme.colorScheme.onBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (favorites.isEmpty()) {
            EmptyMessage("Нажми на сердечко у аниме, чтобы добавить его сюда")
        } else {
            AnimeResults(
                entries = favorites,
                favorites = favorites,
                onAnimeClick = onAnimeClick,
                onToggleFavorite = onToggleFavorite
            )
        }
    }
}

@Composable
private fun ContinueWatchingCard(item: ContinueWatching, onClick: () -> Unit) {
    val progress = if (item.durationMs > 0L) {
        (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimeCover(
            imageUrl = item.anime.thumbnail,
            description = item.anime.title,
            modifier = Modifier.size(width = 72.dp, height = 96.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Продолжить просмотр", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(item.anime.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Серия ${item.episode.number} · ${item.translation}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.team} · ${item.quality}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("${formatPlayerTime(item.positionMs)} / ${formatPlayerTime(item.durationMs)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FavoriteButton(selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (selected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (selected) "Убрать из избранного" else "Добавить в избранное",
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileAvatar(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.profile_avatar),
        contentDescription = "Аватар профиля",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}

@Composable
private fun AnimeResults(
    entries: List<AnimeRepository.Entry>,
    favorites: List<AnimeRepository.Entry>,
    onAnimeClick: (AnimeRepository.Entry) -> Unit,
    onToggleFavorite: (AnimeRepository.Entry) -> Unit
) {
    val duplicateCovers = remember(entries) {
        entries.groupBy { it.thumbnail.trim() }
            .filter { (url, items) -> url.isNotBlank() && items.size > 1 }
            .keys
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(entries, key = { it.id }) { anime ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onAnimeClick(anime) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimeCover(
                    imageUrl = anime.thumbnail,
                    description = anime.title,
                    forcePlaceholder = anime.thumbnail in duplicateCovers,
                    modifier = Modifier.size(width = 88.dp, height = 124.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        anime.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FavoriteButton(
                    selected = favorites.any { sameAnime(it, anime) },
                    onClick = { onToggleFavorite(anime) }
                )
            }
        }
    }
}

@Composable
private fun AnimeDetailsScreen(
    anime: AnimeRepository.Entry,
    episodes: List<AnimeRepository.Episode>,
    selectedEpisode: AnimeRepository.Episode?,
    sources: List<AnimeRepository.StreamSource>,
    episodesLoading: Boolean,
    sourcesLoading: Boolean,
    episodeError: String?,
    sourceError: String?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onEpisodeClick: (AnimeRepository.Episode) -> Unit,
    onSourceClick: (AnimeRepository.StreamSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Назад", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).clickable(onClick = onBack).padding(vertical = 8.dp))
            FavoriteButton(selected = isFavorite, onClick = onToggleFavorite)
        }
        Row(verticalAlignment = Alignment.Top) {
            AnimeCover(
                imageUrl = anime.thumbnail,
                description = anime.title,
                modifier = Modifier.size(width = 112.dp, height = 158.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(anime.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(anime.description.takeIf { it != "null" }.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Серии", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        when {
            episodesLoading -> LoadingIndicator()
            episodeError != null -> ErrorMessage(episodeError)
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(episodes, key = { it.id }) { episode ->
                    val selected = selectedEpisode?.id == episode.id
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onEpisodeClick(episode) }
                            .padding(13.dp)
                    ) {
                        Text("Серия ${episode.number}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        if (episode.name.isNotBlank() && episode.name != episode.number) {
                            Text(episode.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (selectedEpisode != null) {
            Spacer(Modifier.height(10.dp))
            Text("Плееры · серия ${selectedEpisode.number}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            when {
                sourcesLoading -> LoadingIndicator()
                sourceError != null -> ErrorMessage(sourceError)
                sources.isEmpty() -> EmptyMessage("Выбери озвучку")
                else -> LazyColumn(
                    modifier = Modifier.weight(0.7f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sources, key = { "${it.team}-${it.quality}-${it.url}" }) { source ->
                        Text(
                            "${source.team} · ${source.translation} · ${source.quality}",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onSourceClick(source) }
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverPlaceholder() {
    Icon(
        imageVector = Icons.Outlined.Image,
        contentDescription = "Обложка недоступна",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(28.dp)
    )
}

@Composable
private fun AnimeCover(
    imageUrl: String,
    description: String,
    modifier: Modifier = Modifier,
    forcePlaceholder: Boolean = false
) {
    val context = LocalContext.current
    var failed by remember(imageUrl) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (imageUrl.isBlank() || forcePlaceholder || failed) {
            CoverPlaceholder()
        } else {
            AsyncImage(
                model = remember(imageUrl) {
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .addHeader("Referer", "https://v5.animelib.org/")
                        .crossfade(true)
                        .build()
                },
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true }
            )
        }
    }
}

@Composable
private fun NativePlayerScreen(
    source: AnimeRepository.StreamSource,
    startPositionMs: Long,
    onBack: () -> Unit,
    onPictureInPicture: () -> Unit,
    onProgress: (AnimeRepository.StreamSource, Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestOnProgress by rememberUpdatedState(onProgress)
    val inPip = (context as? MainActivity)?.inPictureInPicture?.value ?: false
    var fullscreen by remember(source.url) { mutableStateOf(false) }
    var embeddedView by remember(source.url) { mutableStateOf<WebView?>(null) }
    var playbackError by remember(source.url) { mutableStateOf<String?>(null) }
    var buffering by remember(source.url) { mutableStateOf(true) }
    var fallbackIndex by remember(source.url) { mutableStateOf(0) }
    val player = remember(source.url, source.embedded) {
        if (source.embedded) {
            null
        } else {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/134.0.0.0 Mobile Safari/537.36")
                .setDefaultRequestProperties(
                    mapOf(
                        "Accept" to "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Referer" to "https://animelib.org/",
                        "Origin" to "https://animelib.org",
                        "Sec-Fetch-Dest" to "video",
                        "Sec-Fetch-Mode" to "no-cors",
                        "Sec-Fetch-Site" to "cross-site"
                    )
                )
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
                .build()
                .apply {
                    setMediaItem(createStreamMediaItem(source.url), startPositionMs.coerceAtLeast(0L))
                    prepare()
                    playWhenReady = true
                }
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) playbackError = null
                if (playbackState == Player.STATE_ENDED) {
                    val completedPlayer = player ?: return
                    latestOnProgress(
                        source,
                        completedPlayer.currentPosition.coerceAtLeast(0L),
                        completedPlayer.duration.takeIf { it > 0L } ?: 0L
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                buffering = false
                val detail = error.cause?.message ?: error.message ?: error.errorCodeName
                val fallbackUrl = source.fallbackUrls.getOrNull(fallbackIndex)
                if (fallbackUrl != null) {
                    fallbackIndex++
                    playbackError = null
                    buffering = true
                    player?.setMediaItem(
                        createStreamMediaItem(fallbackUrl),
                        player?.currentPosition?.coerceAtLeast(0L) ?: startPositionMs.coerceAtLeast(0L)
                    )
                    player?.prepare()
                    player?.play()
                } else {
                    playbackError = "Не удалось подключиться к потоку (${error.errorCodeName}). $detail"
                }
            }

        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.let { activePlayer ->
                latestOnProgress(
                    source,
                    activePlayer.currentPosition.coerceAtLeast(0L),
                    activePlayer.duration.takeIf { it > 0L } ?: 0L
                )
            }
            player?.release()
        }
    }
    LaunchedEffect(player) {
        val activePlayer = player ?: return@LaunchedEffect
        var lastSavedBucket = startPositionMs.coerceAtLeast(0L) / 10_000L
        while (true) {
            val currentPosition = activePlayer.currentPosition.coerceAtLeast(0L)
            val currentDuration = activePlayer.duration.takeIf { it > 0L } ?: 0L
            val currentBucket = currentPosition / 10_000L
            if (currentBucket != lastSavedBucket) {
                lastSavedBucket = currentBucket
                latestOnProgress(source, currentPosition, currentDuration)
            }
            delay(1_000)
        }
    }
    DisposableEffect(source.url) {
        onDispose {
            setPlayerFullscreen(context as? Activity, false)
            embeddedView?.let {
                it.stopLoading()
                it.destroy()
            }
        }
    }
    DisposableEffect(fullscreen) {
        setPlayerFullscreen(context as? Activity, fullscreen)
        onDispose { }
    }
    BackHandler(enabled = fullscreen && !inPip) {
        fullscreen = false
    }

    Box(modifier.background(Color.Black)) {
        if (source.embedded) {
            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/134.0.0.0 Mobile Safari/537.36"
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl(source.url, mapOf("Referer" to "https://animelib.org/"))
                    }.also { embeddedView = it }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = false
                        keepScreenOn = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!inPip) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(
                            source.team,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("${source.translation} · ${source.quality}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    IconButton(onClick = onPictureInPicture) {
                        Icon(Icons.Outlined.PictureInPictureAlt, contentDescription = "Картинка в картинке", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { fullscreen = !fullscreen }) {
                        Icon(
                            if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                            contentDescription = if (fullscreen) "Выйти из полного экрана" else "Полный экран",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (!source.embedded && playbackError == null && buffering) {
                    MaterialPanel(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            Text("Подключаюсь к потоку…", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (!source.embedded) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerControl(Icons.Outlined.Replay10, "Назад на 10 секунд") { player?.seekBack() }
                        Spacer(Modifier.size(22.dp))
                        IconButton(
                            onClick = {
                                player?.let { current ->
                                    if (current.playWhenReady) current.pause() else current.play()
                                }
                            },
                            modifier = Modifier.size(66.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            val isPlaying = player?.playWhenReady == true
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(Modifier.size(22.dp))
                        PlayerControl(Icons.Outlined.Forward10, "Вперёд на 10 секунд") { player?.seekForward() }
                    }

                    PlayerTimeline(
                        player = player,
                        showReplay = player?.playbackState == Player.STATE_ENDED,
                        onReplay = {
                            player?.seekTo(0L)
                            player?.play()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    playbackError?.let { message ->
                        Spacer(Modifier.height(10.dp))
                        MaterialPanel(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Видео не запустилось", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(message, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Повторить",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        playbackError = null
                                        buffering = true
                                        fallbackIndex = 0
                                        player?.setMediaItem(
                                            createStreamMediaItem(source.url),
                                            player?.currentPosition?.coerceAtLeast(0L) ?: startPositionMs.coerceAtLeast(0L)
                                        )
                                        player?.prepare()
                                        player?.play()
                                    }.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        content()
    }
}

@Composable
private fun PlayerTimeline(
    player: ExoPlayer?,
    showReplay: Boolean,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    var positionMs by remember(player) {
        mutableLongStateOf(player?.currentPosition?.coerceAtLeast(0L) ?: 0L)
    }
    var durationMs by remember(player) {
        mutableLongStateOf(player?.duration?.takeIf { it > 0L } ?: 0L)
    }
    var dragFraction by remember(player) { mutableStateOf<Float?>(null) }

    LaunchedEffect(player) {
        val activePlayer = player ?: return@LaunchedEffect
        while (true) {
            val currentPosition = activePlayer.currentPosition.coerceAtLeast(0L)
            val currentDuration = activePlayer.duration.takeIf { it > 0L } ?: 0L
            if (positionMs != currentPosition) positionMs = currentPosition
            if (durationMs != currentDuration) durationMs = currentDuration
            delay(250)
        }
    }

    val shownPositionMs = if (durationMs > 0L && dragFraction != null) {
        (durationMs * dragFraction!!).toLong()
    } else {
        positionMs
    }

    MaterialPanel(modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Slider(
                value = dragFraction ?: if (durationMs > 0L) {
                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else 0f,
                onValueChange = { dragFraction = it },
                onValueChangeFinished = {
                    dragFraction?.let { fraction ->
                        if (durationMs > 0L) player?.seekTo((durationMs * fraction).toLong())
                    }
                    dragFraction = null
                },
                enabled = durationMs > 0L
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${formatPlayerTime(shownPositionMs)} / ${formatPlayerTime(durationMs)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp
                )
                if (showReplay) {
                    Text("Повторить", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onReplay).padding(4.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerControl(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(31.dp))
    }
}

private fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun createStreamMediaItem(url: String): MediaItem {
    val builder = MediaItem.Builder().setUri(url)
    if (url.substringBefore('#').contains(".m3u8", ignoreCase = true)) {
        builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    }
    return builder.build()
}

private fun setPlayerFullscreen(activity: Activity?, enabled: Boolean) {
    activity ?: return
    activity.requestedOrientation = if (enabled) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    if (enabled) {
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun ProfileScreen(
    loggedIn: Boolean,
    accountName: String,
    accountNameLoading: Boolean,
    loading: Boolean,
    error: String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenLinkSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Профиль", color = MaterialTheme.colorScheme.onBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            ProfileAvatar(Modifier.size(42.dp))
        }
        Spacer(Modifier.height(16.dp))
        MaterialPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                if (loggedIn) {
                    Text("Вход выполнен", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            accountNameLoading -> "Загружаю имя аккаунта…"
                            accountName.isNotBlank() -> accountName
                            else -> "Аккаунт AnimeLib"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Выйти") }
                } else {
                    Text("Вход через AnimeLib", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Откроется браузер Android. После входа приложение вернётся по ссылке animelib.org.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onOpenLinkSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Настроить возврат из браузера")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onLogin, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Войти через AnimeLib")
                    }
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        ErrorMessage(error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SimplePage(title: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { Text(title, color = Color.White, fontSize = 22.sp) }
}

@Composable
private fun LoadingIndicator() {
    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorMessage(message: String) { Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

@Composable
private fun EmptyMessage(message: String) { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }

@Composable
private fun FloatingBottomBar(
    selectedTab: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        "Главная" to Icons.Outlined.Home,
        "Каталог" to Icons.Outlined.GridView,
        "Моё" to Icons.Outlined.FavoriteBorder,
        "Профиль" to Icons.Outlined.PersonOutline
    )
    NavigationBar(modifier = modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        tabs.forEach { (label, icon) ->
            NavigationBarItem(
                selected = selectedTab == label,
                onClick = { onSelect(label) },
                icon = {
                    if (label == "Профиль") ProfileAvatar(Modifier.size(24.dp))
                    else Icon(icon, contentDescription = label)
                },
                label = { Text(label) },
                alwaysShowLabel = true
            )
        }
    }
}
