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

class RedditAuthRequiredException(message: String) : Exception(message)

class RedditApiService {

    // Fast, strict timeouts to prevent any 120s hangs or freezing
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private val userAgent = "android:com.aistudio.redditwallpapers:v1.2.0 (by /u/tv_curator)"

    private val tokenLock = Any()
    private var cachedOAuthToken: String? = null
    private var cachedCredHash: String? = null
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
        AppLogger.i(tag, "Iniciando consulta para r/$cleanSub [Modo: ${sort.displayName} | after: ${after ?: "inicio"}]")

        val cleanId = clientId?.trim().orEmpty()
        val cleanSecret = clientSecret?.trim().orEmpty()

        // 1. If Reddit Client ID is provided, use official Reddit OAuth API
        if (cleanId.isNotBlank()) {
            AppLogger.i(tag, "Conectando vía Reddit OAuth oficial (oauth.reddit.com) para r/$cleanSub...")
            val oauthResult = fetchWithOAuth(cleanSub, sort, limit, after, cleanId, cleanSecret)
            if (oauthResult.isSuccess) {
                val page = oauthResult.getOrThrow()
                if (page.posts.isNotEmpty()) {
                    AppLogger.s(tag, "✓ Reddit OAuth retornó ${page.posts.size} fotos reales para r/$cleanSub (after: ${page.afterToken ?: "fin"})")
                } else {
                    AppLogger.w(tag, "Reddit OAuth respondió HTTP 200 pero sin imágenes en esta página de r/$cleanSub")
                }
                return@withContext Result.success(page)
            } else {
                val err = oauthResult.exceptionOrNull()?.message ?: "Error OAuth desconocido"
                AppLogger.e(tag, "Fallo en Reddit OAuth para r/$cleanSub: $err")
                return@withContext Result.failure(RedditAuthRequiredException("Reddit OAuth falló: $err"))
            }
        }

        // 2. Fallback anonymous endpoints (only used if no Reddit Client ID is configured)
        val queryParams = mutableListOf("limit=$limit", "raw_json=1")
        if (sort.timeFilter != null) {
            queryParams.add("t=${sort.timeFilter}")
        }
        if (!after.isNullOrBlank()) {
            queryParams.add("after=$after")
        }
        val queryString = queryParams.joinToString("&")

        val endpoints = listOf(
            "https://www.reddit.com/r/$cleanSub/${sort.endpointName}.json?$queryString",
            "https://api.reddit.com/r/$cleanSub/${sort.endpointName}.json?$queryString"
        )

        var is403Encountered = false
        var lastHttpError = ""

        for (targetUrl in endpoints) {
            val domain = if (targetUrl.contains("api.reddit.com")) "api.reddit.com" else "www.reddit.com"
            try {
                AppLogger.d(tag, "GET $domain -> $targetUrl")
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val code = response.code
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank() && body.trimStart().startsWith("{")) {
                            val page = parseRedditJsonResponse(body, cleanSub)
                            if (page.posts.isNotEmpty()) {
                                AppLogger.s(tag, "✓ $domain retornó ${page.posts.size} fotos reales para r/$cleanSub")
                                return@withContext Result.success(page)
                            }
                        }
                    } else {
                        lastHttpError = "HTTP $code"
                        if (code == 403 || code == 401) {
                            is403Encountered = true
                            AppLogger.w(tag, "Respuesta $code de $domain para r/$cleanSub (Reddit bloqueó consulta sin API key)")
                        } else {
                            AppLogger.w(tag, "Respuesta $code de $domain para r/$cleanSub")
                        }
                    }
                }
            } catch (e: Exception) {
                lastHttpError = e.message ?: e.javaClass.simpleName
                AppLogger.d(tag, "Excepción en $domain: ${e.message}")
            }
        }

        // 3. Fallback: Try Reddit RSS / Atom Feed
        try {
            val rssResult = fetchFromRssFeed(cleanSub, sort, limit)
            if (rssResult != null && rssResult.posts.isNotEmpty()) {
                AppLogger.s(tag, "✓ Canal RSS retornó ${rssResult.posts.size} fotos para r/$cleanSub")
                return@withContext Result.success(rssResult)
            }
        } catch (e: Exception) {
            AppLogger.d(tag, "Fallo en canal RSS: ${e.message}")
        }

        val finalMessage = if (is403Encountered) {
            "HTTP 403: Reddit bloqueó la petición sin API Key. Configura tu Client ID y Secret en Ajustes (🔑) o en Secrets."
        } else {
            "No se pudieron cargar fotos de r/$cleanSub ($lastHttpError)"
        }

        AppLogger.e(tag, finalMessage)
        Result.failure(RedditAuthRequiredException(finalMessage))
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
        if (isVideo) return null

        // Check self item first, then crosspost_parent_list[0] if crossposted
        val mediaPair = extractMediaFromItem(item)
            ?: item.optJSONArray("crosspost_parent_list")?.optJSONObject(0)?.let { extractMediaFromItem(it) }
            ?: return null

        val (validImageUrl, width, height) = mediaPair

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

    private fun extractMediaFromItem(item: JSONObject): Triple<String, Int, Int>? {
        if (item.optBoolean("is_video", false)) return null
        val domain = item.optString("domain", "")

        var validImageUrl: String? = null
        var width = 0
        var height = 0

        // 1. Direct url / url_overridden_by_dest
        val directUrl = item.optString("url_overridden_by_dest", item.optString("url", "")).unescapeHtml()
        if (isImageExtension(directUrl)) {
            validImageUrl = directUrl
        }

        // 2. Preview images
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

        // 3. Reddit gallery
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

        // 4. Imgur / i.redd.it check
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
        return Triple(validImageUrl, width, height)
    }

    private fun fetchWithOAuth(
        cleanSub: String,
        sort: FeedSort,
        limit: Int,
        after: String?,
        clientId: String,
        clientSecret: String
    ): Result<SubredditPage> {
        val tokenResult = getOAuthToken(clientId, clientSecret)
        if (tokenResult.isFailure) {
            return Result.failure(tokenResult.exceptionOrNull() ?: Exception("Error al autenticar con Reddit"))
        }
        val token = tokenResult.getOrThrow()

        val queryParams = mutableListOf("limit=$limit", "raw_json=1")
        if (sort.timeFilter != null) {
            queryParams.add("t=${sort.timeFilter}")
        }
        if (!after.isNullOrBlank()) {
            queryParams.add("after=$after")
        }
        val url = "https://oauth.reddit.com/r/$cleanSub/${sort.endpointName}?${queryParams.joinToString("&")}"

        return try {
            AppLogger.d("RedditOAuth", "GET oauth.reddit.com/r/$cleanSub/${sort.endpointName}?${queryParams.joinToString("&")}")
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", userAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        Result.success(parseRedditJsonResponse(body, cleanSub))
                    } else {
                        Result.failure(Exception("Cuerpo de respuesta vacío desde oauth.reddit.com"))
                    }
                } else {
                    if (response.code == 401) {
                        synchronized(tokenLock) {
                            cachedOAuthToken = null
                        }
                    }
                    Result.failure(Exception("HTTP ${response.code} desde oauth.reddit.com para r/$cleanSub"))
                }
            }
        } catch (e: Exception) {
            AppLogger.e("RedditOAuth", "Fallo de red llamando a oauth.reddit.com", e)
            Result.failure(e)
        }
    }

    private fun getOAuthToken(clientId: String, clientSecret: String): Result<String> {
        val credHash = "$clientId:$clientSecret".hashCode().toString()
        val now = System.currentTimeMillis()
        synchronized(tokenLock) {
            if (cachedOAuthToken != null && cachedCredHash == credHash && now < tokenExpiryTime) {
                return Result.success(cachedOAuthToken!!)
            }
        }

        return try {
            val credentials = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
            val formBuilder = FormBody.Builder()
            if (clientSecret.isNotBlank()) {
                formBuilder.add("grant_type", "client_credentials")
            } else {
                formBuilder.add("grant_type", "https://oauth.reddit.com/grants/installed_client")
                formBuilder.add("device_id", "DO_NOT_TRACK_THIS_DEVICE")
            }

            val request = Request.Builder()
                .url("https://www.reddit.com/api/v1/access_token")
                .header("Authorization", "Basic $credentials")
                .header("User-Agent", userAgent)
                .post(formBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val token = json.optString("access_token")
                    val expiresIn = json.optLong("expires_in", 3600)
                    if (token.isNotBlank()) {
                        synchronized(tokenLock) {
                            cachedOAuthToken = token
                            cachedCredHash = credHash
                            tokenExpiryTime = System.currentTimeMillis() + (expiresIn - 60) * 1000
                        }
                        AppLogger.s("RedditOAuth", "Token OAuth obtenido exitosamente (expira en ${expiresIn}s)")
                        Result.success(token)
                    } else {
                        val err = json.optString("error", "Token vacío")
                        Result.failure(Exception("Reddit OAuth rechazado: $err"))
                    }
                } else {
                    val err = "HTTP ${response.code} al pedir token OAuth"
                    AppLogger.e("RedditOAuth", err)
                    Result.failure(Exception(err))
                }
            }
        } catch (e: Exception) {
            AppLogger.e("RedditOAuth", "Excepción obteniendo token OAuth", e)
            Result.failure(e)
        }
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
