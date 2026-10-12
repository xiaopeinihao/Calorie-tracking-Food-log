package com.calorietracker.app.data.repository

import com.calorietracker.app.data.local.dao.FoodDao
import com.calorietracker.app.data.local.entity.FoodItem
import com.calorietracker.app.domain.nutrition.NutritionMath
import com.calorietracker.app.domain.nutrition.NutritionSummary
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 食物库仓库。UI 层**只能**通过它读写食物营养数据，
 * 任何热量换算都必须走 [NutritionMath]，保证「每 100g 值 × 克数」的口径全局唯一。
 */
@Singleton
class FoodRepository @Inject constructor(
    private val foodDao: FoodDao,
) {

    fun search(query: String): Flow<List<FoodItem>> =
        if (query.isBlank()) foodDao.quickPicks(QUICK_PICK_LIMIT) else foodDao.search(query.trim())

    fun quickPicks(): Flow<List<FoodItem>> = foodDao.quickPicks(QUICK_PICK_LIMIT)

    fun byCategory(category: String): Flow<List<FoodItem>> = foodDao.byCategory(category)

    suspend fun count(): Int = foodDao.count()

    suspend fun findById(id: String): FoodItem? = foodDao.findById(id)

    /** 按名称精确/模糊匹配，用于把识别结果落到权威食物库上 */
    suspend fun resolveByName(name: String): FoodItem? =
        foodDao.findByExactName(name) ?: foodDao.searchOnce(name.trim()).firstOrNull()

    /** 一次性搜索（AI prompt 里给候选条目名用） */
    suspend fun searchOnce(query: String): List<FoodItem> =
        if (query.isBlank()) emptyList() else foodDao.searchOnce(query.trim())

    /**
     * 把 AI 估算出来的食物存进本地库，下次就能直接搜到。
     * 会明确打上 `aiEstimated` 标记，界面上与权威数据区分开显示——
     * 这条数据是模型给的，不是成分表给的，不能混为一谈。
     */
    suspend fun saveAiEstimated(
        name: String,
        per100g: NutritionSummary,
        source: String,
    ): FoodItem? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || per100g.kcal <= 0) return null
        resolveByName(trimmed)?.let { return it }   // 已经能搜到就不重复插
        val item = FoodItem(
            id = "ai:" + trimmed.hashCode().toString(16) + "-" + per100g.kcal.toInt(),
            name = trimmed,
            category = "我的食物",
            kcalPer100g = per100g.kcal,
            carbsPer100g = per100g.carbs,
            proteinPer100g = per100g.protein,
            fatPer100g = per100g.fat,
            commonServing = "一份约 100g",
            commonServingGrams = 100,
            source = source,
            sourceRef = null,
            alias = null,
            cooked = false,
            aiEstimated = true,
        )
        foodDao.insertAll(listOf(item))
        return item
    }

    /** 常用分类清单（用于手动录入页的筛选） */
    suspend fun categories(): List<String> = foodDao.allCategories()

    /**
     * 把「某食物 + 克数」换算成热量与营养素。
     * 这是全工程唯一的换算入口：热量直接取数据库自报值做线性缩放，不做任何再拟合。
     */
    fun toSummary(food: FoodItem, grams: Int): NutritionSummary = NutritionSummary(
        kcal = NutritionMath.scalePer100g(food.kcalPer100g, grams),
        carbs = NutritionMath.scalePer100g(food.carbsPer100g, grams),
        protein = NutritionMath.scalePer100g(food.proteinPer100g, grams),
        fat = NutritionMath.scalePer100g(food.fatPer100g, grams),
    )

    private companion object {
        const val QUICK_PICK_LIMIT = 12
    }
}
