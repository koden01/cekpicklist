package com.example.cekpicklist.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.adapter.ResiDetailAdapter
import com.example.cekpicklist.data.ResiDetail
import com.example.cekpicklist.api.ExpeditionSummaryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modal untuk menampilkan detail expedisi (sama dengan web app)
 */
class ExpeditionDetailModalFragment : DialogFragment() {
    
    companion object {
        private const val ARG_COURIER_NAME = "courier_name"
        private const val ARG_SELECTED_DATE = "selected_date"
        
        fun newInstance(courierName: String, selectedDate: String): ExpeditionDetailModalFragment {
            val fragment = ExpeditionDetailModalFragment()
            val args = Bundle()
            args.putString(ARG_COURIER_NAME, courierName)
            args.putString(ARG_SELECTED_DATE, selectedDate)
            fragment.arguments = args
            return fragment
        }
    }
    
    private lateinit var tvTitle: TextView
    private lateinit var rvResiDetails: RecyclerView
    private lateinit var btnClose: Button
    private lateinit var btnExport: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnBatalResi: Button
    private lateinit var btnConfirmResi: Button
    private lateinit var resiDetailAdapter: ResiDetailAdapter
    private lateinit var expeditionSummaryService: ExpeditionSummaryService
    
    private var courierName: String = ""
    private var selectedDate: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
        
        arguments?.let {
            courierName = it.getString(ARG_COURIER_NAME, "")
            selectedDate = it.getString(ARG_SELECTED_DATE, "")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_expedition_detail_modal, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadExpeditionDetails()
    }
    
    private fun initViews(view: View) {
        tvTitle = view.findViewById(R.id.tvModalTitle)
        rvResiDetails = view.findViewById(R.id.rvResiDetails)
        btnClose = view.findViewById(R.id.btnCloseModal)
        btnExport = view.findViewById(R.id.btnExportData)
        btnRefresh = view.findViewById(R.id.btnRefreshData)
        btnBatalResi = view.findViewById(R.id.btnBatalResi)
        btnConfirmResi = view.findViewById(R.id.btnConfirmResi)
        
        tvTitle.text = "Detail Resi $courierName (Belum Kirim)"
        
        expeditionSummaryService = ExpeditionSummaryService()
    }
    
    private fun setupRecyclerView() {
        resiDetailAdapter = ResiDetailAdapter { resiNumber, action, value ->
            handleResiAction(resiNumber, action, value)
        }
        
        rvResiDetails.layoutManager = LinearLayoutManager(requireContext())
        rvResiDetails.adapter = resiDetailAdapter
    }
    
    private fun setupClickListeners() {
        btnClose.setOnClickListener {
            dismiss()
        }
        
        btnExport.setOnClickListener {
            exportDataToClipboard()
        }
        
        btnRefresh.setOnClickListener {
            loadExpeditionDetails()
        }
        
        btnBatalResi.setOnClickListener {
            showBatalResiDialog()
        }
        
        btnConfirmResi.setOnClickListener {
            showConfirmResiDialog()
        }
    }
    
    private fun loadExpeditionDetails() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val resiDetails = withContext(Dispatchers.IO) {
                    expeditionSummaryService.getExpeditionDetailRecords(courierName, selectedDate)
                }
                
                resiDetailAdapter.updateResiDetails(resiDetails)
                
                // Update title with count
                tvTitle.text = "Detail Resi $courierName (${resiDetails.size} resi)"
                
            } catch (e: Exception) {
                // Handle error
                tvTitle.text = "Error loading data for $courierName"
            }
        }
    }
    
    private fun handleResiAction(resiNumber: String, action: String, value: Any?) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    expeditionSummaryService.updateResiStatus(resiNumber, action, value)
                }
                
                if (success) {
                    // Reload data
                    loadExpeditionDetails()
                }
                
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    private fun exportDataToClipboard() {
        try {
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Expedition Data", generateExportData())
            clipboard.setPrimaryClip(clip)
            
            android.widget.Toast.makeText(requireContext(), "📋 Data berhasil disalin ke clipboard", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(), "❌ Gagal menyalin data", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun generateExportData(): String {
        val sb = StringBuilder()
        sb.appendLine("Detail Resi $courierName - $selectedDate")
        sb.appendLine("=".repeat(50))
        sb.appendLine()
        
        // Header
        sb.appendLine("Resi\tOrder\tChannel\tCourier\tCreated\tDate Trans\tFlag\tCEKFU\tKarung\tSchedule\tKeterangan")
        
        // Data rows - get current data from adapter
        val currentData = resiDetailAdapter.getCurrentData()
        currentData.forEach { resi ->
            sb.appendLine("${resi.Resi}\t${resi.orderno ?: "-"}\t${resi.chanelsales ?: "-"}\t${resi.couriername ?: "-"}\t${resi.created ?: "-"}\t${resi.datetrans ?: "-"}\t${resi.flag ?: "-"}\t${resi.cekfuStatus}\t${resi.nokarung ?: "-"}\t${resi.schedule ?: "-"}\t${resi.Keterangan ?: "-"}")
        }
        
        return sb.toString()
    }
    
    private fun showBatalResiDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("❌ Batal Resi")
        builder.setMessage("Masukkan nomor resi yang akan dibatalkan:")
        
        val input = android.widget.EditText(requireContext())
        input.hint = "Nomor resi"
        builder.setView(input)
        
        builder.setPositiveButton("Batal Resi") { _, _ ->
            val resiNumber = input.text.toString().trim()
            if (resiNumber.isNotEmpty()) {
                handleResiAction(resiNumber, "batal", null)
            } else {
                android.widget.Toast.makeText(requireContext(), "Nomor resi tidak boleh kosong", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    private fun showConfirmResiDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("✅ Confirm Resi")
        builder.setMessage("Masukkan nomor resi yang akan dikonfirmasi:")
        
        val input = android.widget.EditText(requireContext())
        input.hint = "Nomor resi"
        builder.setView(input)
        
        builder.setPositiveButton("Confirm") { _, _ ->
            val resiNumber = input.text.toString().trim()
            if (resiNumber.isNotEmpty()) {
                handleResiAction(resiNumber, "confirm", null)
            } else {
                android.widget.Toast.makeText(requireContext(), "Nomor resi tidak boleh kosong", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
