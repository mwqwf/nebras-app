package com.nebras.mobile.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * دمج ملفّات MP3 — نقل حرفيّ لـ `core/utils/audio_merge.dart` (Kotlin خالص،
 * بلا مكتبة ترميز).
 *
 * الفكرة: MP3 تيّار إطارات مستقلّة، فيكفي **قصّ الوسوم** (ID3v2 في الرأس،
 * ID3v1 في الذيل، وإطار VBR الاستهلاليّ Xing/Info/VBRI) ثمّ لصق الإطارات
 * الصافية بالترتيب. نشترط تطابق «مواصفة التيّار» (الإصدار/الطبقة/معدّل
 * العيّنات/الأقنية) بين كلّ الملفّات وإلّا صار الناتج مشوّهاً.
 */
object AudioMerger {

    /** الحدّ الأقصى للملفّات في عمليّة دمج واحدة. */
    const val MAX_FILES = 10

    fun isMp3(fileName: String): Boolean =
        fileName.lowercase().trim().endsWith(".mp3")

    /**
     * يدمج [inputs] إلى [outputPath] ويُعيد الملفّ الناتج. عند أيّ فشل
     * يُحذف الناتج الجزئيّ ويُعاد رمي الخطأ.
     */
    suspend fun mergeMp3(
        inputs: List<File>,
        outputPath: String,
        maxTotalBytes: Long? = null,
        onProgress: ((percent: Double) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        require(inputs.isNotEmpty()) { "لا توجد ملفات للدمج" }
        require(inputs.size <= MAX_FILES) { "الحد الأقصى $MAX_FILES ملفات" }

        if (maxTotalBytes != null) {
            val totalBytes = inputs.sumOf { it.length() }
            if (totalBytes > maxTotalBytes) {
                throw IOException("الحجم الكلي للدمج كبير جداً")
            }
        }

        val out = File(outputPath)
        var streamSpec: Int? = null
        try {
            out.outputStream().buffered().use { sink ->
                for ((i, input) in inputs.withIndex()) {
                    val bytes = input.readBytes()
                    val frames = audioFramesOnly(bytes)
                    if (streamSpec == null) streamSpec = frames.spec
                    if (frames.spec != streamSpec) {
                        throw IOException("ملفات MP3 المختارة ليست بترميز صوتي متوافق")
                    }
                    sink.write(bytes, frames.start, frames.end - frames.start)
                    onProgress?.invoke((i + 1).toDouble() / inputs.size * 100)
                }
                sink.flush()
            }
        } catch (e: Throwable) {
            runCatching { if (out.exists()) out.delete() }
            throw e
        }
        out
    }

    /**
     * يحدّد مدى الإطارات الصوتيّة الصافية داخل [b] ويستخرج «مواصفة التيّار».
     * يقصّ: رأس ID3v2 (وذيله الاختياريّ)، وID3v1 من الذيل، وإطار VBR الأوّل.
     */
    private fun audioFramesOnly(b: ByteArray): Mp3Frames {
        var start = 0
        var end = b.size

        // رأس ID3v2: "ID3" + حجم مشفّر بسبع بتّات لكلّ بايت.
        if (end >= 10 &&
            b[0].toInt() and 0xFF == 0x49 &&
            b[1].toInt() and 0xFF == 0x44 &&
            b[2].toInt() and 0xFF == 0x33
        ) {
            val size = ((b[6].toInt() and 0x7F) shl 21) or
                ((b[7].toInt() and 0x7F) shl 14) or
                ((b[8].toInt() and 0x7F) shl 7) or
                (b[9].toInt() and 0x7F)
            var s = 10 + size
            if ((b[5].toInt() and 0x10) != 0) s += 10 // ذيل ID3v2 الاختياريّ
            if (s < end) start = s
        }

        // ذيل ID3v1 ("TAG" قبل آخر 128 بايت).
        if (end - start > 128 &&
            b[end - 128].toInt() and 0xFF == 0x54 &&
            b[end - 127].toInt() and 0xFF == 0x41 &&
            b[end - 126].toInt() and 0xFF == 0x47
        ) {
            end -= 128
        }

        // البحث عن أوّل مزامنة إطار صالحة (0xFF 0xEx) يتبعها إطار صالح.
        var p = start
        while (p + 4 <= end) {
            if (b[p].toInt() and 0xFF == 0xFF && (b[p + 1].toInt() and 0xE0) == 0xE0.toByte().toInt() and 0xE0) {
                val len = frameLength(b, p)
                if (len > 0) {
                    val next = p + len
                    if (next + 4 > end ||
                        (
                            b[next].toInt() and 0xFF == 0xFF &&
                                (b[next + 1].toInt() and 0xE0) == 0xE0.toByte().toInt() and 0xE0
                            )
                    ) {
                        break
                    }
                }
            }
            p++
        }
        if (p + 4 > end) throw IOException("لم يُعثر على صوت MPEG في الملف")
        start = p

        // إطار VBR الاستهلاليّ (Xing/Info/VBRI) ليس صوتاً — نتخطّاه.
        val firstLen = frameLength(b, start)
        if (firstLen > 0 && start + firstLen <= end) {
            if (hasVbrMarker(b, start, start + firstLen)) start += firstLen
        }
        if (start + 4 > end || frameLength(b, start) <= 0) {
            throw IOException("لم يُعثر على صوت MPEG صالح بعد الوسوم")
        }

        // مواصفة التيّار: الإصدار + الطبقة + معدّل العيّنات + نمط الأقنية.
        val h1 = b[start + 1].toInt() and 0xFF
        val h2 = b[start + 2].toInt() and 0xFF
        val h3 = b[start + 3].toInt() and 0xFF
        val spec = (((h1 shr 3) and 0x03) shl 8) or
            (((h1 shr 1) and 0x03) shl 6) or
            (((h2 shr 2) and 0x03) shl 4) or
            ((h3 shr 6) and 0x03)

        return Mp3Frames(start, end, spec)
    }

    private fun hasVbrMarker(b: ByteArray, from: Int, to: Int): Boolean {
        var i = from
        while (i + 4 <= to) {
            val c = b[i].toInt() and 0xFF
            val c1 = b[i + 1].toInt() and 0xFF
            val c2 = b[i + 2].toInt() and 0xFF
            val c3 = b[i + 3].toInt() and 0xFF
            if (c == 0x58 && c1 == 0x69 && c2 == 0x6E && c3 == 0x67) return true // Xing
            if (c == 0x49 && c1 == 0x6E && c2 == 0x66 && c3 == 0x6F) return true // Info
            if (c == 0x56 && c1 == 0x42 && c2 == 0x52 && c3 == 0x49) return true // VBRI
            i++
        }
        return false
    }

    private val SR_V1 = intArrayOf(44100, 48000, 32000)

    private val BR_V1_L1 = intArrayOf(
        0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448,
    )
    private val BR_V1_L2 = intArrayOf(
        0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384,
    )
    private val BR_V1_L3 = intArrayOf(
        0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320,
    )
    private val BR_V2_L1 = intArrayOf(
        0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256,
    )
    private val BR_V2_L23 = intArrayOf(
        0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160,
    )

    /** طول إطار MPEG بالبايت، أو -1 إن كان الرأس غير صالح. */
    private fun frameLength(b: ByteArray, p: Int): Int {
        if (p + 4 > b.size) return -1
        val h1 = b[p + 1].toInt() and 0xFF
        val h2 = b[p + 2].toInt() and 0xFF
        val version = (h1 shr 3) and 0x03 // 0=MPEG2.5 1=محجوز 2=MPEG2 3=MPEG1
        val layer = (h1 shr 1) and 0x03 // 1=III 2=II 3=I
        val brIdx = (h2 shr 4) and 0x0F
        val srIdx = (h2 shr 2) and 0x03
        val padding = (h2 shr 1) and 0x01
        if (version == 1 || layer == 0 || brIdx == 0 || brIdx == 15 || srIdx == 3) {
            return -1
        }

        val base = SR_V1[srIdx]
        val sampleRate = when (version) {
            3 -> base
            2 -> base / 2
            else -> base / 4
        }

        val isV1 = version == 3
        val table = when (layer) {
            3 -> if (isV1) BR_V1_L1 else BR_V2_L1 // Layer I
            2 -> if (isV1) BR_V1_L2 else BR_V2_L23 // Layer II
            else -> if (isV1) BR_V1_L3 else BR_V2_L23 // Layer III
        }

        val bitrate = table[brIdx] * 1000
        if (bitrate == 0) return -1

        if (layer == 3) return (12 * bitrate / sampleRate + padding) * 4
        val coef = if (layer == 1 && !isV1) 72 else 144
        return coef * bitrate / sampleRate + padding
    }

    private data class Mp3Frames(val start: Int, val end: Int, val spec: Int)
}
