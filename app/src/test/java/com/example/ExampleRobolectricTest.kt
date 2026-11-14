package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.util.extractFirstUrl
import com.example.ui.util.formatDurationMs
import com.example.ui.util.isLikelyVideoUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VeloPlay", appName)
  }

  @Test
  fun `formatDurationMs and video URL helpers work`() {
    assertEquals("01:05", formatDurationMs(65_000L))
    assertEquals("1:01:05", formatDurationMs(3_665_000L))
    assertTrue(isLikelyVideoUrl("https://cdn.example.com/stream/playlist.m3u8?token=abc"))
    assertTrue(isLikelyVideoUrl("http://192.168.43.1:8080/stream"))
    assertFalse(isLikelyVideoUrl("https://example.com/index.html"))
    assertEquals(
      "https://example.com/clip.mp4",
      extractFirstUrl("Watch this https://example.com/clip.mp4 now")
    )
  }
}
