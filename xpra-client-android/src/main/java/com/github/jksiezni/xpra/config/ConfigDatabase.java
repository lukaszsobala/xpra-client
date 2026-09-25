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

package com.github.jksiezni.xpra.config;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 *
 */
@Database(entities = {ServerDetails.class}, version = 6, exportSchema = false)
@TypeConverters(ConvertersKt.class)
public abstract class ConfigDatabase extends RoomDatabase {

    public abstract ConnectionDao getConfigs();

    private static ConfigDatabase instance;

    /**
     * Adds the resolution setting, keeping the saved connections.
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE ServerDetails ADD COLUMN scalePercent INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * Adds the clipboard sharing setting, on by default.
     */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE ServerDetails ADD COLUMN clipboardSharing INTEGER NOT NULL DEFAULT 1");
        }
    };

    /**
     * Adds how long to wait for the windows of the apps started from the phone.
     */
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE ServerDetails ADD COLUMN appWindowTimeout INTEGER NOT NULL DEFAULT 30");
        }
    };

    /**
     * Adds the video decoding setting, on by default.
     */
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE ServerDetails ADD COLUMN videoDecoding INTEGER NOT NULL DEFAULT 1");
        }
    };

    public static void setup(Application app) {
        if (instance == null) {
            instance = Room.databaseBuilder(app, ConfigDatabase.class, "config.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build();
        }
    }

    public static ConfigDatabase getInstance() {
        return instance;
    }
}
