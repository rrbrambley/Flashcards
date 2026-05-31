package com.rrbrambley.flashcards

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import okhttp3.OkHttpClient

object FlashcardsImageLoaderFactory : SingletonImageLoader.Factory {
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
                add(SvgDecoder.Factory())
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
