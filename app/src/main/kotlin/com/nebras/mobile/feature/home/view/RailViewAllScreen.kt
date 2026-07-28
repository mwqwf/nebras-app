package com.nebras.mobile.feature.home.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nebras.mobile.core.model.Content
import com.nebras.mobile.core.widget.AppBarWidget
import com.nebras.mobile.core.widget.MediaContentCard
import com.nebras.mobile.feature.home.model.HomeRail

/**
 * قائمة عموديّة لكلّ عناصر رفّ أفقيّ — «اعرض الكلّ».
 * نقل `home/view/rail_view_all_screen.dart`.
 *
 * تُعرض بحالة محليّة من [HomeScreen] (لا مسار تنقّل مستقلّ)، لذلك تستقبل
 * [onBack] بدل الاعتماد على مكدّس التنقّل.
 */
@Composable
fun RailViewAllScreen(
    rail: HomeRail,
    onBack: () -> Unit,
    onItemTap: (Content) -> Unit,
) {
    // زرّ «رجوع» في النظام يُغلق الشاشة كما يفعل زرّ الشريط العلويّ.
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppBarWidget(title = rail.title, onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(rail.items, key = { it.id }) { item ->
                MediaContentCard(
                    content = item,
                    onTap = { onItemTap(item) },
                )
            }
        }
    }
}
