package com.qris.soundbox.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
    
    @Query("SELECT SUM(amount) FROM transactions WHERE timestamp >= :startOfDay")
    fun getTodayTotalSales(startOfDay: Long): Flow<Double?>
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules")
    fun getAllRulesFlow(): Flow<List<Rule>>

    @Query("SELECT * FROM rules")
    suspend fun getAllRulesList(): List<Rule>

    @Query("SELECT * FROM rules WHERE isActive = 1")
    suspend fun getActiveRules(): List<Rule>

    @Query("SELECT * FROM rules WHERE packageName = :packageName AND isActive = 1")
    suspend fun getRulesForPackage(packageName: String): List<Rule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: Rule)

    @Delete
    suspend fun deleteRule(rule: Rule)
    
    @Query("SELECT COUNT(*) FROM rules")
    suspend fun getRuleCount(): Int
}

@Database(entities = [Transaction::class, Rule::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun ruleDao(): RuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qris_soundbox_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
