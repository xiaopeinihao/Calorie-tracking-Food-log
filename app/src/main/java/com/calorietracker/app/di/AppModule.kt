package com.calorietracker.app.di

import android.content.Context
import androidx.room.Room
import com.calorietracker.app.BuildConfig
import com.calorietracker.app.data.local.AppDatabase
import com.calorietracker.app.data.local.dao.FoodDao
import com.calorietracker.app.data.local.dao.GoalDao
import com.calorietracker.app.data.local.dao.MealDao
import com.calorietracker.app.data.recognition.MockRecognitionService
import com.calorietracker.app.data.recognition.RemoteRecognitionService
import com.calorietracker.app.data.recognition.remote.RecognitionApi
import com.calorietracker.app.domain.recognition.FoodRecognitionService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // 记录类数据的迁移必须显式处理，禁止破坏性重建。
            //
            // 这里**刻意不加** fallbackToDestructiveMigrationOnDowngrade：
            // 磁盘上的库版本高于代码时（从新版本 APK 回退、或从云备份恢复），
            // Room 会静默 DROP 全表重建 —— 用户的饮食记录、目标、自建食物
            // 会一次全部消失且没有任何提示。宁可抛异常让人看见，
            // 也不要「看起来正常启动、数据却没了」。
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideFoodDao(db: AppDatabase): FoodDao = db.foodDao()

    @Provides
    fun provideMealDao(db: AppDatabase): MealDao = db.mealDao()

    @Provides
    fun provideGoalDao(db: AppDatabase): GoalDao = db.goalDao()
}

@Module
@InstallIn(SingletonComponent::class)
object RecognitionModule {

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            }
        )
        .build()

    @Provides
    @Singleton
    fun provideRecognitionApi(client: OkHttpClient): RecognitionApi = Retrofit.Builder()
        .baseUrl(BuildConfig.RECOGNITION_BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(RecognitionApi::class.java)

    /**
     * 识别服务选择器：API Key 为空 → 离线 Mock（默认，全功能可用）；
     * 配置了 Key 才走真实远端服务。这条规则写在代码里，不靠人记。
     */
    @Provides
    @Singleton
    fun provideRecognitionService(
        mock: MockRecognitionService,
        remote: RemoteRecognitionService,
    ): FoodRecognitionService =
        if (BuildConfig.RECOGNITION_API_KEY.isBlank()) mock else remote
}
