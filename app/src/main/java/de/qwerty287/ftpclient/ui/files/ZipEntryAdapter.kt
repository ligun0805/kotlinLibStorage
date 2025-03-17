package de.qwerty287.ftpclient.ui.files

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.qwerty287.ftpclient.R
import java.util.zip.ZipEntry as JavaZipEntry

class ZipEntryAdapter(
    private val onEntryClick: (JavaZipEntry) -> Unit
) : ListAdapter<JavaZipEntry, ZipEntryAdapter.ViewHolder>(ZipEntryDiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.textViewName)
        val sizeTextView: TextView = view.findViewById(R.id.textViewSize)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_zip_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        holder.nameTextView.text = entry.name
        holder.sizeTextView.text = if (entry.isDirectory) "Directory" else "${entry.size} bytes"
        
        holder.itemView.setOnClickListener {
            onEntryClick(entry)
        }
    }

    private class ZipEntryDiffCallback : DiffUtil.ItemCallback<JavaZipEntry>() {
        override fun areItemsTheSame(oldItem: JavaZipEntry, newItem: JavaZipEntry): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: JavaZipEntry, newItem: JavaZipEntry): Boolean {
            return oldItem.name == newItem.name && 
                   oldItem.size == newItem.size &&
                   oldItem.isDirectory == newItem.isDirectory
        }
    }
} 