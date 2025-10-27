package com.android.imageprocesser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Bitmap.CompressFormat
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import java.io.ByteArrayOutputStream

@Composable
public fun Brightness(
    brightness: () -> Int,
) {
    val brightnessValue = brightness()
    val context = LocalImageContext.current ?: error("ImageContext not provided")

    ImageProcesserBlock(
        content = {},
        imageProcessor = BrightnessProcessor(
            brightness = { brightnessValue },
            width = { context.width },
            height = { context.height }
        )
    )
}

internal class BrightnessProcessor(
    private val brightness: () -> Int,
    private val width: () -> Int,
    private val height: () -> Int
) : ImageProcessor {
    override fun process(
        imageData: ByteArray,
        children: List<ImageProcessorNode>
    ): ByteArray {
        val isEncoded = detectFormat(imageData) != null
        return if (isEncoded) {
            adjustImageBrightness(imageData, brightness = brightness(), format = null, quality = 90)
        } else {
            adjustBrightnessRawArgb8888(
                input = imageData,
                width = width(),
                height = height(),
                brightness = brightness()
            )
        }
    }
}

private fun adjustBrightnessRawArgb8888(
    input: ByteArray,
    width: Int,
    height: Int,
    brightness: Int
): ByteArray {
    // 入力値のバリデーション
    require(input.size == width * height * 4) {
        "Input byte array size does not match width and height."
    }
    require(brightness in -255..255) {
        "Brightness value must be between -255 and 255."
    }

    // 結果を格納する新しいByteArrayを準備
    val output = ByteArray(input.size)

    // 全ピクセルをループ処理
    // iは各ピクセルの先頭(アルファチャンネル)のインデックス
    for (i in input.indices step 4) {
        // アルファチャンネルはそのままコピー
        output[i] = input[i]

        // 各色チャンネルの値を取得し、明るさを加算
        // .toInt() & 0xFF で符号なしの値(0-255)に変換してから計算
        val r = (input[i + 1].toInt() and 0xFF) + brightness
        val g = (input[i + 2].toInt() and 0xFF) + brightness
        val b = (input[i + 3].toInt() and 0xFF) + brightness

        // 計算結果を0-255の範囲に収め、Byteに変換して出力配列に格納
        output[i + 1] = r.coerceIn(0, 255).toByte() // 赤
        output[i + 2] = g.coerceIn(0, 255).toByte() // 緑
        output[i + 3] = b.coerceIn(0, 255).toByte() // 青
    }

    return output
}

/**
 * 画像の明るさを調整します。
 */
private fun adjustImageBrightness(
    input: ByteArray,
    brightness: Int,
    format: CompressFormat? = null,
    quality: Int = 90
): ByteArray {
    require(brightness in -255..255) { "brightness must be between -255 and 255" }
    require(input.isNotEmpty()) { "input ByteArray cannot be empty" }

    // 1) デコード
    val opts = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inMutable = true
        inJustDecodeBounds = false
        inSampleSize = 1
    }

    val originalBitmap = try {
        BitmapFactory.decodeByteArray(input, 0, input.size, opts)
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to decode image: ${e.message}", e)
    }

    if (originalBitmap == null) {
        val formatHint = detectFormat(input)
        throw IllegalArgumentException(
            "Failed to decode input image. " +
                    "Input size: ${input.size} bytes, " +
                    "Detected format: $formatHint, " +
                    "First 16 bytes: ${input.take(16).joinToString { "%02X".format(it) }}"
        )
    }

    // 2) 明るさ調整
    val adjustedBitmap = adjustBrightness(originalBitmap, brightness)

    // 3) 出力フォーマットを決定
    val detected = detectFormat(input)
    val outFormat = format ?: run {
        when {
            adjustedBitmap.hasAlpha() && detected != CompressFormat.WEBP -> CompressFormat.PNG
            else -> detected ?: CompressFormat.JPEG
        }
    }

    // 4) エンコード
    val baos = ByteArrayOutputStream()
    val success = adjustedBitmap.compress(outFormat, quality, baos)
    if (!success) {
        throw IllegalStateException("Failed to compress bitmap")
    }

    return baos.toByteArray()
}

/**
 * 明るさ調整
 */
private fun adjustBrightness(bitmap: Bitmap, brightness: Int): Bitmap {
    val b = brightness.toFloat()
    val cm = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, b, // R に +b
            0f, 1f, 0f, 0f, b, // G に +b
            0f, 0f, 1f, 0f, b, // B に +b
            0f, 0f, 0f, 1f, 0f  // A はそのまま
        )
    )
    val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(cm)
    }
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return result
}

/**
 * 入力バイト列の先頭からフォーマット推定
 */
internal fun detectFormat(bytes: ByteArray): CompressFormat? {
    if (bytes.size < 3) return null

    return when {
        // JPEG
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() ->
            CompressFormat.JPEG

        // PNG
        bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() && bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
                bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte() ->
            CompressFormat.PNG

        // WebP
        bytes.size >= 12 &&
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() &&
                bytes[3] == 0x46.toByte() && // "RIFF"
                bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> // "WEBP"
            CompressFormat.WEBP

        else -> null
    }
}