package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.BorderStyle
import com.example.data.model.FeedSort
import com.example.data.model.RedditPost
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.RedditOrange
import com.example.util.HumorHelper
import com.example.util.LocalImageCacheManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WallpaperCanvasViewer(
    post: RedditPost?,
    borderColor: Color,
    borderStyle: BorderStyle,
    isTvMode: Boolean,
    showControls: Boolean,
    isAutoPlay: Boolean,
    isFavorite: Boolean,
    isSettingWallpaper: Boolean,
    currentIndex: Int,
    totalPosts: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTvMode: () -> Unit,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier,
    currentSort: FeedSort = FeedSort.TOP,
    onSelectSort: (FeedSort) -> Unit = {},
    isCachedLocally: Boolean = false,
    onOpenSubreddits: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentTimeString by remember { mutableStateOf("") }

    // Live clock for TV & Ambient viewing
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTimeString = sdf.format(Date())
            delay(1000L)
        }
    }

    // Auto-hide controls in TV mode after 6 seconds of inactivity
    LaunchedEffect(showControls, isTvMode) {
        if (showControls && isTvMode) {
            delay(6000L)
            onToggleControls()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onToggleControls()
            }
            .testTag("wallpaper_canvas_container")
    ) {
        // --- 1. Background Fill / Border Style (No-Crop Policy: Fills border with random color/palette) ---
        Crossfade(
            targetState = Pair(borderStyle, borderColor),
            animationSpec = tween(durationMillis = 600),
            modifier = Modifier.fillMaxSize(),
            label = "BackgroundBorderAnimation"
        ) { (style, color) ->
            when (style) {
                BorderStyle.RANDOM_VIBRANT, BorderStyle.DOMINANT_PALETTE, BorderStyle.SLATE_DARK -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        color.copy(alpha = 0.95f),
                                        color.copy(alpha = 0.70f),
                                        Color(0xFF090A10)
                                    )
                                )
                            )
                    )
                }
                BorderStyle.AMBIENT_BLUR -> {
                    if (post != null) {
                        val cachedFile = remember(post.id, post.imageUrl) {
                            LocalImageCacheManager.getCachedFile(context, post.id, post.imageUrl)
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(cachedFile ?: post.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(radius = 36.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        borderColor.copy(alpha = 0.90f),
                                        borderColor.copy(alpha = 0.65f),
                                        Color(0xFF0B0C12)
                                    )
                                )
                            )
                    )
                }
                BorderStyle.OLED_BLACK -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                }
            }
        }

        // --- 2. Main Picture Canvas (MANDATORY: Uncropped ContentScale.Fit, uses cached image from device if available) ---
        Crossfade(
            targetState = post,
            animationSpec = tween(durationMillis = 400),
            modifier = Modifier.fillMaxSize(),
            label = "PostCrossfade"
        ) { currentPost ->
            if (currentPost != null) {
                val cachedFile = remember(currentPost.id, currentPost.imageUrl) {
                    LocalImageCacheManager.getCachedFile(context, currentPost.id, currentPost.imageUrl)
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(cachedFile ?: currentPost.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = currentPost.title,
                        contentScale = ContentScale.Fit, // STRICT UN-CROPPED FIT
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isTvMode) 0.dp else 4.dp)
                            .shadow(elevation = 14.dp, shape = RoundedCornerShape(2.dp))
                            .testTag("main_uncropped_image")
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = RedditOrange)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Buscando fotos épicas de subreddits...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        // --- 3. Top Info Bar (Subreddit, Sort Switcher Top vs New, Humor Badge, Clock) ---
        AnimatedVisibility(
            visible = showControls || !isTvMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.90f),
                                Color.Black.copy(alpha = 0.60f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Subreddit badge & Sort Switcher [ 🏆 Top | ⏱️ New ]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Subreddit pill
                            Surface(
                                color = RedditOrange,
                                shape = RoundedCornerShape(12.dp),
                                shadowElevation = 4.dp,
                                modifier = Modifier.clickable { onOpenSubreddits() }
                            ) {
                                Text(
                                    text = if (post?.isLocal == true) "📱 ${post.subreddit}" else "r/${post?.subreddit ?: "reddit"}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            // Prominent Top vs New sorting toggle
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // TOP (Votes)
                                    Surface(
                                        color = if (currentSort == FeedSort.TOP) NeonCyan else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .clickable { onSelectSort(FeedSort.TOP) }
                                            .testTag("sort_toggle_top")
                                    ) {
                                        Text(
                                            text = "🏆 Top",
                                            color = if (currentSort == FeedSort.TOP) Color.Black else Color.White.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    // NEW (Chronological)
                                    Surface(
                                        color = if (currentSort == FeedSort.NEW) NeonCyan else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .clickable { onSelectSort(FeedSort.NEW) }
                                            .testTag("sort_toggle_new")
                                    ) {
                                        Text(
                                            text = "⏱️ New",
                                            color = if (currentSort == FeedSort.NEW) Color.Black else Color.White.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Offline cache indicator badge
                            if (isCachedLocally) {
                                Surface(
                                    color = Color(0xFF00E676).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Save,
                                            contentDescription = "En caché local",
                                            tint = Color(0xFF00E676),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Caché",
                                            color = Color(0xFF00E676),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Clock & TV Mode Toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = currentTimeString,
                                    color = NeonCyan,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = onToggleTvMode,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                    .testTag("tv_mode_button")
                            ) {
                                Icon(
                                    imageVector = if (isTvMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Modo TV / Pantalla Completa",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Title & Humorous Badge Description
                    if (post != null) {
                        Text(
                            text = post.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = NeonPurple.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = HumorHelper.getHumorousBadge(post),
                                    color = NeonCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "👍 ${post.score} votos • ${HumorHelper.getHumorousReaction(post)}",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // --- 4. Side Carousel Arrow Buttons (For TV Remote / Tablets / Quick Touch) ---
        AnimatedVisibility(
            visible = showControls || !isTvMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .testTag("prev_image_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Foto anterior",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showControls || !isTvMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            IconButton(
                onClick = onNext,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .testTag("next_image_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Foto siguiente",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // --- 5. Bottom Floating Action Pill Bar with Humor Descriptions ---
        AnimatedVisibility(
            visible = showControls || !isTvMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                // Humor subtitle description above bottom bar
                if (post != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = HumorHelper.getHumorousWallpaperDescription(post.id),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    color = Color(0xFF151722).copy(alpha = 0.94f),
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Index indicator
                        Text(
                            text = "${if (totalPosts > 0) currentIndex + 1 else 0} / $totalPosts",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 2.dp)
                        )

                        // AutoPlay Toggle
                        IconButton(
                            onClick = onTogglePlay,
                            modifier = Modifier
                                .background(
                                    if (isAutoPlay) RedditOrange else Color.White.copy(alpha = 0.15f),
                                    CircleShape
                                )
                                .testTag("autoplay_button")
                        ) {
                            Icon(
                                imageVector = if (isAutoPlay) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Reproducir / Pausar",
                                tint = Color.White
                            )
                        }

                        // Favorite Button
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .background(
                                    if (isFavorite) Color(0xFFFF1744) else Color.White.copy(alpha = 0.15f),
                                    CircleShape
                                )
                                .testTag("favorite_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = Color.White
                            )
                        }

                        // Set as Wallpaper Button (CRITICAL: Never crop, pad with random border color)
                        FilledTonalButton(
                            onClick = onSetWallpaper,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("set_wallpaper_button")
                        ) {
                            if (isSettingWallpaper) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = RedditOrange
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aplicando...", fontSize = 13.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Wallpaper,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Poner de Fondo", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Settings & Customization
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                .testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Ajustes & Subreddits",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
