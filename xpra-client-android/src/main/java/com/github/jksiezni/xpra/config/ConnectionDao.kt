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

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConnectionDao {

    @Query("SELECT * FROM ServerDetails")
    fun getAll(): LiveData<List<ServerDetails>>

    /** null when the server was removed */
    @Query("SELECT * FROM ServerDetails WHERE id = :id")
    suspend fun getById(id: Int): ServerDetails?

    /** the SSH private keys used by the servers, see [com.github.jksiezni.xpra.ssh.SshKeys] */
    @Query("SELECT sshPrivateKeyFile FROM ServerDetails WHERE sshPrivateKeyFile IS NOT NULL")
    fun getPrivateKeyFiles(): List<String>

    /** for a key which is gone, ie: the settings were restored from a backup, which leaves the keys out */
    @Query("UPDATE ServerDetails SET sshPrivateKeyFile = NULL WHERE sshPrivateKeyFile = :path")
    fun forgetPrivateKeyFile(path: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(config: ServerDetails)

    @Delete
    fun delete(config: ServerDetails)
}
