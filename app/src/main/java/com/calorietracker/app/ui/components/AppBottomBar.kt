package com.calorietracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.calorietracker.app.ui.navigation.Routes
import com.calorietracker.app.ui.theme.CalorieColors

private data class TabItem(val route: String, val label: String, val filled: ImageVector, val outlined: ImageVector)

private val TABS = listOf(
    TabItem(Routes.HOME, "首页", Icons.Filled.Home, Icons.Outlined.Home),
    TabItem(Routes.RECORD, "记录", Icons.Filled.FormatListBulleted, Icons.Outlined.FormatListBulleted),
    TabItem(Routes.STATS, "报表", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    TabItem(Routes.PROFILE, "我的", Icons.Filled.Person, Icons.Outlined.Person),
)

/** 底部导航：4 个 tab，选中态用主色，未选中用次文字色 */
@Composable
fun AppBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CalorieColors.Surface)
            .padding(top = 10.dp, bottom = 6.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEach { tab ->
            val selected = currentRoute == tab.route
            val color = if (selected) CalorieColors.Primary else CalorieColors.TextSecondary
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab.route) }
                    .padding(vertical = 4.dp),
            ) {
                Icon(
                    imageVector = if (selected) tab.filled else tab.outlined,
                    contentDescription = tab.label,
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
            }
        }
    }
}
