package com.example.cekpicklist.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.data.PicklistStatus
import com.example.cekpicklist.utils.Logger
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*

class PicklistSelectionAdapter(
    private var picklistStatuses: List<PicklistStatus>,
    private val onPicklistSelected: (String) -> Unit
) : RecyclerView.Adapter<PicklistSelectionAdapter.PicklistViewHolder>() {

    private var filteredPicklists: List<PicklistStatus> = picklistStatuses
    private var searchQuery: String = ""
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun updatePicklists(newPicklistStatuses: List<PicklistStatus>) {
        picklistStatuses = newPicklistStatuses
        filterPicklists(searchQuery)
    }

    fun filterPicklists(query: String) {
        searchQuery = query
        coroutineScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                if (query.isBlank()) {
                    picklistStatuses
                } else {
                    picklistStatuses.filter { picklistStatus ->
                        picklistStatus.picklistNumber.contains(query, ignoreCase = true)
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                filteredPicklists = filtered
                notifyDataSetChanged()
                Logger.Adapter.d("Filtered ${filtered.size} picklists for query: '$query'")
            }
        }
    }

    fun getFilteredCount(): Int = filteredPicklists.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PicklistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_picklist_selection, parent, false)
        return PicklistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PicklistViewHolder, position: Int) {
        val picklistStatus = filteredPicklists[position]
        Logger.Adapter.itemBound(position, picklistStatus.picklistNumber)
        holder.bind(picklistStatus, searchQuery)
    }

    override fun getItemCount(): Int = filteredPicklists.size

    inner class PicklistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPicklistNumber: TextView = itemView.findViewById(R.id.tvPicklistNumber)
        private val tvPicklistInfo: TextView = itemView.findViewById(R.id.tvPicklistInfo)
        private val ivPicklistIcon: ImageView = itemView.findViewById(R.id.ivPicklistIcon)

        fun bind(picklistStatus: PicklistStatus, searchQuery: String = "") {
            tvPicklistNumber.text = picklistStatus.picklistNumber
            
            // Set icon berdasarkan status scan
            if (picklistStatus.isScanned) {
                ivPicklistIcon.setImageResource(R.drawable.ic_check_circle_green)
                ivPicklistIcon.visibility = View.VISIBLE
                
                // Set info dengan qty yang kurang
                val remainingText = if (picklistStatus.remainingQty > 0) {
                    "Kurang ${picklistStatus.remainingQty} item"
                } else {
                    "Selesai"
                }
                tvPicklistInfo.text = remainingText
            } else {
                ivPicklistIcon.setImageResource(R.drawable.ic_arrow_down)
                ivPicklistIcon.visibility = View.VISIBLE
                
                // Set info default
                tvPicklistInfo.text = if (searchQuery.isNotBlank()) {
                    "Hasil pencarian"
                } else {
                    "Hari ini"
                }
            }
            
            // Set click listener dengan optimasi
            itemView.setOnClickListener {
                Logger.Adapter.itemClicked(position, picklistStatus.picklistNumber)
                onPicklistSelected(picklistStatus.picklistNumber)
            }
        }
    }
}
