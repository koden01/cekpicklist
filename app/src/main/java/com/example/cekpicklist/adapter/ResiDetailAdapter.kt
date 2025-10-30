package com.example.cekpicklist.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.data.ResiDetail

/**
 * Adapter untuk tabel detail resi (sama dengan web app)
 */
class ResiDetailAdapter(
    private val onResiAction: (String, String, Any?) -> Unit
) : RecyclerView.Adapter<ResiDetailAdapter.ResiViewHolder>() {
    
    private var resiDetails: List<ResiDetail> = emptyList()
    
    fun updateResiDetails(newDetails: List<ResiDetail>) {
        resiDetails = newDetails
        notifyDataSetChanged()
    }
    
    fun getCurrentData(): List<ResiDetail> = resiDetails
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_resi_detail, parent, false)
        return ResiViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ResiViewHolder, position: Int) {
        holder.bind(resiDetails[position])
    }
    
    override fun getItemCount(): Int = resiDetails.size
    
    inner class ResiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvResi: TextView = itemView.findViewById(R.id.tvResi)
        private val tvOrderNo: TextView = itemView.findViewById(R.id.tvOrderNo)
        private val tvChannelSales: TextView = itemView.findViewById(R.id.tvChannelSales)
        private val tvCourierName: TextView = itemView.findViewById(R.id.tvCourierName)
        private val tvCreated: TextView = itemView.findViewById(R.id.tvCreated)
        private val tvDateTrans: TextView = itemView.findViewById(R.id.tvDateTrans)
        private val tvFlag: TextView = itemView.findViewById(R.id.tvFlag)
        private val tvCekfu: TextView = itemView.findViewById(R.id.tvCekfu)
        private val tvNoKarung: TextView = itemView.findViewById(R.id.tvNoKarung)
        private val tvSchedule: TextView = itemView.findViewById(R.id.tvSchedule)
        private val tvKeterangan: TextView = itemView.findViewById(R.id.tvKeterangan)
        
        fun bind(resi: ResiDetail) {
            tvResi.text = resi.Resi
            tvOrderNo.text = resi.orderno ?: "-"
            tvChannelSales.text = resi.chanelsales ?: "-"
            tvCourierName.text = resi.couriername ?: "-"
            tvCreated.text = resi.created ?: "-"
            tvDateTrans.text = resi.datetrans ?: "-"
            tvFlag.text = resi.flag ?: "-"
            tvCekfu.text = resi.cekfuStatus
            tvNoKarung.text = resi.nokarung ?: "-"
            tvSchedule.text = resi.schedule ?: "-"
            tvKeterangan.text = resi.Keterangan ?: "-"
            
            // Set status color
            val statusColor = when (resi.status) {
                "Batal" -> 0xFFEF4444.toInt()
                "Sudah Kirim" -> 0xFF10B981.toInt()
                "Belum Kirim" -> 0xFFF59E0B.toInt()
                else -> 0xFF6B7280.toInt()
            }
            tvFlag.setTextColor(statusColor)
            tvSchedule.setTextColor(statusColor)
        }
    }
}
