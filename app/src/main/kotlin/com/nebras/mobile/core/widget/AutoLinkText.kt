package com.nebras.mobile.core.widget

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow

/**
 * **Global Linkify** — يكشف الروابط داخل نصوص الوصف ويفتحها بالمتصفح
 * الخارجيّ. يُستعمل في كلّ شاشات الوصف حتى تبقى التجربة موحَّدة.
 * نقل `core/widgets/auto_link_text.dart` (بديل `flutter_linkify` + `url_launcher`).
 */
@Composable
fun AutoLinkText(
    text: String?,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    val value = text.orEmpty()
    if (value.isEmpty()) return

    val context = LocalContext.current
    val baseStyle = style ?: MaterialTheme.typography.bodyMedium
    val primary = MaterialTheme.colorScheme.primary

    val annotated = buildAnnotatedString {
        var lastIndex = 0
        for (match in URL_REGEX.findAll(value)) {
            append(value.substring(lastIndex, match.range.first))
            val url = match.value
            withLink(
                LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = primary,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ) {
                    openExternalUrlSafely(context, url)
                },
            ) {
                append(url)
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < value.length) append(value.substring(lastIndex))
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = baseStyle,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign ?: TextAlign.Start,
    )
}

/** كاشف الروابط — يطابق http(s) وwww. مثل `UrlLinkifier` في Flutter. */
private val URL_REGEX = Regex(
    "(?:https?://|www\\.)[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
    RegexOption.IGNORE_CASE,
)

/**
 * حارس السلامة: نقبل http/https فقط وبمضيف غير فارغ — لا نفتح مخطّطات
 * أخرى (`file:`، `intent:`، …) قد تُستغلّ من نصّ وصف قادم من الخادم.
 */
fun isSafeExternalUrl(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    val normalized = normalizeUrl(raw.trim())
    val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return false
    return !uri.host.isNullOrEmpty()
}

fun openExternalUrlSafely(context: Context, raw: String?): Boolean {
    if (!isSafeExternalUrl(raw)) return false
    val uri = Uri.parse(normalizeUrl(raw!!.trim()))
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrElse { e ->
        if (e is ActivityNotFoundException) false else false
    }
}

/** `www.example.com` بلا مخطّط تُعامل كـ https (نفس سلوك `UrlLinkifier`). */
private fun normalizeUrl(raw: String): String =
    if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw

/** لبناء نصّ فيه رابط داخل `buildAnnotatedString` بأمان. */
private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withLink(
    link: LinkAnnotation,
    block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit,
) {
    val index = pushLink(link)
    try {
        block()
    } finally {
        pop(index)
    }
}

/** يبني [AnnotatedString] بروابط قابلة للنقر من نصّ خام (للاستعمال خارج Compose). */
fun buildLinkedText(value: String, linkColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        var lastIndex = 0
        for (match in URL_REGEX.findAll(value)) {
            append(value.substring(lastIndex, match.range.first))
            withLink(
                LinkAnnotation.Url(
                    url = match.value,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ),
            ) {
                append(match.value)
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < value.length) append(value.substring(lastIndex))
    }
