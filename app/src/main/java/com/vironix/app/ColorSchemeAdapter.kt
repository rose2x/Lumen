package com.vironix.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * ColorSchemeAdapter
 *
 * Shows each available ColorScheme as a row with a small color swatch,
 * its name, and a checkmark on whichever one is currently selected.
 */
class ColorSchemeAdapter(
    private val schemes: List<ColorScheme>,
    private var selectedId: String,
    private val onSelect: (ColorScheme) -> Unit
) : RecyclerView.Adapter<ColorSchemeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val swatch: View = view.findViewById(R.id.swatch)
        val name: TextView = view.findViewById(R.id.schemeName)
        val check: TextView = view.findViewById(R.id.schemeCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_color_scheme, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val scheme = schemes[position]
        holder.name.text = scheme.displayName
        holder.swatch.setBackgroundColor(scheme.background)
        holder.check.visibility = if (scheme.id == selectedId) View.VISIBLE else View.INVISIBLE
        holder.itemView.setOnClickListener {
            val previouslySelected = selectedId
            selectedId = scheme.id
            onSelect(scheme)
            notifyItemChanged(schemes.indexOfFirst { it.id == previouslySelected })
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = schemes.size
}
