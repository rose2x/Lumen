package com.vironix.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * DistroAdapter
 *
 * Feeds the list of supported distros (from DistroCatalog) into the
 * RecyclerView on the picker screen, and reports which one is
 * already-installed vs needs-downloading.
 */
class DistroAdapter(
    private val distros: List<Distro>,
    private val installedIds: Set<DistroId>,
    private val onClick: (Distro) -> Unit
) : RecyclerView.Adapter<DistroAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.distroName)
        val details: TextView = view.findViewById(R.id.distroDetails)
        val status: TextView = view.findViewById(R.id.distroStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_distro, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val distro = distros[position]
        holder.name.text = distro.displayName
        holder.details.text = "${distro.packageManagerHint}  •  ${distro.approxDownloadSize}"
        holder.status.text = if (installedIds.contains(distro.id)) "✓ Installed — tap to open" else "Not installed — tap to download & install"
        holder.itemView.setOnClickListener { onClick(distro) }
    }

    override fun getItemCount(): Int = distros.size
}
