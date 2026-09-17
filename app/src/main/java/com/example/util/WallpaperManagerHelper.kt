package com.example.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import com.example.data.model.BorderStyle
import com.example.data.model.RedditPost
import com.example.data.model.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

object WallpaperManagerHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun setPostAsWallpaper(
        context: Context,
        post: RedditPost,
        borderStyle: BorderStyle,
        target: WallpaperTarget
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Use cached image on the device (or cache it locally if first load)
            val originalBitmap = LocalImageCacheManager.loadBitmap(context, post)
                ?: return@withContext Result.failure(Exception("No se pudo cargar la imagen desde el almacenamiento local o red"))

            // Get target device resolution
            val wm = WallpaperManager.getInstance(context)
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = if (wm.desiredMinimumWidth > 0) wm.desiredMinimumWidth else displayMetrics.widthPixels
            val screenHeight = if (wm.desiredMinimumHeight > 0) wm.desiredMinimumHeight else displayMetrics.heightPixels

            val finalWidth = screenWidth.coerceAtLeast(1080)
            val finalHeight = screenHeight.coerceAtLeast(1920)

            // CRITICAL MANDATE: Never crop the photo! Fit entirely and pad borders with chosen style
            val framedWallpaperBitmap = createUncroppedFramedBitmap(
                srcBitmap = originalBitmap,
                targetWidth = finalWidth,
                targetHeight = finalHeight,
                borderStyle = borderStyle,
                postId = post.id
            )

            val wallpaperFlag = when (target) {
                WallpaperTarget.HOME_AND_LOCK -> {
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                WallpaperTarget.HOME_ONLY -> WallpaperManager.FLAG_SYSTEM
                WallpaperTarget.LOCK_ONLY -> WallpaperManager.FLAG_LOCK
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.setBitmap(framedWallpaperBitmap, null, true, wallpaperFlag)
            } else {
                wm.setBitmap(framedWallpaperBitmap)
            }

            originalBitmap.recycle()
            if (framedWallpaperBitmap != originalBitmap) {
                framedWallpaperBitmap.recycle()
            }

            Result.success("¡Fondo de pantalla aplicado exitosamente sin recortar la foto!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createUncroppedFramedBitmap(
        srcBitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        borderStyle: BorderStyle,
        postId: String
    ): Bitmap {
        val resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        // Calculate aspect fit dimensions
        val srcW = srcBitmap.width.toFloat()
        val srcH = srcBitmap.height.toFloat()

        val scale = Math.min(targetWidth / srcW, targetHeight / srcH)
        val destW = (srcW * scale).toInt()
        val destH = (srcH * scale).toInt()

        val destX = (targetWidth - destW) / 2
        val destY = (targetHeight - destH) / 2

        // Draw background according to user's requested border style
        val bgPaint = Paint().apply { isAntiAlias = true }
        when (borderStyle) {
            BorderStyle.RANDOM_VIBRANT, BorderStyle.DOMINANT_PALETTE, BorderStyle.SLATE_DARK -> {
                val color = PaletteHelper.getBorderColorForPost(postId, borderStyle, srcBitmap)
                bgPaint.color = color.toArgb()
                canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), bgPaint)
            }
            BorderStyle.OLED_BLACK -> {
                canvas.drawColor(AndroidColor.BLACK)
            }
            BorderStyle.AMBIENT_BLUR -> {
                // Scale src to fill and blur as ambient background
                try {
                    val blurredBg = Bitmap.createScaledBitmap(srcBitmap, 32, (32 * targetHeight / targetWidth).coerceAtLeast(32), true)
                    val bgDestRect = Rect(0, 0, targetWidth, targetHeight)
                    val bgSrcRect = Rect(0, 0, blurredBg.width, blurredBg.height)
                    val filterPaint = Paint().apply {
                        isFilterBitmap = true
                        isDither = true
                    }
                    canvas.drawBitmap(blurredBg, bgSrcRect, bgDestRect, filterPaint)
                    // Dim overlay
                    val dimPaint = Paint().apply {
                        color = AndroidColor.argb(120, 0, 0, 0)
                    }
                    canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), dimPaint)
                    blurredBg.recycle()
                } catch (_: Exception) {
                    canvas.drawColor(AndroidColor.BLACK)
                }
            }
        }

        // Draw the full uncropped image centered
        val destRect = Rect(destX, destY, destX + destW, destY + destH)
        val srcRect = Rect(0, 0, srcBitmap.width, srcBitmap.height)
        val imagePaint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
        canvas.drawBitmap(srcBitmap, srcRect, destRect, imagePaint)

        return resultBitmap
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android TV)")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val stream: InputStream? = response.body?.byteStream()
                    if (stream != null) {
                        BitmapFactory.decodeStream(stream)
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
