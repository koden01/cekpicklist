package com.example.cekpicklist.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.cekpicklist.R
import com.example.cekpicklist.data.Warehouse

class WarehouseAdapter(
    private val context: Context,
    private var warehouses: List<Warehouse> = emptyList()
) : androidx.recyclerview.widget.RecyclerView.Adapter<WarehouseAdapter.WarehouseViewHolder>() {

    class WarehouseViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val tvWarehouseName: TextView = itemView.findViewById(R.id.tvWarehouseName)
        val tvWarehouseId: TextView = itemView.findViewById(R.id.tvWarehouseId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WarehouseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_warehouse_dropdown, parent, false)
        return WarehouseViewHolder(view)
    }

    override fun onBindViewHolder(holder: WarehouseViewHolder, position: Int) {
        val warehouse = warehouses[position]
        
        holder.tvWarehouseName.text = warehouse.warehouseName
        holder.tvWarehouseId.text = "ID: ${warehouse.warehouseId}"
    }

    override fun getItemCount(): Int = warehouses.size

    fun updateWarehouses(newWarehouses: List<Warehouse>) {
        warehouses = newWarehouses
        notifyDataSetChanged()
    }
}

/**
 * ArrayAdapter untuk AutoCompleteTextView warehouse dropdown
 */
class WarehouseArrayAdapter(
    context: Context,
    private var warehouses: List<Warehouse> = emptyList()
) : android.widget.ArrayAdapter<Warehouse>(context, android.R.layout.simple_dropdown_item_1line, mutableListOf<Warehouse>()) {

    override fun getCount(): Int = warehouses.size

    override fun getItem(position: Int): Warehouse = warehouses[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_dropdown_item_1line, parent, false)
        
        val textView = view.findViewById<TextView>(android.R.id.text1)
        val warehouse = warehouses[position]
        textView.text = warehouse.warehouseName
        
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_dropdown_item_1line, parent, false)
        
        val textView = view.findViewById<TextView>(android.R.id.text1)
        val warehouse = warehouses[position]
        textView.text = warehouse.warehouseName
        
        return view
    }

    fun updateWarehouses(newWarehouses: List<Warehouse>) {
        warehouses = newWarehouses
        notifyDataSetChanged()
    }
}
