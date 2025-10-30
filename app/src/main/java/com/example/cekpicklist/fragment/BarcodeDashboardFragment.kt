package com.example.cekpicklist.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.adapter.ExpeditionSummaryAdapter
import com.example.cekpicklist.data.ExpeditionSummary
import com.example.cekpicklist.viewmodel.BarcodeScannerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class BarcodeDashboardFragment : Fragment() {

    private lateinit var viewModel: BarcodeScannerViewModel
    private lateinit var tvTotalScans: TextView
    private lateinit var tvSuccessRate: TextView
    private lateinit var tvDuplicateScans: TextView
    private lateinit var tvFailedScans: TextView
    private lateinit var tvAvgScanTime: TextView
    private lateinit var tvScannerType: TextView
    private lateinit var tvSyncStatus: TextView
    private lateinit var rvRecentScans: RecyclerView
    private lateinit var rvExpeditionSummaries: RecyclerView
    private lateinit var expeditionSummaryAdapter: ExpeditionSummaryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_barcode_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[BarcodeScannerViewModel::class.java]
        
        initViews(view)
        setupRecyclerView()
        setupObservers()
    }

    private fun initViews(view: View) {
        tvTotalScans = view.findViewById(R.id.tvTotalScans)
        tvSuccessRate = view.findViewById(R.id.tvSuccessRate)
        tvDuplicateScans = view.findViewById(R.id.tvDuplicateScans)
        tvFailedScans = view.findViewById(R.id.tvFailedScans)
        tvAvgScanTime = view.findViewById(R.id.tvAvgScanTime)
        tvScannerType = view.findViewById(R.id.tvScannerType)
        tvSyncStatus = view.findViewById(R.id.tvSyncStatus)
        rvRecentScans = view.findViewById(R.id.rvRecentScans)
        rvExpeditionSummaries = view.findViewById(R.id.rvExpeditionSummaries)
    }

    private fun setupRecyclerView() {
        rvRecentScans.layoutManager = LinearLayoutManager(requireContext())
        // TODO: Setup adapter for recent scans
        
        // Setup expedition summaries RecyclerView
        expeditionSummaryAdapter = ExpeditionSummaryAdapter { expedition ->
            openExpeditionDetailModal(expedition.name)
        }
        
        // Use GridLayoutManager for 2 columns (same as web app)
        rvExpeditionSummaries.layoutManager = GridLayoutManager(requireContext(), 2)
        rvExpeditionSummaries.adapter = expeditionSummaryAdapter
    }

    private fun setupObservers() {
        viewModel.scanCount.observe(viewLifecycleOwner) { count ->
            tvTotalScans.text = count.toString()
        }

        viewModel.scanHistory.observe(viewLifecycleOwner) { history ->
            // Calculate statistics
            val totalScans = history.size
            val successfulScans = history.count { it.status == "success" }
            val duplicateScans = history.count { it.status == "duplicate" }
            val failedScans = history.count { it.status == "failed" }
            
            val successRate = if (totalScans > 0) (successfulScans * 100 / totalScans) else 0
            val avgScanTime = 0 // scanDuration tidak ada lagi di schema baru

            tvTotalScans.text = totalScans.toString()
            tvSuccessRate.text = "$successRate%"
            tvDuplicateScans.text = duplicateScans.toString()
            tvFailedScans.text = failedScans.toString()
            tvAvgScanTime.text = "${avgScanTime}ms"
        }

        viewModel.scannerType.observe(viewLifecycleOwner) { type ->
            tvScannerType.text = type
        }

        // Mock sync status for now
        tvSyncStatus.text = "Aktif"
        tvSyncStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        
        // Load expedition summaries
        loadExpeditionSummaries()
    }
    
    private fun loadExpeditionSummaries() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val selectedDate = getCurrentDate()
                val summaries = withContext(Dispatchers.IO) {
                    com.example.cekpicklist.api.ExpeditionSummaryService().getExpeditionSummaries(selectedDate)
                }
                
                expeditionSummaryAdapter.updateExpeditions(summaries)
                
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
    
    private fun openExpeditionDetailModal(courierName: String) {
        val selectedDate = getCurrentDate()
        val modal = ExpeditionDetailModalFragment.newInstance(courierName, selectedDate)
        modal.show(parentFragmentManager, "ExpeditionDetailModal")
    }
}
