/*
 * Copyright (C) 2020 Jakub Ksiezniak
 *
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.github.jksiezni.xpra.config

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 *
 */
@Database(entities = [ServerDetails::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ConfigDatabase : RoomDatabase() {

    abstract fun connectionDao(): ConnectionDao

    val configs: ConnectionDao
        get() = connectionDao()

    companion object {
        private var instance: ConfigDatabase? = null

        /** for the work which outlives the screen that asked for it, ie: saving a server */
        private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Timber.w(e, "cannot update the saved servers") })

        /**
         * Adds the resolution setting, keeping the saved connections.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ServerDetails ADD COLUMN scalePercent INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Adds the clipboard sharing setting, on by default.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ServerDetails ADD COLUMN clipboardSharing INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * Adds how long to wait for the windows of the apps started from the phone.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ServerDetails ADD COLUMN appWindowTimeout INTEGER NOT NULL DEFAULT 30")
            }
        }

        /**
         * Adds the video decoding setting, on by default.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ServerDetails ADD COLUMN videoDecoding INTEGER NOT NULL DEFAULT 1")
            }
        }

        @JvmStatic
        fun setup(app: Application) {
            if (instance == null) {
                instance = Room.databaseBuilder(app, ConfigDatabase::class.java, "config.db")
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
            }
        }

        /**
         * Runs [block] off the main thread, until it completes: it is not cancelled with the
         * screen which started it. The errors are logged.
         */
        fun inBackground(block: suspend CoroutineScope.() -> Unit) {
            background.launch(block = block)
        }

        /** set up by [setup] when the app starts */
        @JvmStatic
        fun getInstance(): ConfigDatabase = instance!!
    }
}
