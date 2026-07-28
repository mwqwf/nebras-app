package com.nebras.mobile.feature.reader

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * عارض PDF أصليّ — **بديل `syncfusion_flutter_pdfviewer`** بلا أيّ تبعية
 * خارجيّة، مبنيّ على [PdfRenderer] المضمَّن في Android.
 *
 * ⚠️ **حماية الذاكرة**: نرسم الصفحة عند ظهورها فقط ونطلق الـ Bitmap فور
 * خروجها من العرض. رسم كلّ الصفحات مسبقاً كان يُنفد الكومة على ملفّات
 * كبيرة (نفس علّة OOM التي عالجناها في الصور).
 *
 * ⚠️ [PdfRenderer] **ليس آمناً للخيوط**: كلّ عمليّات الفتح/الرسم مقفولة على
 * كائن واحد كي لا يتصادم طلبا رسم متزامنان.
 */
@Composable
fun PdfViewer(
    file: File,
    initialPage: Int,
    onPageChanged: (page: Int, total: Int) -> Unit,
    nightMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val renderWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.toPx() }.toInt().coerceIn(320, 2048)
    }

    val document = remember(file.absolutePath) { PdfDocument(file) }
    DisposableEffect(document) { onDispose { document.close() } }

    val pageCount = document.pageCount
    val listState = rememberLazyListState()

    // استعادة آخر صفحة قراءة عند الفتح.
    LaunchedEffect(document, initialPage) {
        if (initialPage in 1..pageCount) listState.scrollToItem(initialPage - 1)
    }

    // حفظ الصفحة الحاليّة كلّما تغيّرت.
    LaunchedEffect(listState, pageCount) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            if (pageCount > 0) onPageChanged(index + 1, pageCount)
        }
    }

    // تكبير/تصغير بإيماءة القرص فوق كامل المستند.
    var scale by remember { mutableFloatStateOf(1f) }

    if (pageCount <= 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("تعذّر فتح الملف", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(if (nightMode) Color(0xFF121212) else Color(0xFFEFEFEF))
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale),
    ) {
        items(pageCount) { index ->
            PdfPage(
                document = document,
                index = index,
                widthPx = renderWidthPx,
                nightMode = nightMode,
            )
        }
    }
}

/** صفحة واحدة — تُرسم كسولاً عند ظهورها وتُحرَّر عند اختفائها. */
@Composable
private fun PdfPage(
    document: PdfDocument,
    index: Int,
    widthPx: Int,
    nightMode: Boolean,
) {
    val ratio = document.aspectRatio(index)
    val bitmapState = produceState<Bitmap?>(initialValue = null, index, widthPx) {
        value = withContext(Dispatchers.IO) { document.render(index, widthPx) }
    }
    val bitmap by bitmapState

    // تحرير الـ Bitmap عند خروج الصفحة من التركيب.
    DisposableEffect(bitmap) {
        onDispose { bitmap?.recycle() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .padding(vertical = 6.dp, horizontal = 8.dp)
            .background(if (nightMode) Color(0xFF1A1A1A) else Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp == null) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        } else {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "صفحة ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                // الوضع الليليّ: عكس الألوان (مصفوفة قلب القنوات).
                colorFilter = if (nightMode) NIGHT_FILTER else null,
            )
        }
    }
}

/** مرشّح عكس الألوان للوضع الليليّ — أبيض ↔ أسود مع إبقاء التباين. */
private val NIGHT_FILTER = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/**
 * غلاف آمن حول [PdfRenderer]: يفتح الملفّ مرّة واحدة، ويحمي كلّ وصول
 * بقفل (الصفّ لا يدعم التزامن)، ويغلق الموارد عند التخلّص.
 */
private class PdfDocument(file: File) {

    private val lock = Any()
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    val pageCount: Int

    private val ratios: FloatArray

    init {
        var count = 0
        var sizes = FloatArray(0)
        runCatching {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(fd)
            descriptor = fd
            renderer = r
            count = r.pageCount
            sizes = FloatArray(count) { i ->
                r.openPage(i).use { page ->
                    if (page.height > 0) {
                        page.width.toFloat() / page.height.toFloat()
                    } else {
                        0.707f
                    }
                }
            }
        }
        pageCount = count
        ratios = sizes
    }

    /** نسبة العرض/الارتفاع — A4 كافتراضيّ آمن عند التعذّر. */
    fun aspectRatio(index: Int): Float =
        ratios.getOrNull(index)?.takeIf { it > 0f } ?: 0.707f

    fun render(index: Int, widthPx: Int): Bitmap? = synchronized(lock) {
        val r = renderer ?: return null
        if (index !in 0 until r.pageCount) return null
        runCatching {
            r.openPage(index).use { page ->
                val height = (widthPx * page.height.toFloat() / page.width.toFloat()).toInt()
                    .coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                // خلفيّة بيضاء: صفحات PDF الشفّافة تظهر سوداء بدونها.
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }.getOrNull()
    }

    fun close() = synchronized(lock) {
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
    }
}
