package com.android.imageprocesser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import java.io.ByteArrayOutputStream

@Composable
public fun Blur(
    radius: () -> Int = { 12 }
) {
    val context = LocalImageContext.current ?: error("ImageContext not provided")
    val radiusValue = radius()

    Block(
        content = {},
        imageProcessor = BlurProcessor(
            width = { context.width },
            height = { context.height },
            radius = { radiusValue }
        )
    )
}

internal class BlurProcessor(
    private val width: () -> Int,
    private val height: () -> Int,
    private val radius: () -> Int
) : ImageProcessor {
    override fun process(
        imageData: ByteArray,
        children: List<ImageProcessorNode>
    ): ByteArray {
        val isEncoded = detectFormat(imageData) != null
        return if (isEncoded) {
            blurImageBytes(imageData, radius = radius(), format = null, quality = 90)
        } else {
            stackBlurRawArgb(
                input = imageData,
                width = width(),
                height = height(),
                radius = radius()
            )
        }
    }
    private fun blurImageBytes(
        imageData: ByteArray,
        radius: Int,
        format: String?, // "JPEG", "PNG", "WEBP" などを想定
        quality: Int
    ): ByteArray {

        println("======= BlurProcessor.blurImageBytes called =======")
        // 1. デコード
        // ARGB_8888 形式でデコードする (stackBlurRawArgb が4バイト/ピクセルを想定しているため)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val originalBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
            ?: throw IllegalArgumentException("Failed to decode image data")

        // 処理のために変更可能なBitmapコピーを作成
        val bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        originalBitmap.recycle() // 元のBitmapは解放

        val width = bitmap.width
        val height = bitmap.height
        val wh = width * height

        // 2. Bitmap(IntArray) -> ByteArray(ARGB) への変換
        // (stackBlurRawArgb が要求する形式に合わせる)
        val pix = IntArray(wh)
        bitmap.getPixels(pix, 0, width, 0, 0, width, height)

        val rawArgbBytes = ByteArray(wh * 4)
        run {
            var bi = 0
            var pi = 0
            while (pi < wh) {
                val argb = pix[pi]
                rawArgbBytes[bi + 0] = ((argb ushr 24) and 0xFF).toByte() // A
                rawArgbBytes[bi + 1] = ((argb ushr 16) and 0xFF).toByte() // R
                rawArgbBytes[bi + 2] = ((argb ushr 8) and 0xFF).toByte()  // G
                rawArgbBytes[bi + 3] = (argb and 0xFF).toByte()           // B
                bi += 4
                pi++
            }
        }

        // 3. ブラー処理の実行 (同じクラス内の private method を呼び出す)
        val blurredRawArgbBytes = stackBlurRawArgb(
            input = rawArgbBytes,
            width = width,
            height = height,
            radius = radius
        )

        // 4. ByteArray(ARGB) (ブラー済み) -> IntArray (ARGB) への変換
        // (Bitmap に書き戻すため)
        run {
            var bi = 0
            var pi = 0
            while (pi < wh) {
                val a = blurredRawArgbBytes[bi + 0].toInt() and 0xFF
                val r = blurredRawArgbBytes[bi + 1].toInt() and 0xFF
                val g = blurredRawArgbBytes[bi + 2].toInt() and 0xFF
                val b = blurredRawArgbBytes[bi + 3].toInt() and 0xFF
                pix[pi] = (a shl 24) or (r shl 16) or (g shl 8) or b
                bi += 4
                pi++
            }
        }

        // 5. IntArray (ブラー済み) -> Bitmap への書き戻し
        bitmap.setPixels(pix, 0, width, 0, 0, width, height)

        // 6. エンコード
        val outputStream = ByteArrayOutputStream()

        // format が null の場合のフォールバックを決定する
        // process 呼び出し元で quality = 90 が指定されているため、
        // null の場合は JPEG を意図している可能性が高いと判断します。
        val compressFormat: Bitmap.CompressFormat = when (format?.uppercase()) {
            "JPEG" -> Bitmap.CompressFormat.JPEG
            "PNG" -> Bitmap.CompressFormat.PNG
//            "WEBP", "WEBP_LOSSY" -> Bitmap.CompressFormat.WEBP_LOSSY
//            "WEBP_LOSSLESS" -> Bitmap.CompressFormat.WEBP_LOSSLESS
            else -> {
                // formatがnullの場合、デフォルトとしてJPEGを使用
                Bitmap.CompressFormat.JPEG
            }
        }

        bitmap.compress(compressFormat, quality, outputStream)

        // 7. リソース解放
        bitmap.recycle()

        println("======= BlurProcessor.blurImageBytes completed size: ${outputStream.toByteArray().size} =======")

        return outputStream.toByteArray()
    }

    private fun stackBlurRawArgb(
        input: ByteArray,
        width: Int,
        height: Int,
        radius: Int
    ): ByteArray {
        require(radius >= 1)
        require(input.size == width * height * 4)

        val w = width
        val h = height
        val wh = w * h

        // ByteArray(ARGB) -> IntArray(0xAARRGGBB)
        val pix = IntArray(wh)
        run {
            var bi = 0
            var pi = 0
            while (pi < wh) {
                val a = input[bi + 0].toInt() and 0xFF
                val r = input[bi + 1].toInt() and 0xFF
                val g = input[bi + 2].toInt() and 0xFF
                val b = input[bi + 3].toInt() and 0xFF
                pix[pi] = (a shl 24) or (r shl 16) or (g shl 8) or b
                bi += 4
                pi++
            }
        }

        val wm = w - 1
        val hm = h - 1
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        val vmin = IntArray(kotlin.math.max(w, h))

        val divsum = (div + 1) shr 1
        val divsumSq = divsum * divsum
        val dv = IntArray(256 * divsumSq).apply {
            var k = 0
            while (k < size) {
                this[k] = k / divsumSq
                k++
            }
        }

        yi = 0
        yp = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        // 横方向
        y = 0
        while (y < h) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0

            i = -radius
            while (i <= radius) {
                p = pix[yi + kotlin.math.min(wm, kotlin.math.max(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p shr 16) and 0xFF
                sir[1] = (p shr 8) and 0xFF
                sir[2] = p and 0xFF
                rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius

            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) vmin[x] = kotlin.math.min(x + radius + 1, wm)
                p = pix[yp + vmin[x]]

                sir[0] = (p shr 16) and 0xFF
                sir[1] = (p shr 8) and 0xFF
                sir[2] = p and 0xFF

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
                x++
            }
            yp += w
            y++
        }

        // 縦方向
        x = 0
        while (x < w) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0

            var yp2 = -radius * w
            i = -radius
            while (i <= radius) {
                yi = kotlin.math.max(0, yp2) + x

                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]

                rbs = r1 - kotlin.math.abs(i)

                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs

                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }

                if (i < hm) yp2 += w
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                val a = (pix[yi] ushr 24) and 0xFF
                pix[yi] = (a shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) vmin[y] = kotlin.math.min(y + r1, hm) * w
                p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
                y++
            }
            x++
        }

        // IntArray -> ByteArray(ARGB) に戻す
        val out = input.copyOf()
        run {
            var pi = 0
            var bi = 0
            while (pi < wh) {
                val argb = pix[pi]
                out[bi + 0] = ((argb ushr 24) and 0xFF).toByte() // A
                out[bi + 1] = ((argb ushr 16) and 0xFF).toByte() // R
                out[bi + 2] = ((argb ushr 8) and 0xFF).toByte()  // G
                out[bi + 3] = (argb and 0xFF).toByte()           // B
                bi += 4
                pi++
            }
        }
        return out
    }
}