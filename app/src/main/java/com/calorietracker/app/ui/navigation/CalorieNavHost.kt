package com.calorietracker.app.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.calorietracker.app.ui.ai.AiSettingsScreen
import com.calorietracker.app.ui.camera.CameraScreen
import com.calorietracker.app.ui.components.AppBottomBar
import com.calorietracker.app.ui.confirm.ConfirmScreen
import com.calorietracker.app.ui.home.HomeScreen
import com.calorietracker.app.ui.manual.ManualEntryScreen
import com.calorietracker.app.ui.profile.ProfileScreen
import com.calorietracker.app.ui.quickinput.QuickInputScreen
import com.calorietracker.app.ui.record.RecordScreen
import com.calorietracker.app.ui.stats.StatsScreen
import com.calorietracker.app.ui.theme.CalorieColors

@Composable
fun CalorieNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTabRoute = currentRoute in Routes.tabs

    // 导航轨迹落日志：排查「为什么一进来是某某页」这类问题全靠它
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            Log.i(TAG, "route -> ${entry.destination.route} (start=${Routes.HOME})")
        }
    }

    Scaffold(
        containerColor = CalorieColors.Background,
        bottomBar = {
            // 拍照 / 结果 / 手动录入是全屏流程页，不显示底部导航
            if (isTabRoute) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.HOME) { inclusive = route == Routes.HOME }
                            launchSingleTop = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        // 手机竖屏下不产生任何变化；平板 / 折叠屏展开态 / 横屏（宽度 > 560dp）时
        // 不把内容拉满整屏，而是限制在一个舒适的阅读宽度并居中，
        // 顺带让正文远离横屏时的挖孔与曲面边缘。
        //
        // 键盘让位用 imePadding() 这个 **modifier**，而不是在组合期读 inset 的数值：
        // modifier 在布局测量阶段生效，键盘一动就会重新测量，是可靠响应式的
        // （组合期读 WindowInsets.isImeVisible 这类值在部分版本上并不可靠）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxSize(),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenCamera = { navController.navigateSingle(Routes.CAMERA) },
                        onOpenConfirm = { navController.navigateSingle(Routes.CONFIRM) },
                        onOpenQuickInput = { navController.navigateSingle(Routes.QUICK_INPUT) },
                        // 右上角头像走与底部 Tab 相同的路径，保证栈形一致（HOME → PROFILE）
                        onOpenProfile = { navController.navigateSingle(Routes.PROFILE) },
                    )
                }
                composable(Routes.RECORD) {
                    RecordScreen(
                        onOpenConfirm = { navController.navigateSingle(Routes.CONFIRM) },
                    )
                }
                composable(Routes.STATS) { StatsScreen() }
                composable(Routes.PROFILE) {
                    ProfileScreen(onOpenAiSettings = { navController.navigateSingle(Routes.AI_SETTINGS) })
                }

                composable(Routes.AI_SETTINGS) {
                    AiSettingsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.QUICK_INPUT) {
                    QuickInputScreen(
                        onBack = { navController.popBackStack() },
                        onOpenConfirm = { navController.navigateSingle(Routes.CONFIRM) },
                        onOpenAiSettings = { navController.navigateSingle(Routes.AI_SETTINGS) },
                    )
                }

                composable(Routes.CAMERA) {
                    CameraScreen(
                        onClose = { navController.popBackStack() },
                        onRecognitionFinished = {
                            navController.navigate(Routes.CONFIRM) {
                                popUpTo(Routes.CAMERA) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onManualEntry = {
                            navController.navigate(Routes.MANUAL) {
                                popUpTo(Routes.CAMERA) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onOpenAiSettings = { navController.navigateSingle(Routes.AI_SETTINGS) },
                    )
                }

                composable(Routes.CONFIRM) {
                    ConfirmScreen(
                        onBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Routes.HOME)
                            }
                        },
                        onRetake = {
                            navController.navigate(Routes.CAMERA) {
                                popUpTo(Routes.CONFIRM) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onAddManual = { navController.navigateSingle(Routes.MANUAL) },
                        onSaved = { navController.popBackStack(Routes.HOME, inclusive = false) },
                        onDeleted = { navController.popBackStack(Routes.HOME, inclusive = false) },
                    )
                }

                composable(Routes.MANUAL) {
                    ManualEntryScreen(
                        onBack = { navController.popBackStack() },
                        onItemAdded = {
                            // 从结果页进来的，退回结果页接着改；
                            // 从相机失败态直接进来的，返回栈里没有结果页，就必须补一个，
                            // 否则刚选好的食物会「凭空消失」。
                            val previous = navController.previousBackStackEntry?.destination?.route
                            if (previous == Routes.CONFIRM) {
                                navController.popBackStack()
                            } else {
                                navController.navigate(Routes.CONFIRM) {
                                    popUpTo(Routes.MANUAL) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 页面跳转统一走这里：`launchSingleTop` 保证连点两次不会把同一个页面压进返回栈两次
 * （压两次的话，返回键要按两下才退得出去，相机页的 popUpTo 也会漏掉一层）。
 */
private fun NavHostController.navigateSingle(route: String) {
    navigate(route) { launchSingleTop = true }
}

/** 大屏内容宽度上限：超过这个宽度就不再拉满，改为居中显示。 */
private val MaxContentWidth = 560.dp

private const val TAG = "CalorieNav"
