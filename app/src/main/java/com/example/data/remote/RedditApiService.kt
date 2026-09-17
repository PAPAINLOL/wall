package com.example.data.remote

import com.example.data.model.FeedSort
import com.example.data.model.RedditPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RedditApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val userAgents = listOf(
        "android:com.aistudio.redditwallpapers:v1.0.0 (by /u/tv_ambient_curator)",
        "Mozilla/5.0 (Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"
    )

    suspend fun fetchSubredditPosts(
        subredditName: String,
        sort: FeedSort = FeedSort.TOP_MONTH,
        limit: Int = 75
    ): Result<List<RedditPost>> = withContext(Dispatchers.IO) {
        val cleanSub = subredditName.trim().removePrefix("r/").removePrefix("/")
        val sortParam = sort.param
        val url = if (sortParam.contains("?")) {
            "https://www.reddit.com/r/$cleanSub/$sortParam&limit=$limit&raw_json=1"
        } else {
            "https://www.reddit.com/r/$cleanSub/$sortParam.json?limit=$limit&raw_json=1"
        }

        val parsedPosts = mutableListOf<RedditPost>()

        // Try primary URL, then mirror fallback if needed
        val urlsToTry = listOf(
            url,
            "https://api.reddit.com/r/$cleanSub/${sortParam.replace("?", ".json?")}?limit=$limit&raw_json=1",
            "https://old.reddit.com/r/$cleanSub/$sortParam.json?limit=$limit&raw_json=1"
        )

        var lastException: Exception? = null

        for (targetUrl in urlsToTry) {
            for (agent in userAgents) {
                try {
                    val request = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", agent)
                        .header("Accept", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank() && body.startsWith("{")) {
                                val json = JSONObject(body)
                                val data = json.optJSONObject("data")
                                val children = data?.optJSONArray("children")
                                if (children != null && children.length() > 0) {
                                    for (i in 0 until children.length()) {
                                        val item = children.optJSONObject(i)?.optJSONObject("data") ?: continue
                                        val post = extractImagePost(item, cleanSub)
                                        if (post != null) {
                                            parsedPosts.add(post)
                                        }
                                    }
                                    if (parsedPosts.isNotEmpty()) {
                                        return@withContext Result.success(parsedPosts)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }
        }

        if (parsedPosts.isNotEmpty()) {
            Result.success(parsedPosts)
        } else {
            Result.failure(lastException ?: Exception("No se pudieron cargar imágenes de r/$cleanSub"))
        }
    }

    private fun extractImagePost(item: JSONObject, defaultSub: String): RedditPost? {
        val id = item.optString("id")
        if (id.isBlank()) return null

        val title = item.optString("title", "Sin título").unescapeHtml()
        val author = item.optString("author", "anónimo")
        val subreddit = item.optString("subreddit", defaultSub)
        val score = item.optInt("score", item.optInt("ups", 0))
        val numComments = item.optInt("num_comments", 0)
        val permalink = "https://reddit.com" + item.optString("permalink")
        val isVideo = item.optBoolean("is_video", false)
        val postHint = item.optString("post_hint", "")
        val domain = item.optString("domain", "")

        if (isVideo) return null

        var validImageUrl: String? = null
        var width = 0
        var height = 0

        // 1. Check direct url / url_overridden_by_dest
        val directUrl = item.optString("url_overridden_by_dest", item.optString("url", ""))
            .unescapeHtml()

        if (isImageExtension(directUrl)) {
            validImageUrl = directUrl
        }

        // 2. Check preview images
        val preview = item.optJSONObject("preview")
        if (preview != null) {
            val images = preview.optJSONArray("images")
            if (images != null && images.length() > 0) {
                val firstImg = images.optJSONObject(0)
                val source = firstImg?.optJSONObject("source")
                if (source != null) {
                    val previewUrl = source.optString("url", "").unescapeHtml()
                    if (previewUrl.isNotBlank()) {
                        if (validImageUrl == null) {
                            validImageUrl = previewUrl
                        }
                        width = source.optInt("width", 0)
                        height = source.optInt("height", 0)
                    }
                }
            }
        }

        // 3. Check reddit gallery items
        val isGallery = item.optBoolean("is_gallery", false)
        if (isGallery && validImageUrl == null) {
            val mediaMetadata = item.optJSONObject("media_metadata")
            val galleryData = item.optJSONObject("gallery_data")
            val items = galleryData?.optJSONArray("items")
            if (items != null && items.length() > 0 && mediaMetadata != null) {
                val firstItem = items.optJSONObject(0)
                val mediaId = firstItem?.optString("media_id")
                if (!mediaId.isNullOrBlank()) {
                    val mediaObj = mediaMetadata.optJSONObject(mediaId)
                    val sObj = mediaObj?.optJSONObject("s")
                    val mediaUrl = sObj?.optString("u", "")?.unescapeHtml()
                    if (!mediaUrl.isNullOrBlank()) {
                        validImageUrl = mediaUrl
                        width = sObj.optInt("x", 0)
                        height = sObj.optInt("y", 0)
                    }
                }
            }
        }

        // 4. Imgur / i.redd.it direct check
        if (validImageUrl == null && (domain.contains("imgur.com") || domain.contains("redd.it"))) {
            if (directUrl.isNotBlank() && !directUrl.endsWith(".gifv") && !directUrl.endsWith(".mp4")) {
                validImageUrl = if (directUrl.contains("imgur.com") && !directUrl.contains(".")) {
                    "$directUrl.jpg"
                } else {
                    directUrl
                }
            }
        }

        if (validImageUrl.isNullOrBlank()) return null

        return RedditPost(
            id = id,
            subreddit = subreddit,
            title = title,
            author = author,
            imageUrl = validImageUrl,
            permalink = permalink,
            score = score,
            numComments = numComments,
            width = width,
            height = height
        )
    }

    private fun isImageExtension(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") ||
                lower.endsWith(".png") ||
                lower.endsWith(".webp") ||
                lower.contains("i.redd.it") ||
                lower.contains("i.imgur.com")
    }

    private fun String.unescapeHtml(): String {
        return this.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }
}
