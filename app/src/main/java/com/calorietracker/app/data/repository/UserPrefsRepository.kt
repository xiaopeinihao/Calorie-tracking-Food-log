package com.calorietracker.app.data.repository

import android.content.Context
import androidx.core.content.edit
import com.calorietracker.app.data.local.entity.DailyGoal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** 性别：只影响基础代谢公式的常数项 */
enum class Gender(val label: String) { MALE("男"), FEMALE("女") }

/** 身体数据：全部可选填，填了才能自动推荐目标热量 */
data class BodyProfile(
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val age: Int? = null,
    val gender: Gender = Gender.FEMALE,
    /** 活动系数：默认 1.375（轻体力，久坐上班族最常见） */
    val activityFactor: Double = 1.375,
)

/**
 * 用户偏好（身体数据）。目标热量本身存在 Room 的 daily_goals 表里，
 * 这里只存「算目标」需要的输入。
 */
@Singleton
class UserPrefsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(read())
    val profile: StateFlow<BodyProfile> = _profile.asStateFlow()

    private fun read(): BodyProfile = BodyProfile(
        heightCm = prefs.getInt(KEY_HEIGHT, 0).takeIf { it > 0 },
        weightKg = prefs.getFloat(KEY_WEIGHT, 0f).takeIf { it > 0f }?.toDouble(),
        age = prefs.getInt(KEY_AGE, 0).takeIf { it > 0 },
        gender = if (prefs.getString(KEY_GENDER, Gender.FEMALE.name) == Gender.MALE.name) Gender.MALE else Gender.FEMALE,
        activityFactor = prefs.getFloat(KEY_ACTIVITY, 1.375f).toDouble(),
    )

    fun save(profile: BodyProfile) {
        prefs.edit {
            putInt(KEY_HEIGHT, profile.heightCm ?: 0)
            putFloat(KEY_WEIGHT, (profile.weightKg ?: 0.0).toFloat())
            putInt(KEY_AGE, profile.age ?: 0)
            putString(KEY_GENDER, profile.gender.name)
            putFloat(KEY_ACTIVITY, profile.activityFactor.toFloat())
        }
        _profile.value = profile
    }

    /**
     * 身体数据是否在合理区间内。
     *
     * 必须有这道闸：实测中一旦输入框被填进「体重 731kg」这种离谱值，
     * Mifflin-St Jeor 会算出 8200 kcal 的 BMR、11275 kcal 的 TDEE，
     * 再一键设成目标就是 9584 kcal —— 一个健康类 App 给出这种建议是不可接受的。
     * 查不到合理输入时**宁可不给推荐值**，也不给一个看起来像模像样的错数字。
     */
    fun bodyProfileIssue(profile: BodyProfile = _profile.value): String? {
        val height = profile.heightCm
        val weight = profile.weightKg
        val age = profile.age
        if (height == null || weight == null || age == null) return null   // 没填全不算错
        val issues = buildList {
            if (height < MIN_HEIGHT_CM || height > MAX_HEIGHT_CM) {
                add("身高应在 $MIN_HEIGHT_CM~$MAX_HEIGHT_CM cm")
            }
            if (weight < MIN_WEIGHT_KG || weight > MAX_WEIGHT_KG) {
                add("体重应在 ${MIN_WEIGHT_KG.toInt()}~${MAX_WEIGHT_KG.toInt()} kg")
            }
            if (age < MIN_AGE || age > MAX_AGE) {
                add("年龄应在 $MIN_AGE~$MAX_AGE 岁")
            }
        }
        return issues.takeIf { it.isNotEmpty() }?.joinToString("；")
    }

    private fun BodyProfile.isPlausible(): Boolean =
        bodyProfileIssue(this) == null &&
            heightCm != null && weightKg != null && age != null

    /**
     * 基础代谢率 BMR（Mifflin-St Jeor 公式，公认误差最小的简化式之一）：
     * 男：10×体重 + 6.25×身高 − 5×年龄 + 5
     * 女：10×体重 + 6.25×身高 − 5×年龄 − 161
     * 数据不全或超出合理区间时返回 null
     * （UI 显示「请先填写身高体重」/「数值超出合理范围」，而不是编一个数字）。
     */
    fun bmr(profile: BodyProfile = _profile.value): Double? {
        if (!profile.isPlausible()) return null
        val w = profile.weightKg!!
        val h = profile.heightCm!!
        val a = profile.age!!
        val base = 10 * w + 6.25 * h - 5 * a
        return if (profile.gender == Gender.MALE) base + 5 else base - 161
    }

    /** 每日总消耗 TDEE = BMR × 活动系数 */
    fun tdee(profile: BodyProfile = _profile.value): Double? = bmr(profile)?.times(profile.activityFactor)

    /**
     * 推荐摄入：在 TDEE 基础上打 85 折（温和减脂，约 15% 热量缺口）。
     * 再夹到 [MIN_SAFE_KCAL, MAX_SANE_KCAL] 区间里：低于下限不健康，
     * 高于上限说明输入或参数明显不对，这种时候给个「天花板值」也比给 9584 强。
     * 宏量比例沿用产品的 50% / 20% / 30% 供能拆分。
     */
    fun suggestedGoalKcal(profile: BodyProfile = _profile.value): Int? {
        val tdee = tdee(profile) ?: return null
        return (tdee * 0.85).roundToInt().coerceIn(MIN_SAFE_KCAL, MAX_SANE_KCAL)
    }

    fun suggestedGoal(profile: BodyProfile = _profile.value): DailyGoal? {
        val kcal = suggestedGoalKcal(profile) ?: return null
        return DailyGoal(
            date = DailyGoal.DEFAULT_DATE,
            targetKcal = kcal,
            targetCarbs = (kcal * 0.50 / 4).roundToInt(),
            targetProtein = (kcal * 0.20 / 4).roundToInt(),
            targetFat = (kcal * 0.30 / 9).roundToInt(),
        )
    }

    companion object {
        /** 身体数据合理区间：超出就不给推荐值 */
        const val MIN_HEIGHT_CM = 100
        const val MAX_HEIGHT_CM = 250
        const val MIN_WEIGHT_KG = 20.0
        const val MAX_WEIGHT_KG = 300.0
        const val MIN_AGE = 10
        const val MAX_AGE = 120

        /** 推荐摄入的安全上下限 */
        const val MIN_SAFE_KCAL = 1000
        const val MAX_SANE_KCAL = 4000

        private const val KEY_HEIGHT = "height_cm"
        private const val KEY_WEIGHT = "weight_kg"
        private const val KEY_AGE = "age"
        private const val KEY_GENDER = "gender"
        private const val KEY_ACTIVITY = "activity_factor"
    }
}
