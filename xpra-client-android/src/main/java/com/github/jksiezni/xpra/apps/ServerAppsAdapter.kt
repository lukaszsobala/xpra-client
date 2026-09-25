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

package com.github.jksiezni.xpra.apps

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.jksiezni.xpra.R
import xpra.client.ServerApp
import java.util.IdentityHashMap

/**
 * The applications of the server: a tap starts one, its button adds it to the home screen.
 */
class ServerAppsAdapter(
    private val onLaunch: (ServerApp) -> Unit,
    private val onPin: (ServerApp, Bitmap?) -> Unit,
    /** the icon of an application without one from the server, ie: the icon of its window */
    private val fallbackIcon: (ServerApp) -> Bitmap? = { null }
) : ListAdapter<ServerApp, ServerAppsAdapter.ViewHolder>(DIFF_CALLBACK) {

    /** Decoding the icons, SVG ones especially, is too slow to do it on every bind. */
    private val icons = IdentityHashMap<ServerApp, Bitmap?>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(inflater.inflate(R.layout.server_app_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = getItem(position)
        val icon = icons.getOrPut(app) { AppShortcuts.decodeIcon(app) ?: fallbackIcon(app) }
        holder.titleView.text = app.name
        holder.categoryView.text = app.category
        holder.categoryView.visibility = if (app.category.isNullOrEmpty()) View.GONE else View.VISIBLE
        if (icon != null) {
            holder.iconView.setImageBitmap(icon)
        } else {
            holder.iconView.setImageBitmap(AppIcons.letterIcon(app.name, holder.iconSizePx))
        }
        holder.itemView.setOnClickListener { onLaunch(app) }
        holder.pinButton.setOnClickListener { onPin(app, icon) }
    }

    /**
     * Shows the icons which the applications got since, see [fallbackIcon].
     */
    fun refreshIcons() {
        icons.clear()
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCurrentListChanged(previousList: MutableList<ServerApp>, currentList: MutableList<ServerApp>) {
        icons.keys.retainAll(currentList.toSet())
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(android.R.id.icon)
        val titleView: TextView = itemView.findViewById(android.R.id.title)
        val categoryView: TextView = itemView.findViewById(android.R.id.summary)
        val pinButton: View = itemView.findViewById(R.id.pin_btn)
        val iconSizePx = itemView.resources.getDimensionPixelSize(R.dimen.app_icon_size)
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ServerApp>() {
            override fun areItemsTheSame(oldItem: ServerApp, newItem: ServerApp): Boolean {
                return oldItem.name == newItem.name && oldItem.command == newItem.command
            }

            override fun areContentsTheSame(oldItem: ServerApp, newItem: ServerApp): Boolean {
                return areItemsTheSame(oldItem, newItem) && oldItem.category == newItem.category
                    && oldItem.iconData.contentEquals(newItem.iconData)
            }
        }
    }
}
