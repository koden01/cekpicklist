package com.example.cekpicklist.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
        private val iconBackground: FrameLayout = itemView.findViewById(R.id.flIconBackground)

        fun bind(picklistStatus: PicklistStatus, searchQuery: String = "") {
            tvPicklistNumber.text = picklistStatus.picklistNumber
            
            // Set icon dan warna berdasarkan status scan dengan detail selesai/sisa
            if (picklistStatus.isScanned) {
                // Picklist sudah pernah di-scan
                ivPicklistIcon.setImageResource(R.drawable.ic_check_circle_green)
                ivPicklistIcon.visibility = View.VISIBLE
                
                // Set background icon dengan warna hijau
                iconBackground.setBackgroundResource(R.drawable.ic_status_background_green)
                
                // Set warna teks untuk menunjukkan sudah di-scan
                tvPicklistNumber.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                
                // Set info detail berdasarkan status completion
                val statusText = when {
                    picklistStatus.remainingQty == 0 && picklistStatus.overscanQty == 0 -> "✅ Selesai"
                    picklistStatus.remainingQty == 0 && picklistStatus.overscanQty > 0 -> "⚠️ Overscan (+${picklistStatus.overscanQty})"
                    picklistStatus.remainingQty > 0 -> "⚠️ Sisa ${picklistStatus.remainingQty} qty"
                    else -> "✅ Sudah di-scan"
                }
                
                tvPicklistInfo.text = statusText
                tvPicklistInfo.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                
            } else {
                // Picklist belum pernah di-scan sama sekali
                ivPicklistIcon.setImageResource(R.drawable.ic_arrow_down)
                ivPicklistIcon.visibility = View.VISIBLE
                
                // Set background icon default
                iconBackground.setBackgroundResource(R.drawable.ic_status_background)
                
                // Set warna teks default
                tvPicklistNumber.setTextColor(itemView.context.getColor(android.R.color.black))
                
                // Set info untuk belum scan sama sekali
                tvPicklistInfo.text = "📋 Belum scan sama sekali"
                tvPicklistInfo.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
            }
            
            // Set click listener dengan optimasi
            itemView.setOnClickListener {
                Logger.Adapter.itemClicked(adapterPosition, picklistStatus.picklistNumber)
                onPicklistSelected(picklistStatus.picklistNumber)
            }
        }
    }
}
