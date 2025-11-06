package com.example.cekpicklist.adapter

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.viewmodel.ScanHistoryItem
import java.text.SimpleDateFormat
import java.util.*

class BarcodeHistoryAdapter(
    private var items: List<ScanHistoryItem>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<BarcodeHistoryAdapter.ViewHolder>() {
    
    // Expose items untuk akses dari fragment
    val itemsList: List<ScanHistoryItem>
        get() = items

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvResi: TextView = view.findViewById(R.id.tvResi)
        val tvExpedisi: TextView = view.findViewById(R.id.tvExpedisi)
        val tvKarung: TextView = view.findViewById(R.id.tvKarung)
        val tvCreated: TextView = view.findViewById(R.id.tvCreated)
        val tvSchedule: TextView = view.findViewById(R.id.tvSchedule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_barcode_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        holder.tvResi.text = item.Resi
        holder.tvExpedisi.text = item.Keterangan ?: "-"
        // Format karung: hanya angka tanpa "Karung"
        holder.tvKarung.text = item.nokarung ?: "-"
        
        // Format waktu - SELALU gunakan kolom created dari database (format ringkas untuk layout compact)
        // **PENTING**: Data disimpan di Supabase dalam format UTC (ISO 8601)
        // Support format: "2024-12-01T17:00:00.000Z" atau "2024-12-01T17:00:00.000+00:00"
        // Tanggal yang ditampilkan MENGIKUTI tanggal di Supabase (UTC), TIDAK dikonversi ke timezone device
        val createdText = try {
            if (!item.created.isNullOrBlank()) {
                // **PERBAIKAN**: Support multiple ISO 8601 formats
                // Format 1: "2024-11-01T10:30:00.000Z" (dengan Z)
                // Format 2: "2025-10-29T09:15:26.678+00:00" (dengan offset)
                // **STRATEGI**: Coba parse sebagai OffsetDateTime dulu (untuk format dengan offset), jika gagal baru parse sebagai Instant
                val instant = try {
                    // Coba parse sebagai OffsetDateTime (untuk format dengan +00:00 atau -00:00)
                    java.time.OffsetDateTime.parse(item.created).toInstant()
                } catch (_: Exception) {
                    // Jika gagal, coba parse sebagai Instant (untuk format dengan Z)
                    java.time.Instant.parse(item.created)
                }
                
                // **TAMPILKAN TANPA KONVERSI TIMEZONE**: Gunakan UTC untuk memastikan tanggal sesuai Supabase
                // Ini memastikan tanggal yang ditampilkan sama dengan tanggal saat disimpan di Supabase
                val zonedDateTime = instant.atZone(java.time.ZoneOffset.UTC)
                
                // Format ringkas: "dd/MM HH:mm" (contoh: "01/11 10:30")
                val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.getDefault())
                zonedDateTime.format(formatter)
            } else {
                // Fallback ke timestamp hanya jika created benar-benar null/empty
                Log.w("BarcodeHistoryAdapter", "⚠️ created is null/empty for resi ${item.Resi}, using timestamp fallback")
                val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                dateFormat.format(Date(item.timestamp))
            }
        } catch (e: Exception) {
            // Jika parsing created gagal, fallback ke timestamp
            Log.w("BarcodeHistoryAdapter", "⚠️ Failed to parse created '${item.created}' for resi ${item.Resi}: ${e.message}")
            val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            dateFormat.format(Date(item.timestamp))
        }
        holder.tvCreated.text = createdText
        
        // Schedule badge
        val schedule = item.schedule ?: "ontime"
        holder.tvSchedule.text = schedule.uppercase()
        val scheduleColor = when (schedule.lowercase()) {
            "ontime" -> ContextCompat.getColor(holder.itemView.context, R.color.success_color) ?: Color.parseColor("#10B981")
            "late" -> ContextCompat.getColor(holder.itemView.context, R.color.warning_color) ?: Color.parseColor("#F59E0B")
            "batal" -> ContextCompat.getColor(holder.itemView.context, R.color.error_color) ?: Color.parseColor("#EF4444")
            else -> Color.parseColor("#6B7280")
        }
        holder.tvSchedule.setBackgroundColor(scheduleColor)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ScanHistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getItem(position: Int): ScanHistoryItem {
        return items[position]
    }
}

