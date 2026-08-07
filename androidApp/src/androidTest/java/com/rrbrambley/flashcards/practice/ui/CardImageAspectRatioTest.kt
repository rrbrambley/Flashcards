package com.rrbrambley.flashcards.practice.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import com.rrbrambley.flashcards.FlashcardsImageLoaderFactory
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Renders a real flag SVG through the app's real Coil [ImageLoader][coil3.ImageLoader] and measures
 * the flag in the resulting pixels — the regression guard for #363, where wide flags drew stretched.
 *
 * The bug was never in the layout: Coil sizes an SVG from its **view box** by default, and flagcdn's
 * Qatar flag declares `viewBox="0 0 75 18"` (4.17:1) with `width="1400" height="550"` (2.55:1) and
 * `preserveAspectRatio="none"`, so the raster itself came out 1.64x too wide. That's invisible to
 * every layout-level assertion, which is why this measures drawn pixels instead of node bounds.
 *
 * The flags are checked in (`androidTest/assets`) rather than fetched, so the test pins the exact
 * bytes that triggered the bug and needs no network.
 */
class CardImageAspectRatioTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        // The production loader — the SVG intrinsic-size policy under test lives in it.
        SingletonImageLoader.setSafe { FlashcardsImageLoaderFactory.newImageLoader(it) }
    }

    /**
     * Qatar: the flag whose view box and declared size disagree. Its maroon field starts 22/75 of the
     * way across (per the SVG's path), so the full width is derived from the maroon's.
     */
    @Test
    fun cardImage_drawsQatarAtItsDeclaredAspectRatio() {
        val image = renderFlag("flag-qa.svg", screenshotName = "card-image-qatar")

        val maroon = image.boundsOfColor(Color(0xFF8A1538))
        val flagWidth = maroon.width / (1f - 22f / 75f)

        assertAspectRatio(expected = 1400f / 550f, actual = flagWidth / maroon.height, label = "Qatar")
    }

    /**
     * Denmark declares *no* width/height, only a view box — the case `viewBoxFallbackSvgParser` keeps
     * working. Its red field covers the whole flag, so the red bounds are the flag's.
     */
    @Test
    fun cardImage_drawsDenmarkAtItsViewBoxAspectRatio() {
        val image = renderFlag("flag-dk.svg", screenshotName = "card-image-denmark")

        val red = image.boundsOfColor(Color(0xFFC8102E))

        assertAspectRatio(expected = 37f / 28f, actual = red.width / red.height, label = "Denmark")
    }

    /** Renders [asset] in a [CardImage] sized like the practice prompt, and saves the pixels. */
    private fun renderFlag(asset: String, screenshotName: String): ImageBitmap {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open(asset).use { it.readBytes() }

        var resolved = false
        composeTestRule.setContent {
            FlashcardsTheme {
                CardImage(
                    model = bytes,
                    contentDescription = asset,
                    onResolved = { resolved = true },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                )
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 10_000) { resolved }
        composeTestRule.waitForIdle()

        return composeTestRule.onRoot().captureToImage().also { save(it, screenshotName) }
    }

    /**
     * The tightest rectangle covering every pixel close to [color]. The tolerance absorbs the
     * anti-aliased edge and the 1dp outline drawn just inside the image's bounds.
     */
    private fun ImageBitmap.boundsOfColor(color: Color, tolerance: Float = 0.08f): Bounds {
        val pixels = toPixelMap()
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                val matches = abs(pixel.red - color.red) < tolerance &&
                    abs(pixel.green - color.green) < tolerance &&
                    abs(pixel.blue - color.blue) < tolerance
                if (matches) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        assertTrue("Found no pixels matching $color — did the image render?", right >= left)
        return Bounds(width = (right - left + 1).toFloat(), height = (bottom - top + 1).toFloat())
    }

    private fun assertAspectRatio(expected: Float, actual: Float, label: String) {
        val drift = abs(actual - expected) / expected
        assertTrue(
            "$label drew at ${"%.3f".format(actual)}:1, expected ${"%.3f".format(expected)}:1 " +
                "(${"%.1f".format(drift * 100)}% off)",
            drift < 0.06f,
        )
    }

    /**
     * Writes the render out so CI can keep it as an artifact — a visual fix deserves something a human
     * can look at, not just a passing assertion.
     *
     * Prefers the directory AGP passes as `additionalTestOutputDir`, which it pulls off the device
     * itself; falls back to the app's external files dir (which the workflow pulls by hand). Logs the
     * path either way, so a run that produces no artifact says where it actually wrote.
     */
    private fun save(image: ImageBitmap, name: String) {
        val agpOutputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val dir = if (agpOutputDir != null) {
            File(agpOutputDir)
        } else {
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots")
        }
        dir.mkdirs()
        val file = File(dir, "$name.png")
        file.outputStream().use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        Log.i("CardImageRender", "Wrote $name to ${file.absolutePath}")
    }

    private data class Bounds(val width: Float, val height: Float)
}
