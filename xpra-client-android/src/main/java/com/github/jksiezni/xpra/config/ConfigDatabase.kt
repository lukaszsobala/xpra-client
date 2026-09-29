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

        /** set up by [setup] when the app starts */
        @JvmStatic
        fun getInstance(): ConfigDatabase = instance!!
    }
}
