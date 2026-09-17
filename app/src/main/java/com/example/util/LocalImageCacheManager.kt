package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.model.RedditPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Local image caching manager that stores subreddit album covers and wallpapers directly
 * on the device storage. This allows instant wallpaper setting and full offline operation.
 */
object LocalImageCacheManager {

    private const val TAG = "LocalImageCache"
    private const val CACHE_FOLDER = "wallpaper_cache"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun getCacheDir(context: Context): File {
        val dir = File(context.filesDir, CACHE_FOLDER)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCacheFileName(postId: String, imageUrl: String): String {
        val safeId = postId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
        val hash = (imageUrl.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        return "cache_${safeId}_$hash.jpg"
    }

    fun getCachedFile(context: Context, postId: String, imageUrl: String): File? {
        val dir = getCacheDir(context)
        val file = File(dir, getCacheFileName(postId, imageUrl))
        return if (file.exists() && file.length() > 0) file else null
    }

    fun isCached(context: Context, postId: String, imageUrl: String): Boolean {
        return getCachedFile(context, postId, imageUrl) != null
    }

    /**
     * Downloads an image from the network and stores it persistently on device storage.
     */
    suspend fun cacheImageLocally(context: Context, postId: String, imageUrl: String): File? = withContext(Dispatchers.IO) {
        if (imageUrl.isBlank()) return@withContext null

        val existing = getCachedFile(context, postId, imageUrl)
        if (existing != null) return@withContext existing

        try {
            val dir = getCacheDir(context)
            val tempFile = File(dir, "temp_${System.currentTimeMillis()}_${postId.hashCode()}.tmp")
            val targetFile = File(dir, getCacheFileName(postId, imageUrl))

            val request = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", "Mozilla/5.0 (Android TV; Wallpaper Cache)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val stream: InputStream = response.body?.byteStream() ?: return@withContext null
                    FileOutputStream(tempFile).use { output ->
                        stream.copyTo(output)
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        tempFile.renameTo(targetFile)
                        Log.d(TAG, "Imagen cacheada exitosamente: ${targetFile.name} (${targetFile.length()} bytes)")
                        return@withContext targetFile
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al cachear imagen $imageUrl: ${e.message}")
        }
        null
    }

    /**
     * Loads a Bitmap for a post, using the local device cache first.
     * If not found in cache, it downloads, saves to disk, and returns the Bitmap.
     */
    suspend fun loadBitmap(context: Context, post: RedditPost): Bitmap? = withContext(Dispatchers.IO) {
        // 1. Check local device cache
        val cachedFile = getCachedFile(context, post.id, post.imageUrl)
        if (cachedFile != null) {
            try {
                val bmp = BitmapFactory.decodeFile(cachedFile.absolutePath)
                if (bmp != null) {
                    Log.d(TAG, "Bitmap cargado DIRECTO desde caché local de dispositivo: ${cachedFile.name}")
                    return@withContext bmp
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error al decodificar archivo de caché: ${e.message}")
            }
        }

        // 2. Check local file path if provided
        if (!post.localFilePath.isNullOrBlank()) {
            val file = File(post.localFilePath)
            if (file.exists()) {
                try {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) return@withContext bmp
                } catch (_: Exception) {
                }
            }
        }

        // 3. Download, store on device, then decode
        val savedFile = cacheImageLocally(context, post.id, post.imageUrl)
        if (savedFile != null) {
            try {
                val bmp = BitmapFactory.decodeFile(savedFile.absolutePath)
                if (bmp != null) return@withContext bmp
            } catch (_: Exception) {
            }
        }

        // 4. Fallback direct download stream if storage write failed
        try {
            val request = Request.Builder()
                .url(post.imageUrl)
                .header("User-Agent", "Mozilla/5.0 (Android TV)")
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    resp.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error final al cargar bitmap: ${e.message}")
            null
        }
    }

    /**
     * Prefetches a list of posts into local disk storage in the background.
     */
    suspend fun prefetchPosts(context: Context, posts: List<RedditPost>) = withContext(Dispatchers.IO) {
        for (post in posts) {
            if (!isCached(context, post.id, post.imageUrl) && !post.isLocal) {
                cacheImageLocally(context, post.id, post.imageUrl)
            }
        }
    }

    fun getCacheCount(context: Context): Int {
        val dir = getCacheDir(context)
        return dir.listFiles()?.count { it.isFile && it.name.startsWith("cache_") } ?: 0
    }

    fun getCacheSizeMB(context: Context): String {
        val dir = getCacheDir(context)
        val bytes = dir.listFiles()?.sumOf { it.length() } ?: 0L
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    fun clearCache(context: Context): Boolean {
        val dir = getCacheDir(context)
        dir.listFiles()?.forEach { it.delete() }
        return true
    }
}
