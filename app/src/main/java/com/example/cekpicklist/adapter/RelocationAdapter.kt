package com.example.cekpicklist.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.data.RelocationItem
import com.google.android.material.card.MaterialCardView

class RelocationAdapter(
    private var items: List<RelocationItem> = emptyList(),
    private val onItemClick: (RelocationItem) -> Unit = {}
) : RecyclerView.Adapter<RelocationAdapter.RelocationViewHolder>() {

    class RelocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView as MaterialCardView
        val tvArticleName: TextView = itemView.findViewById(R.id.tvArticleName)
        val tvWarehouseTagStatus: TextView = itemView.findViewById(R.id.tvWarehouseTagStatus)
        val tvSize: TextView = itemView.findViewById(R.id.tvSize)
        val tvQty: TextView = itemView.findViewById(R.id.tvQty)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RelocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_relocation, parent, false)
        return RelocationViewHolder(view)
    }

    override fun onBindViewHolder(holder: RelocationViewHolder, position: Int) {
        val item = items[position]
        
        if (item.isValid) {
            // VALID ITEM: Tampilkan semua detail artikel
            holder.tvArticleName.text = item.articleName
            holder.tvSize.text = item.size
            holder.tvQty.text = item.qty.toString()
            holder.tvWarehouseTagStatus.visibility = View.GONE
            
            // Valid item styling - font color hijau, border hijau
            holder.tvArticleName.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.success_color))
            holder.tvSize.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.success_color))
            holder.tvQty.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.success_color))
            holder.cardView.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.success_color)
        } else {
            // INVALID ITEM: Hanya tampilkan warehouse + status + qty
            holder.tvArticleName.text = "${item.warehouse} - ${item.tagStatus}"
            holder.tvSize.text = "" // Kosongkan size untuk invalid
            holder.tvQty.text = item.qty.toString()
            holder.tvWarehouseTagStatus.visibility = View.GONE // Tidak perlu field terpisah
            
            // Invalid item styling - font color merah, border merah
            holder.tvArticleName.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.error_color))
            holder.tvSize.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.error_color))
            holder.tvQty.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.error_color))
            holder.cardView.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.error_color)
        }
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<RelocationItem>) {
        items = newItems
        
        // **DETAILED LOGGING**: Log items yang akan ditampilkan di adapter
        Log.d("RelocationAdapter", "🔥 === ADAPTER ITEMS UPDATE ===")
        Log.d("RelocationAdapter", "🔥 Total items to display: ${items.size}")
        Log.d("RelocationAdapter", "🔥 Valid items: ${items.count { it.isValid }}")
        Log.d("RelocationAdapter", "🔥 Invalid items: ${items.count { !it.isValid }}")
        items.forEachIndexed { index, item ->
            Log.d("RelocationAdapter", "🔥 Adapter Item $index: EPC=${item.epc}, Article=${item.articleName}, Valid=${item.isValid}")
            if (!item.isValid) {
                Log.d("RelocationAdapter", "🔥 ⭐ Invalid item: EPC=${item.epc}, Warehouse=${item.warehouse}, TagStatus=${item.tagStatus}")
            }
        }
        Log.d("RelocationAdapter", "🔥 === END ADAPTER ITEMS ===")
        
        notifyDataSetChanged()
    }
    
    fun getItemAt(position: Int): RelocationItem? {
        return if (position in 0 until itemCount) items[position] else null
    }
}