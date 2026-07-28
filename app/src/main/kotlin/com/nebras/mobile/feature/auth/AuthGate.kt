package com.nebras.mobile.feature.auth

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nebras.mobile.core.theme.NebrasColors

/**
 * نقل `features/auth/view/auth_gate.dart` — البوّابة التي يُوجَّه إليها
 * التطبيق بعد السبلاش.
 *
 * تختار بين:
 *   • [content]        — إذا كان هناك مستخدم مسجّل دخوله (أو ضيف).
 *   • [WelcomeScreen]  — إذا لم يكن.
 *   • شاشة لودر خفيفة — أثناء تحميل الجلسة من التخزين المحلّي/Google.
 *
 * نستخدم [Crossfade] (بديل `AnimatedSwitcher`) حتى يكون التبديل بين
 * الحالات سلساً بدل قفزة بصريّة فجّة. المنطق كلّه مُفوَّض لـ
 * `AuthViewModel.state.status`.
 */
@Composable
fun AuthGate(
    authViewModel: AuthViewModel,
    content: @Composable () -> Unit,
) {
    val auth by authViewModel.state.collectAsStateWithLifecycle()

    // مقابل `addPostFrameCallback` + حارس `_bootstrapCalled`: يُنفَّذ مرّة
    // واحدة لكلّ دخول لهذه الشجرة.
    LaunchedEffect(Unit) {
        authViewModel.bootstrap()
    }

    Crossfade(
        targetState = auth.status,
        animationSpec = tween(durationMillis = 280),
        label = "authGate",
    ) { status ->
        when (status) {
            AuthStatus.UNKNOWN -> AuthLoading()
            AuthStatus.SIGNED_IN -> content()
            AuthStatus.SIGNED_OUT -> WelcomeScreen(authViewModel = authViewModel)
        }
    }
}

/** شاشة لودر خفيفة أثناء تحديد حالة الجلسة — نقل `_AuthLoading`. */
@Composable
private fun AuthLoading() {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.4.dp,
                color = NebrasColors.primary,
            )
        }
    }
}
