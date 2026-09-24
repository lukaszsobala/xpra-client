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

package com.github.jksiezni.xpra.ssh;

import android.app.Activity;
import android.widget.Toast;

import com.github.jksiezni.xpra.config.ServerDetails;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Asks the user for the SSH credentials, or uses the ones saved in the {@link PasswordVault}.
 * <p>
 * A saved password is tried once per connection: when the server rejects it, the user is asked
 * again. Passwords are only saved, or forgotten, once the connection succeeded, see
 * {@link #onConnected()}, so that a mistyped password is never remembered.
 *
 * @author Jakub Księżniak
 */
public class SshUserInfoHandler implements UserInfo, UIKeyboardInteractive {

	private static final String PASSWORD = "password";
	private static final String PASSPHRASE = "passphrase";

	private final Activity activity;
	private final ServerDetails server;
	private final PasswordVault vault;

	/** the saved passwords tried already during this connection */
	private final Set<String> triedSaved = new HashSet<>();
	/** the passwords to save (or null to forget) once connected, by entry */
	private final Map<PasswordVault.Entry, String> pending = new LinkedHashMap<>();

	private String password;

	public SshUserInfoHandler(Activity activity, ServerDetails server) {
		this.activity = activity;
		this.server = server;
		this.vault = new PasswordVault(activity);
	}

	/**
	 * Saves the passwords the user chose to remember, and forgets the others:
	 * call this once the connection succeeded.
	 */
	public void onConnected() {
		for (Map.Entry<PasswordVault.Entry, String> e : pending.entrySet()) {
			if (e.getValue() != null) {
				vault.save(e.getKey(), e.getValue());
			} else {
				vault.remove(e.getKey());
			}
		}
		pending.clear();
	}

	private boolean askPassword(String kind, String message) {
		final PasswordVault.Entry entry = new PasswordVault.Entry(server, kind);
		if (triedSaved.add(entry.getPrefKey())) {
			final String saved = vault.load(entry);
			if (saved != null) {
				password = saved;
				return true;
			}
		}
		try {
			final CredentialsAskTask task = new CredentialsAskTask(activity, message, vault.contains(entry));
			if (!task.execute().get()) {
				return false;
			}
			password = task.getPassword();
			pending.put(entry, task.isRemember() ? password : null);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	@Override
	public boolean promptPassphrase(String message) {
		return askPassword(PASSPHRASE + ":" + server.getSshPrivateKeyFile(), message);
	}

	@Override
	public String getPassphrase() {
		return password;
	}

	@Override
	public boolean promptPassword(String message) {
		return askPassword(PASSWORD, message);
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String[] promptKeyboardInteractive(String destination, String name, String instruction, String[] prompt,
			boolean[] echo) {
		if (prompt.length == 1 && !echo[0]) {
			// a single hidden answer: the password, which can be saved
			return askPassword(PASSWORD, prompt[0]) ? new String[]{password} : null;
		}
		try {
			final CredentialsAskTask task = new CredentialsAskTask(activity, prompt, echo);
			if (task.execute().get()) {
				return task.getAnswers();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return null;
	}

	@Override
	public boolean promptYesNo(String message) {
		try {
			return new YesNoAskTask(activity)
			.execute(message)
			.get();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public void showMessage(String message) {
		activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
	}

}
