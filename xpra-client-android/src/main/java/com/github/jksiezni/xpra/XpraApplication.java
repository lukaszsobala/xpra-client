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

package com.github.jksiezni.xpra;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.github.jksiezni.xpra.config.ConfigDatabase;
import com.github.jksiezni.xpra.ssh.SshKeys;
import com.google.android.material.color.DynamicColors;

import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;

import timber.log.Timber;


public class XpraApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Material You: take the theme colours from the wallpaper, on Android 12+
        DynamicColors.applyToActivitiesIfAvailable(this);
        ConfigDatabase.setup(this);
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        } else {
            Timber.plant(new ReleaseTree());
        }
        Completable.fromAction(() -> SshKeys.INSTANCE.cleanUp(this, ConfigDatabase.getInstance().getConfigs()))
            .subscribeOn(Schedulers.io())
            .subscribe(() -> {}, e -> Timber.w(e, "cannot clean up the SSH keys"));
    }

    /**
     * Release builds only log the problems, as the debug messages describe the traffic with
     * the server, ie: the contents of the clipboard.
     */
    private static class ReleaseTree extends Timber.DebugTree {
        @Override
        protected boolean isLoggable(String tag, int priority) {
            return priority >= Log.WARN;
        }
    }

    public static XpraApplication getInstance(Context context) {
        return (XpraApplication) context.getApplicationContext();
    }

}
