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
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.github.jksiezni.xpra.config.ServerDetails
import timber.log.Timber
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Remembers SSH passwords and key passphrases, encrypted with AES-GCM by a key that lives in the
 * Android Keystore: the key cannot be read out of the device, so neither the stored passwords
 * nor a backup of them can be decrypted anywhere else. The encrypted passwords are also left out
 * of backups (see backup_descriptor.xml and data_extraction_rules.xml).
 *
 * Each password is bound to its server, user name, host and port: changing any of these
 * means the password is asked for again.
 */
class PasswordVault(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Identifies one password of a server, ie: its SSH password or the passphrase of its key.
     */
    class Entry(server: ServerDetails, kind: String) {
        val serverId = server.id
        /** authenticated with the encrypted password, so that it cannot be moved to another entry */
        val label = "${server.id}|$kind|${server.username}@${server.host}:${server.port}"
        val prefKey = "${server.id}.${sha256(label)}"

        override fun equals(other: Any?) = other is Entry && other.prefKey == prefKey

        override fun hashCode() = prefKey.hashCode()
    }

    fun load(entry: Entry): String? {
        val stored = prefs.getString(entry.prefKey, null) ?: return null
        return try {
            val data = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES))
            cipher.updateAAD(entry.label.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            // ie: the key was lost when the app data was restored from a backup
            Timber.w(e, "cannot decrypt the saved password, forgetting it")
            remove(entry)
            null
        } catch (e: IllegalArgumentException) {
            remove(entry)
            null
        }
    }

    fun contains(entry: Entry): Boolean = prefs.contains(entry.prefKey)

    fun save(entry: Entry, password: String) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            cipher.updateAAD(entry.label.toByteArray(Charsets.UTF_8))
            val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val data = cipher.iv + encrypted
            prefs.edit().putString(entry.prefKey, Base64.encodeToString(data, Base64.NO_WRAP)).apply()
        } catch (e: GeneralSecurityException) {
            Timber.e(e, "cannot encrypt the password, it will not be saved")
        }
    }

    fun remove(entry: Entry) {
        prefs.edit().remove(entry.prefKey).apply()
    }

    fun hasPasswords(serverId: Int): Boolean = prefs.all.keys.any { it.startsWith("$serverId.") }

    /**
     * Forgets all the passwords saved for a server.
     */
    fun forget(serverId: Int) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$serverId.") }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // only usable while the device is unlocked
                    setUnlockedDeviceRequired(true)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    companion object {
        const val PREFS_NAME = "saved_passwords"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "xpra-saved-passwords"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
