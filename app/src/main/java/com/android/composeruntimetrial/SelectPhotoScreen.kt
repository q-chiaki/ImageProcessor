package com.android.composeruntimetrial

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.android.imageprocesser.Mirror
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.android.imageprocesser.Blur
import com.android.imageprocesser.Brightness
import com.android.imageprocesser.Chain
import com.android.imageprocesser.ImageProcessingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * URI から画像データを読み取り、ByteArray に変換するサスペンド関数。
 * * @param context アプリケーションコンテキスト
 * @param uri 選択された画像データの URI
 * @return 変換された ByteArray、または失敗した場合は null
 */
suspend fun uriToByteArray(context: Context, uri: Uri): ByteArray? {
    return withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        try {
            // ContentResolver を使用して InputStream を開く
            contentResolver.openInputStream(uri)?.use { inputStream ->
                // InputStream の内容を全て読み取り、ByteArray に変換
                return@withContext inputStream.readBytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // エラーが発生した場合は null を返す
            return@withContext null
        }
    }
}

// =================================================================================
// 2. Compose コンポーネント (Picker の起動と状態管理)
// =================================================================================
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PhotoPickerToByteArraySample(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) } // ARGB生配列
    var isLoading by remember { mutableStateOf(false) }
    var isMirrored by remember { mutableStateOf(false) }
    var isBlurred by remember { mutableStateOf(false) }
    var brightness by remember { mutableIntStateOf(0) }
    var processedImage by remember { mutableStateOf<ByteArray?>(null) }

    processedImage = ImageProcessingContent(
        selectedImageBytes,
        width = selectedBitmap?.width ?: 0,
        height = selectedBitmap?.height ?: 0,
    ) {
        Chain {
            if (isMirrored) {
                println("======== Applying Mirror ========")
                Mirror()
            }
            if (isBlurred) {
                println("======== Applying Blur ========")
                Blur(
                    radius = { 12 }
                )
            }
            Brightness(
                brightness = { brightness },
            )
        }
    }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                isLoading = true
                coroutineScope.launch {
                    val bytes = uriToByteArray(context, uri)
                    if (bytes != null) {
                        // エンコード画像 → Bitmap → ARGB生配列
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        selectedBitmap = bitmap
                        selectedImageBytes = bitmapToArgbByteArray(bitmap) // 生ARGB配列に統一
                    }
                    isLoading = false
                }
            }
        }
    )

    Column(modifier = modifier) {
        Button(
            onClick = {
                pickMediaLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            enabled = !isLoading
        ) {
            Text("画像を選択 (Photo Picker)")
        }

        when {
            isLoading -> Text("画像を読み込み中...")
            selectedBitmap != null -> {
                Text("✅ 画像データ取得完了")

                selectedBitmap?.let { bitmap ->
                    Text("元の画像:")
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Original Image"
                    )
                }

                Row {
                    Text("ミラー: ")
                    ToggleButton(
                        checked = isMirrored,
                        onCheckedChange = { isMirrored = it }
                    ) {
                        Text(if (isMirrored) "ON" else "OFF")
                    }
                }

                Row {
                    Text("ぼかし: ")
                    ToggleButton(
                        checked = isBlurred,
                        onCheckedChange = { isBlurred = it }
                    ) {
                        Text(if (isBlurred) "ON" else "OFF")
                    }
                }

                Row {
                    Text("明るさ: $brightness ")
                    Slider(
                        value = brightness.toFloat(),
                        onValueChange = { brightness = it.toInt() },
                        valueRange = -100f..100f
                    )
                }

                processedImage?.let { bytes ->
                    selectedBitmap?.let { originalBitmap ->
                        println("======== Displaying Processed Image isChanged: ${processedImage.contentEquals(
                            selectedImageBytes
                        )} ========")
                        Text("処理済み画像:")
                        Image(
                            bitmap = argbByteArrayToBitmap(
                                bytes,
                                originalBitmap.width,
                                originalBitmap.height
                            ).asImageBitmap(),
                            contentDescription = "Processed Image"
                        )
                    }
                }
            }
            else -> Text("未選択")
        }
    }
}

// Bitmap を ARGB ByteArray に変換
fun bitmapToArgbByteArray(bitmap: Bitmap): ByteArray {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val byteArray = ByteArray(pixels.size * 4)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val offset = i * 4
        byteArray[offset] = (pixel shr 24 and 0xFF).toByte() // A
        byteArray[offset + 1] = (pixel shr 16 and 0xFF).toByte() // R
        byteArray[offset + 2] = (pixel shr 8 and 0xFF).toByte() // G
        byteArray[offset + 3] = (pixel and 0xFF).toByte() // B
    }
    return byteArray
}

// ARGB ByteArray を Bitmap に変換
fun argbByteArrayToBitmap(byteArray: ByteArray, width: Int, height: Int): Bitmap {
    val pixels = IntArray(width * height)
    for (i in pixels.indices) {
        val offset = i * 4
        val a = byteArray[offset].toInt() and 0xFF
        val r = byteArray[offset + 1].toInt() and 0xFF
        val g = byteArray[offset + 2].toInt() and 0xFF
        val b = byteArray[offset + 3].toInt() and 0xFF
        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}