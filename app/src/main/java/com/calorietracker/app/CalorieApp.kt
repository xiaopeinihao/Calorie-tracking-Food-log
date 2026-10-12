package com.calorietracker.app

import android.app.Application
import android.util.Log
import com.calorietracker.app.data.local.PhotoStorage
import com.calorietracker.app.data.local.dao.MealDao
import com.calorietracker.app.data.local.seed.FoodSeedLoader
import com.calorietracker.app.data.repository.GoalRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CalorieApp : Application() {

    @Inject lateinit var seedLoader: FoodSeedLoader

    @Inject lateinit var goalRepository: GoalRepository

    @Inject lateinit var mealDao: MealDao

    @Inject lateinit var photoStorage: PhotoStorage

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // 首次启动写入随包的权威食物库。
            //
            // 必须兜住异常：这段跑在 Application.onCreate 之后的无主协程里，
            // 异常逃逸会走线程默认处理器直接**崩进程** —— 种子文件一旦损坏，
            // 用户就会「每开一次崩一次」，连进去看设置的机会都没有。
            // 失败时保持安静：沿用上一次成功写入的库存，搜索照常可用。
            runCatching { seedLoader.seedIfNeeded() }
                .onFailure { Log.e(TAG, "食物种子库写入失败，沿用已有库存", it) }

            // 确保存在一行全局默认目标。
            // 注意是「缺失才写」而不是「每次覆盖写」：后者会在每次冷启动把用户
            // 自己设过的目标覆盖回产品默认值（见 GoalRepository.ensureDefaultGoal）。
            runCatching { goalRepository.ensureDefaultGoal() }
                .onFailure { Log.e(TAG, "默认目标写入失败", it) }

            // 清理没有任何记录引用的照片文件。
            // 照片是只写不读的（photo_path 目前没有读取方），删记录也不删文件，
            // 不清理的话私有目录会随使用无限增长。启动扫一次文件名单即可。
            runCatching {
                val removed = photoStorage.pruneOrphans(mealDao.photoPaths())
                if (removed > 0) Log.i(TAG, "已清理无主照片 $removed 个")
            }.onFailure { Log.w(TAG, "无主照片清理失败（不影响使用）", it) }
        }
    }

    private companion object {
        const val TAG = "CalorieApp"
    }
}
