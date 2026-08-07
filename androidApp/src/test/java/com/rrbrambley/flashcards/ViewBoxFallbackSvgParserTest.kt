package com.rrbrambley.flashcards

import coil3.Image
import coil3.annotation.ExperimentalCoilApi
import coil3.request.Options
import coil3.svg.Svg
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The SVG intrinsic-size policy behind the stretched Qatar flag (#363): prefer the document's
 * declared `width`/`height`, falling back to the view box only when there isn't one.
 */
@OptIn(ExperimentalCoilApi::class)
class ViewBoxFallbackSvgParserTest {

    /** flagcdn's Qatar flag: a 75x18 view box stretched onto a 1400x550 canvas. */
    @Test
    fun parse_keepsDeclaredSize_whenItDisagreesWithTheViewBox() {
        val svg = FakeSvg(declaredWidth = 1400f, declaredHeight = 550f, box = Svg.ViewBox(0f, 0f, 75f, 18f))

        val parsed = viewBoxFallbackSvgParser { svg }.parse(emptySource())

        // Sized 2.55:1 (the real flag), not the view box's 4.17:1.
        assertEquals(1400f, parsed.width, 0f)
        assertEquals(550f, parsed.height, 0f)
    }

    /** The eight flags (dk, no, bd, …) that ship a view box and no width/height. */
    @Test
    fun parse_adoptsTheViewBox_whenNoSizeIsDeclared() {
        val svg = FakeSvg(declaredWidth = -1f, declaredHeight = -1f, box = Svg.ViewBox(0f, 0f, 37f, 28f))

        val parsed = viewBoxFallbackSvgParser { svg }.parse(emptySource())

        assertEquals(37f, parsed.width, 0f)
        assertEquals(28f, parsed.height, 0f)
    }

    /** Nothing to fall back to: leave it alone and let the decoder use its own default. */
    @Test
    fun parse_leavesTheDocumentAlone_whenItHasNeitherSizeNorViewBox() {
        val svg = FakeSvg(declaredWidth = -1f, declaredHeight = -1f, box = null)

        val parsed = viewBoxFallbackSvgParser { svg }.parse(emptySource())

        assertEquals(-1f, parsed.width, 0f)
        assertEquals(-1f, parsed.height, 0f)
    }

    private fun emptySource(): BufferedSource = Buffer()

    private class FakeSvg(declaredWidth: Float, declaredHeight: Float, box: Svg.ViewBox?) : Svg {
        override var width: Float = declaredWidth
        override var height: Float = declaredHeight
        override var viewBox: Svg.ViewBox? = box

        override fun width(value: String) {
            width = value.toFloat()
        }

        override fun height(value: String) {
            height = value.toFloat()
        }

        override fun options(options: Options) = Unit

        override fun asImage(width: Int, height: Int): Image = error("not used")
    }
}
