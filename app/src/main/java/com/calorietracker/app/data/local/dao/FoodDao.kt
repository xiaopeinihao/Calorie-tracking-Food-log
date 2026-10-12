package com.calorietracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.calorietracker.app.data.local.entity.FoodItem
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun count(): Int

    /** 只数随包种子条目（不含用户自己存的「我的食物」） */
    @Query("SELECT COUNT(*) FROM food_items WHERE ai_estimated = 0")
    suspend fun countSeedItems(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FoodItem>)

    /**
     * 清掉随包种子条目（升级时重灌用）。
     *
     * 为什么需要「删」：食物库不只会加条目，也会删条目 —— 实测踩过：
     * 早期版本把 USDA 全量英文条目导了进来，后来只保留人工中文命名的那部分，
     * 但老用户设备上那 7,646 条英文条目一直留着，搜索和「常见食物」里照样出现。
     *
     * 条件里限定 `ai_estimated = 0`：用户自己存的「我的食物」是 ai_estimated = 1，
     * 不会被误删；历史记录在 entry_items 里存的是快照，也不受这里影响。
     */
    @Query("DELETE FROM food_items WHERE ai_estimated = 0")
    suspend fun deleteSeedItems()

    /**
     * 重灌种子库：**删除 + 分批插入在同一个事务里**。
     *
     * 拆成「先 deleteSeedItems 再 insertAll」两次独立事务会留下一个空库窗口：
     * 那几十毫秒内搜索结果、常见食物、离线识别全部为空，用户刚好这时进来
     * 就会看到「没有识别到食物」；分批插入期间还会查到一份「半成品库」。
     */
    @Transaction
    suspend fun replaceSeedItems(items: List<FoodItem>, chunk: Int) {
        deleteSeedItems()
        items.chunked(chunk).forEach { insertAll(it) }
    }

    @Query("SELECT * FROM food_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): FoodItem?

    @Query("SELECT * FROM food_items WHERE name = :name LIMIT 1")
    suspend fun findByExactName(name: String): FoodItem?

    /** 模糊搜索（名称 + 别名），名称匹配优先，最多返回 50 条 */
    @Query(
        """
        SELECT * FROM food_items
        WHERE name LIKE '%' || :query || '%' OR IFNULL(alias, '') LIKE '%' || :query || '%'
        ORDER BY (CASE WHEN name = :query THEN 0
                       WHEN name LIKE :query || '%' THEN 1
                       WHEN IFNULL(alias, '') LIKE :query || '%' THEN 2
                       ELSE 3 END), name
        LIMIT 50
        """
    )
    fun search(query: String): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE category = :category ORDER BY name LIMIT :limit")
    fun byCategory(category: String, limit: Int = 30): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items ORDER BY name LIMIT :limit")
    fun all(limit: Int = 100): Flow<List<FoodItem>>

    /** 挂起版模糊搜索：把识别结果回落到食物库时用（不订阅变化） */
    @Query(
        """
        SELECT * FROM food_items
        WHERE name LIKE '%' || :query || '%' OR IFNULL(alias, '') LIKE '%' || :query || '%'
        ORDER BY (CASE WHEN name = :query THEN 0
                       WHEN name LIKE :query || '%' THEN 1
                       WHEN IFNULL(alias, '') LIKE :query || '%' THEN 2
                       ELSE 3 END), name
        LIMIT 50
        """
    )
    suspend fun searchOnce(query: String): List<FoodItem>

    @Query("SELECT DISTINCT category FROM food_items ORDER BY category")
    suspend fun allCategories(): List<String>

    /** 离线模拟识别用：从权威食物库里真随机抽 n 条 */
    @Query("SELECT * FROM food_items ORDER BY RANDOM() LIMIT :n")
    suspend fun randomPick(n: Int): List<FoodItem>

    /**
     * 「常见食物」快捷列表。取哪一批有讲究：
     *  - 「代表值」是成分表给每类食物的推荐条目（「米饭（蒸，代表值）」「鸡蛋（代表值）」…），
     *    正好就是普通人的日常叫法；
     *  - `dish:` 是复算出来的家常菜/主食/小吃。
     *
     * 用随机抽而不是按名称排序：每次进来换一批，用户能看到更多食物。
     * 全库都是简体中文名（英文条目在做数据时已被排除），这里不需要再做过滤。
     */
    @Query(
        """
        SELECT * FROM food_items
        WHERE name LIKE '%代表值%' OR id LIKE 'dish:%'
        ORDER BY RANDOM()
        LIMIT :n
        """
    )
    fun quickPicks(n: Int): Flow<List<FoodItem>>
}
