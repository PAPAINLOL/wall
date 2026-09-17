package com.example.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.data.model.BorderStyle
import com.example.ui.theme.AestheticBorderColors
import kotlin.random.Random

object PaletteHelper {

    fun getBorderColorForPost(
        postId: String,
        style: BorderStyle,
        bitmap: Bitmap? = null
    ): Color {
        return when (style) {
            BorderStyle.RANDOM_VIBRANT -> {
                val seed = postId.hashCode()
                val randomIndex = kotlin.math.abs(seed) % AestheticBorderColors.size
                AestheticBorderColors[randomIndex]
            }
            BorderStyle.DOMINANT_PALETTE -> {
                if (bitmap != null) {
                    extractDominantColor(bitmap)
                } else {
                    val seed = postId.hashCode()
                    AestheticBorderColors[kotlin.math.abs(seed) % AestheticBorderColors.size]
                }
            }
            BorderStyle.OLED_BLACK -> Color.Black
            BorderStyle.SLATE_DARK -> Color(0xFF13141C)
            BorderStyle.AMBIENT_BLUR -> Color(0xFF1E202C)
        }
    }

    fun getRandomAestheticColor(): Color {
        return AestheticBorderColors[Random.nextInt(AestheticBorderColors.size)]
    }

    fun extractDominantColor(bitmap: Bitmap): Color {
        try {
            // Sample corner/border pixels or scaled down bitmap
            val sampleWidth = (bitmap.width / 8).coerceAtLeast(1)
            val sampleHeight = (bitmap.height / 8).coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, false)

            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var count = 0

            // Sample edges where the border touches
            for (x in 0 until sampleWidth) {
                val pTop = scaled.getPixel(x, 0)
                val pBot = scaled.getPixel(x, sampleHeight - 1)
                rSum += android.graphics.Color.red(pTop) + android.graphics.Color.red(pBot)
                gSum += android.graphics.Color.green(pTop) + android.graphics.Color.green(pBot)
                bSum += android.graphics.Color.blue(pTop) + android.graphics.Color.blue(pBot)
                count += 2
            }
            for (y in 0 until sampleHeight) {
                val pLeft = scaled.getPixel(0, y)
                val pRight = scaled.getPixel(sampleWidth - 1, y)
                rSum += android.graphics.Color.red(pLeft) + android.graphics.Color.red(pRight)
                gSum += android.graphics.Color.green(pLeft) + android.graphics.Color.green(pRight)
                bSum += android.graphics.Color.blue(pLeft) + android.graphics.Color.blue(pRight)
                count += 2
            }

            if (scaled != bitmap) {
                scaled.recycle()
            }

            if (count > 0) {
                val avgR = (rSum / count).toInt().coerceIn(0, 255)
                val avgG = (gSum / count).toInt().coerceIn(0, 255)
                val avgB = (bSum / count).toInt().coerceIn(0, 255)
                return Color(avgR, avgG, avgB)
            }
        } catch (_: Exception) {
        }
        return Color(0xFF1E202C)
    }
}
