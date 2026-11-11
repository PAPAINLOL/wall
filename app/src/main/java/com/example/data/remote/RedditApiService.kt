package com.example.data.remote

import android.util.Base64
import com.example.data.model.FeedSort
import com.example.data.model.RedditPost
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class SubredditPage(
    val posts: List<RedditPost>,
    val afterToken: String?
)

class RedditApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    // Rotating User-Agents to prevent blocks
    private val userAgents = listOf(
        "android:com.aistudio.redditwallpapers:v1.1.0 (by /u/tv_ambient_curator)",
        "Mozilla/5.0 (Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    )

    private var cachedOAuthToken: String? = null
    private var tokenExpiryTime: Long = 0

    suspend fun fetchSubredditPosts(
        subredditName: String,
        sort: FeedSort = FeedSort.TOP,
        limit: Int = 50,
        after: String? = null,
        clientId: String? = null,
        clientSecret: String? = null
    ): Result<SubredditPage> = withContext(Dispatchers.IO) {
        val cleanSub = subredditName.trim().removePrefix("r/").removePrefix("/").trim()
        val tag = "RedditApi"
        AppLogger.i(tag, "Iniciando consulta para r/$cleanSub | Modo: ${sort.displayName} | Límite: $limit | After: ${after ?: "inicio"}")

        // 1. If user configured Reddit Client ID & Secret, try official OAuth endpoint first
        if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) {
            val oauthResult = fetchWithOAuth(cleanSub, sort, limit, after, clientId.trim(), clientSecret.trim())
            if (oauthResult != null && oauthResult.posts.isNotEmpty()) {
                AppLogger.s(tag, "Éxito con Reddit OAuth oficial para r/$cleanSub: ${oauthResult.posts.size} fotos")
                return@withContext Result.success(oauthResult)
            }
        }

        // Build list of JSON endpoints (official + reliable public mirrors)
        val queryParams = mutableListOf("limit=$limit", "raw_json=1")
        if (sort.timeFilter != null) {
            queryParams.add("t=${sort.timeFilter}")
        }
        if (!after.isNullOrBlank()) {
            queryParams.add("after=$after")
        }
        val queryString = queryParams.joinToString("&")

        val endpointsToTry = listOf(
            "https://www.reddit.com/r/$cleanSub/${sort.endpointName}.json?$queryString",
            "https://api.reddit.com/r/$cleanSub/${sort.endpointName}.json?$queryString",
            "https://safereddit.com/r/$cleanSub/${sort.endpointName}.json?$queryString",
            "https://redlib.tux.pizza/r/$cleanSub/${sort.endpointName}.json?$queryString",
            "https://redlib.ducks.party/r/$cleanSub/${sort.endpointName}.json?$queryString",
            "https://rl.bloat.cat/r/$cleanSub/${sort.endpointName}.json?$queryString"
        )

        var lastErrorMsg = "No se pudieron obtener imágenes de r/$cleanSub"

        // 2. Try JSON endpoints with rotating User-Agents
        for (targetUrl in endpointsToTry) {
            val domain = try { targetUrl.substringAfter("https://").substringBefore("/") } catch (e: Exception) { targetUrl }
            for (agent in userAgents) {
                try {
                    AppLogger.d(tag, "GET $domain -> $targetUrl")
                    val request = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", agent)
                        .header("Accept", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        val code = response.code
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank() && body.trimStart().startsWith("{")) {
                                val page = parseRedditJsonResponse(body, cleanSub)
                                if (page.posts.isNotEmpty()) {
                                    AppLogger.s(tag, "Éxito [$domain]: Obtenidas ${page.posts.size} imágenes válidas para r/$cleanSub (after: ${page.afterToken ?: "fin"})")
                                    return@withContext Result.success(page)
                                } else {
                                    AppLogger.w(tag, "JSON válido de $domain pero 0 imágenes encontradas (posts de solo texto o filtrados)")
                                }
                            }
                        } else {
                            lastErrorMsg = "HTTP $code en $domain"
                            AppLogger.w(tag, "Respuesta $code de $domain para r/$cleanSub")
                        }
                    }
                } catch (e: Exception) {
                    lastErrorMsg = "${e.javaClass.simpleName}: ${e.message}"
                    AppLogger.d(tag, "Fallo de conexión con $domain: ${e.message}")
                }
            }
        }

        // 3. Fallback: Try Reddit RSS / Atom Feed parser
        AppLogger.i(tag, "Intentando canal RSS de respaldo para r/$cleanSub...")
        val rssResult = fetchFromRssFeed(cleanSub, sort, limit)
        if (rssResult != null && rssResult.posts.isNotEmpty()) {
            AppLogger.s(tag, "Éxito con canal RSS para r/$cleanSub: ${rssResult.posts.size} fotos encontradas")
            return@withContext Result.success(rssResult)
        }

        AppLogger.e(tag, "Todas las fuentes fallaron para r/$cleanSub. Último error: $lastErrorMsg")
        Result.failure(Exception("Error al cargar r/$cleanSub: $lastErrorMsg"))
    }

    private fun parseRedditJsonResponse(jsonString: String, defaultSub: String): SubredditPage {
        val parsedPosts = mutableListOf<RedditPost>()
        var nextAfter: String? = null

        try {
            val root = JSONObject(jsonString)
            val data = root.optJSONObject("data")
            if (data != null) {
                nextAfter = data.optString("after", "").takeIf { it.isNotBlank() && it != "null" }
                val children = data.optJSONArray("children")
                if (children != null) {
                    for (i in 0 until children.length()) {
                        val item = children.optJSONObject(i)?.optJSONObject("data") ?: continue
                        val post = extractImagePost(item, defaultSub)
                        if (post != null) {
                            parsedPosts.add(post)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("RedditApi", "Error parseando JSON de Reddit", e)
        }

        return SubredditPage(posts = parsedPosts, afterToken = nextAfter)
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
        val domain = item.optString("domain", "")

        if (isVideo) return null

        var validImageUrl: String? = null
        var width = 0
        var height = 0

        // 1. Check direct url / url_overridden_by_dest
        val directUrl = item.optString("url_overridden_by_dest", item.optString("url", "")).unescapeHtml()
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

    private fun fetchFromRssFeed(cleanSub: String, sort: FeedSort, limit: Int): SubredditPage? {
        val rssUrl = "https://www.reddit.com/r/$cleanSub/${sort.endpointName}/.rss?limit=$limit"
        try {
            val request = Request.Builder()
                .url(rssUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/atom+xml,application/xml,text/xml")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val xml = response.body?.string() ?: return null
                return parseRssXml(xml, cleanSub)
            }
        } catch (e: Exception) {
            AppLogger.d("RedditRss", "Fallo RSS en r/$cleanSub: ${e.message}")
            return null
        }
    }

    private fun parseRssXml(xml: String, defaultSub: String): SubredditPage {
        val posts = mutableListOf<RedditPost>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inEntry = false
            var currentTitle = ""
            var currentContent = ""
            var currentAuthor = "anónimo"
            var currentId = ""
            var currentLink = ""

            val linkPattern = Pattern.compile("href=[\"'](https://i\\.redd\\.it/[^\"']+|https://preview\\.redd\\.it/[^\"']+|https://[^\"]+\\.(?:jpg|jpeg|png|webp))[\"']")

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name.equals("entry", ignoreCase = true)) {
                            inEntry = true
                            currentTitle = ""
                            currentContent = ""
                            currentAuthor = "anónimo"
                            currentId = ""
                            currentLink = ""
                        } else if (inEntry) {
                            when {
                                name.equals("title", ignoreCase = true) -> currentTitle = parser.nextText().unescapeHtml()
                                name.equals("content", ignoreCase = true) -> currentContent = parser.nextText()
                                name.equals("name", ignoreCase = true) -> currentAuthor = parser.nextText()
                                name.equals("id", ignoreCase = true) -> currentId = parser.nextText().substringAfterLast("/")
                                name.equals("link", ignoreCase = true) -> currentLink = parser.getAttributeValue(null, "href") ?: ""
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name.equals("entry", ignoreCase = true)) {
                            inEntry = false
                            val matcher = linkPattern.matcher(currentContent)
                            var imageUrl: String? = null
                            if (matcher.find()) {
                                imageUrl = matcher.group(1)?.unescapeHtml()
                            }
                            if (imageUrl != null && currentId.isNotBlank()) {
                                posts.add(
                                    RedditPost(
                                        id = currentId,
                                        subreddit = defaultSub,
                                        title = currentTitle.ifBlank { "Foto de r/$defaultSub" },
                                        author = currentAuthor,
                                        imageUrl = imageUrl,
                                        permalink = currentLink,
                                        score = 100
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            AppLogger.e("RedditRss", "Error parseando XML RSS", e)
        }
        return SubredditPage(posts = posts, afterToken = null)
    }

    private fun fetchWithOAuth(
        cleanSub: String,
        sort: FeedSort,
        limit: Int,
        after: String?,
        clientId: String,
        clientSecret: String
    ): SubredditPage? {
        val token = getOAuthToken(clientId, clientSecret) ?: return null
        val queryParams = mutableListOf("limit=$limit", "raw_json=1")
        if (sort.timeFilter != null) {
            queryParams.add("t=${sort.timeFilter}")
        }
        if (!after.isNullOrBlank()) {
            queryParams.add("after=$after")
        }
        val url = "https://oauth.reddit.com/r/$cleanSub/${sort.endpointName}?${queryParams.joinToString("&")}"

        return try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "android:com.aistudio.redditwallpapers:v1.1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        parseRedditJsonResponse(body, cleanSub)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            AppLogger.e("RedditOAuth", "Fallo al llamar a oauth.reddit.com", e)
            null
        }
    }

    private fun getOAuthToken(clientId: String, clientSecret: String): String? {
        val now = System.currentTimeMillis()
        if (cachedOAuthToken != null && now < tokenExpiryTime) {
            return cachedOAuthToken
        }

        return try {
            val credentials = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
            val formBody = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build()

            val request = Request.Builder()
                .url("https://www.reddit.com/api/v1/access_token")
                .header("Authorization", "Basic $credentials")
                .header("User-Agent", "android:com.aistudio.redditwallpapers:v1.1.0")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val token = json.optString("access_token")
                    val expiresIn = json.optLong("expires_in", 3600)
                    if (token.isNotBlank()) {
                        cachedOAuthToken = token
                        tokenExpiryTime = now + (expiresIn - 60) * 1000
                        AppLogger.s("RedditOAuth", "Token OAuth obtenido con éxito")
                        token
                    } else null
                } else {
                    AppLogger.w("RedditOAuth", "Error obteniendo token OAuth: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            AppLogger.e("RedditOAuth", "Excepción obteniendo token OAuth", e)
            null
        }
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
