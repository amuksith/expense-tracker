package com.amuksith.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ExpenseEntity::class, AccountEntity::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                )
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
