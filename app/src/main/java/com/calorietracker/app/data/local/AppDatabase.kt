package com.calorietracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.calorietracker.app.data.local.dao.FoodDao
import com.calorietracker.app.data.local.dao.GoalDao
import com.calorietracker.app.data.local.dao.MealDao
import com.calorietracker.app.data.local.entity.DailyGoal
import com.calorietracker.app.data.local.entity.EntryItem
import com.calorietracker.app.data.local.entity.FoodItem
import com.calorietracker.app.data.local.entity.MealEntry

@Database(
    entities = [
        FoodItem::class,
        MealEntry::class,
        EntryItem::class,
        DailyGoal::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao
    abstract fun goalDao(): GoalDao

    companion object {
        const val NAME = "calorie.db"

        /**
         * v1 → v2：加入 AI 识别所需的两个字段。
         * 用显式迁移而不是破坏性重建 —— 用户的记录不能因为一次升级被清掉。
         *
         * **写成幂等**：`ALTER TABLE ADD COLUMN` 遇到已存在的列会直接抛
         * `duplicate column name` 并让 App 每次启动都崩。而「列已存在」在现实中是会出现的：
         * 迁移执行了一半进程被杀、装过带新字段的中间版本、用户手动恢复过数据库…
         * 一旦发生就直接把用户挡在门外，且无法自愈。所以先查再改。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("food_items", "ai_estimated")) {
                    db.execSQL("ALTER TABLE food_items ADD COLUMN ai_estimated INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("entry_items", "source_label")) {
                    db.execSQL("ALTER TABLE entry_items ADD COLUMN source_label TEXT")
                }
            }
        }

        private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
            query("PRAGMA table_info($table)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex < 0) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return true
                }
            }
            return false
        }
    }
}
