package com.nebras.mobile.feature.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.nebras.mobile.core.theme.NebrasTextStyles
import com.nebras.mobile.feature.auth.AuthViewModel
import com.nebras.mobile.feature.home.HomeViewModel
import java.util.Calendar

/**
 * نقل `core/widgets/greeting_section.dart` — تحيّة بحسب الوقت مع إيموجي،
 * وجملة وصفيّة ودّية (بلا مصادقة وبلا بيانات مستخدم)، وزرّ يفتح ورقة
 * الإعدادات.
 */
@Composable
fun GreetingSection(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    var settingsVisible by remember { mutableStateOf(false) }

    // نقرأ الساعة عند كلّ تركيب (مقابل `DateTime.now().hour` في build).
    // `Calendar` بدل `java.time` لأنّ minSdk = 23.
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = greetingFor(hour)
    val emoji = greetingEmojiFor(hour)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = greeting,
                    modifier = Modifier.weight(1f, fill = false),
                    style = NebrasTextStyles.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Text(text = emoji, style = TextStyle(fontSize = 16.sp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "تصفّح أرشيف المعرفة",
                style = NebrasTextStyles.heading3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { settingsVisible = true }) {
            Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
        }
    }

    if (settingsVisible) {
        ContentOptionsSheet(
            navController = navController,
            authViewModel = authViewModel,
            homeViewModel = homeViewModel,
            onDismissRequest = { settingsVisible = false },
        )
    }
}

/** نقل `_getGreeting`. */
private fun greetingFor(hour: Int): String = when {
    hour < 12 -> "صباح الخير"
    hour < 17 -> "مساء الخير"
    else -> "مساء الخير"
}

/** نقل `_getGreetingEmoji`. */
private fun greetingEmojiFor(hour: Int): String = when {
    hour < 12 -> "☀️"
    hour < 17 -> "👋"
    else -> "🌙"
}
