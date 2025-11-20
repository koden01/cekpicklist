package com.example.cekpicklist.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.data.ReturAggregatedItem
import com.google.android.material.card.MaterialCardView

class ReturAdapter : RecyclerView.Adapter<ReturAdapter.ReturViewHolder>() {

    private val items = mutableListOf<ReturAggregatedItem>()

    fun submitList(newItems: List<ReturAggregatedItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItemAt(position: Int): ReturAggregatedItem? {
        return if (position in items.indices) items[position] else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReturViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_retur, parent, false)
        return ReturViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReturViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ReturViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvArticleName: TextView = itemView.findViewById(R.id.tvArticleName)
        private val tvSize: TextView = itemView.findViewById(R.id.tvSize)
        private val tvQty: TextView = itemView.findViewById(R.id.tvQty)
        private val cardView: MaterialCardView = itemView as MaterialCardView

        fun bind(item: ReturAggregatedItem) {
            val context = itemView.context
            val successColor = ContextCompat.getColor(context, R.color.success_color)
            val dangerColor = ContextCompat.getColor(context, android.R.color.holo_red_dark)
            val dangerBg = ContextCompat.getColor(context, R.color.retur_invalid_background)

            if (item.isValid) {
                tvArticleName.text = item.articleName.ifBlank { item.articleId.ifBlank { "-" } }
                tvSize.text = item.size.ifBlank { "-" }
            } else {
                val warehouseLabel = item.sourceWarehouse.ifBlank { "Warehouse tidak diketahui" }
                val statusLabel = item.statusLabel.ifBlank { "STATUS?" }
                tvArticleName.text = warehouseLabel
                tvSize.text = statusLabel.uppercase()
            }
            tvQty.text = item.quantity.toString()

            if (item.isValid) {
                cardView.strokeColor = successColor
                cardView.setCardBackgroundColor(android.graphics.Color.WHITE)
                tvArticleName.setTextColor(successColor)
                tvSize.setTextColor(successColor)
                tvQty.setTextColor(successColor)
            } else {
                cardView.strokeColor = dangerColor
                cardView.setCardBackgroundColor(dangerBg)
                tvArticleName.setTextColor(dangerColor)
                tvSize.setTextColor(dangerColor)
                tvQty.setTextColor(dangerColor)
            }
        }
    }
}

