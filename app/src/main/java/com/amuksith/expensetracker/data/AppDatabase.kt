package com.amuksith.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExpenseEntity::class, AccountEntity::class, RecurringTransactionEntity::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun accountDao(): AccountDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recurring_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `category` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `accountId` INTEGER NOT NULL,
                        `frequency` TEXT NOT NULL,
                        `nextDueEpochDay` INTEGER NOT NULL,
                        `originalMonthlyDay` INTEGER NOT NULL,
                        `revision` INTEGER NOT NULL DEFAULT 1,
                        `notes` TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recurring_transactions` ADD COLUMN `toAccountId` INTEGER")
                db.execSQL("ALTER TABLE `recurring_transactions` ADD COLUMN `dueTimestamp` INTEGER")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recurring_transactions` ADD COLUMN `customIntervalCount` INTEGER")
                db.execSQL("ALTER TABLE `recurring_transactions` ADD COLUMN `customIntervalUnit` TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default accounts
                        db.execSQL("INSERT INTO accounts (id, name, initialBalance, colorHex) VALUES (1, 'Cash', 0.0, 4280184997)")
                        db.execSQL("INSERT INTO accounts (id, name, initialBalance, colorHex) VALUES (2, 'Bank Account', 0.0, 4279589436)")
                        db.execSQL("INSERT INTO accounts (id, name, initialBalance, colorHex) VALUES (3, 'Business Wallet', 0.0, 4281729352)")
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
