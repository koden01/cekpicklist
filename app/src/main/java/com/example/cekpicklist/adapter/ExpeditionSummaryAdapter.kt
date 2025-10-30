package com.example.cekpicklist.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.data.ExpeditionSummary

/**
 * Adapter untuk expedition summary cards (sama dengan web app)
 */
class ExpeditionSummaryAdapter(
    private val onExpeditionClick: (ExpeditionSummary) -> Unit
) : RecyclerView.Adapter<ExpeditionSummaryAdapter.ExpeditionViewHolder>() {
    
    private var expeditions: List<ExpeditionSummary> = emptyList()
    
    fun updateExpeditions(newExpeditions: List<ExpeditionSummary>) {
        expeditions = newExpeditions
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpeditionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expedition_summary, parent, false)
        return ExpeditionViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ExpeditionViewHolder, position: Int) {
        holder.bind(expeditions[position])
    }
    
    override fun getItemCount(): Int = expeditions.size
    
    inner class ExpeditionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvExpeditionName)
        private val tvTotalTransaksi: TextView = itemView.findViewById(R.id.tvTotalTransaksi)
        private val tvTotalScan: TextView = itemView.findViewById(R.id.tvTotalScan)
        private val tvSisa: TextView = itemView.findViewById(R.id.tvSisa)
        private val tvJumlahKarung: TextView = itemView.findViewById(R.id.tvJumlahKarung)
        private val tvTotalBatal: TextView = itemView.findViewById(R.id.tvTotalBatal)
        private val tvTotalScanFollowUp: TextView = itemView.findViewById(R.id.tvTotalScanFollowUp)
        
        fun bind(expedition: ExpeditionSummary) {
            tvName.text = expedition.name
            tvTotalTransaksi.text = expedition.totalTransaksi.toString()
            tvTotalScan.text = expedition.totalScan.toString()
            tvSisa.text = expedition.sisa.toString()
            tvJumlahKarung.text = expedition.jumlahKarung.toString()
            tvTotalBatal.text = expedition.totalBatal.toString()
            tvTotalScanFollowUp.text = expedition.totalScanFollowUp.toString()
            
            // Set click listener
            itemView.setOnClickListener {
                onExpeditionClick(expedition)
            }
        }
    }
}
