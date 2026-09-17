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
import com.example.util.LocalImageCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RedditRepository(
    private val context: Context,
    private val database: AppDatabase
) {

    private val apiService = RedditApiService()
    private val subredditDao = database.subredditDao()
    private val savedPostDao = database.savedPostDao()
    private val viewHistoryDao = database.viewHistoryDao()

    val allSubreddits: Flow<List<SubredditInfo>> = subredditDao.getAllSubreddits().map { entities ->
        entities.map { it.toModel() }
    }

    val favoritePosts: Flow<List<RedditPost>> = savedPostDao.getFavoritePosts().map { entities ->
        entities.map { it.toModel() }
    }

    suspend fun initializeDefaultsIfNeeded() = withContext(Dispatchers.IO) {
        val count = subredditDao.getSubredditCount()
        if (count == 0) {
            restoreDefaultSubreddits()
        }
    }

    suspend fun toggleSubreddit(name: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        subredditDao.setSubredditEnabled(name, isEnabled)
    }

    suspend fun addCustomSubreddit(name: String, displayName: String, emoji: String) = withContext(Dispatchers.IO) {
        val cleanName = name.trim().removePrefix("r/").removePrefix("/")
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
        }
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
    }

    suspend fun deleteSubreddit(name: String) = withContext(Dispatchers.IO) {
        subredditDao.deleteSubreddit(name)
    }

    /**
     * Fetches posts from all active (enabled) subreddits based on either 'Top' (votes) or 'New' (chronological).
     * Automatically stores fetched images in the device's local cache for offline use and fast wallpaper setting.
     */
    suspend fun fetchPostsFromEnabledSubreddits(
        sort: FeedSort = FeedSort.TOP,
        preventDuplicates: Boolean = true
    ): List<RedditPost> = withContext(Dispatchers.IO) {
        initializeDefaultsIfNeeded()
        val enabledSubs = subredditDao.getEnabledSubreddits()

        if (enabledSubs.isEmpty()) {
            return@withContext getCuratedFallbackPosts()
        }

        val allFetched = mutableListOf<RedditPost>()
        val viewedIds = if (preventDuplicates) viewHistoryDao.getAllViewedIds().toSet() else emptySet()

        for (sub in enabledSubs) {
            val result = apiService.fetchSubredditPosts(sub.name, sort, limit = 50)
            if (result.isSuccess) {
                val posts = result.getOrNull().orEmpty()
                allFetched.addAll(posts)
            }
        }

        var availablePosts = if (preventDuplicates && viewedIds.isNotEmpty()) {
            allFetched.filterNot { viewedIds.contains(it.id) }
        } else {
            allFetched
        }

        // If all posts were already viewed in this cycle, clear view history and reuse all
        if (availablePosts.isEmpty() && allFetched.isNotEmpty()) {
            viewHistoryDao.clearHistory()
            availablePosts = allFetched
        }

        // If online fetch returned nothing (e.g., offline or network error), check local Room DB cache
        val finalPosts = if (availablePosts.isEmpty()) {
            val cachedFromDb = savedPostDao.getCachedPostsList().map { it.toModel() }
            if (cachedFromDb.isNotEmpty()) {
                cachedFromDb
            } else {
                getCuratedFallbackPosts()
            }
        } else {
            availablePosts.shuffled()
        }

        // Cache post metadata in Room
        val cacheEntities = finalPosts.take(60).map { post ->
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

        // Store images directly on local device storage to ensure fast wallpaper changes & offline operation
        LocalImageCacheManager.prefetchPosts(context, finalPosts)

        finalPosts
    }

    suspend fun markPostAsViewed(postId: String) = withContext(Dispatchers.IO) {
        viewHistoryDao.markViewed(ViewHistoryEntity(postId = postId))
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        viewHistoryDao.clearHistory()
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
    }

    // High quality curated offline gallery with Album Covers, Blurry Cats, Hmmm, Wallpapers, etc.
    fun getCuratedFallbackPosts(): List<RedditPost> {
        return listOf(
            RedditPost(
                id = "curated_album_1",
                subreddit = "albumartPorn",
                title = "Pink Floyd - The Dark Side of the Moon (Original Master Vinyl Artwork)",
                author = "audio_curator",
                imageUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=1600&auto=format&fit=crop",
                score = 8420,
                numComments = 312
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
                id = "curated_hmmm_1",
                subreddit = "hmmm",
                title = "hmmm - Cuando el profesor de informática dice que la red es inalámbrica",
                author = "institute_memer",
                imageUrl = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?q=80&w=1600&auto=format&fit=crop",
                score = 15300,
                numComments = 820
            ),
            RedditPost(
                id = "curated_art_1",
                subreddit = "Art",
                title = "Neon Dreams & Infinite Horizons - Óleo sobre lienzo digital",
                author = "pixel_master",
                imageUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?q=80&w=1600&auto=format&fit=crop",
                score = 9800,
                numComments = 190
            ),
            RedditPost(
                id = "curated_space_1",
                subreddit = "spaceporn",
                title = "Nebulosa Carina capturada por el telescopio espacial James Webb",
                author = "astro_walker",
                imageUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=1600&auto=format&fit=crop",
                score = 21400,
                numComments = 940
            ),
            RedditPost(
                id = "curated_cyber_1",
                subreddit = "Cyberpunk",
                title = "Shinjuku Nocturno bajo la lluvia de neón y vapor",
                author = "tokyo_runner",
                imageUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?q=80&w=1600&auto=format&fit=crop",
                score = 11200,
                numComments = 280
            ),
            RedditPost(
                id = "curated_interesting_1",
                subreddit = "mildlyinteresting",
                title = "Este árbol creció exactamente siguiendo la estructura metálica del instituto",
                author = "nature_fan",
                imageUrl = "https://images.unsplash.com/photo-1513836279014-a89f7a76ae86?q=80&w=1600&auto=format&fit=crop",
                score = 17600,
                numComments = 510
            ),
            RedditPost(
                id = "curated_earth_1",
                subreddit = "EarthPorn",
                title = "Amanecer dorado reflejado en los lagos alpinos de los Dolomitas",
                author = "wanderer_99",
                imageUrl = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?q=80&w=1600&auto=format&fit=crop",
                score = 14500,
                numComments = 380
            )
        )
    }

    private fun SubredditEntity.toModel() = SubredditInfo(
        name = name,
        displayName = displayName,
        description = description,
        iconEmoji = iconEmoji,
        isEnabled = isEnabled,
        isCustom = isCustom,
        category = category
    )

    private fun SavedPostEntity.toModel() = RedditPost(
        id = id,
        subreddit = subreddit,
        title = title,
        author = author,
        imageUrl = imageUrl,
        permalink = permalink,
        score = score,
        width = width,
        height = height,
        isLocal = isLocal,
        timestamp = savedAt
    )
}
