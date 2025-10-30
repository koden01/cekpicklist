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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cekpicklist.R
import com.example.cekpicklist.viewmodel.BarcodeScannerViewModel

class BarcodeHistoryFragment : Fragment() {

    private lateinit var viewModel: BarcodeScannerViewModel
    private lateinit var btnDateRange: Button
    private lateinit var etSearchHistory: EditText
    private lateinit var btnClearFilter: Button
    private lateinit var tvHistoryCount: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var layoutEmptyHistory: LinearLayout

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
        btnDateRange = view.findViewById(R.id.btnDateRange)
        etSearchHistory = view.findViewById(R.id.etSearchHistory)
        btnClearFilter = view.findViewById(R.id.btnClearFilter)
        tvHistoryCount = view.findViewById(R.id.tvHistoryCount)
        rvHistory = view.findViewById(R.id.rvHistory)
        layoutEmptyHistory = view.findViewById(R.id.layoutEmptyHistory)
    }

    private fun setupClickListeners() {
        btnDateRange.setOnClickListener {
            // TODO: Show date picker dialog
        }

        btnClearFilter.setOnClickListener {
            etSearchHistory.text.clear()
            btnDateRange.text = "Hari ini"
            // TODO: Clear filters and refresh data
        }

        etSearchHistory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // TODO: Filter history based on search text
            }
        })
    }

    private fun setupRecyclerView() {
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        // TODO: Setup adapter for history
    }

    private fun setupObservers() {
        viewModel.scanHistory.observe(viewLifecycleOwner) { history ->
            val count = history.size
            tvHistoryCount.text = "$count item"
            
            if (count == 0) {
                rvHistory.visibility = View.GONE
                layoutEmptyHistory.visibility = View.VISIBLE
            } else {
                rvHistory.visibility = View.VISIBLE
                layoutEmptyHistory.visibility = View.GONE
                // TODO: Update adapter with filtered data
            }
        }
    }
}
