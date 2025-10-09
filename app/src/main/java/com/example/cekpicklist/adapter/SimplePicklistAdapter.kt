package com.example.cekpicklist.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.api.SupabaseService.SimplePicklist
import com.example.cekpicklist.utils.Logger
import kotlinx.coroutines.*

class SimplePicklistAdapter(
    private var simplePicklists: List<SimplePicklist>,
    private val onPicklistSelected: (String) -> Unit
) : RecyclerView.Adapter<SimplePicklistAdapter.SimplePicklistViewHolder>() {

    private var filteredPicklists: List<SimplePicklist> = simplePicklists
    private var searchQuery: String = ""
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun updatePicklists(newSimplePicklists: List<SimplePicklist>) {
        simplePicklists = newSimplePicklists
        filterPicklists(searchQuery)
        
        // Log summary untuk debugging
        val total = newSimplePicklists.size
        val scanned = newSimplePicklists.count { it.isScanned }
        val notScanned = newSimplePicklists.count { !it.isScanned }
        
        Logger.Adapter.d("📊 Simple Picklist Summary: Total=$total, Scanned=$scanned, Not Scanned=$notScanned")
    }

    fun filterPicklists(query: String) {
        searchQuery = query
        coroutineScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                if (query.isBlank()) {
                    simplePicklists
                } else {
                    simplePicklists.filter { simplePicklist ->
                        simplePicklist.noPicklist.contains(query, ignoreCase = true)
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                filteredPicklists = filtered
                notifyDataSetChanged()
                Logger.Adapter.d("🔍 Filtered ${filtered.size} picklists from ${simplePicklists.size} total")
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimplePicklistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_simple_picklist, parent, false)
        return SimplePicklistViewHolder(view)
    }

    override fun onBindViewHolder(holder: SimplePicklistViewHolder, position: Int) {
        val simplePicklist = filteredPicklists[position]
        holder.bind(simplePicklist, searchQuery)
    }

    override fun getItemCount(): Int = filteredPicklists.size

    inner class SimplePicklistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPicklistNumber: TextView = itemView.findViewById(R.id.tvPicklistNumber)
        private val tvPicklistStatus: TextView = itemView.findViewById(R.id.tvPicklistStatus)

        fun bind(simplePicklist: SimplePicklist, searchQuery: String = "") {
            // Tampilkan nomor picklist
            tvPicklistNumber.text = simplePicklist.noPicklist

            // Set warna dan status berdasarkan scan
            if (simplePicklist.isScanned) {
                // Picklist sudah di-scan - set warna hijau
                tvPicklistNumber.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                tvPicklistStatus.text = "✅ Sudah di-scan"
                tvPicklistStatus.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
            } else {
                // Picklist belum di-scan - set warna default
                tvPicklistNumber.setTextColor(itemView.context.getColor(android.R.color.black))
                tvPicklistStatus.text = "📋 Belum di-scan"
                tvPicklistStatus.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
            }

            // Set click listener
            itemView.setOnClickListener {
                Logger.Adapter.itemClicked(bindingAdapterPosition, simplePicklist.noPicklist)
                onPicklistSelected(simplePicklist.noPicklist)
            }
        }
    }
}
