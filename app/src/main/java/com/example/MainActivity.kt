package com.example

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.FavoritesAndGallerySheet
import com.example.ui.components.SettingsSheet
import com.example.ui.components.SubredditsSheet
import com.example.ui.components.WallpaperCanvasViewer
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.WallpaperViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: WallpaperViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    WallpaperAppContent(viewModel = viewModel)
                }
            }
        }
    }

    // Android TV Remote Control Support (DPAD & Media keys)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> {
                viewModel.nextPost()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                viewModel.previousPost()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_ENTER -> {
                viewModel.toggleAutoPlay()
                true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MENU -> {
                viewModel.toggleTvAmbientMode()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.toggleControlsInTvMode()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

@Composable
fun WallpaperAppContent(viewModel: WallpaperViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSubredditsSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showGallerySheet by remember { mutableStateOf(false) }

    // Photo picker launcher for picking from Android local gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.addLocalImage(uri, "Foto añadida desde galería")
        }
    }

    // Handle toast events
    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val currentPost = uiState.currentPost
    val isFavorite = currentPost != null && uiState.favorites.any { it.id == currentPost.id }

    WallpaperCanvasViewer(
        post = currentPost,
        borderColor = uiState.currentBorderColor,
        borderStyle = uiState.settings.borderStyle,
        isTvMode = uiState.isTvAmbientMode,
        showControls = uiState.showControlsInTvMode,
        isAutoPlay = uiState.settings.isAutoPlay,
        isFavorite = isFavorite,
        isSettingWallpaper = uiState.isSettingWallpaper,
        currentIndex = uiState.currentIndex,
        totalPosts = uiState.posts.size,
        onNext = { viewModel.nextPost() },
        onPrevious = { viewModel.previousPost() },
        onTogglePlay = { viewModel.toggleAutoPlay() },
        onToggleFavorite = { viewModel.toggleFavoriteCurrent() },
        onSetWallpaper = { viewModel.applyCurrentAsWallpaper() },
        onOpenSettings = { showSettingsSheet = true },
        onToggleTvMode = { viewModel.toggleTvAmbientMode() },
        onToggleControls = { viewModel.toggleControlsInTvMode() },
        currentSort = uiState.settings.feedSort,
        onSelectSort = { viewModel.setFeedSort(it) },
        isCachedLocally = uiState.isCurrentPostCached,
        onOpenSubreddits = { showSubredditsSheet = true }
    )

    // Bottom Sheets
    if (showSettingsSheet) {
        SettingsSheet(
            settings = uiState.settings,
            onSetInterval = { viewModel.setInterval(it) },
            onSetBorderStyle = { viewModel.setBorderStyle(it) },
            onSetFeedSort = { viewModel.setFeedSort(it) },
            onSetWallpaperTarget = { viewModel.setWallpaperTarget(it) },
            onTogglePreventDuplicates = { viewModel.togglePreventDuplicates(it) },
            onToggleAutoSetWallpaper = { viewModel.toggleAutoSetWallpaperOnSlide(it) },
            onPickGalleryImage = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onOpenSubreddits = { showSubredditsSheet = true },
            onDismiss = { showSettingsSheet = false },
            cachedCount = uiState.cachedImagesCount,
            cacheSize = uiState.cacheSizeString,
            onCacheAll = { viewModel.cacheAllCurrentImages() },
            onClearCache = { viewModel.clearLocalCache() }
        )
    }

    if (showSubredditsSheet) {
        SubredditsSheet(
            subreddits = uiState.subreddits,
            onToggleSubreddit = { name, enabled -> viewModel.toggleSubreddit(name, enabled) },
            onAddSubreddit = { name, disp, emoji -> viewModel.addCustomSubreddit(name, disp, emoji) },
            onAddMultipleSubreddits = { input -> viewModel.addMultipleSubreddits(input) },
            onDeleteSubreddit = { name -> viewModel.deleteSubreddit(name) },
            onRestoreDefaultSubreddits = { viewModel.restoreDefaultSubreddits() },
            onRefreshPosts = { viewModel.refreshPosts() },
            onDismiss = { showSubredditsSheet = false }
        )
    }

    if (showGallerySheet) {
        FavoritesAndGallerySheet(
            favorites = uiState.favorites,
            allPosts = uiState.posts,
            onSelectPost = { index -> viewModel.jumpToPost(index) },
            onDismiss = { showGallerySheet = false }
        )
    }
}
