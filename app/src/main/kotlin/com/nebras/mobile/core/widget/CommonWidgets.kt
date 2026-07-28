package com.nebras.mobile.core.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.nebras.mobile.core.theme.NebrasColors
import kotlinx.coroutines.delay

/**
 * الويدجتات المشتركة — تجميع لملفّات `core/widgets` الصغيرة في ملفّ واحد
 * (في Compose هذه دوالّ لا أصناف، فتفريقها على ملفّات ليس أنظف).
 */

// ── حالة الفراغ ─────────────────────────────────────────────────────

/** نقل `empty_state_widget.dart` — رسم Lottie + رسالة + زرّ إعادة محاولة. */
@Composable
fun EmptyStateWidget(
    message: String,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("lottie/empty.json"),
    )
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever,
    )

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.height(height),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(actionLabel ?: "إعادة المحاولة")
            }
        }
    }
}

// ── هياكل التحميل (Skeletons) ───────────────────────────────────────

/** مستطيل رماديّ نابض — لبنة كلّ هياكل التحميل. */
@Composable
private fun SkeletonBox(
    width: Dp?,
    height: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    isCircle: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val color = if (isSystemInDarkTheme()) Color(0xFF424242) else Color(0xFFEEEEEE)
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .alpha(alpha)
            .clip(if (isCircle) CircleShape else RoundedCornerShape(cornerRadius))
            .background(color),
    )
}

@Composable
fun HomeSkeletonLoader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        repeat(2) { rail ->
            SkeletonBox(width = if (rail == 0) 120.dp else 100.dp, height = 18.dp)
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(3) {
                    SkeletonBox(width = 160.dp, height = 190.dp, cornerRadius = 16.dp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SearchSkeletonLoader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 24.dp)) {
        repeat(5) {
            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(width = 64.dp, height = 64.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBox(width = null, height = 14.dp)
                    Spacer(Modifier.height(8.dp))
                    SkeletonBox(width = 100.dp, height = 12.dp)
                }
            }
        }
    }
}

@Composable
fun NotificationSkeletonLoader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 24.dp)) {
        repeat(6) {
            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(width = 44.dp, height = 44.dp, isCircle = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBox(width = null, height = 14.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBox(width = 180.dp, height = 12.dp)
                    Spacer(Modifier.height(4.dp))
                    SkeletonBox(width = 60.dp, height = 10.dp)
                }
            }
        }
    }
}

@Composable
fun SavedSkeletonLoader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 24.dp)) {
        repeat(5) {
            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(width = 80.dp, height = 80.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBox(width = null, height = 14.dp)
                    Spacer(Modifier.height(8.dp))
                    SkeletonBox(width = 80.dp, height = 12.dp)
                }
            }
        }
    }
}

// ── ظهور تدريجيّ لعناصر القائمة ─────────────────────────────────────

/**
 * نقل `fade_in_list_item.dart` — ظهور + انزلاق خفيف مع تأخير متدرّج
 * بحسب موضع العنصر في القائمة.
 */
@Composable
fun FadeInListItem(
    index: Int = 0,
    delayPerItemMillis: Long = 50,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay(delayPerItemMillis * index)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 20 },
    ) {
        content()
    }
}

// ── أزرار وحقول ─────────────────────────────────────────────────────

/** نقل `button_widget.dart`. */
@Composable
fun ButtonWidget(
    title: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    cornerRadius: Dp = 16.dp,
    color: Color = NebrasColors.primary,
) {
    Button(
        onClick = onTap,
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(cornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

/** نقل `custom_textfield_widget.dart`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextFieldWidget(
    value: String,
    onValueChange: (String) -> Unit,
    hintText: String,
    modifier: Modifier = Modifier,
    obscureText: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(hintText, style = MaterialTheme.typography.bodyMedium) },
        trailingIcon = trailingIcon,
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (obscureText) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = NebrasColors.primary,
        ),
    )
}

/** نقل `search_baar_widget.dart`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarWidget(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    onSubmitted: ((String) -> Unit)? = null,
    hintText: String = "ابحث عن محتوى...",
) {
    val isActive = query.isNotEmpty()
    val primary = MaterialTheme.colorScheme.primary

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(hintText) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = if (isActive) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        },
        trailingIcon = {
            if (isActive) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "مسح",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSubmitted?.invoke(query) },
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
        ),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = primary.copy(alpha = 0.3f),
        ),
    )
}

// ── شريط علويّ ──────────────────────────────────────────────────────

/** نقل `app_bar_widget.dart` — شريط بسيط بعنوان وزرّ رجوع اختياريّ. */
@Composable
fun AppBarWidget(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconPressed: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIcon != null) {
            IconButton(onClick = { onTrailingIconPressed?.invoke() }) {
                Icon(trailingIcon, contentDescription = null)
            }
        }
        actions?.invoke()
    }
}

// ── عناصر متفرّقة ───────────────────────────────────────────────────

/** نقل `settings_tile.dart`. */
@Composable
fun SettingsTile(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onTap: (() -> Unit)? = null,
) {
    ListItem(
        modifier = modifier.then(
            if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier,
        ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(icon, contentDescription = null, tint = NebrasColors.primary)
        },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        trailingContent = trailing ?: {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        },
    )
}

/** نقل `build_handel.dart` — مقبض الورقة السفليّة. */
@Composable
fun BottomSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(40.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(NebrasColors.greycolor.copy(alpha = 0.2f)),
    )
}

/**
 * نقل `hidden_tap_detector.dart` — يُطلق [onTriggered] بعد [requiredTaps]
 * نقرات متتالية خلال نافذة زمنيّة قصيرة (مدخل مخفيّ).
 */
@Composable
fun HiddenTapDetector(
    requiredTaps: Int,
    onTriggered: () -> Unit,
    modifier: Modifier = Modifier,
    resetAfterMillis: Long = 2_000,
    content: @Composable () -> Unit,
) {
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        delay(resetAfterMillis)
        taps = 0
    }
    Box(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember {
                androidx.compose.foundation.interaction.MutableInteractionSource()
            },
        ) {
            taps += 1
            if (taps >= requiredTaps) {
                taps = 0
                onTriggered()
            }
        },
    ) {
        content()
    }
}

/** نقل `section_grid_tile.dart` — بطاقة قسم في الشبكة. */
@Composable
fun SectionGridTile(
    title: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onTap)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)),
        ) {
            SmartNetworkImage(
                imageUrl = imageUrl,
                title = title,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
            ),
        )
    }
}

/** نصّ عنوان قسم داخل الشاشات (عنوان رفّ/مجموعة). */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** نمط نصّ التلميح الموحَّد (بديل `TextStyles.hintText`). */
val hintTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(
        color = NebrasColors.greycolor,
    )
