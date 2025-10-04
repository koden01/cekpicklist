package com.example.cekpicklist.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.data.Warehouse

class WarehouseSelectionAdapter(
    private var warehouses: List<Warehouse> = emptyList(),
    private var onWarehouseSelected: ((Warehouse) -> Unit)? = null
) : RecyclerView.Adapter<WarehouseSelectionAdapter.WarehouseViewHolder>() {

    class WarehouseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvWarehouseName: TextView = itemView.findViewById(R.id.tvWarehouseName)
        val tvWarehouseId: TextView = itemView.findViewById(R.id.tvWarehouseId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WarehouseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_warehouse_selection, parent, false)
        return WarehouseViewHolder(view)
    }

    override fun onBindViewHolder(holder: WarehouseViewHolder, position: Int) {
        val warehouse = warehouses[position]
        
        holder.tvWarehouseName.text = warehouse.warehouseName
        holder.tvWarehouseId.text = "ID: ${warehouse.warehouseId}"
        
        holder.itemView.setOnClickListener {
            onWarehouseSelected?.invoke(warehouse)
        }
    }

    override fun getItemCount(): Int = warehouses.size

    fun updateWarehouses(newWarehouses: List<Warehouse>) {
        Log.d("WarehouseSelectionAdapter", "🔥 Updating warehouses: ${newWarehouses.size} warehouses")
        newWarehouses.forEachIndexed { index, warehouse ->
            Log.d("WarehouseSelectionAdapter", "🔥 Adapter Warehouse[$index]: ID=${warehouse.warehouseId}, Name=${warehouse.warehouseName}")
        }
        warehouses = newWarehouses
        notifyDataSetChanged()
        Log.d("WarehouseSelectionAdapter", "🔥 Adapter itemCount after update: ${itemCount}")
    }
}
