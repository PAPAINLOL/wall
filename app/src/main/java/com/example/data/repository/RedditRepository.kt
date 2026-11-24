package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.SavedPostEntity
import com.example.data.local.SubredditEntity
import com.example.data.local.ViewHistoryEntity
import com.example.data.model.FeedSort
import com.example.data.model.RedditPost
import com.example.data.model.SubredditInfo
import com.example.data.remote.RedditApiService
import com.example.util.AppLogger
import com.example.util.LocalImageCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class FetchPostsResult(
    val posts: List<RedditPost>,
    val isFromNetwork: Boolean,
    val isFromCache: Boolean,
    val isFallback: Boolean,
    val errorMessage: String? = null
)

class RedditRepository(
    private val context: Context,
    private val database: AppDatabase
) {

    private val apiService = RedditApiService()
    private val subredditDao = database.subredditDao()
    private val savedPostDao = database.savedPostDao()
    private val viewHistoryDao = database.viewHistoryDao()

    // Thread-safe pagination state per subreddit
    private val afterTokens = ConcurrentHashMap<String, String?>()
    private val exhaustedSubs = ConcurrentHashMap.newKeySet<String>()

    val allSubreddits: Flow<List<SubredditInfo>> = subredditDao.getAllSubreddits().map { entities ->
        entities.map { it.toModel() }
    }

    val favoritePosts: Flow<List<RedditPost>> = savedPostDao.getFavoritePosts().map { entities ->
        entities.map { it.toModel() }
    }

    suspend fun initializeDefaultsIfNeeded() = withContext(Dispatchers.IO) {
        val count = subredditDao.getSubredditCount()
        if (count == 0) {
            AppLogger.i("RedditRepo", "Base de datos vacía. Restaurando subreddits por defecto (sssfdg y AlbumCovers activos)")
            restoreDefaultSubreddits()
        } else {
            val all = subredditDao.getAllSubredditsSync()
            val hasSssfdg = all.any { it.name.equals("sssfdg", ignoreCase = true) }
            val hasAlbumCovers = all.any { it.name.equals("AlbumCovers", ignoreCase = true) }

            if (!hasSssfdg) {
                AppLogger.i("RedditRepo", "Añadiendo 'sssfdg' como subreddit principal por defecto")
                subredditDao.insertSubreddit(
                    SubredditEntity(
                        name = "sssfdg",
                        displayName = "sssfdg",
                        description = "Subreddit principal seleccionado por defecto",
                        iconEmoji = "🔥",
                        isEnabled = true,
                        isCustom = false,
                        category = "Favoritos"
                    )
                )
            }
            if (!hasAlbumCovers) {
                AppLogger.i("RedditRepo", "Añadiendo 'AlbumCovers' como subreddit principal por defecto")
                subredditDao.insertSubreddit(
                    SubredditEntity(
                        name = "AlbumCovers",
                        displayName = "Album Covers",
                        description = "Portadas de álbumes de música icónicas y en alta resolución",
                        iconEmoji = "🎵",
                        isEnabled = true,
                        isCustom = false,
                        category = "Música & Arte"
                    )
                )
            }
        }
    }

    suspend fun toggleSubreddit(name: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        AppLogger.i("RedditRepo", "Subreddit r/$name cambiado a: ${if (isEnabled) "Activo" else "Oculto"}")
        subredditDao.setSubredditEnabled(name, isEnabled)
        afterTokens.remove(name)
        exhaustedSubs.remove(name)
    }

    suspend fun addCustomSubreddit(name: String, displayName: String, emoji: String) = withContext(Dispatchers.IO) {
        val cleanName = name.trim().removePrefix("r/").removePrefix("/").trim()
        val entity = SubredditEntity(
            name = cleanName,
            displayName = if (displayName.isBlank()) cleanName else displayName.trim(),
            description = "Subreddit personalizado añadido por el usuario",
            iconEmoji = if (emoji.isBlank()) "🔥" else emoji.trim(),
            isEnabled = true,
            isCustom = true,
            category = "Personalizados"
        )
        subredditDao.insertSubreddit(entity)
        AppLogger.s("RedditRepo", "Añadido nuevo subreddit: r/$cleanName")
    }

    suspend fun addMultipleSubreddits(inputString: String) = withContext(Dispatchers.IO) {
        val names = inputString.split(",", "\n", ";")
            .map { it.trim().removePrefix("r/").removePrefix("/").trim() }
            .filter { it.isNotBlank() }

        val currentCount = subredditDao.getSubredditCount()
        names.forEachIndexed { idx, cleanName ->
            val entity = SubredditEntity(
                name = cleanName,
                displayName = cleanName,
                description = "Subreddit añadido por el usuario",
                iconEmoji = "🔥",
                isEnabled = true,
                isCustom = true,
                category = "Personalizados",
                orderIndex = currentCount + idx
            )
            subredditDao.insertSubreddit(entity)
            AppLogger.i("RedditRepo", "Importado subreddit en lote: r/$cleanName")
        }
        AppLogger.s("RedditRepo", "Añadidos ${names.size} subreddits en lote exitosamente")
    }

    suspend fun restoreDefaultSubreddits() = withContext(Dispatchers.IO) {
        val entities = SubredditInfo.DEFAULT_SUBREDDITS.mapIndexed { index, info ->
            SubredditEntity(
                name = info.name,
                displayName = info.displayName,
                description = info.description,
                iconEmoji = info.iconEmoji,
                isEnabled = info.isEnabled,
                isCustom = info.isCustom,
                category = info.category,
                orderIndex = index
            )
        }
        subredditDao.insertSubreddits(entities)
        resetPagination()
        AppLogger.s("RedditRepo", "Restaurados subreddits predeterminados (sssfdg y AlbumCovers)")
    }

    suspend fun deleteSubreddit(name: String) = withContext(Dispatchers.IO) {
        subredditDao.deleteSubreddit(name)
        afterTokens.remove(name)
        exhaustedSubs.remove(name)
        AppLogger.i("RedditRepo", "Subreddit r/$name eliminado de la lista")
    }

    fun resetPagination() {
        afterTokens.clear()
        exhaustedSubs.clear()
        AppLogger.i("RedditRepo", "Paginación reiniciada")
    }

    /**
     * Fetches fresh posts from all active (enabled) subreddits based on 'Top' (votes) or 'New' (chronological).
     * Never blocks on full image prefetching — returns metadata immediately so UI renders in < 1s.
     */
    suspend fun fetchPostsFromEnabledSubreddits(
        sort: FeedSort = FeedSort.TOP,
        preventDuplicates: Boolean = true,
        maxLimit: Int = 100,
        clientId: String? = null,
        clientSecret: String? = null
    ): FetchPostsResult = withContext(Dispatchers.IO) {
        initializeDefaultsIfNeeded()
        val enabledSubs = subredditDao.getEnabledSubreddits()
        resetPagination()

        val tag = "RedditRepo"
        AppLogger.i(tag, "=== Consultando Reddit | Modo: ${sort.displayName} | Límite: $maxLimit | Subs activos: ${enabledSubs.map { "r/${it.name}" }} ===")

        if (enabledSubs.isEmpty()) {
            AppLogger.w(tag, "No hay ningún subreddit activo. Mostrando galería curada de respaldo.")
            return@withContext FetchPostsResult(
                posts = getCuratedFallbackPosts(),
                isFromNetwork = false,
                isFromCache = false,
                isFallback = true,
                errorMessage = "No hay ningún subreddit activado en tu lista."
            )
        }

        val postsBySub = mutableListOf<List<RedditPost>>()
        val viewedIds = if (preventDuplicates) viewHistoryDao.getAllViewedIds().toSet() else emptySet()
        val errors = mutableListOf<String>()

        val perSubLimit = (maxLimit / enabledSubs.size.coerceAtLeast(1)).coerceIn(20, 60)

        for (sub in enabledSubs) {
            val result = apiService.fetchSubredditPosts(
                subredditName = sub.name,
                sort = sort,
                limit = perSubLimit,
                after = null,
                clientId = clientId,
                clientSecret = clientSecret
            )
            if (result.isSuccess) {
                val page = result.getOrNull()
                val posts = page?.posts.orEmpty()
                if (posts.isNotEmpty()) {
                    postsBySub.add(posts)
                }
                if (page?.afterToken != null) {
                    afterTokens[sub.name] = page.afterToken
                } else {
                    exhaustedSubs.add(sub.name)
                }
                AppLogger.d(tag, "r/${sub.name} retornó ${posts.size} fotos (after: ${page?.afterToken ?: "fin"})")
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Error desconocido en r/${sub.name}"
                errors.add("r/${sub.name}: $msg")
                AppLogger.w(tag, "Fallo al consultar r/${sub.name}: $msg")
            }
        }

        // Interleave round-robin across enabled subreddits so every active subreddit appears early
        val interleaved = interleaveLists(postsBySub)

        var availablePosts = if (preventDuplicates && viewedIds.isNotEmpty()) {
            interleaved.filterNot { viewedIds.contains(it.id) }
        } else {
            interleaved
        }

        // If all fetched posts were already viewed in this cycle, clear view history and reuse all fetched
        if (availablePosts.isEmpty() && interleaved.isNotEmpty()) {
            AppLogger.i(tag, "Todas las fotos de esta tanda ya fueron vistas. Reiniciando ciclo de no repetición.")
            viewHistoryDao.clearHistory()
            availablePosts = interleaved
        }

        if (availablePosts.isNotEmpty()) {
            val finalPosts = availablePosts.take(maxLimit)
            // Cache ONLY real fetched Reddit posts in Room
            val cacheEntities = finalPosts.map { post ->
                SavedPostEntity(
                    id = post.id,
                    subreddit = post.subreddit,
                    title = post.title,
                    author = post.author,
                    imageUrl = post.imageUrl,
                    permalink = post.permalink,
                    score = post.score,
                    width = post.width,
                    height = post.height
                )
            }
            savedPostDao.insertPosts(cacheEntities)
            AppLogger.s(tag, "Listo: ${finalPosts.size} fotos reales de Reddit listas para mostrar.")

            return@withContext FetchPostsResult(
                posts = finalPosts,
                isFromNetwork = true,
                isFromCache = false,
                isFallback = false,
                errorMessage = if (errors.isNotEmpty()) errors.first() else null
            )
        }

        // Network failed or returned 0 images: check local Room cache ONLY for enabled subreddits (excluding curated fallback)
        val enabledSubNamesLower = enabledSubs.map { it.name.lowercase() }.toSet()
        val cachedFromDb = savedPostDao.getCachedPostsList()
            .filter { !it.id.startsWith("curated_") && enabledSubNamesLower.contains(it.subreddit.lowercase()) }
            .map { it.toModel() }

        val combinedError = errors.firstOrNull()
            ?: "No se encontraron imágenes en los subreddits activos (${enabledSubs.joinToString { "r/${it.name}" }})."

        if (cachedFromDb.isNotEmpty()) {
            AppLogger.w(tag, "Sin respuesta de red ($combinedError). Usando ${cachedFromDb.size} fotos reales en caché local para tus subreddits.")
            return@withContext FetchPostsResult(
                posts = cachedFromDb.take(maxLimit),
                isFromNetwork = false,
                isFromCache = true,
                isFallback = false,
                errorMessage = combinedError
            )
        }

        // Last resort: curated offline fallback (NEVER saved to Room)
        AppLogger.e(tag, "Error de red y sin caché local para los subreddits activos: $combinedError")
        return@withContext FetchPostsResult(
            posts = getCuratedFallbackPosts(),
            isFromNetwork = false,
            isFromCache = false,
            isFallback = true,
            errorMessage = combinedError
        )
    }

    /**
     * Paginates and fetches MORE posts when reaching the end of the current photo list
     * up to the user-configured maxPostsLimit in settings.
     */
    suspend fun fetchMorePosts(
        currentPosts: List<RedditPost>,
        sort: FeedSort = FeedSort.TOP,
        maxLimit: Int = 100,
        preventDuplicates: Boolean = true,
        clientId: String? = null,
        clientSecret: String? = null
    ): List<RedditPost> = withContext(Dispatchers.IO) {
        val tag = "RedditPaging"
        if (currentPosts.size >= maxLimit) {
            AppLogger.i(tag, "Límite máximo alcanzado (${currentPosts.size}/$maxLimit fotos). No se piden más según ajustes.")
            return@withContext currentPosts
        }

        val enabledSubs = subredditDao.getEnabledSubreddits()
            .filterNot { exhaustedSubs.contains(it.name) }

        if (enabledSubs.isEmpty()) {
            AppLogger.i(tag, "Todos los subreddits activos llegaron al final de sus páginas.")
            return@withContext currentPosts
        }

        AppLogger.i(tag, "Fin de lista alcanzado. Pidiendo más fotos automáticamente (actual: ${currentPosts.size}, límite: $maxLimit)...")

        val existingIds = currentPosts.map { it.id }.toSet()
        val viewedIds = if (preventDuplicates) viewHistoryDao.getAllViewedIds().toSet() else emptySet()
        val newPostsBySub = mutableListOf<List<RedditPost>>()

        val remainingQuota = maxLimit - currentPosts.size
        val perSubLimit = (remainingQuota / enabledSubs.size.coerceAtLeast(1)).coerceIn(15, 35)

        for (sub in enabledSubs) {
            val currentAfter = afterTokens[sub.name]
            val result = apiService.fetchSubredditPosts(
                subredditName = sub.name,
                sort = sort,
                limit = perSubLimit,
                after = currentAfter,
                clientId = clientId,
                clientSecret = clientSecret
            )
            if (result.isSuccess) {
                val page = result.getOrNull()
                val fetched = page?.posts.orEmpty().filterNot {
                    existingIds.contains(it.id) || (preventDuplicates && viewedIds.contains(it.id))
                }
                if (fetched.isNotEmpty()) {
                    newPostsBySub.add(fetched)
                }
                if (page?.afterToken != null) {
                    afterTokens[sub.name] = page.afterToken
                } else {
                    exhaustedSubs.add(sub.name)
                }
                AppLogger.d(tag, "Paginación r/${sub.name} sumó ${fetched.size} fotos nuevas (after: ${page?.afterToken ?: "fin"})")
            }
        }

        val newPosts = interleaveLists(newPostsBySub)
        if (newPosts.isEmpty()) {
            AppLogger.i(tag, "No se encontraron más fotos nuevas en este pase de paginación.")
            return@withContext currentPosts
        }

        val combined = (currentPosts + newPosts).take(maxLimit)
        AppLogger.s(tag, "Paginación exitosa: Añadidas ${newPosts.size} fotos. Total en sesión: ${combined.size}/$maxLimit")

        val cacheEntities = newPosts.map { post ->
            SavedPostEntity(
                id = post.id,
                subreddit = post.subreddit,
                title = post.title,
                author = post.author,
                imageUrl = post.imageUrl,
                permalink = post.permalink,
                score = post.score,
                width = post.width,
                height = post.height
            )
        }
        savedPostDao.insertPosts(cacheEntities)

        combined
    }

    private fun interleaveLists(lists: List<List<RedditPost>>): List<RedditPost> {
        if (lists.isEmpty()) return emptyList()
        if (lists.size == 1) return lists[0]
        val result = mutableListOf<RedditPost>()
        val maxLen = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) {
            for (list in lists) {
                if (i < list.size) {
                    result.add(list[i])
                }
            }
        }
        return result
    }

    suspend fun prefetchImagesAsync(posts: List<RedditPost>) {
        LocalImageCacheManager.prefetchPosts(context, posts)
    }

    suspend fun markPostAsViewed(postId: String) = withContext(Dispatchers.IO) {
        viewHistoryDao.markViewed(ViewHistoryEntity(postId = postId))
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        viewHistoryDao.clearHistory()
        AppLogger.i("RedditRepo", "Historial de repetición borrado")
    }

    suspend fun toggleFavorite(post: RedditPost, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        savedPostDao.insertPost(
            SavedPostEntity(
                id = post.id,
                subreddit = post.subreddit,
                title = post.title,
                author = post.author,
                imageUrl = post.imageUrl,
                permalink = post.permalink,
                score = post.score,
                width = post.width,
                height = post.height,
                isFavorite = isFavorite,
                isLocal = post.isLocal
            )
        )
        AppLogger.i("RedditRepo", "Foto ${post.id} ${if (isFavorite) "añadida a favoritos" else "removida de favoritos"}")
    }

    // High quality offline album covers shown ONLY if network fails and no cached Reddit posts exist
    fun getCuratedFallbackPosts(): List<RedditPost> {
        return listOf(
            RedditPost(
                id = "curated_album_1",
                subreddit = "AlbumCovers",
                title = "Pink Floyd - The Dark Side of the Moon (Respaldo Offline)",
                author = "audio_curator",
                imageUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=1600&auto=format&fit=crop",
                score = 8420,
                numComments = 312
            ),
            RedditPost(
                id = "curated_sssfdg_1",
                subreddit = "sssfdg",
                title = "Composición surrealista abstracta (Respaldo Offline)",
                author = "sssfdg_artist",
                imageUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=1600&auto=format&fit=crop",
                score = 6120,
                numComments = 189
            ),
            RedditPost(
                id = "curated_album_2",
                subreddit = "AlbumCovers",
                title = "Daft Punk - Random Access Memories (Respaldo Offline)",
                author = "electro_master",
                imageUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=1600&auto=format&fit=crop",
                score = 9810,
                numComments = 420
            ),
            RedditPost(
                id = "curated_sssfdg_2",
                subreddit = "sssfdg",
                title = "Geometría sagrada y colores prismáticos (Respaldo Offline)",
                author = "visual_dreamer",
                imageUrl = "https://images.unsplash.com/photo-1541701494587-cb58502866ab?q=80&w=1600&auto=format&fit=crop",
                score = 7530,
                numComments = 240
            )
        )
    }
}
