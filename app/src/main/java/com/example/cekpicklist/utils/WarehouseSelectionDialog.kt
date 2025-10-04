package com.example.cekpicklist.utils

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.adapter.WarehouseSelectionAdapter
import com.example.cekpicklist.data.Warehouse
import com.google.android.material.textfield.TextInputEditText

class WarehouseSelectionDialog(
    private val context: Context,
    private val warehouses: List<Warehouse>,
    private val onWarehouseSelected: (Warehouse) -> Unit
) {
    
    private var dialog: Dialog? = null
    private var adapter: WarehouseSelectionAdapter? = null
    private var filteredWarehouses: List<Warehouse> = warehouses
    
    fun show() {
        Log.d("WarehouseSelectionDialog", "🔥 Showing warehouse selection dialog with ${warehouses.size} warehouses")
        warehouses.forEachIndexed { index, warehouse ->
            Log.d("WarehouseSelectionDialog", "🔥 Warehouse[$index]: ID=${warehouse.warehouseId}, Name=${warehouse.warehouseName}")
        }
        
        dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_warehouse_selection, null)
        
        setupViews(view)
        setupRecyclerView(view)
        setupSearch(view)
        
        dialog?.setContentView(view)
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog?.show()
    }
    
    private fun setupViews(view: View) {
        val btnClose = view.findViewById<View>(R.id.btnCloseDialog)
        btnClose.setOnClickListener {
            dialog?.dismiss()
        }
    }
    
    private fun setupRecyclerView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvWarehouses)
        val emptyState = view.findViewById<LinearLayout>(R.id.llEmptyState)
        
        adapter = WarehouseSelectionAdapter(filteredWarehouses) { warehouse ->
            try {
                Log.d("WarehouseSelectionDialog", "🔥 Warehouse selected: ${warehouse.warehouseName} (${warehouse.warehouseId})")
                Log.d("WarehouseSelectionDialog", "🔥 Dialog status before callback: isShowing=${dialog?.isShowing}, isNull=${dialog == null}")
                
                onWarehouseSelected(warehouse)
                Log.d("WarehouseSelectionDialog", "🔥 Callback executed successfully")
                
                Log.d("WarehouseSelectionDialog", "🔥 Dialog status after callback: isShowing=${dialog?.isShowing}, isNull=${dialog == null}")
                Log.d("WarehouseSelectionDialog", "🔥 Calling dialog.dismiss()")
                
                // Ensure dismiss is called on UI thread
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    dialog?.dismiss()
                    Log.d("WarehouseSelectionDialog", "🔥 Dialog dismiss called on main thread")
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        dialog?.dismiss()
                        Log.d("WarehouseSelectionDialog", "🔥 Dialog dismiss called on main thread via Handler")
                    }
                }
                Log.d("WarehouseSelectionDialog", "🔥 Dialog dismiss called successfully")
            } catch (e: Exception) {
                Log.e("WarehouseSelectionDialog", "❌ Error in warehouse selection callback: ${e.message}", e)
                // Force dismiss dialog even if there's an error
                try {
                    dialog?.dismiss()
                } catch (dismissError: Exception) {
                    Log.e("WarehouseSelectionDialog", "❌ Error dismissing dialog: ${dismissError.message}", dismissError)
                }
            }
        }
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        
        // Scroll ke atas untuk memastikan semua item terlihat
        recyclerView.post {
            recyclerView.scrollToPosition(0)
            Log.d("WarehouseSelectionDialog", "🔥 RecyclerView scrolled to position 0")
        }
        
        updateEmptyState(view)
    }
    
    private fun setupSearch(view: View) {
        val etSearch = view.findViewById<TextInputEditText>(R.id.etSearchWarehouse)
        
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim().lowercase()
                filteredWarehouses = if (query.isEmpty()) {
                    warehouses
                } else {
                    warehouses.filter { warehouse ->
                        warehouse.warehouseName.lowercase().contains(query) ||
                        warehouse.warehouseId.lowercase().contains(query)
                    }
                }
                
                Log.d("WarehouseSelectionDialog", "🔥 Search query: '$query', filtered results: ${filteredWarehouses.size}")
                
                adapter?.updateWarehouses(filteredWarehouses)
                
                // Scroll ke atas setelah filter
                val recyclerView = view.findViewById<RecyclerView>(R.id.rvWarehouses)
                recyclerView.post {
                    recyclerView.scrollToPosition(0)
                    Log.d("WarehouseSelectionDialog", "🔥 RecyclerView scrolled to position 0 after search")
                }
                
                updateEmptyState(view)
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun updateEmptyState(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvWarehouses)
        val emptyState = view.findViewById<LinearLayout>(R.id.llEmptyState)
        
        if (filteredWarehouses.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }
    }
    
    fun dismiss() {
        dialog?.dismiss()
    }
}
