package com.example.cekpicklist.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.adapter.BarcodeHistoryAdapter
import com.example.cekpicklist.viewmodel.BarcodeScannerViewModel
import com.example.cekpicklist.viewmodel.ScanHistoryItem
import com.google.android.material.snackbar.Snackbar

class BarcodeHistoryFragment : Fragment() {

    private lateinit var viewModel: BarcodeScannerViewModel
    private lateinit var etSearchHistory: EditText
    private lateinit var btnClearFilter: Button
    private lateinit var tvHistoryCount: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var layoutEmptyHistory: LinearLayout
    private lateinit var historyAdapter: BarcodeHistoryAdapter
    private var allHistoryItems: List<ScanHistoryItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_barcode_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[BarcodeScannerViewModel::class.java]
        
        initViews(view)
        setupClickListeners()
        setupRecyclerView()
        setupObservers()
    }

    private fun initViews(view: View) {
        etSearchHistory = view.findViewById(R.id.etSearchHistory)
        btnClearFilter = view.findViewById(R.id.btnClearFilter)
        tvHistoryCount = view.findViewById(R.id.tvHistoryCount)
        rvHistory = view.findViewById(R.id.rvHistory)
        layoutEmptyHistory = view.findViewById(R.id.layoutEmptyHistory)
    }

    private fun setupClickListeners() {
        btnClearFilter.setOnClickListener {
            etSearchHistory.text.clear()
            filterHistory("")
        }

        etSearchHistory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                filterHistory(query)
            }
        })
    }
    
    private fun filterHistory(query: String) {
        if (query.isBlank()) {
            // Tampilkan semua data
            historyAdapter.updateItems(allHistoryItems)
            tvHistoryCount.text = "${allHistoryItems.size} item"
            updateEmptyState(allHistoryItems.isEmpty())
            return
        }
        
        // Filter berdasarkan query (case-insensitive)
        val queryLower = query.lowercase()
        val filtered = allHistoryItems.filter { item ->
            // Cari di Resi
            item.Resi.lowercase().contains(queryLower) ||
            // Cari di Expedisi (Keterangan)
            (item.Keterangan?.lowercase()?.contains(queryLower) == true) ||
            // Cari di Karung
            (item.nokarung?.lowercase()?.contains(queryLower) == true) ||
            // Cari di Status/Schedule
            (item.schedule?.lowercase()?.contains(queryLower) == true) ||
            // Cari di Waktu (created) - raw ISO 8601
            (item.created?.lowercase()?.contains(queryLower) == true) ||
            // Cari di formatted waktu (dd/MM HH:mm) untuk layout compact
            (try {
                if (item.created.isNullOrBlank()) false
                else {
                    // Support format dengan offset (+00:00) dan format dengan Z
                    // Coba parse sebagai OffsetDateTime dulu, jika gagal baru parse sebagai Instant
                    val instant = try {
                        java.time.OffsetDateTime.parse(item.created).toInstant()
                    } catch (_: Exception) {
                        java.time.Instant.parse(item.created)
                    }
                    val dateFormat = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    dateFormat.format(java.util.Date.from(instant)).lowercase().contains(queryLower)
                }
            } catch (e: Exception) {
                false
            }) ||
            // Cari di formatted waktu lengkap (dd/MM/yyyy HH:mm:ss) sebagai alternatif
            (try {
                if (item.created.isNullOrBlank()) false
                else {
                    // Support format dengan offset (+00:00) dan format dengan Z
                    // Coba parse sebagai OffsetDateTime dulu, jika gagal baru parse sebagai Instant
                    val instant = try {
                        java.time.OffsetDateTime.parse(item.created).toInstant()
                    } catch (_: Exception) {
                        java.time.Instant.parse(item.created)
                    }
                    val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    dateFormat.format(java.util.Date.from(instant)).lowercase().contains(queryLower)
                }
            } catch (e: Exception) {
                false
            })
        }
        
        historyAdapter.updateItems(filtered)
        tvHistoryCount.text = "${filtered.size} item"
        updateEmptyState(filtered.isEmpty())
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty && !etSearchHistory.text.toString().trim().isBlank()) {
            rvHistory.visibility = View.GONE
            layoutEmptyHistory.visibility = View.VISIBLE
        } else if (isEmpty) {
            rvHistory.visibility = View.GONE
            layoutEmptyHistory.visibility = View.VISIBLE
        } else {
            rvHistory.visibility = View.VISIBLE
            layoutEmptyHistory.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        
        // Setup adapter
        historyAdapter = BarcodeHistoryAdapter(emptyList<ScanHistoryItem>()) { resi ->
            // Delete callback
            viewModel.deleteScanHistoryItem(resi)
        }
        rvHistory.adapter = historyAdapter
        
        // Setup swipe to delete
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_POSITION) return
                
                val item = historyAdapter.getItem(position)
                val resi = item.Resi
                
                // Hapus item dari adapter (optimistic UI update)
                val currentItems = historyAdapter.itemsList.toMutableList()
                if (position >= 0 && position < currentItems.size) {
                    currentItems.removeAt(position)
                    historyAdapter.updateItems(currentItems)
                }
                
                // Hapus dari ViewModel (cache + Supabase)
                viewModel.deleteScanHistoryItem(resi)
                
                // Show snackbar untuk undo (optional)
                Snackbar.make(
                    rvHistory,
                    "Resi $resi dihapus",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        })
        itemTouchHelper.attachToRecyclerView(rvHistory)
    }

    private fun setupObservers() {
        viewModel.scanHistory.observe(viewLifecycleOwner) { history ->
            // Simpan semua history items untuk filtering
            allHistoryItems = history
            
            // Apply filter jika ada query
            val query = etSearchHistory.text.toString().trim()
            filterHistory(query)
        }
    }
}
