package com.rrbrambley.flashcards

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.Svg
import coil3.svg.SvgDecoder
import coil3.svg.height
import coil3.svg.width
import coil3.util.DebugLogger
import okhttp3.OkHttpClient

object FlashcardsImageLoaderFactory : SingletonImageLoader.Factory {
    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: Context): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(context.applicationContext)
            .logger(DebugLogger())
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
                add(
                    SvgDecoder.Factory(
                        parser = viewBoxFallbackSvgParser(),
                        useViewBoundsAsIntrinsicSize = false,
                    ),
                )
            }
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .maxSizeBytes(50 * 1024 * 1024)
                    .build()
            }
            .build()
    }
}

/**
 * An SVG parser that guarantees the document declares a usable width/height, so [SvgDecoder] can be
 * configured with `useViewBoundsAsIntrinsicSize = false`.
 *
 * Coil sizes an SVG from its **view box** by default, but a view box is a coordinate system, not a
 * rendered size: with `preserveAspectRatio="none"` the two legitimately have different aspect
 * ratios, and it's `width`/`height` that say how the art is meant to come out. flagcdn's Qatar flag
 * is exactly that — `viewBox="0 0 75 18"` (4.17:1) with `width="1400" height="550"` (2.55:1, the
 * real flag) — so sizing off the view box rasterized the bitmap 1.64x too wide and the flag drew
 * stretched (#363). Qatar is the only one of the 252 seeded flags whose two ratios disagree; for the
 * rest this is a no-op, since the decoder fits the same source ratio into the same requested size.
 *
 * Eight flags (bd, bv, cx, dk, ki, no, sj, uy) ship a view box and *no* width/height — there
 * `documentWidth` is -1 and the decoder would fall back to a square default and distort them, so
 * copy the view box into the declared size, which is what the view-box default already did for them.
 */
@OptIn(ExperimentalCoilApi::class)
internal fun viewBoxFallbackSvgParser(delegate: Svg.Parser = Svg.Parser.DEFAULT): Svg.Parser = Svg.Parser { source ->
    delegate.parse(source).also { svg ->
        if (svg.width <= 0f || svg.height <= 0f) {
            svg.viewBox?.let { viewBox ->
                svg.width(viewBox.width.toString())
                svg.height(viewBox.height.toString())
            }
        }
    }
}
