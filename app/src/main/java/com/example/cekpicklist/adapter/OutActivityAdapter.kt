package com.example.cekpicklist.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.data.OutActivityItem

class OutActivityAdapter(
    private var items: List<GroupedOutItem> = emptyList(),
    private val onItemClick: (GroupedOutItem) -> Unit = {}
) : RecyclerView.Adapter<OutActivityAdapter.ViewHolder>() {

    data class GroupedOutItem(
        val articleId: String,
        val articleName: String,
        val size: String,
        val qty: Int
    )

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvArticleName: TextView = itemView.findViewById(R.id.tvArticleName)
        val tvSize: TextView = itemView.findViewById(R.id.tvSize)
        val tvQty: TextView = itemView.findViewById(R.id.tvQty)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_out_activity, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvArticleName.text = item.articleName
        holder.tvSize.text = item.size
        holder.tvQty.text = item.qty.toString()
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<OutActivityItem>) {
        // Group by articleId + size, sum qty, keep articleName from first
        val grouped = newItems
            .groupBy { Pair(it.articleId, it.size) }
            .map { (_, group) ->
                val first = group.first()
                GroupedOutItem(
                    articleId = first.articleId,
                    articleName = first.articleName,
                    size = first.size,
                    qty = group.sumOf { it.qty }
                )
            }
            .sortedBy { it.articleName }
        items = grouped
        notifyDataSetChanged()
    }

    fun getItemAt(position: Int): GroupedOutItem? {
        return if (position in 0 until itemCount) items[position] else null
    }
}
