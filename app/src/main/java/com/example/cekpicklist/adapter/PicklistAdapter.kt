package com.example.cekpicklist.adapter

import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.data.PicklistItem
import com.example.cekpicklist.data.QtyStatus

class PicklistAdapter : RecyclerView.Adapter<PicklistAdapter.ViewHolder>() {
    
    private var items: List<PicklistItem> = emptyList()
    private var onItemDeleteListener: OnItemDeleteListener? = null
    private var onItemClickListener: ((PicklistItem) -> Unit)? = null
    
    interface OnItemDeleteListener {
        fun onItemDelete(position: Int, item: PicklistItem)
    }
    
    fun setOnItemDeleteListener(listener: OnItemDeleteListener) {
        onItemDeleteListener = listener
    }
    
    fun setOnItemClickListener(listener: (PicklistItem) -> Unit) {
        onItemClickListener = listener
    }
    
    fun updateItems(newItems: List<PicklistItem>) {
        val oldSize = items.size
        android.util.Log.d("PicklistAdapter", "🔥 updateItems: oldSize=$oldSize, newSize=${newItems.size}")
        android.util.Log.d("PicklistAdapter", "🔥 New items preview:")
        newItems.take(3).forEach { item ->
            android.util.Log.d("PicklistAdapter", "🔥   - ${item.articleName} ${item.size}: qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan}, status=${item.getQtyStatus()}")
        }
        
        items = newItems
        
        if (oldSize == 0) {
            android.util.Log.d("PicklistAdapter", "🔥 notifyItemRangeInserted: 0 to ${newItems.size}")
            notifyItemRangeInserted(0, newItems.size)
        } else if (newItems.size == 0) {
            android.util.Log.d("PicklistAdapter", "🔥 notifyItemRangeRemoved: 0 to $oldSize")
            notifyItemRangeRemoved(0, oldSize)
        } else {
            android.util.Log.d("PicklistAdapter", "🔥 notifyDataSetChanged")
            notifyDataSetChanged() // Fallback for complex changes
        }
    }
    
    fun getItemAt(position: Int): PicklistItem? {
        return if (position in 0 until items.size) items[position] else null
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_picklist, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        android.util.Log.d("PicklistAdapter", "🔥 onBindViewHolder: position=$position, totalItems=${items.size}")
        android.util.Log.d("PicklistAdapter", "🔥 Binding item: ${item.articleName} ${item.size}")
        holder.bind(item)
    }
    
    override fun getItemCount(): Int = items.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: androidx.cardview.widget.CardView = itemView.findViewById(R.id.cardView)
        private val deleteBackground: LinearLayout = itemView.findViewById(R.id.deleteBackground)
        private val deleteIcon: ImageView = itemView.findViewById(R.id.deleteIcon)
        private val tvArticleName: TextView = itemView.findViewById(R.id.tvArticleName)
        private val tvSize: TextView = itemView.findViewById(R.id.tvSize)
        private val tvQtyPl: TextView = itemView.findViewById(R.id.tvQtyPl)
        private val tvQtyScan: TextView = itemView.findViewById(R.id.tvQtyScan)
        private val tvNAInfo: TextView = itemView.findViewById(R.id.tvNAInfo)
        
        private var isRevealed = false
        private var revealDistance = 0f // Akan dihitung berdasarkan lebar container
        
        fun bind(item: PicklistItem) {
            // **DEBUG**: Log detail binding
            android.util.Log.d("PicklistAdapter", "🔥 === BINDING ITEM ===")
            android.util.Log.d("PicklistAdapter", "🔥 Article: ${item.articleName}")
            android.util.Log.d("PicklistAdapter", "🔥 Size: ${item.size}")
            android.util.Log.d("PicklistAdapter", "🔥 qtyPl: ${item.qtyPl}")
            android.util.Log.d("PicklistAdapter", "🔥 qtyScan: ${item.qtyScan}")
            android.util.Log.d("PicklistAdapter", "🔥 Status: ${item.getQtyStatus()}")
            android.util.Log.d("PicklistAdapter", "🔥 isComplete: ${item.isComplete()}")
            
            tvArticleName.text = item.articleName
            tvSize.text = item.size
            tvQtyPl.text = item.qtyPl.toString()
            
            // **PERBAIKAN**: Tampilkan RFID detect qty tanpa format overscan (+1)
            val qtyScanText = when {
                item.qtyScan == item.qtyPl && item.qtyPl > 0 -> {
                    "${item.qtyScan} ✓"
                }
                else -> {
                    item.qtyScan.toString()
                }
            }
            tvQtyScan.text = qtyScanText
            
            // **LOGGING**: Log detail RFID detect qty
            android.util.Log.d("PicklistAdapter", "🔥 RFID DETECT QTY: ${item.articleName} ${item.size}")
            android.util.Log.d("PicklistAdapter", "🔥   - qtyPl (planned): ${item.qtyPl}")
            android.util.Log.d("PicklistAdapter", "🔥   - qtyScan (detected): ${item.qtyScan}")
            android.util.Log.d("PicklistAdapter", "🔥   - Display text: $qtyScanText")
            if (item.qtyScan > item.qtyPl) {
                android.util.Log.w("PicklistAdapter", "🔥   - OVERSCAN: +${item.qtyScan - item.qtyPl} items")
            }
            
            // Hitung jarak geser berdasarkan lebar area merah yang sebenarnya (25%)
            itemView.post {
                val containerWidth = itemView.width.toFloat()
                // Area merah memiliki layout_weight="1" dari total weight="4" (3+1)
                // Jadi lebar area merah = containerWidth * (1/4) = containerWidth * 0.25f
                revealDistance = containerWidth * (1f / 4f) // 25% dari lebar container
                android.util.Log.d("PicklistAdapter", "🔥 Container width: ${containerWidth}px, Reveal distance: ${revealDistance}px (25%)")
            }
            
            // Reset card position
            cardView.translationX = 0f
            deleteBackground.visibility = View.GONE
            
            // Tampilkan informasi NA jika item memiliki nilai NA
            val naInfo = item.getNAInfo()
            if (naInfo != null) {
                tvNAInfo.text = naInfo
                tvNAInfo.visibility = View.VISIBLE
                android.util.Log.d("PicklistAdapter", "🔥 NA Item: ${item.articleName} - $naInfo")
            } else {
                tvNAInfo.visibility = View.GONE
            }
            
            // Set warna font berdasarkan status quantity (tanpa background)
            val status = item.getQtyStatus()
            android.util.Log.d("PicklistAdapter", "🔥 bind: Article=${item.articleName}, qtyPl=${item.qtyPl}, qtyScan=${item.qtyScan}, Status=$status")
            
            when (status) {
                QtyStatus.RED -> {
                    // Show entire row untuk item yang kurang
                    itemView.visibility = View.VISIBLE
                    tvQtyScan.visibility = View.VISIBLE
                    tvQtyScan.background = null
                    tvQtyScan.setTextColor(Color.RED)
                    android.util.Log.d("PicklistAdapter", "🔥 Set RED color untuk: ${item.articleName}")
                    android.util.Log.d("PicklistAdapter", "🔥 RED item visibility: itemView=${itemView.visibility}, tvQtyScan=${tvQtyScan.visibility}")
                }
                QtyStatus.YELLOW -> {
                    // Show entire row untuk item yang overscan/non-picklist
                    itemView.visibility = View.VISIBLE
                    tvQtyScan.visibility = View.VISIBLE
                    tvQtyScan.background = null
                    tvQtyScan.setTextColor(Color.parseColor("#FFCC00")) // Kuning
                    android.util.Log.d("PicklistAdapter", "🔥 Set YELLOW color untuk: ${item.articleName}")
                }
                QtyStatus.GREEN -> {
                    // Hide entire row untuk item yang sudah complete (sesuai)
                    itemView.visibility = View.GONE
                    android.util.Log.d("PicklistAdapter", "🔥 Hide entire row untuk: ${item.articleName} (GREEN/Complete)")
                }
            }
            
            // Tap membuka modal EPC list untuk artikel
            cardView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClickListener?.invoke(item)
                }
            }
            
            // Setup click listener untuk delete background
            deleteBackground.setOnClickListener {
                // Trigger delete action dengan konfirmasi
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemDeleteListener?.onItemDelete(adapterPosition, item)
                }
            }
        }
        
        private fun revealDeleteArea() {
            isRevealed = true
            deleteBackground.visibility = View.VISIBLE
            
            // Simple slide animation - geser ke kiri sejauh lebar area merah (25%)
            val slideAnimator = ObjectAnimator.ofFloat(cardView, "translationX", 0f, -revealDistance)
            slideAnimator.duration = 200
            slideAnimator.start()
            
            android.util.Log.d("PicklistAdapter", "🔥 Reveal delete area untuk: ${tvArticleName.text}, distance: ${revealDistance}px")
        }
        
        private fun hideDeleteArea() {
            isRevealed = false
            
            // Simple slide back animation - kembali ke posisi normal
            val slideAnimator = ObjectAnimator.ofFloat(cardView, "translationX", -revealDistance, 0f)
            slideAnimator.duration = 200
            
            slideAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    deleteBackground.visibility = View.GONE
                }
            })
            
            slideAnimator.start()
            
            android.util.Log.d("PicklistAdapter", "🔥 Hide delete area untuk: ${tvArticleName.text}, distance: ${revealDistance}px")
        }
        
        
        fun resetPosition() {
            isRevealed = false
            cardView.translationX = 0f
            deleteBackground.visibility = View.GONE
        }
    }
}
