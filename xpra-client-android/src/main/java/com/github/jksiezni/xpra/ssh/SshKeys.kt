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

package com.github.jksiezni.xpra.ssh

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.github.jksiezni.xpra.config.ConnectionDao
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The SSH private keys of the servers, copied into the app's private storage when chosen: a
 * document picked by the user cannot be read by JSch, which needs a file, and may go away.
 *
 * Each key lives in a folder of its own, under the name of the picked document, so that it can
 * be shown to the user. The keys are left out of backups (see backup_descriptor.xml and
 * data_extraction_rules.xml).
 */
object SshKeys {

    private const val KEYS_DIR = "ssh_keys"
    /** far more than any key: anything bigger is not one */
    private const val MAX_KEY_BYTES = 64 * 1024
    /** unused keys younger than this are kept: they may be chosen in a server not saved yet */
    private val UNUSED_KEY_MAX_AGE_MS = TimeUnit.DAYS.toMillis(1)

    class InvalidKeyException(message: String) : IOException(message)

    /**
     * Copies a private key picked by the user, after checking that it is one.
     * It reads the document, which may be slow: not to be called on the main thread.
     *
     * @return the path of the copy, for [com.jcraft.jsch.JSch.addIdentity]
     */
    @Throws(IOException::class)
    fun import(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val data = input.readNBytesCompat(MAX_KEY_BYTES + 1)
            if (data.size > MAX_KEY_BYTES) {
                throw InvalidKeyException("too big for a private key")
            }
            data
        } ?: throw IOException("cannot open $uri")
        try {
            // an encrypted key is parsed without its passphrase, which is asked when connecting
            KeyPair.load(JSch(), bytes, null).dispose()
        } catch (e: JSchException) {
            throw InvalidKeyException(e.message ?: "invalid private key")
        }

        val dir = File(keysDir(context), UUID.randomUUID().toString())
        if (!dir.mkdirs()) {
            throw IOException("cannot create $dir")
        }
        val file = File(dir, safeName(displayName(context, uri)))
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /**
     * The name shown for a key: the name of the document it was copied from.
     */
    fun name(path: String): String = File(path).name

    /**
     * Deletes the copied keys which no server uses any more, ie: replaced or removed keys,
     * or the keys of deleted servers, and forgets the keys which are gone.
     * It uses the database: not to be called on the main thread.
     */
    fun cleanUp(context: Context, dao: ConnectionDao) {
        val used = mutableSetOf<File>()
        for (path in dao.privateKeyFiles) {
            val file = File(path)
            if (file.isFile) {
                used.add(file.parentFile!!)
            } else {
                Timber.w("the SSH key of a server is gone: %s", file.name)
                dao.forgetPrivateKeyFile(path)
            }
        }
        val now = System.currentTimeMillis()
        keysDir(context).listFiles()?.forEach { dir ->
            if (dir !in used && now - dir.lastModified() > UNUSED_KEY_MAX_AGE_MS) {
                Timber.d("deleting an unused SSH key: %s", dir.name)
                dir.deleteRecursively()
            }
        }
    }

    private fun keysDir(context: Context) = File(context.filesDir, KEYS_DIR)

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
        } ?: uri.lastPathSegment

    private fun safeName(name: String?): String {
        val safe = name?.substringAfterLast('/')?.replace(Regex("[^A-Za-z0-9._@+-]"), "_")
        return if (safe.isNullOrEmpty() || safe == "." || safe == "..") "private_key" else safe
    }

    /** InputStream.readNBytes() needs API 33 */
    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < max) {
            val n = read(buffer, 0, minOf(buffer.size, max - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
