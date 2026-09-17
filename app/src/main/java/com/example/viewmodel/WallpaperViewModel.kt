package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.BorderStyle
import com.example.data.model.DisplaySettings
import com.example.data.model.FeedSort
import com.example.data.model.RedditPost
import com.example.data.model.SubredditInfo
import com.example.data.model.WallpaperTarget
import com.example.data.repository.RedditRepository
import com.example.util.LocalImageCacheManager
import com.example.util.PaletteHelper
import com.example.util.WallpaperManagerHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val posts: List<RedditPost> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val isSettingWallpaper: Boolean = false,
    val subreddits: List<SubredditInfo> = emptyList(),
    val favorites: List<RedditPost> = emptyList(),
    val settings: DisplaySettings = DisplaySettings(),
    val isTvAmbientMode: Boolean = false,
    val showControlsInTvMode: Boolean = true,
    val currentBorderColor: Color = Color(0xFF1E202C),
    val error: String? = null,
    val cachedImagesCount: Int = 0,
    val cacheSizeString: String = "0 MB",
    val isCurrentPostCached: Boolean = false
) {
    val currentPost: RedditPost?
        get() = if (posts.isNotEmpty() && currentIndex in posts.indices) posts[currentIndex] else null
}

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RedditRepository
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private var slideshowJob: Job? = null

    init {
        val db = AppDatabase.getDatabase(application)
        repository = RedditRepository(application, db)

        viewModelScope.launch {
            repository.initializeDefaultsIfNeeded()
        }

        viewModelScope.launch {
            repository.allSubreddits.collectLatest { subs ->
                _uiState.update { it.copy(subreddits = subs) }
            }
        }

        viewModelScope.launch {
            repository.favoritePosts.collectLatest { favs ->
                _uiState.update { it.copy(favorites = favs) }
            }
        }

        refreshPosts()
        refreshCacheStats()
    }

    fun refreshPosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val fetched = repository.fetchPostsFromEnabledSubreddits(
                    sort = _uiState.value.settings.feedSort,
                    preventDuplicates = _uiState.value.settings.preventDuplicates
                )
                if (fetched.isNotEmpty()) {
                    val initialPost = fetched[0]
                    val initialColor = PaletteHelper.getBorderColorForPost(
                        postId = initialPost.id,
                        style = _uiState.value.settings.borderStyle
                    )
                    val isCached = LocalImageCacheManager.isCached(
                        getApplication(),
                        initialPost.id,
                        initialPost.imageUrl
                    )
                    _uiState.update {
                        it.copy(
                            posts = fetched,
                            currentIndex = 0,
                            currentBorderColor = initialColor,
                            isLoading = false,
                            isCurrentPostCached = isCached
                        )
                    }
                    repository.markPostAsViewed(initialPost.id)
                    restartSlideshowTimer()
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                val fallback = repository.getCuratedFallbackPosts()
                _uiState.update {
                    it.copy(
                        posts = fallback,
                        currentIndex = 0,
                        isLoading = false,
                        error = "Modo sin conexión: cargando álbumes locales del instituto"
                    )
                }
                restartSlideshowTimer()
            }
            refreshCacheStats()
        }
    }

    fun nextPost() {
        val state = _uiState.value
        if (state.posts.isEmpty()) return
        val nextIdx = (state.currentIndex + 1) % state.posts.size
        val post = state.posts[nextIdx]
        val newColor = PaletteHelper.getBorderColorForPost(
            postId = post.id,
            style = state.settings.borderStyle
        )
        val isCached = LocalImageCacheManager.isCached(getApplication(), post.id, post.imageUrl)
        _uiState.update {
            it.copy(currentIndex = nextIdx, currentBorderColor = newColor, isCurrentPostCached = isCached)
        }
        viewModelScope.launch {
            repository.markPostAsViewed(post.id)
            if (state.settings.autoSetWallpaperOnSlide) {
                applyCurrentAsWallpaper(silent = true)
            }
        }
        restartSlideshowTimer()
    }

    fun previousPost() {
        val state = _uiState.value
        if (state.posts.isEmpty()) return
        val prevIdx = if (state.currentIndex - 1 < 0) state.posts.size - 1 else state.currentIndex - 1
        val post = state.posts[prevIdx]
        val newColor = PaletteHelper.getBorderColorForPost(
            postId = post.id,
            style = state.settings.borderStyle
        )
        val isCached = LocalImageCacheManager.isCached(getApplication(), post.id, post.imageUrl)
        _uiState.update {
            it.copy(currentIndex = prevIdx, currentBorderColor = newColor, isCurrentPostCached = isCached)
        }
        restartSlideshowTimer()
    }

    fun jumpToPost(index: Int) {
        val state = _uiState.value
        if (index in state.posts.indices) {
            val post = state.posts[index]
            val newColor = PaletteHelper.getBorderColorForPost(
                postId = post.id,
                style = state.settings.borderStyle
            )
            val isCached = LocalImageCacheManager.isCached(getApplication(), post.id, post.imageUrl)
            _uiState.update {
                it.copy(currentIndex = index, currentBorderColor = newColor, isCurrentPostCached = isCached)
            }
            restartSlideshowTimer()
        }
    }

    fun toggleAutoPlay() {
        _uiState.update {
            val newSettings = it.settings.copy(isAutoPlay = !it.settings.isAutoPlay)
            it.copy(settings = newSettings)
        }
        if (_uiState.value.settings.isAutoPlay) {
            restartSlideshowTimer()
            emitToast("▶ Reproducción automática activada")
        } else {
            slideshowJob?.cancel()
            emitToast("⏸ Reproducción en pausa")
        }
    }

    fun toggleTvAmbientMode() {
        _uiState.update { it.copy(isTvAmbientMode = !it.isTvAmbientMode) }
    }

    fun toggleControlsInTvMode() {
        _uiState.update { it.copy(showControlsInTvMode = !it.showControlsInTvMode) }
    }

    fun setInterval(seconds: Int) {
        _uiState.update {
            it.copy(settings = it.settings.copy(intervalSeconds = seconds))
        }
        restartSlideshowTimer()
        emitToast("Intervalo actualizado a $seconds seg")
    }

    fun setBorderStyle(style: BorderStyle) {
        _uiState.update {
            it.copy(settings = it.settings.copy(borderStyle = style))
        }
        val post = _uiState.value.currentPost
        if (post != null) {
            val color = PaletteHelper.getBorderColorForPost(post.id, style)
            _uiState.update { it.copy(currentBorderColor = color) }
        }
        emitToast("Bordes configurados: ${style.title}")
    }

    fun setFeedSort(sort: FeedSort) {
        _uiState.update {
            it.copy(settings = it.settings.copy(feedSort = sort))
        }
        refreshPosts()
        val desc = if (sort == FeedSort.TOP) "🏆 Modo Top (Votos)" else "⏱️ Modo New (Cronológico)"
        emitToast(desc)
    }

    fun setWallpaperTarget(target: WallpaperTarget) {
        _uiState.update {
            it.copy(settings = it.settings.copy(wallpaperTarget = target))
        }
        emitToast("Destino de fondo: ${target.displayName}")
    }

    fun togglePreventDuplicates(prevent: Boolean) {
        _uiState.update {
            it.copy(settings = it.settings.copy(preventDuplicates = prevent))
        }
        emitToast(if (prevent) "✓ 'No repetir' activado" else "'No repetir' desactivado")
    }

    fun toggleAutoSetWallpaperOnSlide(auto: Boolean) {
        _uiState.update {
            it.copy(settings = it.settings.copy(autoSetWallpaperOnSlide = auto))
        }
        emitToast(if (auto) "Cambio automático de fondo del sistema ON" else "Cambio automático de fondo del sistema OFF")
    }

    fun toggleSubreddit(name: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleSubreddit(name, enabled)
            emitToast(if (enabled) "r/$name activado" else "r/$name ocultado temporalmente")
            refreshPosts()
        }
    }

    fun addCustomSubreddit(name: String, displayName: String, emoji: String) {
        viewModelScope.launch {
            repository.addCustomSubreddit(name, displayName, emoji)
            emitToast("¡Subreddit r/$name añadido!")
            refreshPosts()
        }
    }

    fun addMultipleSubreddits(input: String) {
        viewModelScope.launch {
            repository.addMultipleSubreddits(input)
            emitToast("¡Subreddits añadidos exitosamente!")
            refreshPosts()
        }
    }

    fun restoreDefaultSubreddits() {
        viewModelScope.launch {
            repository.restoreDefaultSubreddits()
            emitToast("Subreddits restaurados a la lista predeterminada")
            refreshPosts()
        }
    }

    fun deleteSubreddit(name: String) {
        viewModelScope.launch {
            repository.deleteSubreddit(name)
            emitToast("Subreddit r/$name eliminado de la lista")
            refreshPosts()
        }
    }

    fun addLocalImage(uri: Uri, name: String) {
        val newPost = RedditPost(
            id = "local_" + UUID.randomUUID().toString().take(8),
            subreddit = "Galería Local",
            title = if (name.isBlank()) "Foto añadida desde galería" else name,
            author = "Tu Dispositivo",
            imageUrl = uri.toString(),
            score = 9999,
            numComments = 0,
            isLocal = true
        )
        _uiState.update {
            it.copy(posts = listOf(newPost) + it.posts, currentIndex = 0)
        }
        emitToast("¡Imagen de tu galería añadida al carrusel!")
    }

    fun toggleFavoriteCurrent() {
        val post = _uiState.value.currentPost ?: return
        val isFav = _uiState.value.favorites.any { it.id == post.id }
        viewModelScope.launch {
            repository.toggleFavorite(post, !isFav)
            emitToast(if (!isFav) "❤️ Guardado en favoritos" else "💔 Eliminado de favoritos")
        }
    }

    fun applyCurrentAsWallpaper(silent: Boolean = false) {
        val post = _uiState.value.currentPost ?: return
        val context = getApplication<Application>().applicationContext
        val state = _uiState.value

        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(isSettingWallpaper = true) }
            val result = WallpaperManagerHelper.setPostAsWallpaper(
                context = context,
                post = post,
                borderStyle = state.settings.borderStyle,
                target = state.settings.wallpaperTarget
            )
            if (!silent) _uiState.update { it.copy(isSettingWallpaper = false) }

            if (result.isSuccess) {
                if (!silent) emitToast("🖼️ " + result.getOrDefault("¡Fondo aplicado sin recortar la foto!"))
                refreshCacheStats()
            } else {
                if (!silent) emitToast("⚠️ Error al poner fondo: ${result.exceptionOrNull()?.localizedMessage ?: "desconocido"}")
            }
        }
    }

    fun cacheAllCurrentImages() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            emitToast("Guardando fotos en caché del dispositivo...")
            LocalImageCacheManager.prefetchPosts(context, _uiState.value.posts)
            refreshCacheStats()
            emitToast("¡Todas las fotos actuales están cacheadas localmente!")
        }
    }

    fun clearLocalCache() {
        val context = getApplication<Application>().applicationContext
        LocalImageCacheManager.clearCache(context)
        refreshCacheStats()
        emitToast("Caché local de fotos limpiada")
    }

    fun refreshCacheStats() {
        val context = getApplication<Application>().applicationContext
        val count = LocalImageCacheManager.getCacheCount(context)
        val size = LocalImageCacheManager.getCacheSizeMB(context)
        val currentPost = _uiState.value.currentPost
        val isCached = currentPost != null && LocalImageCacheManager.isCached(context, currentPost.id, currentPost.imageUrl)
        _uiState.update {
            it.copy(cachedImagesCount = count, cacheSizeString = size, isCurrentPostCached = isCached)
        }
    }

    private fun restartSlideshowTimer() {
        slideshowJob?.cancel()
        if (!_uiState.value.settings.isAutoPlay) return

        slideshowJob = viewModelScope.launch {
            val intervalMs = (_uiState.value.settings.intervalSeconds * 1000L).coerceAtLeast(3000L)
            while (isActive) {
                delay(intervalMs)
                val state = _uiState.value
                if (state.posts.isNotEmpty()) {
                    val nextIdx = (state.currentIndex + 1) % state.posts.size
                    val post = state.posts[nextIdx]
                    val newColor = PaletteHelper.getBorderColorForPost(
                        postId = post.id,
                        style = state.settings.borderStyle
                    )
                    val isCached = LocalImageCacheManager.isCached(getApplication(), post.id, post.imageUrl)
                    _uiState.update {
                        it.copy(currentIndex = nextIdx, currentBorderColor = newColor, isCurrentPostCached = isCached)
                    }
                    repository.markPostAsViewed(post.id)
                    if (state.settings.autoSetWallpaperOnSlide) {
                        applyCurrentAsWallpaper(silent = true)
                    }
                }
            }
        }
    }

    private fun emitToast(msg: String) {
        viewModelScope.launch {
            _toastEvents.emit(msg)
        }
    }

    override fun onCleared() {
        super.onCleared()
        slideshowJob?.cancel()
    }
}
