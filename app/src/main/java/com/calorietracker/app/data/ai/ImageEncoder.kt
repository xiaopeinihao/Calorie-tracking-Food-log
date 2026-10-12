package com.calorietracker.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 上传给视觉模型前把照片压到合理尺寸。
 *
 * 现在手机拍一张就是 4~12MB，直接 base64 会变成 6~16MB 的请求体：
 * 上传慢、烧 token、还容易被服务商按体积拒绝。压到最长边 1024、质量 85，
 * 一般 100~300KB，识别效果几乎无差别。
 */
@Singleton
class ImageEncoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun toJpeg(uri: Uri, maxSide: Int = DEFAULT_MAX_SIDE, quality: Int = DEFAULT_QUALITY): ByteArray? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver

            // 第一步只读尺寸，避免把整张大图读进内存
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null

            val scaled = scaleDown(bitmap, maxSide)
            ByteArrayOutputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
                output.toByteArray()
            }
        }

    private fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= maxSide && height / (sample * 2) >= maxSide) {
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private companion object {
        const val DEFAULT_MAX_SIDE = 1024
        const val DEFAULT_QUALITY = 85
    }
}
