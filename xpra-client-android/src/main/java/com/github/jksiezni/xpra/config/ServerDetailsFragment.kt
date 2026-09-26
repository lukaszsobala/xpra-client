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

import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceFragmentCompat
import com.github.jksiezni.xpra.R
import com.github.jksiezni.xpra.gl.VideoDecoders
import com.github.jksiezni.xpra.ssh.PasswordVault
import com.github.jksiezni.xpra.ssh.SshKeys
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.regex.Pattern

class ServerDetailsFragment : PreferenceFragmentCompat() {

    private lateinit var dataStore: ServerDetailsDataStore

    /** key files have no MIME type of their own, ie: id_ed25519 */
    private val pickPrivateKey = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importPrivateKey(it) }
    }

    init {
        setHasOptionsMenu(true)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val serverDetails = requireArguments().getSerializable(KEY_SERVER_DETAILS) as ServerDetails
        val dao = ConfigDatabase.getInstance().configs
        dataStore = ServerDetailsDataStore(serverDetails, dao)
        preferenceManager.preferenceDataStore = dataStore
        addPreferencesFromResource(R.xml.server_details_preference)
        setupPreferences()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.server_details_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> if (save()) {
                parentFragmentManager.popBackStack()
            }
            R.id.action_delete -> confirmDelete()
            android.R.id.home -> parentFragmentManager.popBackStack()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupPreferences() {
        findPreference<Preference>(ServerDetailsDataStore.PREF_NAME)?.summaryProvider = EditTextSummaryProvider(getString(R.string.enter_unique_name))
        findPreference<Preference>(ServerDetailsDataStore.PREF_HOST)?.summaryProvider = EditTextSummaryProvider(getString(R.string.enter_unique_name))
        findPreference<Preference>(ServerDetailsDataStore.PREF_USERNAME)?.summaryProvider = EditTextSummaryProvider(getString(R.string.enter_unique_name))
        findPreference<Preference>(ServerDetailsDataStore.PREF_DISPLAY_ID)?.summaryProvider = DisplayIdSummaryProvider(getString(R.string.automatic))
        findPreference<Preference>(ServerDetailsDataStore.PREF_VIDEO)?.summary =
            getString(R.string.video_decoding_summary, VideoDecoders.describe())
        findPreference<Preference>(ServerDetailsDataStore.PREF_APP_WINDOW_TIMEOUT)?.summaryProvider =
            SummaryProvider<EditTextPreference> { preference ->
                val seconds = preference.text?.toIntOrNull() ?: ServerDetails.DEFAULT_APP_WINDOW_TIMEOUT
                if (seconds == ServerDetails.WAIT_FOREVER) {
                    getString(R.string.app_window_timeout_forever)
                } else {
                    getString(R.string.app_window_timeout_seconds, seconds)
                }
            }

        findPreference<ListPreference>(ServerDetailsDataStore.PREF_CONNECTION_TYPE)?.let {
            it.setOnPreferenceChangeListener { _, newValue ->
                setSshPreferencesEnabled(ConnectionType.SSH.name == newValue)
                true
            }
            setSshPreferencesEnabled(ConnectionType.SSH.name == it.value)
        }

        findPreference<Preference>(ServerDetailsDataStore.PREF_PRIVATE_KEY)?.let { pref ->
            updatePrivateKeySummary(pref)
            pref.setOnPreferenceClickListener {
                if (dataStore.serverDetails.sshPrivateKeyFile == null) {
                    pickPrivateKey.launch(arrayOf("*/*"))
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.ssh_private_key)
                        .setItems(arrayOf(getString(R.string.choose_another_key), getString(R.string.remove_key))) { _, which ->
                            if (which == 0) {
                                pickPrivateKey.launch(arrayOf("*/*"))
                            } else {
                                dataStore.serverDetails.sshPrivateKeyFile = null
                                updatePrivateKeySummary(pref)
                            }
                        }
                        .show()
                }
                true
            }
        }

        findPreference<Preference>(PREF_FORGET_PASSWORDS)?.let { pref ->
            val serverId = dataStore.serverDetails.id
            val vault = PasswordVault(requireContext())
            pref.isEnabled = vault.hasPasswords(serverId)
            if (!pref.isEnabled) {
                pref.summary = getString(R.string.no_saved_passwords)
            }
            pref.setOnPreferenceClickListener {
                vault.forget(serverId)
                pref.isEnabled = false
                pref.summary = getString(R.string.no_saved_passwords)
                Toast.makeText(activity, R.string.passwords_forgotten, Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    private fun importPrivateKey(uri: Uri) {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            var error = R.string.invalid_private_key
            val path = withContext(Dispatchers.IO) {
                try {
                    SshKeys.import(context, uri)
                } catch (e: SshKeys.InvalidKeyException) {
                    Timber.w(e, "not a private key: %s", uri)
                    null
                } catch (e: IOException) {
                    Timber.w(e, "cannot read the private key: %s", uri)
                    error = R.string.unreadable_private_key
                    null
                } catch (e: SecurityException) {
                    Timber.w(e, "cannot read the private key: %s", uri)
                    error = R.string.unreadable_private_key
                    null
                }
            }
            if (path == null) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            } else {
                // used once the server is saved: an unused copy is deleted later
                dataStore.serverDetails.sshPrivateKeyFile = path
                findPreference<Preference>(ServerDetailsDataStore.PREF_PRIVATE_KEY)?.let { updatePrivateKeySummary(it) }
            }
        }
    }

    private fun updatePrivateKeySummary(pref: Preference) {
        pref.summary = dataStore.serverDetails.sshPrivateKeyFile?.let { SshKeys.name(it) }
            ?: getString(R.string.ssh_private_key_summary)
    }

    private fun confirmDelete() {
        val server = dataStore.serverDetails
        if (server.id == 0) {
            // never saved
            parentFragmentManager.popBackStack()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_server_title, server.name))
            .setMessage(R.string.delete_server_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                val context = requireContext().applicationContext
                val dao = ConfigDatabase.getInstance().configs
                Completable.fromAction {
                    dao.delete(server)
                    PasswordVault(context).forget(server.id)
                }.subscribeOn(Schedulers.io()).subscribe()
                parentFragmentManager.popBackStack()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun save(): Boolean {
        return if (validate(dataStore.serverDetails)) {
            dataStore.save()
            true
        } else {
            false
        }
    }

    private fun setSshPreferencesEnabled(enabled: Boolean) {
        val portPref = findPreference<EditTextPreference>(ServerDetailsDataStore.PREF_PORT)
        if (enabled) {
            if ("10000" == portPref?.text) {
                portPref.text = "22"
            }
        } else {
            if ("22" == portPref?.text) {
                portPref.text = "10000"
            }
        }
        findPreference<Preference>(ServerDetailsDataStore.PREF_USERNAME)?.isEnabled = enabled
        findPreference<Preference>(ServerDetailsDataStore.PREF_PRIVATE_KEY)?.isEnabled = enabled
    }

    private fun validateName(name: String?): Boolean {
        if (name == null || name.isEmpty()) {
            Toast.makeText(activity, "The connection name must not be empty.", Toast.LENGTH_LONG).show()
            return false
        }
        //		else if (!connectionDao.queryForEq("name", name).isEmpty()) {
//			Toast.makeText(getActivity(), "The connection with that name exists already.", Toast.LENGTH_LONG).show();
//			return false;
//		}
        return true
    }

    private fun validateHostname(host: String?): Boolean {
        if (host == null || host.isEmpty()) {
            Toast.makeText(activity, "The hostname must not be empty.", Toast.LENGTH_LONG).show()
            return false
        } else if (!HOSTNAME_PATTERN.matcher(host).matches()) {
            val matcher = Patterns.IP_ADDRESS.matcher(host)
            if (!matcher.matches()) {
                Toast.makeText(activity, "Invalid hostname: $host", Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    private fun validate(serverDetails: ServerDetails): Boolean {
        return validateName(serverDetails.name) && validateHostname(serverDetails.host)
    }

    companion object {
        private const val KEY_SERVER_DETAILS = "server_details"
        private const val PREF_FORGET_PASSWORDS = "forget_passwords"

        private val HOSTNAME_PATTERN = Pattern.compile("^[0-9a-zA-Z_\\-.]*$")

        fun create(serverDetails: ServerDetails): ServerDetailsFragment {
            return ServerDetailsFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(KEY_SERVER_DETAILS, serverDetails)
                }
            }
        }
    }

}

class EditTextSummaryProvider(private val emptySummary: String) : SummaryProvider<EditTextPreference> {
    override fun provideSummary(preference: EditTextPreference): CharSequence {
        val text = preference.text
        return if (text.isNullOrEmpty()) {
            emptySummary
        } else {
            text
        }
    }
}

class DisplayIdSummaryProvider(private val emptySummary: String) : SummaryProvider<EditTextPreference> {
    override fun provideSummary(preference: EditTextPreference): CharSequence {
        val text = preference.text
        return if (text.isNullOrEmpty() || "-1" == text) {
            emptySummary
        } else {
            text
        }
    }
}
