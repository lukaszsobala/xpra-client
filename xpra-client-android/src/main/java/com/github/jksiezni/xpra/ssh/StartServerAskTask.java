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

import android.content.Context;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;

import com.github.jksiezni.xpra.R;
import com.github.jksiezni.xpra.connection.CommandInput;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Asks whether to start an Xpra server, as none is running, and which command to start in it.
 */
final class StartServerAskTask extends UiTask<Void, Boolean> {

    private final Context context;
    private final String host;
    private final int display;
    private volatile String command = "";

    StartServerAskTask(Context context, String host, int display) {
        this.context = context;
        this.host = host;
        this.display = display;
    }

    @Override
    protected void doOnUIThread(Void... params) {
        final EditText editText = new EditText(context);
        editText.setHint(R.string.start_server_command_hint);
        final FrameLayout layout = new FrameLayout(context);
        final int padding = context.getResources().getDimensionPixelSize(R.dimen.dialog_field_padding);
        layout.setPadding(padding, 0, padding, 0);
        layout.addView(editText);
        final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.start_server_title)
            .setMessage(context.getString(R.string.start_server_message, host, display))
            .setView(layout)
            .setPositiveButton(R.string.start_server, (d, which) -> {
                command = editText.getText().toString().trim();
                postResult(true);
            })
            .setNegativeButton(R.string.cancel, (d, which) -> postResult(false))
            .setOnCancelListener(d -> postResult(false))
            .create();
        CommandInput.configure(editText, () -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        dialog.show();
    }

    /**
     * @return the command to start in the server, or empty for none
     */
    String getCommand() {
        return command;
    }
}
