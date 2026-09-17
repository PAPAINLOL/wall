package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun wallpaper_viewer_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        val samplePost = com.example.data.model.RedditPost(
          id = "test_1",
          subreddit = "albumartPorn",
          title = "Pink Floyd - The Dark Side of the Moon",
          author = "music_curator",
          imageUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17"
        )
        com.example.ui.components.WallpaperCanvasViewer(
          post = samplePost,
          borderColor = androidx.compose.ui.graphics.Color(0xFFFF4500),
          borderStyle = com.example.data.model.BorderStyle.RANDOM_VIBRANT,
          isTvMode = false,
          showControls = true,
          isAutoPlay = true,
          isFavorite = false,
          isSettingWallpaper = false,
          currentIndex = 0,
          totalPosts = 10,
          onNext = {},
          onPrevious = {},
          onTogglePlay = {},
          onToggleFavorite = {},
          onSetWallpaper = {},
          onOpenSettings = {},
          onToggleTvMode = {},
          onToggleControls = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
