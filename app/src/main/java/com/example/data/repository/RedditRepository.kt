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

class RedditRepository(
    private val context: Context,
    private val database: AppDatabase
) {

    private val apiService = RedditApiService()
    private val subredditDao = database.subredditDao()
    private val savedPostDao = database.savedPostDao()
    private val viewHistoryDao = database.viewHistoryDao()

    // Thread-safe map to preserve pagination anchors ('after' token) per subreddit
    private val afterTokens = ConcurrentHashMap<String, String?>()

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
            // Ensure sssfdg and AlbumCovers exist
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
        afterTokens.clear()
        AppLogger.s("RedditRepo", "Restaurados subreddits predeterminados (sssfdg y AlbumCovers)")
    }

    suspend fun deleteSubreddit(name: String) = withContext(Dispatchers.IO) {
        subredditDao.deleteSubreddit(name)
        afterTokens.remove(name)
        AppLogger.i("RedditRepo", "Subreddit r/$name eliminado de la lista")
    }

    fun resetPagination() {
        afterTokens.clear()
        AppLogger.i("RedditRepo", "Paginación reiniciada")
    }

    /**
     * Fetches fresh posts from all active (enabled) subreddits based on 'Top' (votes) or 'New' (chronological).
     */
    suspend fun fetchPostsFromEnabledSubreddits(
        sort: FeedSort = FeedSort.TOP,
        preventDuplicates: Boolean = true,
        maxLimit: Int = 100,
        clientId: String? = null,
        clientSecret: String? = null
    ): List<RedditPost> = withContext(Dispatchers.IO) {
        initializeDefaultsIfNeeded()
        val enabledSubs = subredditDao.getEnabledSubreddits()
        afterTokens.clear() // fresh reload

        val tag = "RedditRepo"
        AppLogger.i(tag, "=== Recargando fotos nuevas | Modo: ${sort.displayName} | Límite máx: $maxLimit | Subs activos: ${enabledSubs.size} ===")

        if (enabledSubs.isEmpty()) {
            AppLogger.w(tag, "No hay ningún subreddit activo. Usando galería curada local.")
            return@withContext getCuratedFallbackPosts()
        }

        val allFetched = mutableListOf<RedditPost>()
        val viewedIds = if (preventDuplicates) viewHistoryDao.getAllViewedIds().toSet() else emptySet()

        // Fetch up to 35 posts per enabled subreddit
        val perSubLimit = (maxLimit / enabledSubs.size.coerceAtLeast(1)).coerceIn(15, 60)

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
                allFetched.addAll(posts)
                afterTokens[sub.name] = page?.afterToken
                AppLogger.d(tag, "r/${sub.name} retornó ${posts.size} posts (after: ${page?.afterToken})")
            } else {
                AppLogger.w(tag, "Fallo al consultar r/${sub.name}: ${result.exceptionOrNull()?.message}")
            }
        }

        var availablePosts = if (preventDuplicates && viewedIds.isNotEmpty()) {
            allFetched.filterNot { viewedIds.contains(it.id) }
        } else {
            allFetched
        }

        // If all posts were already viewed in this cycle, clear view history and reuse all
        if (availablePosts.isEmpty() && allFetched.isNotEmpty()) {
            AppLogger.i(tag, "Todas las fotos ya fueron vistas. Reiniciando ciclo de no repetición.")
            viewHistoryDao.clearHistory()
            availablePosts = allFetched
        }

        // If online fetch returned nothing (e.g., offline or network blocked), check local cache
        val finalPosts: List<RedditPost> = if (availablePosts.isEmpty()) {
            val cachedFromDb: List<RedditPost> = savedPostDao.getCachedPostsList().map { entity: SavedPostEntity -> entity.toModel() }
            if (cachedFromDb.isNotEmpty()) {
                AppLogger.i(tag, "Sin conexión o bloqueado. Usando ${cachedFromDb.size} fotos guardadas en caché local.")
                cachedFromDb
            } else {
                AppLogger.w(tag, "Sin fotos en caché ni red. Cargando colección de álbumes de respaldo.")
                getCuratedFallbackPosts()
            }
        } else {
            availablePosts.take(maxLimit)
        }

        // Cache post metadata in Room
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

        // Store images directly on local device storage
        LocalImageCacheManager.prefetchPosts(context, finalPosts)

        AppLogger.s(tag, "Listo: ${finalPosts.size} fotos listas para visualizar en proyector / fondo.")
        finalPosts
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
        if (enabledSubs.isEmpty()) return@withContext currentPosts

        AppLogger.i(tag, "Fin de lista alcanzado. Obteniendo más fotos automáticamente (actual: ${currentPosts.size}, límite: $maxLimit)...")

        val existingIds = currentPosts.map { it.id }.toSet()
        val viewedIds = if (preventDuplicates) viewHistoryDao.getAllViewedIds().toSet() else emptySet()
        val newPosts = mutableListOf<RedditPost>()

        val remainingQuota = maxLimit - currentPosts.size
        val perSubLimit = (remainingQuota / enabledSubs.size.coerceAtLeast(1)).coerceIn(10, 30)

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
                newPosts.addAll(fetched)
                afterTokens[sub.name] = page?.afterToken
                AppLogger.d(tag, "Paginación r/${sub.name} sumó ${fetched.size} fotos nuevas (after: ${page?.afterToken})")
            }
        }

        if (newPosts.isEmpty()) {
            AppLogger.i(tag, "No se encontraron más fotos nuevas en este pase de paginación.")
            return@withContext currentPosts
        }

        val combined = (currentPosts + newPosts).take(maxLimit)
        AppLogger.s(tag, "Paginación exitosa: Añadidas ${newPosts.size} fotos. Total en sesión: ${combined.size}/$maxLimit")

        // Cache new images in background
        LocalImageCacheManager.prefetchPosts(context, newPosts)

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

    // High quality offline album covers from AlbumCovers & sssfdg
    fun getCuratedFallbackPosts(): List<RedditPost> {
        return listOf(
            RedditPost(
                id = "curated_album_1",
                subreddit = "AlbumCovers",
                title = "Pink Floyd - The Dark Side of the Moon (Original Master Vinyl Artwork)",
                author = "audio_curator",
                imageUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=1600&auto=format&fit=crop",
                score = 8420,
                numComments = 312
            ),
            RedditPost(
                id = "curated_sssfdg_1",
                subreddit = "sssfdg",
                title = "Composición surrealista abstracta de ondas de sonido y neón en vinilo",
                author = "sssfdg_artist",
                imageUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=1600&auto=format&fit=crop",
                score = 6120,
                numComments = 189
            ),
            RedditPost(
                id = "curated_album_2",
                subreddit = "AlbumCovers",
                title = "Daft Punk - Random Access Memories (Silver & Gold Helmets High-Res)",
                author = "electro_master",
                imageUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=1600&auto=format&fit=crop",
                score = 9810,
                numComments = 420
            ),
            RedditPost(
                id = "curated_sssfdg_2",
                subreddit = "sssfdg",
                title = "Geometría sagrada y colores prismáticos en lienzo analógico",
                author = "visual_dreamer",
                imageUrl = "https://images.unsplash.com/photo-1541701494587-cb58502866ab?q=80&w=1600&auto=format&fit=crop",
                score = 7530,
                numComments = 240
            ),
            RedditPost(
                id = "curated_album_3",
                subreddit = "AlbumCovers",
                title = "Radiohead - OK Computer (Stylized Dystopian Highway Cover Art)",
                author = "indie_vibes",
                imageUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=1600&auto=format&fit=crop",
                score = 11400,
                numComments = 512
            ),
            RedditPost(
                id = "curated_cat_1",
                subreddit = "blurrypicturesofcats",
                title = "Gato a velocidad hiperespacial persiguiendo un láser fantasma a las 3 AM",
                author = "cat_enthusiast",
                imageUrl = "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?q=80&w=1600&auto=format&fit=crop",
                score = 12900,
                numComments = 450
            ),
            RedditPost(
                id = "curated_album_4",
                subreddit = "AlbumCovers",
                title = "Miles Davis - Kind of Blue (Classic Blue Silhouette Session)",
                author = "jazz_collector",
                imageUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?q=80&w=1600&auto=format&fit=crop",
                score = 8900,
                numComments = 290
            ),
            RedditPost(
                id = "curated_sssfdg_3",
                subreddit = "sssfdg",
                title = "Efecto prisma cósmico y resplandor aurora en proyector",
                author = "light_sculptor",
                imageUrl = "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?q=80&w=1600&auto=format&fit=crop",
                score = 5420,
                numComments = 115
            )
        )
    }
}
